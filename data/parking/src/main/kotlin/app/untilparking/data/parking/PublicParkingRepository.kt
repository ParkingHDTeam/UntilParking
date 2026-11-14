package app.untilparking.data.parking

import app.untilparking.domain.AppError
import app.untilparking.domain.model.AvailabilityReading
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationParkingLink
import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.distanceMetersTo
import app.untilparking.domain.repository.ParkingRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * pleos 빌드의 주차장 데이터 (공공 데이터 스펙 P1).
 * API가 좌표 반경 조건을 받지 않아 전국 데이터를 한 번 받아 [file]에 두고, 반경 검색은 기기에서 한다.
 * @param api 인증키가 없으면 null
 * @param realtime 실시간 잔여 출처. 없으면 잔여는 늘 미제공이다(실시간 잔여 스펙 V3)
 */
class PublicParkingRepository(
    private val api: PublicParkingApi?,
    private val file: File,
    private val clock: Clock,
    private val realtime: RealtimeAvailability? = null,
) : ParkingRepository {

    private val mutex = Mutex()

    @Volatile
    private var facilities: List<ParkingFacility>? = null

    // 공공 데이터에는 목적지와 주차장의 관계가 없다. 검증된 관계만 보여 주므로 비운다(스펙 P2, R2)
    override suspend fun linkedParking(destination: Destination): List<Pair<ParkingFacility, DestinationParkingLink>> = emptyList()

    override suspend fun nearby(center: GeoPoint, radiusMeters: Int): List<ParkingFacility> =
        facilities().filter { center.distanceMetersTo(it.location) <= radiusMeters }

    // 표준데이터에는 잔여 대수가 없다. 실시간 출처에 짝이 있는 시설만 채운다(실시간 잔여 스펙 V3), 나머지는 R4 UNAVAILABLE
    override suspend fun availability(parkingId: String): AvailabilityReading? {
        val realtime = realtime ?: return null
        val all = facilities()
        return all.firstOrNull { it.id == parkingId }?.let { realtime.reading(it, all) }
    }

    private suspend fun facilities(): List<ParkingFacility> = facilities ?: mutex.withLock {
        facilities ?: load().also { facilities = it }
    }

    private suspend fun load(): List<ParkingFacility> {
        val saved = readSaved()
        if (saved != null && Duration.between(saved.fetchedAt, clock.instant()) < MAX_AGE) return toFacilities(saved.rows)
        val rows = try {
            download().also { save(Saved(clock.instant(), it)) }
        } catch (e: IOException) {
            saved?.rows ?: throw AppError.ParkingSourceFailed("공공 주차장 데이터를 받지 못했습니다", e)
        } catch (e: JSONException) {
            saved?.rows ?: throw AppError.ParkingSourceFailed("공공 주차장 응답을 읽지 못했습니다", e)
        } catch (e: AppError.ParkingSourceFailed) {
            // 옛 저장본이 있으면 그것으로 찾는다 [가설]
            saved?.rows ?: throw e
        }
        return toFacilities(rows)
    }

    private suspend fun download(): List<JSONObject> {
        val api = api ?: throw AppError.ParkingSourceFailed("공공데이터포털 인증키가 없습니다")
        val rows = mutableListOf<JSONObject>()
        var pageNo = 1
        while (true) {
            val (total, items) = parsePage(api.page(pageNo, PAGE_ROWS))
            rows += items.map(::trim)
            if (items.isEmpty() || rows.size >= total) return rows
            pageNo++
        }
    }

    /**
     * 응답 → (전체 건수, 이 쪽의 행). 2026-11-09 실제 응답 형식:
     * `{"header":{"resultCode":"00"},"body":{"items":{"item":[...]},"totalCount":18994}}`, 마지막 다음 쪽은 resultCode 03.
     * 인증 오류는 `OpenAPI_ServiceResponse` 봉투로 온다.
     */
    private fun parsePage(body: String): Pair<Int, List<JSONObject>> {
        val root = JSONObject(body)
        root.optJSONObject("OpenAPI_ServiceResponse")?.let { error ->
            val reason = error.optJSONObject("cmmMsgHeader")?.optString("errMsg").orEmpty()
            throw AppError.ParkingSourceFailed("공공데이터포털 오류 $reason")
        }
        when (val code = root.getJSONObject("header").optString("resultCode")) {
            "00" -> Unit
            "03" -> return 0 to emptyList() // NODATA_ERROR
            else -> throw AppError.ParkingSourceFailed("공공데이터포털 결과 코드 $code")
        }
        val content = root.getJSONObject("body")
        val total = content.optInt("totalCount", -1).takeIf { it >= 0 } ?: throw JSONException("totalCount 없음")
        val items = content.optJSONObject("items")?.optJSONArray("item") ?: JSONArray()
        return total to List(items.length()) { items.getJSONObject(it) }
    }

    private fun trim(row: JSONObject) = JSONObject().apply {
        PublicParkingRow.KEYS.forEach { key -> if (!row.isNull(key)) put(key, row.get(key)) }
    }

    private fun toFacilities(rows: List<JSONObject>): List<ParkingFacility> =
        rows.mapNotNull(PublicParkingRow::toFacility).distinctBy { it.id }

    private class Saved(val fetchedAt: Instant, val rows: List<JSONObject>)

    private fun readSaved(): Saved? = try {
        if (!file.exists()) {
            null
        } else {
            val root = JSONObject(file.readText())
            val rows = root.getJSONArray("rows")
            Saved(Instant.ofEpochMilli(root.getLong("fetchedAt")), List(rows.length()) { rows.getJSONObject(it) })
        }
    } catch (e: IOException) {
        null
    } catch (e: JSONException) {
        // 깨진 저장본은 없는 것으로 본다
        null
    }

    /** 다 받은 뒤에만 부른다. 임시 파일에 쓰고 바꿔 끼워 일부만 남지 않게 한다. */
    private fun save(saved: Saved) {
        val json = JSONObject().put("fetchedAt", saved.fetchedAt.toEpochMilli()).put("rows", JSONArray(saved.rows))
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(file)) throw IOException("저장본을 바꾸지 못했습니다")
    }

    private companion object {
        /** 표준데이터는 반기 갱신 [가설: 30일]. */
        val MAX_AGE: Duration = Duration.ofDays(30)

        /** 한 쪽 행 수 [가설: 상한]. */
        const val PAGE_ROWS = 1000
    }
}
