package dev.yona.audit

import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 고정 940px 그리드를 Bootstrap 5 반응형 그리드로 옮긴 것을 지키는 구조 검사이다(X-38i, `decisions.md` D-57).
 */
class GridModernizationTest {

    private val templates = File("src/main/resources/templates").walkTopDown().filter { it.isFile && it.extension == "html" }.toList()

    @Test
    fun `자체 그리드 SCSS와 940px 컨테이너가 없다`() {
        assertFalse(File("app/assets/stylesheets/scss/_grid.scss").exists(), "_grid.scss(BS2 고정/유동 그리드)는 지워져야 한다")
        assertFalse(File("app/assets/stylesheets/yobi.scss").readText().contains("scss/grid"))
        val overrides = File("app/assets/stylesheets/scss/_bs-overrides.scss").readText()
        assertFalse(Regex("""\.container \{[^}]*940px""").containsMatchIn(overrides), ".container 940px 고정이 남아 있다")
        assertFalse(File("app/assets/stylesheets/scss/_bs-variables.scss").readText().contains("\$container-max-widths"), "컨테이너 최대 폭 재정의가 남아 있다")
    }

    @Test
    fun `모든 col-lg 열은 row의 직접 자식이고 열이 없는 row는 없다`() {
        val problems = mutableListOf<String>()
        templates.forEach { f ->
            val doc = Jsoup.parse(f.readText())
            doc.select("[class~=(^|\\s)col-lg-\\d+(\\s|$)]").forEach { e ->
                val p = e.parent()
                if (p == null || !p.hasClass("row")) problems += "${f.name}: <${e.tagName()} class=\"${e.className()}\"> 의 부모가 row가 아니다"
            }
            doc.select(".row").forEach { r ->
                val hasCol = r.children().any { c -> c.classNames().any { it.startsWith("col") } }
                if (!hasCol) problems += "${f.name}: <${r.tagName()} class=\"${r.className()}\"> 는 열이 없는 row이다"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
