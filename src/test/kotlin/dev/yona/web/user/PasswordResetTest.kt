package dev.yona.web.user

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.PasswordResetRequestedEvent
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.security.LocalAuthenticator
import dev.yona.security.PasswordResetTokens
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 비밀번호 재설정 요청(`/lostPassword`), 재설정(`/resetPassword`), 사이트 관리자의 재설정(`POST /:user?action=resetPassword`)을 검증한다.
 *
 * 원본: `app/controllers/PasswordResetApp.java`, `app/utils/PasswordReset.java`, `app/views/site/lostPassword.scala.html`,
 * `app/views/user/resetPassword.scala.html`, `app/controllers/UserApp.java:1338-1362`, `app/models/User.java:519-533`.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class PasswordResetTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var tokens: PasswordResetTokens
    @Autowired private lateinit var authenticator: LocalAuthenticator
    @Autowired private lateinit var events: ApplicationEvents
    @MockitoBean private lateinit var mailSender: org.springframework.mail.javamail.JavaMailSender

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "pr$n", name = "pr$n", email = "pr$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("old-pass", salt)
            )
        )
    }

    private fun login(user: User, password: String = "old-pass"): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", password))
    }

    // --- 요청 화면 ---

    @Test
    fun `요청 화면의 구성`() {
        val page = Jsoup.parse(mockMvc.perform(get("/lostPassword")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("Password reset request", page.title())
        assertEquals("Reset password for Yona", page.selectFirst("h1.title")?.text())
        val form = page.selectFirst("form[action=/lostPassword]")!!
        assertEquals("post", form.attr("method").lowercase())
        assertTrue(form.selectFirst("input#loginId[name=loginId]")!!.hasAttr("required"))
        assertNotNull(form.selectFirst("input#emailAddress[name=emailAddress]"))
        assertEquals("Confirm", form.selectFirst("button")?.text())
        assertNull(page.selectFirst(".alert"))
    }

    @Test
    fun `로그인한 사용자는 자기 정보로 입력란이 채워진다`() {
        val user = newUser()
        val page = Jsoup.parse(mockMvc.perform(get("/lostPassword").session(login(user))).andReturn().response.contentAsString)
        assertEquals(user.loginId, page.selectFirst("input#loginId")?.attr("value"))
        assertEquals(user.email, page.selectFirst("input#emailAddress")?.attr("value"))
    }

    // --- 요청 ---

    private fun request(loginId: String?, email: String?) = mockMvc.perform(
        post("/lostPassword").apply { loginId?.let { param("loginId", it) } }.apply { email?.let { param("emailAddress", it) } }
    ).andExpect(status().isOk).andReturn().response.contentAsString

    @Test
    fun `아이디와 이메일이 맞으면 재설정 메일 요청을 알리고 성공 안내를 보여 준다`() {
        val user = newUser()
        val page = Jsoup.parse(request(user.loginId, user.email))
        assertEquals("Mail has been sent.", page.selectFirst(".alert-success h4")?.text())
        assertNotNull(page.selectFirst(".alert-success"))
        assertNull(page.selectFirst(".alert-error"))
        val event = events.stream(PasswordResetRequestedEvent::class.java).toList().single()
        assertEquals(user.id, event.user.id)
        assertEquals(40, event.token.length)
        assertTrue(event.token.all { it in "0123456789abcdef" })
        assertTrue(tokens.isValid(event.token))
    }

    @Test
    fun `메일을 보내지 못하면 성공 안내를 보이지 않고 오류 문구도 없이 폼만 다시 보여 준다`() {
        org.mockito.Mockito.doThrow(org.springframework.mail.MailSendException("down")).`when`(mailSender).send(org.mockito.ArgumentMatchers.any(jakarta.mail.internet.MimeMessage::class.java))
        val user = newUser()
        val page = Jsoup.parse(request(user.loginId, user.email))
        // v1.6 sendPasswordResetMail은 EmailException이면 false를 돌려주고 errorMessage는 null이다.
        assertNull(page.selectFirst(".alert-success"))
        assertNull(page.selectFirst(".alert-error"))
        assertNotNull(page.selectFirst("input#emailAddress"))
    }

    @Test
    fun `아이디나 이메일이 틀리면 오류 안내를 보여 주고 아무것도 보내지 않는다`() {
        val user = newUser()
        listOf(user.loginId to "other@example.com", "nobody-here" to user.email, user.loginId to user.email!!.uppercase(), null to null).forEach { (id, email) ->
            val page = Jsoup.parse(request(id, email))
            assertEquals("Invalid password reset request", page.selectFirst(".alert-error")?.ownText()?.trim())
            assertNull(page.selectFirst(".alert-success"))
        }
        assertEquals(0, events.stream(PasswordResetRequestedEvent::class.java).count())
    }

    @Test
    fun `새로 요청하면 이전 요청의 주소는 쓸 수 없다`() {
        val user = newUser()
        request(user.loginId, user.email)
        val first = events.stream(PasswordResetRequestedEvent::class.java).toList().single().token
        request(user.loginId, user.email)
        val second = events.stream(PasswordResetRequestedEvent::class.java).toList().last().token
        assertTrue(!tokens.isValid(first))
        assertTrue(tokens.isValid(second))
    }

    // --- 재설정 ---

    @Test
    fun `재설정 화면의 구성`() {
        val page = Jsoup.parse(mockMvc.perform(get("/resetPassword").param("s", "abc123")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("Reset password", page.title())
        val form = page.selectFirst("form[name=passwordReset]")!!
        assertEquals("/resetPassword", form.attr("action"))
        assertEquals("abc123", form.selectFirst("input[type=hidden][name=hashString]")?.attr("value"))
        assertEquals("Password", form.selectFirst("input#password")?.attr("placeholder"))
        assertEquals("Password confirmation", form.selectFirst("input#retypedPassword")?.attr("placeholder"))
        assertTrue(page.select("script").any { "resetPassword" in it.data() })
        mockMvc.perform(get("/resetPassword")).andExpect(status().isBadRequest)
    }

    @Test
    fun `맞는 주소로 재설정하면 새 비밀번호로 로그인된다`() {
        val user = newUser()
        request(user.loginId, user.email)
        val token = events.stream(PasswordResetRequestedEvent::class.java).toList().single().token
        val body = mockMvc.perform(post("/resetPassword").param("hashString", token).param("password", "brand-new"))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val page = Jsoup.parse(body)
        assertNotNull(page.selectFirst("form[action=/users/login]"))
        assertTrue(body.contains("Please log in with the new password!"))
        users.flush()
        val saved = users.findById(user.id!!).get()
        assertEquals(LegacyPasswordHasher.hash("brand-new", saved.passwordSalt!!), saved.password)
        assertTrue(!tokens.isValid(token))
    }

    @Test
    fun `잘못되었거나 이미 쓴 주소는 400이다`() {
        val user = newUser()
        request(user.loginId, user.email)
        val token = events.stream(PasswordResetRequestedEvent::class.java).toList().single().token
        mockMvc.perform(post("/resetPassword").param("hashString", "bogus").param("password", "x")).andExpect(status().isBadRequest)
        mockMvc.perform(post("/resetPassword").param("password", "x")).andExpect(status().isBadRequest)
        mockMvc.perform(post("/resetPassword").param("hashString", token).param("password", "x")).andExpect(status().isOk)
        val again = mockMvc.perform(post("/resetPassword").param("hashString", token).param("password", "y")).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(again.contains("Wrong url to reset password."))
    }

    @Test
    fun `한 시간이 지난 주소는 쓸 수 없다`() {
        val user = newUser()
        val token = tokens.issue(user.loginId!!, now = System.currentTimeMillis() - 3_600_001L)
        assertTrue(!tokens.isValid(token))
        mockMvc.perform(post("/resetPassword").param("hashString", token).param("password", "x")).andExpect(status().isBadRequest)
        assertNotNull(authenticator)
    }

    // --- 사이트 관리자의 재설정 ---

    private fun siteManager(): User = newUser().also { siteAdmins.save(SiteAdmin(admin = it)) }

    @Test
    fun `사이트 관리자는 사용자의 비밀번호를 여섯 글자 임시 비밀번호로 바꾼다`() {
        val manager = siteManager()
        val target = newUser()
        val body = mockMvc.perform(post("/${target.loginId}").param("action", "resetPassword").session(login(manager)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.loginId").value(target.loginId))
            .andExpect(jsonPath("$.name").value(target.name))
            .andReturn().response.contentAsString
        val newPassword = Regex("\"newPassword\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        assertEquals(6, newPassword.length)
        users.flush()
        val saved = users.findById(target.id!!).get()
        assertEquals(LegacyPasswordHasher.hash(newPassword, saved.passwordSalt!!), saved.password)
    }

    @Test
    fun `사이트 관리자가 아니거나 없는 사용자이면 403이다`() {
        val target = newUser()
        mockMvc.perform(post("/${target.loginId}").param("action", "resetPassword").session(login(newUser())))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.isSuccess").value(false)).andExpect(jsonPath("$.reason").value("FORBIDDEN"))
        mockMvc.perform(post("/nobody-here").param("action", "resetPassword").session(login(siteManager())))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.reason").value("FORBIDDEN"))
        mockMvc.perform(post("/${target.loginId}").param("action", "resetPassword"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `action이 다르면 400이다`() {
        val manager = siteManager()
        val target = newUser()
        mockMvc.perform(post("/${target.loginId}").param("action", "other").session(login(manager)))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.reason").value("BAD_REQUEST"))
        mockMvc.perform(post("/${target.loginId}").session(login(manager))).andExpect(status().isBadRequest)
    }
}
