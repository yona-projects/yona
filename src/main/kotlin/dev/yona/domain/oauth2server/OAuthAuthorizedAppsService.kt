package dev.yona.domain.oauth2server

import dev.yona.domain.audit.AuditActor
import dev.yona.domain.audit.AuditActorType
import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.util.Date

/**
 * 사용자가 승인한 앱 한 개이다(설정의 "승인한 앱" 목록). [ownerLoginId]는 앱을 만든 사용자이고 CIMD 앱은 없다. [host]는 CIMD 앱이면 클라이언트 ID의 호스트이다.
 */
data class AuthorizedApp(
    val clientRowId: Long, val clientId: String, val name: String, val ownerLoginId: String?, val host: String?, val scopes: List<String>, val approvedDate: Date?, val activeTokens: Boolean
)

/**
 * 사용자가 승인한 앱을 보고 취소한다(추가 개발 항목 X-1, `decisions.md` D-71). 취소하면 그 앱에 발급한 모든 토큰이 즉시 쓸 수 없게 되고(인가를 지운다) 저장한 승인(범위)도 지워져
 * 다음 요청 때 승인 화면이 다시 나온다.
 */
@Service
class OAuthAuthorizedAppsService(
    private val consents: OAuthConsentRepository,
    private val authorizations: OAuthAuthorizationRepository,
    private val clients: OAuthClientRepository,
    private val users: UserRepository,
    private val audit: AuditRecorder
) {

    /** 사용자가 승인한 앱을 최근에 승인한 순서로 돌려준다. 지워진 앱의 승인은 건너뛴다. */
    @Transactional(readOnly = true)
    fun list(user: User): List<AuthorizedApp> {
        val userId = requireNotNull(user.id)
        val tokenOwners = authorizations.findByUserIdOrderByCreatedDateDescIdDesc(userId).filter { it.accessTokenHash != null }.mapNotNull { it.clientRowId }.toSet()
        return consents.findByUserIdOrderByCreatedDateDescIdDesc(userId).mapNotNull { consent ->
            val client = consent.clientRowId?.let { clients.findById(it).orElse(null) } ?: return@mapNotNull null
            AuthorizedApp(
                requireNotNull(client.id), requireNotNull(client.clientId), client.name.orEmpty(), client.ownerId?.takeIf { client.ownerType == OAuthClient.USER }?.let { users.findById(it).orElse(null)?.loginId },
                if (client.registration == OAuthClient.CIMD) runCatching { URI(client.clientId.orEmpty()).host }.getOrNull() else null,
                consent.scopes.orEmpty().split(',').filter { it.isNotEmpty() }, consent.createdDate, client.id in tokenOwners
            )
        }
    }

    /** 사용자의 승인을 취소한다. 승인한 적이 없으면 `false`이다. */
    @Transactional
    fun revoke(user: User, clientRowId: Long): Boolean {
        val userId = requireNotNull(user.id)
        val consentRemoved = consents.deleteByUserAndClient(userId, clientRowId)
        val authorizationsRemoved = authorizations.deleteByUserAndClient(userId, clientRowId)
        if (consentRemoved == 0 && authorizationsRemoved == 0) return false
        audit.record(
            AuditEvent(
                "oauth.authorization_revoked", targetType = "oauth_client", targetId = clientRowId.toString(), targetLabel = clients.findById(clientRowId).orElse(null)?.clientId,
                actor = AuditActor(AuditActorType.USER, user.id, user.loginId)
            )
        )
        return true
    }
}
