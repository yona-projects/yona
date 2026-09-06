package com.github.yonaprojects.yona.domain.sso

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional

// yona-wiki P3-06(엔터프라이즈 SSO) Step4 — application.yml 기반 최소 동작 경로(설정 파일만으로도
// 동작)와 관리자 UI(DB 저장)를 모두 지원해야 한다는 요구사항 검증. DB에 저장된 행이 없으면
// application.yml(@Value 기본값)로 만든 설정을 돌려주고, 관리자 UI가 저장하면 그 이후로는 DB 값이
// 우선한다.
class SsoSettingsServiceSpec : DescribeSpec({
    val oidcRepository = mockk<OidcSsoSettingsRepository>()
    val saml2Repository = mockk<Saml2SsoSettingsRepository>()

    fun service(
        oidcEnabled: Boolean = false,
        oidcIssuerUri: String = "",
        saml2Enabled: Boolean = false,
        saml2IdpSsoUrl: String = ""
    ) = SsoSettingsService(
        oidcRepository, saml2Repository,
        oidcEnabled, "oidc", oidcIssuerUri, "yml-client-id", "yml-client-secret",
        saml2Enabled, "saml2", saml2IdpSsoUrl, "yml-idp-entity-id", "yml-cert"
    )

    describe("getOidcSettings") {
        it("DB에 저장된 행이 없으면 application.yml 기본값으로 만든 설정을 반환해야 한다") {
            every { oidcRepository.findById(OidcSsoSettings.SINGLETON_ID) } returns Optional.empty()

            val settings = service(oidcEnabled = true, oidcIssuerUri = "https://idp.example.com").getOidcSettings()

            settings.enabled shouldBe true
            settings.issuerUri shouldBe "https://idp.example.com"
            settings.clientId shouldBe "yml-client-id"
        }

        it("DB에 저장된 행이 있으면 그 값을 그대로 반환해야 한다") {
            val saved = OidcSsoSettings(enabled = true, registrationId = "oidc", issuerUri = "https://db.example.com")
            every { oidcRepository.findById(OidcSsoSettings.SINGLETON_ID) } returns Optional.of(saved)

            val settings = service().getOidcSettings()

            settings.issuerUri shouldBe "https://db.example.com"
        }
    }

    describe("saveOidcSettings") {
        it("입력값으로 싱글턴 행을 upsert해야 한다") {
            every { oidcRepository.findById(OidcSsoSettings.SINGLETON_ID) } returns Optional.empty()
            val savedSlot = slot<OidcSsoSettings>()
            every { oidcRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service().saveOidcSettings(
                enabled = true, registrationId = "my-oidc",
                issuerUri = "https://idp.example.com", clientId = "cid", clientSecret = "secret"
            )

            result.enabled shouldBe true
            result.registrationId shouldBe "my-oidc"
            result.issuerUri shouldBe "https://idp.example.com"
            verify(exactly = 1) { oidcRepository.save(any()) }
        }
    }

    describe("getSaml2Settings") {
        it("DB에 저장된 행이 없으면 application.yml 기본값으로 만든 설정을 반환해야 한다") {
            every { saml2Repository.findById(Saml2SsoSettings.SINGLETON_ID) } returns Optional.empty()

            val settings = service(saml2Enabled = true, saml2IdpSsoUrl = "https://idp.example.com/sso").getSaml2Settings()

            settings.enabled shouldBe true
            settings.idpSsoUrl shouldBe "https://idp.example.com/sso"
            settings.idpEntityId shouldBe "yml-idp-entity-id"
        }
    }

    describe("saveSaml2Settings") {
        it("입력값으로 싱글턴 행을 upsert해야 한다") {
            every { saml2Repository.findById(Saml2SsoSettings.SINGLETON_ID) } returns Optional.empty()
            val savedSlot = slot<Saml2SsoSettings>()
            every { saml2Repository.save(capture(savedSlot)) } answers { savedSlot.captured }

            val result = service().saveSaml2Settings(
                enabled = true, registrationId = "my-saml2",
                idpSsoUrl = "https://idp.example.com/sso", idpEntityId = "https://idp.example.com/entity",
                idpCertificate = "-----BEGIN CERTIFICATE-----\nMIIB...\n-----END CERTIFICATE-----"
            )

            result.enabled shouldBe true
            result.registrationId shouldBe "my-saml2"
            result.idpSsoUrl shouldBe "https://idp.example.com/sso"
            verify(exactly = 1) { saml2Repository.save(any()) }
        }
    }
})
