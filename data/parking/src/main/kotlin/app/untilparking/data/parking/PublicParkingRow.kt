package app.untilparking.data.parking

import app.untilparking.domain.model.FeeModel
import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.OperatingSchedule
import app.untilparking.domain.model.ParkingCategory
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.ParkingMethod
import org.json.JSONObject
import java.time.LocalTime

/**
 * 표준데이터 한 행 → 시설 (공공 데이터 스펙 P2). 쓸 수 없는 행은 null.
 * 필드명은 2026-11-09 실제 응답에서 확인했다. 원본의 오타(`Colse` 등)도 원본 표기 그대로 둔다.
 */
internal object PublicParkingRow {

    /** 저장본에 남길 필드. 여기 없는 필드는 받은 뒤 버린다. */
    val KEYS = listOf(
        "prkplceNo", "prkplceNm", "prkplceType", "latitude", "longitude", "rdnmadr", "lnmadr", "institutionNm",
        "parkingchrgeInfo", "basicTime", "basicCharge", "addUnitTime", "addUnitCharge", "dayCmmtkt",
        "weekdayOperOpenHhmm", "weekdayOperColseHhmm", "satOperOperOpenHhmm", "satOperCloseHhmm",
        "holidayOperOpenHhmm", "holidayCloseOpenHhmm", "operDay",
    )

    fun toFacility(row: JSONObject): ParkingFacility? {
        val no = row.text("prkplceNo") ?: return null
        val name = row.text("prkplceNm") ?: return null
        val lat = row.text("latitude")?.toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
        val lon = row.text("longitude")?.toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
        val location = GeoPoint(lat, lon)
        return ParkingFacility(
            id = "pub:$no",
            name = name,
            location = location,
            // 입구 좌표가 없어 위치 좌표로 안내한다(사용자 결정 2026-11-09)
            entrance = location,
            category = when (row.text("prkplceType")) {
                "노상" -> ParkingCategory.ON_STREET
                "노외" -> ParkingCategory.OFF_STREET
                "부설" -> ParkingCategory.ATTACHED
                else -> ParkingCategory.UNKNOWN
            },
            method = ParkingMethod.UNKNOWN,
            address = row.text("rdnmadr") ?: row.text("lnmadr"),
            operator = row.text("institutionNm"),
            feeModel = fee(row),
            operatingSchedule = schedule(row, "평일", "weekdayOperOpenHhmm", "weekdayOperColseHhmm"),
            saturdaySchedule = schedule(row, "토요일", "satOperOperOpenHhmm", "satOperCloseHhmm"),
            holidaySchedule = schedule(row, "공휴일", "holidayOperOpenHhmm", "holidayCloseOpenHhmm"),
        )
    }

    private fun fee(row: JSONObject): FeeModel? = when (row.text("parkingchrgeInfo")) {
        "무료" -> FeeModel(0, 0, 0, 0)
        "유료" -> {
            val values = listOf("basicTime", "basicCharge", "addUnitTime", "addUnitCharge").map { row.text(it)?.toIntOrNull() }
            if (values.any { it == null }) {
                null
            } else {
                val (baseMinutes, baseWon, stepMinutes, stepWon) = values.map { it!! }
                FeeModel(baseMinutes, baseWon, stepMinutes, stepWon, maxDailyWon = row.text("dayCmmtkt")?.toIntOrNull()?.takeIf { it > 0 })
            }
        }
        // 혼합·그 밖의 값은 요금제를 하나로 정할 수 없다 [가설]
        else -> null
    }

    /**
     * operDay(예: "평일+토요일")에 그 요일이 없으면 휴무다. 실제 데이터에서 휴무 요일은 00:00~00:00으로 오고,
     * 2026-11-09 전체 18994건 중 토요일 690건·공휴일 1510건이 모두 operDay에 그 요일이 없는 행이었다 [가설: 데이터 근거].
     */
    private fun schedule(row: JSONObject, day: String, openKey: String, closeKey: String): OperatingSchedule? {
        val operDay = row.text("operDay")
        if (operDay != null && day !in operDay) return OperatingSchedule.CLOSED_ALL_DAY
        return schedule(row.text(openKey), row.text(closeKey))
    }

    private fun schedule(openText: String?, closeText: String?): OperatingSchedule? {
        val open = time(openText) ?: return null
        val endsAtMidnight = closeText == "24:00"
        if (open == LocalTime.MIDNIGHT && (endsAtMidnight || closeText == "23:59")) {
            return OperatingSchedule(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, allDay = true)
        }
        val close = if (endsAtMidnight) LocalTime.MAX else time(closeText) ?: return null
        // 시작 = 종료, 밤샘 운영은 0시 넘김을 판정하지 않으므로 모름으로 둔다 [가설]
        if (!close.isAfter(open)) return null
        return OperatingSchedule(open, close)
    }

    private val HHMM = Regex("""(\d{1,2}):(\d{2})""")

    private fun time(text: String?): LocalTime? {
        val (h, m) = HHMM.matchEntire(text ?: return null)?.destructured ?: return null
        val hour = h.toInt()
        val minute = m.toInt()
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }

    /** 비었거나 JSON null이면 null. */
    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }
}
