package app.untilparking.usecase

import app.untilparking.domain.model.Availability
import app.untilparking.domain.model.AvailabilityReading
import app.untilparking.domain.model.AvailabilityStatus
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationChange
import app.untilparking.domain.model.Eligibility
import app.untilparking.domain.model.EligibilityStatus
import app.untilparking.domain.model.FeeEstimate
import app.untilparking.domain.model.FeeModel
import app.untilparking.domain.model.FeeStatus
import app.untilparking.domain.model.Filters
import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.MirrorBasis
import app.untilparking.domain.model.OperatingStatus
import app.untilparking.domain.model.Page
import app.untilparking.domain.model.ParkingCandidate
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.ParkingMethod
import app.untilparking.domain.model.Restriction
import app.untilparking.domain.model.RestrictionField
import app.untilparking.domain.model.RestrictionOperator
import app.untilparking.domain.model.SearchConditions
import app.untilparking.domain.model.SortKey
import app.untilparking.domain.model.SuvFlag
import app.untilparking.domain.model.VehicleField
import app.untilparking.domain.model.VehicleProfile
import app.untilparking.domain.model.VehicleValueInput
import app.untilparking.domain.model.VoiceCommand
import app.untilparking.domain.model.WeightBasis
import app.untilparking.domain.model.distanceMetersTo
import app.untilparking.domain.usecase.ClassifyDestinationChange
import app.untilparking.domain.usecase.EstimateFee
import app.untilparking.domain.usecase.ExpandRadius
import app.untilparking.domain.usecase.JudgeAvailability
import app.untilparking.domain.usecase.JudgeEligibility
import app.untilparking.domain.usecase.PageCandidates
import app.untilparking.domain.usecase.ParseVoiceCommand
import app.untilparking.domain.usecase.SelectCandidates
import app.untilparking.domain.usecase.ValidateVehicleValue
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

/** 스펙 R1. 확인된 위반만 BLOCKED, 비교할 수 없으면 UNKNOWN, 전부 확인·충족일 때만 VERIFIED_MATCH. */
class JudgeEligibilityImpl : JudgeEligibility {

    override fun invoke(parking: ParkingFacility, profile: VehicleProfile, today: LocalDate): Eligibility {
        // 제한 정보가 없는 것은 "제한 없음"이 아니라 "모름"이다 [가설]
        if (parking.restrictions.isEmpty()) return Eligibility(EligibilityStatus.UNKNOWN, listOf("NO_RESTRICTION_DATA"))
        val results = parking.restrictions.map { judge(it, profile, today) }
        val reasons = results.flatMap { it.reasons }
        return when {
            results.any { it.status == EligibilityStatus.BLOCKED } -> Eligibility(EligibilityStatus.BLOCKED, reasons)
            results.all { it.status == EligibilityStatus.VERIFIED_MATCH } -> Eligibility(EligibilityStatus.VERIFIED_MATCH)
            else -> Eligibility(EligibilityStatus.UNKNOWN, reasons)
        }
    }

    private fun judge(r: Restriction, p: VehicleProfile, today: LocalDate): Eligibility {
        val name = r.field.name
        if (r.conflicting) return unknown("${name}_CONFLICT")
        val verifiedAt = r.verifiedAt ?: return unknown("${name}_EXPIRED")
        if (verifiedAt.plusDays(VALID_DAYS).isBefore(today)) return unknown("${name}_EXPIRED")
        if (r.field == RestrictionField.SUV) {
            return when (p.suv) {
                SuvFlag.YES -> blocked("SUV_NOT_ALLOWED")
                SuvFlag.NO -> MATCH
                SuvFlag.UNKNOWN -> unknown("SUV_UNKNOWN")
            }
        }
        val vehicleValue = when (r.field) {
            RestrictionField.HEIGHT_MM -> p.heightMm
            RestrictionField.WIDTH_MM -> p.widthMm
            RestrictionField.LENGTH_MM -> p.lengthMm
            RestrictionField.WEIGHT_KG -> p.weightKg
            RestrictionField.SUV -> null
        } ?: return unknown("${name}_UNKNOWN")
        if (r.field == RestrictionField.WEIGHT_KG && !sameBasis(r.weightBasis, p.weightBasis)) return unknown("WEIGHT_BASIS_MISMATCH")
        if (r.field == RestrictionField.WIDTH_MM && !sameBasis(r.mirrorBasis, p.mirrorBasis)) return unknown("MIRROR_BASIS_MISMATCH")
        val fits = when (r.operator) {
            RestrictionOperator.LTE -> vehicleValue <= r.value
            RestrictionOperator.LT -> vehicleValue < r.value
        }
        return if (fits) MATCH else blocked("${name}_EXCEEDED")
    }

    private fun sameBasis(a: WeightBasis, b: WeightBasis) = a != WeightBasis.UNKNOWN && a == b
    private fun sameBasis(a: MirrorBasis, b: MirrorBasis) = a != MirrorBasis.UNKNOWN && a == b
    private fun unknown(reason: String) = Eligibility(EligibilityStatus.UNKNOWN, listOf(reason))
    private fun blocked(reason: String) = Eligibility(EligibilityStatus.BLOCKED, listOf(reason))

