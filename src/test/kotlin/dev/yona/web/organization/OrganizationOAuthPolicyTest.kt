package dev.yona.web.organization

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ApprovalState
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.oauth2server.OAuthConsent
import dev.yona.domain.oauth2server.OAuthConsentRepository
import dev.yona.domain.oauth2server.OAuthOrgPolicyService
import dev.yona.domain.project.OrganizationManagementService
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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
import kotlin.test.assertTrue

/**
 * 조직 설정의 "OAuth 앱 정책" 탭(X-39, `decisions.md` D-78)을 검증한다. 조직 관리자만 들어가 제한을 켜고 끄며, 조직 구성원이 승인한 적이 있는 앱을 승인하거나 거부한다.
 */
@AutoConfigureMockMvc
@Transactional
class OrganizationOAuthPolicyTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var organizationService: OrganizationService
    @Autowired private lateinit var management: OrganizationManagementService
    @Autowired private lateinit var clientService: OAuthClientService
    @Autowired private lateinit var consents: OAuthConsentRepository
    @Autowired private lateinit var policy: OAuthOrgPolicyService

    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "opt$k-$stamp", name = "opt$k", email = "opt$k-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun page(s: MockHttpSession, path: String): Document = Jsoup.parse(mockMvc.perform(get(path).session(s)).andExpect(status().isOk).andReturn().response.contentAsString)

    private inner class Setup {
        val admin = user(); val member = user(); val outsider = user()
        val org = organizationService.create("opt$stamp${++n}", null, admin).also { management.addMember(it, admin, member.loginId!!) }
        val path = "/organizations/${org.name}/oauth-policy"
        val app = (clientService.create(user(), "정책 앱", "", "", "https://a.example/cb", false, setOf("read:user")) as ClientCreation.Created).client
            .also { consents.save(OAuthConsent(clientRowId = it.id, userId = member.id, scopes = "read:user", createdDate = Date())) }
    }

    @Test
    fun `로그인해야 하고 조직 관리자만 들어갈 수 있다`() {
        val x = Setup()
        mockMvc.perform(get(x.path)).andExpect(status().is3xxRedirection)
        mockMvc.perform(get(x.path).session(login(x.member))).andExpect(status().isForbidden)
        mockMvc.perform(get(x.path).session(login(x.outsider))).andExpect(status().isForbidden)
        mockMvc.perform(post(x.path).session(login(x.member)).param("restricted", "true")).andExpect(status().isForbidden)
        mockMvc.perform(post("${x.path}/apps/${x.app.id}").session(login(x.member)).param("state", "APPROVED")).andExpect(status().isForbidden)
        mockMvc.perform(get("/organizations/없는조직/oauth-policy").session(login(x.admin))).andExpect(status().isNotFound)
    }

    @Test
    fun `설정 탭에 OAuth 앱 정책 탭이 있고 기본은 제한 없음이다`() {
        val x = Setup(); val s = login(x.admin); val doc = page(s, x.path)
        assertTrue(doc.select("ul.nav-tabs a[href\$=/oauth-policy]").single().hasClass("active"))
        assertEquals("false", doc.selectFirst("form#oauth-policy-form input[name=restricted][checked]")?.attr("value") ?: "false")
        assertNotNull(doc.selectFirst("form#oauth-policy-form input[name=restricted][value=true]")); assertNotNull(doc.selectFirst("form#oauth-policy-form input[name=restricted][value=false]"))
    }

    @Test
    fun `제한을 켜고 끌 수 있다`() {
        val x = Setup(); val s = login(x.admin)
        mockMvc.perform(post(x.path).session(s).param("restricted", "true")).andExpect(status().is3xxRedirection)
        assertTrue(policy.restricted(x.org.id!!))
        assertNotNull(page(s, x.path).selectFirst("form#oauth-policy-form input[name=restricted][value=true][checked]"))
        mockMvc.perform(post(x.path).session(s).param("restricted", "false")).andExpect(status().is3xxRedirection)
        assertFalse(policy.restricted(x.org.id!!))
    }

    @Test
    fun `구성원이 승인한 앱이 목록에 나오고 승인과 거부와 되돌리기를 할 수 있다`() {
        val x = Setup(); val s = login(x.admin)
        val row = page(s, x.path).select("table#policy-apps tr.policy-app-row").single()
        assertTrue(row.text().contains("정책 앱")); assertNotNull(row.selectFirst("form[action\$=/apps/${x.app.id}] button[value=APPROVED]"))
        mockMvc.perform(post("${x.path}/apps/${x.app.id}").session(s).param("state", "APPROVED")).andExpect(status().is3xxRedirection)
        policy.setRestricted(x.org, true, x.admin)
        assertTrue(policy.allows(x.org.id!!, x.app.id!!))
        mockMvc.perform(post("${x.path}/apps/${x.app.id}").session(s).param("state", "DENIED")).andExpect(status().is3xxRedirection)
        assertFalse(policy.allows(x.org.id!!, x.app.id!!))
        mockMvc.perform(post("${x.path}/apps/${x.app.id}").session(s).param("state", "PENDING")).andExpect(status().is3xxRedirection)
        assertEquals(null, policy.candidates(x.org).single().state)
        mockMvc.perform(post("${x.path}/apps/${x.app.id}").session(s).param("state", "엉뚱")).andExpect(status().isBadRequest)
        mockMvc.perform(post("${x.path}/apps/999999").session(s).param("state", "APPROVED")).andExpect(status().isNotFound)
    }

    @Test
    fun `승인 화면은 제한한 조직의 승인이 필요하다는 경고를 보인다`() {
        val x = Setup(); policy.setRestricted(x.org, true, x.admin)
        assertEquals(listOf(x.org.name), policy.restrictedUnapprovedOrganizationNames(x.member, x.app.id!!))
        policy.decide(x.org, x.app.id!!, ApprovalState.APPROVED, x.admin)
        assertEquals(emptyList(), policy.restrictedUnapprovedOrganizationNames(x.member, x.app.id!!))
        assertEquals(emptyList(), policy.restrictedUnapprovedOrganizationNames(x.outsider, x.app.id!!))
    }
}
