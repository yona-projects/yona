package dev.yona.web.organization

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ClientOwner
import dev.yona.domain.oauth2server.OAuthClientRepository
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.project.OrganizationManagementService
import dev.yona.domain.project.OrganizationService
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 조직 설정의 "OAuth 앱" 탭(X-39, `decisions.md` D-78)을 검증한다. 조직 관리자만 조직이 소유한 앱을 만들고 고치고 비밀 값을 다시 만들고 지우며, 구성원과 외부인은 들어갈 수 없다.
 * 다른 조직의 앱은 404이고, 관리자 설정으로 앱 만들기를 끄면 조직도 새로 만들 수 없다. 화면은 사용자 설정의 앱 화면과 같은 양식이다.
 */
@AutoConfigureMockMvc
@Transactional
class OrganizationOAuthAppsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var organizationService: OrganizationService
    @Autowired private lateinit var management: OrganizationManagementService
    @Autowired private lateinit var clientService: OAuthClientService
    @Autowired private lateinit var clients: OAuthClientRepository
    @Autowired private lateinit var settings: SiteSettingService

    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000

    @BeforeEach
    fun enable() { settings.setOauthUserAppsEnabled(true) }

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "oor$k-$stamp", name = "oor$k", email = "oor$k-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun page(s: MockHttpSession, path: String): Document = Jsoup.parse(mockMvc.perform(get(path).session(s)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun appOf(org: dev.yona.domain.project.Organization, admin: User, name: String = "조직 앱", confidential: Boolean = false) =
        (clientService.createFor(ClientOwner.of(org), admin, name, "설명", "", "https://a.example/cb", confidential, setOf("read:user")) as dev.yona.domain.oauth2server.ClientCreation.Created).client

    private fun params(path: String, name: String = "새 앱", type: String = "public", redirects: String = "https://a.example/cb", vararg scopes: String = arrayOf("read:user")) =
        post(path).param("name", name).param("description", "").param("homeUrl", "").param("redirectUris", redirects).param("type", type).apply { scopes.forEach { param("scope", it) } }

    /** 조직 하나와 관리자, 구성원, 외부인이다. */
    private inner class Setup {
        val admin = user(); val member = user(); val outsider = user()
        val org = organizationService.create("oorg$stamp${++n}", "설명", admin).also { management.addMember(it, admin, member.loginId!!) }
        val base = "/organizations/${org.name}/oauth-apps"
    }

    @Test
    fun `로그인해야 하고 조직 관리자만 들어갈 수 있다`() {
        val x = Setup()
        mockMvc.perform(get(x.base)).andExpect(status().is3xxRedirection)
        mockMvc.perform(get(x.base).session(login(x.member))).andExpect(status().isForbidden)
        mockMvc.perform(get(x.base).session(login(x.outsider))).andExpect(status().isForbidden)
        mockMvc.perform(get("/organizations/없는조직/oauth-apps").session(login(x.admin))).andExpect(status().isNotFound)
        for (path in listOf("", "/1", "/1/secret", "/1/delete")) mockMvc.perform(post(x.base + path).session(login(x.member))).andExpect(status().isForbidden)
        mockMvc.perform(get("${x.base}/new").session(login(x.member))).andExpect(status().isForbidden)
    }

    @Test
    fun `조직 설정 탭에 OAuth 앱 탭이 있고 목록 화면에서 활성이다`() {
        val x = Setup(); val s = login(x.admin)
        assertTrue(page(s, x.base).select("ul.nav-tabs a[href\$=/oauth-apps]").single().hasClass("active"))
        assertNotNull(page(s, "/organizations/${x.org.name}/settingform").select("ul.nav-tabs a[href\$=/oauth-apps]").singleOrNull())
    }

    @Test
    fun `목록은 그 조직의 앱만 보이고 비어 있으면 안내한다`() {
        val x = Setup(); val s = login(x.admin)
        assertNotNull(page(s, x.base).selectFirst("#my-apps-empty"))
        val other = organizationService.create("oorg2$stamp${++n}", null, x.admin)
        val mine = appOf(x.org, x.admin, "이 조직 앱"); appOf(other, x.admin, "다른 조직 앱"); clientService.create(x.admin, "개인 앱", "", "", "https://a.example/cb", false, setOf("read:user"))
        val rows = page(s, x.base).select("table#my-apps tr.app-row")
        assertEquals(1, rows.size); assertTrue(rows.single().text().contains("이 조직 앱") && rows.single().text().contains(mine.clientId!!))
        assertNotNull(rows.single().selectFirst("a[href\$=${x.base}/${mine.id}]")); assertNotNull(rows.single().selectFirst("form[action\$=/delete] button"))
    }

    @Test
    fun `앱을 만들면 조직이 소유하고 기밀 앱은 비밀 값을 한 번 보이며 캐시를 막는다`() {
        val x = Setup(); val s = login(x.admin)
        val form = page(s, "${x.base}/new").selectFirst("form#oauth-app-form")!!
        assertTrue(form.attr("action").endsWith(x.base))
        mockMvc.perform(params(x.base, "공개 앱").session(s)).andExpect(status().is3xxRedirection)
        assertEquals(x.org.id, clients.findAll().single { it.name == "공개 앱" }.ownerId)
        val result = mockMvc.perform(params(x.base, "기밀 앱", "confidential").session(s)).andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate")).andReturn()
        val doc = Jsoup.parse(result.response.contentAsString)
        assertTrue(doc.text().contains("yona_cs_"))
        val stored = clients.findAll().single { it.name == "기밀 앱" }
        assertEquals("ORGANIZATION", stored.ownerType); assertEquals(x.org.id, stored.ownerId)
        assertNotNull(doc.selectFirst("a[href\$=${x.base}/${stored.id}], a[href\$=${x.base}]"))
    }

    @Test
    fun `잘못된 입력은 400이고 앱 만들기를 끄면 조직도 새로 만들 수 없지만 이미 있는 앱은 보인다`() {
        val x = Setup(); val s = login(x.admin); val app = appOf(x.org, x.admin)
        mockMvc.perform(params(x.base, "", "public").session(s)).andExpect(status().isBadRequest)
        settings.setOauthUserAppsEnabled(false)
        mockMvc.perform(get("${x.base}/new").session(s)).andExpect(status().isForbidden)
        mockMvc.perform(params(x.base, "안 되는 앱").session(s)).andExpect(status().isForbidden)
        assertNotNull(page(s, x.base).selectFirst("#user-apps-disabled")); assertEquals(1, page(s, x.base).select("tr.app-row").size)
        mockMvc.perform(get("${x.base}/${app.id}").session(s)).andExpect(status().isOk)
    }

    @Test
    fun `수정과 비밀 값 다시 만들기와 지우기는 그 조직의 앱에만 되고 다른 조직의 앱은 404이다`() {
        val x = Setup(); val s = login(x.admin); val other = organizationService.create("oorg3$stamp${++n}", null, x.admin)
        val app = appOf(x.org, x.admin, confidential = true); val foreign = appOf(other, x.admin)
        assertEquals("조직 앱", page(s, "${x.base}/${app.id}").selectFirst("input[name=name]")!!.attr("value"))
        mockMvc.perform(get("${x.base}/${foreign.id}").session(s)).andExpect(status().isNotFound)
        mockMvc.perform(params("${x.base}/${foreign.id}", "해킹").session(s)).andExpect(status().isNotFound)
        mockMvc.perform(post("${x.base}/${foreign.id}/secret").session(s)).andExpect(status().isNotFound)
        mockMvc.perform(post("${x.base}/${foreign.id}/delete").session(s)).andExpect(status().isNotFound)
        mockMvc.perform(params("${x.base}/${app.id}", "바뀐 이름").session(s)).andExpect(status().is3xxRedirection)
        assertEquals("바뀐 이름", clients.findById(app.id!!).get().name)
        mockMvc.perform(post("${x.base}/${app.id}/secret").session(s)).andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
        mockMvc.perform(post("${x.base}/${app.id}/delete").session(s)).andExpect(status().is3xxRedirection)
        assertNull(clients.findById(app.id!!).orElse(null)); assertNotNull(clients.findById(foreign.id!!).orElse(null))
    }

    @Test
    fun `사용자 설정의 앱 목록에는 조직이 소유한 앱이 보이지 않는다`() {
        val x = Setup(); appOf(x.org, x.admin, "조직 전용 앱")
        assertFalse(page(login(x.admin), "/user/editform/oauth-apps").text().contains("조직 전용 앱"))
    }

    @Test
    fun `수정 화면에 이전 대상으로 내 계정과 내가 관리하는 다른 조직만 보인다`() {
        val x = Setup(); val s = login(x.admin); val app = appOf(x.org, x.admin)
        val mine = organizationService.create("oorg4$stamp${++n}", null, x.admin)
        val foreign = organizationService.create("oorg5$stamp${++n}", null, x.outsider).also { management.addMember(it, x.outsider, x.admin.loginId!!) }
        val options = page(s, "${x.base}/${app.id}").select("form#oauth-transfer-form select[name=target] option").map { it.attr("value") }
        assertEquals(setOf("user", "org:${mine.name}"), options.toSet()); assertFalse(options.contains("org:${foreign.name}")); assertFalse(options.contains("org:${x.org.name}"))
        assertNotNull(page(s, "${x.base}/${app.id}").selectFirst("form#oauth-transfer-form input[name=confirm]"))
    }

    @Test
    fun `이전하면 앱이 대상 소유자로 옮겨 가고 이름이 틀리거나 허용되지 않은 대상이면 400이다`() {
        val x = Setup(); val s = login(x.admin); val app = appOf(x.org, x.admin, "옮길 앱")
        val mine = organizationService.create("oorg6$stamp${++n}", null, x.admin)
        val foreign = organizationService.create("oorg7$stamp${++n}", null, x.outsider).also { management.addMember(it, x.outsider, x.admin.loginId!!) }
        mockMvc.perform(post("${x.base}/${app.id}/transfer").session(s).param("target", "user").param("confirm", "틀린 이름")).andExpect(status().isBadRequest)
        mockMvc.perform(post("${x.base}/${app.id}/transfer").session(s).param("target", "org:${foreign.name}").param("confirm", "옮길 앱")).andExpect(status().isBadRequest)
        mockMvc.perform(post("${x.base}/${app.id}/transfer").session(s).param("target", "org:없는조직").param("confirm", "옮길 앱")).andExpect(status().isBadRequest)
        assertEquals(x.org.id, clients.findById(app.id!!).get().ownerId)
        mockMvc.perform(post("${x.base}/${app.id}/transfer").session(s).param("target", "org:${mine.name}").param("confirm", "옮길 앱")).andExpect(status().is3xxRedirection)
        assertEquals(mine.id, clients.findById(app.id!!).get().ownerId)
        mockMvc.perform(get("${x.base}/${app.id}").session(s)).andExpect(status().isNotFound)
        mockMvc.perform(post("${x.base}/${app.id}/transfer").session(s).param("target", "user").param("confirm", "옮길 앱")).andExpect(status().isNotFound)
    }

    @Test
    fun `조직 관리자가 아니면 이전할 수 없다`() {
        val x = Setup(); val app = appOf(x.org, x.admin, "옮길 앱")
        mockMvc.perform(post("${x.base}/${app.id}/transfer").session(login(x.member)).param("target", "user").param("confirm", "옮길 앱")).andExpect(status().isForbidden)
        assertEquals(x.org.id, clients.findById(app.id!!).get().ownerId)
    }
}
