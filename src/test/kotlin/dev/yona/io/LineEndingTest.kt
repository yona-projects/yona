package dev.yona.io

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 줄바꿈 처리를 검증한다. 원본: `app/utils/LineEnding.java`.
 *
 * v1.6의 `changeLineEnding(…, DOS)`는 `\n`을 `\n`으로 바꾸는 코드라 아무것도 하지 않는다. 그대로 재현한다([refactoring-backlog.md] R-27).
 */
class LineEndingTest {

    @Test
    fun `줄바꿈 종류를 찾는다`() {
        assertEquals(LineEnding.EndingType.UNDEFINED, LineEnding.find(""))
        assertEquals(LineEnding.EndingType.UNDEFINED, LineEnding.find(null))
        assertEquals(LineEnding.EndingType.DOS, LineEnding.find("a\r\nb"))
        assertEquals(LineEnding.EndingType.UNIX, LineEnding.find("a\nb"))
        assertEquals(LineEnding.EndingType.UNIX, LineEnding.find("한 줄"))
    }

    @Test
    fun `유닉스로 바꾸면 CRLF를 LF로 바꾼다`() {
        assertEquals("a\nb\n", LineEnding.change("a\r\nb\r\n", "unix"))
        assertEquals("a\nb", LineEnding.change("a\r\nb", ""))
        assertEquals("a\nb", LineEnding.change("a\nb", null))
    }

    @Test
    fun `DOS로 바꾸라고 해도 v1_6처럼 내용이 그대로이다`() {
        assertEquals("a\nb", LineEnding.change("a\nb", "DOS"))
        assertEquals("a\nb", LineEnding.change("a\nb", "dos"))
        assertEquals("a\r\nb", LineEnding.change("a\r\nb", "DOS"))
    }

    @Test
    fun `빈 내용은 빈 문자열이다`() {
        assertEquals("", LineEnding.change("", "DOS"))
        assertEquals("", LineEnding.change(null, "unix"))
    }

    @Test
    fun `끝에 줄바꿈이 없으면 붙인다`() {
        assertEquals("a\n", LineEnding.addEol("a"))
        assertEquals("a\n", LineEnding.addEol("a\n"))
        assertEquals("a\r\nb\r\n", LineEnding.addEol("a\r\nb"))
        assertEquals("\n", LineEnding.addEol(""))
        assertNull(LineEnding.addEol(null))
    }
}
