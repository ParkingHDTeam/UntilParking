package app.untilparking.feature.parking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.untilparking.domain.model.AlternativeReason
import app.untilparking.domain.model.Availability
import app.untilparking.domain.model.AvailabilityStatus
import app.untilparking.domain.model.Congestion
import app.untilparking.domain.model.CongestionLevel
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
import app.untilparking.domain.model.PendingArrival
import app.untilparking.domain.model.Restriction
import app.untilparking.domain.model.RestrictionField
import app.untilparking.domain.model.RoofBox
import app.untilparking.domain.model.SearchConditions
import app.untilparking.domain.model.SortKey
import app.untilparking.domain.model.SuvFlag
import app.untilparking.domain.model.VehicleField
import app.untilparking.domain.model.VehicleProfile
import java.time.Instant
import java.time.format.DateTimeFormatter

// 밤 운전을 기준으로 한 어두운 바탕에 강조색 하나. 색은 상태(남은 자리·안내 진행·경고)에만 쓴다(2026-11-12 디자인 검증).
private val Bg = Color(0xFF0E1620)
private val Raised = Color(0xFF18222E)
private val Line = Color(0xFF243142)
private val Ink = Color(0xFFEDF2F7)
private val Muted = Color(0xFF94A3B3)
private val Accent = Color(0xFF5AC8FA)
private val OnAccent = Color(0xFF07131C)
private val Good = Color(0xFF4ADE80)
private val Warn = Color(0xFFF5B74E)
private val Danger = Color(0xFFFF8A80)
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 글자는 네 단계(28·24·20·16sp). 팔 길이 거리에서 읽을 수 있게 가장 작은 글자도 16sp다. */
private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
)

private val ButtonShape = RoundedCornerShape(12.dp)
private val Gutter = 32.dp

/** 비교 표의 열 폭. 이름 열이 나머지를 쓴다. */
private val NumberCol = 56.dp
private val SpacesCol = 150.dp
private val CongestionCol = 140.dp
private val DistanceCol = 140.dp
private val FeeCol = 170.dp
private val ActionCol = 150.dp

/** 줄마다 끝이 맞도록 숫자는 같은 폭으로 그린다. */
private val Figure: TextStyle
    @Composable get() = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")

@Composable
fun UntilParkingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = Bg,
            surfaceVariant = Raised,
            primary = Accent,
            onPrimary = OnAccent,
            secondary = Muted,
            onSurface = Ink,
            onBackground = Ink,
            onSurfaceVariant = Muted,
            outline = Line,
            error = Danger,
        ),
        typography = AppTypography,
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
    Surface(modifier = modifier.fillMaxSize(), color = Bg, contentColor = Ink) {
        Column(Modifier.fillMaxSize()) {
            Header(titleOf(state), state, actions) {
                if (state.screen == Screen.COMPARE) HeaderButton("조건 변경", actions::openConditions, enabled = state.controlsEnabled)
            }
            VoiceBanner(state.voice)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.screen) {
                    // 도착 후보를 묻는 동안에는 S00 자리에 S13이 보인다(혼잡도 스펙 C2)
                    Screen.DESTINATION_WAIT -> state.arrivalToReport?.let { ArrivalReportScreen(it, actions) } ?: DestinationWaitScreen(state, actions)
                    Screen.DESTINATION_PARKING -> DestinationParkingScreen(state, actions)
                    Screen.COMPARE -> CompareScreen(state, actions)
                    Screen.CONDITIONS -> ConditionsScreen(state, actions)
                    Screen.ALTERNATIVES -> AlternativesScreen(state, actions)
                    Screen.VEHICLE -> VehicleScreen(state, actions)
                }
            }
        }
    }
}

