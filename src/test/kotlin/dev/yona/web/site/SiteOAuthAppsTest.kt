package dev.yona.web.site

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientRepository
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.oauth2server.OAuthConsent
import dev.yona.domain.oauth2server.OAuthConsentRepository
import dev.yona.domain.site.SiteSettingService
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사이트 관리의 OAuth 앱 화면과 보안 설정(X-1 6단계, `decisions.md` D-71)을 검증한다: 관리자만 사이트 전체의 앱 목록을 보고 문제 있는 앱을 지우며(그 앱의 토큰이 모두 무효),
 * "보안" 화면에서 CIMD 허용과 사용자의 앱 만들기 허용을 바꾼다.
 */
@AutoConfigureMockMvc
@Transactional
class SiteOAuthAppsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var admins: SiteAdminRepository
    @Autowired private lateinit var clientService: OAuthClientService
    @Autowired private lateinit var clients: OAuthClientRepository
    @Autowired private lateinit var consents: OAuthConsentRepository
    @Autowired private lateinit var settings: SiteSettingService
    @Autowired private lateinit var organizationService: dev.yona.domain.project.OrganizationService

    private var n = 0

    private fun user(admin: Boolean = false): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        val u = users.save(User(loginId = "so$k-${System.nanoTime()}", name = "so$k", email = "so$k@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        if (admin) admins.save(SiteAdmin(admin = u))
        return u
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun app(owner: User, name: String) = (clientService.create(owner, name, "", "", "https://a.example/cb", false, setOf("read:user")) as ClientCreation.Created).client

    @Test
    fun `조직이 소유한 앱은 소유자 칸에 조직 이름을 보인다`() {
        val admin = user(); val org = organizationService.create("so${System.nanoTime() % 1000000}", null, admin)
        clientService.createFor(dev.yona.domain.oauth2server.ClientOwner.of(org), admin, "조직 앱", "", "", "https://a.example/cb", false, setOf("read:user"))
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/oauth-apps").session(login(user(admin = true)))).andReturn().response.contentAsString)
        val row = doc.select("table#all-apps tr.app-row").single { it.text().contains("조직 앱") }
        assertTrue(row.text().contains(org.name!!), row.text())
    }

    @Test
    fun `관리자만 앱 목록을 연다`() {
        mockMvc.perform(get("/sites/oauth-apps")).andExpect(status().isForbidden)
        mockMvc.perform(get("/sites/oauth-apps").session(login(user()))).andExpect(status().isForbidden)
        mockMvc.perform(post("/sites/oauth-apps/1/delete").session(login(user()))).andExpect(status().isForbidden)
        mockMvc.perform(get("/sites/oauth-apps").session(login(user(admin = true)))).andExpect(status().isOk)
    }

    @Test
    fun `사이트 전체의 앱을 소유자와 등록 방식과 승인한 사용자 수와 함께 보인다`() {
        val owner = user(); val a = app(owner, "관리자가 볼 앱"); val b = app(user(), "다른 앱")
        consents.save(OAuthConsent(clientRowId = a.id, userId = user().id, scopes = "read:user", createdDate = Date())); consents.save(OAuthConsent(clientRowId = a.id, userId = user().id, scopes = "read:user", createdDate = Date()))
        clients.save(OAuthClient(clientId = "https://claude.example/c.json", name = "문서 앱", redirectUris = "https://c.example/cb", confidential = false, allowedScopes = "read:user", registration = OAuthClient.CIMD, createdDate = Date()))
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/oauth-apps").session(login(user(admin = true)))).andReturn().response.contentAsString)
        val rows = doc.select("table#all-apps tr.app-row")
        val mine = rows.first { it.text().contains("관리자가 볼 앱") }
        assertTrue(mine.text().contains(owner.loginId!!) && mine.text().contains("2"), mine.text())
        assertTrue(rows.any { it.text().contains("다른 앱") } && rows.any { it.text().contains("문서 앱") && it.text().contains("claude.example") })
        assertNotNull(doc.selectFirst("li.active a[href\$=/sites/oauth-apps]"))
        assertNotNull(mine.selectFirst("form[action\$=/delete] button"))
    }

    @Test
    fun `관리자가 앱을 지우면 앱과 승인이 사라진다`() {
        val owner = user(); val a = app(owner, "지울 앱"); consents.save(OAuthConsent(clientRowId = a.id, userId = user().id, scopes = "read:user", createdDate = Date()))
        mockMvc.perform(post("/sites/oauth-apps/${a.id}/delete").session(login(user(admin = true)))).andExpect(status().is3xxRedirection)
        assertNull(clients.findById(a.id!!).orElse(null)); assertTrue(consents.findAll().isEmpty())
        mockMvc.perform(post("/sites/oauth-apps/${a.id}/delete").session(login(user(admin = true)))).andExpect(status().isNotFound)
    }

    @Test
    fun `보안 화면에서 CIMD 허용과 사용자의 앱 만들기 허용을 바꾼다`() {
        val s = login(user(admin = true))
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/security").session(s)).andReturn().response.contentAsString)
        val form = doc.selectFirst("form#oauth-settings-form")!!
        assertTrue(form.selectFirst("input[name=cimd]")!!.hasAttr("checked") && form.selectFirst("input[name=userApps]")!!.hasAttr("checked"), "기본은 둘 다 허용")
        mockMvc.perform(post("/sites/security/oauth").session(s)).andExpect(status().is3xxRedirection)
        assertFalse(settings.oauthCimdEnabled()); assertFalse(settings.oauthUserAppsEnabled())
        mockMvc.perform(post("/sites/security/oauth").session(s).param("cimd", "true")).andExpect(status().is3xxRedirection)
        assertTrue(settings.oauthCimdEnabled()); assertFalse(settings.oauthUserAppsEnabled())
        mockMvc.perform(post("/sites/security/oauth").session(login(user())).param("cimd", "true")).andExpect(status().isForbidden)
    }

    @Test
    fun `보안 화면에서 MCP 서버 사용과 쓰기 도구 허용을 바꾼다`() {
        val s = login(user(admin = true))
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/security").session(s)).andReturn().response.contentAsString)
        val form = doc.selectFirst("form#mcp-settings-form")!!
        assertTrue(form.selectFirst("input[name=enabled]")!!.hasAttr("checked") && form.selectFirst("input[name=write]")!!.hasAttr("checked"), "기본은 둘 다 켬")
        mockMvc.perform(post("/sites/security/mcp").session(s)).andExpect(status().is3xxRedirection)
        assertFalse(settings.mcpEnabled()); assertFalse(settings.mcpWriteEnabled())
        mockMvc.perform(post("/sites/security/mcp").session(s).param("enabled", "true")).andExpect(status().is3xxRedirection)
        assertTrue(settings.mcpEnabled()); assertFalse(settings.mcpWriteEnabled())
        mockMvc.perform(post("/sites/security/mcp").session(login(user())).param("enabled", "true")).andExpect(status().isForbidden)
    }
}
