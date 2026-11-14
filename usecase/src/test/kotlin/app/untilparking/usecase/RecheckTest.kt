package app.untilparking.usecase

import app.untilparking.domain.model.RecheckStatus
import app.untilparking.domain.model.RestrictionField
import app.untilparking.domain.model.SearchConditions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 스펙 R7 최종 확인 전 재조회 (기획서 17·23절, T07). */
class RecheckTest {

    private val lot = facility("a")
    private val conditions = SearchConditions()

    private fun recheck(repo: FakeParkingRepository) =
        RecheckParkingImpl(repo, JudgeEligibilityImpl(), JudgeAvailabilityImpl(), EstimateFeeImpl(SEOUL), FakeCongestionRepository(), SummarizeCongestionImpl(), fixedClock())

    @Test
    fun `신선한 0대면 FULL`() = runTest {
        val repo = FakeParkingRepository(listOf(lot), readings = mapOf("a" to reading(0, sourceAgeMinutes = 1)))
        assertEquals(RecheckStatus.FULL, recheck(repo)(candidate("a"), conditions, profile(), DEST).status)
    }

    @Test
    fun `차량 제한 위반이면 BLOCKED`() = runTest {
        val low = facility("a", restrictions = listOf(restriction(RestrictionField.HEIGHT_MM, 1500)))
        val repo = FakeParkingRepository(listOf(low), readings = mapOf("a" to reading(12, sourceAgeMinutes = 1)))
        val result = recheck(repo)(candidate("a").copy(parking = low), conditions, profile(height = 1800), DEST)
        assertEquals(RecheckStatus.BLOCKED, result.status)
    }

    @Test
    fun `LIVE 잔여가 있으면 READY이고 30초 뒤 만료`() = runTest {
        val repo = FakeParkingRepository(listOf(lot), readings = mapOf("a" to reading(12, sourceAgeMinutes = 1)))
        val result = recheck(repo)(candidate("a"), conditions, profile(), DEST)
        assertEquals(RecheckStatus.READY, result.status)
        assertEquals(NOW.plusSeconds(30), result.expiresAt)
        assertTrue(result.snapshotId.isNotBlank())
    }

    @Test
    fun `오래된 정보면 UNKNOWN`() = runTest {
        val repo = FakeParkingRepository(listOf(lot), readings = mapOf("a" to reading(12, sourceAgeMinutes = 6)))
        assertEquals(RecheckStatus.UNKNOWN, recheck(repo)(candidate("a"), conditions, profile(), DEST).status)
    }

    @Test
    fun `잔여 미제공이면 UNKNOWN`() = runTest {
        val repo = FakeParkingRepository(listOf(lot))
        assertEquals(RecheckStatus.UNKNOWN, recheck(repo)(candidate("a"), conditions, profile(), DEST).status)
    }

    @Test
    fun `조회 실패면 UNKNOWN`() = runTest {
        val repo = FakeParkingRepository(listOf(lot), failIds = setOf("a"))
        assertEquals(RecheckStatus.UNKNOWN, recheck(repo)(candidate("a"), conditions, profile(), DEST).status)
    }

    @Test
    fun `재조회는 최신 잔여로 후보를 바꾼다`() = runTest {
        val repo = FakeParkingRepository(listOf(lot), readings = mapOf("a" to reading(3, sourceAgeMinutes = 1)))
        val result = recheck(repo)(candidate("a", spaces = 12), conditions, profile(), DEST)
        assertEquals(3, result.candidate.availability.availableSpaces)
    }
}