private fun titleOf(s: ParkingUiState): String {
    val destination = s.destination?.name?.takeIf { it.isNotBlank() }
    return when (s.screen) {
        Screen.DESTINATION_WAIT -> if (s.arrivalToReport != null) "도착한 주차장은 지금 어떤가요?" else "주차까지"
        Screen.DESTINATION_PARKING -> destination?.let { "$it 주차 정보" } ?: "목적지 주차 정보"
        Screen.COMPARE -> destination?.let { "$it 주변 주차장" } ?: "주변 주차장"
        Screen.CONDITIONS -> if (s.moving) "간단한 주차 조건" else "주차 조건"
        Screen.ALTERNATIVES -> when (s.alternativeReason) {
            AlternativeReason.FULL -> "고른 주차장이 만차로 바뀌었어요"
            AlternativeReason.BLOCKED -> "고른 주차장에는 내 차가 들어갈 수 없어요"
            AlternativeReason.EMPTY -> "조건에 맞는 주차장이 없어요"
            AlternativeReason.NETWORK -> "주차장 정보를 가져오지 못했어요"
            AlternativeReason.STALE -> "주차장 정보가 오래됐어요"
            AlternativeReason.INVALID_ENTRANCE -> "입구 위치가 없어 안내할 수 없어요"
            null -> "다른 주차장을 고르세요"
        }
        Screen.VEHICLE -> "내 차 정보"
    }
}

// ---------- 공통 ----------

/** 모든 화면의 머리: 지금 화면이 무엇인지 + 음성 + 내 차 설정(정차 중에만, 기획서 14절). 시계는 시스템 상태 표시줄에 있다. */
@Composable
private fun Header(title: String, state: ParkingUiState, actions: ParkingActions, extra: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        extra()
        HeaderButton(
            text = when (state.voice) {
                VoiceState.IDLE -> "음성"
                VoiceState.LISTENING -> "듣는 중 · 멈춤"
                VoiceState.PROCESSING -> "알아듣는 중"
                VoiceState.SPEAKING -> "말하는 중"
            },
            onClick = actions::toggleVoice,
            enabled = state.controlsEnabled,
            active = state.voice != VoiceState.IDLE,
        )
        HeaderButton("내 차 설정", actions::openVehicle, enabled = state.parked && state.screen != Screen.VEHICLE)
    }
    HorizontalDivider(color = Line)
}

@Composable
private fun VoiceBanner(voice: VoiceState) {
    if (voice == VoiceState.IDLE) return
    Text(
        text = when (voice) {
            VoiceState.LISTENING -> "듣고 있어요. “두 번째”, “기계식 빼줘”, “거리순”처럼 말씀하세요. 화면을 누르면 멈춰요."
            VoiceState.PROCESSING -> "말씀을 알아듣고 있어요"
            VoiceState.SPEAKING -> "안내를 읽고 있어요"
            VoiceState.IDLE -> ""
        },
        style = MaterialTheme.typography.bodyLarge,
        color = Accent,
        modifier = Modifier.fillMaxWidth().background(Accent.copy(alpha = 0.12f)).padding(horizontal = Gutter, vertical = 12.dp),
    )
}

@Composable
private fun Frame(bottom: (@Composable RowScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = Gutter, vertical = 20.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        if (bottom != null) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = bottom)
        }
    }
}

/** 터치 영역 최소 64dp, 문구 최대 2줄, 말줄임 없음(기획서 14절). */
@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent, disabledContainerColor = Raised, disabledContentColor = Muted),
        contentPadding = PaddingValues(horizontal = 32.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center) }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Raised, contentColor = Ink, disabledContainerColor = Raised.copy(alpha = 0.5f), disabledContentColor = Muted),
        contentPadding = PaddingValues(horizontal = 28.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center) }
}

@Composable
private fun HeaderButton(text: String, onClick: () -> Unit, enabled: Boolean = true, active: Boolean = false) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (active) Accent.copy(alpha = 0.2f) else Raised,
            contentColor = if (active) Accent else Ink,
            disabledContainerColor = Raised.copy(alpha = 0.5f),
            disabledContentColor = Muted,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = Modifier.heightIn(min = 56.dp),
    ) { Text(text, style = MaterialTheme.typography.bodyLarge) }
}

@Composable
private fun Note(text: String, color: Color = Warn, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = modifier)
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        HorizontalDivider(color = Line)
        content()
    }
}

/** 줄 전체를 눌러 켜고 끈다. 화면 읽기에는 스위치로 읽힌다. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Ink else Muted, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = Accent, uncheckedThumbColor = Muted, uncheckedTrackColor = Raised, uncheckedBorderColor = Line),
        )
    }
    HorizontalDivider(color = Line)
}

/** 하나만 고르는 선택(정렬·주차 시간·루프박스·차종). */
@Composable
private fun <T> Choice(options: List<T>, selected: T, label: (T) -> String, enabled: Boolean = true, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                enabled = enabled,
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Accent.copy(alpha = 0.18f),
                    activeContentColor = Accent,
                    activeBorderColor = Accent,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = Ink,
                    inactiveBorderColor = Line,
                ),
                modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 120.dp),
            ) { Text(label(option), style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false) }
        }
    }
}

