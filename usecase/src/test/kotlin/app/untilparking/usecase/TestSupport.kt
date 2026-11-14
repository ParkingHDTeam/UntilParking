package app.untilparking.usecase

import app.untilparking.domain.AppError
import app.untilparking.domain.model.Availability
import app.untilparking.domain.model.AvailabilityReading
import app.untilparking.domain.model.AvailabilityStatus
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationParkingLink
import app.untilparking.domain.model.DestinationSource
import app.untilparking.domain.model.Distance
import app.untilparking.domain.model.DistanceKind
import app.untilparking.domain.model.Eligibility
import app.untilparking.domain.model.EligibilityStatus
import app.untilparking.domain.model.FeeEstimate
import app.untilparking.domain.model.FeeModel
import app.untilparking.domain.model.FeeStatus
import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.MirrorBasis
import app.untilparking.domain.model.OperatingSchedule
import app.untilparking.domain.model.OperatingStatus
import app.untilparking.domain.model.ParkingCandidate
import app.untilparking.domain.model.ParkingCategory
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.ParkingMethod
import app.untilparking.domain.model.Restriction
import app.untilparking.domain.model.RestrictionField
import app.untilparking.domain.model.RestrictionOperator
import app.untilparking.domain.model.SuvFlag
import app.untilparking.domain.model.VehicleProfile
import app.untilparking.domain.model.WeightBasis
import app.untilparking.domain.model.distanceMetersTo
import app.untilparking.domain.repository.ParkingRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 2026-11-07(토) 14:32, 서울. 기획서 시안의 시각이다. */
val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
val NOW_LOCAL: LocalDateTime = LocalDateTime.of(2026, 11, 7, 14, 32)
val NOW: Instant = NOW_LOCAL.atZone(SEOUL).toInstant()
val TODAY: LocalDate = NOW_LOCAL.toLocalDate()
fun fixedClock(at: Instant = NOW): Clock = Clock.fixed(at, SEOUL)

/** 기획서 예시의 방문 목적지. 좌표는 설명용. */
val SEORIM = GeoPoint(37.5000, 127.0000)
val DEST = Destination("d1", "서림문화센터", SEORIM, DestinationSource.NAVI)

private const val EARTH_RADIUS_M = 6_371_000.0

/** 기준점에서 북쪽·동쪽으로 미터만큼 옮긴 지점. */
fun GeoPoint.offset(northMeters: Double, eastMeters: Double = 0.0): GeoPoint {
    val lat = latitude + Math.toDegrees(northMeters / EARTH_RADIUS_M)
    val lon = longitude + Math.toDegrees(eastMeters / (EARTH_RADIUS_M * Math.cos(Math.toRadians(latitude))))
    return GeoPoint(lat, lon)
}

fun restriction(
    field: RestrictionField,
    value: Int = 0,
    operator: RestrictionOperator = RestrictionOperator.LTE,
    verifiedAt: LocalDate? = TODAY.minusDays(1),
    weightBasis: WeightBasis = WeightBasis.UNKNOWN,
    mirrorBasis: MirrorBasis = MirrorBasis.UNKNOWN,
    conflicting: Boolean = false,
) = Restriction(field, operator, value, weightBasis, mirrorBasis, null, verifiedAt, conflicting)

fun facility(
    id: String,
    method: ParkingMethod = ParkingMethod.SELF,
    at: GeoPoint = SEORIM.offset(300.0),
    restrictions: List<Restriction> = listOf(restriction(RestrictionField.HEIGHT_MM, 2100)),
    fee: FeeModel? = FeeModel(30, 1500, 10, 500),
    schedule: OperatingSchedule? = null,
    category: ParkingCategory = ParkingCategory.OFF_STREET,
    entrance: GeoPoint? = at,
    selfZoneVerified: Boolean = false,
    saturday: OperatingSchedule? = schedule,
    holiday: OperatingSchedule? = schedule,
) = ParkingFacility(
    id = id, name = "주차장 $id", location = at, entrance = entrance, category = category, method = method,
    restrictions = restrictions, feeModel = fee, operatingSchedule = schedule, selfZoneVerified = selfZoneVerified,
    saturdaySchedule = saturday, holidaySchedule = holiday,
)

fun profile(
    height: Int? = 1800,
    width: Int? = 1900,
    length: Int? = 4800,
    weight: Int? = null,
    mirror: MirrorBasis = MirrorBasis.EXCLUDING,
    suv: SuvFlag = SuvFlag.UNKNOWN,
    weightBasis: WeightBasis = WeightBasis.UNKNOWN,
) = VehicleProfile(height, width, length, weight, weightBasis, mirror, suv)

/** 원본 시각·수신 시각을 "몇 분 전"으로 만든다. */
fun reading(spaces: Int?, sourceAgeMinutes: Long? = null, receivedAgeMinutes: Long = 0) = AvailabilityReading(
    availableSpaces = spaces,
    sourceUpdatedAt = sourceAgeMinutes?.let { NOW.minus(Duration.ofMinutes(it)) },
    receivedAt = NOW.minus(Duration.ofMinutes(receivedAgeMinutes)),
)

fun candidate(
    id: String,
    distance: Int? = 300,
    won: Int? = 3000,
    spaces: Int? = 5,
    status: AvailabilityStatus = AvailabilityStatus.LIVE,
    eligibility: EligibilityStatus = EligibilityStatus.VERIFIED_MATCH,
    method: ParkingMethod = ParkingMethod.SELF,
    operating: OperatingStatus = OperatingStatus.OPEN,
    selfZoneVerified: Boolean = false,
) = ParkingCandidate(
    parking = facility(id, method = method, selfZoneVerified = selfZoneVerified),
    eligibility = Eligibility(eligibility),
    distance = Distance(distance, if (distance == null) DistanceKind.UNKNOWN else DistanceKind.STRAIGHT),
    fee = FeeEstimate(won, NOW, if (won == null) FeeStatus.UNAVAILABLE else FeeStatus.ESTIMATED),
    availability = Availability(status, spaces, null, NOW),
    operating = operating,
)

val List<ParkingCandidate>.ids: List<String> get() = map { it.parking.id }

class FakeParkingRepository(
    private val facilities: List<ParkingFacility>,
    private val links: Map<String, DestinationParkingLink> = emptyMap(),
    var readings: Map<String, AvailabilityReading?> = emptyMap(),
    private val failIds: Set<String> = emptySet(),
) : ParkingRepository {
    val nearbyRequests = mutableListOf<Pair<GeoPoint, Int>>()

    override suspend fun linkedParking(destination: Destination): List<Pair<ParkingFacility, DestinationParkingLink>> =
        links.mapNotNull { (id, link) -> facilities.firstOrNull { it.id == id }?.let { it to link } }

    override suspend fun nearby(center: GeoPoint, radiusMeters: Int): List<ParkingFacility> {
        nearbyRequests += center to radiusMeters
        return facilities.filter { center.distanceMetersTo(it.location) <= radiusMeters }
    }

    override suspend fun availability(parkingId: String): AvailabilityReading? {
        if (parkingId in failIds) throw AppError.ParkingSourceFailed("503")
        return readings[parkingId]
    }
}
