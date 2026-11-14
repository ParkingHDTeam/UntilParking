package app.untilparking.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class ParkingCategory { ON_STREET, OFF_STREET, ATTACHED, UNKNOWN }

enum class ParkingMethod { SELF, MECHANICAL, MIXED, UNKNOWN }

enum class RestrictionField { HEIGHT_MM, WIDTH_MM, LENGTH_MM, WEIGHT_KG, SUV }

/** 제한 문구의 연산자. "이하"는 LTE, "미만"은 LT. 원본대로 보존한다(스펙 R1). */
enum class RestrictionOperator { LTE, LT }

enum class WeightBasis { CURB, GROSS, ACTUAL, UNKNOWN }

enum class MirrorBasis { EXCLUDING, INCLUDING, UNKNOWN }

data class Restriction(
    val field: RestrictionField,
    val operator: RestrictionOperator = RestrictionOperator.LTE,
    /** SUV 제한이면 쓰지 않는다. 단위는 필드 이름대로 mm·kg. */
    val value: Int = 0,
    val weightBasis: WeightBasis = WeightBasis.UNKNOWN,
    val mirrorBasis: MirrorBasis = MirrorBasis.UNKNOWN,
    val sourceUrl: String? = null,
    val verifiedAt: LocalDate? = null,
    /** 운영기관과 공공 출처의 값이 다르다. */
    val conflicting: Boolean = false,
)

/** 검증된 단일 일자 요금제만 담는다. 복잡한 요금제는 시설의 feeModel을 null(산출 불가)로 둔다. */
data class FeeModel(
    val baseMinutes: Int,
    val baseWon: Int,
    val stepMinutes: Int,
    val stepWon: Int,
    val maxDailyWon: Int? = null,
)

data class OperatingSchedule(
    val open: LocalTime,
    val close: LocalTime,
    val allDay: Boolean = false,
    /** 그 요일은 운영하지 않는다(공공 데이터 스펙 P2). open·close는 보지 않는다. */
    val closed: Boolean = false,
) {
    companion object {
        val CLOSED_ALL_DAY = OperatingSchedule(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, closed = true)
    }
}

data class ParkingFacility(
    val id: String,
    val name: String,
    val location: GeoPoint,
    /** 입구 좌표. 없으면 안내(경로 변경)를 할 수 없다. */
    val entrance: GeoPoint?,
    val category: ParkingCategory,
    val method: ParkingMethod,
    val address: String? = null,
    val operator: String? = null,
    val restrictions: List<Restriction> = emptyList(),
    val feeModel: FeeModel? = null,
    /** 평일(월~금) 운영 시간. 토요일·일요일 값을 따로 주지 않으면 매일 이 값이다. */
    val operatingSchedule: OperatingSchedule? = null,
    /** MIXED일 때 자주식 구역의 별도 입구·이용 조건이 확인됐나(스펙 R3). */
    val selfZoneVerified: Boolean = false,
    val saturdaySchedule: OperatingSchedule? = operatingSchedule,
    /** 일요일에 쓰는 공휴일 운영 시간. 법정 공휴일 달력은 보지 않는다(공공 데이터 스펙 P3). */
    val holidaySchedule: OperatingSchedule? = operatingSchedule,
)

/** 그 요일의 운영 시간. null이면 모른다. */
fun ParkingFacility.scheduleOn(day: DayOfWeek): OperatingSchedule? = when (day) {
    DayOfWeek.SATURDAY -> saturdaySchedule
    DayOfWeek.SUNDAY -> holidaySchedule
    else -> operatingSchedule
}

/** 데이터 출처가 준 잔여 대수 원본. 판정 전. */
data class AvailabilityReading(
    val availableSpaces: Int?,
    val sourceUpdatedAt: Instant?,
    val receivedAt: Instant,
)

enum class AvailabilityStatus { LIVE, RECEIVED, STALE, UNAVAILABLE, ERROR }

data class Availability(
    val status: AvailabilityStatus,
    val availableSpaces: Int? = null,
    val sourceUpdatedAt: Instant? = null,
    val receivedAt: Instant? = null,
) {
    /** 신선한 만차: 0대이고 LIVE·RECEIVED(스펙 R3). 오래된 0대는 만차로 보지 않는다. */
    val isFreshFull: Boolean
        get() = availableSpaces == 0 && (status == AvailabilityStatus.LIVE || status == AvailabilityStatus.RECEIVED)
}

enum class LinkType { OWN, PARTNER }

/** 목적지와 주차장의 검증된 관계(부설·제휴). 이름·거리만으로 만들지 않는다(스펙 R1). */
data class DestinationParkingLink(
    val parkingId: String,
    val type: LinkType,
    val supportNote: String? = null,
    val verifiedAt: LocalDate? = null,
)

enum class EligibilityStatus { VERIFIED_MATCH, UNKNOWN, BLOCKED }

data class Eligibility(val status: EligibilityStatus, val reasons: List<String> = emptyList())

enum class DistanceKind { WALK, STRAIGHT, UNKNOWN }

data class Distance(val meters: Int?, val kind: DistanceKind, val minutes: Int? = null)

enum class FeeStatus { ESTIMATED, UNAVAILABLE }

data class FeeEstimate(val won: Int?, val basisAt: Instant, val status: FeeStatus)

enum class OperatingStatus { OPEN, CLOSED, UNKNOWN }

data class ParkingCandidate(
    val parking: ParkingFacility,
    val eligibility: Eligibility,
    val distance: Distance,
    val fee: FeeEstimate,
    val availability: Availability,
    val link: DestinationParkingLink? = null,
    val operating: OperatingStatus = OperatingStatus.UNKNOWN,
    /** 최근 입력의 평균 혼잡도. 입력이 없거나 읽지 못했으면 null(혼잡도 스펙 C4). */
    val congestion: Congestion? = null,
)

enum class RecheckStatus { READY, FULL, BLOCKED, UNKNOWN }

data class Recheck(
    val snapshotId: String,
    val candidate: ParkingCandidate,
    val status: RecheckStatus,
    /** 이 시각이 지나면 다시 조회하고 다시 확인받는다(스펙 R7). */
    val expiresAt: Instant,
)

data class SearchResult(
    val candidates: List<ParkingCandidate>,
    val radiusM: Int,
    /** 목적지에 연결된 주차장(S01). 반경과 무관하게 전부. */
    val linked: List<ParkingCandidate>,
)

data class Page<T>(val items: List<T>, val index: Int, val count: Int)
