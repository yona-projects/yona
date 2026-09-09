package com.github.yonaprojects.yona.domain.oauth2server

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant

// Spring Authorization Server의 OAuth2Authorization(인가 코드/액세스 토큰/리프레시 토큰의 발급
// 상태)을 저장하는 JPA 엔티티. 공식 스키마(oauth2_authorization, JdbcOAuth2AuthorizationService)를
// 그대로 베낀 컬럼 구성이다(직접 소스 확인). 초기엔 oauth2_authorization_code/access_token/
// refresh_token만 쓰고 oidc_id_token/user_code/device_code는 쓰지 않아(OIDC 로그인·디바이스
// 플로우 미사용) 제외했었지만, OIDC 활성화부터 oidc_id_token 4컬럼(아래 oidcIdToken* 필드, 공식
// 스키마와 동일 구성)을 다시 채워넣었다 — user_code/device_code는 여전히 미사용(디바이스 플로우
// 없음). JdbcOAuth2AuthorizationService와 동일하게 attributes/각 토큰의 metadata는 Jackson으로
// 직렬화한 JSON 문자열로 저장한다(JpaOAuth2AuthorizationService 참고, SecurityJackson2Modules +
// OAuth2AuthorizationServerJackson2Module 재사용 — Spring 공식 모듈, 직접 직렬화 코드를 새로
// 짜지 않음).
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

    // @Lob 단독으로는 JPA의 @Column.length 기본값(255)이 그대로 적용돼, Hibernate가 MariaDB/MySQL
    // 방언에서 이 길이를 기준으로 LOB 하위 타입을 고른다(<=255 -> TINYTEXT). attributes 컬럼은
    // OAuth2AuthorizationRequest 전체(추가 파라미터 포함)를 JSON으로 담아 255바이트를 쉽게
    // 넘기는데, 실제 MCP 클라이언트 E2E 통합테스트에서 DataIntegrityViolationException(Data too
    // long for column 'attributes')으로 처음 발견됐다 — 기존 MockMvc 전용 테스트는 스코프 2개짜리
    // 짧은 값만 써서 우연히 한계 밑에 머물러 있었을 뿐이다. PullRequest.body 등 기존 관례(별도
    // @Lob 없이 큰 length만 지정)와 동일하게 넉넉한 length를 명시해 MEDIUMTEXT/LONGTEXT급 하위
    // 타입을 고르도록 고정한다 — 6개 DB(MariaDB/PostgreSQL/MySQL/SQL Server/CUBRID/H2) 전부
    // length 속성 자체는 이식성 있게 지원되므로 방언별 분기 없이 그대로 적용 가능하다.
    @Lob
    @Column(name = "authorized_scopes", length = 1_000_000)
    var authorizedScopes: String? = null,

    @Lob
    @Column(name = "attributes", length = 1_000_000)
    var attributes: String? = null,

    @Column(name = "state", length = 500)
    var state: String? = null,

    @Lob
    @Column(name = "authorization_code_value", length = 1_000_000)
    var authorizationCodeValue: String? = null,
    var authorizationCodeIssuedAt: Instant? = null,
    var authorizationCodeExpiresAt: Instant? = null,
    @Lob
    @Column(name = "authorization_code_metadata", length = 1_000_000)
    var authorizationCodeMetadata: String? = null,

    @Lob
    @Column(name = "access_token_value", length = 1_000_000)
    var accessTokenValue: String? = null,
    var accessTokenIssuedAt: Instant? = null,
    var accessTokenExpiresAt: Instant? = null,
    @Lob
    @Column(name = "access_token_metadata", length = 1_000_000)
    var accessTokenMetadata: String? = null,
    @Column(name = "access_token_type", length = 50)
    var accessTokenType: String? = null,
    @Lob
    @Column(name = "access_token_scopes", length = 1_000_000)
    var accessTokenScopes: String? = null,

    @Lob
    @Column(name = "refresh_token_value", length = 1_000_000)
    var refreshTokenValue: String? = null,
    var refreshTokenIssuedAt: Instant? = null,
    var refreshTokenExpiresAt: Instant? = null,
    @Lob
    @Column(name = "refresh_token_metadata", length = 1_000_000)
    var refreshTokenMetadata: String? = null,

    // 위 주석("oidc_id_token ... 컬럼은 제외했다")은 OIDC 미사용 시점엔 맞는 말이었지만, OIDC를
    // 켜는 순간부터는 진짜 gap이 된다: Spring Authorization Server는 OidcIdToken을 다른 토큰들과
    // 완전히 동일한 범용 메커니즘
    // (OAuth2Authorization.Builder.token(idToken) { metadata -> metadata[CLAIMS_METADATA_NAME] =
    // idToken.claims })으로 OAuth2Authorization에 담아 저장을 요청한다(공식 소스
    // OAuth2AuthorizationCodeAuthenticationProvider 확인) — 이 엔티티가 이 토큰 타입만 저장할 컬럼이
    // 없으면 발급 자체는 성공해도 재저장 시 ID 토큰이 조용히 사라지고, 이후 `/userinfo` 호출이
    // `authorization.getToken(OidcIdToken::class.java)`를 못 찾아 매번 invalid_token으로 실패한다
    // (JdbcOAuth2AuthorizationService 공식 구현의 oidc_id_token_value/issued_at/expires_at/metadata
    // 4컬럼 구성을 그대로 따른다 — claims는 metadata 맵 안에 CLAIMS_METADATA_NAME 키로 이미
    // 포함되어 있어 별도 컬럼이 필요 없다).
    @Lob
    @Column(name = "oidc_id_token_value", length = 1_000_000)
    var oidcIdTokenValue: String? = null,
    var oidcIdTokenIssuedAt: Instant? = null,
    var oidcIdTokenExpiresAt: Instant? = null,
    @Lob
    @Column(name = "oidc_id_token_metadata", length = 1_000_000)
    var oidcIdTokenMetadata: String? = null
)
