package app.untilparking.usecase

import app.untilparking.domain.model.Availability
import app.untilparking.domain.model.AvailabilityStatus.LIVE
import app.untilparking.domain.model.AvailabilityStatus.RECEIVED
import app.untilparking.domain.model.AvailabilityStatus.STALE
import app.untilparking.domain.model.AvailabilityStatus.UNAVAILABLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 스펙 R4 잔여 대수 상태 (기획서 24절, T13). */
class AvailabilityTest {

    private val judge = JudgeAvailabilityImpl()

    @Test
    fun `원본 시각 5분 이내면 LIVE`() {
        val result = judge(reading(12, sourceAgeMinutes = 5), NOW)
        assertEquals(LIVE, result.status)
        assertEquals(12, result.availableSpaces)
    }

    @Test
    fun `원본 시각 6분 전이면 STALE`() {
        assertEquals(STALE, judge(reading(12, sourceAgeMinutes = 6), NOW).status)
    }

    @Test
    fun `원본 없이 수신 2분이면 RECEIVED`() {
        assertEquals(RECEIVED, judge(reading(12, receivedAgeMinutes = 2), NOW).status)
    }

    @Test
    fun `원본 없이 수신 3분이면 STALE`() {
        assertEquals(STALE, judge(reading(12, receivedAgeMinutes = 3), NOW).status)
    }

    @Test
    fun `정보가 없으면 UNAVAILABLE`() {
        assertEquals(UNAVAILABLE, judge(null, NOW).status)
    }

    @Test
    fun `잔여 대수 null은 0대가 아니라 UNAVAILABLE`() {
        val result = judge(reading(null, sourceAgeMinutes = 1), NOW)
        assertEquals(UNAVAILABLE, result.status)
        assertEquals(null, result.availableSpaces)
    }

    @Test
    fun `신선한 0대만 만차다`() {
        assertTrue(Availability(LIVE, 0).isFreshFull)
        assertTrue(Availability(RECEIVED, 0).isFreshFull)
        assertFalse(Availability(STALE, 0).isFreshFull)
        assertFalse(Availability(LIVE, 1).isFreshFull)
    }
}
