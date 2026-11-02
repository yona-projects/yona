package dev.yona.web.user

import dev.yona.domain.twofactor.SoftAuthenticator
import dev.yona.domain.twofactor.TwoFactorService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 보안 키(WebAuthn) 등록과 로그인(X-2 7단계, `decisions.md` D-69)의 웹 계약을 검증한다. 소프트웨어 인증기가 브라우저 대신 응답한다. 설정에서 등록하면 백업 코드 화면이 한 번 나오고,
 * 보안 키가 있는 사용자는 로그인의 2단계에서 보안 키로 통과한다. 실패는 코드 입력과 같은 횟수로 센다.
 */
@AutoConfigureMockMvc
@Transactional
class WebAuthnWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var twoFactor: TwoFactorService

    private var n = 0
    private val origin = "http://localhost"

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "wb$k-${System.nanoTime()}", name = "wb$k", email = "wb$k@example.com", passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    /** 설정 화면 절차로 보안 키를 등록한다. 백업 코드 화면을 돌려준다. */
    private fun registerKey(s: MockHttpSession, key: SoftAuthenticator, name: String = "내 키"): String {
        val options = mockMvc.perform(post("/user/two-factor/webauthn/options").session(s)).andExpect(status().isOk).andReturn().response.contentAsString
        mockMvc.perform(post("/user/two-factor/webauthn/register").session(s).param("name", name).param("credential", key.create(options))).andExpect(status().isOk).andExpect(jsonPath("$.ok").value(true))
        return mockMvc.perform(get("/user/two-factor/webauthn/done").session(s)).andReturn().response.contentAsString
    }

    @Test
    fun `설정에서 보안 키를 등록하면 백업 코드 화면이 한 번 나오고 목록에 키가 보인다`() {
        val u = user(); val s = login(u); val key = SoftAuthenticator(origin, "localhost")
        val done = Jsoup.parse(registerKey(s, key))
        assertEquals(10, done.select("#backup-codes li").size)
        mockMvc.perform(get("/user/two-factor/webauthn/done").session(s)).andExpect(status().is3xxRedirection)
        val list = Jsoup.parse(mockMvc.perform(get("/user/editform/two-factor").session(s)).andReturn().response.contentAsString)
        val row = list.select("tr.method-row").single()
        assertEquals("WEBAUTHN", row.attr("data-type")); assertTrue(row.text().contains("내 키"))
    }

    @Test
    fun `등록 요청 없이 또는 변조한 응답으로는 등록하지 못한다`() {
        val s = login(user()); val key = SoftAuthenticator(origin, "localhost")
        mockMvc.perform(post("/user/two-factor/webauthn/register").session(s).param("credential", "{}")).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("twoFactor.securityKey.rejected"))
        val options = mockMvc.perform(post("/user/two-factor/webauthn/options").session(s)).andReturn().response.contentAsString
        mockMvc.perform(post("/user/two-factor/webauthn/register").session(s).param("credential", key.create(options, tamperChallenge = true))).andExpect(status().isBadRequest)
        mockMvc.perform(post("/user/two-factor/webauthn/register").session(s).param("credential", key.create(options))).andExpect(status().isBadRequest)
    }

    @Test
    fun `보안 키가 있는 사용자는 로그인의 2단계에서 보안 키로 통과한다`() {
        val u = user(); val key = SoftAuthenticator(origin, "localhost"); registerKey(login(u), key)
        val s = MockHttpSession()
        mockMvc.perform(post("/users/login").session(s).param("loginIdOrEmail", u.loginId!!).param("password", "1234").param("redirectUrl", "/user/editform")).andExpect(redirectedUrl("/users/login/2fa"))
        val page = Jsoup.parse(mockMvc.perform(get("/users/login/2fa").session(s)).andReturn().response.contentAsString)
        assertNotNull(page.selectFirst("#security-key-use"), "보안 키 버튼")
        val options = mockMvc.perform(post("/users/login/2fa/webauthn/options").session(s)).andExpect(status().isOk).andReturn().response.contentAsString
        mockMvc.perform(post("/users/login/2fa/webauthn").session(s).param("credential", key.get(options))).andExpect(status().isOk).andExpect(jsonPath("$.redirect").value("/user/editform"))
        assertEquals(200, mockMvc.perform(get("/user/editform").session(s)).andReturn().response.status)
    }

    @Test
    fun `보안 키가 없는 사용자의 2단계 화면에는 보안 키 버튼이 없다`() {
        val u = user(); val s = login(u)
        val secret = dev.yona.domain.twofactor.Base32.decode(twoFactor.beginTotp(u, "Yona", u.loginId!!)!!.secret)
        val past = java.time.Instant.now().minusSeconds(300)
        twoFactor.confirmTotp(u, dev.yona.domain.twofactor.Totp.code(secret, past.epochSecond / 30), past)
        val fresh = MockHttpSession()
        mockMvc.perform(post("/users/login").session(fresh).param("loginIdOrEmail", u.loginId!!).param("password", "1234"))
        assertEquals(null, Jsoup.parse(mockMvc.perform(get("/users/login/2fa").session(fresh)).andReturn().response.contentAsString).selectFirst("#security-key-use"))
        mockMvc.perform(post("/users/login/2fa/webauthn/options").session(fresh)).andExpect(status().isForbidden)
    }

    @Test
    fun `다른 사용자의 키나 변조한 응답이나 재사용한 응답은 거부하고 AJAX 로그인은 보안 키 여부를 알린다`() {
        val u = user(); val key = SoftAuthenticator(origin, "localhost"); registerKey(login(u), key)
        val other = user(); val otherKey = SoftAuthenticator(origin, "localhost"); registerKey(login(other), otherKey)
        val s = MockHttpSession()
        mockMvc.perform(post("/users/login").session(s).accept(MediaType.APPLICATION_JSON).param("loginIdOrEmail", u.loginId!!).param("password", "1234"))
            .andExpect(status().isOk).andExpect(jsonPath("$.twoFactor").value(true)).andExpect(jsonPath("$.securityKey").value(true))
        val options = mockMvc.perform(post("/users/login/2fa/webauthn/options").session(s)).andReturn().response.contentAsString
        mockMvc.perform(post("/users/login/2fa/webauthn").session(s).param("credential", otherKey.get(options))).andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("user.twoFactor.invalid"))
        val again = mockMvc.perform(post("/users/login/2fa/webauthn/options").session(s)).andReturn().response.contentAsString
        val good = key.get(again)
        mockMvc.perform(post("/users/login/2fa/webauthn").session(s).param("credential", good)).andExpect(status().isOk)
        val replay = MockHttpSession()
        mockMvc.perform(post("/users/login").session(replay).param("loginIdOrEmail", u.loginId!!).param("password", "1234"))
        mockMvc.perform(post("/users/login/2fa/webauthn/options").session(replay))
        mockMvc.perform(post("/users/login/2fa/webauthn").session(replay).param("credential", good)).andExpect(status().isForbidden)
    }

    @Test
    fun `진행 중인 로그인이 없으면 보안 키 확인은 거부한다`() {
        mockMvc.perform(post("/users/login/2fa/webauthn/options").session(MockHttpSession())).andExpect(status().isForbidden)
        mockMvc.perform(post("/users/login/2fa/webauthn").session(MockHttpSession()).param("credential", "{}")).andExpect(status().isForbidden)
    }
}