private fun operatingLine(c: ParkingCandidate, minutes: Int): String? = when (c.operating) {
    OperatingStatus.CLOSED -> c.parking.operatingSchedule?.let { "운영 시간 밖 · ${it.open.format(CLOCK)}~${it.close.format(CLOCK)} (도착 후 ${minutesLabel(minutes)} 기준)" } ?: "운영 시간 밖"
    OperatingStatus.UNKNOWN -> "운영 시간 미확인"
    OperatingStatus.OPEN -> null
}

private fun restrictionLine(r: Restriction, profile: VehicleProfile): String {
    fun m(mm: Int) = "%.1fm".format(mm / 1000.0)
    return when (r.field) {
        RestrictionField.HEIGHT_MM -> "높이 제한 ${m(r.value)}" + (profile.heightMm?.let { " · 내 차 ${m(it)}" } ?: " · 내 차 높이 미입력")
        RestrictionField.WIDTH_MM -> "폭 제한 ${m(r.value)}" + (profile.widthMm?.let { " · 내 차 ${m(it)}" } ?: " · 내 차 폭 미입력")
        RestrictionField.LENGTH_MM -> "길이 제한 ${m(r.value)}" + (profile.lengthMm?.let { " · 내 차 ${m(it)}" } ?: "")
        RestrictionField.WEIGHT_KG -> "중량 제한 ${"%,d".format(r.value)}kg" + (profile.weightKg?.let { " · 내 차 ${"%,d".format(it)}kg" } ?: " · 내 차 중량 미입력")
        RestrictionField.SUV -> "SUV 입차 불가"
    }
}

private fun conditionSummary(c: SearchConditions): String = buildList {
    add("반경 ${c.radiusM}m")
    if (c.filters.excludeMechanical) add("기계식 제외")
    add("차량 제한 위반 제외")
    if (!c.filters.includeUnknownAvailability) add("잔여 정보 없는 곳 제외")
}.joinToString(" · ")

// ---------- 비교 표 (S02 · S07) ----------

private enum class RowStatus(val label: String) {
    IDLE("안내"),
    CHECKING("확인 중"),
    REQUESTING("요청 중"),
    GUIDING("안내 중"),
    FAILED("다시 시도"),
    UNANSWERED("확인 못 함"),
}

private fun rowStatus(s: ParkingUiState): RowStatus = when {
    s.recheckLoading -> RowStatus.CHECKING
    else -> when (s.routeState) {
        RouteState.Idle -> RowStatus.IDLE
        RouteState.Pending -> RowStatus.REQUESTING
        RouteState.Started -> RowStatus.GUIDING
        is RouteState.Failed -> RowStatus.FAILED
        RouteState.Unknown -> RowStatus.UNANSWERED
    }
}

@Composable
private fun CandidateTable(
    items: List<ParkingCandidate>,
    state: ParkingUiState,
    numbered: Boolean,
    onGuide: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (numbered) Spacer(Modifier.width(NumberCol))
            HeadCell("주차장", Modifier.weight(1f), TextAlign.Start)
            HeadCell("남은 자리", Modifier.width(SpacesCol))
            HeadCell("혼잡도", Modifier.width(CongestionCol))
            HeadCell("직선 거리", Modifier.width(DistanceCol))
            HeadCell("${minutesLabel(state.conditions.parkingMinutes)} 요금", Modifier.width(FeeCol))
            Spacer(Modifier.width(ActionCol + 16.dp))
        }
        HorizontalDivider(color = Line)
        // 다시 찾는 동안 목록은 그대로 두고 얇은 진행 줄만 보인다
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxSize(), color = Accent, trackColor = Color.Transparent)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (items.isEmpty() && state.loading) {
                repeat(3) {
                    SkeletonRow(numbered)
                    HorizontalDivider(color = Line)
                }
            }
            items.forEachIndexed { index, c ->
                CandidateRow(if (numbered) index + 1 else null, c, state, onGuide)
                HorizontalDivider(color = Line)
            }
        }
    }
}

@Composable
private fun HeadCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.End) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = align, maxLines = 1, modifier = modifier)
}

