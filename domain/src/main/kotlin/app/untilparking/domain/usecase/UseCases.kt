package app.untilparking.domain.usecase

import app.untilparking.domain.model.ArrivalPrompt
import app.untilparking.domain.model.Availability
import app.untilparking.domain.model.AvailabilityReading
import app.untilparking.domain.model.Congestion
import app.untilparking.domain.model.CongestionLevel
import app.untilparking.domain.model.CongestionReport
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationChange
import app.untilparking.domain.model.DestinationSearchResult
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.model.Eligibility
import app.untilparking.domain.model.FeeEstimate
import app.untilparking.domain.model.FeeModel
import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.Page
import app.untilparking.domain.model.ParkingCandidate
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.PendingArrival
import app.untilparking.domain.model.Recheck
import app.untilparking.domain.model.RouteRequestResult
import app.untilparking.domain.model.SearchConditions
import app.untilparking.domain.model.SearchResult
import app.untilparking.domain.model.VehicleField
import app.untilparking.domain.model.VehicleProfile
import app.untilparking.domain.model.VehicleValueInput
import app.untilparking.domain.model.VoiceCommand
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

// 화면(feature)은 이 인터페이스만 본다. 구현은 usecase 모듈, 배선은 app 모듈의 Hilt 모듈에 있다.
// 규칙의 정본은 docs/specs/parking-guidance.md.

/** R1 차량 제한 판정 */
fun interface JudgeEligibility {
    operator fun invoke(parking: ParkingFacility, profile: VehicleProfile, today: LocalDate): Eligibility
}

/** R4 잔여 대수 상태 */
fun interface JudgeAvailability {
    operator fun invoke(reading: AvailabilityReading?, now: Instant): Availability
}

/** R5 요금 */
fun interface EstimateFee {
    operator fun invoke(feeModel: FeeModel?, startAt: Instant, parkingMinutes: Int): FeeEstimate
}

/** R3 제외 순서와 정렬 */
fun interface SelectCandidates {
    operator fun invoke(candidates: List<ParkingCandidate>, conditions: SearchConditions): List<ParkingCandidate>
}

/** R2·R3·R6: 목적지 주변 후보와 연결 주차장을 만든다 */
fun interface SearchParking {
    suspend operator fun invoke(destination: Destination, conditions: SearchConditions, profile: VehicleProfile): SearchResult
}

/** R2 반경 확대: 500 → 1000 → 2000, 그 다음은 null */
fun interface ExpandRadius {
    operator fun invoke(currentRadiusM: Int): Int?
}

/** R7 최종 확인 전 재조회 */
fun interface RecheckParking {
    suspend operator fun invoke(
        candidate: ParkingCandidate,
        conditions: SearchConditions,
        profile: VehicleProfile,
        destination: Destination,
    ): Recheck
}

/** R8 차량 값 입력 검증 */
fun interface ValidateVehicleValue {
    operator fun invoke(field: VehicleField, text: String): VehicleValueInput
}

/** R10 음성 명령 해석 */
fun interface ParseVoiceCommand {
    operator fun invoke(utterance: String, visibleCount: Int): VoiceCommand
}

/** R7 목적지 변경 분류: 자체 경로 변경인가 새 방문인가 */
fun interface ClassifyDestinationChange {
    operator fun invoke(previous: Destination?, next: Destination?, plannedEntrance: GeoPoint?): DestinationChange
}

/** R2 5개씩 페이지 */
fun interface PageCandidates {
    operator fun invoke(items: List<ParkingCandidate>, page: Int): Page<ParkingCandidate>
}

fun interface RequestGuidance {
    suspend operator fun invoke(parking: ParkingFacility): RouteRequestResult
}

fun interface ObserveDestination {
    operator fun invoke(): Flow<Destination?>
}

fun interface RefreshDestination {
    operator fun invoke()
}

fun interface ObserveDrivingState {
    operator fun invoke(): Flow<DrivingState>
}

fun interface ObserveVehicleProfile {
    operator fun invoke(): Flow<VehicleProfile>
}

fun interface SaveVehicleProfile {
    suspend operator fun invoke(profile: VehicleProfile)
}

fun interface Speak {
    suspend operator fun invoke(text: String)
}

fun interface Listen {
    suspend operator fun invoke(): String?
}

/** 목적지 검색 스펙 D1: 들은 말을 키워드로 다듬어 내비 장소 검색을 연다. 묻기·듣기는 화면이 Speak·Listen으로 한다. */
fun interface OpenDestinationSearch {
    operator fun invoke(heard: String?, driving: DrivingState): DestinationSearchResult
}

/** 목적지 알림 스펙 N1: 앱이 화면에 없을 때 새 방문 목적지를 알림으로 알린다. change는 R7 분류 결과다. */
fun interface AlertNewDestination {
    operator fun invoke(change: DestinationChange, next: Destination?, appVisible: Boolean)
}

/** 목적지 알림 스펙 N1: 앱이 화면에 오면 떠 있는 알림을 지운다. */
fun interface DismissDestinationAlert {
    operator fun invoke()
}

/** 혼잡도 스펙 C4: 최근 2시간 입력의 평균. 입력이 없으면 null. */
fun interface SummarizeCongestion {
    operator fun invoke(reports: List<CongestionReport>, now: Instant): Congestion?
}

/** 혼잡도 스펙 C1: 내비 목적지가 없어진 것이 도착인가. guiding은 그 줄이 "안내 중"인 주차장, 없으면 null. change는 R7 분류 결과다. */
fun interface DetectArrival {
    operator fun invoke(change: DestinationChange, next: Destination?, guiding: ParkingFacility?, now: Instant): PendingArrival?
}

/** 혼잡도 스펙 C2: 도착 후보를 두고 지금 물을지. */
fun interface JudgeArrivalPrompt {
    operator fun invoke(pending: PendingArrival, driving: DrivingState, now: Instant): ArrivalPrompt
}

/** 혼잡도 스펙 C3: 고른 값을 지금 시각으로 저장한다. */
fun interface ReportCongestion {
    suspend operator fun invoke(parkingId: String, level: CongestionLevel)
}
