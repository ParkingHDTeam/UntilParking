package app.untilparking.usecase

import app.untilparking.domain.AppError
import app.untilparking.domain.model.Destination
import app.untilparking.domain.model.DestinationSearchResult
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.model.ParkingFacility
import app.untilparking.domain.model.RouteRequestResult
import app.untilparking.domain.repository.NavigationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 목적지 검색 스펙 D1. */
class OpenDestinationSearchTest {

    private class FakeNavigation(private val failOpen: Boolean = false) : NavigationRepository {
        val opened = mutableListOf<String>()
        override val destination: Flow<Destination?> = flowOf(null)
        override fun refreshDestination() = Unit
        override suspend fun requestRoute(parking: ParkingFacility) = RouteRequestResult.Unknown
        override fun openPlaceSearch(keyword: String) {
            if (failOpen) throw AppError.NavigationFailed("검색 화면을 열 수 없음")
            opened += keyword
        }
    }

    private val nav = FakeNavigation()
    private val open = OpenDestinationSearchImpl(nav)

    @Test
    fun `주행 중이거나 주행 상태를 모르면 열지 않는다`() {
        assertEquals(DestinationSearchResult.Blocked, open("강남역", DrivingState.MOVING))
        assertEquals(DestinationSearchResult.Blocked, open("강남역", DrivingState.UNKNOWN))
        assertEquals(emptyList<String>(), nav.opened)
    }

    @Test
    fun `들은 장소 이름으로 내비 장소 검색을 연다`() {
        assertEquals(DestinationSearchResult.Opened("강남역"), open("강남역", DrivingState.PARKED))
        assertEquals(listOf("강남역"), nav.opened)
    }

    @Test
    fun `끝의 요청 말과 문장 부호는 떼고 연다`() {
        listOf(
            "강남역 찾아줘" to "강남역",
            "서울역으로 가줘" to "서울역",
            "롯데월드 타워 검색해줘." to "롯데월드 타워",
            "광주송정역으로 안내해 줘" to "광주송정역",
            "코엑스로 가자" to "코엑스",
        ).forEach { (heard, keyword) -> open(heard, DrivingState.PARKED) }
        assertEquals(listOf("강남역", "서울역", "롯데월드 타워", "광주송정역", "코엑스"), nav.opened)
    }

    @Test
    fun `못 들었거나 요청 말만 있으면 빈 키워드로 연다`() {
        assertEquals(DestinationSearchResult.Opened(""), open(null, DrivingState.PARKED))
        assertEquals(DestinationSearchResult.Opened(""), open("  ", DrivingState.PARKED))
        assertEquals(DestinationSearchResult.Opened(""), open("찾아줘", DrivingState.PARKED))
        assertEquals(listOf("", "", ""), nav.opened)
    }

    @Test
    fun `취소라고 하면 열지 않는다`() {
        assertEquals(DestinationSearchResult.Cancelled, open("취소", DrivingState.PARKED))
        assertEquals(DestinationSearchResult.Cancelled, open("그만할래", DrivingState.PARKED))
        assertEquals(DestinationSearchResult.Cancelled, open("음성 종료", DrivingState.PARKED))
        assertEquals(emptyList<String>(), nav.opened)
    }

    @Test
    fun `내비가 검색 화면을 못 열면 NavigationFailed`() {
        val failing = OpenDestinationSearchImpl(FakeNavigation(failOpen = true))
        val error = runCatching { failing("강남역", DrivingState.PARKED) }.exceptionOrNull()
        assertTrue(error is AppError.NavigationFailed)
    }
}
