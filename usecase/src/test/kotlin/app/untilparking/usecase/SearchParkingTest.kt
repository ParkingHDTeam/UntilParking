package app.untilparking.usecase

import app.untilparking.domain.model.AvailabilityStatus
import app.untilparking.domain.model.Congestion
import app.untilparking.domain.model.CongestionLevel
import app.untilparking.domain.model.DestinationParkingLink
import app.untilparking.domain.model.DistanceKind
import app.untilparking.domain.model.LinkType
import app.untilparking.domain.model.OperatingSchedule
import app.untilparking.domain.model.OperatingStatus
import app.untilparking.domain.model.SearchConditions
import app.untilparking.domain.model.SortKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime

/** 스펙 R2 검색 범위, R6 시간, 후보 조립 (기획서 15·19절). */
class SearchParkingTest {

    private fun search(repo: FakeParkingRepository, congestion: FakeCongestionRepository = FakeCongestionRepository()) = SearchParkingImpl(
        repo, JudgeEligibilityImpl(), JudgeAvailabilityImpl(), EstimateFeeImpl(SEOUL), SelectCandidatesImpl(), congestion, SummarizeCongestionImpl(), fixedClock(),
    )

    @Test
    fun `반경 안 시설만 후보가 된다`() = runTest {
        val repo = FakeParkingRepository(
            listOf(facility("in1", at = SEORIM.offset(300.0)), facility("in2", at = SEORIM.offset(480.0)), facility("out", at = SEORIM.offset(520.0))),
        )
        val result = search(repo)(DEST, SearchConditions(radiusM = 500), profile())
        assertEquals(listOf("in1", "in2"), result.candidates.ids)
        assertEquals(500, result.radiusM)
        assertEquals(SEORIM to 500, repo.nearbyRequests.single())
    }

