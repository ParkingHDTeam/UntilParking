package app.untilparking.feature.parking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.untilparking.domain.model.AlternativeReason
import app.untilparking.domain.model.Availability
import app.untilparking.domain.model.AvailabilityStatus
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationSource
import app.untilparking.domain.model.Distance
import app.untilparking.domain.model.DistanceKind
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.model.Eligibility
import app.untilparking.domain.model.EligibilityStatus
import app.untilparking.domain.model.FeeEstimate
import app.untilparking.domain.model.FeeStatus
import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.LinkType
import app.untilparking.domain.model.OperatingStatus
import app.untilparking.domain.model.Page
import app.untilparking.domain.model.ParkingCandidate
import app.untilparking.domain.model.ParkingCategory
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.ParkingMethod
import app.untilparking.domain.model.RecheckStatus
import app.untilparking.domain.model.Restriction
import app.untilparking.domain.model.RestrictionField
import app.untilparking.domain.model.RoofBox
import app.untilparking.domain.model.SortKey
import app.untilparking.domain.model.SuvFlag
import app.untilparking.domain.model.VehicleField
import app.untilparking.domain.model.VehicleProfile
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// 시안(1600×800)의 색. 고정 픽셀이 아니라 비율 배치다(기획서 14절).
private val Bg = Color(0xFF0E1620)
private val CardColor = Color(0xFF17212D)
private val CardAlt = Color(0xFF1F2B3A)
private val Accent = Color(0xFF5AC8FA)
private val OnAccent = Color(0xFF07131C)
private val Good = Color(0xFF4ADE80)
private val Warn = Color(0xFFF5B74E)
private val Muted = Color(0xFF9AA7B5)
private val Danger = Color(0xFFFF7A7A)
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun UntilParkingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = CardColor,
            surfaceVariant = CardAlt,
            primary = Accent,
            onPrimary = OnAccent,
            secondary = Muted,
            onSurface = Color.White,
            onBackground = Color.White,
            error = Danger,
        ),
        content = content,
    )
}

@Composable
fun ParkingRoute(viewModel: ParkingViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ParkingScreen(state, viewModel, modifier)
}

@Composable
fun ParkingScreen(state: ParkingUiState, actions: ParkingActions, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state, actions)
            VoiceBanner(state.voice)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.screen) {
                    Screen.DESTINATION_WAIT -> DestinationWaitScreen(state, actions)
                    Screen.DESTINATION_PARKING -> DestinationParkingScreen(state, actions)
                    Screen.COMPARE -> CompareScreen(state, actions)
                    Screen.CONDITIONS -> ConditionsScreen(state, actions)
                    Screen.CONFIRM -> ConfirmScreen(state, actions)
                    Screen.ALTERNATIVES -> AlternativesScreen(state, actions)
                    Screen.VEHICLE -> VehicleScreen(state, actions)
                }
            }
        }
    }
}

// ---------- 공통 ----------

/** 공통 상단: 음성은 S05를 열고, 내 차 설정은 정차 중에만 S08을 연다(기획서 14절). */
@Composable
private fun TopBar(state: ParkingUiState, actions: ParkingActions) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now()
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("주차까지", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        SmallButton(
            text = when (state.voice) {
                VoiceState.IDLE -> "음성"
                VoiceState.LISTENING -> "듣는 중 · 종료"
                VoiceState.PROCESSING -> "처리 중"
                VoiceState.SPEAKING -> "안내 중"
            },
            onClick = actions::toggleVoice,
            enabled = state.controlsEnabled,
            highlighted = state.voice != VoiceState.IDLE,
        )
        Spacer(Modifier.width(12.dp))
        SmallButton("내 차 설정", actions::openVehicle, enabled = state.parked)
        Spacer(Modifier.width(20.dp))
        Text(now.format(CLOCK), fontSize = 16.sp, color = Muted)
    }
    HorizontalDivider(color = CardAlt)
}

