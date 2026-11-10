package dev.yona.web.project

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * 규칙 묶음 폼의 입력란 너비를 지킨다(`decisions.md` A-453).
 *
 * 이 폼의 입력란은 `input-xlarge`, `input-large`, `input-mini` 같은 옛 크기 클래스를 쓰는데 BS5로 옮기면서 그 클래스의 정의가 없어졌다. 그러면 `.form-control`의 `width: 100%`가
 * 내용 크기에 맞춰 줄어드는 칸(`.cu-desc`) 안에서 풀려, 도움말이 있는 칸만 넓고 없는 칸은 좁아졌다(이름 199px, 대상 브랜치 415px).
 * 스타일 규칙이 글 입력란을 같은 너비로, 숫자 입력란을 좁게 정해야 한다.
 */
class RulesetFormWidthTest {

    private val scss = File("app/assets/stylesheets/scss/_page.scss").readText()

    private fun ruleBody(selector: String): String? =
        Regex(Regex.escape(selector) + "\\s*\\{([^}]*)\\}").find(scss)?.groupValues?.get(1)

    @Test
    fun `규칙 묶음 폼의 글 입력란은 같은 너비를 가진다`() {
        val body = ruleBody("#rulesetForm input.form-control[type=\"text\"]")
        assertTrue(body != null && Regex("width:\\s*420px").containsMatchIn(body), "_page.scss에 `#rulesetForm input.form-control[type=\"text\"] { width: 420px }` 규칙이 있어야 한다")
    }

    @Test
    fun `규칙 묶음 폼의 숫자 입력란은 좁다`() {
        val body = ruleBody("#rulesetForm input.input-mini")
        assertTrue(body != null && Regex("width:\\s*5rem").containsMatchIn(body), "_page.scss에 `#rulesetForm input.input-mini { width: 5rem }` 규칙이 있어야 한다")
    }
}
