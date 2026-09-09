package com.github.yonaprojects.yona.domain.oauth2server

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// "Authorized OAuth Apps" 화면(GitHub의 "Settings > Applications >
// Authorized OAuth Apps"와 동등한 기능)의 데이터 소스. 사용자가 자신이 인가한 OAuth 클라이언트를
// 조회하고 취소(revoke)할 수 있게 한다 — 백엔드(OAuth2 인가 서버)만 만들고 실제로 관리할 방법이
// 없는 상태로 남기지 말라는 지시에 따른 필수 UI.
data class AuthorizedAppView(
    val clientId: String,
    val clientName: String,
    val scopes: List<String>,
    // RFC7591 DCR로 자동 등록된 클라이언트인지(Claude Code 등) 사전등록 클라이언트인지 구분 —
    // 화면에서 사용자가 "이게 뭔지" 판단하는 데 도움을 준다.
    val dynamicallyRegistered: Boolean
)

@Service
class OAuthAuthorizedAppsService(
    private val consentRepository: OAuthAuthorizationConsentRepository,
    private val clientRepository: OAuthRegisteredClientRepository,
    private val authorizationRepository: OAuthAuthorizationRepository
) {
    fun listAuthorizedApps(principalName: String): List<AuthorizedAppView> {
        return consentRepository.findByPrincipalName(principalName)
            .mapNotNull { consent ->
                val client = clientRepository.findById(consent.registeredClientId).orElse(null) ?: return@mapNotNull null
                AuthorizedAppView(
                    clientId = client.clientId,
                    clientName = client.clientName,
                    scopes = consent.authorities.split(",")
                        .filter { it.isNotBlank() }
                        .map { it.removePrefix("SCOPE_") },
                    dynamicallyRegistered = client.dynamicallyRegistered
                )
            }
            .sortedBy { it.clientName.lowercase() }
    }

    // 동의 레코드만 지우면 다음 로그인 때 동의 화면이 다시 뜰 뿐, 이미
    // 발급된 액세스/리프레시 토큰은 만료 전까지 계속 유효하다(OAuthAuthorizationRepository.
    // deleteByRegisteredClientIdAndPrincipalName() 주석 참고) — GitHub의 "Revoke access"처럼 즉시
    // 접근을 차단하려면 두 저장소 모두에서 지워야 한다.
    @Transactional
    fun revoke(principalName: String, clientId: String) {
        val client = clientRepository.findByClientId(clientId).orElse(null) ?: return
        consentRepository.deleteByRegisteredClientIdAndPrincipalName(client.id, principalName)
        authorizationRepository.deleteByRegisteredClientIdAndPrincipalName(client.id, principalName)
    }
}
