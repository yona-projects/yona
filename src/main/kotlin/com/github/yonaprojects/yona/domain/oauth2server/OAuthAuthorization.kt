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

    // yona-wiki P3-07 Step6(회귀 수정, 2026-09-06) — @Lob 단독으로는 JPA의 @Column.length 기본값
    // (255)이 그대로 적용돼, Hibernate가 MariaDB/MySQL 방언에서 이 길이를 기준으로 LOB 하위 타입을
    // 고른다(<=255 -> TINYTEXT). attributes 컬럼은 OAuth2AuthorizationRequest 전체(추가 파라미터
    // 포함)를 JSON으로 담아 255바이트를 쉽게 넘기는데, 실제 MCP 클라이언트 E2E 통합테스트
    // (McpToolsEndToEndSpec)로 처음 발견됐다(DataIntegrityViolationException: Data too long for
    // column 'attributes') — 기존 MockMvc 전용 테스트(McpOAuth2SecurityIntegrationSpec)는 스코프
    // 2개짜리 짧은 값만 써서 우연히 한계 밑에 머물러 있었을 뿐이다. PullRequest.body 등 기존
    // 관례(별도 @Lob 없이 큰 length만 지정)와 동일하게 넉넉한 length를 명시해 MEDIUMTEXT/LONGTEXT급
    // 하위 타입을 고르도록 고정한다 — 6개 DB(MariaDB/PostgreSQL/MySQL/SQL Server/CUBRID/H2) 전부
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
    var refreshTokenMetadata: String? = null
)
