package dev.yona.markdown

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [MarkdownRenderer]의 정화와 보안 동작을 검증한다. v1.6 `utils.Markdown`의 정책을 따른다. */
class MarkdownRendererTest {

    private val renderer = MarkdownRenderer()

    @Test
    fun `빈 문자열은 그대로 돌려준다`() {
        assertEquals("", renderer.render(""))
    }

    @Test
    fun `스크립트 태그를 제거한다`() {
        assertFalse(renderer.render("<script>alert(1)</script>hi").contains("script"))
    }

    @Test
    fun `자바스크립트 프로토콜 링크는 걸러진다`() {
        assertFalse(renderer.render("[x](javascript:alert(1))").contains("javascript"))
    }

    @Test
    fun `허용된 프로토콜의 링크는 유지한다`() {
        assertTrue(renderer.render("[x](mailto:a@b.c)").contains("href=\"mailto:a"))
    }

    @Test
    fun `비디오와 아이프레임은 허용 목록에 따라 유지한다`() {
        val html = renderer.render("<video controls src=\"http://a/b.mp4\"></video>")
        assertTrue(html.contains("<video"), html)
    }
}
