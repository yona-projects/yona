package dev.yona.web

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
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 화면에 붙는 "U" 단축키(내 정보로 이동)가 세션에 로그인한 사용자에게만 생기는지 검증한다.
 *
 * v1.6 `common/scripts`는 현재 사용자가 아니라 세션의 `loginId` 값으로 단축키를 만든다. 개인 API 토큰으로만 요청하면 현재 사용자는
 * 토큰의 주인이지만 세션이 없어 단축키가 없다(공식 v1.16.0의 `/-_-api/v1/admin/users` 403 화면으로 확인했다).
 */
@AutoConfigureMockMvc
@Transactional
class TokenLayoutTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository

    private fun user(token: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "tl$token", name = "tl$token", email = "tl$token@example.com", state = UserState.ACTIVE, passwordSalt = salt,
            password = LegacyPasswordHasher.hash("1234", salt), token = token))
    }

    @Test
    fun `토큰으로만 요청하면 내 정보 단축키가 없다`() {
        val owner = user("a1")
        val html = mockMvc.perform(get("/-_-api/v1/admin/users").header("Authorization", "token a1")).andReturn().response.contentAsString
        assertTrue(html.contains("yobi.ShortcutKey.setKeymapLink"), "화면이 그려졌어야 한다")
        assertFalse(html.contains("\"U\":"), "세션이 없으면 U 단축키는 없다: ${owner.loginId}")
    }

    @Test
    fun `사이트 관리자 전용 API의 403은 Cache-Control 없이 낸다 (v1_6 액션 합성이 기본 액션 밖이다)`() {
        user("c1")
        mockMvc.perform(get("/-_-api/v1/admin/users").header("Authorization", "token c1"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().doesNotExist("Cache-Control"))
    }

    @Test
    fun `세션에 로그인하면 내 정보 단축키가 있다`() {
        val owner = user("b1")
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", owner.loginId!!).param("password", "1234"))
        val html = mockMvc.perform(get("/-_-api/v1/admin/users").session(session)).andReturn().response.contentAsString
        assertTrue(html.contains("\"U\": \"\\/${owner.loginId}\""), html.substringAfter("setKeymapLink").take(120))
    }
}
