package app.untilparking.feature.parking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.untilparking.domain.AppError
import app.untilparking.domain.model.AlternativeReason
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationChange
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.model.RecheckStatus
import app.untilparking.domain.model.RoofBox
import app.untilparking.domain.model.RouteRequestResult
import app.untilparking.domain.model.SearchConditions
import app.untilparking.domain.model.SortKey
import app.untilparking.domain.model.SuvFlag
import app.untilparking.domain.model.VehicleField
import app.untilparking.domain.model.VehicleProfile
import app.untilparking.domain.model.VehicleValueInput
import app.untilparking.domain.model.VoiceCommand
import app.untilparking.domain.usecase.ClassifyDestinationChange
import app.untilparking.domain.usecase.ExpandRadius
import app.untilparking.domain.usecase.Listen
import app.untilparking.domain.usecase.ObserveDestination
import app.untilparking.domain.usecase.ObserveDrivingState
import app.untilparking.domain.usecase.ObserveVehicleProfile
import app.untilparking.domain.usecase.PageCandidates
import app.untilparking.domain.usecase.ParseVoiceCommand
import app.untilparking.domain.usecase.RecheckParking
import app.untilparking.domain.usecase.RefreshDestination
import app.untilparking.domain.usecase.RequestGuidance
import app.untilparking.domain.usecase.SaveVehicleProfile
import app.untilparking.domain.usecase.SearchParking
import app.untilparking.domain.usecase.Speak
import app.untilparking.domain.usecase.ValidateVehicleValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * 화면 상태와 흐름의 순서만 맡는다. 규칙(무엇을 고르고 무엇이 맞나)은 전부 유스케이스에 있다.
 * 모든 이벤트는 이 하나의 상태에 순서대로 반영된다(기획서 25절). 상태는 메인 스레드에서만 바꾼다.
 */
