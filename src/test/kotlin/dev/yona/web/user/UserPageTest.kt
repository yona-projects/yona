package dev.yona.web.user

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.servlet.http.Cookie
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사용자 페이지(`GET /:user`)를 검증한다.
 *
 * 원본: `app/controllers/UserApp.java:724-875`(`userInfo`), `app/views/user/view.scala.html`, `partial_issues.scala.html`,
 * `partial_pullRequests.scala.html`, `partial_projectlist.scala.html`, `app/models/Issue.java:524-529`,
 * `app/models/PullRequest.java:219-225`.
 */
@AutoConfigureMockMvc
@Transactional
class UserPageTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository

    private var sequence = 0

    private fun newUser(state: UserState = UserState.ACTIVE, guest: Boolean = false, englishName: String? = null, created: Date = Date()): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "up$n", name = "이름$n", englishName = englishName, email = "up$n@example.com", state = state, isGuest = guest, createdDate = created, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun project(owner: User, name: String, scope: ProjectScope = ProjectScope.PUBLIC, pushed: Date? = null): Project =
        projects.save(Project(owner = owner.loginId, name = name, vcs = "GIT", projectScope = scope, createdDate = Date(), lastPushedDate = pushed))

    private fun join(user: User, project: Project, role: RoleType = RoleType.MEMBER) =
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))

    private fun daysAgo(days: Int) = Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()))

    private var issueNumber = 0L

    private fun issue(project: Project, title: String, author: User, assignee: User? = null, updated: Date = daysAgo(1), state: State = State.OPEN): Issue =
        issues.save(Issue(
            project = project, number = ++issueNumber, title = title, body = "b", state = state, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
            assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, numOfComments = 0, weight = 0, createdDate = updated, updatedDate = updated
        ))

    private fun pullRequest(to: Project, from: Project, contributor: User, title: String, updated: Date = daysAgo(1), state: State = State.OPEN): PullRequest =
        pullRequests.save(PullRequest(title = title, fromProject = from, toProject = to, contributor = contributor, created = updated, updated = updated, state = state, number = ++issueNumber))

    private fun page(path: String, session: MockHttpSession? = null, vararg params: Pair<String, String>): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) }; params.forEach { (k, v) -> param(k, v) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun titles(doc: Document, selector: String) = doc.select("$selector li.post-item a.title").filter { !it.hasClass("project") && it.text().isNotBlank() }.map { it.text().trim() }

    @Test
    fun `프로필 상자에 이름과 로그인 ID와 이메일과 가입일이 보인다`() {
        val u = newUser(englishName = "Gil Dong", created = Date(1_709_640_000_000L))
        val doc = page("/${u.loginId}")
        assertEquals(u.name, doc.selectFirst(".site-breadcrumb-inner h3")!!.text())
        assertEquals("Gil Dong", doc.selectFirst(".whoami .name")!!.text())
        assertEquals("@${u.loginId}", doc.selectFirst(".whoami .loginid")!!.text())
        assertEquals(u.email, doc.selectFirst(".whoami .email")!!.text())
        assertEquals("Mar 05, 2024", doc.selectFirst(".user-since .since")!!.text())
        assertTrue(doc.select(".whoami .edit").isEmpty())
    }

    @Test
    fun `내 페이지에는 프로필 수정 버튼이 있다`() {
        val me = newUser()
        val doc = page("/${me.loginId}", login(me))
        assertEquals("/user/editform", doc.selectFirst(".whoami .edit a")!!.attr("href"))
    }

    @Test
    fun `사이트 관리자와 잠긴 사용자와 게스트에게 표시가 붙는다`() {
        val admin = newUser(); siteAdmins.save(SiteAdmin(admin = admin))
        assertEquals("SITE ADMIN", page("/${admin.loginId}").selectFirst(".user-status .label-success")!!.text())
        assertEquals("BLOCKED", page("/${newUser(state = UserState.LOCKED).loginId}").selectFirst(".user-status .label-important")!!.text())
        assertEquals("OUR GUEST", page("/${newUser(guest = true).loginId}").selectFirst(".guest-user .left-mark")!!.text())
    }

    @Test
    fun `조직 이름이면 조직 페이지로 보낸다`() {
        organizations.save(Organization(name = "acme", descr = "d", created = Date()))
        mockMvc.perform(get("/acme")).andExpect(status().isSeeOther).andExpect(redirectedUrl("/organizations/acme"))
    }

    @Test
    fun `없는 사용자는 404이다`() {
        mockMvc.perform(get("/nobody-here")).andExpect(status().isNotFound)
    }

    @Test
    fun `daysAgo를 주지 않으면 14일이고 쿠키에 저장된다`() {
        val u = newUser()
        val result = mockMvc.perform(get("/${u.loginId}")).andExpect(status().isOk).andReturn()
        assertEquals("14", Jsoup.parse(result.response.contentAsString).selectFirst("#daysAgoBtn")!!.attr("value"))
        assertEquals("14", result.response.getCookie("daysAgo")!!.value)
    }

    @Test
    fun `daysAgo를 주면 그 값을 쓰고 쿠키에 저장한다`() {
        val u = newUser()
        val result = mockMvc.perform(get("/${u.loginId}").param("daysAgo", "30")).andReturn()
        assertEquals("30", Jsoup.parse(result.response.contentAsString).selectFirst("#daysAgoBtn")!!.attr("value"))
        assertEquals("30", result.response.getCookie("daysAgo")!!.value)
    }

    @Test
    fun `daysAgo를 주지 않으면 쿠키 값을 쓴다`() {
        val u = newUser()
        val result = mockMvc.perform(get("/${u.loginId}").cookie(Cookie("daysAgo", "7"))).andReturn()
        assertEquals("7", Jsoup.parse(result.response.contentAsString).selectFirst("#daysAgoBtn")!!.attr("value"))
        assertNull(result.response.getCookie("daysAgo"))
    }

    @Test
    fun `daysAgo가 음수이면 1일이다`() {
        val u = newUser()
        val result = mockMvc.perform(get("/${u.loginId}").param("daysAgo", "-3")).andReturn()
        assertEquals("1", Jsoup.parse(result.response.contentAsString).selectFirst("#daysAgoBtn")!!.attr("value"))
        assertEquals("1", result.response.getCookie("daysAgo")!!.value)
    }

    @Test
    fun `selected로 활성 탭을 고르고 기본은 issues이다`() {
        val u = newUser()
        fun active(doc: Document) = doc.select(".user-stream-box > ul.nav-tabs > li.active a").map { it.attr("href") }
        assertEquals(listOf("#issues"), active(page("/${u.loginId}")))
        assertEquals(listOf("#projects"), active(page("/${u.loginId}", null, "selected" to "projects")))
        assertTrue(page("/${u.loginId}", null, "selected" to "projects").selectFirst("#projects")!!.hasClass("active"))
    }

    @Test
    fun `이슈 탭에는 최근 기간에 갱신된 담당 이슈와 작성 이슈가 상태별로 보인다`() {
        val u = newUser(); val other = newUser(); val p = project(other, "p")
        issue(p, "내가 쓴 열린 이슈", u); issue(p, "내게 할당된 닫힌 이슈", other, assignee = u, state = State.CLOSED)
        issue(p, "남의 이슈", other); issue(p, "오래된 이슈", u, updated = daysAgo(20))
        val doc = page("/${u.loginId}")
        assertEquals(listOf("내가 쓴 열린 이슈"), titles(doc, "#openIssues"))
        assertEquals(listOf("내게 할당된 닫힌 이슈"), titles(doc, "#closedIssues"))
        assertEquals(listOf("1", "1"), doc.select("#issues > ul.nav-tabs .num-badge").map { it.text() })
        assertEquals("2", doc.selectFirst(".user-stream-box > ul.nav-tabs li:first-child .num-badge")!!.text())
    }

    @Test
    fun `daysAgo를 늘리면 오래된 이슈도 보인다`() {
        val u = newUser(); val p = project(u, "p")
        issue(p, "오래된 이슈", u, updated = daysAgo(20))
        assertEquals(listOf("오래된 이슈"), titles(page("/${u.loginId}", null, "daysAgo" to "30"), "#openIssues"))
    }

    @Test
    fun `읽을 수 없는 프로젝트의 이슈는 보이지 않는다`() {
        val u = newUser(); val viewer = newUser()
        val hidden = project(u, "hidden", ProjectScope.PRIVATE); val open = project(u, "open")
        issue(hidden, "비공개 이슈", u); issue(open, "공개 이슈", u)
        assertEquals(listOf("공개 이슈"), titles(page("/${u.loginId}", login(viewer)), "#openIssues"))
        assertEquals(listOf("공개 이슈"), titles(page("/${u.loginId}"), "#openIssues"))
        join(viewer, hidden)
        assertEquals(setOf("공개 이슈", "비공개 이슈"), titles(page("/${u.loginId}", login(viewer)), "#openIssues").toSet())
    }

    @Test
    fun `풀 리퀘스트 탭에는 최근에 갱신한 내가 보낸 풀 리퀘스트가 보인다`() {
        val u = newUser(); val other = newUser(); val target = project(other, "target"); val fork = project(u, "fork")
        pullRequest(target, fork, u, "내 PR"); pullRequest(target, fork, other, "남의 PR"); pullRequest(target, fork, u, "오래된 PR", updated = daysAgo(20))
        val doc = page("/${u.loginId}")
        assertEquals(listOf("내 PR"), titles(doc, "#pullRequests"))
        assertEquals("1", doc.select(".user-stream-box > ul.nav-tabs li")[1].selectFirst(".num-badge")!!.text())
    }

    @Test
    fun `프로젝트 탭에는 읽을 수 있는 소속 프로젝트가 최근 푸시 순 다음 이름 순으로 보인다`() {
        val u = newUser(); val other = newUser()
        val a = project(other, "a-none"); val b = project(other, "b-old", pushed = daysAgo(5)); val c = project(other, "c-new", pushed = daysAgo(1))
        val d = project(other, "d-none"); val hidden = project(other, "z-hidden", ProjectScope.PRIVATE)
        listOf(a, b, c, d, hidden).forEach { join(u, it) }
        val doc = page("/${u.loginId}", null, "selected" to "projects")
        assertEquals(listOf("c-new", "b-old", "a-none", "d-none"), doc.select("#projects li.project .project-name").map { it.text() })
    }

    @Test
    fun `내 페이지의 프로젝트 목록에는 소유하지 않은 프로젝트의 탈퇴 링크가 있다`() {
        val me = newUser(); val other = newUser()
        val joined = project(other, "joined"); val own = project(me, "own")
        join(me, joined); join(me, own, RoleType.MANAGER)
        val doc = page("/${me.loginId}", login(me), "selected" to "projects")
        assertEquals(listOf("/info/leave/${other.loginId}/joined"), doc.select("#projects a.leaveProject").map { it.attr("href") })
        assertTrue(page("/${me.loginId}", null, "selected" to "projects").select("a.leaveProject").isEmpty())
    }

    @Test
    fun `게스트가 보면 활동 탭이 없다`() {
        val u = newUser(); val guest = newUser(guest = true)
        val doc = page("/${u.loginId}", login(guest))
        assertTrue(doc.select(".user-stream-box ul.nav-tabs").isEmpty())
        assertNotNull(doc.selectFirst(".user-info-box"))
    }
}
