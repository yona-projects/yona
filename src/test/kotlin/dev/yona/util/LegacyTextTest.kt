package dev.yona.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [LegacyText]가 commons-lang `StringUtils.isBlank`와 Java `trim()`의 경계 입력에서 같은 결과를 내는지 검증한다. */
class LegacyTextTest {

    @Test
    fun `줄 바꿈 없는 공백만 든 값은 비어 있지 않다`() {
        assertFalse(LegacyText.isBlank(" "))
        assertFalse(LegacyText.isBlank("  "))
        assertTrue(LegacyText.isBlank(null))
        assertTrue(LegacyText.isBlank(""))
        assertTrue(LegacyText.isBlank(" \t\r\n　"))
        assertTrue(LegacyText.isNotBlank("a"))
    }

    @Test
    fun `자르기는 U+0020 이하의 글자만 지운다`() {
        assertEquals(" a ", LegacyText.trim(" a "))
        assertEquals("a", LegacyText.trim("\u0001 a\u0000\n"))
        assertEquals("　a", LegacyText.trim(" 　a "))
    }
}