/** 줄 전체가 안내 버튼이다. 누르면 재조회 후 바로 경로를 바꾼다(스펙 R7). */
@Composable
private fun CandidateRow(number: Int?, c: ParkingCandidate, state: ParkingUiState, onGuide: (String) -> Unit) {
    val mine = c.parking.id == state.selectedId
    val status = if (mine) rowStatus(state) else RowStatus.IDLE
    val enabled = state.controlsEnabled && !state.guidanceBusy
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (mine) Accent.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(enabled = enabled, onClickLabel = "${c.parking.name}으로 안내", role = Role.Button) { onGuide(c.parking.id) }
            .semantics { stateDescription = status.label }
            .padding(vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (number != null) {
                Text("$number", style = Figure, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.width(NumberCol))
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.parking.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(metaLine(c), style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SpacesCell(c.availability, Modifier.width(SpacesCol))
            CongestionCell(c.congestion, Modifier.width(CongestionCol))
            DistanceCell(c.distance, Modifier.width(DistanceCol))
            FeeCell(c.fee, Modifier.width(FeeCol))
            Spacer(Modifier.width(16.dp))
            StatusPill(status, active = enabled || mine, modifier = Modifier.width(ActionCol))
        }
        val detail = when (val r = state.routeState) {
            is RouteState.Failed -> if (mine) "경로를 바꾸지 못했어요. ${r.reason}" to Danger else null
            RouteState.Unknown -> if (mine) "내비가 답하지 않았어요. 자동으로 다시 보내지 않아요." to Warn else null
            RouteState.Started -> if (mine) "내비 화면에서 안내가 이어져요" to Muted else null
            else -> null
        }
        if (detail != null) {
            Note(detail.first, detail.second, Modifier.padding(start = if (number != null) NumberCol else 12.dp, top = 6.dp))
        }
    }
}

private fun metaLine(c: ParkingCandidate): String = listOfNotNull(
    c.link?.let { if (it.type == LinkType.PARTNER) "목적지 제휴" else "목적지 부설" },
    methodLabel(c.parking.method),
    eligibilityLabel(c.eligibility),
    if (c.operating == OperatingStatus.UNKNOWN) "운영 시간 미확인" else null,
).joinToString(" · ")

/** 남은 자리를 아는 곳만 색을 쓴다. 모르면 "정보 미확인"(2026-11-12 사용자 결정). */
@Composable
private fun SpacesCell(a: Availability, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        when (a.status) {
            AvailabilityStatus.LIVE, AvailabilityStatus.RECEIVED -> {
                val spaces = a.availableSpaces ?: 0
                if (spaces > 0) Text("${spaces}대", style = Figure, color = Good) else Text("만차", style = Figure, color = Warn)
            }
            AvailabilityStatus.STALE -> {
                Text("정보 미확인", style = MaterialTheme.typography.bodyLarge, color = Muted)
                a.availableSpaces?.let { Text("이전 ${it}대", style = MaterialTheme.typography.bodyMedium, color = Muted) }
            }
            AvailabilityStatus.UNAVAILABLE, AvailabilityStatus.ERROR -> Text("정보 미확인", style = MaterialTheme.typography.bodyLarge, color = Muted)
        }
    }
}

private fun congestionColor(level: CongestionLevel): Color = when (level) {
    CongestionLevel.SMOOTH -> Good
    CongestionLevel.NORMAL -> Ink
    CongestionLevel.CONGESTED -> Warn
}

