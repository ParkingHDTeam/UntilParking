package app.untilparking.di

import android.content.Context
import app.untilparking.BuildConfig
import app.untilparking.data.driving.CarUxDrivingStateRepository
import app.untilparking.data.navi.NaviHelperNavigationRepository
import app.untilparking.data.parking.HttpPublicParkingApi
import app.untilparking.data.parking.HttpTextFetcher
import app.untilparking.data.parking.PublicParkingRepository
import app.untilparking.data.parking.RealtimeAvailability
import app.untilparking.data.parking.RealtimeSources
import app.untilparking.data.voice.PleosVoiceRepository
import app.untilparking.domain.repository.DrivingStateRepository
import app.untilparking.domain.repository.NavigationRepository
import app.untilparking.domain.repository.ParkingRepository
import app.untilparking.domain.repository.VoiceRepository
import app.untilparking.mock.MockControls
import app.untilparking.mock.NoMockControls
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Singleton

/** pleos 플레이버: 실제 Pleos SDK 어댑터. SDK 인스턴스를 들고 있어 앱 프로세스 동안 하나씩만 둔다. */
@Module
@InstallIn(SingletonComponent::class)
object EnvModule {
    @Provides @Singleton
    fun navigation(@ApplicationContext context: Context): NavigationRepository = NaviHelperNavigationRepository(context)

    @Provides @Singleton
    fun driving(@ApplicationContext context: Context): DrivingStateRepository = CarUxDrivingStateRepository(context)

    @Provides @Singleton
    fun voice(@ApplicationContext context: Context): VoiceRepository =
        PleosVoiceRepository(context, BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET)

    /**
     * 공공 데이터 스펙 P4. 인증키가 없으면 api가 null이라 주차장 조회가 ParkingSourceFailed로 끝난다.
     * 잔여 대수는 실시간 출처(부산시설공단·대전·교통안전공단)에서 짝을 찾은 시설만 채운다(실시간 잔여 스펙 V4).
     */
    @Provides @Singleton
    fun parking(@ApplicationContext context: Context, clock: Clock): ParkingRepository = PublicParkingRepository(
        api = BuildConfig.PARKING_SERVICE_KEY.takeIf { it.isNotBlank() }?.let(::HttpPublicParkingApi),
        file = File(context.filesDir, "public-parking.json"),
        clock = clock,
        realtime = RealtimeAvailability(
            RealtimeSources.create(BuildConfig.PARKING_SERVICE_KEY, HttpTextFetcher()),
            clock,
        ),
    )

    @Provides
    fun controls(): MockControls = NoMockControls
}
