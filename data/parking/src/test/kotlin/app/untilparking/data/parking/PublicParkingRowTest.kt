package app.untilparking.data.parking

import app.untilparking.domain.model.FeeModel
import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.OperatingSchedule
import app.untilparking.domain.model.ParkingCategory
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.ParkingMethod
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** 공공 데이터 스펙 P2 행 → 시설. */
class PublicParkingRowTest {

    private fun map(row: JSONObject): ParkingFacility {
        val facility = PublicParkingRow.toFacility(row)
        assertNotNull("행이 버려졌다", facility)
        return facility!!
    }

    @Test
    fun `유형과 좌표를 옮기고 입구는 위치 좌표다`() {
        val f = map(row("7", type = "노상", lat = "35.1374", lon = "126.7910"))
        assertEquals("pub:7", f.id)
        assertEquals(GeoPoint(35.1374, 126.7910), f.location)
        assertEquals(f.location, f.entrance)
        assertEquals(ParkingCategory.ON_STREET, f.category)
        assertEquals(ParkingMethod.UNKNOWN, f.method)
        assertTrue(f.restrictions.isEmpty())
        assertEquals("광주광역시 광산구 상무대로 7", f.address)
        assertEquals("광주광역시 광산구", f.operator)
        assertEquals(ParkingCategory.OFF_STREET, map(row(type = "노외")).category)
        assertEquals(ParkingCategory.ATTACHED, map(row(type = "부설")).category)
        assertEquals(ParkingCategory.UNKNOWN, map(row(type = "기타")).category)
    }

    @Test
    fun `좌표가 비었거나 0이거나 숫자가 아닌 행은 버린다`() {
        assertNull(PublicParkingRow.toFacility(row(lat = null)))
        assertNull(PublicParkingRow.toFacility(row(lon = "")))
        assertNull(PublicParkingRow.toFacility(row(lat = "0")))
        assertNull(PublicParkingRow.toFacility(row(lat = "북위 35도")))
    }

    @Test
    fun `이름이 빈 행은 버린다`() {
        assertNull(PublicParkingRow.toFacility(row(name = " ")))
    }

    @Test
    fun `무료는 0원`() {
        assertEquals(FeeModel(0, 0, 0, 0), map(row(charge = "무료", basicCharge = null)).feeModel)
    }

    @Test
    fun `유료 값이 다 있으면 요금 모델이고 1일 주차권은 일 최대 요금이다`() {
        assertEquals(FeeModel(30, 1000, 10, 500), map(row()).feeModel)
        assertEquals(FeeModel(30, 1000, 10, 500, maxDailyWon = 10000), map(row(dayTicket = "10000")).feeModel)
    }

    @Test
    fun `유료 값이 빠지거나 혼합이면 산출 불가`() {
        assertNull(map(row(basicCharge = null)).feeModel)
        assertNull(map(row(addTime = "십분")).feeModel)
        assertNull(map(row(charge = "혼합")).feeModel)
    }

    @Test
    fun `24시간 표기는 종일 운영이고 24시 종료는 그날 끝까지다`() {
        assertTrue(map(row(weekday = "00:00" to "24:00")).operatingSchedule!!.allDay)
        assertTrue(map(row(weekday = "00:00" to "23:59")).operatingSchedule!!.allDay)
        assertEquals(OperatingSchedule(LocalTime.of(9, 0), LocalTime.MAX), map(row(weekday = "09:00" to "24:00")).operatingSchedule)
    }

    @Test
    fun `요일별 운영 시간을 따로 옮기고 비거나 형식이 다르면 그 요일은 모름`() {
        val f = map(row(weekday = "09:00" to "18:00", saturday = null to null, holiday = "10:00" to "15:00"))
        assertEquals(OperatingSchedule(LocalTime.of(9, 0), LocalTime.of(18, 0)), f.operatingSchedule)
        assertNull(f.saturdaySchedule)
        assertEquals(OperatingSchedule(LocalTime.of(10, 0), LocalTime.of(15, 0)), f.holidaySchedule)
        assertNull(map(row(weekday = "9시" to "18:00")).operatingSchedule)
    }

    @Test
    fun `밤샘 운영이나 시작과 종료가 같으면 그 요일은 모름`() {
        assertNull(map(row(weekday = "18:00" to "02:00")).operatingSchedule)
        assertNull(map(row(weekday = "00:00" to "00:00")).operatingSchedule)
    }

    @Test
    fun `운영 요일에 없는 요일은 휴무다`() {
        // 실제 데이터에서 휴무 요일은 00:00~00:00으로 온다(토 690건, 공휴일 1510건 모두 operDay에 없음)
        val f = map(row(operDay = "평일", saturday = "00:00" to "00:00", holiday = "00:00" to "00:00"))
        assertTrue(f.saturdaySchedule!!.closed)
        assertTrue(f.holidaySchedule!!.closed)
        assertEquals(OperatingSchedule(LocalTime.of(9, 0), LocalTime.of(18, 0)), f.operatingSchedule)
        // operDay가 없으면 시각만 본다
        assertNull(map(row(operDay = null, saturday = "00:00" to "00:00")).saturdaySchedule)
    }
}
