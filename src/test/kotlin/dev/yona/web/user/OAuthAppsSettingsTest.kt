package dev.yona.web.user

import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.OAuthAuthorization
import dev.yona.domain.oauth2server.OAuthAuthorizationRepository
import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientRepository
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.oauth2server.OAuthConsent
import dev.yona.domain.oauth2server.OAuthConsentRepository
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.BeforeEach
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
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 설정의 "OAuth 앱" 화면(X-1 6단계, `decisions.md` D-71)을 검증한다. 한 화면에 "내가 만든 앱"과 "승인한 앱"이 있고, 앱 만들기와 수정은 따로 있는 화면이다. 기밀 앱의 비밀 값은 만든 직후와
 * 다시 만든 직후에 한 번만 보이고 캐시를 막는다. 소유자만 고치고 지우며 승인한 앱은 취소할 수 있다. 관리자 설정으로 사용자의 앱 만들기를 끌 수 있다.
 */
@AutoConfigureMockMvc
@Transactional
class OAuthAppsSettingsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var clientService: OAuthClientService
    @Autowired private lateinit var clients: OAuthClientRepository
    @Autowired private lateinit var consents: OAuthConsentRepository
    @Autowired private lateinit var authorizations: OAuthAuthorizationRepository
    @Autowired private lateinit var settings: SiteSettingService
    @Autowired private lateinit var organizationService: dev.yona.domain.project.OrganizationService
    @Autowired private lateinit var management: dev.yona.domain.project.OrganizationManagementService

    private var n = 0

    @BeforeEach
    fun enable() { settings.setOauthUserAppsEnabled(true) }

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "oas$k-${System.nanoTime()}", name = "oas$k", email = "oas$k@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun page(s: MockHttpSession, path: String = "/user/editform/oauth-apps"): Document = Jsoup.parse(mockMvc.perform(get(path).session(s)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun app(owner: User, name: String = "내 앱", confidential: Boolean = false) =
        (clientService.create(owner, name, "설명", "https://home.example", "https://a.example/cb", confidential, setOf("read:user", "repo")) as ClientCreation.Created)

    private fun createParams(name: String = "새 앱", type: String = "public", redirects: String = "https://a.example/cb", vararg scopes: String = arrayOf("read:user")) =
        post("/user/oauth-apps").param("name", name).param("description", "").param("homeUrl", "").param("redirectUris", redirects).param("type", type).apply { scopes.forEach { param("scope", it) } }

    @Test
    fun `로그인해야 쓸 수 있다`() {
        mockMvc.perform(get("/user/editform/oauth-apps")).andExpect(status().is3xxRedirection)
        mockMvc.perform(get("/user/editform/oauth-apps/new")).andExpect(status().is3xxRedirection)
        for (path in listOf("/user/oauth-apps", "/user/oauth-apps/1", "/user/oauth-apps/1/secret", "/user/oauth-apps/1/delete", "/user/oauth-apps/authorized/1/revoke")) mockMvc.perform(post(path)).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `설정 탭에 OAuth 앱 탭이 있고 목록 화면에서 활성이다`() {
        val s = login(user())
        assertTrue(page(s).select("ul.nav-tabs a[href$=/user/editform/oauth-apps]").single().hasClass("active"))
        assertNotNull(page(s, "/user/editform").select("ul.nav-tabs a[href$=/user/editform/oauth-apps]").singleOrNull())
    }

    @Test
    fun `목록은 한 화면에 내가 만든 앱과 승인한 앱을 보이고 비어 있으면 안내한다`() {
        val doc = page(login(user()))
        assertNotNull(doc.selectFirst("#my-apps-empty")); assertNotNull(doc.selectFirst("#authorized-empty")); assertNotNull(doc.selectFirst("a#oauth-app-new"))
        assertNull(doc.selectFirst("table#my-apps")); assertNull(doc.selectFirst("table#authorized-apps"))
    }

    @Test
    fun `내가 만든 앱은 이름과 클라이언트 ID와 종류를 보이고 남의 앱은 보이지 않는다`() {
        val me = user(); val other = user(); val mine = app(me, "내 공개 앱"); app(me, "내 기밀 앱", confidential = true); app(other, "남의 앱")
        val rows = page(login(me)).select("table#my-apps tr.app-row")
        assertEquals(2, rows.size); assertTrue(rows.any { it.text().contains("내 공개 앱") && it.text().contains(mine.client.clientId!!) })
        assertTrue(rows.none { it.text().contains("남의 앱") })
        assertTrue(rows.any { it.selectFirst("a[href$=/user/editform/oauth-apps/${mine.client.id}]") != null })
        assertTrue(rows.all { it.selectFirst("form[action$=/delete] button") != null })
    }

    @Test
    fun `승인한 앱은 앱 이름과 만든 사용자와 범위를 보이고 취소 단추가 있다`() {
        val owner = user(); val me = user(); val c = app(owner, "승인한 앱").client
        consents.save(OAuthConsent(clientRowId = c.id, userId = me.id, scopes = "read:user,repo", createdDate = Date()))
        authorizations.save(OAuthAuthorization(id = "ui-a", clientRowId = c.id, userId = me.id, grantType = "authorization_code", attributes = "{}", accessTokenHash = TokenHasher.sha256Hex("x"), createdDate = Date()))
        val row = page(login(me)).select("table#authorized-apps tr.authorized-row").single()
        assertTrue(row.text().contains("승인한 앱") && row.text().contains(owner.loginId!!) && row.text().contains("read:user") && row.text().contains("repo"))
        assertNotNull(row.selectFirst("form[action$=/revoke] button"))
    }

    @Test
    fun `승인을 취소하면 목록에서 사라지고 남의 승인은 취소하지 못한다`() {
        val owner = user(); val me = user(); val other = user(); val c = app(owner).client; val s = login(me)
        consents.save(OAuthConsent(clientRowId = c.id, userId = me.id, scopes = "read:user", createdDate = Date()))
        mockMvc.perform(post("/user/oauth-apps/authorized/${c.id}/revoke").session(login(other))).andExpect(status().isNotFound)
        mockMvc.perform(post("/user/oauth-apps/authorized/${c.id}/revoke").session(s)).andExpect(status().is3xxRedirection)
        assertTrue(page(s).select("tr.authorized-row").isEmpty())
    }

    @Test
    fun `앱 만들기는 별도 화면이고 범위 체크박스에는 막힌 범위가 없다`() {
        val doc = page(login(user()), "/user/editform/oauth-apps/new")
        val form = doc.selectFirst("form#oauth-app-form")!!
        assertTrue(form.attr("action").endsWith("/user/oauth-apps"))
        for (field in listOf("name", "description", "homeUrl", "redirectUris")) assertNotNull(form.selectFirst("[name=$field]"), field)
        assertEquals(setOf("public", "confidential"), form.select("input[name=type]").map { it.attr("value") }.toSet())
        val scopes = form.select("input[name=scope]").map { it.attr("value") }
        assertTrue("repo" in scopes && "read:user" in scopes && scopes.none { it == "site_admin" || it.endsWith("_key") })
    }

    @Test
    fun `공개 앱을 만들면 수정 화면으로 가고 기밀 앱은 비밀 값을 한 번 보인다`() {
        val me = user(); val s = login(me)
        val created = mockMvc.perform(createParams(name = "공개 앱", scopes = arrayOf("read:user")).session(s)).andExpect(status().is3xxRedirection).andReturn()
        val id = clients.findAll().single { it.name == "공개 앱" }.id
        assertTrue(created.response.getHeader("Location")!!.endsWith("/user/editform/oauth-apps/$id"))
        val result = mockMvc.perform(createParams(name = "기밀 앱", type = "confidential", scopes = arrayOf("repo")).session(s)).andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate")).andReturn()
        val doc = Jsoup.parse(result.response.contentAsString)
        val secret = doc.selectFirst("#client-secret")!!.text()
        assertTrue(secret.startsWith("yona_cs_")); assertTrue(doc.selectFirst("#client-id")!!.text().startsWith("yona_app_"))
        val stored = clients.findAll().single { it.name == "기밀 앱" }
        assertTrue(stored.secretHash != secret && dev.yona.domain.apitoken.TokenHasher.sha256Hex(secret) == stored.secretHash)
        assertFalse(page(s, "/user/editform/oauth-apps/${stored.id}").text().contains(secret), "다시 보이지 않는다")
    }

    @Test
    fun `잘못된 입력은 입력을 되돌려 주며 400으로 이유를 보인다`() {
        val s = login(user())
        val bad = mockMvc.perform(createParams(name = "x", redirects = "http://example.com/cb").session(s)).andExpect(status().isBadRequest).andReturn()
        val doc = Jsoup.parse(bad.response.contentAsString)
        assertNotNull(doc.selectFirst(".alert-danger")); assertEquals("x", doc.selectFirst("input[name=name]")!!.attr("value"))
        assertEquals(0, clients.count())
        mockMvc.perform(createParams(scopes = arrayOf("site_admin")).session(s)).andExpect(status().isBadRequest)
        mockMvc.perform(createParams(scopes = arrayOf("read:ssh_key")).session(s)).andExpect(status().isBadRequest)
    }

    @Test
    fun `소유자만 수정 화면을 열고 고치고 비밀 값을 다시 만들고 지운다`() {
        val owner = user(); val other = user(); val created = app(owner, confidential = true); val id = created.client.id!!
        val so = login(owner); val sx = login(other)
        mockMvc.perform(get("/user/editform/oauth-apps/$id").session(sx)).andExpect(status().isNotFound)
        for (path in listOf("/user/oauth-apps/$id", "/user/oauth-apps/$id/secret", "/user/oauth-apps/$id/delete")) mockMvc.perform(post(path).session(sx).param("name", "해킹")).andExpect(status().isNotFound)
        val edit = page(so, "/user/editform/oauth-apps/$id")
        assertEquals("내 앱", edit.selectFirst("input[name=name]")!!.attr("value")); assertTrue(edit.text().contains(created.client.clientId!!))
        assertNull(edit.selectFirst("input[name=type]:not([disabled])"), "종류는 만든 뒤 바꿀 수 없다")
        mockMvc.perform(post("/user/oauth-apps/$id").session(so).param("name", "바뀐 이름").param("description", "").param("homeUrl", "").param("redirectUris", "https://b.example/cb").param("scope", "read:user")).andExpect(status().is3xxRedirection)
        assertEquals("바뀐 이름", clients.findById(id).get().name)
        val regenerated = Jsoup.parse(mockMvc.perform(post("/user/oauth-apps/$id/secret").session(so)).andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate")).andReturn().response.contentAsString)
        assertTrue(regenerated.selectFirst("#client-secret")!!.text().startsWith("yona_cs_"))
        mockMvc.perform(post("/user/oauth-apps/$id/delete").session(so)).andExpect(status().is3xxRedirection)
        assertNull(clients.findById(id).orElse(null))
    }

    @Test
    fun `관리자가 사용자의 앱 만들기를 끄면 만들기 화면과 요청이 막히고 단추가 사라진다`() {
        val me = user(); val s = login(me); val own = app(me)
        settings.setOauthUserAppsEnabled(false)
        assertNull(page(s).selectFirst("a#oauth-app-new")); assertNotNull(page(s).selectFirst("#user-apps-disabled"))
        mockMvc.perform(get("/user/editform/oauth-apps/new").session(s)).andExpect(status().isForbidden)
        mockMvc.perform(createParams().session(s)).andExpect(status().isForbidden)
        assertTrue(page(s).select("tr.app-row").size == 1, "이미 만든 앱은 보인다")
        mockMvc.perform(post("/user/oauth-apps/${own.client.id}/delete").session(s)).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `사용자의 앱은 내가 관리하는 조직으로만 이전할 수 있다`() {
        val me = user(); val other = user(); val s = login(me); val app = app(me, "옮길 앱")
        val mine = organizationService.create("ua${System.nanoTime() % 1000000}", null, me)
        val foreign = organizationService.create("ub${System.nanoTime() % 1000000}", null, other).also { management.addMember(it, other, me.loginId!!) }
        val options = page(s, "/user/editform/oauth-apps/${app.client.id}").select("form#oauth-transfer-form select[name=target] option").map { it.attr("value") }
        assertEquals(listOf("org:${mine.name}"), options)
        mockMvc.perform(post("/user/oauth-apps/${app.client.id}/transfer").session(s).param("target", "org:${foreign.name}").param("confirm", "옮길 앱")).andExpect(status().isBadRequest)
        mockMvc.perform(post("/user/oauth-apps/${app.client.id}/transfer").session(s).param("target", "org:${mine.name}").param("confirm", "아님")).andExpect(status().isBadRequest)
        mockMvc.perform(post("/user/oauth-apps/${app.client.id}/transfer").session(s).param("target", "org:${mine.name}").param("confirm", "옮길 앱")).andExpect(status().is3xxRedirection)
        assertEquals(mine.id, clients.findById(app.client.id!!).get().ownerId)
        assertTrue(page(s).select("tr.app-row").isEmpty())
        mockMvc.perform(post("/user/oauth-apps/${app.client.id}/transfer").session(s).param("target", "org:${mine.name}").param("confirm", "옮길 앱")).andExpect(status().isNotFound)
    }
}
