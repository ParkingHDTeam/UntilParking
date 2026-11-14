package app.untilparking.data.parking

import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.ParkingCategory
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.ParkingMethod
import org.junit.Assert.assertEquals
import org.junit.Test

fun facility(id: String, name: String, address: String, lat: Double = 37.5, lon: Double = 127.0) =
    ParkingFacility(id, name, GeoPoint(lat, lon), GeoPoint(lat, lon), ParkingCategory.OFF_STREET, ParkingMethod.UNKNOWN, address = address)

/** 실시간 잔여 스펙 V2. 시설 이름·주소는 2026-11-14 표준데이터에 있는 것을 썼다. */
class RealtimeMatcherTest {

    private val seoul = listOf("서울특별시")
    private val none = emptyMap<String, RealtimeRow>()

    @Test
    fun `괄호와 공백과 끝의 주차장 공영을 떼고 이름을 비교한다`() {
        assertEquals("종묘", RealtimeMatcher.normalize("종묘주차장 공영주차장(시)"))
        assertEquals("종묘", RealtimeMatcher.normalize("종묘"))
        assertEquals("가수원도서관주변노상", RealtimeMatcher.normalize("가수원도서관 주변 노상주차장"))
        assertEquals("", RealtimeMatcher.normalize("공영주차장"))
    }

    @Test
    fun `지역 안에 같은 이름이 하나뿐이면 짝짓는다`() {
        val jongmyo = facility("pub:1", "종묘", "서울특별시 종로구 훈정동 2")
        val other = facility("pub:2", "동순라길(종묘)", "서울특별시 종로구 인의동 112-10")
        val row = RealtimeRow("종묘주차장 공영주차장(시)", 1012)
        assertEquals(mapOf("pub:1" to row), RealtimeMatcher.match(listOf(row), listOf(jongmyo, other), seoul))
    }

    @Test
    fun `같은 이름이 둘이면 짝짓지 않는다`() {
        val a = facility("pub:1", "중앙공영주차장", "서울특별시 중구 1")
        val b = facility("pub:2", "중앙 주차장", "서울특별시 강서구 2")
        assertEquals(none, RealtimeMatcher.match(listOf(RealtimeRow("중앙", 5)), listOf(a, b), seoul))
    }

    @Test
    fun `지역 밖 시설과는 짝짓지 않는다`() {
        val yeoju = facility("pub:9", "세종로(경찰서 민원실 앞)", "경기도 여주시 홍문동 216-1")
        assertEquals(none, RealtimeMatcher.match(listOf(RealtimeRow("세종로 공영주차장(시)", 944)), listOf(yeoju), seoul))
    }

    @Test
    fun `좌표가 있으면 300m 안의 가장 가까운 같은 이름과 짝짓는다`() {
        val daejeon = listOf("대전광역시")
        // 행 좌표에서 북쪽으로 약 111m, 222m, 555m
        val near = facility("pub:1", "시청 주차장", "대전광역시 서구 1", lat = 36.3010, lon = 127.38)
        val farther = facility("pub:2", "시청주차장", "대전광역시 서구 2", lat = 36.3020, lon = 127.38)
        val tooFar = facility("pub:3", "시청", "대전광역시 서구 3", lat = 36.3050, lon = 127.38)
        val row = RealtimeRow("시청 공영주차장", 7, location = GeoPoint(36.3000, 127.38))
        assertEquals(mapOf("pub:1" to row), RealtimeMatcher.match(listOf(row), listOf(tooFar, farther, near), daejeon))
        assertEquals(none, RealtimeMatcher.match(listOf(row), listOf(tooFar), daejeon))
    }

    @Test
    fun `두 행이 한 시설에 짝지어지면 그 시설은 짝 없음`() {
        val jongmyo = facility("pub:1", "종묘", "서울특별시 종로구 훈정동 2")
        val rows = listOf(RealtimeRow("종묘", 1), RealtimeRow("종묘 공영주차장", 2))
        assertEquals(none, RealtimeMatcher.match(rows, listOf(jongmyo), seoul))
    }
}
