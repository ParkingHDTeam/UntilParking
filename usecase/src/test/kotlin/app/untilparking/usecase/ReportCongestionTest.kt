package app.untilparking.usecase

import app.untilparking.domain.model.CongestionLevel
import app.untilparking.domain.model.CongestionReport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 혼잡도 스펙 C3 입력. */
class ReportCongestionTest {

    @Test
    fun `고른 값을 그 주차장과 지금 시각으로 저장한다`() = runTest {
        val repo = FakeCongestionRepository()
        ReportCongestionImpl(repo, fixedClock())("a", CongestionLevel.CONGESTED)
        assertEquals(listOf(CongestionReport("a", CongestionLevel.CONGESTED, NOW)), repo.saved)
    }
}
