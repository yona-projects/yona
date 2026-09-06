package com.github.yonaprojects.yona.config.sso

import com.github.yonaprojects.yona.domain.sso.Saml2SsoSettings
import com.github.yonaprojects.yona.domain.sso.SsoSettingsService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

private const val TEST_CERTIFICATE = """-----BEGIN CERTIFICATE-----
MIIDHzCCAgegAwIBAgIUJ0shHk2fHWrf/OchVXj4ApS1Z5kwDQYJKoZIhvcNAQEL
BQAwHzEdMBsGA1UEAwwUdGVzdC1pZHAuZXhhbXBsZS5jb20wHhcNMjYwOTA2MDIw
ODI4WhcNMzYwOTAzMDIwODI4WjAfMR0wGwYDVQQDDBR0ZXN0LWlkcC5leGFtcGxl
LmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAL4dyunCTcLx10kM
sTQP2a+O9727klwBl/DNyYsmZOwNcQG5CAPxpI7mgJDjFI9FS67sRnjC36Nfjz/d
J9514UbprKJRT0Lu7AW9iwexr4GJz9ePyabPIpu9d/nAmGqrUK9V4nCEk/bmVD+C
m8phNCBi/BVq0qItr685g4PNkMmQuEAXV5bWrXH0jAMS23y4ZNXtCnC9EwvluH+c
IkRIjXS7Ka7wxPdifBu0Zts2Bc0uTAH2J6UQozPauszN4jiucRRRs1EddPXUbCSB
ACtVIiCvEyB+I4gMXb3nuqLQ8D0M6c3cZITpq47WZ9jr2L3HX5tMdgX9ieocilyw
uGHe9HcCAwEAAaNTMFEwHQYDVR0OBBYEFD1Xk+2n/lHRe3OWuHJUTVHYuAZ4MB8G
A1UdIwQYMBaAFD1Xk+2n/lHRe3OWuHJUTVHYuAZ4MA8GA1UdEwEB/wQFMAMBAf8w
DQYJKoZIhvcNAQELBQADggEBAHhQPGKPQkSIQmHpKv1YqbljoWkuro/65g0gS/R7
iEoSQn9EV7aQunBLJB/Nwk37AjhzWHSbSxc2HCev+haWt3NtsnCG9lVfx9vnxIdW
KvMqfZD6x+4187haqs181wZy8c5u4zBe+scjFucY22t7QRY3SXzsCkzTf2HFEpFi
HEvbQj83VZcpfFBW5A8husda4UkknwACJNNNcZgCy7UUZNYGjeIZgjzJ9PHfgL+f
dWTL9u5Nj9MznzpHmvZWAkWfBxZzk20xL933zo+UsBuBzvGAkSsiK5Q9mxKt7CxJ
KBmhbHUZLY8RIHle57mLHO54+OQneX24iWq5/xY4tHeAfck=
-----END CERTIFICATE-----"""

// yona-wiki P3-06(엔터프라이즈 SSO) Step4 — 관리자 UI가 DB에 저장한 IdP 메타데이터로 매 요청마다
// RelyingPartyRegistration을 조립하는지 검증. 실제 IdP/testcontainer 없이 자체 서명 테스트
// 인증서(PEM)만으로 X.509 파싱까지 포함해 순수하게 테스트한다.
class YonaRelyingPartyRegistrationRepositorySpec : DescribeSpec({
    val ssoSettingsService = mockk<SsoSettingsService>()
    val repository = YonaRelyingPartyRegistrationRepository(ssoSettingsService, "http://localhost:8080")

    describe("YonaRelyingPartyRegistrationRepository.findByRegistrationId") {
        it("활성화돼 있고 필수 필드가 모두 있으면 RelyingPartyRegistration을 만들어야 한다") {
            every { ssoSettingsService.getSaml2Settings() } returns Saml2SsoSettings(
                enabled = true, registrationId = "saml2",
                idpSsoUrl = "https://idp.example.com/sso",
                idpEntityId = "https://idp.example.com/entity",
                idpCertificate = TEST_CERTIFICATE
            )

            val registration = repository.findByRegistrationId("saml2")

            registration.shouldNotBeNull()
            registration.assertingPartyMetadata.entityId shouldBe "https://idp.example.com/entity"
            registration.assertingPartyMetadata.singleSignOnServiceLocation shouldBe "https://idp.example.com/sso"
        }

        it("비활성화 상태면 null을 반환해야 한다") {
            every { ssoSettingsService.getSaml2Settings() } returns Saml2SsoSettings(enabled = false)

            repository.findByRegistrationId("saml2").shouldBeNull()
        }

        it("registrationId가 일치하지 않으면 null을 반환해야 한다") {
            every { ssoSettingsService.getSaml2Settings() } returns Saml2SsoSettings(
                enabled = true, registrationId = "saml2",
                idpSsoUrl = "https://idp.example.com/sso",
                idpEntityId = "https://idp.example.com/entity",
                idpCertificate = TEST_CERTIFICATE
            )

            repository.findByRegistrationId("other").shouldBeNull()
        }

        it("필수 필드(인증서 등)가 비어 있으면 null을 반환해야 한다") {
            every { ssoSettingsService.getSaml2Settings() } returns Saml2SsoSettings(
                enabled = true, registrationId = "saml2",
                idpSsoUrl = "https://idp.example.com/sso",
                idpEntityId = "https://idp.example.com/entity",
                idpCertificate = null
            )

            repository.findByRegistrationId("saml2").shouldBeNull()
        }
    }
})
