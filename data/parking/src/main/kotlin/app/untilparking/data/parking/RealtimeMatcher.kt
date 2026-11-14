package app.untilparking.data.parking

import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.distanceMetersTo

/** 실시간 행 → 표준데이터 시설 짝 (실시간 잔여 스펙 V2). 출처마다 ID 체계가 달라 이름으로 맞춘다. */
internal object RealtimeMatcher {

    /** 좌표를 주는 출처의 최대 거리 [가설: 300m]. */
    private const val MAX_METERS = 300.0

    private val BRACKETS = Regex("""\([^)]*\)|（[^）]*）|\[[^\]]*]""")
    private val SPACES = Regex("""\s+""")
    private val SUFFIXES = listOf("주차장", "공영")

    /** 괄호 안 글자·공백을 지우고, 끝의 "주차장"·"공영"을 더 떼어지지 않을 때까지 뗀다. */
    fun normalize(name: String): String {
        var current = name.replace(BRACKETS, "").replace(SPACES, "")
        while (true) {
            val next = SUFFIXES.fold(current) { acc, suffix -> acc.removeSuffix(suffix) }
            if (next == current) return current
            current = next
        }
    }

    /** 시설 id → 짝지어진 행. 한 시설에 행이 둘 이상 짝지어지면 그 시설은 뺀다 [가설]. */
    fun match(rows: List<RealtimeRow>, facilities: List<ParkingFacility>, regions: List<String>): Map<String, RealtimeRow> {
        val byName = facilities.filter { it.isIn(regions) }.groupBy { normalize(it.name) }
        return rows.mapNotNull { row ->
            val same = normalize(row.name).takeIf { it.isNotEmpty() }?.let { byName[it] }.orEmpty()
            val location = row.location
            val facility = if (location == null) {
                same.singleOrNull()
            } else {
                same.map { it to location.distanceMetersTo(it.location) }.filter { it.second <= MAX_METERS }.minByOrNull { it.second }?.first
            }
            facility?.let { it.id to row }
        }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
    }
}
