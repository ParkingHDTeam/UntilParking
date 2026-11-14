package app.untilparking.data.parking

import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.ParkingFacility
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/** 실시간 출처가 준 주차장 한 곳. 판정 전 (실시간 잔여 스펙 V1). */
data class RealtimeRow(
    val name: String,
    val availableSpaces: Int,
    val sourceUpdatedAt: Instant? = null,
    /** 좌표를 주는 출처만. 있으면 짝 맞추기에 거리 조건이 붙는다(스펙 V2). */
    val location: GeoPoint? = null,
)

/**
 * 실시간 잔여 출처 하나 (스펙 V1).
 * @param regions 이 출처가 다루는 주소 앞부분. 시설 주소가 이것으로 시작해야 부르고 짝짓는다.
 * @param fetch 그 출처가 아는 행 전부. 네트워크·형식 오류면 IOException·JSONException·SAXException.
 */
class RealtimeSource(
    val name: String,
    val regions: List<String>,
    val fetch: suspend () -> List<RealtimeRow>,
) {
    fun covers(facility: ParkingFacility): Boolean = facility.isIn(regions)
}

internal fun ParkingFacility.isIn(regions: List<String>): Boolean = regions.any { address?.startsWith(it) == true }

/** URL 하나를 받아 본문을 그대로 돌려준다. 응답 규격은 [ApiRows]가 해석한다. */
fun interface TextFetcher {
    /** 네트워크·HTTP 오류면 IOException. */
    suspend fun get(url: String): String
}

class HttpTextFetcher(private val io: CoroutineDispatcher = Dispatchers.IO) : TextFetcher {
    override suspend fun get(url: String): String = withContext(io) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** 교통안전공단 실시간 주차장 한 곳의 이름·좌표. 시설정보에서 뽑아 둔 표에서 읽는다 (스펙 V1). */
internal data class TsLot(val name: String, val location: GeoPoint)

/** 교통안전공단 시도 하나의 실시간 주차장. [regions]는 그 시도의 주소 앞부분. */
internal class TsSido(val code: String, val regions: List<String>, val lots: Map<String, TsLot>)

/** scripts/build_ts_realtime_lots.py가 만든 리소스 ts-realtime-lots.json을 읽는다. */
internal object TsRealtimeLots {

    fun parse(json: String): List<TsSido> {
        val sidos = JSONObject(json).getJSONArray("sidos")
        return List(sidos.length()) { index ->
            val sido = sidos.getJSONObject(index)
            val names = sido.getJSONArray("names")
            val lots = sido.getJSONArray("lots")
            TsSido(
                code = sido.getString("code"),
                regions = List(names.length()) { names.getString(it) },
                lots = (0 until lots.length()).map { lots.getJSONArray(it) }
                    .associate { it.getString(0) to TsLot(it.getString(1), GeoPoint(it.getDouble(2), it.getDouble(3))) },
            )
        }
    }

    fun load(): List<TsSido> = parse(checkNotNull(TsRealtimeLots::class.java.getResource("/ts-realtime-lots.json")).readText())
}

/** pleos 빌드의 실시간 출처 목록 (스펙 V1, V4). 키가 비면 출처가 없다. */
object RealtimeSources {

    /** @param dataGoKrKey 공공데이터포털 Decoding 키. 부산시설공단·대전·교통안전공단 활용신청이 되어 있어야 한다. */
    fun create(dataGoKrKey: String, http: TextFetcher): List<RealtimeSource> = create(dataGoKrKey, http, TsRealtimeLots.load())

    internal fun create(dataGoKrKey: String, http: TextFetcher, ts: List<TsSido>): List<RealtimeSource> {
        if (dataGoKrKey.isBlank()) return emptyList()
        fun url(path: String, rows: Int, extra: String = "") = { page: Int ->
            "https://apis.data.go.kr/$path?serviceKey=${encode(dataGoKrKey)}&pageNo=$page&numOfRows=$rows$extra"
        }
        val busan = RealtimeSource("부산시설공단", listOf("부산광역시")) {
            pages(http, url("B552587/ParkingInfoService_v2/getParkingInfoList_v2", 1000)).mapNotNull(RealtimeParsers::busan)
        }
        // 대전은 numOfRows를 크게 줘도 한 쪽에 50건만 준다(2026-11-14 확인)
        val daejeon = RealtimeSource("대전", listOf("대전광역시")) {
            pages(http, url("6300000/pis/parkinglotIF", 50)).mapNotNull(RealtimeParsers::daejeon)
        }
        val nationwide = ts.map { sido ->
            RealtimeSource("교통안전공단 ${sido.code}", sido.regions) {
                pages(http, url("B553881/Parking_v1/PrkRealtimeInfo_v1", 1000, "&format=2&sidoCd=${sido.code}")).mapNotNull { RealtimeParsers.ts(it, sido.lots) }
            }
        }
        return listOf(busan, daejeon) + nationwide
    }

    /** 첫 쪽의 totalCount를 보고 나머지 쪽을 한꺼번에 받는다. */
    private suspend fun pages(http: TextFetcher, url: (Int) -> String): List<Map<String, String>> {
        val first = ApiRows.page(http.get(url(1)))
        val size = first.items.size
        val total = first.totalCount ?: return first.items
        if (size == 0 || total <= size) return first.items
        val rest = coroutineScope { (2..(total + size - 1) / size).map { page -> async { ApiRows.items(http.get(url(page))) } }.awaitAll() }
        return first.items + rest.flatten()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
