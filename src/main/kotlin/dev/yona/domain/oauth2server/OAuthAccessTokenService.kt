package dev.yona.domain.oauth2server

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.isLockedOut
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 유효한 OAuth 액세스 토큰이 알려 주는 것이다. [scopes]는 승인한 범위이고 사용자의 실제 권한은 각 기능이 따로 확인한다. */
data class OAuthGrant(val user: User, val scopes: Set<String>, val authorizationId: String, val clientId: String, val clientName: String, val clientRowId: Long, val resources: Set<String> = emptySet(), val oidcScopes: Set<String> = emptySet()) {

    /**
     * 토큰이 [resource](보호 자원의 주소)에서 쓸 수 있는지이다(RFC 8707, X-6, `decisions.md` D-72 6번). 승인 요청에 `resource`가 없었으면 외부 API(`/-_-api/v1`)에서만 쓸 수 있고,
     * 있었으면 그 자원들에서만 쓸 수 있다.
     */
    fun allows(resource: String, default: Boolean): Boolean = if (resources.isEmpty()) default else resource in resources
}

/**
 * OAuth 액세스 토큰을 자원(외부 API) 쪽에서 확인한다(추가 개발 항목 X-1, `decisions.md` D-71). 개인용 토큰([dev.yona.domain.apitoken.ApiTokenService])과 같은 위치에서 쓰며, 해시가 맞고
 * 만료되지 않았고 취소(무효화)되지 않았고 앱과 사용자가 있으며 사용자가 잠기거나 삭제되지 않았을 때만 돌려준다. OAuth 앱에 주지 않는 범위([OAuthScopes.BLOCKED])는 저장된 값에 있어도 뺀다.
 */
@Service
class OAuthAccessTokenService(
    private val authorizations: OAuthAuthorizationRepository,
    private val clients: OAuthClientRepository,
    private val users: UserRepository
) {

    @Transactional(readOnly = true)
    fun authenticate(value: String, now: Instant = Instant.now()): OAuthGrant? {
        if (!value.startsWith(PREFIX)) return null
        val authorization = authorizations.findByAccessTokenHash(TokenHasher.sha256Hex(value)) ?: return null
        if (authorization.accessTokenExpiresDate?.toInstant()?.isAfter(now) != true) return null
        if (INVALIDATED.containsMatchIn(authorization.accessTokenMetadata.orEmpty())) return null
        val client = clients.findById(authorization.clientRowId ?: return null).orElse(null) ?: return null
        val user = users.findById(authorization.userId ?: return null).orElse(null) ?: return null
        if (user.state.isLockedOut()) return null
        val scopes = ApiScopes.parseOrEmpty(authorization.accessTokenScopes) - OAuthScopes.BLOCKED
        return OAuthGrant(user, scopes, requireNotNull(authorization.id), requireNotNull(client.clientId), client.name.orEmpty(), requireNotNull(client.id), resourcesOf(authorization.attributes),
            authorization.accessTokenScopes.orEmpty().split(',').map { it.trim() }.filter { it in OAuthScopes.OIDC }.toSet())
    }

    /** 승인 요청에 실린 `resource` 값들이다. 저장된 속성(승인 요청의 추가 매개변수)에서 읽는다. 없거나 읽을 수 없으면 빈 집합이다. */
    private fun resourcesOf(attributes: String?): Set<String> {
        if (attributes.isNullOrBlank()) return emptySet()
        val node = runCatching { MAPPER.readTree(attributes).findValue("resource") }.getOrNull() ?: return emptySet()
        val values = if (node.isArray) node.toList().map { it.asString("") } else listOf(node.asString(""))
        return values.flatMap { it.split(' ') }.filter { it.isNotBlank() }.toSet()
    }

    companion object {
        private val MAPPER = tools.jackson.databind.json.JsonMapper.builder().build()

        /** OAuth 액세스 토큰의 접두어이다. */
        const val PREFIX = "yona_oat_"
        private val INVALIDATED = Regex("\"metadata\\.token\\.invalidated\"\\s*:\\s*true")
    }
}
