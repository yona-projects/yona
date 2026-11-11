package dev.yona.web.user

import dev.yona.web.HttpPreference
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter
import org.springframework.security.web.context.NullSecurityContextRepository
import org.springframework.transaction.support.TransactionTemplate

/**
 * 비밀번호 로그인(`POST /users/login`)을 스프링 시큐리티의 인증 필터로 처리한다(로드맵 X-43 3단계, `decisions.md` D-129). 이전에는 `LoginController`가 직접 했다.
 * v1.6 `UserApp.login`, `loginByFormRequest`, `loginByAjaxRequest`(`app/controllers/UserApp.java:154-259`)의 동작은 그대로이다.
 *
 * - 맡을 요청은 [PasswordLoginRequestMatcher]가 가린다(입력이 빠진 요청은 컨트롤러가 400으로 다시 그린다).
 * - 판정은 [authenticationManager]에 맡긴다(`LocalAuthenticator.authenticationManager`, 비밀번호 제공자 하나). 거절은 [dev.yona.security.LoginDeniedException]으로 온다.
 * - 성공과 실패의 응답은 [PasswordLoginSuccessHandler], [PasswordLoginFailureHandler]가 만든다(폼은 리다이렉트와 안내, AJAX는 JSON).
 * - **스프링이 성공한 인증을 세션에 저장하지 않게 한다**(`NullSecurityContextRepository`). 세션 시작은 2단계 인증 관문과 세션 식별자 회전을 거치는
 *   [dev.yona.security.LoginSessionService]가 맡는다. 그래야 2단계 인증을 켠 사용자가 비밀번호만으로 로그인되지 않는다.
 * - 이전의 컨트롤러 `@Transactional`처럼 판정과 응답 처리를 한 트랜잭션 안에서 한다.
 *
 * **스프링 빈이 아니다.** 서블릿 필터 빈은 부트가 보안 체인과 따로 한 번 더 등록해 로그인이 두 번 처리된다. 보안 설정이 직접 만들어 체인에 넣는다.
 */
class PasswordLoginFilter(
    authenticationManager: AuthenticationManager,
    success: PasswordLoginSuccessHandler,
    private val failure: PasswordLoginFailureHandler,
    private val transactions: TransactionTemplate
) : AbstractAuthenticationProcessingFilter(PasswordLoginRequestMatcher(), authenticationManager) {

    init {
        setAuthenticationSuccessHandler(success)
        setAuthenticationFailureHandler(failure)
        // 스프링이 성공한 인증을 세션에 저장하지 않는다. 세션 시작은 처리기가 LoginSessionService로 한다
        setSecurityContextRepository(NullSecurityContextRepository())
    }

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (!requiresAuthentication(request as HttpServletRequest, response as HttpServletResponse)) {
            chain.doFilter(request, response)
            return
        }
        // 맡은 요청은 처리기가 응답을 끝내므로 체인을 잇지 않는다(필터는 `continueChainBeforeSuccessfulAuthentication`을 켜지 않았다)
        transactions.executeWithoutResult { super.doFilter(request, response, chain) }
    }

    override fun attemptAuthentication(request: HttpServletRequest, response: HttpServletResponse): Authentication {
        noCache(response)
        // 같은 아이디로 연달아 틀려 잠겨 있으면 비밀번호를 확인하지 않고 거절한다(usersec-F11)
        if (failure.isLocked(request)) throw dev.yona.security.LoginDeniedException(dev.yona.security.LoginResult.TooManyAttempts)
        val token = UsernamePasswordAuthenticationToken.unauthenticated(request.getParameter("loginIdOrEmail"), request.getParameter("password"))
        return authenticationManager.authenticate(token)
    }

    /** 캐시를 막는 응답 헤더를 붙인다 (v1.6 `UserApp.noCache`). */
    private fun noCache(response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader(HttpHeaders.EXPIRES, "0")
    }

    companion object {
        /** JSON 응답을 원하는 요청(팝업의 AJAX 로그인)인지이다. */
        fun wantsJson(request: HttpServletRequest): Boolean = HttpPreference.isJsonPreferred(request.getHeader(HttpHeaders.ACCEPT))
    }
}
