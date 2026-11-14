package app.untilparking.di

import android.content.Context
import app.untilparking.data.notification.NotificationDestinationAlert
import app.untilparking.data.parking.FileCongestionRepository
import app.untilparking.data.parking.SampleParkingRepository
import app.untilparking.data.vehicle.DataStoreVehicleProfileRepository
import app.untilparking.domain.repository.CongestionRepository
import app.untilparking.domain.repository.DestinationAlertRepository
import app.untilparking.domain.repository.DrivingStateRepository
import app.untilparking.domain.repository.NavigationRepository
import app.untilparking.domain.repository.ParkingRepository
import app.untilparking.domain.repository.VehicleProfileRepository
import app.untilparking.domain.repository.VoiceRepository
import app.untilparking.domain.usecase.AlertNewDestination
import app.untilparking.domain.usecase.ClassifyDestinationChange
import app.untilparking.domain.usecase.DetectArrival
import app.untilparking.domain.usecase.DismissDestinationAlert
import app.untilparking.domain.usecase.EstimateFee
import app.untilparking.domain.usecase.ExpandRadius
import app.untilparking.domain.usecase.JudgeArrivalPrompt
import app.untilparking.domain.usecase.JudgeAvailability
import app.untilparking.domain.usecase.JudgeEligibility
import app.untilparking.domain.usecase.Listen
import app.untilparking.domain.usecase.OpenDestinationSearch
import app.untilparking.domain.usecase.ObserveDestination
import app.untilparking.domain.usecase.ObserveDrivingState
import app.untilparking.domain.usecase.ObserveVehicleProfile
import app.untilparking.domain.usecase.PageCandidates
import app.untilparking.domain.usecase.ParseVoiceCommand
import app.untilparking.domain.usecase.RecheckParking
import app.untilparking.domain.usecase.RefreshDestination
import app.untilparking.domain.usecase.ReportCongestion
import app.untilparking.domain.usecase.RequestGuidance
import app.untilparking.domain.usecase.SaveVehicleProfile
import app.untilparking.domain.usecase.SearchParking
import app.untilparking.domain.usecase.SelectCandidates
import app.untilparking.domain.usecase.Speak
import app.untilparking.domain.usecase.SummarizeCongestion
import app.untilparking.domain.usecase.ValidateVehicleValue
import app.untilparking.usecase.AlertNewDestinationImpl
import app.untilparking.usecase.ClassifyDestinationChangeImpl
import app.untilparking.usecase.DetectArrivalImpl
import app.untilparking.usecase.DismissDestinationAlertImpl
import app.untilparking.usecase.EstimateFeeImpl
import app.untilparking.usecase.ExpandRadiusImpl
import app.untilparking.usecase.JudgeArrivalPromptImpl
import app.untilparking.usecase.JudgeAvailabilityImpl
import app.untilparking.usecase.JudgeEligibilityImpl
import app.untilparking.usecase.ListenImpl
import app.untilparking.usecase.OpenDestinationSearchImpl
import app.untilparking.usecase.ObserveDestinationImpl
import app.untilparking.usecase.ObserveDrivingStateImpl
import app.untilparking.usecase.ObserveVehicleProfileImpl
import app.untilparking.usecase.PageCandidatesImpl
import app.untilparking.usecase.ParseVoiceCommandImpl
import app.untilparking.usecase.RecheckParkingImpl
import app.untilparking.usecase.RefreshDestinationImpl
import app.untilparking.usecase.ReportCongestionImpl
import app.untilparking.usecase.RequestGuidanceImpl
import app.untilparking.usecase.SaveVehicleProfileImpl
import app.untilparking.usecase.SearchParkingImpl
import app.untilparking.usecase.SelectCandidatesImpl
import app.untilparking.usecase.SpeakImpl
import app.untilparking.usecase.SummarizeCongestionImpl
import app.untilparking.usecase.ValidateVehicleValueImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Singleton

// 의존 그래프 전체가 이 파일과 플레이버별 EnvModule(내비·주행·음성)에 있다. 구현을 바꾸려면 여기만 고친다.

