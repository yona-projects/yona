package com.github.yonaprojects.yona.domain.oauth2server

import org.springframework.data.jpa.repository.JpaRepository

interface OAuthAuthorizationConsentRepository : JpaRepository<OAuthAuthorizationConsent, String> {
    fun findByPrincipalName(principalName: String): List<OAuthAuthorizationConsent>
    fun deleteByRegisteredClientIdAndPrincipalName(registeredClientId: String, principalName: String)

    // OAuthAppsAdminController가 클라이언트 자체를 삭제할 때, 그 클라이언트에 대한 모든 사용자의
    // 동의 레코드를 한 번에 정리한다(사용자별로 순회할 필요 없음).
    fun deleteByRegisteredClientId(registeredClientId: String)
}
