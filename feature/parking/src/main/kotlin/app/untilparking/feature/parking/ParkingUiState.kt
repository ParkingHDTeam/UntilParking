package app.untilparking.feature.parking

import app.untilparking.domain.model.AlternativeReason
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.model.Page
import app.untilparking.domain.model.ParkingCandidate
import app.untilparking.domain.model.PendingArrival
import app.untilparking.domain.model.Recheck
import app.untilparking.domain.model.RoofBox
import app.untilparking.domain.model.SearchConditions
import app.untilparking.domain.model.SuvFlag
import app.untilparking.domain.model.VehicleField
import app.untilparking.domain.model.VehicleProfile
import java.time.Instant

/** 기획서 화면 ID. S04·S09·S10·S12·S14는 아직 없다(README). S11은 CONDITIONS의 주행 중 모습, S13 혼잡도 선택은 DESTINATION_WAIT에서 도착 후보를 물을 때의 모습이다. */
enum class Screen {
    DESTINATION_WAIT, // S00
    DESTINATION_PARKING, // S01
    COMPARE, // S02
    CONDITIONS, // S03 · S11
    // S06 최종 확인은 2026-11-12에 없앴다. S02 줄을 누르면 재조회 후 바로 안내한다(스펙 R7).
    ALTERNATIVES, // S07
    VEHICLE, // S08
}

enum class VoiceState { IDLE, LISTENING, PROCESSING, SPEAKING }

sealed interface RouteState {
    data object Idle : RouteState
    data object Pending : RouteState
    data object Started : RouteState
    data class Failed(val reason: String) : RouteState
    /** 15초 무응답. 목적지 재조회로 적용 여부를 본다. */
    data object Unknown : RouteState
}

/** 화면과 음성이 공유하는 단일 상태(기획서 25절). */
data class ParkingUiState(
    val screen: Screen = Screen.DESTINATION_WAIT,
    val driving: DrivingState = DrivingState.UNKNOWN,
    /** 방문 목적지. 차량 도착지를 주차장으로 바꿔도 유지한다. */
    val destination: Destination? = null,
    val conditions: SearchConditions = SearchConditions(),
    val draftConditions: SearchConditions = SearchConditions(),
    val profile: VehicleProfile = VehicleProfile(),
    val draftVehicleText: Map<VehicleField, String> = emptyMap(),
    val draftVehicleErrors: Map<VehicleField, String> = emptyMap(),
    val draftSuv: SuvFlag = SuvFlag.UNKNOWN,
    val draftRoofBox: RoofBox = RoofBox.UNKNOWN,
    val loading: Boolean = false,
    val linked: List<ParkingCandidate> = emptyList(),
    val candidates: List<ParkingCandidate> = emptyList(),
    val page: Page<ParkingCandidate> = Page(emptyList(), 0, 0),
    /** 운전자가 누른 주차장. 재조회·경로 요청·안내 중 표시가 이 줄에 붙는다. */
    val selectedId: String? = null,
    val recheck: Recheck? = null,
    val recheckLoading: Boolean = false,
    val routeState: RouteState = RouteState.Idle,
    val alternativeReason: AlternativeReason? = null,
    val failedCandidate: ParkingCandidate? = null,
    val voice: VoiceState = VoiceState.IDLE,
    val notice: String? = null,
    val receivedAt: Instant? = null,
    /** 도착 후보. 정차하면 혼잡도를 묻는다(혼잡도 스펙 C1·C2). */
    val pendingArrival: PendingArrival? = null,
    val askingCongestion: Boolean = false,
) {
    val selected: ParkingCandidate?
        get() = (candidates + linked).firstOrNull { it.parking.id == selectedId }

    /** 지금 혼잡도를 묻고 있는 도착. S00 자리에 S13이 보인다. */
    val arrivalToReport: PendingArrival? get() = pendingArrival.takeIf { askingCongestion }

    /** 재조회나 경로 요청이 진행 중이면 다른 줄을 눌러도 무시한다(스펙 R7, T08). */
    val guidanceBusy: Boolean get() = recheckLoading || routeState == RouteState.Pending

    /** 주행 상태를 모르면 조회 읽기만 한다(스펙 R9). */
    val controlsEnabled: Boolean get() = driving != DrivingState.UNKNOWN
    val parked: Boolean get() = driving == DrivingState.PARKED
    val moving: Boolean get() = driving == DrivingState.MOVING
}
