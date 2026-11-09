package app.untilparking.domain.model

enum class SuvFlag { YES, NO, UNKNOWN }

enum class RoofBox { YES, NO, UNKNOWN }

/** 사용자가 입력한 차량 프로필. 값이 없으면 null(미확인). 0이나 -1로 미확인을 표현하지 않는다. */
data class VehicleProfile(
    val heightMm: Int? = null,
    val widthMm: Int? = null,
    val lengthMm: Int? = null,
    val weightKg: Int? = null,
    val weightBasis: WeightBasis = WeightBasis.UNKNOWN,
    /** 폭은 사이드미러 제외 기준으로 입력받는다(스펙 R8). */
    val mirrorBasis: MirrorBasis = MirrorBasis.EXCLUDING,
    val suv: SuvFlag = SuvFlag.UNKNOWN,
    val roofBox: RoofBox = RoofBox.UNKNOWN,
    /** 저장할 때마다 1씩 오른다. 후보·최종 확인 무효화의 기준. */
    val version: Int = 0,
)

enum class VehicleField { HEIGHT_MM, WIDTH_MM, LENGTH_MM, WEIGHT_KG }

sealed interface VehicleValueInput {
    data class Value(val value: Int) : VehicleValueInput
    /** 빈 값은 미확인(null)으로 반영한다. */
    data object Empty : VehicleValueInput
    data class Invalid(val message: String) : VehicleValueInput
}
