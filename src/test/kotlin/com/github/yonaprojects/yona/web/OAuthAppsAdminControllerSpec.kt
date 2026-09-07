package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationConsentRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClient
import com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClientRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-14(yona를 OAuth2 서버로 제공) 1라운드 — 사이트 관리자가 confidential/public OAuth
// 앱을 직접 등록/삭제할 수 있는 화면(/site/oauth-apps). SsoAdminControllerSpec과 동일한 패턴.
class OAuthAppsAdminControllerSpec : DescribeSpec({
    val clientRepository = mockk<OAuthRegisteredClientRepository>()
    val consentRepository = mockk<OAuthAuthorizationConsentRepository>()
    val authorizationRepository = mockk<OAuthAuthorizationRepository>()
    val userRepository = mockk<UserRepository>()
    val passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    val controller = OAuthAppsAdminController(clientRepository, consentRepository, authorizationRepository, userRepository, passwordEncoder)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")
    val adminUser = User(id = 1L, loginId = "admin", name = "admin", state = UserState.SITE_ADMIN)
    val normalAuth = UsernamePasswordAuthenticationToken("normal", "password")
    val normalUser = User(id = 2L, loginId = "normal", name = "normal", state = UserState.ACTIVE)

    beforeTest {
        clearMocks(clientRepository, consentRepository, authorizationRepository, userRepository)
        every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
        every { userRepository.findByLoginId("normal") } returns Optional.of(normalUser)
    }

    describe("GET /site/oauth-apps") {
        it("사이트 관리자는 등록된 앱 목록을 조회할 수 있어야 한다") {
            every { clientRepository.findAll() } returns emptyList()

            mockMvc.perform(get("/site/oauth-apps").principal(adminAuth))
                .andExpect(status().isOk)
                .andExpect(view().name("site/oauth_apps"))
                .andExpect(model().attributeExists("apps", "availableScopes"))
        }

        it("사이트 관리자가 아니면 403 화면을 반환해야 한다") {
            mockMvc.perform(get("/site/oauth-apps").principal(normalAuth))
                .andExpect(view().name("error/403"))
        }
    }

    describe("POST /site/oauth-apps/register") {
        it("public 클라이언트로 등록하면 시크릿 없이 PKCE 필수로 저장돼야 한다") {
            val saved = slot<OAuthRegisteredClient>()
            every { clientRepository.save(capture(saved)) } answers { saved.captured }
            every { clientRepository.findAll() } returns emptyList()

            mockMvc.perform(
                post("/site/oauth-apps/register").principal(adminAuth)
                    .param("clientName", "My Public App")
                    .param("redirectUri", "https://example.com/callback")
                    .param("confidential", "false")
                    .param("scopes", "issues:read")
            ).andExpect(status().isOk)

            saved.captured.clientSecret shouldBe null
            saved.captured.requireProofKey shouldBe true
            saved.captured.clientAuthenticationMethods shouldBe "none"
            saved.captured.scopes shouldBe "issues:read"
        }

        it("confidential 클라이언트로 등록하면 시크릿이 인코딩되어 저장되고 평문은 화면에 한 번 노출돼야 한다") {
            val saved = slot<OAuthRegisteredClient>()
            every { clientRepository.save(capture(saved)) } answers { saved.captured }
            every { clientRepository.findAll() } returns emptyList()

            val result = mockMvc.perform(
                post("/site/oauth-apps/register").principal(adminAuth)
                    .param("clientName", "My Server App")
                    .param("redirectUri", "https://example.com/callback")
                    .param("confidential", "true")
                    .param("scopes", "issues:read", "issues:write")
            ).andExpect(status().isOk)
                .andExpect(model().attributeExists("registeredPlainSecret"))
                .andReturn()

            val plainSecret = result.modelAndView!!.model["registeredPlainSecret"] as String
            saved.captured.clientSecret shouldNotBe null
            saved.captured.clientSecret shouldNotBe plainSecret // 평문 그대로 저장되면 안 됨
            passwordEncoder.matches(plainSecret, saved.captured.clientSecret) shouldBe true
            saved.captured.requireProofKey shouldBe false
            saved.captured.clientAuthenticationMethods shouldBe "client_secret_basic"
        }

        it("사이트 관리자가 아니면 403 화면을 반환해야 한다") {
            mockMvc.perform(
                post("/site/oauth-apps/register").principal(normalAuth)
                    .param("clientName", "x")
                    .param("redirectUri", "https://example.com/callback")
            ).andExpect(view().name("error/403"))
        }
    }

    describe("POST /site/oauth-apps/{id}/delete") {
        it("클라이언트와 관련 동의/토큰 레코드를 모두 삭제해야 한다") {
            val client = OAuthRegisteredClient(
                id = "client-1", clientId = "cid-1", clientName = "App",
                clientAuthenticationMethods = "none", authorizationGrantTypes = "authorization_code", scopes = "issues:read"
            )
            every { clientRepository.findById("client-1") } returns Optional.of(client)
            every { consentRepository.deleteByRegisteredClientId("client-1") } returns Unit
            every { authorizationRepository.deleteByRegisteredClientId("client-1") } returns Unit
            every { clientRepository.deleteById("client-1") } returns Unit

            mockMvc.perform(post("/site/oauth-apps/{id}/delete", "client-1").principal(adminAuth))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/site/oauth-apps"))

            verify(exactly = 1) { consentRepository.deleteByRegisteredClientId("client-1") }
            verify(exactly = 1) { authorizationRepository.deleteByRegisteredClientId("client-1") }
            verify(exactly = 1) { clientRepository.deleteById("client-1") }
        }
    }
})
