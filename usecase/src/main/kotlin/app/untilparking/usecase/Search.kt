package app.untilparking.usecase

import app.untilparking.domain.AppError
import app.untilparking.domain.model.Availability
import app.untilparking.domain.model.AvailabilityStatus
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationParkingLink
import app.untilparking.domain.model.Distance
import app.untilparking.domain.model.DistanceKind
import app.untilparking.domain.model.EligibilityStatus
import app.untilparking.domain.model.OperatingSchedule
import app.untilparking.domain.model.OperatingStatus
import app.untilparking.domain.model.ParkingCandidate
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.Recheck
import app.untilparking.domain.model.RecheckStatus
import app.untilparking.domain.model.SearchConditions
import app.untilparking.domain.model.SearchResult
import app.untilparking.domain.model.VehicleProfile
import app.untilparking.domain.model.distanceMetersTo
import app.untilparking.domain.repository.ParkingRepository
import app.untilparking.domain.usecase.EstimateFee
import app.untilparking.domain.usecase.JudgeAvailability
import app.untilparking.domain.usecase.JudgeEligibility
import app.untilparking.domain.usecase.RecheckParking
import app.untilparking.domain.usecase.SearchParking
import app.untilparking.domain.usecase.SelectCandidates
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** 시설 하나를 후보 하나로 만든다. 검색과 재조회가 같은 조립을 쓴다. */
internal class CandidateBuilder(
    private val repo: ParkingRepository,
    private val judgeEligibility: JudgeEligibility,
    private val judgeAvailability: JudgeAvailability,
    private val estimateFee: EstimateFee,
    private val clock: Clock,
) {
    suspend fun build(
        facility: ParkingFacility,
        link: DestinationParkingLink?,
        destination: Destination,
        conditions: SearchConditions,
        profile: VehicleProfile,
    ): ParkingCandidate {
        val now = clock.instant()
        // 시작 시각은 내비 도착 예정 시각, 없으면 지금(스펙 R6)
        val startAt = destination.etaAt ?: now
        val endAt = startAt.plus(conditions.parkingMinutes.toLong(), ChronoUnit.MINUTES)
        val availability = try {
            judgeAvailability(repo.availability(facility.id), now)
        } catch (e: AppError) {
            Availability(AvailabilityStatus.ERROR)
        }
        return ParkingCandidate(
            parking = facility,
            eligibility = judgeEligibility(facility, profile, now.atZone(clock.zone).toLocalDate()),
            // 보행 경로 공급자가 없어 직선 거리다. 도보 시간으로 환산하지 않는다(스펙 R2)
            distance = Distance(destination.location.distanceMetersTo(facility.location).roundToInt(), DistanceKind.STRAIGHT),
            fee = estimateFee(facility.feeModel, startAt, conditions.parkingMinutes),
            availability = availability,
            link = link,
            operating = operatingStatus(facility.operatingSchedule, startAt, endAt),
        )
    }

    private fun operatingStatus(schedule: OperatingSchedule?, startAt: Instant, endAt: Instant): OperatingStatus {
        if (schedule == null) return OperatingStatus.UNKNOWN
        if (schedule.allDay) return OperatingStatus.OPEN
        val start = startAt.atZone(clock.zone)
        val end = endAt.atZone(clock.zone)
        // 일자 경계를 넘는 주차는 운영 시간 판정을 하지 않는다 [가설]
        if (start.toLocalDate() != end.toLocalDate()) return OperatingStatus.UNKNOWN
        val inside = !start.toLocalTime().isBefore(schedule.open) && !end.toLocalTime().isAfter(schedule.close)
        return if (inside) OperatingStatus.OPEN else OperatingStatus.CLOSED
    }
}

class SearchParkingImpl(
    private val repo: ParkingRepository,
    judgeEligibility: JudgeEligibility,
    judgeAvailability: JudgeAvailability,
    estimateFee: EstimateFee,
    private val select: SelectCandidates,
    clock: Clock,
) : SearchParking {

    private val builder = CandidateBuilder(repo, judgeEligibility, judgeAvailability, estimateFee, clock)

    override suspend fun invoke(destination: Destination, conditions: SearchConditions, profile: VehicleProfile): SearchResult {
        val links = repo.linkedParking(destination)
        val linkById = links.associate { (facility, link) -> facility.id to link }
        // 연결 주차장은 반경과 무관하게 S01에 보인다
        val linked = links.map { (facility, link) -> builder.build(facility, link, destination, conditions, profile) }
        // 검색 중심은 방문 목적지다. 현재 차량 위치를 쓰지 않는다(스펙 R2)
        val nearby = repo.nearby(destination.location, conditions.radiusM)
            .map { facility -> builder.build(facility, linkById[facility.id], destination, conditions, profile) }
        return SearchResult(select(nearby, conditions), conditions.radiusM, linked)
    }
}

class RecheckParkingImpl(
    repo: ParkingRepository,
    judgeEligibility: JudgeEligibility,
    judgeAvailability: JudgeAvailability,
    estimateFee: EstimateFee,
    private val clock: Clock,
) : RecheckParking {

    private val builder = CandidateBuilder(repo, judgeEligibility, judgeAvailability, estimateFee, clock)

    override suspend fun invoke(
        candidate: ParkingCandidate,
        conditions: SearchConditions,
        profile: VehicleProfile,
        destination: Destination,
    ): Recheck {
        val now = clock.instant()
        val fresh = builder.build(candidate.parking, candidate.link, destination, conditions, profile)
        val status = when {
            fresh.eligibility.status == EligibilityStatus.BLOCKED -> RecheckStatus.BLOCKED
            fresh.availability.isFreshFull -> RecheckStatus.FULL
            fresh.availability.status == AvailabilityStatus.LIVE || fresh.availability.status == AvailabilityStatus.RECEIVED -> RecheckStatus.READY
            // STALE·UNAVAILABLE·ERROR: 경고에 동의해야 계속할 수 있다
            else -> RecheckStatus.UNKNOWN
        }
        return Recheck(
            snapshotId = "snap-${now.toEpochMilli()}-${candidate.parking.id}",
            candidate = fresh,
            status = status,
            expiresAt = now.plusSeconds(CONFIRM_VALID_SECONDS),
        )
    }

    private companion object {
        const val CONFIRM_VALID_SECONDS = 30L
    }
}