@Composable
private fun VoiceBanner(voice: VoiceState) {
    if (voice == VoiceState.IDLE) return
    Surface(color = Accent.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when (voice) {
                VoiceState.LISTENING -> "듣고 있어요. 예: “기계식 빼줘”, “두 번째”, “거리순”, “안내 시작”. 화면을 터치하면 듣기를 멈춰요."
                VoiceState.PROCESSING -> "말씀을 해석하고 있어요…"
                VoiceState.SPEAKING -> "안내를 읽고 있어요…"
                VoiceState.IDLE -> ""
            },
            fontSize = 16.sp,
            color = Accent,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Frame(
    title: String,
    subtitle: String?,
    bottom: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, fontSize = 16.sp, color = Muted, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = bottom)
    }
}

/** 터치 영역 최소 64dp, 문구 최대 2줄, 말줄임 없음(기획서 14절). */
@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    val label: @Composable RowScope.() -> Unit = {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center)
    }
    if (primary) {
        Button(onClick = onClick, modifier = modifier.heightIn(min = 64.dp), enabled = enabled, shape = shape, content = label)
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 64.dp),
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardAlt, contentColor = Color.White),
            content = label,
        )
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit, enabled: Boolean = true, highlighted: Boolean = false) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (highlighted) Accent.copy(alpha = 0.25f) else CardAlt,
            contentColor = if (highlighted) Accent else Color.White,
        ),
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(text, fontSize = 16.sp) }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = CardColor, modifier = modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun Chip(text: String, highlighted: Boolean = false, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    val clickable = if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) Accent.copy(alpha = 0.18f) else CardAlt,
        border = if (highlighted) BorderStroke(2.dp, Accent) else null,
        modifier = Modifier.heightIn(min = 44.dp).then(clickable),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                fontSize = 16.sp,
                color = if (!enabled) Muted else if (highlighted) Accent else Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 20.sp, modifier = Modifier.weight(1f), color = if (enabled) Color.White else Muted)
        Chip(if (checked) "켜짐" else "꺼짐", highlighted = checked, enabled = enabled)
    }
}

private fun availabilityColor(a: Availability): Color = when (a.status) {
    AvailabilityStatus.LIVE, AvailabilityStatus.RECEIVED -> if ((a.availableSpaces ?: 0) > 0) Good else Warn
    else -> Warn
}

private fun eligibilityColor(e: Eligibility): Color = when (e.status) {
    EligibilityStatus.VERIFIED_MATCH -> Good
    EligibilityStatus.UNKNOWN -> Warn
    EligibilityStatus.BLOCKED -> Danger
}

/** 운영 시간 밖이거나 모를 때만 한 줄. 열려 있으면 아무 말도 하지 않는다. */
private fun operatingLine(c: ParkingCandidate, minutes: Int): String? = when (c.operating) {
    OperatingStatus.CLOSED -> c.parking.operatingSchedule?.let { "운영 시간 밖 · ${it.open.format(CLOCK)}~${it.close.format(CLOCK)} (도착 후 ${minutesLabel(minutes)} 기준)" } ?: "운영 시간 밖"
    OperatingStatus.UNKNOWN -> "운영 시간 확인 필요"
    OperatingStatus.OPEN -> null
}

private fun restrictionLine(r: Restriction, profile: VehicleProfile): String {
    fun m(mm: Int) = "%.1fm".format(mm / 1000.0)
    return when (r.field) {
        RestrictionField.HEIGHT_MM -> "차량 높이 제한 ${m(r.value)}" + (profile.heightMm?.let { " · 내 차 높이 ${m(it)}" } ?: " · 내 차 높이 미입력")
        RestrictionField.WIDTH_MM -> "차량 폭 제한 ${m(r.value)}" + (profile.widthMm?.let { " · 내 차 폭 ${m(it)}" } ?: " · 내 차 폭 미입력")
        RestrictionField.LENGTH_MM -> "차량 길이 제한 ${m(r.value)}" + (profile.lengthMm?.let { " · 내 차 길이 ${m(it)}" } ?: "")
        RestrictionField.WEIGHT_KG -> "중량 제한 ${"%,d".format(r.value)}kg" + (profile.weightKg?.let { " · 내 차 ${"%,d".format(it)}kg" } ?: " · 내 차 중량 미입력")
        RestrictionField.SUV -> "SUV 입차 불가"
    }
}

