package dev.yona.web.user

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.PasswordResetRequestedEvent
import dev.yona.domain.user.LinkedAccount
import dev.yona.domain.user.LinkedAccountRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserCredential
import dev.yona.domain.user.UserCredentialRepository
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.security.PasswordResetTokens
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SSO(OIDC, SAML2)로 연결된 사용자는 비밀번호를 만들 수 없다(`decisions.md` D-128). IdP가 신원의 기준이므로 이메일 재설정, 재설정 주소, 사이트 관리자의
 * 임시 비밀번호 어느 길로도 로컬 비밀번호가 생기지 않는다. SSO 전용 모드의 비상 로그인(`관리자 로그인`)에 쓰는 사이트 관리자는 예외이다.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class PasswordResetSsoTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var credentials: UserCredentialRepository
    @Autowired private lateinit var linked: LinkedAccountRepository
    @Autowired private lateinit var tokens: PasswordResetTokens
    @Autowired private lateinit var events: ApplicationEvents
    @MockitoBean private lateinit var mailSender: org.springframework.mail.javamail.JavaMailSender

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ps$n", name = "ps$n", email = "ps$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("old-pass", salt)))
    }

    private fun linkSso(user: User, provider: String = "sso-oidc") {
        val credential = credentials.save(UserCredential(user = user, loginId = user.loginId, email = user.email, active = true, emailValidated = true))
        linked.save(LinkedAccount(userCredential = credential, providerKey = provider, providerUserId = "idp|${user.loginId}"))
    }

    private fun siteManager(): User = newUser().also { siteAdmins.save(SiteAdmin(admin = it)) }

    private fun request(user: User) = mockMvc.perform(post("/lostPassword").param("loginId", user.loginId!!).param("emailAddress", user.email!!))

    private fun passwordIsOld(id: Long): Boolean = users.findById(id).get().let { it.password == LegacyPasswordHasher.hash("old-pass", it.passwordSalt!!) }

    @Test
    fun `SSO로 연결된 사용자는 재설정을 요청해도 메일을 보내지 않고 안내만 받는다`() {
        val user = newUser(); linkSso(user)
        val html = request(user).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals(0, events.stream(PasswordResetRequestedEvent::class.java).count(), "재설정 메일 이벤트")
        assertTrue(html.contains("single sign-on"), "SSO 사용자라는 안내가 보여야 한다")
    }

    @Test
    fun `SAML2로 연결된 사용자도 같다`() {
        val user = newUser(); linkSso(user, "sso-saml2")
        request(user).andExpect(status().isOk)
        assertEquals(0, events.stream(PasswordResetRequestedEvent::class.java).count())
    }

    @Test
    fun `재설정 주소를 받은 뒤에 SSO로 연결된 사용자는 새 비밀번호를 만들 수 없다`() {
        val user = newUser()
        val token = tokens.issue(user.loginId!!)
        linkSso(user)
        mockMvc.perform(post("/resetPassword").param("hashString", token).param("password", "new-pass")).andExpect(status().isBadRequest)
        users.flush()
        assertTrue(passwordIsOld(user.id!!), "비밀번호가 바뀌면 안 된다")
    }

    @Test
    fun `사이트 관리자도 SSO로 연결된 사용자에게 임시 비밀번호를 줄 수 없다`() {
        val manager = siteManager(); val target = newUser(); linkSso(target)
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", manager.loginId!!).param("password", "old-pass")) }
        mockMvc.perform(post("/${target.loginId}").param("action", "resetPassword").session(session))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.isSuccess").value(false)).andExpect(jsonPath("$.reason").value("SSO_USER"))
        users.flush()
        assertTrue(passwordIsOld(target.id!!))
    }

    @Test
    fun `SSO로 연결된 사이트 관리자는 비상용 로컬 비밀번호를 재설정할 수 있다`() {
        val manager = siteManager(); linkSso(manager)
        request(manager).andExpect(status().isOk)
        assertEquals(1, events.stream(PasswordResetRequestedEvent::class.java).count(), "비상 로그인용 재설정 메일 이벤트")
    }

    @Test
    fun `SSO와 연결되지 않은 사용자는 그대로 재설정한다`() {
        val user = newUser()
        request(user).andExpect(status().isOk)
        assertEquals(1, events.stream(PasswordResetRequestedEvent::class.java).count())
    }
}
