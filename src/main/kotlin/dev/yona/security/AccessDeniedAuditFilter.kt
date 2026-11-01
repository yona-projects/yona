package dev.yona.security

import dev.yona.domain.audit.AuditBoundary
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 응답이 403이었던 요청을 감사 로그에 `access.denied`로 남긴다(추가 개발 항목 X-9 A3, `decisions.md` D-62). 인증 필터 뒤에 두어 행위자(로그인 사용자나 토큰)가 이미 정해져 있다.
 * 구체적인 거부 이벤트(`access.api_scope_denied`)를 이미 남긴 요청은 건너뛴다. 요청 경로만 남기고 질의 문자열은 남기지 않는다.
 */
class AccessDeniedAuditFilter(private val boundary: AuditBoundary) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        chain.doFilter(request, response)
        if (response.status != 403 || request.getAttribute(AuditBoundary.RECORDED_ATTRIBUTE) != null) return
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
        boundary.accessDenied(request.method, path, response.status, if (path.startsWith("/-_-api")) "api" else "web")
    }
}
