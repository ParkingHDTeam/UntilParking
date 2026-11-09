package app.untilparking.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

/** 스펙 R2 3개씩 페이지 (기획서 15절, T02). */
class PagingTest {

    private val page = PageCandidatesImpl()
    private val four = (1..4).map { candidate("p$it") }

    @Test
    fun `후보 4개면 첫 페이지 3개 둘째 페이지 1개`() {
        val first = page(four, 0)
        assertEquals(listOf("p1", "p2", "p3"), first.items.ids)
        assertEquals(2, first.count)
        val second = page(four, 1)
        assertEquals(listOf("p4"), second.items.ids)
        assertEquals(1, second.index)
    }

    @Test
    fun `30개면 10페이지`() = assertEquals(10, page((1..30).map { candidate("p$it") }, 0).count)

    @Test
    fun `후보가 없으면 페이지도 없다`() {
        val empty = page(emptyList(), 0)
        assertEquals(0, empty.count)
        assertEquals(0, empty.items.size)
    }

    @Test
    fun `범위 밖 페이지는 끝 페이지로 맞춘다`() {
        assertEquals(1, page(four, 5).index)
        assertEquals(0, page(four, -1).index)
    }
}
