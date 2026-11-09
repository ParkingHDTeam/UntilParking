package app.untilparking.usecase

import app.untilparking.domain.model.SortKey
import app.untilparking.domain.model.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Test

/** 스펙 R10 음성 명령 (기획서 26절, T05). */
class VoiceCommandTest {

    private val parse = ParseVoiceCommandImpl()

    @Test
    fun `기계식 빼줘는 기계식 제외`() = assertEquals(VoiceCommand.ExcludeMechanical(true), parse("기계식 빼줘", 3))

    @Test
    fun `기계식 포함해줘는 기계식 포함`() = assertEquals(VoiceCommand.ExcludeMechanical(false), parse("기계식 포함해줘", 3))

    @Test
    fun `잔여 정보 없는 곳 제외`() = assertEquals(VoiceCommand.IncludeUnknownAvailability(false), parse("잔여 정보 없는 곳 제외", 3))

    @Test
    fun `잔여 정보 없는 곳 포함`() = assertEquals(VoiceCommand.IncludeUnknownAvailability(true), parse("잔여 정보 없는 곳 포함", 3))

    @Test
    fun `첫 번째는 0번`() = assertEquals(VoiceCommand.Select(0), parse("첫 번째", 3))

    @Test
    fun `두 번째는 1번`() = assertEquals(VoiceCommand.Select(1), parse("두 번째", 3))

    @Test
    fun `세 번째는 2번`() = assertEquals(VoiceCommand.Select(2), parse("세 번째", 3))

    @Test
    fun `2번도 1번 선택이다`() = assertEquals(VoiceCommand.Select(1), parse("2번", 3))

    @Test
    fun `네 번째는 보이는 범위 밖이라 불분명`() = assertEquals(VoiceCommand.Unclear, parse("네 번째", 3))

    @Test
    fun `보이는 게 둘인데 세 번째면 불분명`() = assertEquals(VoiceCommand.Unclear, parse("세 번째", 2))

    @Test
    fun `거리순`() = assertEquals(VoiceCommand.Sort(SortKey.DISTANCE), parse("거리순으로", 3))

    @Test
    fun `요금순`() = assertEquals(VoiceCommand.Sort(SortKey.COST), parse("요금순", 3))

    @Test
    fun `여유 공간순`() = assertEquals(VoiceCommand.Sort(SortKey.AVAILABILITY), parse("여유 공간순", 3))

    @Test
    fun `다음 페이지`() = assertEquals(VoiceCommand.NextPage, parse("다음 페이지", 3))

    @Test
    fun `이전 페이지`() = assertEquals(VoiceCommand.PreviousPage, parse("이전 페이지", 3))

    @Test
    fun `안내 시작`() = assertEquals(VoiceCommand.StartGuidance, parse("안내 시작", 3))

    @Test
    fun `예만으로는 경로를 보내지 않는다`() = assertEquals(VoiceCommand.Unclear, parse("예", 3))

    @Test
    fun `취소 그만 음성 종료는 종료`() {
        assertEquals(VoiceCommand.Cancel, parse("취소", 3))
        assertEquals(VoiceCommand.Cancel, parse("그만", 3))
        assertEquals(VoiceCommand.Cancel, parse("음성 종료", 3))
    }

    @Test
    fun `기계식 빼고 두 번째는 필터만 적용한다`() = assertEquals(VoiceCommand.ExcludeMechanical(true), parse("기계식 빼고 두 번째", 3))

    @Test
    fun `빈 말은 불분명`() = assertEquals(VoiceCommand.Unclear, parse("", 3))

    @Test
    fun `모르는 말은 불분명`() = assertEquals(VoiceCommand.Unclear, parse("오늘 날씨 어때", 3))
}
