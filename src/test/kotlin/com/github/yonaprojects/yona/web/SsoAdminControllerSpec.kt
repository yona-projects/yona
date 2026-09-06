package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.sso.OidcSsoSettings
import com.github.yonaprojects.yona.domain.sso.Saml2SsoSettings
import com.github.yonaprojects.yona.domain.sso.SsoSettingsService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-06(엔터프라이즈 SSO) Step4(사용자 지시로 확장) — 관리자 UI(/site/sso)에서 OIDC/SAML2
// IdP 설정을 조회/등록/수정할 수 있어야 한다. SiteViewController와 동일한 관리자 전용 패턴
// (checkAdmin → IllegalArgumentException → error/403)을 그대로 따른다.
class SsoAdminControllerSpec : DescribeSpec({
    val ssoSettingsService = mockk<SsoSettingsService>()
    val userRepository = mockk<UserRepository>()

    val controller = SsoAdminController(ssoSettingsService, userRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")
    val adminUser = User(id = 1L, loginId = "admin", name = "admin", state = UserState.SITE_ADMIN)
    val normalAuth = UsernamePasswordAuthenticationToken("normal", "password")
    val normalUser = User(id = 2L, loginId = "normal", name = "normal", state = UserState.ACTIVE)

    beforeTest {
        clearMocks(ssoSettingsService, userRepository)
        every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
        every { userRepository.findByLoginId("normal") } returns Optional.of(normalUser)
    }

    describe("GET /site/sso") {
        it("사이트 관리자는 현재 설정을 조회할 수 있어야 한다") {
            every { ssoSettingsService.getOidcSettings() } returns OidcSsoSettings(enabled = true, issuerUri = "https://idp.example.com")
            every { ssoSettingsService.getSaml2Settings() } returns Saml2SsoSettings(enabled = false)

            mockMvc.perform(get("/site/sso").principal(adminAuth))
                .andExpect(status().isOk)
                .andExpect(view().name("site/sso"))
                .andExpect(model().attributeExists("oidcSettings", "saml2Settings"))
        }

        it("사이트 관리자가 아니면 403 화면을 반환해야 한다") {
            mockMvc.perform(get("/site/sso").principal(normalAuth))
                .andExpect(view().name("error/403"))
        }
    }

    describe("POST /site/sso/oidc") {
        it("사이트 관리자는 OIDC 설정을 저장하고 /site/sso로 리다이렉트돼야 한다") {
            every {
                ssoSettingsService.saveOidcSettings(true, "oidc", "https://idp.example.com", "cid", "secret")
            } returns OidcSsoSettings()

            mockMvc.perform(
                post("/site/sso/oidc").principal(adminAuth)
                    .param("enabled", "true")
                    .param("registrationId", "oidc")
                    .param("issuerUri", "https://idp.example.com")
                    .param("clientId", "cid")
                    .param("clientSecret", "secret")
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/site/sso"))

            verify(exactly = 1) {
                ssoSettingsService.saveOidcSettings(true, "oidc", "https://idp.example.com", "cid", "secret")
            }
        }

        it("사이트 관리자가 아니면 403 화면을 반환하고 저장을 시도하지 않아야 한다") {
            mockMvc.perform(
                post("/site/sso/oidc").principal(normalAuth)
                    .param("enabled", "true")
                    .param("registrationId", "oidc")
            )
                .andExpect(view().name("error/403"))

            verify(exactly = 0) { ssoSettingsService.saveOidcSettings(any(), any(), any(), any(), any()) }
        }
    }

    describe("POST /site/sso/saml2") {
        it("사이트 관리자는 SAML2 설정을 저장하고 /site/sso로 리다이렉트돼야 한다") {
            every {
                ssoSettingsService.saveSaml2Settings(
                    true, "saml2", "https://idp.example.com/sso", "https://idp.example.com/entity", "cert-pem"
                )
            } returns Saml2SsoSettings()

            mockMvc.perform(
                post("/site/sso/saml2").principal(adminAuth)
                    .param("enabled", "true")
                    .param("registrationId", "saml2")
                    .param("idpSsoUrl", "https://idp.example.com/sso")
                    .param("idpEntityId", "https://idp.example.com/entity")
                    .param("idpCertificate", "cert-pem")
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/site/sso"))

            verify(exactly = 1) {
                ssoSettingsService.saveSaml2Settings(
                    true, "saml2", "https://idp.example.com/sso", "https://idp.example.com/entity", "cert-pem"
                )
            }
        }

        it("사이트 관리자가 아니면 403 화면을 반환하고 저장을 시도하지 않아야 한다") {
            mockMvc.perform(
                post("/site/sso/saml2").principal(normalAuth)
                    .param("enabled", "true")
                    .param("registrationId", "saml2")
            )
                .andExpect(view().name("error/403"))

            verify(exactly = 0) { ssoSettingsService.saveSaml2Settings(any(), any(), any(), any(), any()) }
        }
    }
})
