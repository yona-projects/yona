package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.twofactor.TwoFactorService
import com.github.yonaprojects.yona.domain.user.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.rememberme.InvalidCookieException
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices
import org.springframework.web.filter.OncePerRequestFilter

// 2FA는 폼 로그인 성공 핸들러(YonaAuthenticationSuccessHandler)에서만 걸린다. HTTP Basic과 remember-me 쿠키는 그 핸들러를 거치지 않고도
// 완전 인증을 만들 수 있어, 비밀번호만 아는 공격자가 TOTP 없이 들어올 수 있었다. 아래 두 클래스가 그 우회로를 막는다.

/**
 * 웹 화면에서 HTTP Basic으로 인증된 요청이 2FA 등록 계정이면 거부한다(401). Git/SVN/Hg(/git, /svn, /hg)는 전용 필터가 Basic 비밀번호를
 * 직접 처리하는 기존 설계(v1.6과 동일)를 유지하므로 제외한다. PAT는 Bearer/Yona-Token 헤더라 영향이 없다.
 */
class TwoFactorBasicGuardFilter(
    private val twoFactorService: TwoFactorService,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Basic ", ignoreCase = true) && !isVcsPath(request.requestURI.removePrefix(request.contextPath))) {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication != null && authentication.isAuthenticated &&
                authentication !is Pre2faAuthenticationToken && authentication !is AnonymousAuthenticationToken
            ) {
                val user = userRepository.findByLoginId(authentication.name).orElse(null)
                if (user != null && twoFactorService.isTwoFactorEnabled(user)) {
                    SecurityContextHolder.clearContext()
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = "application/json;charset=UTF-8"
                    response.writer.write("""{"message":"Two-factor authentication is enabled for this account. Sign in through the login form or use a personal access token."}""")
                    return
                }
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun isVcsPath(path: String): Boolean =
        path.startsWith("/git/") || path.startsWith("/svn/") || path.startsWith("/hg/")
}

/**
 * 2FA 등록 계정에는 remember-me 쿠키를 발급하지 않고, 이미 발급된 쿠키로는 자동 로그인하지 않는다.
 * AbstractAuthenticationProcessingFilter는 rememberMeServices.loginSuccess()를 성공 핸들러보다 먼저 호출하므로, 막지 않으면 비밀번호 단계에서
 * 이미 영속 쿠키가 응답에 실리고 그 쿠키만으로 2FA 없이 완전 인증이 복원된다.
 */
class TwoFactorAwareRememberMeServices(
    key: String,
    userDetailsService: UserDetailsService,
    private val twoFactorService: TwoFactorService,
    private val userRepository: UserRepository
) : TokenBasedRememberMeServices(key, userDetailsService) {

    override fun onLoginSuccess(request: HttpServletRequest, response: HttpServletResponse, successfulAuthentication: Authentication) {
        if (isTwoFactorAccount(successfulAuthentication.name)) return
        super.onLoginSuccess(request, response, successfulAuthentication)
    }

    override fun processAutoLoginCookie(cookieTokens: Array<String>, request: HttpServletRequest, response: HttpServletResponse): UserDetails {
        val userDetails = super.processAutoLoginCookie(cookieTokens, request, response)
        if (isTwoFactorAccount(userDetails.username)) {
            throw InvalidCookieException("remember-me is not allowed for two-factor accounts")
        }
        return userDetails
    }

    private fun isTwoFactorAccount(loginId: String): Boolean =
        userRepository.findByLoginId(loginId).map { twoFactorService.isTwoFactorEnabled(it) }.orElse(false)
}
