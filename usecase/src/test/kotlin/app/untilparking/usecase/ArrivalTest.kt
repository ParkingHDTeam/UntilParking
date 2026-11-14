package app.untilparking.usecase

import app.untilparking.domain.model.ArrivalPrompt
import app.untilparking.domain.model.DestinationChange
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.model.PendingArrival
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

/** 혼잡도 스펙 C1 도착 판정, C2 묻는 시점. */
class ArrivalTest {

    private val detect = DetectArrivalImpl()
    private val judge = JudgeArrivalPromptImpl()
    private val lot = facility("a")
    private val pending = PendingArrival("a", "주차장 a", NOW)

    @Test
    fun `안내 중에 내비 목적지가 없어지면 도착 후보다`() {
        assertEquals(pending, detect(DestinationChange.NEW_VISIT, null, lot, NOW))
    }

    @Test
    fun `안내 중인 주차장이 없으면 도착 후보가 아니다`() {
        assertNull(detect(DestinationChange.NEW_VISIT, null, null, NOW))
    }

    @Test
    fun `목적지가 다른 곳으로 바뀌면 도착 후보가 아니다`() {
        assertNull(detect(DestinationChange.NEW_VISIT, DEST, lot, NOW))
    }

    @Test
    fun `같은 목적지나 자체 경로 변경은 도착 후보가 아니다`() {
        // 요청 중에 목적지가 잠깐 없어지면 R7이 SAME으로 분류한다
        assertNull(detect(DestinationChange.SAME, null, lot, NOW))
        assertNull(detect(DestinationChange.OWN_REROUTE, DEST, lot, NOW))
    }

    @Test
    fun `정차 중이면 묻는다`() {
        assertEquals(ArrivalPrompt.ASK, judge(pending, DrivingState.PARKED, NOW))
    }

    @Test
    fun `10분 뒤에 정차해도 묻는다`() {
        assertEquals(ArrivalPrompt.ASK, judge(pending, DrivingState.PARKED, NOW.plus(Duration.ofMinutes(10))))
    }

    @Test
    fun `10분을 넘기면 버린다`() {
        val late = NOW.plus(Duration.ofMinutes(10)).plusSeconds(1)
        assertEquals(ArrivalPrompt.DROP, judge(pending, DrivingState.PARKED, late))
        assertEquals(ArrivalPrompt.DROP, judge(pending, DrivingState.MOVING, late))
    }

    @Test
    fun `주행 중이거나 주행 상태를 모르면 기다린다`() {
        assertEquals(ArrivalPrompt.WAIT, judge(pending, DrivingState.MOVING, NOW))
        assertEquals(ArrivalPrompt.WAIT, judge(pending, DrivingState.UNKNOWN, NOW))
    }
}
