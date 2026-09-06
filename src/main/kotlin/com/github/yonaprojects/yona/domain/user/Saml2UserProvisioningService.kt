package com.github.yonaprojects.yona.domain.user

import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * yona-wiki P3-06(엔터프라이즈 SSO) Step3 — `LdapUserProvisioningService`/`OidcUserProvisioningService`와
 * 동일한 JIT 프로비저닝을 SAML2 어서션 속성에 대해 수행한다. 이메일 속성 이름은 IdP마다 다를 수 있어
 * (Okta/Azure AD/Keycloak 등) 설정 가능한 attribute 이름을 인자로 받는다(`SsoSettingsService` 참고).
 */
@Service
class Saml2UserProvisioningService(
    private val userRepository: UserRepository
) {
    @Transactional
    fun reconcile(
        principal: Saml2AuthenticatedPrincipal,
        emailAttributeName: String,
        displayNameAttributeName: String
    ): User {
        val email = principal.getFirstAttribute<String>(emailAttributeName)
            ?: principal.name.takeIf { it.contains("@") }
            ?: throw IllegalStateException(
                "SAML 어서션에 이메일 속성($emailAttributeName)이 없고 NameID도 이메일 형식이 아닙니다."
            )

        val displayName = principal.getFirstAttribute<String>(displayNameAttributeName)
            ?: email.substringBefore("@")

        val existing = userRepository.findByEmail(email).orElse(null)
        return if (existing == null) {
            createNewUser(email, displayName)
        } else {
            syncExistingUser(existing, displayName)
        }
    }

    private fun createNewUser(email: String, displayName: String): User {
        val loginId = email.substringBefore("@")
        val user = User(
            loginId = loginId,
            name = displayName,
            email = email,
            state = UserState.ACTIVE,
            createdDate = Instant.now()
        )
        return userRepository.save(user)
    }

    private fun syncExistingUser(user: User, displayName: String): User {
        user.name = displayName
        return userRepository.save(user)
    }
}
