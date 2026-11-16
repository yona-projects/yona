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
    fun `비디오는 허용 목록에 따라 유지한다`() {
        val html = renderer.render("<video controls src=\"http://a/b.mp4\"></video>")
        assertTrue(html.contains("<video"), html)
    }

    // ---- 정화 정책 강화(A-621, 선생님 결정): file 주소만 v1.6 그대로 두고 나머지 위험 요소를 막는다 ----

    @Test
    fun `iframe은 주소와 상관없이 제거한다 (외부 페이지를 끼워 넣는 피싱과 클릭 가로채기 방지)`() {
        for (src in listOf("https://evil.example/login", "http://intranet/wiki", "file:///etc/passwd")) {
            val html = renderer.sanitize("<iframe src=\"$src\" width=\"100%\" height=\"600\"></iframe>")
            assertFalse(html.contains("iframe"), html)
        }
    }

    @Test
    fun `file 주소의 링크는 v1_6처럼 유지한다`() {
        val html = renderer.sanitize("<a href=\"file:///share/doc.txt\">문서</a>")
        assertTrue(html.contains("href=\"file:///share/doc.txt\""), html)
    }

    @Test
    fun `입력란은 작업 목록의 체크박스만 남긴다 (가짜 로그인 틀 방지)`() {
        val html = renderer.sanitize("<input type=\"password\" name=\"pw\"><input type=\"text\"><input type=\"checkbox\" disabled checked>")
        assertFalse(html.contains("password") || html.contains("type=\"text\""), html)
        assertTrue(html.contains("type=\"checkbox\""), html)
        val tasks = renderer.render("- [x] 끝\n- [ ] 할 일")
        assertTrue(tasks.contains("type=\"checkbox\""), tasks)
    }

    @Test
    fun `id는 제목의 yb-header 이름 공간만 남긴다 (화면 요소와 id가 겹치는 DOM 덮어쓰기 방지)`() {
        val html = renderer.sanitize("<div id=\"cloneURL\">a</div><h1 id=\"yb-header-제목\">b</h1>")
        assertFalse(html.contains("cloneURL"), html)
        assertTrue(html.contains("id=\"yb-header-제목\""), html)
        assertTrue(renderer.render("# 제목").contains("id=\"yb-header-제목\""))
    }

    @Test
    fun `class는 마크다운 변환기가 쓰는 이름만 남긴다 (화면의 모달이나 알림 모양을 흉내 내는 것을 방지)`() {
        val html = renderer.sanitize("<span class=\"modal-backdrop\">a</span><code class=\"language-kotlin\">b</code><a class=\"head-anchor\" href=\"#x\">c</a>")
        assertFalse(html.contains("modal-backdrop"), html)
        assertTrue(html.contains("class=\"language-kotlin\"") && html.contains("class=\"head-anchor\""), html)
        // 앱이 만드는 변경 이력의 class는 같은 정화기를 거쳐도 남는다
        assertTrue(renderer.sanitize("<div class='history-made-by'>x</div>").contains("history-made-by"))
        val rendered = renderer.render("```kotlin\nval a = 1\n```")
        assertTrue(rendered.contains("language-kotlin"), rendered)
    }

    @Test
    fun `보는 사람이나 프로젝트 범위가 다르면 같은 원문도 결과를 따로 보관한다 (R-16)`() {
        // v1.6은 원문만 키로 써서, 권한이 있는 사람이 먼저 본 결과(읽을 수 없는 이슈의 제목이 든 링크)가 권한이 없는 사람에게도 돌아갔다
        val member = renderer.renderWithPipeline("본문", "p1:u1") { "$it 제목이 보인다" }
        val outsider = renderer.renderWithPipeline("본문", "p1:u0") { "$it 제목이 없다" }
        assertEquals(true, member.contains("제목이 보인다"), member)
        assertEquals(true, outsider.contains("제목이 없다"), outsider)
        // 프로젝트 문맥이 없는 render()와도 같은 키를 쓰지 않는다
        assertEquals(false, renderer.render("본문").contains("제목"))
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
        // marked 4.3.0으로 올리며 선생님이 승인한 차이(D-118): v1.6은 <strong><em>, CommonMark 순서는 <em><strong>이다(화면은 같다)
        assertEquals("<p><em><strong>둘다</strong></em></p>", renderer.render("***둘다***").trim())
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