/** 운전자들이 최근에 남긴 혼잡도의 평균과 입력 수. 없으면 "정보 없음"(혼잡도 스펙 C4). */
@Composable
private fun CongestionCell(c: Congestion?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        if (c == null) {
            Text("정보 없음", style = MaterialTheme.typography.bodyLarge, color = Muted)
        } else {
            Text(congestionLabel(c.level), style = MaterialTheme.typography.titleLarge, color = congestionColor(c.level))
            Text("최근 ${c.reportCount}명", style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}

@Composable
private fun DistanceCell(d: Distance, modifier: Modifier) {
    when (d.kind) {
        DistanceKind.STRAIGHT -> Text(metersLabel(d.meters), style = Figure, textAlign = TextAlign.End, modifier = modifier)
        DistanceKind.WALK -> Text("도보 ${d.minutes}분", style = Figure, textAlign = TextAlign.End, modifier = modifier)
        DistanceKind.UNKNOWN -> Text("미확인", style = MaterialTheme.typography.bodyLarge, color = Muted, textAlign = TextAlign.End, modifier = modifier)
    }
}

private fun metersLabel(meters: Int?): String = when {
    meters == null -> "미확인"
    meters >= 1000 -> "%.1fkm".format(meters / 1000.0)
    else -> "${meters}m"
}

@Composable
private fun FeeCell(fee: FeeEstimate, modifier: Modifier) {
    when (val won = fee.won) {
        null -> Text("정보 없음", style = MaterialTheme.typography.bodyLarge, color = Muted, textAlign = TextAlign.End, modifier = modifier)
        0 -> Text("무료", style = Figure, textAlign = TextAlign.End, modifier = modifier)
        else -> Text("%,d원".format(won), style = Figure, textAlign = TextAlign.End, modifier = modifier)
    }
}

@Composable
private fun StatusPill(status: RowStatus, active: Boolean, modifier: Modifier) {
    val (content, container, border) = when (status) {
        RowStatus.IDLE -> Triple(if (active) Accent else Muted, Color.Transparent, if (active) Accent else Line)
        RowStatus.CHECKING, RowStatus.REQUESTING -> Triple(Ink, Raised, Line)
        RowStatus.GUIDING -> Triple(Good, Good.copy(alpha = 0.14f), Good.copy(alpha = 0.6f))
        RowStatus.FAILED -> Triple(Danger, Color.Transparent, Danger)
        RowStatus.UNANSWERED -> Triple(Warn, Color.Transparent, Warn)
    }
    Surface(shape = ButtonShape, color = container, border = BorderStroke(1.dp, border), modifier = modifier.heightIn(min = 56.dp)) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            if (status == RowStatus.CHECKING || status == RowStatus.REQUESTING) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(status.label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}

@Composable
private fun SkeletonRow(numbered: Boolean) {
    @Composable
    fun Bar(width: Dp, height: Dp) = Box(Modifier.width(width).height(height).background(Raised, RoundedCornerShape(6.dp)))
    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        if (numbered) Spacer(Modifier.width(NumberCol)) else Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Bar(260.dp, 24.dp)
            Bar(180.dp, 16.dp)
        }
        Box(Modifier.width(SpacesCol), contentAlignment = Alignment.CenterEnd) { Bar(72.dp, 24.dp) }
        Box(Modifier.width(CongestionCol), contentAlignment = Alignment.CenterEnd) { Bar(72.dp, 24.dp) }
        Box(Modifier.width(DistanceCol), contentAlignment = Alignment.CenterEnd) { Bar(72.dp, 24.dp) }
        Box(Modifier.width(FeeCol), contentAlignment = Alignment.CenterEnd) { Bar(88.dp, 24.dp) }
        Spacer(Modifier.width(ActionCol + 16.dp))
    }
}

// ---------- S00 ----------

@Composable
private fun DestinationWaitScreen(state: ParkingUiState, actions: ParkingActions) {
    val listening = state.voice != VoiceState.IDLE
    Frame {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Column(Modifier.widthIn(max = 880.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("내비에서 목적지를 정하면 주변 주차장을 바로 보여 드려요", style = MaterialTheme.typography.headlineSmall)
                Text("안내를 시작하면 이 화면이 주차장 목록으로 바뀝니다.", style = MaterialTheme.typography.bodyLarge, color = Muted)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("목적지 다시 확인", actions::refreshDestination, enabled = state.controlsEnabled)
                    // 정차 중에만 검색한다(목적지 검색 스펙 D1, R9)
                    SecondaryButton(if (listening) "듣고 있어요" else "목적지 직접 검색", actions::searchDestination, enabled = state.parked && !listening)
                }
                if (!state.parked && state.driving != DrivingState.UNKNOWN) Note("목적지 직접 검색은 정차 중에만 할 수 있어요", Muted)
                if (state.driving == DrivingState.UNKNOWN) Note("주행 상태를 확인할 수 없어 지금은 보기만 할 수 있어요")
                state.notice?.let { Note(it) }
            }
        }
    }
}

// ---------- S13 (S00에서 도착 후보를 물을 때) ----------

