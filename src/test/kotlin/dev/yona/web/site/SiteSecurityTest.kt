package dev.yona.web.site

import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.site.TwoFactorEnforcement
import dev.yona.domain.twofactor.Base32
import dev.yona.domain.twofactor.Totp
import dev.yona.domain.twofactor.TwoFactorService
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사이트 관리의 보안 설정(X-2 6단계, `decisions.md` D-69)을 검증한다: 2단계 인증 강제 방식(끔, 관리자만, 모두)을 관리자 화면에서 바꾸고, 강제 대상이 2단계 인증을 켜지 않았으면
 * 로그인 직후 등록 화면으로 보낸다. 관리자는 사용자 목록에서 사용자의 2단계 인증을 해제할 수 있다.
 */
@AutoConfigureMockMvc
@Transactional
class SiteSecurityTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var admins: SiteAdminRepository
    @Autowired private lateinit var settings: SiteSettingService
    @Autowired private lateinit var twoFactor: TwoFactorService

    private var sequence = 0

    private fun user(admin: Boolean = false): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val u = users.save(User(loginId = "sx$n-${System.nanoTime()}", name = "sx$n", email = "sx$n@example.com", state = dev.yona.domain.enumeration.UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        if (admin) admins.save(SiteAdmin(admin = u))
        return u
    }

    private fun login(u: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    /** 2단계 인증을 켠다. 켠 뒤의 로그인은 코드가 필요하므로 켜기 전에 로그인해 둔 세션을 쓴다. */
    private fun enable(u: User) {
        val secret = Base32.decode(twoFactor.beginTotp(u, "Yona", u.loginId!!)!!.secret)
        val past = Instant.now().minusSeconds(300)
        twoFactor.confirmTotp(u, Totp.code(secret, past.epochSecond / 30), past)
    }

    @Test
    fun `기본 강제 방식은 끔이다`() {
        assertEquals(TwoFactorEnforcement.OFF, settings.twoFactorEnforcement())
    }

    @Test
    fun `보안 화면은 관리자만 열고 현재 방식을 보인다`() {
        mockMvc.perform(get("/sites/security")).andExpect(status().isForbidden)
        mockMvc.perform(get("/sites/security").session(login(user()))).andExpect(status().isForbidden)
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/security").session(login(user(admin = true)))).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(setOf("OFF", "ADMINS", "ALL"), doc.select("form#security-form input[name=twoFactorEnforcement]").map { it.attr("value") }.toSet())
        assertEquals("OFF", doc.selectFirst("input[name=twoFactorEnforcement][checked]")!!.attr("value"))
        assertNotNull(doc.selectFirst("li.active a[href$=/sites/security]"), "사이드바에서 활성이다")
    }

    @Test
    fun `관리자가 강제 방식을 바꾸면 저장되고 잘못된 값은 거부한다`() {
        val s = login(user(admin = true))
        mockMvc.perform(post("/sites/security").session(s).param("twoFactorEnforcement", "ALL")).andExpect(status().is3xxRedirection)
        assertEquals(TwoFactorEnforcement.ALL, settings.twoFactorEnforcement())
        mockMvc.perform(post("/sites/security").session(s).param("twoFactorEnforcement", "EVERYONE")).andExpect(status().isBadRequest)
        assertEquals(TwoFactorEnforcement.ALL, settings.twoFactorEnforcement())
        mockMvc.perform(post("/sites/security").session(login(user())).param("twoFactorEnforcement", "OFF")).andExpect(status().isForbidden)
        assertEquals(TwoFactorEnforcement.ALL, settings.twoFactorEnforcement())
    }

    @Test
    fun `모두 강제하면 2단계 인증이 없는 사용자는 등록 화면으로 가고 허용된 주소는 그대로 열린다`() {
        val u = user(); val s = login(u)
        settings.setTwoFactorEnforcement(TwoFactorEnforcement.ALL)
        mockMvc.perform(get("/").session(s)).andExpect(status().is3xxRedirection).andExpect(header().string("Location", "/user/editform/two-factor"))
        mockMvc.perform(get("/user/editform").session(s)).andExpect(status().is3xxRedirection)
        mockMvc.perform(get("/user/editform/two-factor").session(s)).andExpect(status().isOk)
        mockMvc.perform(post("/user/two-factor/begin").session(s)).andExpect(status().isOk)
        mockMvc.perform(get("/messages.js").session(s)).andExpect(status().isOk)
        mockMvc.perform(get("/logout").session(s)).andExpect(status().is3xxRedirection).andExpect(header().string("Location", org.hamcrest.Matchers.not("/user/editform/two-factor")))
    }

    @Test
    fun `강제로 이동한 등록 화면에는 이유를 알리는 안내가 보인다`() {
        val s = login(user()); settings.setTwoFactorEnforcement(TwoFactorEnforcement.ALL)
        mockMvc.perform(get("/").session(s))
        val page = mockMvc.perform(get("/user/editform/two-factor").session(s)).andReturn().response.contentAsString
        assertTrue(page.contains("requires two-step verification"), "강제 안내 문구")
    }

    @Test
    fun `관리자만 강제하면 관리자는 갇히고 일반 사용자는 그대로 쓴다`() {
        val admin = user(admin = true); val normal = user(); val a = login(admin); val n = login(normal)
        settings.setTwoFactorEnforcement(TwoFactorEnforcement.ADMINS)
        mockMvc.perform(get("/").session(a)).andExpect(status().is3xxRedirection).andExpect(header().string("Location", "/user/editform/two-factor"))
        mockMvc.perform(get("/").session(n)).andExpect(status().isOk)
    }

    @Test
    fun `끔이면 아무도 가로막지 않고 2단계 인증을 켠 사용자는 강제해도 통과한다`() {
        val u = user(); val s = login(u); val done = user(); val d = login(done)
        mockMvc.perform(get("/").session(s)).andExpect(status().isOk)
        enable(done); settings.setTwoFactorEnforcement(TwoFactorEnforcement.ALL)
        mockMvc.perform(get("/").session(d)).andExpect(status().isOk)
    }

    @Test
    fun `익명 요청과 외부 API 토큰 요청은 강제의 대상이 아니다`() {
        settings.setTwoFactorEnforcement(TwoFactorEnforcement.ALL)
        mockMvc.perform(get("/")).andExpect(status().isOk)
        mockMvc.perform(get("/users/loginform")).andExpect(status().isOk)
    }

    @Test
    fun `관리자는 사용자 목록에서 2단계 인증을 해제할 수 있고 버튼은 켠 사용자에게만 보인다`() {
        val admin = user(admin = true); val target = user(); val plain = user(); enable(target)
        val s = login(admin)
        val list = Jsoup.parse(mockMvc.perform(get("/sites/userList").session(s)).andReturn().response.contentAsString)
        assertNotNull(list.selectFirst("[data-request-uri*=resetTwoFactor][data-request-uri*=${target.loginId}]"))
        assertNull(list.selectFirst("[data-request-uri*=resetTwoFactor][data-request-uri*=${plain.loginId}]"))
        mockMvc.perform(post("/sites/resetTwoFactor").session(login(plain)).param("loginId", target.loginId!!)).andExpect(status().isForbidden)
        assertTrue(twoFactor.isEnabled(target))
        mockMvc.perform(post("/sites/resetTwoFactor").session(s).param("loginId", target.loginId!!).param("state", "ACTIVE")).andExpect(status().isOk)
        assertFalse(twoFactor.isEnabled(target))
    }
}
