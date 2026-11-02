package dev.yona.mcp

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.oauth2server.OAuthAccessTokenService
import dev.yona.domain.site.SiteSettingService
import dev.yona.security.ApiTokenHeaders
import dev.yona.security.TokenFailureLimiter
import dev.yona.security.YonaAuthentication
import dev.yona.web.user.WebAuthnContexts
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * MCP 서버(`/mcp`)의 접근 규칙을 적용한다(추가 개발 항목 X-6, `decisions.md` D-72). 이 경로는 세션과 쿠키를 보지 않고 **OAuth 액세스 토큰(`yona_oat_`)만** 받는다.
 * 개인용 토큰은 받지 않는다. 확인 순서는 다음과 같다.
 *
 * 1. 관리자가 MCP를 껐으면 404이다.
 * 2. `Origin` 머리글이 있으면 이 서버의 출처와 같아야 한다. 다르면 403이다(MCP 규격이 DNS 재바인딩 방지로 요구한다). 머리글이 없는 요청(브라우저가 아닌 클라이언트)은 받는다.
 * 3. `MCP-Protocol-Version` 머리글이 있으면 지원하는 버전이어야 한다. 아니면 400이다. 없으면 규격대로 2025-03-26으로 본다.
 * 4. 토큰이 없거나 틀리면 401이고 `WWW-Authenticate: Bearer resource_metadata="..."`로 보호 자원 메타데이터(RFC 9728)를 알린다.
 *    연달아 실패한 주소는 외부 API와 같이 429로 막는다.
 * 5. 토큰에 읽기 범위(`read:repo`, `repo`는 포함한다)가 없으면 403이고 `error="insufficient_scope"`로 필요한 범위를 알린다.
 */
class McpAccessFilter(
    private val settings: SiteSettingService,
    private val oauthTokens: OAuthAccessTokenService,
    private val failures: TokenFailureLimiter,
    private val audit: dev.yona.domain.audit.AuditBoundary
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !isMcpPath(request)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        // 세션이나 remember-me 쿠키로 채워진 인증 정보는 쓰지 않는다
        SecurityContextHolder.clearContext()
        if (!settings.mcpEnabled()) return fail(response, 404, "not found")
        val origin = request.getHeader("Origin")
        if (origin != null && origin != WebAuthnContexts.of(request).origin) return fail(response, 403, "the origin is not allowed")
        val version = request.getHeader(VERSION_HEADER)
        if (version != null && version !in SUPPORTED_VERSIONS) return fail(response, 400, "unsupported MCP protocol version")

        val metadata = WebAuthnContexts.of(request).origin + request.contextPath.orEmpty() + METADATA_PATH
        val challenge = "Bearer resource_metadata=\"$metadata\""
        val address = request.remoteAddr.orEmpty()
        if (failures.isBlocked(address)) {
            audit.blocked(address, "mcp")
            return fail(response, 429, "too many failed token attempts")
        }
        val value = ApiTokenHeaders.extract(request)
        val grant = value?.takeIf { it.startsWith(OAuthAccessTokenService.PREFIX) }?.let { oauthTokens.authenticate(it) }
        if (grant == null) {
            if (value != null) { failures.recordFailure(address); audit.tokenFailure("mcp") }
            return fail(response, 401, "unauthorized request", challenge)
        }
        failures.recordSuccess(address)
        if (!ApiScopes.implies(grant.scopes, ApiScopes.READ_REPO)) {
            return fail(response, 403, "the token scope does not allow this request", "$challenge, error=\"insufficient_scope\", scope=\"${ApiScopes.READ_REPO}\"")
        }
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = YonaAuthentication.of(grant.user, viaToken = true, scopes = grant.scopes)
        SecurityContextHolder.setContext(context)
        chain.doFilter(request, response)
    }

    private fun fail(response: HttpServletResponse, status: Int, message: String, challenge: String? = null) {
        response.status = status
        challenge?.let { response.setHeader("WWW-Authenticate", it) }
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write("{\"message\":\"$message\"}")
    }

    companion object {
        const val PATH = "/mcp"
        const val METADATA_PATH = "/.well-known/oauth-protected-resource/mcp"
        const val VERSION_HEADER = "MCP-Protocol-Version"

        /** 지원하는 MCP 프로토콜 버전이다(헤더가 없으면 첫 값으로 본다). */
        val SUPPORTED_VERSIONS = setOf("2025-03-26", "2025-06-18", "2025-11-25")

        /** 요청이 MCP 서버 경로(`/mcp` 자신과 그 아래)인지이다. */
        fun isMcpPath(request: HttpServletRequest): Boolean {
            val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
            return path == PATH || path.startsWith("$PATH/")
        }
    }
}