    private companion object {
        const val VALID_DAYS = 30L
        val MATCH = Eligibility(EligibilityStatus.VERIFIED_MATCH)
    }
}

/** 스펙 R4. 원본 시각 5분, 원본 없으면 수신 2분. 정보 없음은 0대가 아니다. */
class JudgeAvailabilityImpl : JudgeAvailability {

    override fun invoke(reading: AvailabilityReading?, now: Instant): Availability {
        if (reading?.availableSpaces == null) {
            return Availability(AvailabilityStatus.UNAVAILABLE, null, reading?.sourceUpdatedAt, reading?.receivedAt)
        }
        val source = reading.sourceUpdatedAt
        val status = if (source != null) {
            if (Duration.between(source, now) <= LIVE_WINDOW) AvailabilityStatus.LIVE else AvailabilityStatus.STALE
        } else {
            if (Duration.between(reading.receivedAt, now) <= RECEIVED_WINDOW) AvailabilityStatus.RECEIVED else AvailabilityStatus.STALE
        }
        return Availability(status, reading.availableSpaces, source, reading.receivedAt)
    }

    private companion object {
        val LIVE_WINDOW: Duration = Duration.ofMinutes(5)
        val RECEIVED_WINDOW: Duration = Duration.ofMinutes(2)
    }
}

/** 스펙 R5. 기본료 + ceil(max(0, 분−기본분)/단위) × 추가료. 일자 경계를 넘으면 산출 불가. */
class EstimateFeeImpl(private val zone: ZoneId) : EstimateFee {

    override fun invoke(feeModel: FeeModel?, startAt: Instant, parkingMinutes: Int): FeeEstimate {
        if (feeModel == null) return FeeEstimate(null, startAt, FeeStatus.UNAVAILABLE)
        val endAt = startAt.plus(parkingMinutes.toLong(), ChronoUnit.MINUTES)
        if (startAt.atZone(zone).toLocalDate() != endAt.atZone(zone).toLocalDate()) return FeeEstimate(null, startAt, FeeStatus.UNAVAILABLE)
        val extra = max(0, parkingMinutes - feeModel.baseMinutes)
        val steps = if (feeModel.stepMinutes <= 0) 0 else (extra + feeModel.stepMinutes - 1) / feeModel.stepMinutes
        val raw = feeModel.baseWon + steps * feeModel.stepWon
        val won = feeModel.maxDailyWon?.let { min(raw, it) } ?: raw
        return FeeEstimate(won, startAt, FeeStatus.ESTIMATED)
    }
}

/** 스펙 R3. 제외 순서: 제한 위반 → 운영 불가 → 신선한 만차 → 사용자 필터. 그다음 정렬, 최대 30개. */
class SelectCandidatesImpl : SelectCandidates {

    override fun invoke(candidates: List<ParkingCandidate>, conditions: SearchConditions): List<ParkingCandidate> {
        val filters = conditions.filters
        val kept = candidates.filter { c ->
            c.eligibility.status != EligibilityStatus.BLOCKED &&
                c.operating != OperatingStatus.CLOSED &&
                !c.availability.isFreshFull &&
                methodAllowed(c.parking, filters) &&
                (filters.includeUnknownAvailability || c.availability.status !in NO_INFO)
        }
        return kept.sortedWith(comparator(conditions.sort)).take(MAX_CANDIDATES)
    }

    private fun methodAllowed(parking: ParkingFacility, filters: Filters): Boolean = when (parking.method) {
        ParkingMethod.SELF -> true
        ParkingMethod.MECHANICAL -> !filters.excludeMechanical
        // 혼합식은 자주식 구역의 별도 입구·조건이 확인된 때만 기계식 제외에서 살아남는다
        ParkingMethod.MIXED -> !filters.excludeMechanical || parking.selfZoneVerified
        ParkingMethod.UNKNOWN -> filters.includeUnknownMethod
    }

    private fun comparator(sort: SortKey): Comparator<ParkingCandidate> {
        val primary: Comparator<ParkingCandidate> = when (sort) {
            SortKey.DISTANCE -> compareBy<ParkingCandidate, Int?>(nullsLast()) { it.distance.meters }
            SortKey.COST -> compareBy<ParkingCandidate, Int?>(nullsLast()) { it.fee.won }
            SortKey.AVAILABILITY -> compareBy<ParkingCandidate, Int?>(nullsLast()) { it.availability.availableSpaces?.let { n -> -n } }
        }
        return primary
            .thenBy<ParkingCandidate, Int?>(nullsLast()) { it.distance.meters }
            .thenBy { it.parking.id }
    }

    private companion object {
        const val MAX_CANDIDATES = 30
        val NO_INFO = setOf(AvailabilityStatus.UNAVAILABLE, AvailabilityStatus.ERROR)
    }
}

