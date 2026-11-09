package app.untilparking.usecase

import app.untilparking.domain.model.VehicleField.HEIGHT_MM
import app.untilparking.domain.model.VehicleField.LENGTH_MM
import app.untilparking.domain.model.VehicleField.WEIGHT_KG
import app.untilparking.domain.model.VehicleField.WIDTH_MM
import app.untilparking.domain.model.VehicleValueInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 스펙 R8 차량 값 입력 (기획서 18절, T11). */
class VehicleValidationTest {

    private val validate = ValidateVehicleValueImpl()

    @Test
    fun `높이 0은 오류`() = assertTrue(validate(HEIGHT_MM, "0") is VehicleValueInput.Invalid)

    @Test
    fun `높이 5001은 오류`() {
        val result = validate(HEIGHT_MM, "5001")
        assertEquals(VehicleValueInput.Invalid("허용 범위를 확인하세요"), result)
    }

    @Test
    fun `높이 500과 5000은 값`() {
        assertEquals(VehicleValueInput.Value(500), validate(HEIGHT_MM, "500"))
        assertEquals(VehicleValueInput.Value(5000), validate(HEIGHT_MM, "5000"))
    }

    @Test
    fun `빈 값은 미확인`() = assertEquals(VehicleValueInput.Empty, validate(HEIGHT_MM, ""))

    @Test
    fun `공백만이면 미확인`() = assertEquals(VehicleValueInput.Empty, validate(HEIGHT_MM, "  "))

    @Test
    fun `높이 1800은 값`() = assertEquals(VehicleValueInput.Value(1800), validate(HEIGHT_MM, "1800"))

    @Test
    fun `폭 3001은 오류`() = assertTrue(validate(WIDTH_MM, "3001") is VehicleValueInput.Invalid)

    @Test
    fun `길이 999는 오류`() = assertTrue(validate(LENGTH_MM, "999") is VehicleValueInput.Invalid)

    @Test
    fun `길이 12000은 값`() = assertEquals(VehicleValueInput.Value(12000), validate(LENGTH_MM, "12000"))

    @Test
    fun `중량 199는 오류`() = assertTrue(validate(WEIGHT_KG, "199") is VehicleValueInput.Invalid)

    @Test
    fun `중량 10000은 값`() = assertEquals(VehicleValueInput.Value(10000), validate(WEIGHT_KG, "10000"))

    @Test
    fun `소수는 오류`() = assertEquals(VehicleValueInput.Invalid("숫자만 입력하세요"), validate(HEIGHT_MM, "18.5"))

    @Test
    fun `음수는 오류`() = assertEquals(VehicleValueInput.Invalid("숫자만 입력하세요"), validate(HEIGHT_MM, "-1800"))
}
