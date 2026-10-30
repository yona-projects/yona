package dev.yona.web.user

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.EmailValidationRequestedEvent
import dev.yona.domain.user.Email
import dev.yona.domain.user.EmailRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 이메일 설정 탭과 보조 이메일 관리(추가, 삭제, 대표 이메일 지정, 인증 메일, 인증 링크)를 검증한다.
 *
 * 원본: `app/controllers/UserApp.java:1035-1142`, `app/views/user/edit_emails.scala.html`, `app/models/Email.java`.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class EmailSettingsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var sessionUsers: dev.yona.security.SessionUserCache
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var emails: EmailRepository
    @Autowired private lateinit var events: ApplicationEvents

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "ee$n", name = "ee$n", email = "ee$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun sub(user: User, address: String, valid: Boolean, token: String? = null): Email =
        emails.save(Email(user = user, email = address, valid = valid, token = token))

    private fun page(session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get("/user/editform/emails").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    // --- 화면 ---

    @Test
    fun `이메일 탭의 구성`() {
        val user = newUser()
        val verified = sub(user, "verified@example.com", true)
        val pending = sub(user, "pending@example.com", false)
        val page = page(login(user))
        assertTrue(page.select(".page-wrap > ul.nav-tabs li")[3].selectFirst("a")!!.hasClass("active"))
        val form = page.selectFirst("form[action=/user/email]")!!
        assertEquals("post", form.attr("method").lowercase())
        assertEquals("New E-mail address", form.selectFirst("input[name=email]")?.attr("placeholder"))
        assertEquals("Add", form.selectFirst("button")?.text())
        val rows = page.select("table tr")
        assertEquals(3, rows.size)
        assertEquals(user.email, rows[0].selectFirst("strong")?.text())
        assertEquals("Primary email address", rows[0].selectFirst(".label-head")?.text())
        assertEquals("verified@example.com", rows[1].selectFirst("span.ml10")?.text())
        assertEquals("/user/email/delete/${verified.id}", rows[1].selectFirst("button[data-request-method=delete]")?.attr("data-request-uri"))
        assertEquals("/user/email/setAsMain/${verified.id}", rows[1].selectFirst("button[data-request-method=put]")?.attr("href"))
        assertEquals("/user/email/sendValidationEmail/${pending.id}", rows[2].selectFirst("button[data-request-method=post]")?.attr("href"))
        assertEquals("Send a validation email.", rows[2].selectFirst("button[data-request-method=post]")?.text()?.trim())
    }

    // --- 추가 ---

    private fun add(session: MockHttpSession?, address: String?) =
        mockMvc.perform(post("/user/email").apply { session?.let { session(it) } }.apply { address?.let { param("email", it) } })

    @Test
    fun `새 보조 이메일을 인증 전 상태로 추가한다`() {
        val user = newUser()
        add(login(user), "extra@example.com").andExpect(status().isSeeOther).andExpect(header().string("Location", "/user/editform"))
        val saved = emails.findAll().single()
        assertEquals("extra@example.com", saved.email)
        assertEquals(false, saved.valid)
        assertEquals(user.id, saved.user?.id)
    }

    @Test
    fun `형식이 틀리거나 비어 있으면 안내하고 추가하지 않는다`() {
        val session = login(newUser())
        add(session, "not-an-email").andExpect(status().isSeeOther).andExpect(flash().attribute("warning", "error.email"))
        add(session, "").andExpect(flash().attribute("warning", "error.required"))
        add(session, null).andExpect(flash().attribute("warning", "error.required"))
        assertTrue(emails.findAll().isEmpty())
    }

    @Test
    fun `이미 쓰는 이메일이면 안내하고 추가하지 않는다`() {
        val other = newUser()
        val user = newUser()
        sub(user, "mine@example.com", false)
        val session = login(user)
        listOf(other.email!!, user.email!!, "mine@example.com").forEach {
            add(session, it).andExpect(status().isSeeOther).andExpect(flash().attribute("warning", "Email address already exists"))
        }
        assertEquals(1, emails.findAll().size)
    }

    @Test
    fun `이메일 추가는 로그인해야 한다`() {
        add(null, "a@example.com").andExpect(status().isForbidden)
    }

    // --- 삭제 ---

    @Test
    fun `자기 보조 이메일을 지운다`() {
        val user = newUser()
        val email = sub(user, "gone@example.com", true)
        mockMvc.perform(delete("/user/email/delete/${email.id}").session(login(user))).andExpect(status().isSeeOther).andExpect(header().string("Location", "/user/editform"))
        assertTrue(emails.findAll().isEmpty())
    }

    @Test
    fun `남의 이메일은 지울 수 없고 없거나 익명이면 403이다`() {
        val owner = newUser()
        val email = sub(owner, "keep@example.com", true)
        mockMvc.perform(delete("/user/email/delete/${email.id}").session(login(newUser()))).andExpect(status().isForbidden)
        mockMvc.perform(delete("/user/email/delete/${email.id}")).andExpect(status().isForbidden)
        mockMvc.perform(delete("/user/email/delete/99999").session(login(owner))).andExpect(status().isForbidden)
        assertEquals(1, emails.findAll().size)
    }

    // --- 대표 이메일 ---

    @Test
    fun `보조 이메일을 대표 이메일로 바꾸면 이전 대표 이메일이 인증된 보조 이메일이 된다`() {
        val user = newUser()
        val oldMain = user.email!!
        val email = sub(user, "next-main@example.com", true)
        mockMvc.perform(put("/user/email/setAsMain/${email.id}").session(login(user))).andExpect(status().isSeeOther).andExpect(header().string("Location", "/user/editform"))
        users.flush()
        assertEquals("next-main@example.com", users.findById(user.id!!).get().email)
        val remaining = emails.findAll().single()
        assertEquals(oldMain, remaining.email)
        assertEquals(true, remaining.valid)
    }

    @Test
    fun `대표 이메일을 바꾸면 서버 캐시 항목이 새것으로 바뀐다 (v1_6 removeEmail의 yonaUsers_put)`() {
        val user = newUser()
        val email = sub(user, "cache-main@example.com", true)
        sessionUsers.remember(user.id!!)
        val before = sessionUsers.memoOf(user.id!!)
        mockMvc.perform(put("/user/email/setAsMain/${email.id}").session(login(user))).andExpect(status().isSeeOther)
        org.junit.jupiter.api.Assertions.assertNotSame(before, sessionUsers.memoOf(user.id!!))
    }

    @Test
    fun `인증 전 이메일도 대표 이메일로 바꿀 수 있다 (v1 6 동작)`() {
        val user = newUser()
        val email = sub(user, "unverified@example.com", false)
        mockMvc.perform(put("/user/email/setAsMain/${email.id}").session(login(user))).andExpect(status().isSeeOther)
        users.flush()
        assertEquals("unverified@example.com", users.findById(user.id!!).get().email)
    }

    @Test
    fun `남의 이메일은 대표로 바꿀 수 없다`() {
        val owner = newUser()
        val email = sub(owner, "foreign@example.com", true)
        mockMvc.perform(put("/user/email/setAsMain/${email.id}").session(login(newUser()))).andExpect(status().isForbidden)
        mockMvc.perform(put("/user/email/setAsMain/${email.id}")).andExpect(status().isForbidden)
    }

    // --- 인증 메일과 링크 ---

    @Test
    fun `인증 메일을 요청하면 숫자 50자 토큰을 만들고 이벤트를 알린다`() {
        val user = newUser()
        val email = sub(user, "check@example.com", false)
        mockMvc.perform(post("/user/email/sendValidationEmail/${email.id}").session(login(user)))
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/user/editform"))
            .andExpect(flash().attribute("warning", "확인 메일을 전송했습니다."))
        emails.flush()
        val saved = emails.findById(email.id!!).get()
        assertEquals(50, saved.token!!.length)
        assertTrue(saved.token!!.all { it.isDigit() })
        val event = events.stream(EmailValidationRequestedEvent::class.java).toList().single()
        assertEquals("check@example.com", event.email.email)
        assertEquals("/user/email/confirm/${email.id}/${saved.token}", event.confirmPath)
    }

    @Test
    fun `남의 이메일의 인증 메일은 요청할 수 없다`() {
        val owner = newUser()
        val email = sub(owner, "foreign@example.com", false)
        mockMvc.perform(post("/user/email/sendValidationEmail/${email.id}").session(login(newUser()))).andExpect(status().isForbidden)
        assertEquals(0, events.stream(EmailValidationRequestedEvent::class.java).count())
    }

    @Test
    fun `인증 링크를 열면 인증되고 그 사용자로 로그인된다`() {
        val user = newUser()
        val email = sub(user, "confirm@example.com", false, token = "12345")
        val session = MockHttpSession()
        mockMvc.perform(get("/user/email/confirm/${email.id}/12345").session(session))
            .andExpect(status().isSeeOther).andExpect(header().string("Location", "/user/editform"))
        emails.flush()
        assertEquals(true, emails.findById(email.id!!).get().valid)
        mockMvc.perform(get("/user/editform").session(session)).andExpect(status().isOk)
    }

    @Test
    fun `토큰이 틀리거나 없는 이메일이면 403이다`() {
        val user = newUser()
        val email = sub(user, "no@example.com", false, token = "12345")
        mockMvc.perform(get("/user/email/confirm/${email.id}/wrong")).andExpect(status().isForbidden)
        mockMvc.perform(get("/user/email/confirm/99999/12345")).andExpect(status().isForbidden)
        emails.flush()
        assertFalse(emails.findById(email.id!!).get().valid == true)
        assertNotNull(user.id)
    }
}
