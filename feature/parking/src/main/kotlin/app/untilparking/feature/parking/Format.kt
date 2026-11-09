package app.untilparking.feature.parking

import app.untilparking.domain.model.Availability
import app.untilparking.domain.model.AvailabilityStatus
import app.untilparking.domain.model.Distance
import app.untilparking.domain.model.DistanceKind
import app.untilparking.domain.model.Eligibility
import app.untilparking.domain.model.EligibilityStatus
import app.untilparking.domain.model.FeeEstimate
import app.untilparking.domain.model.ParkingCategory
import app.untilparking.domain.model.ParkingMethod
import app.untilparking.domain.model.SortKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// 화면 문구. 미확인은 숨기지 않고 "정보 없음"으로 말한다(기획서 22절).

private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun Instant.hm(zone: ZoneId = ZoneId.systemDefault()): String = atZone(zone).format(HM)

fun sortLabel(key: SortKey): String = when (key) {
    SortKey.DISTANCE -> "거리 가까운 순"
    SortKey.COST -> "예상 비용 낮은 순"
    SortKey.AVAILABILITY -> "여유 공간 우선"
}

fun methodLabel(method: ParkingMethod): String = when (method) {
    ParkingMethod.SELF -> "자주식"
    ParkingMethod.MECHANICAL -> "기계식"
    ParkingMethod.MIXED -> "자주·기계 혼합"
    ParkingMethod.UNKNOWN -> "방식 미확인"
}

fun categoryLabel(category: ParkingCategory): String = when (category) {
    ParkingCategory.ON_STREET -> "노상"
    ParkingCategory.OFF_STREET -> "노외"
    ParkingCategory.ATTACHED -> "부설"
    ParkingCategory.UNKNOWN -> "구분 미확인"
}

fun availabilityLabel(a: Availability): String = when (a.status) {
    AvailabilityStatus.LIVE, AvailabilityStatus.RECEIVED -> "잔여 ${a.availableSpaces}대"
    AvailabilityStatus.STALE -> "잔여 ${a.availableSpaces}대 · 오래된 정보"
    AvailabilityStatus.UNAVAILABLE -> "잔여 대수 정보 없음"
    AvailabilityStatus.ERROR -> "잔여 정보 조회 실패"
}

fun distanceLabel(d: Distance): String = when (d.kind) {
    DistanceKind.WALK -> "도보 ${d.minutes}분"
    DistanceKind.STRAIGHT -> "직선 ${d.meters}m"
    DistanceKind.UNKNOWN -> "거리 미확인"
}

fun feeLabel(fee: FeeEstimate, minutes: Int): String = when (val won = fee.won) {
    null -> "요금 산출 불가"
    0 -> "${minutesLabel(minutes)} 무료"
    else -> "${minutesLabel(minutes)} 예상 ${"%,d".format(won)}원"
}

fun minutesLabel(minutes: Int): String = when {
    minutes % 60 == 0 -> "${minutes / 60}시간"
    minutes < 60 -> "${minutes}분"
    else -> "${minutes / 60}시간 ${minutes % 60}분"
}

fun eligibilityLabel(e: Eligibility): String = when (e.status) {
    EligibilityStatus.VERIFIED_MATCH -> "차량 조건 확인"
    EligibilityStatus.UNKNOWN -> "차량 제한 일부 미확인"
    EligibilityStatus.BLOCKED -> "입차 불가"
}

/** 수신 시각 줄. 원본 갱신 시각이 없으면 그렇다고 말한다. */
fun receivedLine(a: Availability?, receivedAt: Instant?): String {
    val received = (a?.receivedAt ?: receivedAt)?.hm() ?: return "아직 조회 전"
    val source = a?.sourceUpdatedAt?.hm()
    return if (source != null) "잔여 대수 수신 $received · 원본 갱신 $source" else "잔여 대수 수신 $received · 원본 갱신 시각 미제공"
}
