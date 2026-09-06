package com.github.yonaprojects.yona.domain.sso

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table

/**
 * yona-wiki P3-06(엔터프라이즈 SSO) Step4 — 관리자 UI가 등록/조회/수정하는 SAML2 IdP 설정.
 * 필드 구성/명명은 GitHub Enterprise의 조직 설정 > Security > "SAML single sign-on" 화면을
 * 그대로 차용한다(모호할 때 GitHub 방식을 따르는 공통 방침) — Sign on URL, Issuer, Public
 * Certificate, 활성화 여부(GitHub의 "Enable SAML authentication"). GitHub은 여기에 더해
 * Signature/Digest Method 드롭다운도 제공하지만 OpenSAML 기본값(RSA-SHA256/SHA256)으로
 * 충분해 과도한 설계를 피하기 위해 생략했다.
 */
@Entity
@Table(name = "saml2_sso_settings")
class Saml2SsoSettings(
    @Id
    var id: Long = SINGLETON_ID,

    @Column(nullable = false)
    var enabled: Boolean = false,

    @Column(nullable = false)
    var registrationId: String = "saml2",

    // GitHub의 "Sign on URL" — IdP의 SSO(SingleSignOnService) 엔드포인트.
    @Column(length = 2000)
    var idpSsoUrl: String? = null,

    // GitHub의 "Issuer" — IdP의 entity ID.
    @Column(length = 500)
    var idpEntityId: String? = null,

    // GitHub의 "Public Certificate" — IdP가 서명한 어서션을 검증할 PEM 형식 X.509 인증서.
    @Lob
    @Column
    var idpCertificate: String? = null
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}
