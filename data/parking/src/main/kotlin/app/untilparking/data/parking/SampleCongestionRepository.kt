package app.untilparking.data.parking

import app.untilparking.domain.model.CongestionLevel
import app.untilparking.domain.model.CongestionReport
import app.untilparking.domain.repository.CongestionRepository
import java.time.Clock
import java.time.Duration

/** mock 빌드의 혼잡도: 다른 운전자가 남긴 것처럼 보이는 가상 입력에 이 기기의 입력을 더한다(혼잡도 스펙 C5). */
class SampleCongestionRepository(private val device: CongestionRepository, private val clock: Clock) : CongestionRepository {

    override suspend fun reports(parkingId: String): List<CongestionReport> {
        val now = clock.instant()
        // 시각을 읽을 때마다 지금 기준으로 만들어 가상 입력이 낡지 않게 한다
        val sample = SAMPLE[parkingId].orEmpty().map { (level, minutesAgo) -> CongestionReport(parkingId, level, now.minus(Duration.ofMinutes(minutesAgo))) }
        return sample + device.reports(parkingId)
    }

    override suspend fun submit(report: CongestionReport) = device.submit(report)

    private companion object {
        /** 주차장 ID → (단계, 몇 분 전). SampleParkingRepository의 시설이다. */
        val SAMPLE: Map<String, List<Pair<CongestionLevel, Long>>> = mapOf(
            // 서림 공영: 평균 2.67 → 혼잡
            "pk-001" to listOf(CongestionLevel.CONGESTED to 20L, CongestionLevel.CONGESTED to 45L, CongestionLevel.NORMAL to 70L),
            // 중앙 노상: 원활
            "pk-002" to listOf(CongestionLevel.SMOOTH to 30L),
            // 문화광장: 평균 1.5 → 보통
            "pk-003" to listOf(CongestionLevel.NORMAL to 15L, CongestionLevel.SMOOTH to 50L),
        )
    }
}