/** 안내받은 주차장에 도착해 정차하면 혼잡도를 묻는다. 누르면 바로 저장하고 닫는다(혼잡도 스펙 C3). */
@Composable
private fun ArrivalReportScreen(arrival: PendingArrival, actions: ParkingActions) {
    Frame(
        bottom = {
            Spacer(Modifier.weight(1f))
            SecondaryButton("건너뛰기", actions::skipCongestionReport)
        },
    ) {
        Text(arrival.parkingName, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            "고른 값은 이 주차장의 최근 혼잡도에 반영돼요.",
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // 사용자가 말한 순서: 혼잡, 보통, 원활
            CongestionLevel.entries.reversed().forEach { level ->
                Surface(
                    onClick = { actions.reportCongestion(level) },
                    shape = ButtonShape,
                    color = Raised,
                    border = BorderStroke(1.dp, congestionColor(level).copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f).heightIn(min = 200.dp),
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(congestionLabel(level), style = MaterialTheme.typography.headlineSmall, color = congestionColor(level))
                        Text(congestionHint(level), style = MaterialTheme.typography.bodyLarge, color = Muted, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// ---------- S01 (지금은 들어오는 길이 없다, 스펙 R2) ----------

@Composable
private fun DestinationParkingScreen(state: ParkingUiState, actions: ParkingActions) {
    val first = state.linked.firstOrNull()
    Frame(
        bottom = {
            SecondaryButton("주변 주차장 비교", actions::openCompare, enabled = state.controlsEnabled)
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                "이 주차장으로 안내",
                { first?.let { actions.guideTo(it.parking.id) } },
                enabled = first != null && state.controlsEnabled && !state.guidanceBusy,
            )
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.loading && state.linked.isEmpty() -> Text("목적지 주차 정보를 찾고 있어요", style = MaterialTheme.typography.bodyLarge, color = Muted)
                state.linked.isEmpty() -> {
                    Text("목적지에 연결된 주차장 정보가 없어요", style = MaterialTheme.typography.titleLarge)
                    Text("주변 주차장을 비교해서 고를 수 있어요.", style = MaterialTheme.typography.bodyLarge, color = Muted)
                }
                else -> state.linked.forEach { LinkedBlock(it, state) }
            }
            state.notice?.let { Note(it) }
            if (!state.controlsEnabled) Note("주행 상태를 확인할 수 없어 지금은 보기만 할 수 있어요")
        }
    }
}

@Composable
private fun LinkedBlock(c: ParkingCandidate, state: ParkingUiState) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(c.parking.name, style = MaterialTheme.typography.titleLarge)
        Text(metaLine(c), style = MaterialTheme.typography.bodyLarge, color = Muted)
        SpacesCell(c.availability, Modifier)
        c.parking.restrictions.forEach { Text(restrictionLine(it, state.profile), style = MaterialTheme.typography.bodyLarge) }
        c.link?.supportNote?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        operatingLine(c, state.conditions.parkingMinutes)?.let { Note(it) }
    }
    HorizontalDivider(color = Line)
}

// ---------- S02 ----------

@Composable
private fun CompareScreen(state: ParkingUiState, actions: ParkingActions) {
    val enabled = state.controlsEnabled
    Frame {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Choice(SortKey.entries, state.conditions.sort, ::sortShortLabel, enabled = enabled, onSelect = actions::sortBy)
            Text(conditionSummary(state.conditions), style = MaterialTheme.typography.bodyMedium, color = Muted, maxLines = 2, modifier = Modifier.weight(1f))
        }
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!enabled) Note("주행 상태를 확인할 수 없어 지금은 보기만 할 수 있어요")
            state.notice?.let { Note(it) }
        }
        CandidateTable(state.page.items, state, numbered = true, onGuide = actions::guideTo, modifier = Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                receivedLine(state.page.items.firstOrNull { it.availability.receivedAt != null }?.availability, state.receivedAt),
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                modifier = Modifier.weight(1f),
            )
            if (state.page.count > 1) {
                SecondaryButton("이전", actions::previousPage, enabled = enabled && state.page.index > 0)
                Text("${state.page.index + 1} / ${state.page.count}", style = Figure, color = Muted)
                SecondaryButton("다음", actions::nextPage, enabled = enabled && state.page.index < state.page.count - 1)
            }
        }
    }
}

// ---------- S03 · S11 ----------

