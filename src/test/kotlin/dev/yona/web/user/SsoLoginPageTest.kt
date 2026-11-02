package dev.yona.web.user

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.sso.OidcSsoForm
import dev.yona.domain.sso.Saml2SsoForm
import dev.yona.domain.sso.SsoSettingsService
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.FakeOidcIdp
import dev.yona.support.FakeSamlIdp
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 로그인 화면의 SSO 단추와 SSO 전용 모드(X-16 단계 5, `decisions.md` D-80 12번)를 검증한다. 켠 SSO마다 단추가 생기고 돌아갈 주소를 이어 붙이며, SSO 전용 모드에서는 로컬 비밀번호
 * 로그인과 LDAP, 소셜 로그인을 막고 사이트 관리자만 비상 경로(로컬 로그인)를 쓴다.
 */
@AutoConfigureMockMvc
@Transactional
class SsoLoginPageTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var admins: SiteAdminRepository
    @Autowired private lateinit var sso: SsoSettingsService
    @Autowired private lateinit var siteSettings: SiteSettingService

    private val oidcIdp = FakeOidcIdp()
    private val samlIdp = FakeSamlIdp()
    private var n = 0

    @AfterEach
    fun stop() { oidcIdp.close() }

    private fun user(admin: Boolean = false): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        val u = users.save(User(loginId = "lp$k-${System.nanoTime()}", name = "lp$k", email = "lp$k-${System.nanoTime()}@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        if (admin) admins.save(SiteAdmin(admin = u))
        return u
    }

    private fun enableOidc(name: String = "회사 OIDC") = sso.saveOidc(
        OidcSsoForm(true, name, oidcIdp.issuer, oidcIdp.clientId, oidcIdp.clientSecret, "openid profile email", "email", "name", "", true, true, false, false, false, 60), null
    )

    private fun enableSaml(name: String = "회사 SAML") = sso.saveSaml2(
        Saml2SsoForm(true, name, samlIdp.entityId, samlIdp.ssoUrl, samlIdp.certificatePem, "", "", "email", "name", "", true, true, false, false, false, false, 60), null
    )

    private fun loginPage(query: String = "") = Jsoup.parse(mockMvc.perform(get("/users/loginform$query")).andReturn().response.contentAsString)

    @Test
    fun `SSO를 켜지 않으면 단추가 없다`() {
        assertNull(loginPage().selectFirst("a.sso-login-btn"))
    }

    @Test
    fun `켠 SSO마다 표시 이름으로 단추가 생기고 돌아갈 주소를 잇는다`() {
        enableOidc(); enableSaml()
        val buttons = loginPage("?redirectUrl=/user/editform").select(".page.full a.sso-login-btn")
        assertEquals(2, buttons.size)
        val oidc = buttons.first { it.attr("href").contains("/sso/oidc/login") }; val saml = buttons.first { it.attr("href").contains("/sso/saml2/login") }
        assertTrue(oidc.text().contains("회사 OIDC")); assertTrue(saml.text().contains("회사 SAML"))
        assertTrue(oidc.attr("href").contains("returnTo=/user/editform") || oidc.attr("href").contains("returnTo=%2Fuser%2Feditform"), oidc.attr("href"))
    }

    @Test
    fun `표시 이름이 비어 있으면 기본 문구를 쓴다`() {
        enableOidc(name = "")
        assertTrue(loginPage().selectFirst("a.sso-login-btn")!!.text().isNotBlank())
    }

    @Test
    fun `표시 이름의 HTML은 글자로 보인다`() {
        enableOidc(name = "<script>alert(1)</script>")
        val html = mockMvc.perform(get("/users/loginform")).andReturn().response.contentAsString
        assertTrue(!html.contains("<script>alert(1)</script>"))
    }

    @Test
    fun `로그인 대화상자에도 단추가 들어간다`() {
        enableOidc()
        val html = mockMvc.perform(get("/")).andReturn().response.contentAsString
        assertNotNull(Jsoup.parse(html).selectFirst("#loginDialog a.sso-login-btn"))
    }

    // --- SSO 전용 모드 ---

    @Test
    fun `SSO 전용 모드를 켜려면 켠 SSO가 하나 이상 있어야 한다`() {
        assertTrue(!siteSettings.setSsoOnly(true))
        enableOidc(); assertTrue(siteSettings.setSsoOnly(true)); assertTrue(siteSettings.ssoOnly())
    }

    @Test
    fun `SSO 전용 모드에서는 비밀번호 입력란과 소셜 로그인 단추가 사라진다`() {
        enableOidc(); siteSettings.setSsoOnly(true)
        val doc = loginPage()
        assertNull(doc.selectFirst("form input[name=password]")); assertTrue(doc.select("a.oauth-login-btn:not(.sso-login-btn)").isEmpty()); assertNotNull(doc.selectFirst("a.sso-login-btn"))
        assertNotNull(doc.selectFirst("a.sso-local-link"))
        assertNotNull(loginPage("?local=true").selectFirst("form input[name=password]"))
    }

    @Test
    fun `SSO 전용 모드에서 일반 사용자는 비밀번호로 로그인할 수 없다`() {
        val u = user(); enableOidc(); siteSettings.setSsoOnly(true)
        val session = MockHttpSession()
        val response = mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", u.loginId!!).param("password", "1234")).andReturn().response
        assertTrue(response.getHeader("Location")!!.startsWith("/users/loginform"), response.getHeader("Location"))
        assertTrue(mockMvc.perform(get("/user/editform").session(session)).andReturn().response.status != 200)
        val ajax = mockMvc.perform(post("/users/login").header("Accept", "application/json").param("loginIdOrEmail", u.loginId!!).param("password", "1234")).andReturn().response
        assertEquals(403, ajax.status); assertTrue(ajax.contentAsString.contains("sso.error.ssoOnly"))
    }

    @Test
    fun `SSO 전용 모드에서도 사이트 관리자는 로컬로 로그인할 수 있다`() {
        val a = user(admin = true); enableOidc(); siteSettings.setSsoOnly(true)
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", a.loginId!!).param("password", "1234"))
        assertEquals(200, mockMvc.perform(get("/user/editform").session(session)).andReturn().response.status)
    }

    @Test
    fun `SSO 전용 모드에서는 소셜 로그인도 막는다`() {
        enableOidc(); siteSettings.setSsoOnly(true)
        val response = mockMvc.perform(get("/authenticate/github")).andReturn().response
        assertTrue(response.getHeader("Location")?.startsWith("/users/loginform") == true || response.status == 404, "${response.status} ${response.getHeader("Location")}")
    }

    @Test
    fun `SSO 전용 모드를 끄면 평소대로 돌아온다`() {
        val u = user(); enableOidc(); siteSettings.setSsoOnly(true); siteSettings.setSsoOnly(false)
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", u.loginId!!).param("password", "1234"))
        assertEquals(200, mockMvc.perform(get("/user/editform").session(session)).andReturn().response.status)
    }
}
