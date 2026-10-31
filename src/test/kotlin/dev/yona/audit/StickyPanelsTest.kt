package dev.yona.audit

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * v1.6의 `affix` 동작을 CSS `position: sticky`로 옮긴 곳(`decisions.md` A-291)을 지키는 구조 검사이다.
 *
 * 위치만 붙잡으면 되는 요소(이슈 정보창, 관리자 막대)는 스크립트 없이 CSS로 고정한다.
 * `sticky`는 부모 열이 행 전체 높이를 가져야 움직일 수 있으므로, 정보창이 든 행은 `d-flex`로 열을 늘린다.
 */
class StickyPanelsTest {

    private fun text(path: String) = File(path).readText()

    @Test
    fun `이슈 정보창은 sticky로 고정되고 스크립트가 affix를 부르지 않는다`() {
        val scss = text("app/assets/stylesheets/scss/_page.scss")
        assertTrue(Regex("""\.issue-info \{[^}]*?&\.sticky-top""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(scss), ".issue-info.sticky-top 규칙이 있어야 한다")
        listOf("issue/view.html", "board/view.html").forEach {
            assertTrue(Regex("""class="issue-info[^"]*\bsticky-top\b""").containsMatchIn(text("src/main/resources/templates/$it")), "$it 의 정보창에 sticky-top이 있어야 한다")
        }
        listOf("yobi.issue.View.js", "yobi.board.View.js").forEach {
            assertFalse(text("src/main/resources/static/assets/javascripts/service/$it").contains("yona.affix"), "$it 는 affix를 부르면 안 된다")
        }
    }

    @Test
    fun `정보창이 든 행은 열이 행 높이만큼 늘어나도록 d-flex이다`() {
        listOf("issue/view.html", "board/view.html").forEach {
            assertTrue(text("src/main/resources/templates/$it").contains("""<div class="board-body row d-flex">"""), "$it 의 board-body 행에 d-flex가 있어야 한다")
        }
    }

    @Test
    fun `관리자 막대는 sticky이고 data-affix-top 표식을 쓰지 않는다`() {
        val scss = text("app/assets/stylesheets/scss/_page.scss")
        assertTrue(Regex("""\.admin-logged-in-affix \{[^}]*?position:\s*sticky""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(scss))
        listOf("layout/layout.html", "layout/migration-layout.html").forEach {
            assertFalse(text("src/main/resources/templates/$it").contains("data-affix-top"), "$it 에 data-affix-top이 남아 있다")
        }
    }

    @Test
    fun `쓰지 않는 BS2 글꼴과 그림과 less 스크립트는 저장소에 없다`() {
        listOf(
            "src/main/resources/static/assets/bootstrap/fonts",
            "src/main/resources/static/assets/bootstrap/images",
            "src/main/resources/static/assets/javascripts/lib/less-1.3.0.min.js"
        ).forEach { assertFalse(File(it).exists(), "$it 는 지워져야 한다") }
    }

    @Test
    fun `일괄 수정 막대와 리뷰 상자는 sticky이고 affix 스크립트와 클래스를 쓰지 않는다`() {
        val scss = text("app/assets/stylesheets/scss/_page.scss")
        assertTrue(Regex("""\.mass-update-sticky \{[^}]*?position:\s*sticky""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(scss), ".filter-wrap.mass-update-sticky는 sticky여야 한다")
        listOf("issue/partial-list-wrap.html", "milestone/view.html").forEach {
            assertTrue(text("src/main/resources/templates/$it").contains("mass-update-sticky"), "$it 의 막대 줄에 mass-update-sticky가 있어야 한다")
        }
        assertTrue(Regex("""\.review-container \{[^}]*?position:\s*sticky""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(scss), ".review-container는 sticky여야 한다")
        assertFalse(Regex("""&\.affix|^\.affix""", RegexOption.MULTILINE).containsMatchIn(scss), "SCSS에 .affix 규칙이 남아 있다")
        listOf("yobi.issue.MassUpdate.js", "yobi.code.Diff.js").forEach {
            val js = text("src/main/resources/static/assets/javascripts/service/$it")
            assertFalse(js.contains("yona.affix"), "$it 는 yona.affix를 부르면 안 된다")
            assertFalse(js.contains("'affix") || js.contains("\"affix"), "$it 에 affix 클래스 참조가 남아 있다")
        }
    }

    @Test
    fun `yona Affix 스크립트는 저장소와 스크립트 목록에 없다`() {
        assertFalse(File("src/main/resources/static/assets/javascripts/common/yona.Affix.js").exists())
        assertFalse(text("src/main/resources/templates/common/scripts.html").contains("yona.Affix.js"))
    }

    @Test
    fun `일괄 수정 막대 줄은 붙은 동안 v1_6처럼 위아래 여백과 아래 선을 가진다`() {
        val scss = text("app/assets/stylesheets/scss/_page.scss")
        val rule = Regex("""\.mass-update-sticky \{.*?&\.stuck \{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL).find(scss)?.groupValues?.get(1)
        assertTrue(rule != null, ".mass-update-sticky.stuck 규칙이 있어야 한다")
        assertTrue(rule!!.contains("padding: 15px 0") && rule.contains("border-bottom: 1px solid rgba(0, 0, 0, 0.1)"), "v1.6 affix 값(여백 15px 0, 아래 선)이어야 한다")
        val js = text("src/main/resources/static/assets/javascripts/service/yobi.issue.MassUpdate.js")
        assertTrue(js.contains("toggleClass('stuck'") || js.contains("toggleClass(\"stuck\""), "스크립트가 붙은 상태를 stuck 클래스로 표시해야 한다")
    }
}
