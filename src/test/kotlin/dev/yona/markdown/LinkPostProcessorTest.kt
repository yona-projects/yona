package dev.yona.markdown

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 렌더링한 HTML의 링크 후처리를 검증한다.
 *
 * 원본: `app/utils/Markdown.java:88-131` (`removeJavascriptInHref`, `checkReferrer`).
 */
class LinkPostProcessorTest {

    private fun processor(noReferrer: Boolean = false, hostname: String = "yona.example.com") = LinkPostProcessor(noReferrer, hostname)

    @Test
    fun `자바스크립트 링크는 샵으로 바뀐다`() {
        assertEquals("<a href=\"#\">x</a>", processor().removeJavascriptInHref("<a href=\"javascript:alert(1)\">x</a>"))
    }

    @Test
    fun `난독화한 자바스크립트 링크도 막는다`() {
        assertEquals("<a href=\"#\">x</a>", processor().removeJavascriptInHref("<a href=\"java\nscript:alert(1)\">x</a>"))
        assertEquals("<a href=\"#\">x</a>", processor().removeJavascriptInHref("<a href=\" JaVaScRiPt:alert(1)\">x</a>"))
    }

    @Test
    fun `줄바꿈 태그 앞뒤에 줄바꿈이나 들여쓰기를 넣지 않는다 (jsoup 1_8_3 출력과 같다)`() {
        val html = "<p>a<br>b</p><ul><li>x<br>y</li></ul>"
        assertEquals(html, processor().removeJavascriptInHref(html))
        assertEquals(html, processor(noReferrer = true).checkReferrer(html))
    }

    @Test
    fun `일반 링크는 그대로 둔다`() {
        assertEquals("<a href=\"https://yona.io\">x</a>", processor().removeJavascriptInHref("<a href=\"https://yona.io\">x</a>"))
    }

    @Test
    fun `설정이 꺼져 있으면 외부 링크를 그대로 둔다`() {
        val html = "<a href=\"https://other.com/a\">x</a>"
        assertEquals(html, processor(noReferrer = false).checkReferrer(html))
    }

    @Test
    fun `설정이 켜져 있으면 외부 링크에 noreferrer를 붙인다`() {
        assertEquals(
            "<a href=\"https://other.com/a\" rel=\" noreferrer\">x</a>",
            processor(noReferrer = true).checkReferrer("<a href=\"https://other.com/a\">x</a>")
        )
    }

    @Test
    fun `같은 사이트의 링크와 상대 링크에는 붙이지 않는다`() {
        val html = "<a href=\"https://yona.example.com/a\">x</a><a href=\"/b\">y</a>"
        assertEquals(html, processor(noReferrer = true).checkReferrer(html))
    }

    @Test
    fun `이미 있는 rel 값 뒤에 붙인다`() {
        assertEquals(
            "<a href=\"https://other.com\" rel=\"nofollow noreferrer\">x</a>",
            processor(noReferrer = true).checkReferrer("<a href=\"https://other.com\" rel=\"nofollow\">x</a>")
        )
    }
}
