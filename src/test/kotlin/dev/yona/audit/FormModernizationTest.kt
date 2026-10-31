package dev.yona.audit

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse

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
}
