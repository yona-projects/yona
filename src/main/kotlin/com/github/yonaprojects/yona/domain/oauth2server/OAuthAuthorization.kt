package com.github.yonaprojects.yona.domain.oauth2server

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant

// yona-wiki P3-07(MCP 서버) Step2 — Spring Authorization Server의 OAuth2Authorization(인가 코드/
// 액세스 토큰/리프레시 토큰의 발급 상태)을 저장하는 JPA 엔티티. 공식 스키마(oauth2_authorization,
// JdbcOAuth2AuthorizationService)를 그대로 베낀 컬럼 구성이지만(직접 소스 확인 — 이 프로젝트가
// oauth2_authorization_code/access_token/refresh_token만 쓰고 oidc_id_token/user_code/device_code는
// 쓰지 않아(OIDC 로그인·디바이스 플로우 미사용) 해당 컬럼은 제외했다. JdbcOAuth2AuthorizationService와
// 동일하게 attributes/각 토큰의 metadata는 Jackson으로 직렬화한 JSON 문자열로 저장한다
// (JpaOAuth2AuthorizationService 참고, SecurityJackson2Modules + OAuth2AuthorizationServerJackson2Module
// 재사용 — Spring 공식 모듈, 직접 직렬화 코드를 새로 짜지 않음).
@Entity
@Table(name = "oauth_authorization")
class OAuthAuthorization(
    @Id
    @Column(length = 100)
    var id: String,

    @Column(name = "registered_client_id", nullable = false, length = 100)
    var registeredClientId: String,

    @Column(name = "principal_name", nullable = false, length = 200)
    var principalName: String,

    @Column(name = "authorization_grant_type", nullable = false, length = 100)
    var authorizationGrantType: String,

    @Lob
    @Column(name = "authorized_scopes")
    var authorizedScopes: String? = null,

    @Lob
    @Column(name = "attributes")
    var attributes: String? = null,

    @Column(name = "state", length = 500)
    var state: String? = null,

    @Lob
    @Column(name = "authorization_code_value")
    var authorizationCodeValue: String? = null,
    var authorizationCodeIssuedAt: Instant? = null,
    var authorizationCodeExpiresAt: Instant? = null,
    @Lob
    @Column(name = "authorization_code_metadata")
    var authorizationCodeMetadata: String? = null,

    @Lob
    @Column(name = "access_token_value")
    var accessTokenValue: String? = null,
    var accessTokenIssuedAt: Instant? = null,
    var accessTokenExpiresAt: Instant? = null,
    @Lob
    @Column(name = "access_token_metadata")
    var accessTokenMetadata: String? = null,
    @Column(name = "access_token_type", length = 50)
    var accessTokenType: String? = null,
    @Lob
    @Column(name = "access_token_scopes")
    var accessTokenScopes: String? = null,

    @Lob
    @Column(name = "refresh_token_value")
    var refreshTokenValue: String? = null,
    var refreshTokenIssuedAt: Instant? = null,
    var refreshTokenExpiresAt: Instant? = null,
    @Lob
    @Column(name = "refresh_token_metadata")
    var refreshTokenMetadata: String? = null
)
