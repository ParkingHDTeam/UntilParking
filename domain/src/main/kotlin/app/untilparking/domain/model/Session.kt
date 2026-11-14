package app.untilparking.domain.model

import java.time.Instant

enum class DestinationSource { NAVI, SEARCH }

/** 방문 목적지. 차량 도착지를 주차장 입구로 바꿔도 이 값은 앱이 유지한다. */
data class Destination(
    val id: String,
    val name: String,
    val location: GeoPoint,
    val source: DestinationSource,
    val address: String? = null,
    /** 내비 남은 시간으로 계산한 도착 예정 시각. 모르면 null → 지금 시각(스펙 R6). */
    val etaAt: Instant? = null,
)

data class Filters(
    val excludeMechanical: Boolean = true,
    val includeUnknownMethod: Boolean = true,
    val includeUnknownAvailability: Boolean = true,
)

enum class SortKey { DISTANCE, COST, AVAILABILITY }

data class SearchConditions(
    val filters: Filters = Filters(),
    val sort: SortKey = SortKey.DISTANCE,
    val parkingMinutes: Int = 60,
    val radiusM: Int = 500,
)

/** PARKED·MOVING은 플랫폼 신호로만 정한다. 신호가 없으면 UNKNOWN이지 정차가 아니다(스펙 R9). */
enum class DrivingState { PARKED, MOVING, UNKNOWN }

sealed interface RouteRequestResult {
    data object Success : RouteRequestResult
    data class Failed(val reason: String) : RouteRequestResult
    /** 15초 안에 결과가 없다. 목적지를 재조회해 적용 여부를 판정하고 자동 재전송하지 않는다. */
    data object Unknown : RouteRequestResult
}

enum class DestinationChange { SAME, OWN_REROUTE, NEW_VISIT }

enum class AlternativeReason { FULL, BLOCKED, EMPTY, NETWORK, STALE, INVALID_ENTRANCE }

sealed interface VoiceCommand {
    data class ExcludeMechanical(val exclude: Boolean) : VoiceCommand
    data class IncludeUnknownAvailability(val include: Boolean) : VoiceCommand
    data class Sort(val key: SortKey) : VoiceCommand
    /** 0부터. 지금 보이는 줄(한 페이지 5개) 기준. */
    data class Select(val index: Int) : VoiceCommand
    data object NextPage : VoiceCommand
    data object PreviousPage : VoiceCommand
    data object StartGuidance : VoiceCommand
    data object Cancel : VoiceCommand
    data object Unclear : VoiceCommand
}

/** 목적지 직접 검색 결과(목적지 검색 스펙 D1). */
sealed interface DestinationSearchResult {
    /** 내비 장소 검색 화면을 이 키워드로 열었다. 빈 문자열이면 빈 검색 화면이다. */
    data class Opened(val keyword: String) : DestinationSearchResult
    data object Cancelled : DestinationSearchResult
    /** 정차 중이 아니라 열지 않았다(R9). */
    data object Blocked : DestinationSearchResult
}
