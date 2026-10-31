package dev.yona.audit

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 테마 색을 BS2 팔레트에서 Bootstrap 5 기본 팔레트로 옮긴 것을 지키는 구조 검사이다(X-38i, `decisions.md` D-58).
 */
class ThemeColorTest {

    private val dir = File("app/assets/stylesheets/scss")
    private val variables get() = File(dir, "_bs-variables.scss").readText()

    @Test
    fun `테마 색 변수는 BS5 기본 값이다`() {
        mapOf(
            "primary" to "#0d6efd", "secondary" to "#6c757d", "success" to "#198754", "info" to "#0dcaf0",
            "warning" to "#ffc107", "danger" to "#dc3545", "dark" to "#212529",
            "link-color" to "#0d6efd", "link-hover-color" to "#0a58ca",
        ).forEach { (name, value) ->
            assertTrue(Regex("""^\$$name:\s*${Regex.escape(value)};""", RegexOption.MULTILINE).containsMatchIn(variables), "\$$name 는 $value 여야 한다")
        }
    }

    @Test
    fun `자체 SCSS는 BS2 테마 색을 직접 쓰지 않고 변수를 쓴다`() {
        val old = Regex("""#0088cc|#08c\b|#005580|#b94a48|#468847|#3a87ad|#f89406""", RegexOption.IGNORE_CASE)
        val users = dir.walkTopDown().filter { it.isFile && it.extension == "scss" }.filter { old.containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertTrue(users.isEmpty(), "BS2 테마 색을 직접 쓰는 SCSS: $users")
    }

    @Test
    fun `밝은 info와 warning 배경에는 글자색까지 정해 주는 text-bg 클래스를 쓴다`() {
        val bare = Regex("""(?<![\w-])bg-(info|warning)(?![\w-])""")
        val users = File("src/main/resources/templates").walkTopDown().filter { it.isFile && it.extension == "html" }
            .filter { bare.containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertTrue(users.isEmpty(), "흰 글자가 밝은 배경에 올라가는 bg-info, bg-warning 사용처: $users (text-bg-info, text-bg-warning으로 바꾼다)")
    }
}
