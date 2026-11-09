package app.untilparking.usecase

import app.untilparking.domain.model.FeeModel
import app.untilparking.domain.model.FeeStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/** 스펙 R5 요금 (기획서 19절, T14). */
class FeeTest {

    private val estimate = EstimateFeeImpl(SEOUL)
    private val model = FeeModel(baseMinutes = 30, baseWon = 1500, stepMinutes = 10, stepWon = 500)

    @Test
    fun `기본 30분 1500원 추가 10분 500원에 60분이면 3000원`() {
        val fee = estimate(model, NOW, 60)
        assertEquals(3000, fee.won)
        assertEquals(FeeStatus.ESTIMATED, fee.status)
    }

    @Test
    fun `31분이면 2000원`() {
        assertEquals(2000, estimate(model, NOW, 31).won)
    }

    @Test
    fun `30분이면 기본료만`() {
        assertEquals(1500, estimate(model, NOW, 30).won)
    }

    @Test
    fun `0분이어도 기본료`() {
        assertEquals(1500, estimate(model, NOW, 0).won)
    }

    @Test
    fun `일 최대 요금을 넘지 않는다`() {
        assertEquals(5000, estimate(model.copy(maxDailyWon = 5000), NOW, 300).won)
    }

    @Test
    fun `요금제가 없으면 산출 불가`() {
        val fee = estimate(null, NOW, 60)
        assertEquals(null, fee.won)
        assertEquals(FeeStatus.UNAVAILABLE, fee.status)
    }

    @Test
    fun `일자 경계를 넘으면 산출 불가`() {
        val lateStart = LocalDateTime.of(2026, 11, 7, 23, 30).atZone(SEOUL).toInstant()
        assertEquals(FeeStatus.UNAVAILABLE, estimate(model, lateStart, 60).status)
    }

    @Test
    fun `0원은 무료로 계산된다`() {
        val fee = estimate(FeeModel(120, 0, 30, 1000), NOW, 60)
        assertEquals(0, fee.won)
        assertEquals(FeeStatus.ESTIMATED, fee.status)
    }

    @Test
    fun `기준 시각은 시작 시각이다`() {
        assertEquals(NOW, estimate(model, NOW, 60).basisAt)
    }
}
