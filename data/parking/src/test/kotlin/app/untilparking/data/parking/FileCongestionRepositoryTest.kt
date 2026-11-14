package app.untilparking.data.parking

import app.untilparking.domain.model.CongestionLevel
import app.untilparking.domain.model.CongestionReport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/** 혼잡도 스펙 C5 기기 저장. */
class FileCongestionRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val t0: Instant = Instant.parse("2026-11-14T05:00:00Z")
    private val a = CongestionReport("a", CongestionLevel.CONGESTED, t0)
    private val b = CongestionReport("b", CongestionLevel.SMOOTH, t0.plusSeconds(60))

    private fun file() = File(tmp.root, "congestion-reports.json")

    @Test
    fun `남긴 입력을 그 주차장으로 다시 읽는다`() = runTest {
        val repo = FileCongestionRepository(file())
        repo.submit(a)
        repo.submit(b)
        assertEquals(listOf(a), repo.reports("a"))
        assertEquals(listOf(b), repo.reports("b"))
        assertEquals(emptyList<CongestionReport>(), repo.reports("c"))
    }

    @Test
    fun `앱을 다시 켜도 남아 있다`() = runTest {
        FileCongestionRepository(file()).submit(a)
        assertEquals(listOf(a), FileCongestionRepository(file()).reports("a"))
    }

    @Test
    fun `깨진 파일은 입력이 없는 것으로 본다`() = runTest {
        file().writeText("{\"reports\":[{\"parkingId\"")
        val repo = FileCongestionRepository(file())
        assertEquals(emptyList<CongestionReport>(), repo.reports("a"))
        // 다음 저장이 새로 쓴다
        repo.submit(a)
        assertEquals(listOf(a), FileCongestionRepository(file()).reports("a"))
    }

    @Test
    fun `모르는 단계는 그 줄만 버린다`() = runTest {
        file().writeText(
            """{"reports":[{"parkingId":"a","level":"JAMMED","reportedAt":${t0.toEpochMilli()}},{"parkingId":"a","level":"CONGESTED","reportedAt":${t0.toEpochMilli()}}]}""",
        )
        assertEquals(listOf(a), FileCongestionRepository(file()).reports("a"))
    }
}
