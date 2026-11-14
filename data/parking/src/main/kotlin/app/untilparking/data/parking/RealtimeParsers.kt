package app.untilparking.data.parking

import app.untilparking.domain.model.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/** 응답 한 쪽. [totalCount]는 전체 행 수, 응답에 없으면 null. */
internal data class ApiPage(val items: List<Map<String, String>>, val totalCount: Int?)

/**
 * 공공 API 응답 → item 행 (실시간 잔여 스펙 V1). 부산·대전은 XML, 교통안전공단은 JSON(format=2)이다(2026-11-14 실제 응답).
 * 실패 봉투·오류 결과 코드면 IOException, 형식이 깨졌으면 JSONException·SAXException.
 */
internal object ApiRows {

    private val OK = setOf("00", "0")
    private const val NO_DATA = "03"

    fun items(body: String): List<Map<String, String>> = page(body).items

    fun page(body: String): ApiPage {
        val text = body.trim()
        return if (text.startsWith("<")) xml(text) else json(JSONObject(text))
    }

    /** null이면 결과 코드가 없는 응답이다. NODATA면 false. */
    private fun proceed(code: String?): Boolean = when (code) {
        null, in OK -> true
        NO_DATA -> false
        else -> throw IOException("결과 코드 $code")
    }

    private fun xml(text: String): ApiPage {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(text)))
        if (doc.documentElement.tagName == "OpenAPI_ServiceResponse") throw IOException("공공데이터포털 오류 ${doc.firstText("errMsg")}")
        if (!proceed(doc.firstText("resultCode"))) return ApiPage(emptyList(), 0)
        val items = doc.getElementsByTagName("item")
        return ApiPage(List(items.length) { (items.item(it) as Element).fields() }, doc.firstText("totalCount")?.toIntOrNull())
    }

    private fun Document.firstText(tag: String): String? = getElementsByTagName(tag).item(0)?.textContent?.trim()

    private fun Element.fields(): Map<String, String> {
        val children = childNodes
        return (0 until children.length).map { children.item(it) }.filterIsInstance<Element>().associate { it.tagName to it.textContent.trim() }
    }

    /** 교통안전공단: {"resultCode":"0","totalCount":"4648","PrkRealtimeInfo":[..]}. 행은 최상위의 배열 하나다. */
    private fun json(root: JSONObject): ApiPage {
        root.optJSONObject("OpenAPI_ServiceResponse")?.let { error ->
            throw IOException("공공데이터포털 오류 ${error.optJSONObject("cmmMsgHeader")?.optString("errMsg")}")
        }
        if (!proceed(root.optString("resultCode").takeIf { it.isNotEmpty() })) return ApiPage(emptyList(), 0)
        val rows = root.keys().asSequence().mapNotNull { root.optJSONArray(it) }.firstOrNull() ?: JSONArray()
        return ApiPage(List(rows.length()) { rows.getJSONObject(it).fields() }, root.optString("totalCount").toIntOrNull())
    }

    private fun JSONObject.fields(): Map<String, String> = keys().asSequence().filterNot(::isNull).associateWith { optString(it).trim() }
}

/** 출처별 item 한 행 → [RealtimeRow] (스펙 V1). 쓸 수 없는 행은 null. 필드는 2026-11-14 실제 응답으로 확인했다. */
internal object RealtimeParsers {

    private val KST: ZoneId = ZoneId.of("Asia/Seoul")

    /** 부산시설공단 getParkingInfoList_v2. curravacnt = 현재 주차 가능 대수. */
    fun busan(row: Map<String, String>): RealtimeRow? = realtimeRow(row["parknm"], row.int("curravacnt"), time(row["lastupdatetime"]))

    /** 대전 parkinglotIF. 756건 중 totalQty가 있는 곳만 실시간이 들어온다. 나머지는 resQty가 0이라 만차로 읽으면 안 된다. */
    fun daejeon(row: Map<String, String>): RealtimeRow? {
        val total = row.int("totalQty")?.takeIf { it > 0 } ?: return null
        return realtimeRow(row["name"], row.int("resQty")?.coerceAtMost(total), location = point(row["lat"], row["lon"]))
    }

    /**
     * 교통안전공단 PrkRealtimeInfo_v1. 이름·좌표는 응답에 없어 [lots](시설정보에서 뽑아 둔 표)에서 ID로 찾는다.
     * 총면수가 0이면 실시간이 없는 곳이다. 가능 대수가 총면수보다 큰 행이 있어 총면수로 자른다.
     */
    fun ts(row: Map<String, String>, lots: Map<String, TsLot>): RealtimeRow? {
        val lot = lots[row["prk_center_id"]] ?: return null
        val total = row.int("pkfc_ParkingLots_total")?.takeIf { it > 0 } ?: return null
        return realtimeRow(lot.name, row.int("pkfc_Available_ParkingLots_total")?.coerceAtMost(total), location = lot.location)
    }

    private fun realtimeRow(name: String?, available: Int?, updatedAt: Instant? = null, location: GeoPoint? = null): RealtimeRow? {
        val trimmed = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return RealtimeRow(trimmed, maxOf(0, available ?: return null), updatedAt, location)
    }

    private fun Map<String, String>.int(key: String): Int? = get(key)?.trim()?.toDoubleOrNull()?.toInt()

    /** 0이거나 한반도 밖이면 좌표 없음으로 본다. */
    private fun point(lat: String?, lon: String?): GeoPoint? {
        val la = lat?.toDoubleOrNull() ?: return null
        val lo = lon?.toDoubleOrNull() ?: return null
        return if (la in 33.0..39.0 && lo in 124.0..132.0) GeoPoint(la, lo) else null
    }

    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 원본 갱신 시각은 한국 시각으로 읽는다. 형식이 다르면 없음. */
    private fun time(text: String?): Instant? {
        val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { LocalDateTime.parse(value, TIME_FORMAT) }.getOrNull()?.atZone(KST)?.toInstant()
    }
}
