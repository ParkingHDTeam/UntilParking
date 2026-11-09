package app.untilparking.usecase

import app.untilparking.domain.model.EligibilityStatus.BLOCKED
import app.untilparking.domain.model.EligibilityStatus.UNKNOWN
import app.untilparking.domain.model.EligibilityStatus.VERIFIED_MATCH
import app.untilparking.domain.model.MirrorBasis
import app.untilparking.domain.model.RestrictionField.HEIGHT_MM
import app.untilparking.domain.model.RestrictionField.SUV
import app.untilparking.domain.model.RestrictionField.WEIGHT_KG
import app.untilparking.domain.model.RestrictionField.WIDTH_MM
import app.untilparking.domain.model.RestrictionOperator.LT
import app.untilparking.domain.model.SuvFlag
import app.untilparking.domain.model.WeightBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 스펙 R1 차량 제한 판정 (기획서 20절, T04·T16). */
class EligibilityTest {

    private val judge = JudgeEligibilityImpl()

    @Test
    fun `높이 2100에 제한 2100 이하면 충족`() {
        val result = judge(facility("a", restrictions = listOf(restriction(HEIGHT_MM, 2100))), profile(height = 2100), TODAY)
        assertEquals(VERIFIED_MATCH, result.status)
    }

    @Test
    fun `높이 2100에 제한 2100 미만이면 BLOCKED`() {
        val result = judge(facility("a", restrictions = listOf(restriction(HEIGHT_MM, 2100, LT))), profile(height = 2100), TODAY)
        assertEquals(BLOCKED, result.status)
        assertTrue(result.reasons.contains("HEIGHT_MM_EXCEEDED"))
    }

    @Test
    fun `높이 2101에 제한 2100 이하면 BLOCKED`() {
        assertEquals(BLOCKED, judge(facility("a"), profile(height = 2101), TODAY).status)
    }

    @Test
    fun `차량 높이를 모르면 UNKNOWN`() {
        val result = judge(facility("a"), profile(height = null), TODAY)
        assertEquals(UNKNOWN, result.status)
        assertEquals(listOf("HEIGHT_MM_UNKNOWN"), result.reasons)
    }

    @Test
    fun `중량 기준이 다르면 UNKNOWN`() {
        val lot = facility("a", restrictions = listOf(restriction(WEIGHT_KG, 2000, weightBasis = WeightBasis.GROSS)))
        val result = judge(lot, profile(weight = 1800, weightBasis = WeightBasis.CURB), TODAY)
        assertEquals(UNKNOWN, result.status)
        assertEquals(listOf("WEIGHT_BASIS_MISMATCH"), result.reasons)
    }

    @Test
    fun `중량 기준이 같으면 값을 비교한다`() {
        val lot = facility("a", restrictions = listOf(restriction(WEIGHT_KG, 2000, weightBasis = WeightBasis.GROSS)))
        assertEquals(VERIFIED_MATCH, judge(lot, profile(weight = 1800, weightBasis = WeightBasis.GROSS), TODAY).status)
    }

    @Test
    fun `폭 제한이 미러 포함 기준이면 미러 제외 차량값과 비교하지 않는다`() {
        val lot = facility("a", restrictions = listOf(restriction(WIDTH_MM, 2000, mirrorBasis = MirrorBasis.INCLUDING)))
        val result = judge(lot, profile(width = 1900, mirror = MirrorBasis.EXCLUDING), TODAY)
        assertEquals(UNKNOWN, result.status)
        assertEquals(listOf("MIRROR_BASIS_MISMATCH"), result.reasons)
    }

    @Test
    fun `확인 31일 지난 제한은 UNKNOWN`() {
        val lot = facility("a", restrictions = listOf(restriction(HEIGHT_MM, 2100, verifiedAt = TODAY.minusDays(31))))
        val result = judge(lot, profile(), TODAY)
        assertEquals(UNKNOWN, result.status)
        assertEquals(listOf("HEIGHT_MM_EXPIRED"), result.reasons)
    }

    @Test
    fun `확인 30일 지난 제한은 아직 유효하다`() {
        val lot = facility("a", restrictions = listOf(restriction(HEIGHT_MM, 2100, verifiedAt = TODAY.minusDays(30))))
        assertEquals(VERIFIED_MATCH, judge(lot, profile(), TODAY).status)
    }

    @Test
    fun `확인일이 없는 제한은 UNKNOWN`() {
        val lot = facility("a", restrictions = listOf(restriction(HEIGHT_MM, 2100, verifiedAt = null)))
        assertEquals(UNKNOWN, judge(lot, profile(), TODAY).status)
    }

    @Test
    fun `출처가 상충하는 제한은 UNKNOWN`() {
        val lot = facility("a", restrictions = listOf(restriction(HEIGHT_MM, 2100, conflicting = true)))
        val result = judge(lot, profile(), TODAY)
        assertEquals(UNKNOWN, result.status)
        assertEquals(listOf("HEIGHT_MM_CONFLICT"), result.reasons)
    }

    @Test
    fun `SUV 불가에 SUV면 BLOCKED`() {
        val lot = facility("a", restrictions = listOf(restriction(SUV)))
        assertEquals(BLOCKED, judge(lot, profile(suv = SuvFlag.YES), TODAY).status)
    }

    @Test
    fun `SUV 불가에 차종 미확인이면 UNKNOWN`() {
        val lot = facility("a", restrictions = listOf(restriction(SUV)))
        assertEquals(UNKNOWN, judge(lot, profile(suv = SuvFlag.UNKNOWN), TODAY).status)
    }

    @Test
    fun `SUV 불가에 SUV가 아니면 충족`() {
        val lot = facility("a", restrictions = listOf(restriction(SUV)))
        assertEquals(VERIFIED_MATCH, judge(lot, profile(suv = SuvFlag.NO), TODAY).status)
    }

    @Test
    fun `상충 없는 다른 위반이 있으면 BLOCKED가 UNKNOWN보다 앞선다`() {
        val lot = facility(
            "a",
            restrictions = listOf(restriction(HEIGHT_MM, 1500), restriction(WIDTH_MM, 2000, conflicting = true)),
        )
        assertEquals(BLOCKED, judge(lot, profile(height = 1800), TODAY).status)
    }

    @Test
    fun `제한 정보가 없으면 UNKNOWN`() {
        val result = judge(facility("a", restrictions = emptyList()), profile(), TODAY)
        assertEquals(UNKNOWN, result.status)
        assertEquals(listOf("NO_RESTRICTION_DATA"), result.reasons)
    }

    @Test
    fun `모든 제한이 확인되고 충족하면 VERIFIED_MATCH`() {
        val lot = facility(
            "a",
            restrictions = listOf(restriction(HEIGHT_MM, 2100), restriction(WIDTH_MM, 2000, mirrorBasis = MirrorBasis.EXCLUDING)),
        )
        val result = judge(lot, profile(height = 1800, width = 1900), TODAY)
        assertEquals(VERIFIED_MATCH, result.status)
        assertTrue(result.reasons.isEmpty())
    }
}
