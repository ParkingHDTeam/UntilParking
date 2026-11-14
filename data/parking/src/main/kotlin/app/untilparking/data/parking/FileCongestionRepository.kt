package app.untilparking.data.parking

import app.untilparking.domain.AppError
import app.untilparking.domain.model.CongestionLevel
import app.untilparking.domain.model.CongestionReport
import app.untilparking.domain.repository.CongestionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * 혼잡도 입력을 기기 파일에 둔다(혼잡도 스펙 C5). 이 기기에서 남긴 입력만 보인다.
 * 서버가 생기면 이 구현을 공용 저장소로 바꾼다.
 */
class FileCongestionRepository(private val file: File) : CongestionRepository {

    private val mutex = Mutex()
    private var cached: List<CongestionReport>? = null

    override suspend fun reports(parkingId: String): List<CongestionReport> = mutex.withLock { all() }.filter { it.parkingId == parkingId }

    override suspend fun submit(report: CongestionReport) = mutex.withLock {
        val next = all() + report
        try {
            withContext(Dispatchers.IO) { save(next) }
        } catch (e: IOException) {
            throw AppError.CongestionFailed("혼잡도를 저장하지 못했습니다", e)
        }
        cached = next
    }

    private suspend fun all(): List<CongestionReport> = cached ?: withContext(Dispatchers.IO) { read() }.also { cached = it }

    private fun read(): List<CongestionReport> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val rows = JSONObject(file.readText()).getJSONArray("reports")
            List(rows.length()) { rows.getJSONObject(it) }.mapNotNull { row ->
                // 모르는 단계는 그 줄만 버린다
                val level = CongestionLevel.entries.firstOrNull { it.name == row.getString("level") } ?: return@mapNotNull null
                CongestionReport(row.getString("parkingId"), level, Instant.ofEpochMilli(row.getLong("reportedAt")))
            }
        }
    } catch (e: IOException) {
        emptyList()
    } catch (e: JSONException) {
        // 깨진 저장본은 없는 것으로 본다. 다음 저장이 새로 쓴다
        emptyList()
    }

    /** 임시 파일에 쓰고 바꿔 끼워 일부만 남지 않게 한다. */
    private fun save(reports: List<CongestionReport>) {
        val rows = reports.map { JSONObject().put("parkingId", it.parkingId).put("level", it.level.name).put("reportedAt", it.reportedAt.toEpochMilli()) }
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(JSONObject().put("reports", JSONArray(rows)).toString())
        if (!temp.renameTo(file)) throw IOException("저장본을 바꾸지 못했습니다")
    }
}
