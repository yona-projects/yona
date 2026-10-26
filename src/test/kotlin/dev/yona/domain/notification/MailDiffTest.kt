package dev.yona.domain.notification

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 본문 변경 알림 메일에 넣는 변경 차이 표시를 검증한다. 원본: `app/utils/DiffUtil.java`.
 */
class MailDiffTest {

    @Test
    fun `HTML 차이는 삭제와 추가에 배경색을 입히고 줄바꿈을 바꾼다`() {
        val html = MailDiff.html("abc\nxyz", "abd\nxyz")
        assertTrue(html.contains("<span style='background-color: #fda9a6;padding: 2px 0;'>c</span>"), html)
        assertTrue(html.contains("<span style='background-color: #abdd52;padding: 2px 0;'>d</span>"), html)
        assertTrue(html.contains("&nbsp<br/>\n"), html)
    }

    @Test
    fun `같은 부분이 100자를 넘으면 앞뒤 50자만 남기고 말줄임을 넣으며 그 안의 줄바꿈도 바뀐다`() {
        val same = "a".repeat(60) + "b".repeat(60)
        val html = MailDiff.html("$same!", "$same?")
        assertTrue(html.contains("a".repeat(50) + "<span style='color: #bdbdbd;font-size: 16px;font-family: serif;'>...&nbsp<br/>\n......&nbsp<br/>\n......&nbsp<br/>\n...</span>" + "b".repeat(50)), html)
    }

    @Test
    fun `HTML 특수 문자는 이스케이프한다`() {
        assertTrue(MailDiff.html("", "<b>").contains("&lt;b&gt;"))
    }

    @Test
    fun `어포스트로피는 이스케이프하지 않는다 (v1_6 escapeHtml4)`() {
        // commons-lang3 escapeHtml4는 '를 바꾸지 않는다. 일반 텍스트 메일에 &#39;가 그대로 보이지 않게 한다.
        assertTrue(MailDiff.html("a'b", "a'c").contains("a'"), MailDiff.html("a'b", "a'c"))
        assertTrue(!MailDiff.html("a'b", "a'c").contains("&#39;"))
        assertTrue(MailDiff.plain("it's", "it's ok").contains("it's"))
        // 입력에 이미 있던 &#39;는 &로 시작하므로 &amp;#39;가 되어 되돌리지 않는다
        assertTrue(MailDiff.html("", "&#39; '").contains("&amp;#39; '"), MailDiff.html("", "&#39; '"))
    }

    @Test
    fun `일반 텍스트 차이는 삭제는 3개의 빼기, 추가는 3개의 더하기로 시작한다`() {
        assertEquals("--- c\n+++ d\n", MailDiff.plain("c", "d"))
        assertEquals("ab\n+++ c\n", MailDiff.plain("ab", "abc"))
    }

    @Test
    fun `일반 텍스트에서도 같은 부분이 길면 말줄임으로 줄인다`() {
        val same = "a".repeat(60) + "b".repeat(60)
        assertEquals("a".repeat(50) + "......\n......\n...\n" + "b".repeat(50) + "\n+++ ?\n", MailDiff.plain(same, "$same?"))
    }

    @Test
    fun `값이 없으면 빈 문자열로 본다`() {
        assertEquals("+++ x\n", MailDiff.plain(null, "x"))
    }
}
