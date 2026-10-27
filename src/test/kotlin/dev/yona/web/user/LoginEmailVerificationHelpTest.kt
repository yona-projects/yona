package dev.yona.web.user

import dev.yona.support.selectFirst
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * 이메일 인증을 쓰는 사이트(`application.use.email.verification`)의 로그인 화면은 처음 로그인하면 확인 메일이 간다는 안내를 보인다.
 * 원본: `app/views/user/login.scala.html:45-47`(`UserApp.isUsingEmailVerification`).
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.use-email-verification=true"])
@AutoConfigureMockMvc
@Transactional
class LoginEmailVerificationHelpTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `로그인 화면은 확인 메일 안내를 폼 위에 보인다`() {
        val doc = Jsoup.parse(mockMvc.perform(get("/users/loginform")).andExpect(status().isOk).andReturn().response.contentAsString)
        val help = doc.selectFirst(".login-form-wrap .email-verification-help")!!
        assertEquals("If you are trying to login for the first time, a confirmation mail will be sent.", help.text())
        assertEquals("div", help.tagName())
        assertEquals("form", help.nextElementSibling()!!.tagName())
    }
}
