package com.github.yonaprojects.yona.config.sso

import com.github.yonaprojects.yona.domain.sso.OidcSsoSettings
import com.github.yonaprojects.yona.domain.sso.SsoSettingsService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType

// yona-wiki P3-06(엔터프라이즈 SSO) Step4 — 기존 소셜 로그인(google/github, application.yml 기반)은
// 그대로 유지하면서 관리자 UI/설정으로 등록한 엔터프라이즈 OIDC를 함께 찾을 수 있어야 한다. 실제
// OIDC Discovery(네트워크 호출)는 하지 않고 enterpriseRegistrationBuilder를 대체해 단위테스트한다.
class YonaClientRegistrationRepositorySpec : DescribeSpec({
    fun oauth2ClientProperties(): OAuth2ClientProperties {
        val properties = OAuth2ClientProperties()
        val google = OAuth2ClientProperties.Registration()
        google.clientId = "google-client-id"
        google.clientSecret = "google-client-secret"
        google.scope = setOf("profile", "email")
        properties.registration["google"] = google
        return properties
    }

    val ssoSettingsService = mockk<SsoSettingsService>()

    describe("YonaClientRegistrationRepository.findByRegistrationId") {
        it("기존 소셜 로그인(google) 등록은 그대로 조회할 수 있어야 한다") {
            val repository = YonaClientRegistrationRepository(oauth2ClientProperties(), ssoSettingsService) {
                error("호출되면 안 됨")
            }

            val registration = repository.findByRegistrationId("google")

            registration.shouldNotBeNull()
            registration.clientId shouldBe "google-client-id"
        }

        it("엔터프라이즈 OIDC가 활성화돼 있고 registrationId가 일치하면 동적으로 빌드해야 한다") {
            every { ssoSettingsService.getOidcSettings() } returns OidcSsoSettings(
                enabled = true, registrationId = "oidc", issuerUri = "https://idp.example.com",
                clientId = "enterprise-cid", clientSecret = "enterprise-secret"
            )
            val fakeRegistration = ClientRegistration.withRegistrationId("oidc")
                .clientId("enterprise-cid")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .tokenUri("https://idp.example.com/token")
                .authorizationUri("https://idp.example.com/authorize")
                .redirectUri("{baseUrl}/{action}/oauth2/code/{registrationId}")
                .build()
            val repository = YonaClientRegistrationRepository(oauth2ClientProperties(), ssoSettingsService) {
                fakeRegistration
            }

            val registration = repository.findByRegistrationId("oidc")

            registration.shouldNotBeNull()
            registration.clientId shouldBe "enterprise-cid"
        }

        it("엔터프라이즈 OIDC가 비활성화돼 있으면 null을 반환해야 한다") {
            every { ssoSettingsService.getOidcSettings() } returns OidcSsoSettings(enabled = false)
            val repository = YonaClientRegistrationRepository(oauth2ClientProperties(), ssoSettingsService) {
                error("비활성화 상태면 호출되면 안 됨")
            }

            repository.findByRegistrationId("oidc").shouldBeNull()
        }

        it("등록되지 않은 registrationId는 null을 반환해야 한다") {
            every { ssoSettingsService.getOidcSettings() } returns OidcSsoSettings(enabled = true, registrationId = "oidc")
            val repository = YonaClientRegistrationRepository(oauth2ClientProperties(), ssoSettingsService) {
                error("호출되면 안 됨")
            }

            repository.findByRegistrationId("unknown").shouldBeNull()
        }
    }
})
