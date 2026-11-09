package app.untilparking.feature.parking

import app.untilparking.domain.model.RoofBox
import app.untilparking.domain.model.SearchConditions
import app.untilparking.domain.model.SuvFlag
import app.untilparking.domain.model.VehicleField

/** 화면과 음성이 같은 동작을 부른다(기획서 25절 UiAction). 구현은 ParkingViewModel. */
interface ParkingActions {
    fun refreshDestination()
    fun openCompare()
    fun openDestinationParking()
    fun select(id: String)
    fun selectAndConfirm(id: String)
    fun nextPage()
    fun previousPage()
    fun cycleSort()
    fun openConditions()
    fun updateDraftConditions(conditions: SearchConditions)
    fun applyConditions()
    fun cancelConditions()
    fun resetDraftConditions()
    fun confirmSelection()
    fun startGuidance()
    fun acknowledgeUnknown()
    fun recheck()
    fun cancelConfirm()
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
}

/** 프리뷰용. */
object NoParkingActions : ParkingActions {
    override fun refreshDestination() = Unit
    override fun openCompare() = Unit
    override fun openDestinationParking() = Unit
    override fun select(id: String) = Unit
    override fun selectAndConfirm(id: String) = Unit
    override fun nextPage() = Unit
    override fun previousPage() = Unit
    override fun cycleSort() = Unit
    override fun openConditions() = Unit
    override fun updateDraftConditions(conditions: SearchConditions) = Unit
    override fun applyConditions() = Unit
    override fun cancelConditions() = Unit
    override fun resetDraftConditions() = Unit
    override fun confirmSelection() = Unit
    override fun startGuidance() = Unit
    override fun acknowledgeUnknown() = Unit
    override fun recheck() = Unit
    override fun cancelConfirm() = Unit
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
}
