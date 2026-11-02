package dev.yona.oauth2server

import dev.yona.domain.ldap.LdapDirectory
import dev.yona.domain.ldap.LdapUser
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.OAuthProviderFlow
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * LDAP을 켠 사이트에서 Yona가 OIDC 제공자일 때(외부 앱이 Yona로 로그인을 요청), LDAP 계정으로 로그인하면 보관해 둔 승인 요청이 이어지는지 검증한다.
 */
@SpringBootTest(
    properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.ldap.enabled=true",
        "yona.data-dir=build/tmp/it-resume-ldap", "yona.oauth2.oidc.signing-key-file=src/test/resources/oauth2/oidc-test-key.pem"]
)
@AutoConfigureMockMvc
@Transactional
class OAuthResumeAfterLdapLoginTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var clients: OAuthClientService
    @MockitoBean private lateinit var directory: LdapDirectory

    @Test
    fun `LDAP 계정으로 로그인하면 승인 요청이 이어진다`() {
        val flow = OAuthProviderFlow(mockMvc, users, clients, "ldap")
        Mockito.`when`(directory.authenticate("ldapkim", "pw")).thenReturn(LdapUser("엘답 김", "ldapkim@corp.test", "ldapkim", "", ""))
        val session = MockHttpSession(); val page = flow.begin(session)
        val redirectUrl = Jsoup.parse(mockMvc.perform(get(page).session(session)).andReturn().response.contentAsString).selectFirst("form[action$=/users/login] input[name=redirectUrl]")!!.attr("value")
        val response = mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", "ldapkim").param("password", "pw").param("redirectUrl", redirectUrl)).andReturn().response
        assertEquals("/oauth2/resume", response.getHeader("Location"))
        val user = users.findByLoginId("ldapkim"); assertNotNull(user)
        assertEquals(user.id.toString(), flow.finish(session).subject)
    }
}
