package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.oauth2server.OAuthAppRegistrationService
import com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClient
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
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

// yona-wiki P3-17 — [[p3-14]] 1라운드에서는 이 컨트롤러(/site/oauth-apps)가 등록까지 사이트 관리자
// 전용으로 했지만, P3-17에서 등록/자기소유 삭제는 UserViewController(/user/editform/oauth-apps-owned,
// 사용자 셀프서비스)로 옮기고 여기는 "사이트 전체 앱 조회(감사) + 소유자와 무관한 강제 삭제"만
// 남긴다. 등록 폼/POST .../register 라우트는 완전히 제거됐다 — 그 테스트들은 여기서 지우고
// UserViewControllerSpec 쪽에 새로 추가했다.
class OAuthAppsAdminControllerSpec : DescribeSpec({
    val oAuthAppRegistrationService = mockk<OAuthAppRegistrationService>()
    val userRepository = mockk<UserRepository>()

    val controller = OAuthAppsAdminController(oAuthAppRegistrationService, userRepository)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")
    val adminUser = User(id = 1L, loginId = "admin", name = "admin", state = UserState.SITE_ADMIN)
    val normalAuth = UsernamePasswordAuthenticationToken("normal", "password")
    val normalUser = User(id = 2L, loginId = "normal", name = "normal", state = UserState.ACTIVE)
    val ownerUser = User(id = 3L, loginId = "owner", name = "owner", state = UserState.ACTIVE)

    beforeTest {
        clearMocks(oAuthAppRegistrationService, userRepository)
        every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
        every { userRepository.findByLoginId("normal") } returns Optional.of(normalUser)
    }

    describe("GET /site/oauth-apps") {
        it("사이트 관리자는 등록된 앱 목록을 조회할 수 있어야 한다") {
            every { oAuthAppRegistrationService.listAll() } returns emptyList()

            mockMvc.perform(get("/site/oauth-apps").principal(adminAuth))
                .andExpect(status().isOk)
                .andExpect(view().name("site/oauth_apps"))
                .andExpect(model().attributeExists("apps"))
        }

        it("사이트 관리자가 아니면 403 화면을 반환해야 한다") {
            mockMvc.perform(get("/site/oauth-apps").principal(normalAuth))
                .andExpect(view().name("error/403"))
        }

        it("다른 사용자가 등록한 앱(ownerId 있음)과 DCR 자동등록 앱(ownerId 없음)을 모두 보여주고 소유자를 함께 표시해야 한다") {
            val ownedByOther = OAuthRegisteredClient(
                id = "client-owned", clientId = "cid-owned", clientName = "Owner's App",
                clientAuthenticationMethods = "client_secret_basic", authorizationGrantTypes = "authorization_code",
                scopes = "issues:read", ownerId = 3L
            )
            val dcrClient = OAuthRegisteredClient(
                id = "client-dcr", clientId = "cid-dcr", clientName = "Claude Code",
                clientAuthenticationMethods = "none", authorizationGrantTypes = "authorization_code",
                scopes = "issues:read", dynamicallyRegistered = true, ownerId = null
            )
            every { oAuthAppRegistrationService.listAll() } returns listOf(ownedByOther, dcrClient)
            every { userRepository.findAllById(listOf(3L)) } returns listOf(ownerUser)

            val result = mockMvc.perform(get("/site/oauth-apps").principal(adminAuth))
                .andExpect(status().isOk)
                .andReturn()

            @Suppress("UNCHECKED_CAST")
            val rows = result.modelAndView!!.model["apps"] as List<OAuthAppsAdminController.OAuthAppAdminRow>
            rows.size shouldBe 2
            rows.first { it.app.id == "client-owned" }.ownerLoginId shouldBe "owner"
            rows.first { it.app.id == "client-dcr" }.ownerLoginId shouldBe null
        }
    }

    describe("POST /site/oauth-apps/register (제거됨)") {
        it("더 이상 이 경로로 등록할 수 없어야 한다(404)") {
            mockMvc.perform(
                post("/site/oauth-apps/register").principal(adminAuth)
                    .param("clientName", "x")
                    .param("redirectUri", "https://example.com/callback")
            ).andExpect(status().isNotFound)
        }
    }

    describe("POST /site/oauth-apps/{id}/delete") {
        it("클라이언트와 관련 동의/토큰 레코드를 모두 삭제해야 한다") {
            val client = OAuthRegisteredClient(
                id = "client-1", clientId = "cid-1", clientName = "App",
                clientAuthenticationMethods = "none", authorizationGrantTypes = "authorization_code", scopes = "issues:read"
            )
            every { oAuthAppRegistrationService.findById("client-1") } returns client
            every { oAuthAppRegistrationService.deleteClientAndRelatedRecords(client) } returns Unit

            mockMvc.perform(post("/site/oauth-apps/{id}/delete", "client-1").principal(adminAuth))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/site/oauth-apps"))

            verify(exactly = 1) { oAuthAppRegistrationService.deleteClientAndRelatedRecords(client) }
        }

        it("소유자가 다른 앱도(사이트 관리자 강제 오버라이드) 삭제할 수 있어야 한다") {
            val ownedByOther = OAuthRegisteredClient(
                id = "client-owned", clientId = "cid-owned", clientName = "Owner's App",
                clientAuthenticationMethods = "client_secret_basic", authorizationGrantTypes = "authorization_code",
                scopes = "issues:read", ownerId = 3L
            )
            every { oAuthAppRegistrationService.findById("client-owned") } returns ownedByOther
            every { oAuthAppRegistrationService.deleteClientAndRelatedRecords(ownedByOther) } returns Unit

            mockMvc.perform(post("/site/oauth-apps/{id}/delete", "client-owned").principal(adminAuth))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/site/oauth-apps"))

            verify(exactly = 1) { oAuthAppRegistrationService.deleteClientAndRelatedRecords(ownedByOther) }
        }

        it("사이트 관리자가 아니면 403 화면을 반환해야 한다") {
            mockMvc.perform(post("/site/oauth-apps/{id}/delete", "client-1").principal(normalAuth))
                .andExpect(view().name("error/403"))
        }
    }
})
