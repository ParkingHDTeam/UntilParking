package app.untilparking.usecase

import app.untilparking.domain.model.DestinationSearchResult
import app.untilparking.domain.model.DrivingState
import app.untilparking.domain.repository.NavigationRepository
import app.untilparking.domain.usecase.OpenDestinationSearch

/** 목적지 검색 스펙 D1. 묻기·듣기는 화면이 하고, 여기서는 들은 말을 키워드로 다듬어 내비 검색을 연다. */
class OpenDestinationSearchImpl(private val nav: NavigationRepository) : OpenDestinationSearch {

    override fun invoke(heard: String?, driving: DrivingState): DestinationSearchResult {
        // 정차 중에만(R9). 듣는 사이 주행이 시작됐어도 여기서 막힌다
        if (driving != DrivingState.PARKED) return DestinationSearchResult.Blocked
        val text = heard.orEmpty().trim()
        if (CANCEL_WORDS.any { text.replace(" ", "").contains(it) }) return DestinationSearchResult.Cancelled
        val keyword = keywordOf(text)
        nav.openPlaceSearch(keyword)
        return DestinationSearchResult.Opened(keyword)
    }

    /** 끝의 문장 부호와 요청 말("찾아줘", "으로 가줘" 등)을 뗀다 [가설: 뗄 말 목록]. */
    private fun keywordOf(text: String): String {
        val withoutMarks = text.trimEnd { it in ".?!~ " }
        val request = REQUEST_TAILS.firstOrNull { withoutMarks.replace(" ", "").endsWith(it) } ?: return withoutMarks
        // 띄어쓰기가 섞여 있어도("안내해 줘") 요청 말의 글자 수만큼 뒤에서 뗀다
        var remaining = request.length
        var end = withoutMarks.length
        while (remaining > 0 && end > 0) {
            end--
            if (withoutMarks[end] != ' ') remaining--
        }
        return withoutMarks.substring(0, end).trimEnd()
    }

    private companion object {
        val CANCEL_WORDS = listOf("취소", "그만", "음성종료")

        /** 긴 것부터 본다. "으로"·"로"는 요청 말에 붙여 함께 뗀다. */
        val REQUEST_TAILS = listOf("가줘", "가자", "안내해줘", "찾아줘", "검색해줘")
            .flatMap { listOf("으로$it", "로$it", it) }
            .sortedByDescending { it.length }
    }
}
