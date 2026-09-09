package com.github.yonaprojects.yona.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// SecurityContext에 Pre2faAuthenticationToken만 있는 동안(1차 비밀번호 인증은 통과했지만 2FA
// 검증 전) 2FA 검증 화면/로그아웃/정적 자원 외 모든 요청을 2FA 검증 화면으로 강제 리다이렉트한다.
// SecurityConfig.securityFilterChain()의 다른 커스텀 필터들과 동일하게
// BasicAuthenticationFilter 뒤에 addFilterAfter로 끼워 넣는다.
@Component
class Pre2faGateFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication is Pre2faAuthenticationToken) {
            val path = request.requestURI.removePrefix(request.contextPath)
            if (!isAllowedDuringPre2fa(path)) {
                response.sendRedirect("${request.contextPath}/users/login/2fa")
                return
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun isAllowedDuringPre2fa(path: String): Boolean =
        path.startsWith("/users/login/2fa") ||
            path == "/users/logout" || path == "/logout" ||
            path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/") ||
            path.startsWith("/stylesheets/") || path.startsWith("/javascripts/") ||
            path.startsWith("/bootstrap/") || path.startsWith("/assets/")
}
