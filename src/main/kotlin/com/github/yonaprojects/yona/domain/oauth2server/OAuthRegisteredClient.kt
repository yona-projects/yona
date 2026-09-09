package com.github.yonaprojects.yona.domain.oauth2server

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import java.time.Instant

// Spring Authorization Server의 RegisteredClient를 저장하는 JPA 엔티티. 공식
// JdbcRegisteredClientRepository(고정 스키마, MariaDB/PostgreSQL/MySQL 등 특정 방언 전제)를 쓰지
// 않고 이 엔티티를 새로 만든 이유 — 이 프로젝트는 마이그레이션 도구 없이 Hibernate ddl-auto로 6개
// DB(MariaDB/PostgreSQL/MySQL/SQL Server/CUBRID/H2)를 전부 지원해야 해서 ApiToken과 동일한
// 패턴(엔티티 하나로 전 DB 자동 대응)을 따른다.
//
// redirectUris/scopes/grantTypes/authMethods는 ProtectedBranch.restrictPushTo와 동일한 이유(카디널리티가
// 작아 별도 조인 테이블을 둘 만큼의 복잡도가 없음)로 콤마 구분 문자열 컬럼으로 저장한다 —
// JpaRegisteredClientRepository가 Set<String>으로 파싱/직렬화한다.
@Entity
@Table(name = "oauth_registered_client")
class OAuthRegisteredClient(
    @Id
    @Column(length = 100)
    var id: String,

    @Column(name = "client_id", nullable = false, unique = true, length = 100)
    var clientId: String,

    @Column(name = "client_id_issued_at", nullable = false)
    var clientIdIssuedAt: Instant = Instant.now(),

    // DCR로 등록되는 MCP 클라이언트는 공개 클라이언트(PKCE 전용, client_secret 없음)가 기본이라
    // nullable — client_secret_basic 등을 쓰는 사전등록 클라이언트를 위해 남겨둔다.
    @Column(name = "client_secret", length = 200)
    var clientSecret: String? = null,

    @Column(name = "client_secret_expires_at")
    var clientSecretExpiresAt: Instant? = null,

    @Column(name = "client_name", nullable = false, length = 200)
    var clientName: String,

    // OAuthAuthorization.kt와 동일한 이유(@Lob 단독이면 @Column.length 기본값 255가 그대로
    // 적용돼 MariaDB/MySQL에서 TINYTEXT로 축소됨)로 아래 콤마 구분 문자열 컬럼 전부에 명시적으로
    // 넉넉한 length를 지정한다 — scopes(현재 16개 스코프 문자열을 콤마로 이어붙이면 이미 190자
    // 안팎이라, 스코프 그룹이 하나만 늘어도 255자 한계에 부딪힐 수 있었다.
    // 콤마 구분 문자열: 예) "client_secret_basic,none"
    @Lob
    @Column(name = "client_authentication_methods", nullable = false, length = 1_000_000)
    var clientAuthenticationMethods: String,

    // 콤마 구분 문자열: 예) "authorization_code,refresh_token"
    @Lob
    @Column(name = "authorization_grant_types", nullable = false, length = 1_000_000)
    var authorizationGrantTypes: String,

    @Lob
    @Column(name = "redirect_uris", length = 1_000_000)
    var redirectUris: String? = null,

    @Lob
    @Column(name = "scopes", nullable = false, length = 1_000_000)
    var scopes: String,

    // RFC7591 DCR로 등록된 클라이언트인지(사전등록 클라이언트와 구분 — "Authorized OAuth Apps" 화면에서
    // 사용자에게 보여줄 때, 어떤 클라이언트가 자동등록됐는지 관리자가 구분할 수 있게 한다).
    @Column(name = "dynamically_registered", nullable = false)
    var dynamicallyRegistered: Boolean = false,

    // requireProofKey는 RFC7636(PKCE) 강제 여부 — MCP 클라이언트는 항상 true로 등록한다. false를
    // 허용하는 유일한 이유는 client_secret_basic을 쓰는 사전등록 confidential 클라이언트뿐이다.
    @Column(name = "require_proof_key", nullable = false)
    var requireProofKey: Boolean = true,

    @Column(name = "require_authorization_consent", nullable = false)
    var requireAuthorizationConsent: Boolean = true,

    @Column(name = "access_token_ttl_seconds", nullable = false)
    var accessTokenTtlSeconds: Long = DEFAULT_ACCESS_TOKEN_TTL_SECONDS,

    @Column(name = "refresh_token_ttl_seconds", nullable = false)
    var refreshTokenTtlSeconds: Long = DEFAULT_REFRESH_TOKEN_TTL_SECONDS,

    @Column(name = "reuse_refresh_tokens", nullable = false)
    var reuseRefreshTokens: Boolean = false,

    // OAuth 앱 등록을 사이트 관리자 전용에서 사용자 셀프서비스로 전환하며 추가한 소유자 컬럼.
    // DCR로 등록되는 MCP 클라이언트와 이 컬럼이 생기기 전에 이미 존재하던 사전등록 클라이언트는
    // 소유자가 없으므로(null) nullable — null이면 관리자 감사(audit) 화면에서 "DCR/system" 등으로
    // 표시한다. 사용자가 셀프서비스로 등록한 앱만 이 값이 채워지고, 삭제 시 이 값과 loginUser.id를
    // 대조해 IDOR(다른 사용자의 앱 삭제)을 막는다.
    @Column(name = "owner_id")
    var ownerId: Long? = null
) {
    companion object {
        const val DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 3600L // 1시간
        const val DEFAULT_REFRESH_TOKEN_TTL_SECONDS = 2_592_000L // 30일 — 이 앱의 클라이언트는
        // 전부 공개(PKCE 전용) 클라이언트라 Spring Authorization Server가 리프레시 토큰을 아예
        // 발급하지 않는다(OAuth2RefreshTokenGenerator.isPublicClientForAuthorizationCodeGrant 참고)
        // — 현재는 사실상 쓰이지 않는 값이지만, 향후 confidential 클라이언트를 지원하게 되면 그때
        // 의미를 갖는다.
    }
}
