package com.github.yonaprojects.yona.domain.sso

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * yona-wiki P3-06(엔터프라이즈 SSO) Step4 — 관리자 UI(`SsoAdminController`)가 등록/조회/수정하는
 * 범용 OIDC IdP 설정. 사이트 전체에 하나만 존재하는 싱글턴 행(id 고정값 SINGLETON_ID)으로 관리한다
 * — LDAP+OIDC+SAML2를 동시에 여러 개 등록하는 멀티테넌트 요구사항은 이 계획의 범위 밖(리스크 표
 * "복잡한 우선순위/폴백 정책까지 설계하지 않는다" 참고).
 *
 * 필드 구성은 임의의 OIDC IdP(Okta/Azure AD/Keycloak 등)를 붙이는 일반적인 엔터프라이즈 SaaS
 * 설정 화면 관행을 따른다(GitHub은 조직 SSO로 OIDC를 별도 제공하지 않아 SAML만큼의 직접적인
 * 선례는 없음) — Issuer URL(OIDC Discovery), Client ID, Client Secret, 활성화 여부.
 */
@Entity
@Table(name = "oidc_sso_settings")
class OidcSsoSettings(
    @Id
    var id: Long = SINGLETON_ID,

    @Column(nullable = false)
    var enabled: Boolean = false,

    @Column(nullable = false)
    var registrationId: String = "oidc",

    @Column(length = 2000)
    var issuerUri: String? = null,

    @Column(length = 500)
    var clientId: String? = null,

    @Column(length = 500)
    var clientSecret: String? = null
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}
