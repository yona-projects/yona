package com.github.yonaprojects.yona.domain.oauth2server

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

// Spring Authorization Server의 OAuth2AuthorizationConsent(사용자가 어떤 클라이언트에 어떤
// 스코프까지 동의했는지)를 저장하는 JPA 엔티티. 자연키(registeredClientId + principalName)를
// "clientId:principalName" 형태의 단일 문자열 id로 합성해 저장한다 — 별도 @EmbeddedId/@IdClass
// 복합키 없이 ApiTokenScope 등 기존 엔티티와 동일하게 단순한 구조를 유지한다(과도한 설계 금지).
// 이 테이블은 동시에 "Authorized OAuth Apps" 화면(사용자가 자신이 인가한 앱을 조회·취소하는
// GitHub 동등 기능)의 데이터 소스이기도 하다 — principalName으로 조회한다.
@Entity
@Table(name = "oauth_authorization_consent")
class OAuthAuthorizationConsent(
    @Id
    @Column(length = 400)
    var id: String,

    @Column(name = "registered_client_id", nullable = false, length = 100)
    var registeredClientId: String,

    @Column(name = "principal_name", nullable = false, length = 200)
    var principalName: String,

    // 긴 문자열 컬럼 매핑 이유는 OAuthAuthorization.kt 참고(@Lob을 쓰지 않는다).
    // 콤마 구분 문자열 — 예) "SCOPE_issues:read,SCOPE_issues:write"
    @JdbcTypeCode(SqlTypes.LONG32NVARCHAR)
    @Column(name = "authorities", nullable = false, length = 1_000_000)
    var authorities: String
) {
    companion object {
        fun idOf(registeredClientId: String, principalName: String) = "$registeredClientId:$principalName"
    }
}
