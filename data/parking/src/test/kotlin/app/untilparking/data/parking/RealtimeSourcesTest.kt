package app.untilparking.data.parking

import app.untilparking.domain.model.GeoPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

/** 실시간 잔여 스펙 V1·V4. 출처 목록과 여러 쪽 받기. */
class RealtimeSourcesTest {

    private class FakeHttp(private val respond: (String) -> String) : TextFetcher {
        val urls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override suspend fun get(url: String): String = respond(url).also { urls += url }
    }

    private fun page(url: String): Int = Regex("""pageNo=(\d+)""").find(url)!!.groupValues[1].toInt()

    private fun daejeonPage(page: Int, total: Int, perPage: Int = 50): String {
        val items = (1..perPage).map { (page - 1) * perPage + it }.filter { it <= total }
            .joinToString("") { "<item><name>대전 $it</name><totalQty>10</totalQty><resQty>3</resQty></item>" }
        return "<response><header><resultCode>00</resultCode></header><body>$items<totalCount>$total</totalCount></body></response>"
    }

    private val seoulLots = TsSido("11", listOf("서울특별시"), mapOf("A" to TsLot("가 주차장", GeoPoint(37.5, 127.0))))

    @Test
    fun `키가 비면 출처가 없다`() {
        assertEquals(emptyList<RealtimeSource>(), RealtimeSources.create("", FakeHttp { "" }, listOf(seoulLots)))
    }

    @Test
    fun `부산 대전과 교통안전공단 시도마다 출처를 만든다`() {
        val busanLots = TsSido("26", listOf("부산광역시"), emptyMap())
        val sources = RealtimeSources.create("key", FakeHttp { "" }, listOf(seoulLots, busanLots))
        assertEquals(listOf("부산시설공단", "대전", "교통안전공단 11", "교통안전공단 26"), sources.map { it.name })
        assertEquals(listOf("서울특별시"), sources[2].regions)
    }

    @Test
    fun `대전은 첫 쪽의 전체 건수를 보고 나머지 쪽을 받는다`() = runTest {
        val http = FakeHttp { url -> daejeonPage(page(url), total = 120) }
        val daejeon = RealtimeSources.create("key", http, emptyList()).single { it.name == "대전" }
        assertEquals((1..120).map { "대전 $it" }, daejeon.fetch().map { it.name })
        assertEquals(listOf(1, 2, 3), http.urls.map(::page).sorted())
        assertTrue(http.urls.all { "numOfRows=50" in it })
    }

    @Test
    fun `교통안전공단은 그 시도 코드로 JSON을 받는다`() = runTest {
        val http = FakeHttp { """{"resultCode":"0","totalCount":"1","PrkRealtimeInfo":[{"prk_center_id":"A","pkfc_ParkingLots_total":"10","pkfc_Available_ParkingLots_total":"4"}]}""" }
        val ts = RealtimeSources.create("key", http, listOf(seoulLots)).single { it.name == "교통안전공단 11" }
        assertEquals(listOf(RealtimeRow("가 주차장", 4, null, GeoPoint(37.5, 127.0))), ts.fetch())
        assertTrue(http.urls.single().let { "Parking_v1/PrkRealtimeInfo_v1" in it && "sidoCd=11" in it && "format=2" in it })
    }

    @Test
    fun `앱에 넣은 교통안전공단 표를 읽는다`() {
        val sidos = TsRealtimeLots.load()
        assertTrue(sidos.isNotEmpty())
        assertTrue(sidos.all { it.regions.isNotEmpty() && it.lots.isNotEmpty() })
    }
}
