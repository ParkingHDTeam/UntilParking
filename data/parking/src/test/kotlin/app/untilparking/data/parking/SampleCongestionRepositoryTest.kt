package app.untilparking.data.parking

import app.untilparking.domain.model.CongestionLevel
import app.untilparking.domain.model.CongestionReport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 혼잡도 스펙 C5 mock 빌드의 가상 입력. */
class SampleCongestionRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val now: Instant = Instant.parse("2026-11-14T05:00:00Z")

    @Test
    fun `가상 입력에 기기 입력을 더한다`() = runTest {
        val repo = SampleCongestionRepository(FileCongestionRepository(File(tmp.root, "congestion-reports.json")), Clock.fixed(now, ZoneId.of("Asia/Seoul")))
        val sample = repo.reports("pk-001")
        assertEquals(3, sample.size)
        // 가상 입력의 시각은 늘 지금 기준 2시간 안이다
        assertTrue(sample.all { Duration.between(it.reportedAt, now) in Duration.ZERO..Duration.ofHours(2) })

        val mine = CongestionReport("pk-001", CongestionLevel.SMOOTH, now)
        repo.submit(mine)
        assertEquals(sample + mine, repo.reports("pk-001"))
        assertEquals(emptyList<CongestionReport>(), repo.reports("pk-005"))
    }
}