/** 플레이버와 무관한 저장소. 주차 데이터(ParkingRepository)는 플레이버마다 달라 EnvModule이 고른다. */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides @Singleton
    fun sampleParking(clock: Clock): SampleParkingRepository = SampleParkingRepository(clock)

    @Provides @Singleton
    fun vehicle(@ApplicationContext context: Context): VehicleProfileRepository = DataStoreVehicleProfileRepository(context)

    /** 이 기기에서 남긴 혼잡도 입력(혼잡도 스펙 C5). 화면이 보는 CongestionRepository는 플레이버마다 달라 EnvModule이 고른다. */
    @Provides @Singleton
    fun deviceCongestion(@ApplicationContext context: Context): FileCongestionRepository =
        FileCongestionRepository(File(context.filesDir, "congestion-reports.json"))

    @Provides @Singleton
    fun destinationAlert(@ApplicationContext context: Context): DestinationAlertRepository = NotificationDestinationAlert(context)
}

/** 유스케이스: 상태가 없어 필요할 때마다 만든다. */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides fun judgeEligibility(): JudgeEligibility = JudgeEligibilityImpl()
    @Provides fun judgeAvailability(): JudgeAvailability = JudgeAvailabilityImpl()
    @Provides fun estimateFee(clock: Clock): EstimateFee = EstimateFeeImpl(clock.zone)
    @Provides fun selectCandidates(): SelectCandidates = SelectCandidatesImpl()
    @Provides fun expandRadius(): ExpandRadius = ExpandRadiusImpl()
    @Provides fun pageCandidates(): PageCandidates = PageCandidatesImpl()
    @Provides fun validateVehicleValue(): ValidateVehicleValue = ValidateVehicleValueImpl()
    @Provides fun parseVoiceCommand(): ParseVoiceCommand = ParseVoiceCommandImpl()
    @Provides fun classifyDestinationChange(): ClassifyDestinationChange = ClassifyDestinationChangeImpl()

    @Provides fun summarizeCongestion(): SummarizeCongestion = SummarizeCongestionImpl()
    @Provides fun detectArrival(): DetectArrival = DetectArrivalImpl()
    @Provides fun judgeArrivalPrompt(): JudgeArrivalPrompt = JudgeArrivalPromptImpl()
    @Provides fun reportCongestion(congestion: CongestionRepository, clock: Clock): ReportCongestion = ReportCongestionImpl(congestion, clock)

    @Provides
    fun searchParking(
        repo: ParkingRepository, eligibility: JudgeEligibility, availability: JudgeAvailability, fee: EstimateFee, select: SelectCandidates,
        congestion: CongestionRepository, summarize: SummarizeCongestion, clock: Clock,
    ): SearchParking = SearchParkingImpl(repo, eligibility, availability, fee, select, congestion, summarize, clock)

    @Provides
    fun recheckParking(
        repo: ParkingRepository, eligibility: JudgeEligibility, availability: JudgeAvailability, fee: EstimateFee,
        congestion: CongestionRepository, summarize: SummarizeCongestion, clock: Clock,
    ): RecheckParking = RecheckParkingImpl(repo, eligibility, availability, fee, congestion, summarize, clock)

    @Provides fun requestGuidance(nav: NavigationRepository): RequestGuidance = RequestGuidanceImpl(nav)
    @Provides fun observeDestination(nav: NavigationRepository): ObserveDestination = ObserveDestinationImpl(nav)
    @Provides fun refreshDestination(nav: NavigationRepository): RefreshDestination = RefreshDestinationImpl(nav)
    @Provides fun openDestinationSearch(nav: NavigationRepository): OpenDestinationSearch = OpenDestinationSearchImpl(nav)
    @Provides fun alertNewDestination(alerts: DestinationAlertRepository): AlertNewDestination = AlertNewDestinationImpl(alerts)
    @Provides fun dismissDestinationAlert(alerts: DestinationAlertRepository): DismissDestinationAlert = DismissDestinationAlertImpl(alerts)
    @Provides fun observeDriving(driving: DrivingStateRepository): ObserveDrivingState = ObserveDrivingStateImpl(driving)
    @Provides fun observeVehicle(vehicle: VehicleProfileRepository): ObserveVehicleProfile = ObserveVehicleProfileImpl(vehicle)
    @Provides fun saveVehicle(vehicle: VehicleProfileRepository): SaveVehicleProfile = SaveVehicleProfileImpl(vehicle)
    @Provides fun speak(voice: VoiceRepository): Speak = SpeakImpl(voice)
    @Provides fun listen(voice: VoiceRepository): Listen = ListenImpl(voice)
}