// ---------- S00 ----------

@Composable
private fun DestinationWaitScreen(state: ParkingUiState, actions: ParkingActions) {
    Frame(
        title = "목적지를 먼저 확인하세요",
        subtitle = "S00 · 목적지가 없거나 조회에 실패한 경우",
        bottom = {
            Spacer(Modifier.weight(1f))
            ActionButton("내비 목적지 다시 확인", actions::refreshDestination, Modifier.weight(1f), primary = true)
        },
    ) {
        Panel(Modifier.fillMaxWidth()) {
            Text("내비게이션에 목적지를 설정해 주세요.", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("설정 후 이 앱으로 돌아와 다시 확인할 수 있습니다.", fontSize = 20.sp)
            Text("정차 중 목적지 직접 검색은 이 버전에 아직 없습니다.", fontSize = 16.sp, color = Muted)
            if (state.driving == DrivingState.UNKNOWN) {
                Text("주행 상태를 확인할 수 없어 조회만 할 수 있습니다.", fontSize = 16.sp, color = Warn)
            }
            state.notice?.let { Text(it, fontSize = 16.sp, color = Warn) }
        }
    }
}

// ---------- S01 ----------

@Composable
private fun DestinationParkingScreen(state: ParkingUiState, actions: ParkingActions) {
    val first = state.linked.firstOrNull()
    Frame(
        title = "목적지의 주차 정보를 확인하세요",
        subtitle = "방문 목적지  ${state.destination?.name.orEmpty()}",
        bottom = {
            ActionButton("주변 주차장 비교", actions::openCompare, Modifier.weight(1f), enabled = state.controlsEnabled)
            ActionButton("음성으로 선택", actions::toggleVoice, Modifier.weight(1f), enabled = state.controlsEnabled)
            Spacer(Modifier.weight(0.4f))
            ActionButton(
                "이 주차장 선택",
                { first?.let { actions.selectAndConfirm(it.parking.id) } },
                Modifier.weight(1.4f),
                primary = true,
                enabled = first != null && state.controlsEnabled,
            )
        },
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(2f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    state.loading && state.linked.isEmpty() -> Panel(Modifier.fillMaxWidth()) { Text("목적지 주차 정보를 조회하고 있어요…", fontSize = 20.sp) }
                    state.linked.isEmpty() -> Panel(Modifier.fillMaxWidth()) {
                        Text("목적지에 연결된 주차장 정보가 없어요", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("주변 주차장을 비교해서 고를 수 있어요.", fontSize = 20.sp)
                        Text("주차 지원은 확인된 시설 안내가 있을 때만 표시합니다.", fontSize = 16.sp, color = Muted)
                        state.notice?.let { Text(it, fontSize = 16.sp, color = Warn) }
                    }
                    else -> state.linked.forEach { LinkedCard(it, state) }
                }
            }
            Panel(Modifier.weight(1f)) {
                Text("방문 목적지는 유지", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("내비게이션의 목적지를 가져왔습니다.", fontSize = 18.sp)
                Text("다른 장소라면 목적지를 다시 확인하세요.", fontSize = 16.sp, color = Muted)
                if (!state.controlsEnabled) Text("주행 상태를 확인할 수 없어 조회만 할 수 있습니다.", fontSize = 16.sp, color = Warn)
            }
        }
    }
}

@Composable
private fun LinkedCard(c: ParkingCandidate, state: ParkingUiState) {
    Panel(Modifier.fillMaxWidth()) {
        Text(c.parking.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (c.link?.type == LinkType.PARTNER) "목적지 제휴" else "목적지 부설", highlighted = true)
            Chip(methodLabel(c.parking.method))
        }
        Text(availabilityLabel(c.availability), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = availabilityColor(c.availability))
        c.parking.restrictions.forEach { Text(restrictionLine(it, state.profile), fontSize = 18.sp) }
        c.link?.supportNote?.let { Text(it, fontSize = 18.sp) }
        operatingLine(c, state.conditions.parkingMinutes)?.let { Text(it, fontSize = 16.sp, color = Warn) }
        Text(eligibilityLabel(c.eligibility), fontSize = 16.sp, color = eligibilityColor(c.eligibility))
        Text("주차 지원은 확인된 시설 안내가 있을 때 표시", fontSize = 14.sp, color = Muted)
    }
}