    @Test
    fun `연결 주차장은 반경 밖이어도 linked에 들어간다`() = runTest {
        val link = DestinationParkingLink("att", LinkType.OWN, "방문객 2시간 지원")
        val repo = FakeParkingRepository(listOf(facility("att", at = SEORIM.offset(900.0))), links = mapOf("att" to link))
        val result = search(repo)(DEST, SearchConditions(), profile())
        assertEquals(listOf("att"), result.linked.ids)
        assertEquals(link, result.linked.single().link)
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun `연결 주차장이 반경 안이면 후보에도 관계가 붙는다`() = runTest {
        val link = DestinationParkingLink("att", LinkType.PARTNER)
        val repo = FakeParkingRepository(listOf(facility("att", at = SEORIM.offset(60.0))), links = mapOf("att" to link))
        val result = search(repo)(DEST, SearchConditions(), profile())
        assertEquals(link, result.candidates.single().link)
    }

    @Test
    fun `거리는 목적지에서 직선이다`() = runTest {
        val repo = FakeParkingRepository(listOf(facility("a", at = SEORIM.offset(300.0))))
        val distance = search(repo)(DEST, SearchConditions(), profile()).candidates.single().distance
        assertEquals(DistanceKind.STRAIGHT, distance.kind)
        assertNotNull(distance.meters)
        assertTrue("직선 300m 안팎이어야 한다: ${distance.meters}", distance.meters!! in 298..302)
        assertEquals(null, distance.minutes)
    }

    @Test
    fun `잔여 조회가 실패한 시설은 ERROR 상태로 남는다`() = runTest {
        val repo = FakeParkingRepository(listOf(facility("a")), failIds = setOf("a"))
        val candidate = search(repo)(DEST, SearchConditions(), profile()).candidates.single()
        assertEquals(AvailabilityStatus.ERROR, candidate.availability.status)
    }

    @Test
    fun `잔여 조회 결과는 판정을 거쳐 후보에 붙는다`() = runTest {
        val repo = FakeParkingRepository(listOf(facility("a")), readings = mapOf("a" to reading(12, sourceAgeMinutes = 1)))
        val availability = search(repo)(DEST, SearchConditions(), profile()).candidates.single().availability
        assertEquals(AvailabilityStatus.LIVE, availability.status)
        assertEquals(12, availability.availableSpaces)
    }

    @Test
    fun `도착 예정 시각이 없으면 지금을 요금 기준 시각으로 쓴다`() = runTest {
        val repo = FakeParkingRepository(listOf(facility("a")))
        assertEquals(NOW, search(repo)(DEST, SearchConditions(), profile()).candidates.single().fee.basisAt)
    }

    @Test
    fun `도착 예정 시각이 있으면 그것을 요금 기준 시각으로 쓴다`() = runTest {
        val eta = NOW.plusSeconds(1200)
        val repo = FakeParkingRepository(listOf(facility("a")))
        assertEquals(eta, search(repo)(DEST.copy(etaAt = eta), SearchConditions(), profile()).candidates.single().fee.basisAt)
    }

    @Test
    fun `운영 시간 안이면 OPEN이고 종료 전에 나올 수 없으면 후보에서 빠진다`() = runTest {
        val openUntil18 = facility("open", at = SEORIM.offset(100.0), schedule = OperatingSchedule(LocalTime.of(9, 0), LocalTime.of(18, 0)))
        val closesAt15 = facility("closes", at = SEORIM.offset(200.0), schedule = OperatingSchedule(LocalTime.of(9, 0), LocalTime.of(15, 0)))
        val unknown = facility("unknown", at = SEORIM.offset(300.0), schedule = null)
        val repo = FakeParkingRepository(listOf(openUntil18, closesAt15, unknown))
        // 14:32 도착, 60분 주차 → 15:32에 나온다
        val result = search(repo)(DEST, SearchConditions(parkingMinutes = 60), profile())
        assertEquals(listOf("open", "unknown"), result.candidates.ids)
        assertEquals(OperatingStatus.OPEN, result.candidates[0].operating)
        assertEquals(OperatingStatus.UNKNOWN, result.candidates[1].operating)
    }

    // 공공 데이터 스펙 P3. NOW(2026-11-07 14:32)는 토요일이다.
    private val weekday9to18 = OperatingSchedule(LocalTime.of(9, 0), LocalTime.of(18, 0))
    private val until13 = OperatingSchedule(LocalTime.of(9, 0), LocalTime.of(13, 0))

    @Test
    fun `토요일 도착이면 토요일 운영 시간으로 판정한다`() = runTest {
        val satShort = facility("sat", schedule = weekday9to18, saturday = until13)
        val result = search(FakeParkingRepository(listOf(satShort)))(DEST.copy(etaAt = NOW), SearchConditions(), profile())
        assertEquals(emptyList<String>(), result.candidates.ids)
    }

    @Test
    fun `일요일 도착이면 공휴일 운영 시간으로 판정한다`() = runTest {
        val holidayShort = facility("hol", schedule = weekday9to18, holiday = until13)
        val sunday = NOW.plus(Duration.ofDays(1))
        val result = search(FakeParkingRepository(listOf(holidayShort)))(DEST.copy(etaAt = sunday), SearchConditions(), profile())
        assertEquals(emptyList<String>(), result.candidates.ids)
    }

    @Test
    fun `그 요일 운영 시간이 없으면 UNKNOWN`() = runTest {
        val noSaturday = facility("nosat", schedule = weekday9to18, saturday = null)
        val result = search(FakeParkingRepository(listOf(noSaturday)))(DEST.copy(etaAt = NOW), SearchConditions(), profile())
        assertEquals(OperatingStatus.UNKNOWN, result.candidates.single().operating)
    }

    @Test
    fun `그 요일이 휴무면 CLOSED로 후보에서 빠진다`() = runTest {
        // 휴무 표시는 시각보다 우선한다
        val closedSaturday = facility("closedsat", schedule = weekday9to18, saturday = weekday9to18.copy(closed = true))
        val openSaturday = facility("opensat", schedule = weekday9to18)
        val result = search(FakeParkingRepository(listOf(closedSaturday, openSaturday)))(DEST.copy(etaAt = NOW), SearchConditions(), profile())
        assertEquals(listOf("opensat"), result.candidates.ids)
    }

    @Test
    fun `차량 조건도 후보마다 판정된다`() = runTest {
        val repo = FakeParkingRepository(listOf(facility("a")))
        val eligibility = search(repo)(DEST, SearchConditions(), profile(height = 1800)).candidates.single().eligibility
        assertEquals(app.untilparking.domain.model.EligibilityStatus.VERIFIED_MATCH, eligibility.status)
    }

    // ---- 혼잡도 스펙 C4 ----

    @Test
    fun `후보에 그 주차장의 혼잡도가 붙는다`() = runTest {
        val repo = FakeParkingRepository(listOf(facility("a", at = SEORIM.offset(100.0)), facility("b", at = SEORIM.offset(200.0))))
        val congestion = FakeCongestionRepository(listOf(report("a", CongestionLevel.CONGESTED)))
        val result = search(repo, congestion)(DEST, SearchConditions(), profile())
        assertEquals(Congestion(CongestionLevel.CONGESTED, 1), result.candidates.first { it.parking.id == "a" }.congestion)
        assertNull(result.candidates.first { it.parking.id == "b" }.congestion)
    }

    @Test
    fun `혼잡도를 읽지 못해도 후보는 나온다`() = runTest {
        val repo = FakeParkingRepository(listOf(facility("a")))
        val result = search(repo, FakeCongestionRepository(failRead = true))(DEST, SearchConditions(), profile())
        assertEquals(listOf("a"), result.candidates.ids)
        assertNull(result.candidates.single().congestion)
    }

    @Test
    fun `혼잡도는 정렬을 바꾸지 않는다`() = runTest {
        val repo = FakeParkingRepository(listOf(facility("far", at = SEORIM.offset(400.0)), facility("near", at = SEORIM.offset(100.0))))
        val congestion = FakeCongestionRepository(listOf(report("near", CongestionLevel.CONGESTED), report("far", CongestionLevel.SMOOTH)))
        SortKey.entries.forEach { sort ->
            val result = search(repo, congestion)(DEST, SearchConditions(sort = sort), profile())
            assertEquals("정렬 $sort", listOf("near", "far"), result.candidates.ids)
        }
    }
}
