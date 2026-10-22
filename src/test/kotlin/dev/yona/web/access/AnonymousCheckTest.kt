package dev.yona.web.access

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * v1.6 `@AnonymousCheck`(`actions.AnonymousCheckAction`)의 동작을 검증한다.
 *
 * 익명 사용자 접근을 막는 사이트(`application.allowsAnonymousAccess=false`)이거나 애너테이션이
 * `requiresLogin=true`이면, 익명 사용자를 로그인 폼으로 보낸다. 이동 대상은 요청 경로이다.
 */
@AutoConfigureMockMvc
@Transactional
class AnonymousCheckTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var users: UserRepository

    private fun loggedInSession(loginId: String): MockHttpSession {
        val salt = LegacyPasswordHasher.generateSalt()
        users.save(
            User(
                loginId = loginId, email = "$loginId@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", loginId).param("password", "1234"))
        return session
    }

    @Test
    fun `익명을 허용하는 사이트에서 기본 애너테이션은 익명도 통과시킨다`() {
        mockMvc.perform(get("/__test/anon/default")).andExpect(status().isOk)
    }

    @Test
    fun `로그인을 요구하는 애너테이션은 익명을 로그인 폼으로 보낸다`() {
        mockMvc.perform(get("/__test/anon/requires-login"))
            .andExpect(status().isSeeOther)
            .andExpect(redirectedUrl("/users/loginform?redirectUrl=/__test/anon/requires-login"))
    }

    @Test
    fun `안내 문구를 켜면 flash 경고가 함께 전달된다`() {
        mockMvc.perform(get("/__test/anon/requires-login-flash"))
            .andExpect(status().isSeeOther)
            .andExpect(flash().attribute("warning", "user.login.alert"))
    }

    @Test
    fun `안내 문구를 켜지 않으면 flash를 남기지 않는다`() {
        mockMvc.perform(get("/__test/anon/requires-login"))
            .andExpect(flash().attributeCount(0))
    }

    @Test
    fun `로그인한 사용자는 통과한다`() {
        val session = loggedInSession("member1")
        mockMvc.perform(get("/__test/anon/requires-login").session(session)).andExpect(status().isOk)
    }

    @Test
    fun `애너테이션이 없는 엔드포인트는 검사하지 않는다`() {
        mockMvc.perform(get("/__test/anon/unchecked")).andExpect(status().isOk)
    }
}

/** 익명 접근을 막는 사이트에서는 모든 `@AnonymousCheck` 엔드포인트가 로그인을 요구한다. */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["yona.allows-anonymous-access=false"])
class AnonymousCheckClosedSiteTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `익명을 막는 사이트에서는 기본 애너테이션도 익명을 로그인 폼으로 보낸다`() {
        mockMvc.perform(get("/__test/anon/default"))
            .andExpect(status().isSeeOther)
            .andExpect(redirectedUrl("/users/loginform?redirectUrl=/__test/anon/default"))
    }

    @Test
    fun `애너테이션이 없는 엔드포인트는 여전히 검사하지 않는다`() {
        mockMvc.perform(get("/__test/anon/unchecked")).andExpect(status().isOk)
    }
}
