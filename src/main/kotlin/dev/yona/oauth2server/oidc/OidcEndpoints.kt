package dev.yona.oauth2server.oidc

import dev.yona.domain.oauth2server.OAuthAccessTokenService
import dev.yona.domain.oauth2server.OAuthScopes
import dev.yona.oauth2server.ProtectedResources
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseBody

/**
 * OIDC 제공자의 발견 문서와 사용자 정보 주소이다(추가 개발 항목 X-40, `decisions.md` D-77). 서명 키를 정하지 않아 OIDC가 꺼져 있으면 둘 다 404이다.
 *
 * 사용자 정보 주소는 `/oauth2/userinfo`이다. 로드맵에 적힌 `/userinfo`는 v1.6의 `/{사용자}` 경로와 겹쳐 `userinfo`라는 사용자의 프로필을 가리므로, 이미 예약한 `oauth2` 아래에 둔다.
 */
@Controller
class OidcEndpoints(private val settings: OidcSettings, private val tokens: OAuthAccessTokenService, private val users: dev.yona.domain.user.UserRepository, private val claims: OidcClaims) {

    @GetMapping("/.well-known/openid-configuration", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun discovery(request: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        if (!settings.enabled) return ResponseEntity.notFound().build()
        val base = ProtectedResources.base(request)
        return ResponseEntity.ok(linkedMapOf(
            "issuer" to base,
            "authorization_endpoint" to "$base/oauth2/authorize",
            "token_endpoint" to "$base/oauth2/token",
            "userinfo_endpoint" to "$base/oauth2/userinfo",
            "jwks_uri" to "$base/oauth2/jwks",
            "revocation_endpoint" to "$base/oauth2/revoke",
            "response_types_supported" to listOf("code"),
            "grant_types_supported" to listOf("authorization_code", "refresh_token"),
            "subject_types_supported" to listOf("public"),
            "id_token_signing_alg_values_supported" to listOf(OidcSettings.ALGORITHM.name),
            "scopes_supported" to OAuthScopes.OIDC + OAuthScopes.grantable(),
            "token_endpoint_auth_methods_supported" to listOf("client_secret_basic", "client_secret_post", "none"),
            "code_challenge_methods_supported" to listOf("S256"),
            "claims_supported" to OidcClaims.SUPPORTED
        ))
    }

    /** 액세스 토큰의 `openid` 범위가 정한 클레임을 돌려준다. 토큰이 없거나 틀리면 401, `openid` 범위가 없으면 403이다(RFC 6750). */
    @RequestMapping("/oauth2/userinfo", method = [RequestMethod.GET, RequestMethod.POST], produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun userInfo(request: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        if (!settings.enabled) return ResponseEntity.notFound().build()
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)?.trim().orEmpty()
        val value = Regex("^(?i:bearer)\\s+(\\S+)$").matchEntire(header)?.groupValues?.get(1)
            ?: return challenge(HttpStatus.UNAUTHORIZED, null)
        val grant = tokens.authenticate(value) ?: return challenge(HttpStatus.UNAUTHORIZED, "invalid_token")
        if ("openid" !in grant.oidcScopes) return challenge(HttpStatus.FORBIDDEN, "insufficient_scope", "scope=\"openid\"")
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.CACHE_CONTROL, "no-store").body(claims.of(grant.user, grant.oidcScopes, ProtectedResources.base(request)))
    }

    private fun challenge(status: HttpStatus, error: String?, extra: String? = null): ResponseEntity<Map<String, Any>> {
        val value = "Bearer" + listOfNotNull(error?.let { "error=\"$it\"" }, extra).joinToString(", ", prefix = if (error != null) " " else "")
        return ResponseEntity.status(status).header(HttpHeaders.WWW_AUTHENTICATE, value).header(HttpHeaders.CACHE_CONTROL, "no-store").body(emptyMap())
    }
}
