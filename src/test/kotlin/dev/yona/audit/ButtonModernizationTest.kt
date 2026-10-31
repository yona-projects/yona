package dev.yona.audit

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 단추를 BS2 모양(그라데이션, 글자 그림자, 직접 만든 드롭다운 단추)에서 Bootstrap 5 고유 모양으로 옮긴 것을 지키는 구조 검사이다(X-38i, `decisions.md` D-56).
 */
class ButtonModernizationTest {

    private fun text(path: String) = File(path).readText()

    private val templates = File("src/main/resources/templates").walkTopDown().filter { it.isFile && it.extension == "html" }.toList()

    @Test
    fun `BS2 단추 모양을 입힌 자체 SCSS가 없다`() {
        assertFalse(File("app/assets/stylesheets/scss/_buttons.scss").exists(), "_buttons.scss(BS2 .btn 모양)는 지워져야 한다")
        assertFalse(text("app/assets/stylesheets/yobi.scss").contains("scss/buttons"), "yobi.scss가 buttons를 가져오면 안 된다")
    }

    @Test
    fun `드롭다운 단추는 BS5 단추 변종과 기본 화살표를 쓰고 d-caret 표식이 없다`() {
        val offenders = templates.filter { f -> Regex("""class="btn dropdown-toggle""").containsMatchIn(f.readText()) }
        assertTrue(offenders.isEmpty(), "btn-outline-secondary 없이 btn dropdown-toggle을 쓰는 템플릿: ${offenders.map { it.name }}")
        val carets = templates.filter { it.readText().contains("d-caret") }
        assertTrue(carets.isEmpty(), "d-caret 표식이 남은 템플릿: ${carets.map { it.name }}")
        val scss = File("app/assets/stylesheets/scss").walkTopDown().filter { it.extension == "scss" }.joinToString("\n") { it.readText() }
        assertFalse(scss.contains(".d-caret"), "SCSS에 .d-caret 규칙이 남아 있다")
    }

    @Test
    fun `지켜보기 단추는 BS5 변종으로 상태를 나타낸다`() {
        val header = text("src/main/resources/templates/project/header.html")
        assertFalse(header.contains("watch-on"), "watch-on(BS2 색 덧입히기)이 남아 있다")
        assertTrue(header.contains("btn-success"), "지켜보는 중 상태는 btn-success로 나타내야 한다")
    }
}
