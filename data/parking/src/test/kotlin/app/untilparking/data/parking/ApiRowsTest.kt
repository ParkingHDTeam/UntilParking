package app.untilparking.data.parking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

/** 실시간 잔여 스펙 V1 응답 봉투. 자료는 2026-11-14 실제 응답에서 몇 행만 남긴 것이다. */
class ApiRowsTest {

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()

    @Test
    fun `XML 응답에서 item 행과 전체 건수를 읽는다`() {
        val page = ApiRows.page(resource("/daejeon-parkinglotif-sample.xml"))
        assertEquals(listOf("NC대전 유성점", "NJ타워 주차장"), page.items.map { it["name"] })
        assertEquals("", page.items[0]["totalQty"])
        assertEquals(756, page.totalCount)
    }

    @Test
    fun `교통안전공단 JSON은 최상위 배열이 행이다`() {
        val page = ApiRows.page(resource("/ts-realtime-sample.json"))
        assertEquals(listOf("00000-19299-00118-00-1", "00000-19199-00017-00-1", "10906-19299-00001-00-1"), page.items.map { it["prk_center_id"] })
        assertEquals(4648, page.totalCount)
    }

    @Test
    fun `결과 코드 03은 빈 목록이다`() {
        assertEquals(emptyList<Map<String, String>>(), ApiRows.items("<response><header><resultCode>03</resultCode><resultMsg>NODATA_ERROR</resultMsg></header></response>"))
    }

    @Test
    fun `인증 오류 봉투와 오류 결과 코드는 실패다`() {
        // 2026-11-14 활용신청 전 키로 교통안전공단 API를 불러 받은 실제 응답
        val notRegistered = """<?xml version="1.0" encoding="UTF-8"?>
            <OpenAPI_ServiceResponse>
            <cmmMsgHeader>
              <errMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</errMsg>
              <returnAuthMsg>등록되지 않은 서비스키</returnAuthMsg>
              <returnReasonCode>30</returnReasonCode>
            </cmmMsgHeader>
            </OpenAPI_ServiceResponse>"""
        listOf(notRegistered, AUTH_ERROR, "<response><header><resultCode>99</resultCode></header></response>", """{"resultCode":"99","resultMsg":"UNKNOWN_ERROR"}""").forEach { body ->
            assertThrows(body, IOException::class.java) { ApiRows.items(body) }
        }
    }
}
