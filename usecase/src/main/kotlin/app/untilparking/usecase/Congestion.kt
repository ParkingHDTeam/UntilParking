package app.untilparking.usecase

import app.untilparking.domain.model.ArrivalPrompt
import app.untilparking.domain.model.Congestion
import app.untilparking.domain.model.CongestionLevel
import app.untilparking.domain.model.CongestionReport
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationChange
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.PendingArrival
import app.untilparking.domain.repository.CongestionRepository
import app.untilparking.domain.usecase.DetectArrival
import app.untilparking.domain.usecase.JudgeArrivalPrompt
import app.untilparking.domain.usecase.ReportCongestion
import app.untilparking.domain.usecase.SummarizeCongestion
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** 혼잡도 스펙 C4. */
class SummarizeCongestionImpl : SummarizeCongestion {
    override fun invoke(reports: List<CongestionReport>, now: Instant): Congestion? {
        // 정확히 2시간 전까지 넣는다
        val recent = reports.filter { Duration.between(it.reportedAt, now) <= WINDOW }
        if (recent.isEmpty()) return null
        // 한가운데(1.5, 2.5)는 더 붐비는 쪽으로 올린다 [가설]
        val score = (2 * recent.sumOf { it.level.score } + recent.size) / (2 * recent.size)
        return Congestion(CongestionLevel.entries.first { it.score == score }, recent.size)
    }

    private companion object {
        /** 혼잡도는 시간대마다 달라 최근 것만 쓴다 [가설: 2시간]. */
        val WINDOW: Duration = Duration.ofHours(2)
    }
}

/** 혼잡도 스펙 C1. */
class DetectArrivalImpl : DetectArrival {
    override fun invoke(change: DestinationChange, next: Destination?, guiding: ParkingFacility?, now: Instant): PendingArrival? {
        // 도착 전용 이벤트가 없어 "안내 중에 목적지가 없어짐"을 도착으로 본다. 안내 취소도 여기로 온다(한계).
        if (change != DestinationChange.NEW_VISIT || next != null) return null
        return guiding?.let { PendingArrival(it.id, it.name, now) }
    }
}

/** 혼잡도 스펙 C2. */
class JudgeArrivalPromptImpl : JudgeArrivalPrompt {
    override fun invoke(pending: PendingArrival, driving: DrivingState, now: Instant): ArrivalPrompt = when {
        Duration.between(pending.at, now) > VALID -> ArrivalPrompt.DROP
        // 주행 상태를 모르면 정차로 간주하지 않는다(R9)
        driving == DrivingState.PARKED -> ArrivalPrompt.ASK
        else -> ArrivalPrompt.WAIT
    }

    private companion object {
        /** 이보다 늦게 세운 차는 다른 곳에 세웠을 수 있다 [가설: 10분]. */
        val VALID: Duration = Duration.ofMinutes(10)
    }
}

/** 혼잡도 스펙 C3. */
class ReportCongestionImpl(private val repo: CongestionRepository, private val clock: Clock) : ReportCongestion {
    override suspend fun invoke(parkingId: String, level: CongestionLevel) = repo.submit(CongestionReport(parkingId, level, clock.instant()))
}
