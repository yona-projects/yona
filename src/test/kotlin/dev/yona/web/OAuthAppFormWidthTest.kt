package dev.yona.web

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OAuth 앱 폼의 입력란이 같은 화면의 종류 카드와 범위 목록(`tf-group`, 최대 760px)과 오른쪽 끝이 맞는지 지킨다(`decisions.md` A-453).
 * 입력란과 설명 칸이 `tf-wide`(560px)라서 아래 카드와 목록(760px)보다 짧아 보였다. LFS 화면은 560px 카드와 맞으므로 `tf-wide`는 그대로 두고 OAuth 폼 전용 클래스를 쓴다.
 */
class OAuthAppFormWidthTest {

    @Test
    fun `OAuth 앱 폼 전용 폭 클래스는 범위 목록과 같은 760px이다`() {
        val scss = File("app/assets/stylesheets/scss/_page.scss").readText()
        val group = Regex("\\.two-factor-page\\s+\\.tf-group\\s*\\{[^}]*max-width:\\s*(\\d+)px").find(scss)?.groupValues?.get(1)
        val form = Regex("\\.two-factor-page\\s+\\.tf-group-wide\\s*\\{[^}]*max-width:\\s*(\\d+)px").find(scss)?.groupValues?.get(1)
        assertTrue(form != null, "_page.scss에 `.two-factor-page .tf-group-wide { max-width: ...px }`가 있어야 한다")
        assertEquals(group, form, "OAuth 폼의 입력란 폭은 tf-group의 최대 폭과 같아야 한다")
    }

    @Test
    fun `OAuth 앱 폼의 입력란과 설명 칸은 tf-group-wide를 쓴다`() {
        val text = File("src/main/resources/templates/oauth/partial-apps.html").readText()
        val fields = Regex("<(input|textarea)\\b[^>]*\\bid=\"(oauth-name|oauth-description|oauth-home|oauth-redirects|oauth-transfer-confirm)\"[^>]*>").findAll(text).toList()
        assertEquals(5, fields.size, "폼 입력 요소 5개")
        val wrong = fields.filter { "tf-group-wide" !in it.value }.map { it.groupValues[2] }
        assertEquals(emptyList(), wrong, "tf-group-wide가 없는 입력 요소")
    }
}
