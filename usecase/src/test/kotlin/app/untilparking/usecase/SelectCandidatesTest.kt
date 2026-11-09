package app.untilparking.usecase

import app.untilparking.domain.model.AvailabilityStatus.LIVE
import app.untilparking.domain.model.AvailabilityStatus.STALE
import app.untilparking.domain.model.AvailabilityStatus.UNAVAILABLE
import app.untilparking.domain.model.EligibilityStatus.BLOCKED
import app.untilparking.domain.model.Filters
import app.untilparking.domain.model.OperatingStatus
import app.untilparking.domain.model.ParkingMethod.MECHANICAL
import app.untilparking.domain.model.ParkingMethod.MIXED
import app.untilparking.domain.model.ParkingMethod.UNKNOWN
import app.untilparking.domain.model.SearchConditions
import app.untilparking.domain.model.SortKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** 스펙 R3 제외 순서와 정렬 (기획서 16·19절, T03). */
class SelectCandidatesTest {

    private val select = SelectCandidatesImpl()
    private val default = SearchConditions()

    @Test
    fun `기계식 제외 켜고 방식 미확인 포함이면 자주식과 미확인만 남는다`() {
        val result = select(
            listOf(candidate("self", distance = 100), candidate("mech", distance = 50, method = MECHANICAL), candidate("unk", distance = 200, method = UNKNOWN)),
            default,
        )
        assertEquals(listOf("self", "unk"), result.ids)
    }

    @Test
    fun `기계식 제외를 꺼도 BLOCKED는 남지 않는다`() {
        val conditions = default.copy(filters = Filters(excludeMechanical = false))
        val result = select(listOf(candidate("mech", method = MECHANICAL), candidate("blocked", eligibility = BLOCKED)), conditions)
        assertEquals(listOf("mech"), result.ids)
    }

    @Test
    fun `혼합식은 자주식 구역이 확인된 때만 기계식 제외에서 살아남는다`() {
        val result = select(
            listOf(candidate("mixed", method = MIXED), candidate("mixedOk", method = MIXED, selfZoneVerified = true)),
            default,
        )
        assertEquals(listOf("mixedOk"), result.ids)
    }

    @Test
    fun `방식 미확인 포함을 끄면 미확인 방식이 빠진다`() {
        val conditions = default.copy(filters = Filters(includeUnknownMethod = false))
        val result = select(listOf(candidate("self"), candidate("unk", method = UNKNOWN)), conditions)
        assertEquals(listOf("self"), result.ids)
    }

    @Test
    fun `잔여 미제공 포함을 끄면 미제공이 빠진다`() {
        val conditions = default.copy(filters = Filters(includeUnknownAvailability = false))
        val result = select(listOf(candidate("live"), candidate("none", spaces = null, status = UNAVAILABLE)), conditions)
        assertEquals(listOf("live"), result.ids)
    }

    @Test
    fun `잔여 미제공 포함을 켜면 미제공도 남는다`() {
        val result = select(listOf(candidate("live"), candidate("none", distance = 400, spaces = null, status = UNAVAILABLE)), default)
        assertEquals(listOf("live", "none"), result.ids)
    }

    @Test
    fun `신선한 만차는 빠진다`() {
        val result = select(listOf(candidate("open"), candidate("full", spaces = 0, status = LIVE)), default)
        assertEquals(listOf("open"), result.ids)
    }

    @Test
    fun `오래된 0대는 만차로 보지 않고 남는다`() {
        val result = select(listOf(candidate("open"), candidate("stale0", distance = 400, spaces = 0, status = STALE)), default)
        assertEquals(listOf("open", "stale0"), result.ids)
    }

    @Test
    fun `운영 시간 충돌은 빠지고 불명은 남는다`() {
        val result = select(
            listOf(candidate("open"), candidate("closed", operating = OperatingStatus.CLOSED), candidate("unk", distance = 400, operating = OperatingStatus.UNKNOWN)),
            default,
        )
        assertEquals(listOf("open", "unk"), result.ids)
    }

    @Test
    fun `거리순은 가까운 순이고 null은 마지막`() {
        val result = select(listOf(candidate("far", distance = 400), candidate("none", distance = null), candidate("near", distance = 100)), default)
        assertEquals(listOf("near", "far", "none"), result.ids)
    }

    @Test
    fun `요금순은 싼 순이고 산출 불가는 마지막`() {
        val conditions = default.copy(sort = SortKey.COST)
        val result = select(listOf(candidate("exp", won = 3000), candidate("na", won = null), candidate("cheap", won = 1000)), conditions)
        assertEquals(listOf("cheap", "exp", "na"), result.ids)
    }

    @Test
    fun `여유 공간순은 많은 순이고 미제공은 마지막`() {
        val conditions = default.copy(sort = SortKey.AVAILABILITY)
        val result = select(
            listOf(candidate("few", spaces = 3), candidate("none", spaces = null, status = UNAVAILABLE), candidate("many", spaces = 12)),
            conditions,
        )
        assertEquals(listOf("many", "few", "none"), result.ids)
    }

    @Test
    fun `동점은 거리 다음 id 사전순`() {
        val conditions = default.copy(sort = SortKey.COST)
        val result = select(
            listOf(candidate("b", won = 1000, distance = 100), candidate("a", won = 1000, distance = 100), candidate("c", won = 1000, distance = 50)),
            conditions,
        )
        assertEquals(listOf("c", "a", "b"), result.ids)
    }

    @Test
    fun `30개까지만 남긴다`() {
        val many = (1..35).map { candidate("p%02d".format(it), distance = it) }
        assertEquals(30, select(many, default).size)
    }
}
