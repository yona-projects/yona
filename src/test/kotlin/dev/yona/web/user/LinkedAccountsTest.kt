package dev.yona.web.user

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.sso.OidcSsoForm
import dev.yona.domain.sso.SsoSettingsService
import dev.yona.domain.user.LinkedAccount
import dev.yona.domain.user.LinkedAccountRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserCredential
import dev.yona.domain.user.UserCredentialRepository
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.FakeOidcIdp
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사용자 설정의 "연결된 계정" 화면과 관리자 연결 해제(X-16 단계 5b, `decisions.md` D-80 18, 22번)를 검증한다. 본인은 자기 연결을 보고 해제하며(SSO 전용 모드가 꺼져 있을 때만),
 * 사이트 관리자는 다른 사용자의 SSO 연결을 해제한다. 해제는 감사 기록(`auth.sso_unlinked`)에 남는다.
 */
@AutoConfigureMockMvc
@Transactional
class LinkedAccountsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var admins: SiteAdminRepository
    @Autowired private lateinit var credentials: UserCredentialRepository
    @Autowired private lateinit var linked: LinkedAccountRepository
    @Autowired private lateinit var sso: SsoSettingsService
    @Autowired private lateinit var siteSettings: SiteSettingService
    @Autowired private lateinit var logs: dev.yona.domain.audit.AuditLogRepository

    private val idp = FakeOidcIdp()
    private var n = 0

    @AfterEach
    fun stop() { idp.close() }

    private fun user(admin: Boolean = false): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        val u = users.save(User(loginId = "la$k-${System.nanoTime()}", name = "la$k", email = "la$k-${System.nanoTime()}@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        if (admin) admins.save(SiteAdmin(admin = u))
        return u
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun link(u: User, provider: String, id: String): LinkedAccount {
        val credential = credentials.findByUserId(u.id!!).firstOrNull() ?: credentials.save(UserCredential(user = u, loginId = u.loginId, email = u.email, name = u.name, active = true, emailValidated = true))
        return linked.save(LinkedAccount(userCredential = credential, providerKey = provider, providerUserId = id))
    }

    @Test
    fun `로그인해야 열 수 있다`() {
        assertTrue(mockMvc.perform(get("/user/editform/linked-accounts")).andReturn().response.status != 200)
    }

    @Test
    fun `내 연결 계정을 제공자별로 보이고 식별자는 일부만 보인다`() {
        val u = user(); link(u, "sso-oidc", "https://idp.example/realms/corp|abcdef123456"); link(u, "github", "9876543210")
        val doc = Jsoup.parse(mockMvc.perform(get("/user/editform/linked-accounts").session(login(u))).andReturn().response.contentAsString)
        val rows = doc.select("tr.linked-row")
        assertEquals(2, rows.size)
        assertEquals("sso-oidc", rows.first { it.attr("data-provider") == "sso-oidc" }.attr("data-provider"))
        assertFalse(doc.text().contains("abcdef123456")); assertFalse(doc.text().contains("9876543210"))
        assertNotNull(doc.selectFirst("ul.nav-tabs a[href\$=linked-accounts]"))
    }

    @Test
    fun `다른 사용자의 연결은 보이지 않는다`() {
        val a = user(); val b = user(); link(b, "sso-saml2", "https://idp|subject-b-12345")
        assertTrue(Jsoup.parse(mockMvc.perform(get("/user/editform/linked-accounts").session(login(a))).andReturn().response.contentAsString).select("tr.linked-row").isEmpty())
    }

    @Test
    fun `본인이 연결을 해제하면 지워지고 감사 기록에 남는다`() {
        val u = user(); val account = link(u, "sso-oidc", "https://idp|subject-1234567"); val session = login(u)
        val response = mockMvc.perform(post("/user/linked-accounts/${account.id}/unlink").session(session)).andReturn().response
        assertTrue(response.status in 300..399)
        assertNull(linked.findById(account.id!!).orElse(null))
        assertTrue(logs.findAll().any { it.action == "auth.sso_unlinked" && it.details!!.contains("self") })
    }

    @Test
    fun `다른 사용자의 연결은 해제할 수 없다`() {
        val a = user(); val b = user(); val account = link(b, "sso-oidc", "https://idp|subject-b-7654321")
        mockMvc.perform(post("/user/linked-accounts/${account.id}/unlink").session(login(a)))
        assertNotNull(linked.findById(account.id!!).orElse(null))
    }

    @Test
    fun `SSO 전용 모드에서는 본인이 해제할 수 없다`() {
        val u = user(); val account = link(u, "sso-oidc", "https://idp|subject-1111111")
        sso.saveOidc(OidcSsoForm(true, "회사", idp.issuer, idp.clientId, idp.clientSecret, "openid", "email", "name", "", true, true, false, false, false, 60), null)
        siteSettings.setSsoOnly(true)
        val session = MockHttpSession()
        // 전용 모드에서 일반 사용자는 로컬 로그인이 막히므로 관리자 세션이 아닌 본인 세션을 직접 만든다
        val admin = user(admin = true); val adminSession = login(admin)
        mockMvc.perform(post("/user/linked-accounts/${account.id}/unlink").session(session))
        assertNotNull(linked.findById(account.id!!).orElse(null))
        // 관리자는 전용 모드여도 해제할 수 있다
        mockMvc.perform(post("/sites/unlinkSso").session(adminSession).param("loginId", u.loginId!!))
        assertNull(linked.findById(account.id!!).orElse(null))
    }

    @Test
    fun `관리자는 사용자의 SSO 연결을 모두 해제하고 소셜 연결은 남긴다`() {
        val u = user(); val a = link(u, "sso-oidc", "https://idp|subject-2222222"); val b = link(u, "sso-saml2", "https://idp2|subject-3333333"); val c = link(u, "github", "5555555")
        val adminSession = login(user(admin = true))
        assertTrue(mockMvc.perform(post("/sites/unlinkSso").session(adminSession).param("loginId", u.loginId!!)).andReturn().response.status in 200..399)
        assertNull(linked.findById(a.id!!).orElse(null)); assertNull(linked.findById(b.id!!).orElse(null)); assertNotNull(linked.findById(c.id!!).orElse(null))
        assertTrue(logs.findAll().any { it.action == "auth.sso_unlinked" && it.details!!.contains("admin") })
    }

    @Test
    fun `관리자가 아니면 SSO 연결을 해제할 수 없다`() {
        val u = user(); val a = link(u, "sso-oidc", "https://idp|subject-4444444")
        mockMvc.perform(post("/sites/unlinkSso").session(login(user())).param("loginId", u.loginId!!))
        assertNotNull(linked.findById(a.id!!).orElse(null))
    }

    @Test
    fun `관리자 사용자 목록은 SSO 연결이 있는 사용자에게만 해제 단추를 보인다`() {
        val u = user(); link(u, "sso-oidc", "https://idp|subject-6666666"); val other = user()
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/userList").session(login(user(admin = true)))).andReturn().response.contentAsString)
        val withButton = doc.select("a[data-request-uri*=unlinkSso]").map { it.attr("data-request-uri") }
        assertTrue(withButton.any { it.contains(u.loginId!!) }); assertFalse(withButton.any { it.contains(other.loginId!!) })
    }
}
