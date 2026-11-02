package dev.yona.web.social

import dev.yona.domain.ldap.LdapDirectory
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertTrue

/** LDAP을 켠 사이트에서는 소셜 로그인을 받지 않는다(`decisions.md` D-81). */
@SpringBootTest(
    properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.ldap.enabled=true",
        "yona.social.login-support=github", "yona.social.github.client-id=gh-id", "yona.social.github.client-secret=gh-secret", "yona.social.github.authorization-url=https://github.test/authorize"]
)
@AutoConfigureMockMvc
@Transactional
class SocialLoginLdapSuppressionTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @MockitoBean private lateinit var directory: LdapDirectory

    @Test
    fun `LDAP을 켜면 소셜 로그인은 보이지도 시작되지도 않는다`() {
        val buttons = Jsoup.parse(mockMvc.perform(get("/users/loginform")).andReturn().response.contentAsString).select(".page.full a.oauth-login-btn")
        assertTrue(buttons.isEmpty())
        assertTrue(mockMvc.perform(get("/authenticate/github")).andReturn().response.getHeader("Location")!!.startsWith("/users/loginform"))
    }
}
