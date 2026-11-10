package dev.yona.security

import dev.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.RememberMeServices
import org.springframework.stereotype.Component

/**
 * 로그인 유지 쿠키(`yona.token`)를 스프링 시큐리티의 [RememberMeServices]로 다룬다(로드맵 X-43 2단계 작업 2-1, `decisions.md` D-129).
 * 쿠키 이름, 값 형식(`로그인ID:만료시각:서명`), 30일, `HttpOnly`, `SameSite=Lax`는 [RememberMeCookies]가 그대로 맡고, 이 클래스는 읽기, 만들기, 지우기의 입구이다.
 *
 * - [autoLogin]: 쿠키로 사용자를 찾아 [YonaAuthentication]을 돌려준다. 서명이 맞지 않거나 기간이 지났거나 비밀번호가 바뀌었거나 옛 형식이거나 계정이
 *   잠기거나 지워졌으면 쿠키를 지우고 `null`이다(v1.6은 해시만 같으면 잠긴 계정도 통과시켰다, R-05, R-107). 2단계 인증을 켠 사용자는 알려진 기기 쿠키가
 *   함께 있을 때만 통과시키며, 이때는 쿠키를 지우지 않고 익명으로 둔다(X-2, D-69). 세션 저장과 서버 캐시 기록은 호출하는 필터가 한다.
 * - [loginSuccess]: 그 사용자의 로그인 유지 쿠키를 응답에 싣는다. 로그인 유지를 고른 때만 호출한다(스프링 기본처럼 요청 인자를 보지 않는다).
 * - [loginFail]: 쿠키를 지운다.
 */
@Component
class YonaRememberMeServices(
    private val users: UserRepository,
    private val cookies: RememberMeCookies,
    private val gate: TwoFactorGate
) : RememberMeServices {

    override fun autoLogin(request: HttpServletRequest, response: HttpServletResponse): Authentication? {
        val cookie = request.cookies?.firstOrNull { it.name == YonaAuthenticationFilter.REMEMBER_ME_COOKIE } ?: return null
        val user = RememberMeCookies.loginIdOf(cookie.value)?.let { users.findByLoginId(it) }
        // 서명이 맞지 않거나 기간이 지났거나 비밀번호가 바뀌었거나 옛 형식이면 쿠키를 지운다. 잠기거나 지워진 계정도 쿠키로 로그인되지 않는다.
        // v1.6은 쿠키의 해시가 비밀번호와 같기만 하면 잠기거나 지워진 계정도 로그인 상태로 보았다(R-05, R-107)
        if (user == null || user.state.isLockedOut() || !cookies.verify(cookie.value, user)) {
            loginFail(request, response)
            return null
        }
        // 2단계 인증을 켠 사용자는 알려진 기기 쿠키가 함께 있을 때만 이 쿠키로 인정한다(X-2, D-69). 쿠키는 지우지 않고 익명으로 둔다
        if (!gate.passes(user, request)) return null
        return YonaAuthentication.of(user, viaCookie = true)
    }

    override fun loginFail(request: HttpServletRequest, response: HttpServletResponse) {
        YonaAuthenticationFilter.discardRememberMeCookie(response)
    }

    override fun loginSuccess(request: HttpServletRequest, response: HttpServletResponse, successfulAuthentication: Authentication) {
        val principal = (successfulAuthentication as? YonaAuthentication)?.principal ?: return
        val user = users.findById(principal.userId).orElse(null) ?: return
        response.addCookie(cookies.create(user, secure = request.isSecure))
    }
}
