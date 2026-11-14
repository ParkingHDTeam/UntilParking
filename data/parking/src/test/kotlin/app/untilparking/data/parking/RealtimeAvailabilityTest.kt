package app.untilparking.data.parking

import app.untilparking.domain.AppError
import app.untilparking.domain.model.AvailabilityReading
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger

/** 실시간 잔여 스펙 V3. */
class RealtimeAvailabilityTest {

    private val t0: Instant = Instant.parse("2026-11-14T01:00:00Z")

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("Asia/Seoul")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private class FakeSource(name: String = "서울", regions: List<String> = listOf("서울특별시"), respond: () -> List<RealtimeRow>) {
        val calls = AtomicInteger()
        val source = RealtimeSource(name, regions) {
            calls.incrementAndGet()
            // 받는 도중 다른 코루틴이 끼어들 수 있게 한 번 양보한다(동시 호출 테스트)
            yield()
            respond()
        }
    }

    private val clock = MutableClock(t0)
    private val jongmyo = facility("pub:1", "종묘", "서울특별시 종로구 훈정동 2")
    private val busan = facility("pub:2", "서면", "부산광역시 부산진구 1")
    private val all = listOf(jongmyo, busan)

    private fun realtime(vararg sources: FakeSource) = RealtimeAvailability(sources.map { it.source }, clock)

    @Test
    fun `어느 출처 지역에도 없으면 부르지 않고 null`() = runTest {
        val seoul = FakeSource { listOf(RealtimeRow("종묘", 3)) }
        assertNull(realtime(seoul).reading(busan, all))
        assertEquals(0, seoul.calls.get())
    }

    @Test
    fun `짝이 있으면 받은 시각과 함께 잔여를 준다`() = runTest {
        val updated = t0.minusSeconds(60)
        val seoul = FakeSource { listOf(RealtimeRow("종묘주차장 공영주차장(시)", 1012, updated)) }
        assertEquals(AvailabilityReading(1012, updated, t0), realtime(seoul).reading(jongmyo, all))
    }

    @Test
    fun `30초 안에는 다시 받지 않고 지나면 다시 받는다`() = runTest {
        var spaces = 3
        val seoul = FakeSource { listOf(RealtimeRow("종묘", spaces)) }
        val realtime = realtime(seoul)
        assertEquals(AvailabilityReading(3, null, t0), realtime.reading(jongmyo, all))
        spaces = 5
        clock.now = t0.plusSeconds(29)
        assertEquals(AvailabilityReading(3, null, t0), realtime.reading(jongmyo, all))
        clock.now = t0.plusSeconds(30)
        assertEquals(AvailabilityReading(5, null, t0.plusSeconds(30)), realtime.reading(jongmyo, all))
        assertEquals(2, seoul.calls.get())
    }

    @Test
    fun `동시에 물어도 한 번만 받는다`() = runTest {
        val seoul = FakeSource { listOf(RealtimeRow("종묘", 3)) }
        val realtime = realtime(seoul)
        val readings = List(3) { async { realtime.reading(jongmyo, all) } }.awaitAll()
        assertEquals(List(3) { AvailabilityReading(3, null, t0) }, readings)
        assertEquals(1, seoul.calls.get())
    }

    @Test
    fun `출처가 실패하면 ParkingSourceFailed이고 30초 동안 다시 부르지 않는다`() = runTest {
        // 활용신청 전 키는 매번 같은 오류다. 실패를 기억하지 않으면 후보마다 다시 부른다
        val seoul = FakeSource { throw IOException("SERVICE_KEY_IS_NOT_REGISTERED_ERROR") }
        val realtime = realtime(seoul)
        repeat(2) {
            assertTrue(runCatching { realtime.reading(jongmyo, all) }.exceptionOrNull() is AppError.ParkingSourceFailed)
        }
        assertEquals(1, seoul.calls.get())
    }

    @Test
    fun `다른 출처에 짝이 있으면 실패를 덮는다`() = runTest {
        val nationwide = FakeSource("전국", listOf("")) { throw IOException("HTTP 500") }
        val seoul = FakeSource { listOf(RealtimeRow("종묘", 3)) }
        assertEquals(AvailabilityReading(3, null, t0), realtime(nationwide, seoul).reading(jongmyo, all))
    }

    @Test
    fun `두 출처에 다 짝이 있으면 더 최근 값을 쓴다`() = runTest {
        val older = FakeSource("가") { listOf(RealtimeRow("종묘", 3, t0.minusSeconds(120))) }
        val newer = FakeSource("나") { listOf(RealtimeRow("종묘", 9, t0.minusSeconds(10))) }
        assertEquals(AvailabilityReading(9, t0.minusSeconds(10), t0), realtime(older, newer).reading(jongmyo, all))
    }
}
