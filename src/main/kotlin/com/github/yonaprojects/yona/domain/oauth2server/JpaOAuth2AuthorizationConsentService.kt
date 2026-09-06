package com.github.yonaprojects.yona.domain.oauth2server

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

// yona-wiki P3-07(MCP 서버) Step2 — OAuth2AuthorizationConsentService의 JPA 기반 구현.
@Component
class JpaOAuth2AuthorizationConsentService(
    private val repository: OAuthAuthorizationConsentRepository
) : OAuth2AuthorizationConsentService {

    @Transactional
    override fun save(authorizationConsent: OAuth2AuthorizationConsent) {
        val entity = OAuthAuthorizationConsent(
            id = OAuthAuthorizationConsent.idOf(
                authorizationConsent.registeredClientId,
                authorizationConsent.principalName
            ),
            registeredClientId = authorizationConsent.registeredClientId,
            principalName = authorizationConsent.principalName,
            authorities = authorizationConsent.authorities.joinToString(",") { it.authority.orEmpty() }
        )
        repository.save(entity)
    }

    @Transactional
    override fun remove(authorizationConsent: OAuth2AuthorizationConsent) {
        repository.deleteByRegisteredClientIdAndPrincipalName(
            authorizationConsent.registeredClientId,
            authorizationConsent.principalName
        )
    }

    override fun findById(registeredClientId: String, principalName: String): OAuth2AuthorizationConsent? {
        val entity = repository.findById(OAuthAuthorizationConsent.idOf(registeredClientId, principalName))
            .orElse(null) ?: return null
        return toConsent(entity)
    }

    companion object {
        fun toConsent(entity: OAuthAuthorizationConsent): OAuth2AuthorizationConsent {
            val builder = OAuth2AuthorizationConsent.withId(entity.registeredClientId, entity.principalName)
            entity.authorities.split(",").filter { it.isNotBlank() }.forEach {
                builder.authority(SimpleGrantedAuthority(it))
            }
            return builder.build()
        }
    }
}
