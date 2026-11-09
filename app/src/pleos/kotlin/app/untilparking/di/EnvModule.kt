package app.untilparking.di

import android.content.Context
import app.untilparking.BuildConfig
import app.untilparking.data.driving.CarUxDrivingStateRepository
import app.untilparking.data.navi.NaviHelperNavigationRepository
import app.untilparking.data.voice.PleosVoiceRepository
import app.untilparking.domain.repository.DrivingStateRepository
import app.untilparking.domain.repository.NavigationRepository
import app.untilparking.domain.repository.VoiceRepository
import app.untilparking.mock.MockControls
import app.untilparking.mock.NoMockControls
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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

    @Provides
    fun controls(): MockControls = NoMockControls
}
