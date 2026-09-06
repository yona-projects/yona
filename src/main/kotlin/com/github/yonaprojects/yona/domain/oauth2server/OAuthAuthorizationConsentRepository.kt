package com.github.yonaprojects.yona.domain.oauth2server

import org.springframework.data.jpa.repository.JpaRepository

interface OAuthAuthorizationConsentRepository : JpaRepository<OAuthAuthorizationConsent, String> {
    fun findByPrincipalName(principalName: String): List<OAuthAuthorizationConsent>
    fun deleteByRegisteredClientIdAndPrincipalName(registeredClientId: String, principalName: String)
}
