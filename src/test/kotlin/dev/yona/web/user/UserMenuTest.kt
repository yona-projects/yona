package dev.yona.web.user

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
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
import jakarta.persistence.EntityManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import dev.yona.domain.misc.FavoriteOrganization
import dev.yona.domain.misc.FavoriteProject
import dev.yona.domain.misc.RecentIssue
import dev.yona.domain.misc.RecentIssueRepository
import dev.yona.domain.misc.Watch
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.RecentProject
import dev.yona.domain.project.RecentProjectRepository
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.misc.FavoriteOrganizationRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사용자 메뉴(사이드바)의 내용(`GET /user/usermenuTabContentList`)과 사이드바 페이지(`GET /user/sidebar`)를 검증한다.
 *
 * 원본: `app/views/common/usermenu_tab_content_list.scala.html`, `app/views/index/myOrganizationList*.scala.html`, `myProjectList*.scala.html`,
 * `myRecentIssueList*.scala.html`, `allProjectList_partial.scala.html`, `app/views/index/sidebar.scala.html`, `app/views/layout_framed.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class UserMenuTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var favoriteProjects: FavoriteProjectRepository
    @Autowired private lateinit var favoriteOrganizations: FavoriteOrganizationRepository
    @Autowired private lateinit var recentProjects: RecentProjectRepository
    @Autowired private lateinit var recentIssues: RecentIssueRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "um$n", name = "um$n", email = "um$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun project(owner: String, name: String, scope: ProjectScope = ProjectScope.PUBLIC, org: Organization? = null, created: Long = 1_700_000_000_000L): Project =
        projects.save(Project(owner = owner, name = name, vcs = "GIT", projectScope = scope, organization = org, overview = "$name 개요", createdDate = Date(created)))

    private fun organization(name: String): Organization = organizations.save(Organization(name = name, descr = "d", created = Date()))

    private fun fragment(session: MockHttpSession? = null): org.jsoup.nodes.Document =
        Jsoup.parse(mockMvc.perform(get("/user/usermenuTabContentList").apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `세 개의 탭 내용이 있다`() {
        val doc = fragment(login(newUser()))
        assertEquals(listOf("myOrganizationList", "myProjectList", "myRecentIssueList"), doc.select("div.tab-pane.user-project-list").map { it.id() })
        assertTrue(doc.selectFirst("#myOrganizationList")!!.hasClass("active"))
    }

    @Test
    fun `즐겨찾기 탭의 첫 묶음은 내 프로젝트이다`() {
        val me = newUser()
        val a = project(me.loginId!!, "beta"); val b = project(me.loginId!!, "alpha", ProjectScope.PRIVATE)
        projectUsers.save(ProjectUser(user = me, project = b, role = roles.findById(RoleType.MANAGER.id).get()))
        project("other", "zzz")
        val group = fragment(login(me)).selectFirst("#myOrganizationList li.org-li")!!
        assertEquals(me.loginId, group.selectFirst(".org-name")!!.text())
        val items = group.select("ul.project-ul li.user-li")
        assertEquals(listOf("alpha", "beta"), items.map { it.selectFirst(".project-name")!!.text().trim() })
        assertEquals("/${me.loginId}/alpha/go", items[0].attr("data-location"))
        assertTrue(items[0].select(".yobicon-lock").isNotEmpty()); assertTrue(items[1].select(".yobicon-lock").isEmpty())
        assertTrue(items.all { it.hasClass("hide") })
        assertEquals("beta 개요", items[1].selectFirst("[data-toggle=popover]")!!.attr("data-content"))
        assertEquals(a.id.toString(), items[1].selectFirst(".star-project")!!.attr("data-project-id"))
        assertNotNull(b.id)
    }

    @Test
    fun `즐겨찾기한 프로젝트는 항상 보이고 별이 채워진다`() {
        val me = newUser(); val p = project(me.loginId!!, "mine")
        favoriteProjects.save(FavoriteProject(user = me, project = p, owner = p.owner, projectName = p.name))
        val item = fragment(login(me)).selectFirst("#myOrganizationList li.org-li ul.project-ul li.user-li")!!
        assertTrue(item.hasClass("show-always")); assertFalse(item.hasClass("hide"))
        assertTrue(item.selectFirst(".star")!!.hasClass("starred"))
    }

    @Test
    fun `즐겨찾기한 프로젝트의 소유 조직은 묶음으로 보이고 조직 즐겨찾기가 먼저 나온다`() {
        val me = newUser(); val orgA = organization("aorg"); val orgB = organization("borg")
        val inA = project("aorg", "pa", org = orgA); val inB = project("borg", "pb", org = orgB); project("borg", "pc", org = orgB)
        favoriteProjects.save(FavoriteProject(user = me, project = inA, owner = "aorg", projectName = "pa"))
        favoriteProjects.save(FavoriteProject(user = me, project = inB, owner = "borg", projectName = "pb"))
        favoriteOrganizations.save(FavoriteOrganization(user = me, organization = orgB, organizationName = "borg"))
        val groups = fragment(login(me)).select("#myOrganizationList li.org-li")
        assertEquals(listOf(me.loginId, "borg", "aorg"), groups.map { it.selectFirst(".org-name")!!.text() })
        val borg = groups[1]
        assertTrue(borg.hasClass("favored")); assertTrue(borg.selectFirst(".star-org .star")!!.hasClass("starred"))
        assertEquals(orgB.id.toString(), borg.selectFirst(".star-org")!!.attr("data-organization-id"))
        assertEquals(listOf("pb", "pc"), borg.select("ul.project-ul .project-name").map { it.text().trim() })
        assertTrue(borg.selectFirst("li.user-li")!!.hasClass("show-always"))
        assertFalse(groups[2].selectFirst(".star-org .star")!!.hasClass("starred"))
    }

    @Test
    fun `조직 묶음의 프로젝트 개수는 조직 관리자와 사이트 관리자에게만 보인다`() {
        val admin = newUser(); val org = organization("countorg")
        project("countorg", "p1", org = org); val p2 = project("countorg", "p2", org = org)
        favoriteProjects.save(FavoriteProject(user = admin, project = p2, owner = "countorg", projectName = "p2"))
        val plain = fragment(login(admin)).select("#myOrganizationList li.org-li")[1]
        assertEquals("", plain.selectFirst(".project-owner")!!.text())
        organizationUsersFor(admin, org)
        assertEquals("2", fragment(login(admin)).select("#myOrganizationList li.org-li")[1].selectFirst(".project-owner")!!.text())
    }

    private fun organizationUsersFor(user: User, org: Organization) {
        entityManager.persist(dev.yona.domain.project.OrganizationUser(user = user, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get()))
        entityManager.flush()
    }

    @Test
    fun `소유 조직이 아닌 즐겨찾기 프로젝트는 기타 목록에 있다`() {
        val me = newUser(); val other = project("somebody", "tool")
        project(me.loginId!!, "mine") // 내 프로젝트나 조직이 하나도 없으면 목록 전체가 "결과 없음"이다(v1.6 조건).
        favoriteProjects.save(FavoriteProject(user = me, project = other, owner = "somebody", projectName = "tool"))
        // v1.6 마크업은 빈 `ul.etc-favorites` 다음에 줄들이 이어진다(같은 `ul#organizations`의 자식이다).
        val doc = fragment(login(me))
        assertTrue(doc.select("#myOrganizationList ul.etc-favorites").isNotEmpty())
        val etc = doc.select("#myOrganizationList ul#organizations > li.user-li")
        assertEquals(listOf("tool"), etc.map { it.selectFirst(".project-name")!!.text().trim() })
        assertEquals("/somebody", etc[0].selectFirst(".project-owner a")!!.attr("href"))
        assertTrue(etc[0].selectFirst(".star")!!.hasClass("starred"))
    }

    @Test
    fun `내 프로젝트도 조직도 없으면 즐겨찾기 탭은 결과 없음이다`() {
        val me = newUser(); val other = project("somebody", "tool")
        favoriteProjects.save(FavoriteProject(user = me, project = other, owner = "somebody", projectName = "tool"))
        val doc = fragment(login(me))
        assertEquals("No results", doc.selectFirst("#myOrganizationList #organizations.no-result")!!.text())
        assertTrue(doc.select("#myOrganizationList li.user-li").isEmpty())
    }

    @Test
    fun `구독 프로젝트는 이름을 대소문자 구분해 정렬한다 (v1_6 Ebean 메모리 정렬은 compareTo)`() {
        val me = newUser()
        listOf("alpha", "Zeta", "beta").forEachIndexed { i, name ->
            val p = project("o$i", name)
            watches.save(Watch(user = me, resourceType = ResourceType.PROJECT, resourceId = p.id.toString()))
        }
        val tab = fragment(login(me)).selectFirst("#myProjectList")!!
        // 대문자가 소문자보다 앞선다
        assertEquals(listOf("Zeta", "alpha", "beta"), tab.select("#watching li.user-li .project-name").map { it.text().trim() })
    }

    @Test
    fun `프로젝트 탭의 네 목록`() {
        val me = newUser(); val member = newUser()
        val visited = project("v1", "visited"); val created1 = project(me.loginId!!, "mine-old", created = 1_000L); val created2 = project(me.loginId!!, "mine-new", created = 2_000L)
        val watched = project("w1", "watched"); val joined = project("j1", "joined")
        recentProjects.save(RecentProject(userId = me.id, owner = "v1", project = visited, projectName = "visited"))
        watches.save(Watch(user = me, resourceType = ResourceType.PROJECT, resourceId = watched.id.toString()))
        projectUsers.save(ProjectUser(user = me, project = joined, role = roles.findById(RoleType.MEMBER.id).get()))
        projectUsers.save(ProjectUser(user = me, project = created1, role = roles.findById(RoleType.MANAGER.id).get()))
        val tab = fragment(login(me)).selectFirst("#myProjectList")!!
        fun names(id: String) = tab.select("#$id li.user-li .project-name").map { it.text().trim() }
        assertEquals(listOf("visited"), names("recentlyVisited"))
        assertEquals(listOf("mine-new", "mine-old"), names("createdByMe"))
        assertEquals(listOf("watched"), names("watching"))
        assertEquals(listOf("joined"), names("joinmember"))
        assertTrue(tab.selectFirst("#recentlyVisited")!!.hasClass("active"))
        assertEquals(listOf("#recentlyVisited", "#createdByMe", "#watching", "#joinmember"), tab.select("ul.nav-subtab a").map { it.attr("href") })
        assertNotNull(member.id)
    }

    @Test
    fun `비어 있는 목록은 결과 없음이다`() {
        val tab = fragment(login(newUser())).selectFirst("#myProjectList")!!
        assertEquals("No results", tab.selectFirst("#recentlyVisited.no-result")!!.text())
        assertEquals("No results", tab.selectFirst("#joinmember.no-result")!!.text())
        assertEquals("No results", fragment(login(newUser())).selectFirst("#myRecentIssueList #recentlyVisitedIssues.no-result")!!.text())
    }

    @Test
    fun `최근 본 이슈와 글은 최근 순서로 제목과 주소가 있다`() {
        val me = newUser()
        recentIssues.save(RecentIssue(user = me, title = "첫째", url = "/o/p/issue/3", createdDate = Date()))
        recentIssues.save(RecentIssue(user = me, postingId = 9, title = "둘째", url = "/o/p/post/4", createdDate = Date()))
        val items = fragment(login(me)).select("#myRecentIssueList #recentlyVisitedIssues li.user-li")
        assertEquals(listOf("둘째", "첫째"), items.map { it.selectFirst(".issue-title")!!.text() })
        assertEquals("/o/p/post/4", items[0].attr("data-location"))
        assertEquals("p #4", items[0].selectFirst("[data-toggle=popover]")!!.attr("data-content"))
        assertEquals("p #3", items[1].selectFirst("[data-toggle=popover]")!!.attr("data-content"))
    }

    @Test
    fun `읽을 수 없는 프로젝트는 즐겨찾기 묶음에 보이지 않는다`() {
        val me = newUser(); val org = organization("secretorg")
        val open = project("secretorg", "open", org = org); val secret = project("secretorg", "secret", ProjectScope.PRIVATE, org = org)
        favoriteProjects.save(FavoriteProject(user = me, project = open, owner = "secretorg", projectName = "open"))
        favoriteProjects.save(FavoriteProject(user = me, project = secret, owner = "secretorg", projectName = "secret"))
        val names = fragment(login(me)).select("#myOrganizationList li.org-li")[1].select("ul.project-ul .project-name").map { it.text().trim() }
        assertEquals(listOf("open"), names)
    }

    @Test
    fun `익명도 빈 내용을 받는다`() {
        val doc = fragment()
        assertEquals(3, doc.select("div.tab-pane.user-project-list").size)
        assertTrue(doc.select("li.user-li").isEmpty())
    }

    // --- 사이드바 페이지 ---

    @Test
    fun `사이드바 페이지는 왼쪽 메뉴와 본문 프레임을 가진다`() {
        val me = newUser()
        val doc = Jsoup.parse(mockMvc.perform(get("/user/sidebar").param("path", "/admin/demo/issues").param("hash", "comment-3").session(login(me))).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("/admin/demo/issues#comment-3", doc.selectFirst("iframe#mainFrameId")!!.attr("src"))
        assertEquals("mainFrame", doc.selectFirst("iframe#mainFrameId")!!.attr("name"))
        val sidebar = doc.selectFirst("div#sidebar.sidebar")!!
        assertEquals("/${me.loginId}", sidebar.selectFirst(".user-menu a")!!.attr("href"))
        assertTrue(sidebar.select("#myOrganizationList, #myProjectList, #myRecentIssueList").size == 3)
        assertTrue(doc.body().hasClass("framed-body"))
        assertTrue(doc.select("script").any { "UsermenuUrl" in it.data() && "usermenuTabContentList" in it.data() })
    }

    @Test
    fun `해시가 없으면 경로만, 경로가 없으면 로그인 기본 화면이다`() {
        val me = newUser()
        val session = login(me)
        assertEquals("/a/b", Jsoup.parse(mockMvc.perform(get("/user/sidebar").param("path", "/a/b").param("hash", "").session(session)).andReturn().response.contentAsString).selectFirst("iframe#mainFrameId")!!.attr("src"))
        assertEquals("/notifications", Jsoup.parse(mockMvc.perform(get("/user/sidebar").param("hash", "").session(session)).andReturn().response.contentAsString).selectFirst("iframe#mainFrameId")!!.attr("src"))
    }

    @Test
    fun `hash 파라미터가 없으면 500이다 (v1_6 layout_framed의 None_get)`() {
        mockMvc.perform(get("/user/sidebar").param("path", "/a")).andExpect(status().isInternalServerError)
        mockMvc.perform(get("/user/sidebar").param("path", "/a").param("hash", "")).andExpect(status().isOk)
    }

    @Test
    fun `익명은 사이드바 목록 없이 프레임만 본다`() {
        val doc = Jsoup.parse(mockMvc.perform(get("/user/sidebar").param("path", "/projects").param("hash", "")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertTrue(doc.select("div#sidebar .user-menu-wrap").isEmpty())
        assertEquals("/projects", doc.selectFirst("iframe#mainFrameId")!!.attr("src"))
    }

    // --- 프로젝트 바로가기 ---

    @Test
    fun `프로젝트 바로가기는 이슈, 게시판, 홈 순서로 보낸다`() {
        val p = project("goowner", "goproj")
        val setting = entityManager.createQuery("select m from ProjectMenuSetting m where m.project.id = :p", ProjectMenuSetting::class.java).setParameter("p", p.id).resultList.firstOrNull()
            ?: entityManager.merge(ProjectMenuSetting(project = p))
        setting.issue = false; setting.board = true; entityManager.persist(setting); entityManager.flush()
        mockMvc.perform(get("/goowner/goproj/go")).andExpect(status().is3xxRedirection).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", "/goowner/goproj/posts"))
        setting.board = false; entityManager.flush()
        mockMvc.perform(get("/goowner/goproj/go")).andExpect(status().is3xxRedirection).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", "/goowner/goproj"))
        setting.issue = true; entityManager.flush()
        mockMvc.perform(get("/goowner/goproj/go")).andExpect(status().isOk)
    }
}
