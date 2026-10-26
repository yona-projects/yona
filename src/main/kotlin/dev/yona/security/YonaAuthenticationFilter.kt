package dev.yona.security

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청을 보낸 사용자를 찾아 보안 컨텍스트에 채우는 필터이다. v1.6 `UserApp.currentUser()`의 확인 순서와 같다.
 *
 * 1. 세션: Spring Security가 세션에서 복원한 인증 정보. 사용자가 사라졌거나 LOCKED이면 무효로 보고 비운다.
 * 2. API 토큰: `Authorization: token <값>` 또는 `Yona-Token` 헤더. 세션에는 기록하지 않는다.
 * 3. remember-me 쿠키 `yobi.token`(`로그인ID:해시된패스워드`). 성공하면 세션에도 기록한다.
 *    값이 잘못되었거나 잠긴 계정이면 쿠키를 지운다.
 *
 * 쿠키에 해시된 패스워드가 그대로 들어가는 것은 v1.6의 설계이며 그대로 따른다([refactoring-backlog.md] R-05).
 */
class YonaAuthenticationFilter(private val users: UserRepository) : OncePerRequestFilter() {

    private val securityContextRepository = HttpSessionSecurityContextRepository()

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (!hasValidSession()) {
            authenticateByToken(request) || authenticateByRememberMeCookie(request, response)
        }
        chain.doFilter(request, response)
    }

    /** 세션 인증 정보가 아직 유효한지 확인한다. 유효하지 않으면 보안 컨텍스트를 비운다. */
    private fun hasValidSession(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication as? YonaAuthentication ?: return false
        val user = users.findById(authentication.principal.userId).orElse(null)
        if (user == null || user.state.isLockedOut()) {
            SecurityContextHolder.clearContext()
            return false
        }
        return true
    }

    private fun authenticateByToken(request: HttpServletRequest): Boolean {
        val token = extractToken(request) ?: return false
        val user = users.findByToken(token) ?: return false
        SecurityContextHolder.getContext().authentication = YonaAuthentication.of(user, viaToken = true)
        return true
    }

    private fun authenticateByRememberMeCookie(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val cookie = request.cookies?.firstOrNull { it.name == REMEMBER_ME_COOKIE } ?: return false
        val parts = cookie.value.split(TOKEN_SEPARATOR).filter { it.isNotEmpty() }
        val user = if (parts.size == TOKEN_LENGTH) users.findByLoginId(parts[0]) else null
        val valid = user != null && user.password == parts[1] && !user.state.isLockedOut()
        if (!valid) {
            discardRememberMeCookie(response)
            return false
        }
        val authentication = YonaAuthentication.of(requireNotNull(user))
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)
        return true
    }

    private fun extractToken(request: HttpServletRequest): String? {
        val authorization = request.getHeader("Authorization")
        if (authorization != null && authorization.contains("token ")) {
            return authorization.split("token ").getOrNull(1)
        }
        return request.getHeader(TOKEN_HEADER)
    }

    companion object {
        /** remember-me 쿠키 이름이다 (v1.6 `UserApp.TOKEN`). */
        const val REMEMBER_ME_COOKIE = "yobi.token"

        /** 쿠키 값의 구분자이다 (v1.6 `TOKEN_SEPARATOR`). */
        const val TOKEN_SEPARATOR = ":"
        private const val TOKEN_LENGTH = 2

        /** remember-me 쿠키의 유효 기간(초)이다. 30일이다 (v1.6 `MAX_AGE`). */
        const val REMEMBER_ME_MAX_AGE = 30 * 24 * 60 * 60

        /** 개인 API 토큰 헤더 이름이다 (v1.6 `USER_TOKEN_HEADER`). */
        const val TOKEN_HEADER = "Yona-Token"

        /** remember-me 쿠키를 즉시 만료시킨다. */
        fun discardRememberMeCookie(response: HttpServletResponse) {
            response.addCookie(Cookie(REMEMBER_ME_COOKIE, "").apply {
                maxAge = 0
                path = "/"
            })
        }
    }
}