@Composable
private fun ConditionsScreen(state: ParkingUiState, actions: ParkingActions) {
    val draft = state.draftConditions
    val enabled = state.controlsEnabled
    fun filters(block: app.untilparking.domain.model.Filters.() -> app.untilparking.domain.model.Filters) =
        actions.updateDraftConditions(draft.copy(filters = draft.filters.block()))
    if (state.moving) {
        // S11: 주행 중에는 스위치 셋만(기획서 S11)
        Frame(
            bottom = {
                SecondaryButton("취소", actions::cancelConditions)
                Spacer(Modifier.weight(1f))
                PrimaryButton("적용하고 다시 찾기", actions::applyConditions, enabled = enabled)
            },
        ) {
            Column(Modifier.widthIn(max = 880.dp)) {
                HorizontalDivider(color = Line)
                SwitchRow("기계식 제외", draft.filters.excludeMechanical, enabled) { v -> filters { copy(excludeMechanical = v) } }
                SwitchRow("방식을 모르는 곳 포함", draft.filters.includeUnknownMethod, enabled) { v -> filters { copy(includeUnknownMethod = v) } }
                SwitchRow("잔여 정보가 없는 곳 포함", draft.filters.includeUnknownAvailability, enabled) { v -> filters { copy(includeUnknownAvailability = v) } }
            }
        }
        return
    }
    Frame(
        bottom = {
            SecondaryButton("초기화", actions::resetDraftConditions)
            SecondaryButton("취소", actions::cancelConditions)
            Spacer(Modifier.weight(1f))
            PrimaryButton("적용하고 다시 찾기", actions::applyConditions, enabled = enabled)
        },
    ) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Section("주차 방식", Modifier.weight(1f)) {
                SwitchRow("기계식 제외", draft.filters.excludeMechanical, enabled) { v -> filters { copy(excludeMechanical = v) } }
                SwitchRow("방식을 모르는 곳 포함", draft.filters.includeUnknownMethod, enabled) { v -> filters { copy(includeUnknownMethod = v) } }
                Note("자주식은 항상 포함해요", Muted, Modifier.padding(top = 12.dp))
            }
            Section("남은 자리와 차량 제한", Modifier.weight(1f)) {
                SwitchRow("잔여 정보가 없는 곳 포함", draft.filters.includeUnknownAvailability, enabled) { v -> filters { copy(includeUnknownAvailability = v) } }
                Note("내 차가 들어갈 수 없는 곳은 항상 빼요", Muted, Modifier.padding(top = 12.dp))
            }
            Section("정렬과 주차 시간", Modifier.weight(1f)) {
                Spacer(Modifier.height(16.dp))
                Choice(SortKey.entries, draft.sort, ::sortShortLabel, enabled = enabled) { actions.updateDraftConditions(draft.copy(sort = it)) }
                Text("예상 주차 시간", style = MaterialTheme.typography.bodyLarge, color = Muted, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                Choice(listOf(30, 60, 120, 180, 240), draft.parkingMinutes, ::minutesLabel, enabled = enabled) { actions.updateDraftConditions(draft.copy(parkingMinutes = it)) }
                Note("요금은 이 시간으로 계산해요", Muted, Modifier.padding(top = 12.dp))
            }
        }
    }
}

// ---------- S07 ----------

