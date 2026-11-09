package app.untilparking.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

/** 스펙 R2 반경 확대. */
class ExpandRadiusTest {

    private val expand = ExpandRadiusImpl()

    @Test
    fun `500 다음은 1000`() = assertEquals(1000, expand(500))

    @Test
    fun `1000 다음은 2000`() = assertEquals(2000, expand(1000))

    @Test
    fun `2000에도 없으면 더 넓히지 않는다`() = assertEquals(null, expand(2000))

    @Test
    fun `단계 사이 값이면 다음 단계로 올린다`() = assertEquals(1000, expand(700))
}
