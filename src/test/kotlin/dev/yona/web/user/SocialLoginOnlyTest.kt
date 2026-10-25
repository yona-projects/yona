package dev.yona.web.user

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * 소셜 로그인만 허용하는 사이트(`application.use.social.login.only`)에서 로그인 요청은 거부한다.
 * 원본: `UserApp.login`(`app/controllers/UserApp.java:161-166`).
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.social-login-only=true"])
@AutoConfigureMockMvc
@Transactional
class SocialLoginOnlyTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `비밀번호 로그인 요청은 안내와 함께 홈 화면을 그린다`() {
        val result = mockMvc.perform(post("/users/login").param("loginIdOrEmail", "admin").param("password", "admin00")).andExpect(status().isOk).andReturn()
        assertEquals("index/notifications", result.modelAndView?.viewName)
        assertEquals("app.warn.support.social.login.only", result.flashMap["error"])
        val notices = (result.modelAndView!!.model["flashMessages"] as dev.yona.web.layout.FlashMessages).notices
        assertEquals(1, notices.size)
    }

    @Test
    fun `AJAX 로그인 요청도 같다`() {
        mockMvc.perform(post("/users/login").header("Accept", "application/json").param("loginIdOrEmail", "admin").param("password", "admin00")).andExpect(status().isOk)
    }
}
