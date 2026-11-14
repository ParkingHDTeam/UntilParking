package app.untilparking.data.parking

import app.untilparking.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 실시간 잔여 스펙 V1. 자료는 2026-11-14 실제 응답에서 몇 행만 남긴 것이다. */
class RealtimeParsersTest {

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()

    @Test
    fun `부산은 주차 가능 대수와 갱신 시각을 읽고 대수가 빈 행은 버린다`() {
        val rows = ApiRows.items(resource("/busan-parkinginfo-sample.xml")).mapNotNull(RealtimeParsers::busan)
        val updated = LocalDateTime.parse("2026-11-11T17:50:00").atZone(ZoneId.of("Asia/Seoul")).toInstant()
        assertEquals(listOf(RealtimeRow("반송1, 3동 공영", 103, updated), RealtimeRow("좌1동", 0, updated)), rows)
    }

    @Test
    fun `대전은 총면수가 있는 행만 읽는다`() {
        // 총면수가 빈 행은 resQty가 0이어도 만차가 아니라 실시간이 없는 곳이다
        val rows = ApiRows.items(resource("/daejeon-parkinglotif-sample.xml")).mapNotNull(RealtimeParsers::daejeon)
        assertEquals(listOf(RealtimeRow("NJ타워 주차장", 173, null, GeoPoint(36.35195389, 127.37948831))), rows)
    }

    @Test
    fun `교통안전공단은 표에서 이름과 좌표를 찾고 총면수를 넘는 값은 자른다`() {
        val lots = mapOf(
            "00000-19299-00118-00-1" to TsLot("면수 없는 곳", GeoPoint(37.0, 127.0)),
            "00000-19199-00017-00-1" to TsLot("가 주차장", GeoPoint(37.1, 127.1)),
            "10906-19299-00001-00-1" to TsLot("나 주차장", GeoPoint(37.2, 127.2)),
        )
        val rows = ApiRows.items(resource("/ts-realtime-sample.json")).mapNotNull { RealtimeParsers.ts(it, lots) }
        assertEquals(listOf(RealtimeRow("가 주차장", 60, null, GeoPoint(37.1, 127.1)), RealtimeRow("나 주차장", 129, null, GeoPoint(37.2, 127.2))), rows)
    }

    @Test
    fun `교통안전공단 표에 없는 ID는 버린다`() {
        val rows = ApiRows.items(resource("/ts-realtime-sample.json")).mapNotNull { RealtimeParsers.ts(it, emptyMap()) }
        assertEquals(emptyList<RealtimeRow>(), rows)
    }
}
