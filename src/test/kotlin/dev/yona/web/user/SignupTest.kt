package dev.yona.web.user

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.UserVerificationRequestedEvent
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.user.Email
import dev.yona.domain.user.EmailRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.domain.user.UserVerification
import dev.yona.domain.user.UserVerificationRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 가입 화면(`GET /users/signupform`), 가입(`POST /users/signup`), 이메일 인증(`GET /verify/:loginId/:code`),
 * 사용 여부 확인(`GET /user/isUsed`, `/user/isEmailExist`)을 검증한다.
 *
 * 원본: `app/controllers/UserApp.java:369-405, 478-495, 1008-1024, 1237-1289`, `app/views/user/signup.scala.html`,
 * `verified.scala.html`, `app/models/UserVerification.java`.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class SignupTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var emails: EmailRepository
    @Autowired private lateinit var verifications: UserVerificationRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var events: ApplicationEvents

    private var sequence = 0

    private fun existingUser(email: String? = null, loginId: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = loginId ?: "su$n", name = "su$n", email = email ?: "su$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun signup(
        loginId: String? = "newbie${++sequence}", name: String? = "신규", email: String? = "newbie$sequence@example.com",
        password: String? = "secret", session: MockHttpSession? = null
    ): MvcResult {
        val request = post("/users/signup")
        session?.let { request.session(it) }
        loginId?.let { request.param("loginId", it) }
        name?.let { request.param("name", it) }
        email?.let { request.param("email", it) }
        password?.let { request.param("password", it) }
        return mockMvc.perform(request).andReturn()
    }

    private fun doc(result: MvcResult): Document = Jsoup.parse(result.response.contentAsString)

    // --- 화면 ---

    @Test
    fun `가입 화면의 구성`() {
        val page = Jsoup.parse(mockMvc.perform(get("/users/signupform")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("Sign up", page.title())
        assertEquals("Sign up for Yona", page.selectFirst("h1.title")?.text())
        assertEquals("Web-based platform for collaborative software development", page.selectFirst("p.tag-line")?.text())
        val form = page.selectFirst("form[name=signup]")!!
        assertEquals("/users/signup", form.attr("action"))
        assertEquals("post", form.attr("method").lowercase())
        assertEquals(listOf("loginId", "name", "email", "password", "retypedPassword"), form.select("input").map { it.attr("name") })
        assertEquals("User ID (lower case)", form.selectFirst("label[for=loginId]")?.text())
        assertEquals("Sign up", form.selectFirst("button[type=submit]")?.text())
        assertEquals("/users/loginform", form.selectFirst("a.go-login")?.attr("href"))
        assertTrue(page.select("script").any { it.attr("src").endsWith("/assets/javascripts/lib/validate.js") })
        val script = page.select("script").map { it.data().replace("\\/", "/") }.first { "user.SignUp" in it }
        assertTrue("\"sLogindIdCheckUrl\"" in script && "/user/isUsed?name=" in script && "/user/isEmailExist?email=" in script, script)
    }

    @Test
    fun `로그인한 사용자는 가입 화면 대신 홈으로 간다`() {
        val user = existingUser()
        val session = MockHttpSession().also {
            mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
        }
        mockMvc.perform(get("/users/signupform").session(session)).andExpect(status().isSeeOther).andExpect(header().string("Location", "/"))
    }

    // --- 가입 ---

    @Test
    fun `가입하면 활성 사용자가 만들어지고 로그인된 채 홈으로 간다`() {
        val session = MockHttpSession()
        val result = signup("alice-new", "앨리스", "alice-new@example.com", "pw1234", session)
        assertEquals(303, result.response.status)
        assertEquals("/", result.response.getHeader("Location"))
        val user = users.findByLoginId("alice-new")!!
        assertEquals(UserState.ACTIVE, user.state)
        assertEquals("앨리스", user.name)
        assertNotNull(user.createdDate)
        assertEquals(LegacyPasswordHasher.hash("pw1234", user.passwordSalt!!), user.password)
        // 로그인된 상태이다.
        val home = mockMvc.perform(get("/").session(session)).andReturn().response.contentAsString
        assertTrue(home.contains("alice-new") || home.contains("앨리스"))
    }

    @Test
    fun `이름의 위험한 태그는 정화한다`() {
        signup("sanitized", "<script>x</script><b>굵게</b>", "sanitized@example.com")
        assertEquals("<b>굵게</b>", users.findByLoginId("sanitized")!!.name)
    }

    @Test
    fun `폼이 보내지 않는 항목은 요청으로 정할 수 없다`() {
        mockMvc.perform(
            post("/users/signup").param("loginId", "tamper").param("name", "n").param("email", "tamper@example.com")
                .param("password", "x").param("state", "SITE_ADMIN").param("isGuest", "true").param("token", "stolen")
        )
        val user = users.findByLoginId("tamper")!!
        assertEquals(UserState.ACTIVE, user.state)
        assertNull(user.token)
        assertNotEquals(true, user.isGuest)
    }

    private fun assertRejected(result: MvcResult) {
        assertEquals(400, result.response.status)
        assertEquals("Sign up", doc(result).title())
        assertNotNull(doc(result).selectFirst("form[name=signup]"))
    }

    @Test
    fun `로그인 ID가 비었거나 형식이 틀리거나 예약어이면 거부한다`() {
        listOf("", "   ", "has space", "bad!id", "Projects").forEach { assertRejected(signup(loginId = it, email = "x${++sequence}@example.com")) }
        assertRejected(signup(loginId = null, email = "y${++sequence}@example.com"))
    }

    @Test
    fun `비밀번호가 비었으면 거부한다`() {
        assertRejected(signup(password = "  "))
        assertRejected(signup(password = null))
    }

    @Test
    fun `이메일 형식이 틀리면 거부한다`() {
        assertRejected(signup(email = "not-an-email"))
    }

    @Test
    fun `이미 있는 로그인 ID나 조직 이름이면 거부한다`() {
        existingUser(loginId = "takenid")
        organizations.save(Organization(name = "takenorg", created = Date()))
        assertRejected(signup(loginId = "TakenId"))
        assertRejected(signup(loginId = "takenorg"))
    }

    @Test
    fun `이미 쓰는 이메일이면 거부한다`() {
        existingUser(email = "dup@example.com")
        assertRejected(signup(email = "DUP@example.com"))
        val owner = existingUser()
        emails.save(Email(user = owner, email = "second@example.com", valid = true))
        assertRejected(signup(email = "second@example.com"))
    }

    @Test
    fun `거부된 가입은 입력을 채우지 않은 빈 폼을 보여 준다 (v1 6 동작)`() {
        val page = doc(signup(loginId = "has space", name = "내이름"))
        assertEquals("", page.selectFirst("input#uname")?.attr("value").orEmpty())
        assertEquals("", page.selectFirst("input#loginId")?.attr("value").orEmpty())
    }

    @Test
    fun `인증되지 않은 오래된 이메일 행은 가입하면 지운다`() {
        val other = existingUser()
        emails.save(Email(user = other, email = "reuse@example.com", valid = false, token = "t"))
        signup("reuser", "n", "reuse@example.com")
        assertTrue(emails.findAll().none { it.email == "reuse@example.com" && it.valid != true })
    }

    // --- 사용 여부 확인 ---

    @Test
    fun `로그인 ID 사용 여부와 예약어 여부를 JSON으로 알린다`() {
        existingUser(loginId = "usedid")
        organizations.save(Organization(name = "usedorg", created = Date()))
        mockMvc.perform(get("/user/isUsed").param("name", "UsedId")).andExpect(status().isOk).andExpect(jsonPath("$.isExist").value(true)).andExpect(jsonPath("$.isReserved").value(false))
        mockMvc.perform(get("/user/isUsed").param("name", "usedorg")).andExpect(jsonPath("$.isExist").value(true))
        mockMvc.perform(get("/user/isUsed").param("name", "freeid")).andExpect(jsonPath("$.isExist").value(false))
        mockMvc.perform(get("/user/isUsed").param("name", "Projects")).andExpect(jsonPath("$.isReserved").value(true))
    }

    @Test
    fun `이메일 사용 여부를 JSON으로 알린다`() {
        existingUser(email = "used@example.com")
        val owner = existingUser()
        emails.save(Email(user = owner, email = "extra@example.com", valid = true))
        emails.save(Email(user = owner, email = "pending@example.com", valid = false))
        fun ask(email: String) = get("/user/isEmailExist").param("email", email).contentType("application/json").content("{}")
        mockMvc.perform(ask("USED@example.com")).andExpect(jsonPath("$.isExist").value(true))
        mockMvc.perform(ask("extra@example.com")).andExpect(jsonPath("$.isExist").value(true))
        mockMvc.perform(ask("pending@example.com")).andExpect(jsonPath("$.isExist").value(false))
        mockMvc.perform(ask("free@example.com")).andExpect(jsonPath("$.isExist").value(false))
    }

    @Test
    fun `이메일 사용 여부는 JSON 내용 형식일 때만 본문이 JSON이어야 한다 (공식 v1_16_0 실행 결과)`() {
        existingUser(email = "used2@example.com")
        // 내용 형식이 없거나 JSON이 아니면 본문을 해석하지 않는다
        mockMvc.perform(get("/user/isEmailExist").param("email", "used2@example.com")).andExpect(jsonPath("$.isExist").value(true))
        mockMvc.perform(get("/user/isEmailExist").param("email", "used2@example.com").contentType("text/plain").content("{}")).andExpect(jsonPath("$.isExist").value(true))
        mockMvc.perform(get("/user/isEmailExist").param("email", "used2@example.com").contentType("application/x-www-form-urlencoded").content("a=b")).andExpect(jsonPath("$.isExist").value(true))
        // JSON 내용 형식이면 본문이 JSON이어야 한다
        mockMvc.perform(get("/user/isEmailExist").param("email", "used2@example.com").contentType("application/json")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/user/isEmailExist").param("email", "used2@example.com").contentType("application/json").content("{bad")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/user/isEmailExist").param("email", "used2@example.com").contentType("text/json").content("{}")).andExpect(jsonPath("$.isExist").value(true))
        mockMvc.perform(get("/user/isEmailExist").param("email", "used2@example.com").contentType("application/json").content("[1]")).andExpect(jsonPath("$.isExist").value(true))
    }

    @Test
    fun `이메일 사용 여부는 이메일 인자가 없으면 400이다`() {
        mockMvc.perform(get("/user/isEmailExist")).andExpect(status().isBadRequest)
    }

    // --- 인증 링크 ---

    private fun lockedUserWithVerification(timestamp: Long = System.currentTimeMillis(), code: String = "code-123"): User {
        val user = existingUser().also { it.state = UserState.LOCKED; users.save(it) }
        verifications.save(UserVerification(user = user, loginId = user.loginId, verificationCode = code, timestamp = timestamp))
        return user
    }

    @Test
    fun `인증 링크를 열면 사용자가 활성화되고 인증 정보가 지워진다`() {
        val user = lockedUserWithVerification()
        val page = Jsoup.parse(mockMvc.perform(get("/verify/${user.loginId}/code-123")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("Verified User", page.selectFirst("h1.title")?.text())
        assertEquals(user.loginId, page.select(".reset-password p")[0].text())
        assertEquals("User is verified. Try logging in.", page.selectFirst("p.tag-line")?.text())
        users.flush()
        assertEquals(UserState.ACTIVE, users.findById(user.id!!).get().state)
        assertTrue(verifications.findAll().isEmpty())
    }

    @Test
    fun `잘못된 코드나 하루가 지난 코드는 404이다`() {
        val user = lockedUserWithVerification()
        mockMvc.perform(get("/verify/${user.loginId}/wrong")).andExpect(status().isNotFound)
        val old = lockedUserWithVerification(timestamp = System.currentTimeMillis() - 25L * 3_600_000, code = "old-code")
        mockMvc.perform(get("/verify/${old.loginId}/old-code")).andExpect(status().isNotFound)
        users.flush()
        assertEquals(UserState.LOCKED, users.findById(old.id!!).get().state)
    }

    @Test
    fun `로그인한 사용자는 인증 링크 대신 홈으로 간다`() {
        val user = lockedUserWithVerification()
        val other = existingUser()
        val session = MockHttpSession().also {
            mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", other.loginId!!).param("password", "1234"))
        }
        mockMvc.perform(get("/verify/${user.loginId}/code-123").session(session)).andExpect(status().isSeeOther).andExpect(header().string("Location", "/"))
        assertNotNull(events)
    }
}

/** 이메일 인증을 켠 설정에서의 가입을 검증한다 (`application.use.email.verification`). */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@TestPropertySource(properties = ["yona.use-email-verification=true"])
class SignupWithEmailVerificationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var verifications: UserVerificationRepository
    @Autowired private lateinit var events: ApplicationEvents

    @Test
    fun `가입하면 잠긴 사용자와 인증 정보가 만들어지고 인증 메일 요청이 알려진다`() {
        val session = MockHttpSession()
        mockMvc.perform(post("/users/signup").session(session).param("loginId", "verifyme").param("name", "n").param("email", "verifyme@example.com").param("password", "x"))
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/"))
            .andExpect(flash().attribute("info", "user.verification.mail.sent"))
        val user = users.findByLoginId("verifyme")!!
        assertEquals(UserState.LOCKED, user.state)
        val verification = verifications.findAll().single()
        assertEquals(user.id, verification.user?.id)
        assertEquals(36, verification.verificationCode!!.length)
        assertEquals(1, events.stream(UserVerificationRequestedEvent::class.java).count())
        // 잠긴 사용자는 로그인되지 않는다.
        val home = mockMvc.perform(get("/").session(session)).andReturn().response.contentAsString
        assertTrue(!home.contains("verifyme"))
    }
}

/** 관리자 승인제를 켠 설정에서의 가입을 검증한다 (`signup.require.admin.confirm`). */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.signup.require-admin-confirm=true"])
class SignupWithAdminConfirmTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository

    @Test
    fun `가입하면 잠기고 승인 대기 안내를 받는다`() {
        mockMvc.perform(post("/users/signup").param("loginId", "pending").param("name", "n").param("email", "pending@example.com").param("password", "x"))
            .andExpect(status().isSeeOther)
            .andExpect(flash().attribute("info", "user.signup.requested"))
        assertEquals(UserState.LOCKED, users.findByLoginId("pending")!!.state)
    }

    @Test
    fun `가입 화면에 승인 안내와 관리자 이메일이 거꾸로 보인다`() {
        val page = Jsoup.parse(mockMvc.perform(get("/users/signupform")).andReturn().response.contentAsString)
        val texts = page.select(".center-txt p").map { it.text() }
        assertEquals("Administrator admission is required for activation.", texts[0])
        val admin = users.findByLoginId("admin")!!
        assertEquals("If needed, please contact ${admin.email!!.reversed()}", texts[1])
        assertNotNull(page.selectFirst(".obfuscate"))
    }
}

/** 허용 이메일 도메인을 정한 설정에서의 가입을 검증한다 (`application.allowed.sending.mail.domains`). */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.allowed-sending-mail-domains=allowed.com, other.org"])
class SignupWithAllowedDomainsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository

    @Test
    fun `허용되지 않은 도메인은 안내와 함께 400으로 거부한다`() {
        val result = mockMvc.perform(post("/users/signup").param("loginId", "outsider").param("name", "n").param("email", "x@blocked.net").param("password", "x")).andReturn()
        assertEquals(400, result.response.status)
        assertTrue(result.response.contentAsString.contains("Your email domain is not allowed."))
        assertNull(users.findByLoginId("outsider"))
    }

    @Test
    fun `허용된 도메인은 대소문자와 공백을 가리지 않고 가입한다`() {
        mockMvc.perform(post("/users/signup").param("loginId", "insider").param("name", "n").param("email", "x@OTHER.org").param("password", "x"))
            .andExpect(status().isSeeOther)
        assertNotNull(users.findByLoginId("insider"))
    }
}