@HiltViewModel
class ParkingViewModel @Inject constructor(
    private val observeDestination: ObserveDestination,
    private val observeDrivingState: ObserveDrivingState,
    private val observeVehicleProfile: ObserveVehicleProfile,
    private val refreshDestinationUseCase: RefreshDestination,
    private val searchParking: SearchParking,
    private val recheckParking: RecheckParking,
    private val expandRadiusUseCase: ExpandRadius,
    private val pageCandidates: PageCandidates,
    private val classifyDestinationChange: ClassifyDestinationChange,
    private val requestGuidance: RequestGuidance,
    private val validateVehicleValue: ValidateVehicleValue,
    private val saveVehicleProfile: SaveVehicleProfile,
    private val parseVoiceCommand: ParseVoiceCommand,
    private val speak: Speak,
    private val listen: Listen,
    private val clock: Clock,
) : ViewModel(), ParkingActions {

    private val mutableState = MutableStateFlow(ParkingUiState())
    val state: StateFlow<ParkingUiState> = mutableState.asStateFlow()

    /** 목적지·프로필·조건이 바뀔 때마다 오른다. 늦게 온 응답은 버전이 다르면 버린다(T09). */
    private var searchVersion = 0
    private var searchJob: Job? = null
    private var recheckJob: Job? = null
    private var routeJob: Job? = null
    private var voiceJob: Job? = null
    private var screenBeforeOverlay: Screen = Screen.COMPARE

    init {
        viewModelScope.launch { observeDrivingState().collect(::onDriving) }
        viewModelScope.launch { observeVehicleProfile().collect { profile -> mutableState.update { it.copy(profile = profile) } } }
        viewModelScope.launch { observeDestination().collect(::onDestination) }
    }

    // ---- 목적지 ----

    private fun onDestination(next: Destination?) {
        val current = mutableState.value
        val plannedEntrance = (current.recheck?.candidate ?: current.selected)?.parking?.entrance
        when (classifyDestinationChange(current.destination, next, plannedEntrance)) {
            DestinationChange.SAME -> Unit
            // 차량 도착지를 주차장 입구로 바꾼 것은 앱 자신이다. 방문 목적지는 그대로 둔다.
            DestinationChange.OWN_REROUTE -> mutableState.update {
                if (it.routeState == RouteState.Pending || it.routeState == RouteState.Unknown) it.copy(routeState = RouteState.Started) else it
            }
            DestinationChange.NEW_VISIT -> {
                invalidateSearch()
                mutableState.update {
                    ParkingUiState(
                        driving = it.driving,
                        profile = it.profile,
                        conditions = it.conditions,
                        destination = next,
                        screen = if (next == null) Screen.DESTINATION_WAIT else Screen.DESTINATION_PARKING,
                    )
                }
                if (next != null) search()
            }
        }
    }

    override fun refreshDestination() {
        interruptVoice()
        refreshDestinationUseCase()
    }

    // ---- 조회 ----

    private fun invalidateSearch() {
        searchVersion++
        searchJob?.cancel()
        recheckJob?.cancel()
        voiceJob?.cancel()
    }

    private fun search(then: Screen? = null) {
        val destination = mutableState.value.destination ?: return
        searchVersion++
        val version = searchVersion
        searchJob?.cancel()
        // 조회가 바뀌면 진행 중인 재조회도 버린다. 늦게 온 FULL·BLOCKED 결과가 새 목록 위에서 S07로 튀지 않게 한다.
        recheckJob?.cancel()
        searchJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, notice = null) }
            try {
                val snapshot = mutableState.value
                val result = searchParking(destination, snapshot.conditions, snapshot.profile)
                if (version != searchVersion) return@launch
                mutableState.update {
                    val target = then ?: it.screen
                    val empty = result.candidates.isEmpty() && target == Screen.COMPARE
                    it.copy(
                        loading = false,
                        linked = result.linked,
                        candidates = result.candidates,
                        page = pageCandidates(result.candidates, 0),
                        selectedId = null,
                        recheck = null,
                        unknownAcknowledged = false,
                        receivedAt = clock.instant(),
                        screen = if (empty) Screen.ALTERNATIVES else target,
                        alternativeReason = if (empty) AlternativeReason.EMPTY else it.alternativeReason,
                        failedCandidate = if (empty) null else it.failedCandidate,
                    )
                }
            } catch (e: AppError) {
                if (version != searchVersion) return@launch
                mutableState.update { it.copy(loading = false, notice = e.message, screen = then ?: it.screen) }
            }
        }
    }

    override fun openCompare() {
        interruptVoice()
        val s = mutableState.value
        if (s.candidates.isEmpty() && !s.loading) {
            mutableState.update { it.copy(screen = Screen.ALTERNATIVES, alternativeReason = AlternativeReason.EMPTY, failedCandidate = null) }
        } else {
            mutableState.update { it.copy(screen = Screen.COMPARE) }
        }
    }

    override fun openDestinationParking() {
        interruptVoice()
        mutableState.update { it.copy(screen = Screen.DESTINATION_PARKING) }
    }

    override fun select(id: String) {
        interruptVoice()
        recheckJob?.cancel()
        // 카드 터치는 선택만 바꾼다. 이전 최종 확인은 무효가 된다.
        mutableState.update { it.copy(selectedId = id, recheck = null, unknownAcknowledged = false, routeState = RouteState.Idle) }
    }

    override fun selectAndConfirm(id: String) {
        select(id)
        confirmSelection()
    }

    override fun nextPage() = movePage(+1)

    override fun previousPage() = movePage(-1)

    private fun movePage(delta: Int) {
        interruptVoice()
        recheckJob?.cancel()
        // 페이지가 바뀌면 번호가 바뀌므로 선택과 확인을 초기화한다(T02)
        mutableState.update { it.copy(page = pageCandidates(it.candidates, it.page.index + delta), selectedId = null, recheck = null) }
    }

    override fun cycleSort() {
        interruptVoice()
        val next = when (mutableState.value.conditions.sort) {
            SortKey.DISTANCE -> SortKey.COST
            SortKey.COST -> SortKey.AVAILABILITY
            SortKey.AVAILABILITY -> SortKey.DISTANCE
        }
        mutableState.update { it.copy(conditions = it.conditions.copy(sort = next)) }
        search(Screen.COMPARE)
    }

    // ---- 조건 (S03 · S11) ----

    override fun openConditions() {
        interruptVoice()
        mutableState.update { it.copy(draftConditions = it.conditions, screen = Screen.CONDITIONS) }
    }

    override fun updateDraftConditions(conditions: SearchConditions) {
        interruptVoice()
        mutableState.update { it.copy(draftConditions = conditions) }
    }

    override fun applyConditions() {
        interruptVoice()
        mutableState.update { it.copy(conditions = it.draftConditions) }
        search(Screen.COMPARE)
    }

    override fun cancelConditions() {
        interruptVoice()
        mutableState.update { it.copy(screen = Screen.COMPARE, draftConditions = it.conditions) }
    }

    override fun resetDraftConditions() {
        interruptVoice()
        // 초기화는 편집 초안만 기본값으로 바꾼다. 반경은 유지한다.
        mutableState.update { it.copy(draftConditions = SearchConditions(radiusM = it.conditions.radiusM)) }
    }

    // ---- 최종 확인 (S06) ----

    override fun confirmSelection() {
        interruptVoice()
        if (mutableState.value.selected == null) return
        mutableState.update { it.copy(screen = Screen.CONFIRM, routeState = RouteState.Idle) }
        recheck()
    }

    override fun recheck() {
        interruptVoice()
        val s = mutableState.value
        val candidate = s.selected ?: return
        val destination = s.destination ?: return
        recheckJob?.cancel()
        recheckJob = viewModelScope.launch {
            mutableState.update { it.copy(recheckLoading = true, unknownAcknowledged = false) }
            val result = recheckParking(candidate, s.conditions, s.profile, destination)
            mutableState.update {
                when (result.status) {
                    // 만차·제한 위반이면 경로 변경을 멈추고 대체 후보를 제시한다(S07)
                    RecheckStatus.FULL, RecheckStatus.BLOCKED -> it.copy(
                        recheckLoading = false,
                        recheck = result,
                        screen = Screen.ALTERNATIVES,
                        alternativeReason = if (result.status == RecheckStatus.FULL) AlternativeReason.FULL else AlternativeReason.BLOCKED,
                        failedCandidate = result.candidate,
                        selectedId = null,
                    )
                    else -> it.copy(recheckLoading = false, recheck = result)
                }
            }
        }
    }

    override fun acknowledgeUnknown() {
        interruptVoice()
        mutableState.update { it.copy(unknownAcknowledged = true) }
    }

    override fun startGuidance() {
        interruptVoice()
        val s = mutableState.value
        val recheck = s.recheck ?: return
        if (s.routeState == RouteState.Pending) return // 중복 요청 차단(T08)
        if (!clock.instant().isBefore(recheck.expiresAt)) {
            // 30초가 지났다. 다시 조회하고 다시 확인받는다(T08)
            recheck()
            return
        }
        val ready = recheck.status == RecheckStatus.READY || (recheck.status == RecheckStatus.UNKNOWN && s.unknownAcknowledged)
        if (!ready) return
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            mutableState.update { it.copy(routeState = RouteState.Pending) }
            val result = requestGuidance(recheck.candidate.parking)
            mutableState.update {
                it.copy(
                    routeState = when (result) {
                        RouteRequestResult.Success -> RouteState.Started
                        is RouteRequestResult.Failed -> RouteState.Failed(result.reason)
                        // 결과를 못 받는 사이 내비 목적지가 이미 입구로 바뀌었으면(onDestination의 OWN_REROUTE) 시작된 것이다. 늦은 Unknown으로 되돌리지 않는다.
                        RouteRequestResult.Unknown -> if (it.routeState == RouteState.Started) RouteState.Started else RouteState.Unknown
                    },
                )
            }
            if (result == RouteRequestResult.Unknown) refreshDestinationUseCase()
        }
    }

    override fun cancelConfirm() {
        interruptVoice()
        recheckJob?.cancel()
        mutableState.update { it.copy(screen = Screen.COMPARE, recheck = null, recheckLoading = false, routeState = RouteState.Idle) }
    }

    // ---- 대체 선택 (S07) ----

    override fun refreshList() {
        interruptVoice()
        search(Screen.COMPARE)
    }

    override fun expandRadius() {
        interruptVoice()
        val next = expandRadiusUseCase(mutableState.value.conditions.radiusM) ?: return
        mutableState.update { it.copy(conditions = it.conditions.copy(radiusM = next)) }
        search(Screen.COMPARE)
    }

    // ---- 차량 프로필 (S08) ----

    override fun openVehicle() {
        interruptVoice()
        val s = mutableState.value
        if (!s.parked) return
        screenBeforeOverlay = s.screen
        mutableState.update {
            it.copy(
                screen = Screen.VEHICLE,
                draftVehicleText = it.draftVehicleText.ifEmpty { textOf(it.profile) },
                draftVehicleErrors = emptyMap(),
                draftSuv = it.profile.suv,
                draftRoofBox = it.profile.roofBox,
            )
        }
    }

    private fun textOf(profile: VehicleProfile): Map<VehicleField, String> = mapOf(
        VehicleField.HEIGHT_MM to profile.heightMm?.toString().orEmpty(),
        VehicleField.WIDTH_MM to profile.widthMm?.toString().orEmpty(),
        VehicleField.LENGTH_MM to profile.lengthMm?.toString().orEmpty(),
        VehicleField.WEIGHT_KG to profile.weightKg?.toString().orEmpty(),
    )

    override fun updateDraftVehicle(field: VehicleField, text: String) {
        interruptVoice()
        val result = validateVehicleValue(field, text)
        mutableState.update {
            it.copy(
                draftVehicleText = it.draftVehicleText + (field to text),
                draftVehicleErrors = if (result is VehicleValueInput.Invalid) it.draftVehicleErrors + (field to result.message) else it.draftVehicleErrors - field,
            )
        }
    }

    override fun setDraftSuv(suv: SuvFlag) {
        interruptVoice()
        mutableState.update { it.copy(draftSuv = suv) }
    }

    override fun setDraftRoofBox(roofBox: RoofBox) {
        interruptVoice()
        mutableState.update { it.copy(draftRoofBox = roofBox) }
    }

    override fun saveVehicle() {
        interruptVoice()
        val s = mutableState.value
        if (s.draftVehicleErrors.isNotEmpty()) return
        fun value(field: VehicleField) = (validateVehicleValue(field, s.draftVehicleText[field].orEmpty()) as? VehicleValueInput.Value)?.value
        val profile = s.profile.copy(
            heightMm = value(VehicleField.HEIGHT_MM),
            widthMm = value(VehicleField.WIDTH_MM),
            lengthMm = value(VehicleField.LENGTH_MM),
            weightKg = value(VehicleField.WEIGHT_KG),
            suv = s.draftSuv,
            roofBox = s.draftRoofBox,
        )
        viewModelScope.launch {
            saveVehicleProfile(profile)
            // 저장하면 후보와 최종 확인을 무효화하고 다시 조회한다(스펙 R8). 저장소 흐름보다 먼저 상태에 반영해 재조회가 새 값으로 돈다.
            recheckJob?.cancel()
            mutableState.update {
                it.copy(profile = profile.copy(version = it.profile.version + 1), draftVehicleText = emptyMap(), selectedId = null, recheck = null, screen = restoredScreen(afterInvalidation = true))
            }
            if (mutableState.value.destination != null) search()
        }
    }

    override fun cancelVehicle() {
        interruptVoice()
        mutableState.update { it.copy(screen = restoredScreen(), draftVehicleText = emptyMap(), draftVehicleErrors = emptyMap()) }
    }

    override fun resetDraftVehicle() {
        interruptVoice()
        // 초기화는 사용자 입력만 지운다
        mutableState.update {
            it.copy(draftVehicleText = VehicleField.entries.associateWith { "" }, draftVehicleErrors = emptyMap(), draftSuv = SuvFlag.UNKNOWN, draftRoofBox = RoofBox.UNKNOWN)
        }
    }

    /** 선택·최종 확인을 무효화한 뒤에는 S06·S07로 돌아가지 않는다. 고른 것이 없는 확인 화면이 되기 때문이다(스펙 R8, 흐름도 S08→S02). */
    private fun restoredScreen(afterInvalidation: Boolean = false): Screen {
        val before = screenBeforeOverlay.takeIf { it != Screen.VEHICLE } ?: Screen.COMPARE
        return if (afterInvalidation && (before == Screen.CONFIRM || before == Screen.ALTERNATIVES)) Screen.COMPARE else before
    }

    // ---- 주행 상태 ----

    private fun onDriving(state: DrivingState) {
        mutableState.update { it.copy(driving = state) }
        if (state != DrivingState.MOVING) return
        // 주행 전환: 조건 초안은 버리고 S02로, 정차 전용 화면은 닫는다(T12). 차량 초안은 메모리에 남겨 다시 정차하면 이어 쓴다.
        mutableState.update {
            when (it.screen) {
                Screen.CONDITIONS -> it.copy(screen = Screen.COMPARE, draftConditions = it.conditions)
                Screen.VEHICLE -> it.copy(screen = Screen.COMPARE)
                else -> it
            }
        }
    }

    // ---- 음성 (S05) ----

    override fun toggleVoice() {
        if (voiceJob?.isActive == true) {
            voiceJob?.cancel()
            mutableState.update { it.copy(voice = VoiceState.IDLE) }
            return
        }
        voiceJob = viewModelScope.launch {
            try {
                // 재질문은 한 번만(기획서 26절)
                repeat(2) { attempt ->
                    mutableState.update { it.copy(voice = VoiceState.LISTENING) }
                    val heard = listen()
                    mutableState.update { it.copy(voice = VoiceState.PROCESSING) }
                    val command = parseVoiceCommand(heard.orEmpty(), mutableState.value.page.items.size)
                    if (command == VoiceCommand.Unclear) {
                        say(if (attempt == 0) "다시 말씀해 주세요." else "음성을 종료합니다.")
                        return@repeat
                    }
                    apply(command)?.let { say(it) }
                    return@launch
                }
            } catch (e: AppError) {
                Unit // 음성이 실패해도 화면 상태는 보존한다
            } finally {
                mutableState.update { it.copy(voice = VoiceState.IDLE) }
            }
        }
    }

    /** 터치가 들어오면 듣기를 멈추고 늦게 온 인식 결과를 버린다(T06). */
    private fun interruptVoice() {
        if (mutableState.value.voice == VoiceState.LISTENING) {
            voiceJob?.cancel()
            mutableState.update { it.copy(voice = VoiceState.IDLE) }
        }
    }

    private suspend fun say(text: String) {
        mutableState.update { it.copy(voice = VoiceState.SPEAKING) }
        speak(text)
    }

    /** 명령을 상태에 반영하고 읽어 줄 말을 돌려준다. */
    private fun apply(command: VoiceCommand): String? {
        val s = mutableState.value
        return when (command) {
            is VoiceCommand.ExcludeMechanical -> {
                mutableState.update { it.copy(conditions = it.conditions.copy(filters = it.conditions.filters.copy(excludeMechanical = command.exclude))) }
                search(Screen.COMPARE)
                // 복합 발화("기계식 빼고 두 번째")는 필터만 먼저 적용하고 새 목록에서 다시 고르게 한다
                if (command.exclude) "기계식을 제외했어요. 새 목록에서 다시 골라 주세요." else "기계식을 포함했어요. 새 목록에서 다시 골라 주세요."
            }
            is VoiceCommand.IncludeUnknownAvailability -> {
                mutableState.update { it.copy(conditions = it.conditions.copy(filters = it.conditions.filters.copy(includeUnknownAvailability = command.include))) }
                search(Screen.COMPARE)
                if (command.include) "잔여 정보가 없는 곳도 함께 보여 드려요." else "잔여 정보가 없는 곳은 뺐어요."
            }
            is VoiceCommand.Sort -> {
                mutableState.update { it.copy(conditions = it.conditions.copy(sort = command.key)) }
                search(Screen.COMPARE)
                "${sortLabel(command.key)}으로 정렬했어요."
            }
            is VoiceCommand.Select -> {
                val item = s.page.items.getOrNull(command.index) ?: return "그 번호는 없어요."
                selectAndConfirm(item.parking.id)
                "${item.parking.name}을 골랐어요. 최신 정보를 확인할게요."
            }
            VoiceCommand.NextPage -> { nextPage(); "다음 주차장이에요." }
            VoiceCommand.PreviousPage -> { previousPage(); "이전 주차장이에요." }
            VoiceCommand.StartGuidance -> {
                val recheck = s.recheck
                // "정보 미확인으로 계속"에 동의한 UNKNOWN은 READY처럼 동작한다(스펙 R7). 화면 버튼과 같은 조건이다.
                val ready = recheck != null && (recheck.status == RecheckStatus.READY || (recheck.status == RecheckStatus.UNKNOWN && s.unknownAcknowledged))
                if (s.screen == Screen.CONFIRM && recheck != null && ready && clock.instant().isBefore(recheck.expiresAt)) {
                    startGuidance()
                    "${recheck.candidate.parking.name}으로 안내를 시작합니다."
                } else {
                    "먼저 주차장을 고르고 확인 화면에서 말씀해 주세요."
                }
            }
            VoiceCommand.Cancel, VoiceCommand.Unclear -> null
        }
    }
}
