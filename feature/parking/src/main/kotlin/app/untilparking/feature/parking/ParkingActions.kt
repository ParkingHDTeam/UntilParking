package app.untilparking.feature.parking

import app.untilparking.domain.model.CongestionLevel
import app.untilparking.domain.model.RoofBox
import app.untilparking.domain.model.SearchConditions
import app.untilparking.domain.model.SortKey
import app.untilparking.domain.model.SuvFlag
import app.untilparking.domain.model.VehicleField

/** 화면과 음성이 같은 동작을 부른다(기획서 25절 UiAction). 구현은 ParkingViewModel. */
interface ParkingActions {
    fun refreshDestination()
    /** S00 목적지 직접 검색(목적지 검색 스펙 D1). */
    fun searchDestination()
    fun openCompare()
    fun openDestinationParking()
    /** 그 주차장을 재조회하고 바로 경로를 바꾼다(스펙 R7). */
    fun guideTo(id: String)
    fun nextPage()
    fun previousPage()
    fun sortBy(key: SortKey)
    fun openConditions()
    fun updateDraftConditions(conditions: SearchConditions)
    fun applyConditions()
    fun cancelConditions()
    fun resetDraftConditions()
    fun refreshList()
    fun expandRadius()
    fun openVehicle()
    fun updateDraftVehicle(field: VehicleField, text: String)
    fun setDraftSuv(suv: SuvFlag)
    fun setDraftRoofBox(roofBox: RoofBox)
    fun saveVehicle()
    fun cancelVehicle()
    fun resetDraftVehicle()
    fun toggleVoice()
    /** 도착한 주차장의 혼잡도를 남긴다(혼잡도 스펙 C3). */
    fun reportCongestion(level: CongestionLevel)
    fun skipCongestionReport()
}

/** 프리뷰용. */
object NoParkingActions : ParkingActions {
    override fun refreshDestination() = Unit
    override fun searchDestination() = Unit
    override fun openCompare() = Unit
    override fun openDestinationParking() = Unit
    override fun guideTo(id: String) = Unit
    override fun nextPage() = Unit
    override fun previousPage() = Unit
    override fun sortBy(key: SortKey) = Unit
    override fun openConditions() = Unit
    override fun updateDraftConditions(conditions: SearchConditions) = Unit
    override fun applyConditions() = Unit
    override fun cancelConditions() = Unit
    override fun resetDraftConditions() = Unit
    override fun refreshList() = Unit
    override fun expandRadius() = Unit
    override fun openVehicle() = Unit
    override fun updateDraftVehicle(field: VehicleField, text: String) = Unit
    override fun setDraftSuv(suv: SuvFlag) = Unit
    override fun setDraftRoofBox(roofBox: RoofBox) = Unit
    override fun saveVehicle() = Unit
    override fun cancelVehicle() = Unit
    override fun resetDraftVehicle() = Unit
    override fun toggleVoice() = Unit
    override fun reportCongestion(level: CongestionLevel) = Unit
    override fun skipCongestionReport() = Unit
}