// ---------- S02 ----------

@Composable
private fun CompareScreen(state: ParkingUiState, actions: ParkingActions) {
    val items = state.page.items
    val enabled = state.controlsEnabled
    Frame(
        title = "주변 주차장 ${items.size}곳을 비교하세요",
        subtitle = "${state.destination?.name.orEmpty()}까지 직선 거리 기준 · 반경 ${state.conditions.radiusM}m · 조건 변경 가능",
        bottom = {
            ActionButton("정렬  ${sortLabel(state.conditions.sort)}", actions::cycleSort, Modifier.weight(1.3f), enabled = enabled)
            ActionButton("음성으로 선택", actions::toggleVoice, Modifier.weight(1f), enabled = enabled)
            if (state.page.count > 1) {
                ActionButton("이전", actions::previousPage, Modifier.weight(0.6f), enabled = enabled && state.page.index > 0)
                ActionButton("다음 ${state.page.index + 1}/${state.page.count}", actions::nextPage, Modifier.weight(0.8f), enabled = enabled && state.page.index < state.page.count - 1)
            }
            Spacer(Modifier.weight(0.3f))
            ActionButton("선택한 주차장 확인", actions::confirmSelection, Modifier.weight(1.4f), primary = true, enabled = enabled && state.selected != null)
        },
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1.7f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.conditions.filters.excludeMechanical) Chip("기계식 제외", highlighted = true)
                    Chip("차량 제한 위반 제외", highlighted = true)
                    Chip("조건 변경", onClick = actions::openConditions, enabled = enabled)
                }
                if (state.loading) Text("주차장을 조회하고 있어요…", fontSize = 18.sp, color = Muted)
                items.forEachIndexed { index, c ->
                    CandidateRow(index + 1, c, selected = c.parking.id == state.selectedId, minutes = state.conditions.parkingMinutes, enabled = enabled) {
                        actions.select(c.parking.id)
                    }
                }
                Text(receivedLine(items.firstOrNull { it.availability.receivedAt != null }?.availability, state.receivedAt), fontSize = 14.sp, color = Muted)
                state.notice?.let { Text(it, fontSize = 16.sp, color = Warn) }
            }
            Panel(Modifier.weight(1f)) {
                val s = state.selected
                if (s == null) {
                    Text("카드를 눌러 주차장을 고르세요", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("첫 조회에 자동으로 선택하지 않습니다. 번호로 음성 선택도 됩니다.", fontSize = 16.sp, color = Muted)
                } else {
                    Text(s.parking.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(availabilityLabel(s.availability), fontSize = 18.sp, color = availabilityColor(s.availability))
                    Text("${distanceLabel(s.distance)} · ${methodLabel(s.parking.method)} · ${categoryLabel(s.parking.category)}", fontSize = 16.sp)
                    Text(feeLabel(s.fee, state.conditions.parkingMinutes), fontSize = 16.sp)
                    Text(eligibilityLabel(s.eligibility), fontSize = 16.sp, color = eligibilityColor(s.eligibility))
                    s.parking.restrictions.forEach { Text(restrictionLine(it, state.profile), fontSize = 14.sp, color = Muted) }
                    s.link?.supportNote?.let { Text(it, fontSize = 14.sp, color = Muted) }
                }
                Text("개념 지도 없음 · 거리는 직선 거리", fontSize = 12.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun CandidateRow(number: Int, c: ParkingCandidate, selected: Boolean, minutes: Int, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardColor,
        border = if (selected) BorderStroke(2.dp, Accent) else null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$number", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.width(36.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.parking.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${availabilityLabel(c.availability)}  ·  ${distanceLabel(c.distance)}", fontSize = 18.sp, color = availabilityColor(c.availability))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${methodLabel(c.parking.method)}  |  ${feeLabel(c.fee, minutes)}", fontSize = 16.sp, color = Muted)
                Text(eligibilityLabel(c.eligibility), fontSize = 14.sp, color = eligibilityColor(c.eligibility))
            }
        }
    }
}