@Composable
private fun AlternativesScreen(state: ParkingUiState, actions: ParkingActions) {
    val reason = state.alternativeReason
    val failed = state.failedCandidate
    val alternatives = state.candidates.filter { it.parking.id != failed?.parking?.id }.take(5)
    val guidance = when (reason) {
        AlternativeReason.EMPTY -> "반경 ${state.conditions.radiusM}m 안에서 찾았어요. 반경을 넓히거나 조건을 바꿔 보세요."
        AlternativeReason.NETWORK -> "통신이 되면 목록을 다시 찾아 주세요."
        AlternativeReason.STALE -> "최신 정보로 목록을 다시 찾아 주세요."
        AlternativeReason.INVALID_ENTRANCE -> "아래에서 다른 주차장을 골라 주세요."
        AlternativeReason.FULL, AlternativeReason.BLOCKED, null -> null
    }
    Frame(
        bottom = {
            SecondaryButton("조건 변경", actions::openConditions, enabled = state.controlsEnabled)
            if (reason == AlternativeReason.EMPTY && state.conditions.radiusM < 2000) {
                SecondaryButton("검색 반경 넓히기", actions::expandRadius, enabled = state.controlsEnabled)
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton("목록 다시 찾기", actions::refreshList, enabled = state.controlsEnabled)
        },
    ) {
        guidance?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Muted, modifier = Modifier.padding(bottom = 16.dp)) }
        if (failed != null) {
            Row(
                Modifier.fillMaxWidth().background(Warn.copy(alpha = 0.12f), ButtonShape).padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(failed.parking.name, style = MaterialTheme.typography.titleLarge)
                    Text("경로는 바꾸지 않았어요", style = MaterialTheme.typography.bodyMedium, color = Muted)
                }
                Text(
                    if (reason == AlternativeReason.BLOCKED) eligibilityLabel(failed.eligibility) else "만차",
                    style = MaterialTheme.typography.titleLarge,
                    color = Warn,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        state.notice?.let { Note(it, modifier = Modifier.padding(bottom = 12.dp)) }
        if (alternatives.isNotEmpty() || state.loading) {
            Text("다른 주차장", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            CandidateTable(alternatives, state, numbered = false, onGuide = actions::guideTo, modifier = Modifier.weight(1f))
        } else {
            Text("보여 드릴 다른 주차장이 없어요", style = MaterialTheme.typography.bodyLarge, color = Muted)
        }
    }
}

// ---------- S08 ----------

@Composable
private fun VehicleScreen(state: ParkingUiState, actions: ParkingActions) {
    Frame(
        bottom = {
            SecondaryButton("취소", actions::cancelVehicle)
            SecondaryButton("입력 지우기", actions::resetDraftVehicle)
            Spacer(Modifier.weight(1f))
            PrimaryButton("저장", actions::saveVehicle, enabled = state.draftVehicleErrors.isEmpty() && state.parked)
        },
    ) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            Section("크기", Modifier.weight(1.2f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VehicleFieldRow(VehicleField.HEIGHT_MM, "높이", "mm", state, actions)
                    VehicleFieldRow(VehicleField.WIDTH_MM, "폭", "mm", state, actions)
                    VehicleFieldRow(VehicleField.LENGTH_MM, "길이", "mm", state, actions)
                    VehicleFieldRow(VehicleField.WEIGHT_KG, "중량", "kg", state, actions)
                }
                Note("폭은 사이드미러를 뺀 값이에요. 비워 두면 모르는 값으로 저장해요.", Muted, Modifier.padding(top = 12.dp))
            }
            Section("차량 조건", Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("루프박스", style = MaterialTheme.typography.bodyLarge, color = Muted, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                Choice(listOf(RoofBox.YES, RoofBox.NO, RoofBox.UNKNOWN), state.draftRoofBox, { roofBoxLabel(it) }) { actions.setDraftRoofBox(it) }
                Note("루프박스를 달았다면 높이에 그만큼 더해서 입력하세요. 앱이 더해 주지 않아요.", Muted, Modifier.padding(top = 8.dp))
                Text("차종", style = MaterialTheme.typography.bodyLarge, color = Muted, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                Choice(listOf(SuvFlag.YES, SuvFlag.NO, SuvFlag.UNKNOWN), state.draftSuv, { suvLabel(it) }) { actions.setDraftSuv(it) }
                Note("SUV를 받지 않는 주차장을 거를 때 써요", Muted, Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun roofBoxLabel(r: RoofBox): String = when (r) {
    RoofBox.YES -> "있음"
    RoofBox.NO -> "없음"
    RoofBox.UNKNOWN -> "모름"
}

private fun suvLabel(s: SuvFlag): String = when (s) {
    SuvFlag.YES -> "SUV"
    SuvFlag.NO -> "그 밖"
    SuvFlag.UNKNOWN -> "모름"
}

@Composable
private fun VehicleFieldRow(field: VehicleField, label: String, unit: String, state: ParkingUiState, actions: ParkingActions) {
    val error = state.draftVehicleErrors[field]
    OutlinedTextField(
        value = state.draftVehicleText[field].orEmpty(),
        onValueChange = { actions.updateDraftVehicle(field, it) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        suffix = { Text(unit, color = Muted) },
        placeholder = { Text("모름", color = Muted) },
        isError = error != null,
        supportingText = error?.let { { Text(it, color = Danger) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = Line,
            focusedLabelColor = Accent,
            unfocusedLabelColor = Muted,
            cursorColor = Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
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

@Preview(widthDp = 1600, heightDp = 800)
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
                routeState = RouteState.Started,
            ),
            NoParkingActions,
        )
    }
}
