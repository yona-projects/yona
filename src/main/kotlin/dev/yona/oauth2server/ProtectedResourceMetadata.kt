package dev.yona.oauth2server

import dev.yona.domain.oauth2server.OAuthScopes
import dev.yona.web.user.WebAuthnContexts
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 보호 자원 메타데이터(RFC 9728)이다(추가 개발 항목 X-1, `decisions.md` D-71). 외부 API(`/-_-api/v1`)가 어느 인가 서버의 토큰을 받는지와 지원하는 범위를 알린다.
 * 클라이언트는 401의 `WWW-Authenticate`가 가리키는 이 주소를 읽고 인가 서버를 찾는다. OAuth 앱에 주지 않는 범위는 알리지 않는다.
 */
@Controller
class ProtectedResourceMetadataController {

    @GetMapping("/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/-_-api/v1", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun metadata(request: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        val origin = WebAuthnContexts.of(request).origin + request.contextPath.orEmpty()
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
            linkedMapOf(
                "resource" to "$origin/-_-api/v1", "authorization_servers" to listOf(origin), "scopes_supported" to OAuthScopes.grantable(), "bearer_methods_supported" to listOf("header"),
                "resource_name" to "Yona"
            )
        )
    }
}

/**
 * 외부 API(`/-_-api`)의 401 응답에 `WWW-Authenticate: Bearer resource_metadata="..."`를 더한다(RFC 9728). OAuth 클라이언트가 이 머리글로 보호 자원 메타데이터를 찾는다.
 * 컨트롤러가 이미 `WWW-Authenticate`를 정했으면 건드리지 않는다.
 */
class ProtectedResourceChallengeFilter : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.removePrefix(request.contextPath.orEmpty()).startsWith("/-_-api")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val origin = WebAuthnContexts.of(request).origin + request.contextPath.orEmpty()
        val challenge = "Bearer resource_metadata=\"$origin/.well-known/oauth-protected-resource/-_-api/v1\""
        chain.doFilter(request, object : HttpServletResponseWrapper(response) {
            private fun addChallenge(status: Int) { if (status == 401 && !containsHeader("WWW-Authenticate")) setHeader("WWW-Authenticate", challenge) }
            override fun setStatus(sc: Int) { addChallenge(sc); super.setStatus(sc) }
            override fun sendError(sc: Int) { addChallenge(sc); super.sendError(sc) }
            override fun sendError(sc: Int, msg: String?) { addChallenge(sc); super.sendError(sc, msg) }
        })
    }
}
