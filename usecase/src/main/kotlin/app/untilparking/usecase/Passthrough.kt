package app.untilparking.usecase

import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.RouteRequestResult
import app.untilparking.domain.model.VehicleProfile
import app.untilparking.domain.repository.DrivingStateRepository
import app.untilparking.domain.repository.NavigationRepository
import app.untilparking.domain.repository.VehicleProfileRepository
import app.untilparking.domain.repository.VoiceRepository
import app.untilparking.domain.usecase.Listen
import app.untilparking.domain.usecase.ObserveDestination
import app.untilparking.domain.usecase.ObserveDrivingState
import app.untilparking.domain.usecase.ObserveVehicleProfile
import app.untilparking.domain.usecase.RefreshDestination
import app.untilparking.domain.usecase.RequestGuidance
import app.untilparking.domain.usecase.SaveVehicleProfile
import app.untilparking.domain.usecase.Speak
import kotlinx.coroutines.flow.Flow

// 규칙 없이 저장소를 그대로 넘기는 유스케이스. 화면이 저장소를 직접 보지 않게 하는 경계다.

class RequestGuidanceImpl(private val nav: NavigationRepository) : RequestGuidance {
    override suspend fun invoke(parking: ParkingFacility): RouteRequestResult = nav.requestRoute(parking)
}

class ObserveDestinationImpl(private val nav: NavigationRepository) : ObserveDestination {
    override fun invoke(): Flow<Destination?> = nav.destination
}

class RefreshDestinationImpl(private val nav: NavigationRepository) : RefreshDestination {
    override fun invoke() = nav.refreshDestination()
}

class ObserveDrivingStateImpl(private val driving: DrivingStateRepository) : ObserveDrivingState {
    override fun invoke(): Flow<DrivingState> = driving.state
}

class ObserveVehicleProfileImpl(private val vehicle: VehicleProfileRepository) : ObserveVehicleProfile {
    override fun invoke(): Flow<VehicleProfile> = vehicle.profile
}

class SaveVehicleProfileImpl(private val vehicle: VehicleProfileRepository) : SaveVehicleProfile {
    override suspend fun invoke(profile: VehicleProfile) = vehicle.save(profile)
}

class SpeakImpl(private val voice: VoiceRepository) : Speak {
    override suspend fun invoke(text: String) = voice.speak(text)
}

class ListenImpl(private val voice: VoiceRepository) : Listen {
    override suspend fun invoke(): String? = voice.listen()
}
