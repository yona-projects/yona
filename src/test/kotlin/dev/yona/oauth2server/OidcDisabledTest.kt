package dev.yona.oauth2server

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertTrue

/** 서명 키를 정하지 않으면 OIDC는 꺼져 있다(D-77 3번): 발견 문서와 사용자 정보가 404이고 OIDC 범위로는 앱을 만들 수 없으며 보호 자원 메타데이터에도 나오지 않는다. */
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-oidc-off"])
class OidcDisabledTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var clientService: OAuthClientService

    @Test
    fun `키를 정하지 않으면 발견 문서와 사용자 정보는 404이다`() {
        mockMvc.perform(get("/.well-known/openid-configuration")).andExpect(status().isNotFound)
        mockMvc.perform(get("/oauth2/userinfo")).andExpect(status().isNotFound)
    }

    @Test
    fun `OIDC 범위로는 앱을 만들 수 없고 지원 범위에도 나오지 않는다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        val u = users.save(dev.yona.domain.user.User(loginId = "od${System.nanoTime() % 100000}", name = "x", email = "od${System.nanoTime()}@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val result = clientService.create(u, "꺼진 OIDC", "", "", "https://app.example.com/cb", false, setOf("openid", "read:user"))
        assertTrue(result is ClientCreation.Rejected, "$result")
        mockMvc.perform(get("/.well-known/oauth-protected-resource")).andExpect(status().isOk)
            .andExpect(jsonPath("$.scopes_supported", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("openid"))))
    }

    @Test
    fun `앱 만들기 화면에 OIDC 범위가 보이지 않는다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        val u = users.save(dev.yona.domain.user.User(loginId = "ov${System.nanoTime() % 100000}", name = "x", email = "ov${System.nanoTime()}@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val session = org.springframework.mock.web.MockHttpSession().also { mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }
        val form = mockMvc.perform(get("/user/editform/oauth-apps/new").session(session)).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(form.contains("id=\"oauth-scope-repo\"") && !form.contains("id=\"oauth-scope-openid\""))
    }
}
