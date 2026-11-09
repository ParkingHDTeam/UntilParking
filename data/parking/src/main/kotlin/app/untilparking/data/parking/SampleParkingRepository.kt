package app.untilparking.data.parking

import app.untilparking.domain.model.AvailabilityReading
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationParkingLink
import app.untilparking.domain.model.FeeModel
import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.LinkType
import app.untilparking.domain.model.MirrorBasis
import app.untilparking.domain.model.OperatingSchedule
import app.untilparking.domain.model.ParkingCategory
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.ParkingMethod
import app.untilparking.domain.model.Restriction
import app.untilparking.domain.model.RestrictionField
import app.untilparking.domain.model.WeightBasis
import app.untilparking.domain.model.distanceMetersTo
import app.untilparking.domain.repository.ParkingRepository
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * 기획서 24절의 "개발용 가상 데이터". 서버 v1 계약이 생기면 같은 인터페이스의 HTTP 어댑터로 바꾼다.
 * 시설명·수량·요금·치수는 모두 설명용 예시이고 실제 운영 정보가 아니다.
 */
class SampleParkingRepository(private val clock: Clock) : ParkingRepository {

    /** mock 패널이 만차 시나리오를 만들 때 쓴다. 들어 있는 시설은 잔여 0대로 답한다. */
    @Volatile
    var forcedFull: Set<String> = emptySet()

    override suspend fun linkedParking(destination: Destination): List<Pair<ParkingFacility, DestinationParkingLink>> {
        // 관계는 검증된 매핑으로만 만든다. 여기서는 예시 목적지 좌표 150m 안이면 서림문화센터로 본다.
        if (destination.location.distanceMetersTo(SEORIM_CENTER) > 150.0) return emptyList()
        return listOf(ATTACHED to DestinationParkingLink(ATTACHED.id, LinkType.OWN, "방문객 2시간 지원 · 안내데스크 등록 필요", VERIFIED))
    }

    override suspend fun nearby(center: GeoPoint, radiusMeters: Int): List<ParkingFacility> =
        ALL.filter { center.distanceMetersTo(it.location) <= radiusMeters }

    override suspend fun availability(parkingId: String): AvailabilityReading? {
        val now = clock.instant()
        if (parkingId in forcedFull) return AvailabilityReading(0, now.minus(Duration.ofMinutes(1)), now)
        return when (parkingId) {
            // 원본 갱신 시각을 주지 않는 출처
            "pk-001" -> AvailabilityReading(12, null, now)
            // 1분 전 원본 시각이 있는 출처
            "pk-003" -> AvailabilityReading(3, now.minus(Duration.ofMinutes(1)), now)
            "pk-004" -> AvailabilityReading(5, now.minus(Duration.ofMinutes(1)), now)
            // 부설·노상·임시는 실시간 정보를 제공하지 않는다
            else -> null
        }
    }

    private companion object {
        val SEORIM_CENTER = GeoPoint(37.5000, 127.0000)
        val VERIFIED: LocalDate = LocalDate.of(2026, 10, 28)

        fun height(mm: Int) = Restriction(RestrictionField.HEIGHT_MM, value = mm, verifiedAt = VERIFIED)

        val ATTACHED = ParkingFacility(
            id = "att-001",
            name = "서림문화센터 부설주차장",
            location = GeoPoint(37.5004, 127.0003),
            entrance = GeoPoint(37.5005, 127.0004),
            category = ParkingCategory.ATTACHED,
            method = ParkingMethod.SELF,
            address = "서림로 1",
            restrictions = listOf(height(2100)),
            // 방문객 2시간 무료, 이후 30분 1,000원 (예시)
            feeModel = FeeModel(120, 0, 30, 1000),
            operatingSchedule = OperatingSchedule(LocalTime.of(9, 0), LocalTime.of(21, 0)),
        )

        val ALL = listOf(
            ATTACHED,
            ParkingFacility(
                id = "pk-001",
                name = "서림 공영주차장",
                location = GeoPoint(37.4975, 127.0020),
                entrance = GeoPoint(37.4976, 127.0021),
                category = ParkingCategory.OFF_STREET,
                method = ParkingMethod.SELF,
                address = "서림로 23",
                operator = "서림구 시설관리공단",
                restrictions = listOf(
                    height(2300),
                    Restriction(RestrictionField.WIDTH_MM, value = 2200, mirrorBasis = MirrorBasis.EXCLUDING, verifiedAt = VERIFIED),
                ),
                feeModel = FeeModel(30, 1500, 10, 500, maxDailyWon = 20_000),
                operatingSchedule = OperatingSchedule(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, allDay = true),
            ),
            ParkingFacility(
                id = "pk-002",
                name = "중앙 노상주차장",
                location = GeoPoint(37.5035, 127.0025),
                entrance = GeoPoint(37.5035, 127.0025),
                category = ParkingCategory.ON_STREET,
                method = ParkingMethod.SELF,
                address = "중앙로 노상",
                // 제한 정보가 확인되지 않은 시설
                restrictions = emptyList(),
                feeModel = FeeModel(30, 1200, 10, 400),
                operatingSchedule = OperatingSchedule(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, allDay = true),
            ),
            ParkingFacility(
                id = "pk-003",
                name = "문화광장 주차장",
                location = GeoPoint(37.4962, 126.9980),
                entrance = GeoPoint(37.4961, 126.9979),
                category = ParkingCategory.OFF_STREET,
                method = ParkingMethod.SELF,
                address = "광장길 5",
                restrictions = listOf(
                    height(2100),
                    // 폭 제한이 미러 포함 기준이라 미러 제외 차량값과 바로 비교할 수 없다 → 일부 미확인
                    Restriction(RestrictionField.WIDTH_MM, value = 2000, mirrorBasis = MirrorBasis.INCLUDING, verifiedAt = VERIFIED),
                ),
                feeModel = FeeModel(30, 1000, 10, 300),
                operatingSchedule = OperatingSchedule(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, allDay = true),
            ),
            ParkingFacility(
                id = "pk-004",
                name = "역전 기계식주차장",
                location = GeoPoint(37.5010, 126.9985),
                entrance = GeoPoint(37.5010, 126.9985),
                category = ParkingCategory.OFF_STREET,
                method = ParkingMethod.MECHANICAL,
                address = "역전로 7",
                restrictions = listOf(
                    height(1550),
                    Restriction(RestrictionField.WEIGHT_KG, value = 2000, weightBasis = WeightBasis.GROSS, verifiedAt = VERIFIED),
                ),
                feeModel = FeeModel(30, 1000, 10, 500),
                operatingSchedule = OperatingSchedule(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, allDay = true),
            ),
            ParkingFacility(
                id = "pk-005",
                name = "북문 임시주차장",
                location = GeoPoint(37.5050, 126.9990),
                entrance = null,
                category = ParkingCategory.OFF_STREET,
                method = ParkingMethod.UNKNOWN,
                address = "북문길 12",
                restrictions = emptyList(),
                // 요금제가 확인되지 않아 산출 불가
                feeModel = null,
                operatingSchedule = null,
            ),
        )
    }
}
