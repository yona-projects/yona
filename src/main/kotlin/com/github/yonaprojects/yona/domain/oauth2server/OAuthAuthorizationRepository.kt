package com.github.yonaprojects.yona.domain.oauth2server

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OAuthAuthorizationRepository : JpaRepository<OAuthAuthorization, String> {
    fun findByState(state: String): Optional<OAuthAuthorization>
    fun findByAuthorizationCodeValue(value: String): Optional<OAuthAuthorization>
    fun findByAccessTokenValue(value: String): Optional<OAuthAuthorization>
    fun findByRefreshTokenValue(value: String): Optional<OAuthAuthorization>

    // OAuth2AuthorizationService.findByToken(token, tokenType=null)에 대응
    // (JdbcOAuth2AuthorizationService의 UNKNOWN_TOKEN_TYPE_FILTER와 동일한 의미 — 4개 토큰 컬럼 중
    // 어디에 있는지 모를 때 전부 검색).
    fun findByStateOrAuthorizationCodeValueOrAccessTokenValueOrRefreshTokenValue(
        state: String,
        authorizationCodeValue: String,
        accessTokenValue: String,
        refreshTokenValue: String
    ): Optional<OAuthAuthorization>

    // "Authorized OAuth Apps" 화면에서 사용자가 앱 접근을 취소(revoke)할 때
    // 쓴다. 동의(OAuthAuthorizationConsent) 레코드만 지우면 다음 로그인 때 동의 화면이 다시 뜰
    // 뿐, 이미 발급된 액세스/리프레시 토큰은 만료 전까지 계속 유효하게 남는다(GitHub의 "Revoke
    // access"가 즉시 토큰을 무효화하는 것과 다른 동작) — 이 메서드로 해당 클라이언트+사용자의
    // OAuth2Authorization(토큰 보관 레코드) 자체를 함께 지워 즉시 무효화한다.
    fun deleteByRegisteredClientIdAndPrincipalName(registeredClientId: String, principalName: String)

    // OAuthAppsAdminController가 클라이언트 자체를 삭제할 때, 그 클라이언트로
    // 발급된 모든 사용자의 토큰 레코드를 한 번에 무효화한다.
    fun deleteByRegisteredClientId(registeredClientId: String)
}
