package com.github.yonaprojects.yona.config.oauth2server

import com.github.yonaprojects.yona.config.ApiTokenAuthenticationFilter
import com.github.yonaprojects.yona.domain.oauth2server.OAuthScopeAuthorizer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// yona-wiki P3-14 1라운드 — `/api/v1/**`에서 OAuth2 JWT로 인증된 요청에 대해 [[p3-02]]의
// Fine-grained 스코프 체계(ApiTokenScopeGroup/ApiTokenPermission)와 동일한 인가를 적용한다.
// PAT 인증(ApiTokenAuthenticationFilter)은 자기 경로에서 이미 스코프를 판정하므로, 이 필터는
// SecurityContext의 Authentication이 JwtAuthenticationToken일 때만(= PAT이 아니라 OAuth2로
// 인증된 경우만) 동작한다 — PAT 흐름에는 전혀 관여하지 않는다.
@Component
class OAuthApiScopeAuthorizationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication is JwtAuthenticationToken) {
            val requiredScope = ApiTokenAuthenticationFilter.resolveRequiredScope(request)
            if (requiredScope != null) {
                val grantedScopes = authentication.authorities
                    .mapNotNull { it.authority }
                    .filter { it.startsWith("SCOPE_") }
                    .map { it.removePrefix("SCOPE_") }
                    .toSet()

                val allowed = OAuthScopeAuthorizer.isAuthorized(
                    grantedScopes = grantedScopes,
                    resourceTypes = requiredScope.resourceTypes,
                    requiredPermission = requiredScope.requiredPermission
                )
                if (!allowed) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
                    return
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
