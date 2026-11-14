package app.untilparking.mock

import app.untilparking.domain.model.DrivingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 개발용 패널이 가상 환경을 조작하는 손잡이. mock 플레이버만 실제 구현을 준다.
 * 실차 모드(pleos)에는 정차 강제 스위치를 노출하지 않는다(기획서 26절).
 */
interface MockControls {
    val available: Boolean
    val spoken: Flow<String?>
    /** 목적지 직접 검색으로 내비 장소 검색을 연 키워드(mock은 내비가 없어 띠에 보인다). */
    val placeSearch: Flow<String?>
    fun setDriving(state: DrivingState)
    fun setDestinationPresent(present: Boolean)
    fun setForcedFull(parkingId: String, full: Boolean)
    fun submitVoice(text: String)
}

object NoMockControls : MockControls {
    override val available: Boolean = false
    override val spoken: Flow<String?> = flowOf(null)
    override val placeSearch: Flow<String?> = flowOf(null)
    override fun setDriving(state: DrivingState) = Unit
    override fun setDestinationPresent(present: Boolean) = Unit
    override fun setForcedFull(parkingId: String, full: Boolean) = Unit
    override fun submitVoice(text: String) = Unit
}
