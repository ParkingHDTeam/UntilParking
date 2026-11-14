package app.untilparking.data.parking

import app.untilparking.domain.AppError
import app.untilparking.domain.model.AvailabilityReading
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationSource
import app.untilparking.domain.model.GeoPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 공공 데이터 스펙 P1 받기와 저장. */
class PublicParkingRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val center = GeoPoint(37.5000, 127.0000)
    private val t0: Instant = Instant.parse("2026-11-09T03:00:00Z")

    // 중심에서 북쪽 약 111m, 약 2.2km
    private val near = row("1", lat = "37.5010", lon = "127.0000")
    private val far = row("2", lat = "37.5200", lon = "127.0000")

    private fun file() = File(tmp.root, "public-parking.json")
    private fun clock(at: Instant) = Clock.fixed(at, ZoneId.of("Asia/Seoul"))
    private fun repo(api: PublicParkingApi?, at: Instant = t0) = PublicParkingRepository(api, file(), clock(at))
    private val failing = FakeApi { throw IOException("네트워크 끊김") }

    private suspend fun PublicParkingRepository.ids(radius: Int) = nearby(center, radius).map { it.id }
    private suspend fun failureOf(block: suspend () -> Unit): Throwable? = runCatching { block() }.exceptionOrNull()

    @Test
    fun `저장본이 없으면 전 쪽을 받아 저장하고 그 데이터로 찾는다`() = runTest {
        val api = FakeApi { p -> if (p == 1) page(2, near) else page(2, far) }
        assertEquals(listOf("pub:1"), repo(api).ids(500))
        assertEquals(listOf(1, 2), api.calls)
        // 새로 연 저장소는 API 없이 저장본만으로 찾는다
        assertEquals(listOf("pub:1", "pub:2"), repo(failing, t0.plus(Duration.ofDays(1))).ids(5000))
    }

    @Test
    fun `실제 응답 한 쪽을 읽는다`() = runTest {
        // 2026-11-09 받은 첫 쪽(포천시 3행). 전체 18994건이라 둘째 쪽을 부르고, NODATA면 거기서 멈춘다
        val api = FakeApi { p -> if (p == 1) realPage1() else NO_DATA }
        val found = repo(api).nearby(GeoPoint(37.836159253, 127.153839), 100)
        assertEquals(listOf("pub:236-2-000066"), found.map { it.id })
        assertEquals("하송우사거리 쌈지주차장", found.single().name)
        assertEquals(listOf(1, 2), api.calls)
    }

    @Test
    fun `동시에 두 번 찾아도 한 번만 받는다`() = runTest {
        val api = FakeApi { page(1, near) }
        val repo = repo(api)
        val results = listOf(async { repo.ids(500) }, async { repo.ids(500) }).awaitAll()
        assertEquals(listOf(listOf("pub:1"), listOf("pub:1")), results)
        assertEquals(listOf(1), api.calls)
    }

    @Test
    fun `같은 관리번호는 첫 행만 쓴다`() = runTest {
        val api = FakeApi { page(2, row("1", name = "첫 행"), row("1", name = "둘째 행")) }
        val found = repo(api).nearby(center, 500)
        assertEquals(listOf("첫 행"), found.map { it.name })
    }

    @Test
    fun `저장본이 30일 안이면 API를 부르지 않는다`() = runTest {
        repo(FakeApi { page(1, near) }).ids(500)
        val later = FakeApi { page(1, far) }
        assertEquals(listOf("pub:1"), repo(later, t0.plus(Duration.ofDays(29))).ids(5000))
        assertEquals(emptyList<Int>(), later.calls)
    }

    @Test
    fun `저장본이 30일 지났으면 다시 받는다`() = runTest {
        repo(FakeApi { page(1, near) }).ids(500)
        val later = FakeApi { page(1, far) }
        assertEquals(listOf("pub:2"), repo(later, t0.plus(Duration.ofDays(31))).ids(5000))
        assertEquals(listOf(1), later.calls)
    }

    @Test
    fun `다시 받기가 실패하면 옛 저장본으로 찾는다`() = runTest {
        repo(FakeApi { page(1, near) }).ids(500)
        assertEquals(listOf("pub:1"), repo(failing, t0.plus(Duration.ofDays(31))).ids(5000))
    }

    @Test
    fun `저장본도 없고 받기도 실패하면 ParkingSourceFailed`() = runTest {
        assertTrue(failureOf { repo(failing).ids(500) } is AppError.ParkingSourceFailed)
    }

    @Test
    fun `인증키가 비었으면 호출하지 않고 실패한다`() = runTest {
        assertTrue(failureOf { repo(api = null).ids(500) } is AppError.ParkingSourceFailed)
    }

    @Test
    fun `둘째 쪽이 실패하면 아무것도 저장하지 않는다`() = runTest {
        val api = FakeApi { p -> if (p == 1) page(2, near) else throw IOException("둘째 쪽 실패") }
        assertTrue(failureOf { repo(api).ids(500) } is AppError.ParkingSourceFailed)
        assertFalse(file().exists())
    }

    @Test
    fun `인증 오류 봉투는 실패로 본다`() = runTest {
        assertTrue(failureOf { repo(FakeApi { AUTH_ERROR }).ids(500) } is AppError.ParkingSourceFailed)
        assertFalse(file().exists())
    }

    @Test
    fun `반경 안 시설만 돌려준다`() = runTest {
        assertEquals(listOf("pub:1"), repo(FakeApi { page(2, near, far) }).ids(500))
    }

    @Test
    fun `연결 주차장은 비어 있고 잔여는 미제공이다`() = runTest {
        val repo = repo(FakeApi { page(1, near) })
        val destination = Destination("navi:37.5,127.0", "송정역", center, DestinationSource.NAVI)
        assertEquals(emptyList<Any>(), repo.linkedParking(destination))
        assertNull(repo.availability("pub:1"))
    }

    @Test
    fun `실시간 출처가 있으면 그 시설의 잔여를 물어 준다`() = runTest {
        // 픽스처 시설 주소는 "광주광역시 광산구 상무대로 1", 이름은 "공영주차장 1"
        val source = RealtimeSource("광주", listOf("광주광역시")) { listOf(RealtimeRow("공영주차장 1", 4)) }
        val realtime = RealtimeAvailability(listOf(source), clock(t0))
        val repo = PublicParkingRepository(FakeApi { page(1, near) }, file(), clock(t0), realtime)
        assertEquals(AvailabilityReading(4, null, t0), repo.availability("pub:1"))
    }
}
