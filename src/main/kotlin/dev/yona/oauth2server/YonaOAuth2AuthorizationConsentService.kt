package dev.yona.oauth2server

import dev.yona.domain.oauth2server.OAuthConsent
import dev.yona.domain.oauth2server.OAuthConsentRepository
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 사용자가 앱에 승인한 범위의 저장소이다(추가 개발 항목 X-1, `decisions.md` D-71). 앱이 이미 허용된 범위만 다시 요청하면 승인 화면을 건너뛰는 데 쓴다.
 * 소유자 이름은 사용자 번호의 글이고 앱 식별자는 앱의 행 번호의 글이다.
 */
@Component
class YonaOAuth2AuthorizationConsentService(
    private val consents: OAuthConsentRepository,
    private val users: dev.yona.domain.user.UserRepository,
    private val clients: dev.yona.domain.oauth2server.OAuthClientRepository,
    private val audit: dev.yona.domain.audit.AuditRecorder
) : OAuth2AuthorizationConsentService {

    @Transactional
    override fun save(authorizationConsent: OAuth2AuthorizationConsent) {
        val clientRowId = authorizationConsent.registeredClientId.toLong(); val userId = authorizationConsent.principalName.toLong()
        val entity = consents.findByClientRowIdAndUserId(clientRowId, userId) ?: OAuthConsent(clientRowId = clientRowId, userId = userId, createdDate = Date())
        entity.scopes = authorizationConsent.scopes.joinToString(",")
        consents.save(entity)
        val user = users.findById(userId).orElse(null)
        audit.record(
            dev.yona.domain.audit.AuditEvent(
                "oauth.authorized", targetType = "oauth_client", targetId = clientRowId.toString(), targetLabel = clients.findById(clientRowId).orElse(null)?.clientId,
                details = mapOf("scopes" to authorizationConsent.scopes.toList().sorted()),
                actor = user?.let { dev.yona.domain.audit.AuditActor(dev.yona.domain.audit.AuditActorType.USER, it.id, it.loginId) }
            )
        )
    }

    @Transactional
    override fun remove(authorizationConsent: OAuth2AuthorizationConsent) {
        consents.deleteByUserAndClient(authorizationConsent.principalName.toLong(), authorizationConsent.registeredClientId.toLong())
    }

    @Transactional(readOnly = true)
    override fun findById(registeredClientId: String, principalName: String): OAuth2AuthorizationConsent? {
        val entity = consents.findByClientRowIdAndUserId(registeredClientId.toLongOrNull() ?: return null, principalName.toLongOrNull() ?: return null) ?: return null
        val builder = OAuth2AuthorizationConsent.withId(registeredClientId, principalName)
        entity.scopes.orEmpty().split(',').filter { it.isNotEmpty() }.forEach { builder.scope(it) }
        return builder.build()
    }
}
