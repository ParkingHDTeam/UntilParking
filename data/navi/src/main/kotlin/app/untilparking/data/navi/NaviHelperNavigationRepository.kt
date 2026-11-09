package app.untilparking.data.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import app.untilparking.domain.AppError
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationSource
import app.untilparking.domain.model.GeoPoint
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.RouteRequestResult
import app.untilparking.domain.model.distanceMetersTo
import app.untilparking.domain.repository.NavigationRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/**
 * NaviHelper SDK 어댑터. 앱 프로세스 동안 하나만 둔다(Hilt @Singleton).
 * 조회 API는 값을 돌려주지 않고 콜백으로만 온다(SDK 문서).
 */
class NaviHelperNavigationRepository(context: Context) : NavigationRepository {

    private val naviHelper = NaviHelper(context.applicationContext)

    private val destinationState = MutableStateFlow<Destination?>(null)
    /** 경로 요청의 완료 이벤트와 내비 오류를 한 흐름으로 받는다. 요청을 보낸 뒤 먼저 오는 쪽이 결과다. */
    private val routeResults = MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 8)

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationChanged() {
            // 목적지가 바뀌었다는 신호만 온다. 내용은 다시 물어 onDestinationInfo로 받는다.
            requestDestinationInfo()
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            destinationState.value = destinationInfo.toDomain()
        }

        override fun onRouteCancelled() {
            destinationState.value = null
        }

        override fun onRouteEnded() {
            destinationState.value = null
        }

        override fun onRoutingCompleted() {
            routeResults.tryEmit(Result.success(Unit))
        }

        override fun onError(errorCode: NaviErrorCode) {
            routeResults.tryEmit(Result.failure(AppError.NavigationFailed(errorCode.name)))
        }
    }

    init {
        // 권한이 없거나 Pleos 내비가 없는 기기에서는 SDK가 SecurityException을 던질 수 있다. 그때는 목적지 없음으로 남는다.
        try {
            naviHelper.initialize(
                { requestDestinationInfo() },
                { /* 연결 실패: 목적지 없음 상태로 남는다 */ },
            )
            naviHelper.addListener(listener)
        } catch (e: SecurityException) {
            Unit
        }
    }

    private fun requestDestinationInfo() {
        try {
            naviHelper.getDestinationInfo()
        } catch (e: SecurityException) {
            destinationState.value = null
        }
    }

    override val destination: StateFlow<Destination?> = destinationState

    override fun refreshDestination() = requestDestinationInfo()

    override suspend fun requestRoute(parking: ParkingFacility): RouteRequestResult {
        val entrance = parking.entrance ?: return RouteRequestResult.Failed("입구 좌표 없음")
        val result = try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                routeResults
                    .onSubscription {
                        naviHelper.requestRoute(
                            RouteInfo(
                                latitude = entrance.latitude,
                                longitude = entrance.longitude,
                                poiName = parking.name,
                                // 외부 데이터 장소라 내비 POI ID가 없다. 빈 값 허용 여부는 사무국 확인 전(스펙 빈칸).
                                poiId = "",
                                address = parking.address.orEmpty(),
                                poiSubId = "",
                            ),
                        )
                    }
                    .first()
            }
        } catch (e: SecurityException) {
            return RouteRequestResult.Failed("권한 없음")
        }
        return when {
            // 15초 무응답: 적용 여부를 모른다. 목적지를 재조회하고 자동 재전송하지 않는다(스펙 R7).
            result == null -> RouteRequestResult.Unknown.also { requestDestinationInfo() }
            result.isFailure -> RouteRequestResult.Failed(result.exceptionOrNull()?.message ?: "내비 오류")
            else -> {
                // 완료 이벤트만으로 성공을 확정하지 않는다. 목적지를 다시 받아 입구와 맞는지 본다.
                requestDestinationInfo()
                val applied = withTimeoutOrNull(DESTINATION_TIMEOUT_MS) {
                    destinationState.first { it != null && it.location.distanceMetersTo(entrance) <= OWN_REROUTE_METERS }
                }
                if (applied != null) RouteRequestResult.Success else RouteRequestResult.Unknown
            }
        }
    }

    private fun DestinationInfo.toDomain() = Destination(
        id = "navi:$latitude,$longitude",
        name = name,
        location = GeoPoint(latitude, longitude),
        source = DestinationSource.NAVI,
        // remainTime은 초 단위(API 레퍼런스)
        etaAt = Instant.now().plusSeconds(remainTime.toLong()),
    )

    private companion object {
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val DESTINATION_TIMEOUT_MS = 5_000L
        const val OWN_REROUTE_METERS = 30.0
    }
}
