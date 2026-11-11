package dev.yona.web.shell

import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.user.RoleRepository
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 조직 화면이 새 셸의 사이드바로 그려지는지 검증한다(D-141). 메인 사이드바(홈, 이슈, 게시판, 코드 주고받기, 설정)와
 * 설정 전용 사이드바(맨 위에 "조직으로 돌아가기")를 `ShellNav`가 정한다. 조직 설정 화면도 조직 머리와 사이드바 안에서 열린다.
 */
@AutoConfigureMockMvc
@Transactional
class OrganizationShellTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var roles: RoleRepository

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "os$n", name = "os$n", email = "os$n@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun org(): Organization = organizations.save(Organization(name = "shellorg${++sequence}", descr = "설명", created = Date()))

    private fun join(org: Organization, user: User, role: RoleType) {
        organizationUsers.save(OrganizationUser(user = user, organization = org, role = roles.findById(role.id).get()))
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `메인 사이드바는 홈, 이슈, 게시판, 코드 주고받기이고 일반 구성원에게 설정이 없다`() {
        val org = org(); val member = newUser().also { join(org, it, RoleType.ORG_MEMBER) }
        val nav = page("/organizations/${org.name}", login(member)).selectFirst("nav.shell-sidebar")
        assertNotNull(nav)
        assertEquals(listOf("/organizations/${org.name}", "/organizations/${org.name}/issues", "/organizations/${org.name}/boards", "/organizations/${org.name}/pullrequests"), nav.select("a.nav-link").map { it.attr("href") })
        assertNull(nav.selectFirst(".shell-sidebar-back"))
    }

    @Test
    fun `조직 관리자에게는 설정 항목이 더해진다`() {
        val org = org(); val admin = newUser().also { join(org, it, RoleType.ORG_ADMIN) }
        val hrefs = page("/organizations/${org.name}", login(admin)).select("nav.shell-sidebar a.nav-link").map { it.attr("href") }
        assertEquals("/organizations/${org.name}/settingform", hrefs.last())
        assertEquals(5, hrefs.size)
    }

    @Test
    fun `현재 화면의 항목만 활성이다`() {
        val org = org(); val admin = newUser().also { join(org, it, RoleType.ORG_ADMIN) }; val session = login(admin)
        listOf("" to "/organizations/${org.name}", "/issues" to "/organizations/${org.name}/issues", "/boards" to "/organizations/${org.name}/boards", "/pullrequests" to "/organizations/${org.name}/pullrequests").forEach { (suffix, href) ->
            val active = page("/organizations/${org.name}$suffix", session).select("nav.shell-sidebar a.nav-link.active")
            assertEquals(1, active.size, suffix)
            assertEquals(href, active.first()!!.attr("href"))
        }
    }

    @Test
    fun `설정 화면은 설정 전용 사이드바이고 맨 위에 조직으로 돌아가는 단추가 있다`() {
        val org = org(); val admin = newUser().also { join(org, it, RoleType.ORG_ADMIN) }; val session = login(admin)
        val doc = page("/organizations/${org.name}/members", session)
        val nav = doc.selectFirst("nav.shell-sidebar")!!
        assertEquals("/organizations/${org.name}", nav.selectFirst("a.shell-sidebar-back")!!.attr("href"))
        assertEquals(
            listOf("settingform", "members", "oauth-apps", "oauth-policy", "audit", "deleteForm").map { "/organizations/${org.name}/$it" },
            nav.select("a.nav-link").map { it.attr("href") }
        )
        assertEquals("/organizations/${org.name}/members", nav.selectFirst("a.nav-link.active")!!.attr("href"))
        assertTrue(doc.select(".shell-main ul.nav-tabs a[href\$=/settingform]").isEmpty() && doc.select(".shell > ul.nav-tabs").isEmpty(), "옛 설정 탭 줄이 없어야 한다")
    }

    @Test
    fun `설정 하위 화면에서도 설정 항목이 활성이다(OAuth 앱 상세)`() {
        val org = org(); val admin = newUser().also { join(org, it, RoleType.ORG_ADMIN) }; val session = login(admin)
        val active = page("/organizations/${org.name}/oauth-apps", session).select("nav.shell-sidebar a.nav-link.active")
        assertEquals(1, active.size)
        assertTrue(active.first()!!.attr("href").endsWith("/oauth-apps"))
    }

    @Test
    fun `사이드바와 본문 래퍼가 나란히 놓이고 옛 가로 메뉴는 없다`() {
        val org = org(); val admin = newUser().also { join(org, it, RoleType.ORG_ADMIN) }
        val doc = page("/organizations/${org.name}/issues", login(admin))
        assertNotNull(doc.selectFirst(".shell > nav.shell-sidebar"))
        assertNotNull(doc.selectFirst(".shell > .shell-main"))
        assertNull(doc.selectFirst(".project-menu-outer"), "옛 조직 가로 메뉴가 없어야 한다")
    }

    @Test
    fun `스크립트가 없는 조직 설정 화면만 swap-safe 표지를 갖는다(D-136)`() {
        val org = org(); val admin = newUser().also { join(org, it, RoleType.ORG_ADMIN) }; val session = login(admin)
        listOf("oauth-apps", "oauth-policy").forEach { sub ->
            assertNotNull(page("/organizations/${org.name}/$sub", session).selectFirst(".shell-main [data-swap-safe]"), sub)
        }
        assertNull(page("/organizations/${org.name}", session).selectFirst("[data-swap-safe]"), "조직 홈은 일반 이동이다")
    }
}
