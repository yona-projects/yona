package dev.yona.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LikeEscapeTest {

    @Test
    fun `퍼센트, 밑줄, 역슬래시를 이스케이프한다`() {
        assertEquals("a\\_b\\%c\\\\d", LikeEscape.escape("a_b%c\\d"))
        assertEquals("plain", LikeEscape.escape("plain"))
        assertEquals("", LikeEscape.escape(null))
    }

    @Test
    fun `포함 패턴은 소문자로 만들고 앞뒤에 퍼센트를 붙인다`() {
        assertEquals("%a\\_b%", LikeEscape.contains("A_B"))
    }
}
