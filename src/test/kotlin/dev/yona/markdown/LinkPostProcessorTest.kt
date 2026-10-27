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
    fun `블록 요소는 jsoup 1_8_3의 들여쓰기 출력으로 쓴다 (v1_6 doc_body_html)`() {
        val html = "<p>a<br>b</p><ul><li>x<br>y</li></ul>"
        val expected = "<p>a<br>b</p>\n<ul>\n <li>x<br>y</li>\n</ul>"
        assertEquals(expected, processor().removeJavascriptInHref(html))
        assertEquals(expected, processor(noReferrer = true).checkReferrer(html))
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
        // jsoup 1.8.3의 기본 출력은 인라인 요소를 줄에 나눠 쓴다(v1.6 `doc.body().html()`)
        assertEquals("<a href=\"https://yona.example.com/a\">x</a>\n<a href=\"/b\">y</a>", processor(noReferrer = true).checkReferrer(html))
    }

    @Test
    fun `이미 있는 rel 값 뒤에 붙인다`() {
        assertEquals(
            "<a href=\"https://other.com\" rel=\"nofollow noreferrer\">x</a>",
            processor(noReferrer = true).checkReferrer("<a href=\"https://other.com\" rel=\"nofollow\">x</a>")
        )
    }
}