/** 스펙 R2. 500 → 1000 → 2000. */
class ExpandRadiusImpl : ExpandRadius {
    override fun invoke(currentRadiusM: Int): Int? = STEPS.firstOrNull { it > currentRadiusM }

    private companion object {
        val STEPS = listOf(500, 1000, 2000)
    }
}

/** 스펙 R8. 정수만, 필드별 범위, 빈 값은 미확인. */
class ValidateVehicleValueImpl : ValidateVehicleValue {

    override fun invoke(field: VehicleField, text: String): VehicleValueInput {
        val t = text.trim()
        if (t.isEmpty()) return VehicleValueInput.Empty
        if (!t.all { it.isDigit() }) return VehicleValueInput.Invalid("숫자만 입력하세요")
        val value = t.toIntOrNull() ?: return VehicleValueInput.Invalid(RANGE_MESSAGE)
        val range = when (field) {
            VehicleField.HEIGHT_MM -> 500..5000
            VehicleField.WIDTH_MM -> 500..3000
            VehicleField.LENGTH_MM -> 1000..12000
            VehicleField.WEIGHT_KG -> 200..10000
        }
        return if (value in range) VehicleValueInput.Value(value) else VehicleValueInput.Invalid(RANGE_MESSAGE)
    }

    private companion object {
        const val RANGE_MESSAGE = "허용 범위를 확인하세요"
    }
}

/** 스펙 R10. 공백을 지우고 제한된 명령 집합만 본다. 복합 발화는 필터가 먼저다. */
class ParseVoiceCommandImpl : ParseVoiceCommand {

    override fun invoke(utterance: String, visibleCount: Int): VoiceCommand {
        val t = utterance.replace(" ", "")
        if (t.isBlank()) return VoiceCommand.Unclear
        if (CANCEL_WORDS.any(t::contains)) return VoiceCommand.Cancel
        if (t.contains("기계식")) return VoiceCommand.ExcludeMechanical(exclude = t.contains("빼") || t.contains("제외"))
        if (t.contains("잔여") || t.contains("미제공")) return VoiceCommand.IncludeUnknownAvailability(include = t.contains("포함"))
        when {
            t.contains("거리순") -> return VoiceCommand.Sort(SortKey.DISTANCE)
            t.contains("요금순") || t.contains("비용순") -> return VoiceCommand.Sort(SortKey.COST)
            t.contains("여유") -> return VoiceCommand.Sort(SortKey.AVAILABILITY)
            t.contains("다음페이지") -> return VoiceCommand.NextPage
            t.contains("이전페이지") -> return VoiceCommand.PreviousPage
            t.contains("안내시작") -> return VoiceCommand.StartGuidance
        }
        val index = ORDINALS.entries.firstOrNull { (word, _) -> t.contains(word) }?.value ?: return VoiceCommand.Unclear
        // 번호는 지금 보이는 줄(한 페이지 5개) 기준이다
        return if (index < visibleCount) VoiceCommand.Select(index) else VoiceCommand.Unclear
    }

    private companion object {
        val CANCEL_WORDS = listOf("취소", "그만", "음성종료")
        val ORDINALS = linkedMapOf(
            "첫번째" to 0, "두번째" to 1, "세번째" to 2, "네번째" to 3, "다섯번째" to 4,
            "1번" to 0, "2번" to 1, "3번" to 2, "4번" to 3, "5번" to 4,
        )
    }
}

/** 스펙 R7. 예정 입구 30m 이내로 바뀐 목적지는 앱 자신의 경로 변경이다. */
class ClassifyDestinationChangeImpl : ClassifyDestinationChange {

    override fun invoke(previous: Destination?, next: Destination?, plannedEntrance: GeoPoint?): DestinationChange {
        if (previous == null && next == null) return DestinationChange.SAME
        if (next == null) return DestinationChange.NEW_VISIT
        if (previous != null && previous.id == next.id && previous.location == next.location) return DestinationChange.SAME
        if (plannedEntrance != null && next.location.distanceMetersTo(plannedEntrance) <= OWN_REROUTE_METERS + EPSILON_M) return DestinationChange.OWN_REROUTE
        return DestinationChange.NEW_VISIT
    }

    private companion object {
        const val OWN_REROUTE_METERS = 30.0
        /** 부동소수 왕복 오차 흡수. 1cm. */
        const val EPSILON_M = 0.01
    }
}

/** 스펙 R2. 5개씩(2026-11-14 사용자 결정, 그 전에는 3개). */
class PageCandidatesImpl : PageCandidates {

    override fun invoke(items: List<ParkingCandidate>, page: Int): Page<ParkingCandidate> {
        val count = (items.size + PAGE_SIZE - 1) / PAGE_SIZE
        val index = if (count == 0) 0 else page.coerceIn(0, count - 1)
        return Page(items.drop(index * PAGE_SIZE).take(PAGE_SIZE), index, count)
    }

    private companion object {
        const val PAGE_SIZE = 5
    }
}
