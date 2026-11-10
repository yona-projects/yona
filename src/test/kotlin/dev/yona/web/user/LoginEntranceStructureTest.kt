package dev.yona.web.user

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 비밀번호 로그인 입구가 스프링의 인증 필터로 옮겨졌는지 지키는 구조 시험이다(로드맵 X-43 3단계, `decisions.md` D-129). 인증 판정과 성공, 실패 처리는
 * `PasswordLoginFilter`와 처리기가 맡고, `LoginController`에는 입력이 빠진 요청을 400으로 다시 그리는 일과 로그아웃만 남는다.
 */
class LoginEntranceStructureTest {

    private val main = File("src/main/kotlin/dev/yona")

    @Test
    fun `로그인 컨트롤러는 인증 판정과 성공 처리를 하지 않는다`() {
        val text = File(main, "web/user/LoginController.kt").readText()
        listOf("LocalAuthenticator", "twoFactorLogin.begin", "completion.complete", "audit.loginSuccess", "audit.loginFailure").forEach {
            assertFalse(text.contains(it), "LoginController가 아직 $it 을 쓴다")
        }
    }

    @Test
    fun `보안 설정은 비밀번호 로그인 필터를 등록한다`() {
        val text = File(main, "config/SecurityConfig.kt").readText()
        assertTrue(text.contains("PasswordLoginFilter("), "SecurityConfig에 PasswordLoginFilter가 없다")
    }

    @Test
    fun `비밀번호 로그인 필터는 스프링 빈이 아니다`() {
        // 서블릿 필터 빈은 부트가 보안 체인과 따로 한 번 더 등록해 로그인이 두 번 처리된다
        val text = File(main, "web/user/PasswordLoginFilter.kt").readText()
        assertEquals(0, Regex("""@(Component|Service|Bean)\s*\n\s*class PasswordLoginFilter""").findAll(text).count())
    }
}
