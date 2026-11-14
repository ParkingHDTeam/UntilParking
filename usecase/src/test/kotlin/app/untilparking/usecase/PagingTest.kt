package app.untilparking.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

/** 스펙 R2 5개씩 페이지 (기획서 15절, T02). 2026-11-14 사용자 결정으로 3개에서 늘렸다. */
class PagingTest {

    private val page = PageCandidatesImpl()
    private val six = (1..6).map { candidate("p$it") }

    @Test
    fun `후보 6개면 첫 페이지 5개 둘째 페이지 1개`() {
        val first = page(six, 0)
        assertEquals(listOf("p1", "p2", "p3", "p4", "p5"), first.items.ids)
        assertEquals(2, first.count)
        val second = page(six, 1)
        assertEquals(listOf("p6"), second.items.ids)
        assertEquals(1, second.index)
    }

    @Test
    fun `30개면 6페이지`() = assertEquals(6, page((1..30).map { candidate("p$it") }, 0).count)

    @Test
    fun `후보가 없으면 페이지도 없다`() {
        val empty = page(emptyList(), 0)
        assertEquals(0, empty.count)
        assertEquals(0, empty.items.size)
    }

    @Test
    fun `범위 밖 페이지는 끝 페이지로 맞춘다`() {
        assertEquals(1, page(six, 5).index)
        assertEquals(0, page(six, -1).index)
    }
}
