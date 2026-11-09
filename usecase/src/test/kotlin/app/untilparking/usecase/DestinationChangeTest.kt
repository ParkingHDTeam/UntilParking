package app.untilparking.usecase

import app.untilparking.domain.model.DestinationChange
import app.untilparking.domain.model.DestinationSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** 스펙 R7 목적지 변경 분류 (기획서 25절). */
class DestinationChangeTest {

    private val classify = ClassifyDestinationChangeImpl()
    private val entrance = SEORIM.offset(300.0)

    @Test
    fun `같은 목적지면 SAME`() = assertEquals(DestinationChange.SAME, classify(DEST, DEST, null))

    @Test
    fun `둘 다 없으면 SAME`() = assertEquals(DestinationChange.SAME, classify(null, null, null))

    @Test
    fun `목적지가 사라지면 새 방문으로 초기화한다`() = assertEquals(DestinationChange.NEW_VISIT, classify(DEST, null, entrance))

    @Test
    fun `처음 들어온 목적지는 새 방문`() = assertEquals(DestinationChange.NEW_VISIT, classify(null, DEST, null))

    @Test
    fun `예정 주차장 입구 30m 이내면 자체 경로 변경`() {
        val rerouted = DEST.copy(id = "navi:x", name = "주차장 a", location = entrance.offset(30.0))
        assertEquals(DestinationChange.OWN_REROUTE, classify(DEST, rerouted, entrance))
    }

    @Test
    fun `입구에서 31m면 새 방문`() {
        val other = DEST.copy(id = "navi:y", name = "다른 곳", location = entrance.offset(31.0))
        assertEquals(DestinationChange.NEW_VISIT, classify(DEST, other, entrance))
    }

    @Test
    fun `예정 입구가 없으면 다른 목적지는 새 방문`() {
        val other = DEST.copy(id = "d2", name = "다른 곳", location = SEORIM.offset(50.0), source = DestinationSource.NAVI)
        assertEquals(DestinationChange.NEW_VISIT, classify(DEST, other, null))
    }
}
