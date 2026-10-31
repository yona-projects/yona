package dev.yona.audit

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 알림, 배지, 표, 탭을 BS2 모양에서 Bootstrap 5 기본 모양으로 옮긴 것을 지키는 구조 검사이다(X-38i, `decisions.md` D-56).
 */
class ComponentModernizationTest {

    private fun text(path: String) = File(path).readText()
    private val variables get() = text("app/assets/stylesheets/scss/_bs-variables.scss")
    private val templates get() = File("src/main/resources/templates").walkTopDown().filter { it.isFile && it.extension == "html" }.toList()

    @Test
    fun `알림과 배지는 BS5 기본 모양이다`() {
        assertFalse(File("app/assets/stylesheets/scss/_alerts.scss").exists(), "_alerts.scss(BS2 알림, 배지 모양)는 지워져야 한다")
        assertFalse(text("app/assets/stylesheets/yobi.scss").contains("scss/alerts"))
        listOf("alert-warnning", "alert-block").forEach { cls ->
            val users = templates.filter { it.readText().contains(cls) }
            assertTrue(users.isEmpty(), "$cls 를 쓰는 템플릿: ${users.map { it.name }}")
        }
        listOf("\$alert-", "\$badge-", "-bg-subtle", "-text-emphasis", "-border-subtle").forEach {
            assertFalse(variables.contains(it), "_bs-variables.scss에 BS2 알림/배지 변수 $it 가 남아 있다")
        }
        assertFalse(Regex("""\.badge \{\s*border-radius: 1px""").containsMatchIn(text("app/assets/stylesheets/scss/_override.scss")), ".badge 모서리 덧입히기가 남아 있다")
    }

    @Test
    fun `표는 BS5 기본 모양이다`() {
        assertFalse(File("app/assets/stylesheets/scss/_tables.scss").exists(), "_tables.scss(BS2 표 모양)는 지워져야 한다")
        assertFalse(text("app/assets/stylesheets/yobi.scss").contains("scss/tables"))
        assertFalse(variables.contains("\$table-"), "_bs-variables.scss에 BS2 표 변수가 남아 있다")
    }

    @Test
    fun `탭은 BS5 기본 모양이고 아래 여백은 마크업이 준다`() {
        assertFalse(variables.contains("\$nav-"), "_bs-variables.scss에 BS2 탭 변수가 남아 있다")
        val ui = text("app/assets/stylesheets/scss/_yobiUI.scss")
        assertFalse(ui.contains("padding-left:30px; padding-right:30px"), "탭 링크 BS2 안쪽 여백이 남아 있다")
        assertFalse(Regex("""^\.nav \{""", RegexOption.MULTILINE).containsMatchIn(ui), ".nav 아래 여백 덧입히기가 남아 있다")
        val bare = templates.filter { f -> Regex("""<(?:ul|div|nav)\b[^>]*\bclass="[^"]*\bnav-tabs\b[^"]*"[^>]*>""").findAll(f.readText()).any { m -> !m.value.contains("mb-") && !m.value.contains("margin-bottom") && !m.value.contains("mb10") } }
        assertTrue(bare.isEmpty(), "아래 여백(mb-*)이 없는 nav-tabs: ${bare.map { it.name }}")
    }

    @Test
    fun `BS2식 글자 그림자와 안쪽 그림자와 진행 막대 그라데이션이 없다`() {
        assertFalse(File("app/assets/stylesheets/scss/_progress.scss").exists(), "_progress.scss(BS2 진행 막대)는 지워져야 한다")
        assertFalse(text("app/assets/stylesheets/yobi.scss").contains("scss/progress"))
        val scss = File("app/assets/stylesheets/scss").walkTopDown().filter { it.extension == "scss" }.joinToString("\n") { it.readText() }
        val shadows = Regex("""text-shadow:[ \t]*(?!none|0 0 0|[ \t])[^;]+;""").findAll(scss).map { it.value }.toList()
        // 이미지 위에 겹치는 프로젝트 로고의 수정 글자(`.logo .edit`)만 읽기 쉽도록 그림자를 둔다.
        assertTrue(shadows.size <= 1, "글자 그림자가 남아 있다: $shadows")
        assertFalse(variables.contains("\$dropdown-box-shadow"), "BS2 드롭다운 그림자 변수가 남아 있다")
        assertFalse(Regex("""\.popover \{[^}]*box-shadow""").containsMatchIn(text("app/assets/stylesheets/scss/_yobiUI.scss")), ".popover 그림자 덧입히기가 남아 있다")
        assertFalse(scss.contains("inset 0 1px 1px rgba(0,0,0,0.1)"), "배지의 BS2 안쪽 그림자가 남아 있다")
    }
}
