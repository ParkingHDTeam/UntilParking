package app.untilparking.di

import app.untilparking.data.parking.SampleParkingRepository
import app.untilparking.domain.repository.DrivingStateRepository
import app.untilparking.domain.repository.NavigationRepository
import app.untilparking.domain.repository.ParkingRepository
import app.untilparking.domain.repository.VoiceRepository
import app.untilparking.mock.MockControls
import app.untilparking.mock.MockEnvironment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** mock 플레이버: 내비·주행·음성을 가상 환경 하나가 맡고, 주차장은 기획서 24절 가상 데이터다. */
@Module
@InstallIn(SingletonComponent::class)
object EnvModule {
    @Provides @Singleton
    fun environment(parking: SampleParkingRepository): MockEnvironment = MockEnvironment(parking)

    @Provides fun parking(sample: SampleParkingRepository): ParkingRepository = sample
    @Provides fun navigation(env: MockEnvironment): NavigationRepository = env
    @Provides fun driving(env: MockEnvironment): DrivingStateRepository = env
    @Provides fun voice(env: MockEnvironment): VoiceRepository = env
    @Provides fun controls(env: MockEnvironment): MockControls = env
}
