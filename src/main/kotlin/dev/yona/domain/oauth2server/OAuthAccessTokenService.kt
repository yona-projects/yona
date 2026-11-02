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
data class OAuthGrant(val user: User, val scopes: Set<String>, val authorizationId: String, val clientId: String, val clientName: String)

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
        return OAuthGrant(user, scopes, requireNotNull(authorization.id), requireNotNull(client.clientId), client.name.orEmpty())
    }

    companion object {
        /** OAuth 액세스 토큰의 접두어이다. */
        const val PREFIX = "yona_oat_"
        private val INVALIDATED = Regex("\"metadata\\.token\\.invalidated\"\\s*:\\s*true")
    }
}
