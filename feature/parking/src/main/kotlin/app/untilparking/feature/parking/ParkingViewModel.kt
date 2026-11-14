package app.untilparking.feature.parking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.untilparking.domain.AppError
import app.untilparking.domain.model.AlternativeReason
import app.untilparking.domain.model.ArrivalPrompt
import app.untilparking.domain.model.CongestionLevel
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationSearchResult
import app.untilparking.domain.model.DestinationChange
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.model.ParkingCandidate
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
import app.untilparking.domain.usecase.AlertNewDestination
import app.untilparking.domain.usecase.ClassifyDestinationChange
import app.untilparking.domain.usecase.DetectArrival
import app.untilparking.domain.usecase.DismissDestinationAlert
import app.untilparking.domain.usecase.ExpandRadius
import app.untilparking.domain.usecase.JudgeArrivalPrompt
import app.untilparking.domain.usecase.Listen
import app.untilparking.domain.usecase.ObserveDestination
import app.untilparking.domain.usecase.ObserveDrivingState
import app.untilparking.domain.usecase.ObserveVehicleProfile
import app.untilparking.domain.usecase.PageCandidates
import app.untilparking.domain.usecase.ParseVoiceCommand
import app.untilparking.domain.usecase.RecheckParking
import app.untilparking.domain.usecase.OpenDestinationSearch
import app.untilparking.domain.usecase.RefreshDestination
import app.untilparking.domain.usecase.ReportCongestion
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
    private val openDestinationSearch: OpenDestinationSearch,
    private val searchParking: SearchParking,
    private val recheckParking: RecheckParking,
    private val expandRadiusUseCase: ExpandRadius,
    private val pageCandidates: PageCandidates,
    private val classifyDestinationChange: ClassifyDestinationChange,
    private val alertNewDestination: AlertNewDestination,
    private val dismissDestinationAlert: DismissDestinationAlert,
    private val requestGuidance: RequestGuidance,
    private val validateVehicleValue: ValidateVehicleValue,
    private val saveVehicleProfile: SaveVehicleProfile,
    private val parseVoiceCommand: ParseVoiceCommand,
    private val speak: Speak,
    private val listen: Listen,
    private val detectArrival: DetectArrival,
    private val judgeArrivalPrompt: JudgeArrivalPrompt,
    private val reportCongestionUseCase: ReportCongestion,
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
    /** 화면이 사용자에게 보이는지(Activity onStart~onStop). 안 보일 때만 새 목적지를 알림으로 알린다(목적지 알림 스펙 N1). */
    private var appVisible = true

    init {
        viewModelScope.launch { observeDrivingState().collect(::onDriving) }
        viewModelScope.launch { observeVehicleProfile().collect { profile -> mutableState.update { it.copy(profile = profile) } } }
        viewModelScope.launch { observeDestination().collect(::onDestination) }
    }

    // ---- 목적지 ----

    /** Activity가 onStart·onStop에서 알린다. 화면에 오면 떠 있던 목적지 알림은 필요 없다(목적지 알림 스펙 N1). */
    fun onAppVisible(visible: Boolean) {
        appVisible = visible
        if (visible) dismissDestinationAlert()
    }

    private fun onDestination(next: Destination?) {
        val current = mutableState.value
        val plannedEntrance = (current.recheck?.candidate ?: current.selected)?.parking?.entrance
        val change = classifyDestinationChange(current.destination, next, plannedEntrance, current.routeState == RouteState.Pending)
        alertNewDestination(change, next, appVisible)
        // 그 줄이 "안내 중"일 때 목적지가 없어졌으면 도착 후보다(혼잡도 스펙 C1). 아래에서 상태를 비우기 전에 판정한다.
        val guiding = (current.recheck?.candidate ?: current.selected)?.parking?.takeIf { current.routeState == RouteState.Started }
        val arrival = detectArrival(change, next, guiding, clock.instant())
        when (change) {
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
                        // 새 목적지는 S02 비교부터 연다(2026-11-12 사용자 결정, 스펙 R7). 후보가 없으면 search가 S07로 보낸다.
                        screen = if (next == null) Screen.DESTINATION_WAIT else Screen.COMPARE,
                        pendingArrival = arrival,
                    )
                }
                if (next != null) search() else updateArrivalPrompt()
            }
        }
    }

    override fun refreshDestination() {
        interruptVoice()
        refreshDestinationUseCase()
    }

    /** 목적지 검색 스펙 D1. 묻고 한 번 듣고, 들은 말로 내비 장소 검색을 연다. 목적지는 내비가 바꾼다. */
    override fun searchDestination() {
        if (!mutableState.value.parked || voiceJob?.isActive == true) return
        voiceJob = viewModelScope.launch {
            try {
                val heard = try {
                    say("어디로 갈까요?")
                    mutableState.update { it.copy(voice = VoiceState.LISTENING) }
                    listen()
                } catch (e: AppError.VoiceFailed) {
                    null // 못 들은 것과 같다: 빈 검색 화면을 연다 [가설]
                }
                mutableState.update { it.copy(voice = VoiceState.PROCESSING) }
                val notice = when (openDestinationSearch(heard, mutableState.value.driving)) {
                    is DestinationSearchResult.Opened -> "내비에서 장소를 고르면 이 화면이 바뀝니다"
                    DestinationSearchResult.Cancelled -> null
                    DestinationSearchResult.Blocked -> "정차 중에만 검색할 수 있어요"
                }
                mutableState.update { it.copy(notice = notice) }
            } catch (e: AppError.NavigationFailed) {
                mutableState.update { it.copy(notice = "내비 검색 화면을 열지 못했어요") }
            } finally {
                mutableState.update { it.copy(voice = VoiceState.IDLE) }
            }
        }
    }

    // ---- 도착과 혼잡도 (S13) ----

    /** 도착 후보가 있으면 정차 중일 때만 묻는다(혼잡도 스펙 C2). 목적지와 주행 상태가 바뀔 때마다 다시 본다. */
    private fun updateArrivalPrompt() {
        val s = mutableState.value
        val pending = s.pendingArrival ?: return
        // 묻는 화면이 떠 있는 동안에는 10분이 지나도 닫지 않는다
        if (s.askingCongestion && s.parked) return
        when (judgeArrivalPrompt(pending, s.driving, clock.instant())) {
            ArrivalPrompt.ASK -> mutableState.update { it.copy(askingCongestion = true) }
            ArrivalPrompt.WAIT -> mutableState.update { it.copy(askingCongestion = false) }
            ArrivalPrompt.DROP -> mutableState.update { it.copy(pendingArrival = null, askingCongestion = false) }
        }
    }

    override fun reportCongestion(level: CongestionLevel) {
        interruptVoice()
        val arrival = mutableState.value.arrivalToReport ?: return
        // 먼저 비워서 두 번 눌러도 한 건만 저장한다(혼잡도 스펙 C3)
        mutableState.update { it.copy(pendingArrival = null, askingCongestion = false) }
        viewModelScope.launch {
            val notice = try {
                reportCongestionUseCase(arrival.parkingId, level)
                "혼잡도를 남겼어요"
            } catch (e: AppError) {
                "혼잡도를 저장하지 못했어요"
            }
            // 그 사이 새 목적지가 잡혔으면 S02에 낡은 문구를 올리지 않는다
            mutableState.update { if (it.screen == Screen.DESTINATION_WAIT) it.copy(notice = notice) else it }
        }
    }

    override fun skipCongestionReport() {
        interruptVoice()
        mutableState.update { it.copy(pendingArrival = null, askingCongestion = false) }
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
                    // 안내를 요청했거나 안내 중이면 다시 조회해도 그 줄 표시를 남긴다(스펙 R7)
                    val keepGuidance = it.routeState == RouteState.Started || it.routeState == RouteState.Pending
                    it.copy(
                        loading = false,
                        linked = result.linked,
                        candidates = result.candidates,
                        page = pageCandidates(result.candidates, 0),
                        selectedId = if (keepGuidance) it.selectedId else null,
                        recheck = if (keepGuidance) it.recheck else null,
                        recheckLoading = false,
                        routeState = if (keepGuidance) it.routeState else RouteState.Idle,
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

    override fun nextPage() = movePage(+1)

    override fun previousPage() = movePage(-1)

    private fun movePage(delta: Int) {
        interruptVoice()
        // 페이지가 바뀌면 번호만 바뀐다(T02). 진행 중인 재조회·경로 요청은 주차장 ID에 묶여 있어 그대로 둔다(스펙 R2).
        mutableState.update { it.copy(page = pageCandidates(it.candidates, it.page.index + delta)) }
    }

    override fun sortBy(key: SortKey) {
        interruptVoice()
        if (mutableState.value.conditions.sort == key) return
        mutableState.update { it.copy(conditions = it.conditions.copy(sort = key)) }
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

    // ---- 안내 (S02 줄 · 음성 번호 · S07) ----

    /**
     * 최종 확인 화면 없이 바로 안내한다(2026-11-12 사용자 결정, 스펙 R7).
     * 그 주차장만 재조회해서 만차·차량 제한이면 S07로 보내고, READY나 정보 미확인이면 바로 경로를 바꾼다.
     */
    override fun guideTo(id: String) {
        interruptVoice()
        val s = mutableState.value
        // 재조회·요청 중에는 다른 줄을 눌러도 받지 않는다. requestRoute는 한 번만 나간다(T08).
        if (s.guidanceBusy) return
        val candidate = (s.candidates + s.linked).firstOrNull { it.parking.id == id } ?: return
        val destination = s.destination ?: return
        recheckJob?.cancel()
        mutableState.update { it.copy(selectedId = id, recheck = null, recheckLoading = true, routeState = RouteState.Idle) }
        recheckJob = viewModelScope.launch {
            val result = recheckParking(candidate, s.conditions, s.profile, destination)
            when (result.status) {
                // 만차·제한 위반이면 경로 변경을 멈추고 대체 후보를 제시한다(S07, T07)
                RecheckStatus.FULL, RecheckStatus.BLOCKED -> mutableState.update {
                    it.copy(
                        recheckLoading = false,
                        recheck = result,
                        screen = Screen.ALTERNATIVES,
                        alternativeReason = if (result.status == RecheckStatus.FULL) AlternativeReason.FULL else AlternativeReason.BLOCKED,
                        failedCandidate = result.candidate,
                        selectedId = null,
                    )
                }
                // 정보 미확인(STALE·UNAVAILABLE·ERROR)도 묻지 않는다. 그 줄에 이미 "정보 미확인"이 보인다.
                RecheckStatus.READY, RecheckStatus.UNKNOWN -> {
                    mutableState.update { it.copy(recheckLoading = false, recheck = result, routeState = RouteState.Pending) }
                    requestRoute(result.candidate)
                }
            }
        }
    }

    private fun requestRoute(candidate: ParkingCandidate) {
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            val result = requestGuidance(candidate.parking)
            mutableState.update {
                // 그 사이 다른 줄로 새 요청이 시작됐으면 늦은 결과로 덮지 않는다
                if (it.selectedId != candidate.parking.id) return@update it
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

    /** 후보를 무효화한 뒤에는 S07로 돌아가지 않는다. 만차로 바뀐 그 주차장이 이미 낡은 결과이기 때문이다(스펙 R8, 흐름도 S08→S02). */
    private fun restoredScreen(afterInvalidation: Boolean = false): Screen {
        val before = screenBeforeOverlay.takeIf { it != Screen.VEHICLE } ?: Screen.COMPARE
        return if (afterInvalidation && before == Screen.ALTERNATIVES) Screen.COMPARE else before
    }

    // ---- 주행 상태 ----

    private fun onDriving(state: DrivingState) {
        mutableState.update { it.copy(driving = state) }
        updateArrivalPrompt()
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
                if (s.guidanceBusy) return "앞의 안내 요청을 처리하고 있어요."
                // 번호를 말하면 그 줄을 누른 것과 같다(2026-11-12 사용자 결정, 스펙 R10)
                guideTo(item.parking.id)
                "${item.parking.name}으로 안내할게요."
            }
            VoiceCommand.NextPage -> { nextPage(); "다음 주차장이에요." }
            VoiceCommand.PreviousPage -> { previousPage(); "이전 주차장이에요." }
            // 최종 확인 화면이 없어 "안내 시작"만으로는 어느 주차장인지 모른다
            VoiceCommand.StartGuidance -> "번호로 말씀해 주세요. 예를 들어 첫 번째."
            VoiceCommand.Cancel, VoiceCommand.Unclear -> null
        }
    }
}
