package app.untilparking.usecase

import app.untilparking.domain.model.Congestion
import app.untilparking.domain.model.CongestionLevel.CONGESTED
import app.untilparking.domain.model.CongestionLevel.NORMAL
import app.untilparking.domain.model.CongestionLevel.SMOOTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 혼잡도 스펙 C4 평균. */
class CongestionSummaryTest {

    private val summarize = SummarizeCongestionImpl()

    @Test
    fun `입력이 없으면 정보 없음`() {
        assertNull(summarize(emptyList(), NOW))
    }

    @Test
    fun `한 건이면 그 단계와 1명`() {
        assertEquals(Congestion(CONGESTED, 1), summarize(listOf(report("a", CONGESTED)), NOW))
    }

    @Test
    fun `평균에 가까운 단계로 보여 준다`() {
        // 2.67 → 혼잡, 2.0 → 보통, 1.33 → 원활
        assertEquals(Congestion(CONGESTED, 3), summarize(listOf(report("a", CONGESTED), report("a", CONGESTED), report("a", NORMAL)), NOW))
        assertEquals(Congestion(NORMAL, 2), summarize(listOf(report("a", SMOOTH), report("a", CONGESTED)), NOW))
        assertEquals(Congestion(SMOOTH, 3), summarize(listOf(report("a", SMOOTH), report("a", SMOOTH), report("a", NORMAL)), NOW))
    }

    @Test
    fun `두 단계의 한가운데면 더 붐비는 쪽이다`() {
        assertEquals(Congestion(NORMAL, 2), summarize(listOf(report("a", SMOOTH), report("a", NORMAL)), NOW))
        assertEquals(Congestion(CONGESTED, 2), summarize(listOf(report("a", NORMAL), report("a", CONGESTED)), NOW))
    }

    @Test
    fun `120분 전 입력은 넣고 121분 전 입력은 뺀다`() {
        val reports = listOf(report("a", CONGESTED, ageMinutes = 120), report("a", SMOOTH, ageMinutes = 121), report("a", SMOOTH, ageMinutes = 121))
        assertEquals(Congestion(CONGESTED, 1), summarize(reports, NOW))
    }

    @Test
    fun `오래된 입력뿐이면 정보 없음`() {
        assertNull(summarize(listOf(report("a", CONGESTED, ageMinutes = 121)), NOW))
    }
}
