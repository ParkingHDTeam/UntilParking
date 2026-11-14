package app.untilparking.domain.repository

import app.untilparking.domain.model.AvailabilityReading
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationParkingLink
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.RouteRequestResult
import app.untilparking.domain.model.VehicleProfile
import kotlinx.coroutines.flow.Flow

/** 구현: data:navi (NaviHelper SDK). mock 플레이버는 app 모듈의 가짜. */
interface NavigationRepository {
    /** 내비 목적지. 없으면 null. */
    val destination: Flow<Destination?>

    /** 목적지를 다시 조회한다. 결과는 destination으로 온다. */
    fun refreshDestination()

    /**
     * 차량 도착지를 주차장 입구로 바꾸고 결과를 기다린다. 완료 이벤트 뒤 목적지를 재조회해 입구와 맞으면 Success.
     * 내비 오류는 Failed, 15초 무응답은 Unknown(스펙 R7).
     */
    suspend fun requestRoute(parking: ParkingFacility): RouteRequestResult

    /**
     * 내비의 장소 검색 화면을 키워드로 연다(목적지 검색 스펙 D1). 고른 장소는 destination으로 온다.
     * 열지 못하면 AppError.NavigationFailed.
     */
    fun openPlaceSearch(keyword: String)
}

/** 구현: data:driving (AAOS CarUxRestrictions). */
interface DrivingStateRepository {
    val state: Flow<DrivingState>
}

/** 구현: data:voice (Pleos STT·TTS SDK). */
interface VoiceRepository {
    /** 다 읽을 때까지 기다린다. 실패하면 AppError.VoiceFailed. */
    suspend fun speak(text: String)

    /** 한 번 듣고 최종 인식 결과를 돌려준다. 못 들었으면 null. */
    suspend fun listen(): String?
}

/** 구현: data:parking (지금은 개발용 가상 데이터, 서버가 생기면 v1 계약의 HTTP 어댑터). */
interface ParkingRepository {
    /** 목적지에 검증된 관계(부설·제휴)가 있는 주차장. 없으면 빈 목록. */
    suspend fun linkedParking(destination: Destination): List<Pair<ParkingFacility, DestinationParkingLink>>

    /** 중심에서 직선 반경 안의 시설. 실패하면 AppError.ParkingSourceFailed. */
    suspend fun nearby(center: GeoPoint, radiusMeters: Int): List<ParkingFacility>

    /** 실시간 잔여 대수. 제공하지 않는 시설이면 null. 이번 조회가 실패하면 AppError.ParkingSourceFailed. */
    suspend fun availability(parkingId: String): AvailabilityReading?
}

/** 구현: data:vehicle (DataStore). */
interface VehicleProfileRepository {
    val profile: Flow<VehicleProfile>

    /** 원자적으로 저장하고 version을 올린다. */
    suspend fun save(profile: VehicleProfile)
}

/** 구현: data:notification (Android 알림). 목적지 알림 스펙 N1. */
interface DestinationAlertRepository {
    /** "주변 주차장 보기" 알림을 띄운다. 이미 떠 있으면 새 목적지로 바꾼다. */
    fun show(destination: Destination)

    /** 떠 있는 알림을 지운다. 없으면 아무 일도 없다. */
    fun clear()
}
