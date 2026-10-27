package dev.yona.markdown

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [MarkdownRenderer]의 정화와 보안 동작을 검증한다. v1.6 `utils.Markdown`의 정책을 따른다. */
class MarkdownRendererTest {

    private val cache = RenderedMarkdownCache(maximumSize = 100)
    private val renderer = MarkdownRenderer(cache)

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

    @Test
    fun `같은 원문의 렌더링 결과를 캐시에서 재사용한다`() {
        val first = renderer.render("# 캐시")
        cache.cleanUp()
        assertEquals(1L, cache.size())
        assertEquals(first, renderer.render("# 캐시"))
        cache.cleanUp()
        assertEquals(1L, cache.size())
    }

    @Test
    fun `v1_6의 marked_js 결과를 그대로 낸다 (공식 v1_16_0과 같은 입력으로 확인한 모양)`() {
        // 강조 겹침 순서, 줄바꿈 처리, 제목 번호 매기기는 marked.js를 그대로 실행해야 같아진다
        assertEquals("<p><strong><em>둘다</em></strong></p>", renderer.render("***둘다***").trim())
        assertEquals("<p>줄바꿈<br />두번째줄</p>", renderer.render("줄바꿈  \n두번째줄").trim())
        assertEquals("<p>세번째<br />네번째</p>", renderer.render("세번째\n네번째").trim())
        val headings = renderer.render("# 한글 제목 & 특수 <b>\n# 제목1\n# 제목1\n")
        assertTrue(headings.contains("""id="yb-header-한글-제목-특수-""""), headings)
        assertTrue(headings.contains("""id="yb-header-제목1""""), headings)
        assertTrue(headings.contains("""id="yb-header-제목1-1""""), headings)
    }

    @Test
    fun `제목에는 id와 head-anchor 링크가 붙는다 (v1_6 marked_js 사용자 지정 판)`() {
        val html = renderer.render("# 제목\n")
        assertTrue(html.contains("""<h1 id="yb-header-제목">제목<a href="#yb-header-제목" class="head-anchor">#</a></h1>"""), html)
    }

    @Test
    fun `표의 머리글보다 많은 칸은 남긴다 (v1_6 Nashorn의 ES5 splice)`() {
        val html = renderer.render("| a | b |\n|---|---|\n| 1 | 2 | 3 |\n")
        assertTrue(html.contains("<td>3</td>"), html)
    }

    @Test
    fun `변환 중 스크립트 오류가 나면 원문을 이스케이프해 pre로 감싼다 (v1_6 renderByMarked)`() {
        // 참조 링크의 주소에 공백이 있으면 marked.js가 `Array.prototype.includes`(JDK 8 Nashorn에는 없다)를 불러 오류가 난다
        val source = "[ref]: http://example.com \"T\"\n[ref2]: <http://x.y/a b>\n\n[link][ref] [link2][ref2] 한글 é \"q\" <b>\n"
        val html = renderer.render(source)
        assertTrue(html.startsWith("<pre>[ref]: http://example.com") && html.contains("&lt;b&gt;") && html.endsWith("</pre>"), html)
    }
}
