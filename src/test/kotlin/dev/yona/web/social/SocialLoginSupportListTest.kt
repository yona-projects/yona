package dev.yona.web.social

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `application.social.login.support`는 화면의 버튼과 "또는" 구분선만 거르고, 인증 주소 자체는 막지 않는다.
 *
 * 원본: `utils/TemplateHelper.scala:33`(`isAllowedOAuthProvider`)이 `login.scala.html:78`, `loginDialog.scala.html:57`의 버튼만 거른다.
 * 서버 쪽 `/authenticate/:provider`(play-authenticate)는 이 값을 보지 않고, 등록된 제공자면 목록과 무관하게 인증을 시작한다.
 */
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "yona.social.login-support=github",
        "yona.social.github.client-id=gh-id", "yona.social.github.client-secret=gh-secret",
        "yona.social.google.client-id=g-id", "yona.social.google.client-secret=g-secret",
        "yona.social.google.authorization-url=https://google.test/authorize"
    ]
)
class SocialLoginSupportListTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `목록에 없어도 설정된 제공자의 인증 주소는 열린다`() {
        mockMvc.perform(get("/authenticate/google")).andExpect(status().is3xxRedirection)
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://google.test/authorize")))
    }

    @Test
    fun `목록에 없는 제공자의 버튼은 화면에 나오지 않는다`() {
        val body = mockMvc.perform(get("/users/loginform")).andReturn().response.contentAsString
        assertTrue(body.contains("/authenticate/github"), "허용된 제공자의 버튼이 없다")
        assertFalse(body.contains("/authenticate/google"), "허용되지 않은 제공자의 버튼이 나왔다")
    }
}
