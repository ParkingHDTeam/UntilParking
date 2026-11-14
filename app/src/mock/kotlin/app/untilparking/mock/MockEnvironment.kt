package app.untilparking.mock

import app.untilparking.data.parking.SampleParkingRepository
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationSource
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.RouteRequestResult
import app.untilparking.domain.repository.DrivingStateRepository
import app.untilparking.domain.repository.NavigationRepository
import app.untilparking.domain.repository.VoiceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 폰 에뮬레이터용 가상 차량 환경. 내비 목적지는 예시 목적지로 고정되고, 경로 변경은 0.8초 뒤 성공하며
 * 내비 목적지가 주차장 입구로 바뀐다. 주행 상태와 음성 입력은 MockPanel이 넣는다.
 */
class MockEnvironment(private val parking: SampleParkingRepository) :
    NavigationRepository, DrivingStateRepository, VoiceRepository, MockControls {

    private val destinationPresent = MutableStateFlow(true)
    private val routedTo = MutableStateFlow<ParkingFacility?>(null)
    private val drivingState = MutableStateFlow(DrivingState.PARKED)
    private val utterances = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val spokenState = MutableStateFlow<String?>(null)
    private val placeSearchState = MutableStateFlow<String?>(null)

    // NavigationRepository
    override val destination: Flow<Destination?> = combine(destinationPresent, routedTo) { present, routed ->
        when {
            !present -> null
            routed != null -> Destination("navi:${routed.id}", routed.name, routed.entrance ?: routed.location, DestinationSource.NAVI, routed.address)
            else -> SEORIM
        }
    }

    override fun refreshDestination() = Unit

    override suspend fun requestRoute(parking: ParkingFacility): RouteRequestResult {
        if (parking.entrance == null) return RouteRequestResult.Failed("입구 좌표 없음")
        delay(800)
        routedTo.value = parking
        return RouteRequestResult.Success
    }

    // 내비가 없으니 키워드만 남긴다. 목적지는 띠의 "내비 목적지 있음"으로 바꾼다(목적지 검색 스펙 D1)
    override fun openPlaceSearch(keyword: String) {
        placeSearchState.value = keyword
    }

    // DrivingStateRepository
    override val state: Flow<DrivingState> = drivingState

    // VoiceRepository
    override suspend fun speak(text: String) {
        spokenState.value = text
        delay(400)
    }

    override suspend fun listen(): String? = withTimeoutOrNull(10_000) { utterances.first() }

    // MockControls
    override val available: Boolean = true
    override val spoken: Flow<String?> = spokenState
    override val placeSearch: Flow<String?> = placeSearchState
    override fun setDriving(state: DrivingState) { drivingState.value = state }
    override fun setDestinationPresent(present: Boolean) {
        destinationPresent.value = present
        if (!present) routedTo.value = null
    }
    override fun setForcedFull(parkingId: String, full: Boolean) {
        parking.forcedFull = if (full) parking.forcedFull + parkingId else parking.forcedFull - parkingId
    }
    override fun submitVoice(text: String) { utterances.tryEmit(text) }

    private companion object {
        val SEORIM = Destination("navi:seorim", "서림문화센터", GeoPoint(37.5000, 127.0000), DestinationSource.NAVI, "서림로 1")
    }
}
