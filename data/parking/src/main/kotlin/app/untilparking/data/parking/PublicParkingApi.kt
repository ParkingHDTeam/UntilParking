package app.untilparking.data.parking

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 「전국주차장정보표준데이터」 오픈API 한 쪽을 받아 응답 본문을 그대로 돌려준다.
 * 응답 규격(봉투·결과 코드·행)은 [PublicParkingRepository]가 해석한다.
 */
fun interface PublicParkingApi {
    /** 네트워크·HTTP 오류면 IOException. */
    suspend fun page(pageNo: Int, rows: Int): String
}

/** serviceKey는 공공데이터포털의 Decoding 키다. 여기서 한 번 URL 인코딩한다. */
class HttpPublicParkingApi(
    private val serviceKey: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PublicParkingApi {

    override suspend fun page(pageNo: Int, rows: Int): String = withContext(io) {
        val query = listOf("serviceKey" to serviceKey, "pageNo" to "$pageNo", "numOfRows" to "$rows", "type" to "json")
            .joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val connection = URL("$BASE_URL?$query").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://api.data.go.kr/openapi/tn_pubr_prkplce_info_api"
    }
}
