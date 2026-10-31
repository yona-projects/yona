package dev.yona.audit

import org.junit.jupiter.api.Test
import org.jsoup.Jsoup
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 폼 컨트롤을 BS2/브라우저 기본 모양으로 되돌리던 덧입히기를 지우고 Bootstrap 5 기본 모양을 쓰는 것을 지키는 구조 검사이다(X-38i, `decisions.md` D-56).
 */
class FormModernizationTest {

    private fun scss() = File("app/assets/stylesheets/scss").walkTopDown().filter { it.extension == "scss" }.joinToString("\n") { it.readText() }

    @Test
    fun `체크박스와 라디오를 브라우저 기본 모양으로 되돌리는 규칙이 없다`() {
        val css = scss()
        assertFalse(Regex("""\.form-check \.form-check-input \{""").containsMatchIn(css), ".form-check .form-check-input 되돌림 규칙이 남아 있다")
        assertFalse(Regex("""appearance:\s*auto""").containsMatchIn(css), "appearance: auto 되돌림이 남아 있다")
    }

    private val textTypes = setOf("text", "password", "email", "number", "search", "url", "date", "tel", "datetime", "datetime-local", "time", "month", "week", "file")

    /** 검색 막대(`.search-bar`, `.search-box`)는 입력창과 단추를 한 상자로 그린 사이트 고유 위젯이라 `.form-control` 대상이 아니다. */
    private fun ownWidget(e: org.jsoup.nodes.Element) = e.hasClass("textbox") || e.parents().any { it.hasClass("search-box") || it.hasClass("search-bar") }

    @Test
    fun `글자 입력창과 글상자와 선택 상자와 체크박스는 BS5 클래스를 단다`() {
        val missing = mutableListOf<String>()
        File("src/main/resources/templates").walkTopDown().filter { it.isFile && it.extension == "html" }.forEach { f ->
            val doc = Jsoup.parse(f.readText())
            doc.select("input, textarea, select").forEach { e ->
                if (e.hasAttr("th:type") || ownWidget(e)) return@forEach
                val type = e.attr("type").ifEmpty { "text" }.lowercase()
                val need = when {
                    e.tagName() == "textarea" -> "form-control"
                    e.tagName() == "select" -> "form-select"
                    type in setOf("checkbox", "radio") -> "form-check-input"
                    type in textTypes -> "form-control"
                    else -> null
                } ?: return@forEach
                if (!e.hasClass(need)) missing += "${f.name}: <${e.tagName()} ${e.attr("name")} type=$type> 에 $need 가 없다"
            }
        }
        assertTrue(missing.isEmpty(), missing.joinToString("\n"))
    }

    @Test
    fun `BS2 폼 요소 기본값 규칙이 베이스에 남아 있지 않다`() {
        val base = File("app/assets/stylesheets/scss/_base.scss").readText()
        assertFalse(base.contains("text-inputs"), "text-inputs 함수가 남아 있다")
        assertFalse(Regex("""input, textarea \{\s*width""").containsMatchIn(base), "input, textarea 고정 폭 규칙이 남아 있다")
        assertFalse(Regex("""box-shadow: inset 0 1px 1px""").containsMatchIn(base), "BS2 입력창 안쪽 그림자가 남아 있다")
    }
}
