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
    fun `블록 요소 사이에 줄바꿈을 넣지 않는다(들여쓰기 출력을 끈다, 화면은 v1_6과 같다)`() {
        val html = "<p>a<br>b</p><ul><li>x<br>y</li></ul>"
        // v1.6(jsoup 1.8.3)은 블록 요소 앞뒤에 줄바꿈과 들여쓰기를 넣었다. 지금은 들여쓰기 출력을 꺼서 넣지 않는다. 블록 요소 사이의 공백은 화면에 나타나지 않는다(A-336)
        val expected = "<p>a<br>b</p><ul><li>x<br>y</li></ul>"
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
        // v1.6(jsoup 1.8.3)의 들여쓰기 출력은 본문 바로 아래의 인라인 요소 사이에 줄바꿈을 넣었다. 지금은 읽은 글자 그대로 쓴다. 마크다운 변환 결과는 인라인 요소를 `<p>` 안에 두므로
        // 본문 바로 아래에 인라인 요소가 나란히 놓이는 일은 없고, 있다면 사용자가 직접 쓴 HTML뿐이다(알려진 차이, A-336)
        assertEquals("<a href=\"https://yona.example.com/a\">x</a><a href=\"/b\">y</a>", processor(noReferrer = true).checkReferrer(html))
    }

    @Test
    fun `이미 있는 rel 값 뒤에 붙인다`() {
        assertEquals(
            "<a href=\"https://other.com\" rel=\"nofollow noreferrer\">x</a>",
            processor(noReferrer = true).checkReferrer("<a href=\"https://other.com\" rel=\"nofollow\">x</a>")
        )
    }
}
