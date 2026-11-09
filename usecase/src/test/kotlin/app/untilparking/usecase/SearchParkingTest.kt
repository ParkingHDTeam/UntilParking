package app.untilparking.usecase

import app.untilparking.domain.model.AvailabilityStatus
import app.untilparking.domain.model.DestinationParkingLink
import app.untilparking.domain.model.DistanceKind
import app.untilparking.domain.model.LinkType
import app.untilparking.domain.model.OperatingSchedule
import app.untilparking.domain.model.OperatingStatus
import app.untilparking.domain.model.SearchConditions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** 스펙 R2 검색 범위, R6 시간, 후보 조립 (기획서 15·19절). */
class SearchParkingTest {

    private fun search(repo: FakeParkingRepository) = SearchParkingImpl(
        repo, JudgeEligibilityImpl(), JudgeAvailabilityImpl(), EstimateFeeImpl(SEOUL), SelectCandidatesImpl(), fixedClock(),
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

    @Test
    fun `차량 조건도 후보마다 판정된다`() = runTest {
        val repo = FakeParkingRepository(listOf(facility("a")))
        val eligibility = search(repo)(DEST, SearchConditions(), profile(height = 1800)).candidates.single().eligibility
        assertEquals(app.untilparking.domain.model.EligibilityStatus.VERIFIED_MATCH, eligibility.status)
    }
}
