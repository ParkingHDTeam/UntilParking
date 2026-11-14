package app.untilparking.data.parking

import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject

/** 표준데이터 한 행. 필드명은 2026-11-09 실제 응답에서 확인했다(원본 오타 포함). */
fun row(
    no: String = "1",
    name: String? = "공영주차장 $no",
    lat: String? = "37.5000",
    lon: String? = "127.0000",
    type: String? = "노외",
    charge: String? = "유료",
    basicTime: String? = "30",
    basicCharge: String? = "1000",
    addTime: String? = "10",
    addCharge: String? = "500",
    dayTicket: String? = "0",
    weekday: Pair<String?, String?> = "09:00" to "18:00",
    saturday: Pair<String?, String?> = "09:00" to "18:00",
    holiday: Pair<String?, String?> = "09:00" to "18:00",
    operDay: String? = "평일+토요일+공휴일",
): JSONObject = JSONObject().apply {
    put("prkplceNo", no)
    putOpt("prkplceNm", name)
    putOpt("latitude", lat)
    putOpt("longitude", lon)
    putOpt("prkplceType", type)
    put("rdnmadr", "광주광역시 광산구 상무대로 $no")
    put("institutionNm", "광주광역시 광산구")
    putOpt("parkingchrgeInfo", charge)
    putOpt("basicTime", basicTime)
    putOpt("basicCharge", basicCharge)
    putOpt("addUnitTime", addTime)
    putOpt("addUnitCharge", addCharge)
    putOpt("dayCmmtkt", dayTicket)
    putOpt("weekdayOperOpenHhmm", weekday.first)
    putOpt("weekdayOperColseHhmm", weekday.second)
    putOpt("satOperOperOpenHhmm", saturday.first)
    putOpt("satOperCloseHhmm", saturday.second)
    putOpt("holidayOperOpenHhmm", holiday.first)
    putOpt("holidayCloseOpenHhmm", holiday.second)
    putOpt("operDay", operDay)
}

/** 정상 응답 한 쪽. 형식은 2026-11-09 실제 응답과 같다(최상위 header·body, items.item 배열, 숫자 totalCount). */
fun page(total: Int, vararg rows: JSONObject): String = JSONObject()
    .put("header", JSONObject().put("resultCode", "00").put("resultMsg", "NORMAL SERVICE."))
    .put(
        "body",
        JSONObject().put("items", JSONObject().put("item", JSONArray(rows.toList()))).put("numOfRows", 1000).put("pageNo", 1).put("totalCount", total),
    )
    .toString()

/** 마지막 쪽 다음 쪽의 실제 응답. */
const val NO_DATA = """{"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},"body":null}"""

/** 2026-11-09 받은 실제 응답 첫 쪽(3행, totalCount 18994). */
fun realPage1(): String = checkNotNull(FakeApi::class.java.getResource("/public-parking-page1.json")).readText()

/** 2026-11-09 키 없이 호출해 받은 실제 인증 오류 응답. */
const val AUTH_ERROR = """{"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR","returnAuthMsg":"등록되지 않은 서비스키","returnReasonCode":"30"}}}"""

class FakeApi(private val respond: (pageNo: Int) -> String) : PublicParkingApi {
    val calls = mutableListOf<Int>()

    override suspend fun page(pageNo: Int, rows: Int): String {
        synchronized(calls) { calls += pageNo }
        // 받는 도중 다른 코루틴이 끼어들 수 있게 한 번 양보한다(동시 호출 테스트)
        yield()
        return respond(pageNo)
    }
}
