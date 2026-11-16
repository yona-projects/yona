package dev.yona.web.shell

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
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

/**
 * 검색 결과 화면의 사이드바를 검증한다(D-141). 전역 검색 결과에는 맥락이 없어 사이드바가 없고, 프로젝트와 그룹 안의 검색 결과는
 * 그 맥락의 사이드바를 보여 준다. 분류 목록(이슈, 사용자 …)은 본문 안의 왼쪽 열로 그대로 남는다.
 */
@AutoConfigureMockMvc
@Transactional
class SearchShellTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var roles: RoleRepository

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ss$n", name = "ss$n", email = "ss$n@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `전역 검색 결과에는 사이드바가 없고 분류 목록이 있다`() {
        val doc = page("/search?keyword=a&searchType=issue")
        assertNull(doc.selectFirst("nav.shell-sidebar"))
        assertNotNull(doc.selectFirst(".search-category-wrap"))
    }

    @Test
    fun `프로젝트 검색 결과에는 프로젝트 사이드바가 있고 옛 가로 메뉴는 없다`() {
        val owner = newUser()
        val project = projects.save(Project(owner = owner.loginId, name = "searchproj", vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = owner, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        val doc = page("/${owner.loginId}/searchproj/search?keyword=a&searchType=issue", login(owner))
        assertNotNull(doc.selectFirst(".shell > nav.shell-sidebar"))
        assertNull(doc.selectFirst(".project-menu-outer"))
        assertNotNull(doc.selectFirst(".shell-main .search-category-wrap"), "분류 목록은 본문 안에 남는다")
        assertEquals(0, doc.select("nav.shell-sidebar a.nav-link.active").size, "검색 결과는 사이드바 항목이 아니다")
    }

    @Test
    fun `그룹 검색 결과에는 그룹 사이드바가 있다`() {
        val org: Organization = organizations.save(Organization(name = "searchorg${++sequence}", descr = "설명", created = Date()))
        val doc = page("/organizations/${org.name}/search?keyword=a&searchType=issue")
        assertNotNull(doc.selectFirst(".shell > nav.shell-sidebar"))
        assertNull(doc.selectFirst(".project-menu-outer"))
        assertNotNull(doc.selectFirst(".shell-main .search-category-wrap"))
    }
}
