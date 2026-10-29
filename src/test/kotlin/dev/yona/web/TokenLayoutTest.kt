package dev.yona.web

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertFalse

/**
 * 외부 API(`/-_-api/v1`)가 HTML 화면을 그리지 않는지 검증한다(X-5, `decisions.md` D-36). v1.6은 토큰으로 `/-_-api/v1/admin/users`의 403 화면을 그려
 * 세션 없는 화면에는 "U" 단축키가 없는 차이가 있었다. 외부 API는 이제 토큰 전용 JSON만 돌려주고 세션은 무시하므로 그 화면 자체가 없다.
 */
@AutoConfigureMockMvc
@Transactional
class TokenLayoutTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tokens: ApiTokenService

    private fun user(name: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "tl$name", name = "tl$name", email = "tl$name@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    @Test
    fun `사이트 관리자 범위가 없는 토큰의 거절은 JSON이고 Cache-Control 없이 낸다`() {
        val u = user("c1"); val t = (tokens.create(u, "t", setOf("repo"), null) as TokenCreation.Created).value
        mockMvc.perform(get("/-_-api/v1/admin/users").header("Authorization", "token $t"))
            .andExpect(status().isForbidden).andExpect(header().doesNotExist("Cache-Control"))
    }

    @Test
    fun `세션으로 로그인해도 외부 API는 화면을 그리지 않고 401 JSON이다`() {
        val u = user("b1")
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", u.loginId!!).param("password", "1234"))
        val response = mockMvc.perform(get("/-_-api/v1/admin/users").session(session)).andExpect(status().isUnauthorized).andReturn().response
        assertFalse(response.contentAsString.contains("setKeymapLink"), "HTML 화면이 아니다")
    }
}
