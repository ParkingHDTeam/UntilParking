package app.untilparking.data.parking

import app.untilparking.domain.AppError
import app.untilparking.domain.model.AvailabilityReading
import app.untilparking.domain.model.ParkingFacility
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.xml.sax.SAXException
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 실시간 잔여 스펙 V3. 출처마다 마지막 결과(짝 맞춘 행 또는 실패)를 [KEEP] 동안 쓴다.
 * 후보마다 묻기 때문에, 보관하지 않으면 한 번 찾을 때 같은 출처를 후보 수만큼 부른다.
 */
class RealtimeAvailability(private val sources: List<RealtimeSource>, private val clock: Clock) {

    private class Kept(val receivedAt: Instant, val matched: Map<String, RealtimeRow>, val failure: Exception?)

    private val kept = ConcurrentHashMap<RealtimeSource, Kept>()
    private val locks = sources.associateWith { Mutex() }

    /**
     * 시설이 어느 출처 지역에도 없거나 짝이 없으면 null(R4 UNAVAILABLE).
     * 짝이 없고 덮는 출처 중 실패한 것이 있으면 AppError.ParkingSourceFailed(R4 ERROR).
     * @param facilities 짝 맞추기에 쓰는 전체 시설
     */
    suspend fun reading(facility: ParkingFacility, facilities: List<ParkingFacility>): AvailabilityReading? {
        val results = sources.filter { it.covers(facility) }.map { kept(it, facilities) }
        val found = results.mapNotNull { result ->
            result.matched[facility.id]?.let { AvailabilityReading(it.availableSpaces, it.sourceUpdatedAt, result.receivedAt) }
        }
        found.maxByOrNull { it.sourceUpdatedAt ?: it.receivedAt }?.let { return it }
        results.firstNotNullOfOrNull { it.failure }?.let { throw AppError.ParkingSourceFailed("실시간 잔여를 받지 못했습니다", it) }
        return null
    }

    private suspend fun kept(source: RealtimeSource, facilities: List<ParkingFacility>): Kept = locks.getValue(source).withLock {
        kept[source]?.takeIf { Duration.between(it.receivedAt, clock.instant()) < KEEP }?.let { return it }
        val fresh = try {
            val rows = source.fetch()
            Kept(clock.instant(), RealtimeMatcher.match(rows, facilities, source.regions), null)
        } catch (e: IOException) {
            Kept(clock.instant(), emptyMap(), e)
        } catch (e: JSONException) {
            Kept(clock.instant(), emptyMap(), e)
        } catch (e: SAXException) {
            Kept(clock.instant(), emptyMap(), e)
        }
        fresh.also { kept[source] = it }
    }

    private companion object {
        /** R4의 "수신 2분"보다 짧게 둔다 [가설: 30초]. */
        val KEEP: Duration = Duration.ofSeconds(30)
    }
}