// ---------- S03 · S11 ----------

@Composable
private fun ConditionsScreen(state: ParkingUiState, actions: ParkingActions) {
    val draft = state.draftConditions
    val simple = state.moving
    val enabled = state.controlsEnabled
    fun filters(block: app.untilparking.domain.model.Filters.() -> app.untilparking.domain.model.Filters) =
        actions.updateDraftConditions(draft.copy(filters = draft.filters.block()))
    Frame(
        title = if (simple) "간단한 주차 조건" else "원하는 주차 조건을 선택하세요",
        subtitle = if (simple) "S11 · 주행 중 허용이 검증된 환경에서만 제공" else "정차 중 설정 화면 · 적용 전까지 기존 결과 유지",
        bottom = {
            if (!simple) ActionButton("초기화", actions::resetDraftConditions, Modifier.weight(0.8f))
            ActionButton("취소", actions::cancelConditions, Modifier.weight(0.8f))
            Spacer(Modifier.weight(0.5f))
            ActionButton(if (simple) "적용하고 비교" else "조건 적용하고 다시 보기", actions::applyConditions, Modifier.weight(1.6f), primary = true, enabled = enabled)
        },
    ) {
        if (simple) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.fillMaxWidth()) { SwitchRow("기계식 제외", draft.filters.excludeMechanical, enabled) { v -> filters { copy(excludeMechanical = v) } } }
                Panel(Modifier.fillMaxWidth()) { SwitchRow("방식 미확인 포함", draft.filters.includeUnknownMethod, enabled) { v -> filters { copy(includeUnknownMethod = v) } } }
                Panel(Modifier.fillMaxWidth()) { SwitchRow("잔여 정보 미제공 포함", draft.filters.includeUnknownAvailability, enabled) { v -> filters { copy(includeUnknownAvailability = v) } } }
            }
        } else {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Text("주차 방식", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    SwitchRow("기계식 제외", draft.filters.excludeMechanical, enabled) { v -> filters { copy(excludeMechanical = v) } }
                    SwitchRow("방식 미확인 포함", draft.filters.includeUnknownMethod, enabled) { v -> filters { copy(includeUnknownMethod = v) } }
                    Text("자주식은 기본 포함", fontSize = 16.sp, color = Muted)
                }
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Text("차량과 이용 조건", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("차량 제한 확인  고정", fontSize = 18.sp, color = Muted)
                    Text("높이 제한 초과  제외 고정", fontSize = 18.sp, color = Muted)
                    SwitchRow("잔여 대수 미제공 포함", draft.filters.includeUnknownAvailability, enabled) { v -> filters { copy(includeUnknownAvailability = v) } }
                }
                Panel(Modifier.weight(1f).fillMaxHeight()) {
                    Text("정렬과 비용", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    SortKey.entries.forEach { key ->
                        Chip(sortLabel(key), highlighted = draft.sort == key, enabled = enabled) { actions.updateDraftConditions(draft.copy(sort = key)) }
                    }
                    Text("예상 주차 시간", fontSize = 16.sp, color = Muted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 120, 180, 240).forEach { m ->
                            Chip(minutesLabel(m), highlighted = draft.parkingMinutes == m, enabled = enabled) { actions.updateDraftConditions(draft.copy(parkingMinutes = m)) }
                        }
                    }
                }
            }
            Text("확인된 차량 제한 위반은 제외하며, 미확인 정보 포함 여부만 변경할 수 있습니다.", fontSize = 16.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---------- S06 ----------

@Composable
private fun ConfirmScreen(state: ParkingUiState, actions: ParkingActions) {
    val recheck = state.recheck
    val candidate = recheck?.candidate ?: state.selected
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(recheck) {
        while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }
    val expired = recheck != null && !now.isBefore(recheck.expiresAt)
    val ready = recheck != null && (recheck.status == RecheckStatus.READY || (recheck.status == RecheckStatus.UNKNOWN && state.unknownAcknowledged))
    val canStart = recheck != null && !state.recheckLoading && state.routeState != RouteState.Pending && state.routeState != RouteState.Started && (expired || ready)
    Frame(
        title = "주차장으로 안내를 시작할까요",
        subtitle = "방문 목적지  ${state.destination?.name.orEmpty()} · 변경되는 차량 도착지  주차장 입구",
        bottom = {
            ActionButton("취소하고 비교", actions::cancelConfirm, Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
            ActionButton(
                text = if (expired) "다시 조회하고 확인" else "${candidate?.parking?.name.orEmpty()}으로 안내 시작",
                onClick = actions::startGuidance,
                modifier = Modifier.weight(1.6f),
                primary = true,
                enabled = canStart && state.controlsEnabled,
            )
        },
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Panel(Modifier.weight(2f).fillMaxHeight()) {
                Text(candidate?.parking?.name.orEmpty(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                when {
                    state.recheckLoading -> Text("최신 정보를 조회하고 있어요…", fontSize = 20.sp)
                    recheck == null -> Text("조회 전입니다.", fontSize = 20.sp)
                    else -> {
                        Text("최신 조회 결과를 확인한 뒤 시작하세요.", fontSize = 20.sp)
                        val a = recheck.candidate.availability
                        Text(
                            "${availabilityLabel(a)} · 수신 ${a.receivedAt?.hm() ?: now.hm()} · " + (a.sourceUpdatedAt?.let { "원본 갱신 ${it.hm()}" } ?: "원본 갱신 시각 미제공"),
                            fontSize = 18.sp,
                            color = availabilityColor(a),
                        )
                        if (recheck.status == RecheckStatus.UNKNOWN) {
                            Text("빈자리를 지금 확인할 수 없어요. 이전 정보로 계속할 수 있지만 차량 제한 위반은 우회할 수 없어요.", fontSize = 16.sp, color = Warn)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ActionButton("다시 조회", actions::recheck, Modifier.weight(1f))
                                ActionButton(
                                    if (state.unknownAcknowledged) "정보 미확인으로 계속 · 동의함" else "정보 미확인으로 계속",
                                    actions::acknowledgeUnknown,
                                    Modifier.weight(1.4f),
                                    primary = !state.unknownAcknowledged,
                                    enabled = !state.unknownAcknowledged,
                                )
                            }
                        }
                    }
                }
                Text("방문 목적지는 앱에 유지됩니다.", fontSize = 18.sp)
                Text("경로 변경이 완료되면 내비게이션에서 안내합니다.", fontSize = 16.sp, color = Muted)
                when (val r = state.routeState) {
                    RouteState.Idle -> Unit
                    RouteState.Pending -> Text("경로 변경을 요청했어요. 결과를 기다리는 중…", fontSize = 18.sp, color = Accent)
                    RouteState.Started -> Text("안내가 시작됐어요. 내비게이션 화면에서 이어집니다.", fontSize = 18.sp, color = Good)
                    is RouteState.Failed -> Text("경로 변경 실패: ${r.reason}. 선택은 그대로이니 다시 시도할 수 있어요.", fontSize = 18.sp, color = Danger)
                    RouteState.Unknown -> Text("결과를 확인하지 못했어요. 내비 목적지를 다시 확인합니다. 자동으로 다시 보내지 않아요.", fontSize = 18.sp, color = Warn)
                }
            }
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Text("최종 확인", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("음성  “안내 시작”", fontSize = 18.sp)
                Text("화면  아래 버튼 선택", fontSize = 18.sp)
                Text("남은 자리는 도착 전에 달라질 수 있습니다.", fontSize = 16.sp, color = Warn)
                if (recheck != null && !expired) {
                    val left = java.time.Duration.between(now, recheck.expiresAt).seconds.coerceAtLeast(0)
                    Text("확인 유효 ${left}초", fontSize = 14.sp, color = Muted)
                } else if (expired) {
                    Text("확인 시간이 지났어요. 다시 조회합니다.", fontSize = 14.sp, color = Muted)
                }
            }
        }
    }
}

// ---------- S07 ----------

@Composable
private fun AlternativesScreen(state: ParkingUiState, actions: ParkingActions) {
    val reason = state.alternativeReason
    val failed = state.failedCandidate
    val title = when (reason) {
        AlternativeReason.FULL -> "선택한 주차장이 만차로 바뀌었어요"
        AlternativeReason.BLOCKED -> "선택한 주차장은 차량 제한에 걸려요"
        AlternativeReason.EMPTY -> "조건에 맞는 주차장이 없어요"
        AlternativeReason.NETWORK -> "주차장 정보를 가져오지 못했어요"
        AlternativeReason.STALE -> "주차장 정보가 오래됐어요"
        AlternativeReason.INVALID_ENTRANCE -> "입구 좌표가 없어 안내할 수 없어요"
        null -> "다른 주차장을 선택하세요"
    }
    val alternatives = state.candidates.filter { it.parking.id != failed?.parking?.id }.take(5)
    Frame(
        title = title,
        subtitle = if (reason == AlternativeReason.EMPTY) "반경 ${state.conditions.radiusM}m · 검색 반경을 넓히거나 조건을 완화할 수 있어요" else "경로 변경 전 재조회 결과 · 다른 주차장을 선택할 수 있습니다",
        bottom = {
            ActionButton("조건 변경", actions::openConditions, Modifier.weight(1f), enabled = state.controlsEnabled)
            if (reason == AlternativeReason.EMPTY && state.conditions.radiusM < 2000) {
                ActionButton("검색 반경 넓히기", actions::expandRadius, Modifier.weight(1f), enabled = state.controlsEnabled)
            }
            Spacer(Modifier.weight(0.5f))
            ActionButton("목록 새로고침", actions::refreshList, Modifier.weight(1.4f), primary = true, enabled = state.controlsEnabled)
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (failed != null) {
                Surface(shape = RoundedCornerShape(16.dp), color = Warn.copy(alpha = 0.18f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${failed.parking.name}  ${if (reason == AlternativeReason.BLOCKED) eligibilityLabel(failed.eligibility) else availabilityLabel(failed.availability)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Warn, modifier = Modifier.weight(1f))
                        Text("현재 안내 요청을 중단했습니다.", fontSize = 18.sp)
                    }
                }
            }
            if (state.loading) Text("주차장을 조회하고 있어요…", fontSize = 18.sp, color = Muted)
            alternatives.forEach { c ->
                Surface(shape = RoundedCornerShape(16.dp), color = CardColor, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(c.parking.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("${distanceLabel(c.distance)} · ${availabilityLabel(c.availability)} · ${eligibilityLabel(c.eligibility)}", fontSize = 16.sp, color = Muted)
                        }
                        ActionButton("다시 선택", { actions.selectAndConfirm(c.parking.id) }, enabled = state.controlsEnabled)
                    }
                }
            }
            if (alternatives.isEmpty() && !state.loading) Text("표시할 다른 후보가 없어요.", fontSize = 18.sp, color = Muted)
            state.notice?.let { Text(it, fontSize = 16.sp, color = Warn) }
            Text("통신 실패나 오래된 정보는 만차와 구분하고, 마지막 수신 시각을 함께 표시합니다.", fontSize = 16.sp, color = Muted)
        }
    }
}

// ---------- S08 ----------

@Composable
private fun VehicleScreen(state: ParkingUiState, actions: ParkingActions) {
    Frame(
        title = "내 차의 크기와 조건을 확인하세요",
        subtitle = "정차 중 차량 프로필 · 자동 조회가 지원되지 않는 값은 직접 보완",
        bottom = {
            ActionButton("취소", actions::cancelVehicle, Modifier.weight(1f))
            ActionButton("입력값 초기화", actions::resetDraftVehicle, Modifier.weight(1f))
            Spacer(Modifier.weight(0.5f))
            ActionButton("차량 조건 저장", actions::saveVehicle, Modifier.weight(1.6f), primary = true, enabled = state.draftVehicleErrors.isEmpty() && state.parked)
        },
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Panel(Modifier.weight(1.3f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text("차량 크기", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                VehicleFieldRow(VehicleField.HEIGHT_MM, "높이", "mm", state, actions)
                VehicleFieldRow(VehicleField.WIDTH_MM, "폭", "mm", state, actions)
                VehicleFieldRow(VehicleField.LENGTH_MM, "길이", "mm", state, actions)
                VehicleFieldRow(VehicleField.WEIGHT_KG, "중량", "kg", state, actions)
                Text("폭은 사이드미러 제외 기준입니다. 빈 값은 미확인으로 저장됩니다.", fontSize = 14.sp, color = Muted)
            }
            Panel(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text("정보의 출처", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("높이 · 폭 · 길이 · 중량  사용자 입력", fontSize = 18.sp)
                Text("자동 조회를 지원하는 차량 값은 아직 없어요. 차대번호만으로 차종과 치수를 확정하지 않습니다.", fontSize = 14.sp, color = Muted)
                Text("루프박스", fontSize = 16.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("있음", state.draftRoofBox == RoofBox.YES) { actions.setDraftRoofBox(RoofBox.YES) }
                    Chip("없음", state.draftRoofBox == RoofBox.NO) { actions.setDraftRoofBox(RoofBox.NO) }
                    Chip("미확인", state.draftRoofBox == RoofBox.UNKNOWN) { actions.setDraftRoofBox(RoofBox.UNKNOWN) }
                }
                Text("제조사 기본 제원과 실제 높이가 다르면 실제 높이를 입력하세요. 장착물 높이를 자동으로 더하지 않습니다.", fontSize = 16.sp, color = Warn)
                Text("차종", fontSize = 16.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("SUV", state.draftSuv == SuvFlag.YES) { actions.setDraftSuv(SuvFlag.YES) }
                    Chip("기타", state.draftSuv == SuvFlag.NO) { actions.setDraftSuv(SuvFlag.NO) }
                    Chip("미확인", state.draftSuv == SuvFlag.UNKNOWN) { actions.setDraftSuv(SuvFlag.UNKNOWN) }
                }
            }
        }
    }
}

@Composable
private fun VehicleFieldRow(field: VehicleField, label: String, unit: String, state: ParkingUiState, actions: ParkingActions) {
    val error = state.draftVehicleErrors[field]
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontSize = 18.sp, color = Muted, modifier = Modifier.width(56.dp))
        OutlinedTextField(
            value = state.draftVehicleText[field].orEmpty(),
            onValueChange = { actions.updateDraftVehicle(field, it) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            suffix = { Text(unit, color = Muted) },
            placeholder = { Text("미입력", color = Muted) },
            isError = error != null,
            supportingText = error?.let { { Text(it, color = Danger) } },
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------- 프리뷰 ----------

private fun sampleCandidate(id: String, name: String, spaces: Int?, meters: Int, won: Int?, eligibility: EligibilityStatus): ParkingCandidate {
    val now = Instant.now()
    return ParkingCandidate(
        parking = ParkingFacility(id, name, GeoPoint(37.5, 127.0), GeoPoint(37.5, 127.0), ParkingCategory.OFF_STREET, ParkingMethod.SELF),
        eligibility = Eligibility(eligibility),
        distance = Distance(meters, DistanceKind.STRAIGHT),
        fee = FeeEstimate(won, now, if (won == null) FeeStatus.UNAVAILABLE else FeeStatus.ESTIMATED),
        availability = if (spaces == null) Availability(AvailabilityStatus.UNAVAILABLE) else Availability(AvailabilityStatus.RECEIVED, spaces, null, now),
    )
}

@Preview(widthDp = 1280, heightDp = 640)
@Composable
private fun ComparePreview() {
    val items = listOf(
        sampleCandidate("1", "서림 공영주차장", 12, 330, 3000, EligibilityStatus.VERIFIED_MATCH),
        sampleCandidate("2", "중앙 노상주차장", null, 447, 2400, EligibilityStatus.UNKNOWN),
        sampleCandidate("3", "문화광장 주차장", 3, 458, 1900, EligibilityStatus.UNKNOWN),
    )
    UntilParkingTheme {
        ParkingScreen(
            ParkingUiState(
                screen = Screen.COMPARE,
                driving = DrivingState.PARKED,
                destination = Destination("d", "서림문화센터", GeoPoint(37.5, 127.0), DestinationSource.NAVI),
                candidates = items,
                page = Page(items, 0, 1),
                selectedId = "1",
            ),
            NoParkingActions,
        )
    }
}
