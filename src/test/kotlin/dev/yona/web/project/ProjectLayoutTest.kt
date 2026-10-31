package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.misc.FavoriteProject
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.misc.Watch
import dev.yona.domain.misc.WatchRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 레이아웃(`projectLayout`), 프로젝트 메뉴(`projectMenu`), 프로젝트 헤더(`project/header`)를 검증한다.
 *
 * 원본: `app/views/projectLayout.scala.html`, `app/views/projectMenu.scala.html`, `app/views/project/header.scala.html`,
 * `app/views/common/navbar.scala.html`(프로젝트 검색 범위).
 */
@AutoConfigureMockMvc
@Transactional
class ProjectLayoutTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var attachments: AttachmentRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var favorites: FavoriteProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var postings: PostingRepository

    private var sequence = 0

    private fun newUser(prefix: String = "pl"): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val n = ++sequence
        return users.save(
            User(
                loginId = "$prefix$n", name = "$prefix$n", email = "$prefix$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession {
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
        return session
    }

    private fun newProject(
        scope: ProjectScope = ProjectScope.PUBLIC,
        organization: Organization? = null,
        original: Project? = null,
        codeMemberOnly: Boolean = false,
        menu: ProjectMenuSetting.() -> Unit = {}
    ): Project {
        val n = ++sequence
        val project = projects.save(
            Project(
                owner = "owner$n", name = "proj$n", vcs = "GIT", projectScope = scope, organization = organization,
                originalProject = original, isCodeAccessibleMemberOnly = codeMemberOnly
            )
        )
        menuSettings.save(
            ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true)
                .apply(menu)
        )
        return project
    }

    private fun join(user: User, project: Project, role: RoleType) {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
    }

    private fun page(project: Project, session: MockHttpSession? = null, query: String = ""): Document {
        val request = get("/__test/project-page/${project.id}$query")
        session?.let { request.session(it) }
        return Jsoup.parse(mockMvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString)
    }

    // --- 레이아웃 ---

    @Test
    fun `제목이 없으면 프로젝트 이름과 소유자로 제목을 만든다`() {
        val project = newProject()
        assertEquals("${project.name} - ${project.owner}", page(project).title())
    }

    @Test
    fun `제목 키가 있으면 번역한 제목에 소유자와 프로젝트 이름을 붙인다`() {
        val project = newProject()
        assertEquals("Project home - ${project.owner}/${project.name}", page(project, query = "?title=title.projectHome").title())
    }

    @Test
    fun `이미 번역된 제목도 그대로 쓴다`() {
        val project = newProject()
        assertEquals("You are not authorized - ${project.owner}/${project.name}", page(project, query = "?title=You are not authorized").title())
    }

    @Test
    fun `본문에는 prj 테마가 붙고 상단 메뉴는 프로젝트용이다`() {
        val project = newProject()
        val doc = page(project)
        assertEquals("prj", doc.body().className())
        assertNotNull(doc.selectFirst("header.gnb-outer.project-header"))
        assertNotNull(doc.selectFirst("#project-test-body"))
        assertNotNull(doc.selectFirst("footer.page-footer-outer"))
    }

    @Test
    fun `검색 범위는 프로젝트이고 검색은 프로젝트 주소로 간다`() {
        val project = newProject()
        val doc = page(project)
        val form = doc.selectFirst("form[name=gnb-search-form]")
        assertEquals("/${project.owner}/${project.name}/search", form?.attr("action"))
        assertEquals("This Project", form?.selectFirst("#gnb-search-scope-title")?.text()?.trim())
        val scopes = form!!.select("a[data-toggle=search-scope]").map { it.attr("data-action") }
        assertEquals(listOf("/${project.owner}/${project.name}/search", "/search"), scopes)
        assertTrue(form.selectFirst(".search-box")!!.hasClass("select"))
    }

    @Test
    fun `조직에 속한 프로젝트는 조직 범위 검색도 제공한다`() {
        val org = organizations.save(Organization(name = "org${++sequence}"))
        val project = newProject(organization = org)
        project.owner = org.name
        projects.save(project)
        val scopes = page(project).select("a[data-toggle=search-scope]").map { it.attr("data-action") }
        assertEquals(
            listOf("/${org.name}/${project.name}/search", "/organizations/${org.name}/search", "/search"),
            scopes
        )
    }

    // --- 헤더 ---

    @Test
    fun `로고가 없으면 기본 로고와 기본 배경을 쓴다`() {
        val project = newProject()
        val header = page(project).selectFirst(".project-header-outer")
        assertNotNull(header)
        assertTrue(header.attr("style").contains("/assets/images/project_default.jpg"))
        assertEquals("/assets/images/project_default_logo.png", header.selectFirst(".project-header-avatar img")?.attr("src"))
    }

    @Test
    fun `프로젝트에 올린 첨부가 있으면 로고와 배경으로 쓴다`() {
        val project = newProject()
        val file = attachments.save(Attachment(containerType = ResourceType.PROJECT, containerId = project.id!!, name = "logo.png"))
        val header = page(project).selectFirst(".project-header-outer")!!
        assertTrue(header.attr("style").contains("/files/${file.id}"))
        assertEquals("/files/${file.id}", header.selectFirst(".project-header-avatar img")?.attr("src"))
    }

    @Test
    fun `이동 경로에 소유자와 프로젝트 이름 링크가 있다`() {
        val project = newProject()
        val crumb = page(project).selectFirst(".project-breadcrumb")!!
        assertEquals("/${project.owner}", crumb.selectFirst(".project-author a")?.attr("href"))
        assertEquals(project.owner, crumb.selectFirst(".project-author a")?.text())
        assertEquals("/${project.owner}/${project.name}", crumb.selectFirst(".project-name a")?.attr("href"))
        assertEquals(project.name, crumb.selectFirst(".project-name a")?.text())
        assertNull(crumb.selectFirst(".project-private"))
        assertNull(crumb.selectFirst(".project-protected"))
    }

    @Test
    fun `비공개와 보호 프로젝트는 표시가 붙는다`() {
        val privateDoc = page(newProject(scope = ProjectScope.PRIVATE), login(newUser()).also { })
        // 비공개 프로젝트는 비멤버에게 읽기 권한이 없어도 이 테스트 화면은 레이아웃만 확인한다.
        assertNotNull(privateDoc.selectFirst(".project-breadcrumb .project-private i.yobicon-lock"))
        val protectedDoc = page(newProject(scope = ProjectScope.PROTECTED))
        assertEquals("G", protectedDoc.selectFirst(".project-breadcrumb .project-protected")?.text()?.trim())
        assertEquals("Group Project", protectedDoc.selectFirst(".project-protected")?.attr("title"))
    }

    @Test
    fun `포크한 프로젝트는 원본 링크를 보여 준다`() {
        val original = newProject()
        val fork = newProject(original = original)
        val doc = page(fork)
        assertTrue(doc.selectFirst(".project-breadcrumb-wrap")!!.hasClass("fork"))
        val origin = doc.selectFirst(".project-origin a.project-origin-name")
        assertEquals("/${original.owner}/${original.name}", origin?.attr("href"))
        assertEquals("${original.owner} / ${original.name}", origin?.text())
        assertEquals("Forked from", doc.selectFirst(".project-origin-title")?.text())
    }

    @Test
    fun `즐겨찾기한 프로젝트의 별에 starred가 붙는다`() {
        val project = newProject()
        val user = newUser()
        assertFalse(page(project, login(user)).selectFirst(".user-project-list i.star")!!.hasClass("starred"))
        favorites.save(FavoriteProject(user = user, project = project, owner = project.owner, projectName = project.name))
        val star = page(project, login(user)).selectFirst(".user-project-list")!!
        assertTrue(star.selectFirst("i.star")!!.hasClass("starred"))
        assertEquals(project.id.toString(), star.attr("data-project-id"))
    }

    // --- 가입 신청과 구독 ---

    @Test
    fun `멤버가 아닌 로그인 사용자에게는 가입 신청 버튼이 나온다`() {
        val project = newProject()
        val doc = page(project, login(newUser()))
        val button = doc.selectFirst(".project-util a.enrollBtn")
        assertNotNull(button)
        assertEquals("/${project.owner}/${project.name}/enroll", button.attr("href"))
        assertEquals("Send sign-up request", button.text().trim())
    }

    @Test
    fun `이미 가입을 신청했으면 취소 버튼이 나온다`() {
        val project = newProject()
        val user = newUser()
        user.enrolledProjects.add(project)
        users.save(user)
        val button = page(project, login(user)).selectFirst(".project-util a.enrollBtn")
        assertEquals("/${project.owner}/${project.name}/cancel/enroll", button?.attr("href"))
        assertEquals("Cancel sign-up request", button?.text()?.trim())
    }

    @Test
    fun `멤버에게는 가입 신청 버튼이 없다`() {
        val project = newProject()
        val member = newUser().also { join(it, project, RoleType.MEMBER) }
        assertNull(page(project, login(member)).selectFirst(".project-util a.enrollBtn"))
    }

    @Test
    fun `익명 사용자에게는 가입과 구독 버튼이 없다`() {
        val doc = page(newProject())
        assertNull(doc.selectFirst(".project-util a.enrollBtn"))
        assertNull(doc.selectFirst(".watch-btn"))
    }

    @Test
    fun `구독할 수 있는 사용자에게는 구독자 수와 구독 버튼이 나온다`() {
        val project = newProject()
        val user = newUser()
        val other = newUser()
        watches.save(Watch(user = other, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
        val doc = page(project, login(user))
        val count = doc.selectFirst(".watch-btn a.watcher-count")
        assertEquals("1", count?.text()?.trim())
        assertEquals("/${project.owner}/${project.name}/watchers", count?.attr("href"))
        assertFalse(count!!.hasClass("btn-success"))
        val action = doc.selectFirst(".watch-btn a.watchBtn")
        assertEquals("/${project.owner}/${project.name}/watch", action?.attr("href"))
        assertEquals("Watch", action?.text()?.trim())
        assertEquals("You are not watching the ${project.name} project.", doc.selectFirst(".watch-btn .pop-title")?.text())
    }

    @Test
    fun `구독 중이면 구독 해제 버튼이 나오고 개수가 btn-success로 바뀐다`() {
        val project = newProject()
        val user = newUser()
        watches.save(Watch(user = user, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
        val doc = page(project, login(user))
        assertTrue(doc.selectFirst(".watch-btn a.watcher-count")!!.hasClass("btn-success"))
        val action = doc.selectFirst(".watch-btn a.watchBtn")
        assertEquals("/${project.owner}/${project.name}/unwatch", action?.attr("href"))
        assertEquals("Unwatch", action?.text()?.trim())
        assertEquals("/user/editform/notifications#${project.id}", doc.selectFirst(".watch-btn .btn-wrap a:not(.watchBtn)")?.attr("href"))
    }

    // --- 메뉴 ---

    @Test
    fun `메뉴는 프로젝트 메뉴 설정에 따라 항목을 보여 준다`() {
        val project = newProject()
        val items = page(project).select(".project-menu-nav.project-menu-gruop > li")
        assertEquals(
            listOf("Project home", "Code", "Issue", "Pull request", "Review", "Milestone", "Board"),
            items.map { it.selectFirst(".menu-name")!!.text() }
        )
        assertEquals(
            listOf(
                "/${project.owner}/${project.name}", "/${project.owner}/${project.name}/code",
                "/${project.owner}/${project.name}/issues", "/${project.owner}/${project.name}/pullRequests",
                "/${project.owner}/${project.name}/reviews", "/${project.owner}/${project.name}/milestones",
                "/${project.owner}/${project.name}/posts"
            ),
            items.map { it.selectFirst("a")!!.attr("href") }
        )
        assertEquals(listOf("H", "C", "I", "P", "R", "M", "B"), items.map { it.selectFirst(".short-menu")!!.text().trim() })
    }

    @Test
    fun `꺼진 메뉴는 숨긴다`() {
        val project = newProject { code = false; board = false; review = false; pullRequest = false }
        val names = page(project).select(".project-menu-nav.project-menu-gruop .menu-name").map { it.text() }
        assertEquals(listOf("Project home", "Issue", "Milestone"), names)
    }

    @Test
    fun `포크한 프로젝트의 풀 리퀘스트 메뉴는 보낸 요청 목록으로 간다`() {
        val fork = newProject(original = newProject())
        val href = page(fork).select(".project-menu-nav li a").first { it.text().contains("Pull request") }.attr("href")
        assertEquals("/${fork.owner}/${fork.name}/sentPullRequests", href)
    }

    @Test
    fun `풀 리퀘스트 메뉴는 GIT 프로젝트에만 나온다`() {
        val project = newProject()
        project.vcs = "Subversion"
        projects.save(project)
        assertFalse(page(project).select(".menu-name").any { it.text() == "Pull request" })
    }

    @Test
    fun `코드 열람이 멤버 전용이면 비멤버에게 코드와 풀 리퀘스트와 리뷰 메뉴를 숨긴다`() {
        val project = newProject(codeMemberOnly = true)
        val names = page(project).select(".project-menu-nav.project-menu-gruop .menu-name").map { it.text() }
        assertEquals(listOf("Project home", "Issue", "Milestone", "Board"), names)
        val member = newUser().also { join(it, project, RoleType.MEMBER) }
        val memberNames = page(project, login(member)).select(".project-menu-nav.project-menu-gruop .menu-name").map { it.text() }
        assertTrue(memberNames.containsAll(listOf("Code", "Pull request", "Review")))
    }

    @Test
    fun `현재 메뉴에 active가 붙는다`() {
        val project = newProject()
        val active = page(project, query = "?menu=ISSUE").select(".project-menu-nav.project-menu-gruop > li.active")
        assertEquals(1, active.size)
        assertEquals("Issue", active.first()!!.selectFirst(".menu-name")?.text())
    }

    @Test
    fun `이슈와 게시글 개수가 메뉴 옆에 나온다`() {
        val project = newProject()
        issues.save(Issue(project = project, number = 1, state = State.OPEN))
        issues.save(Issue(project = project, number = 2, state = State.OPEN))
        issues.save(Issue(project = project, number = 3, state = State.CLOSED))
        postings.save(Posting(project = project, number = 1))
        val items = page(project).select(".project-menu-nav.project-menu-gruop > li")
        fun badge(name: String) = items.first { it.selectFirst(".menu-name")!!.text() == name }.selectFirst(".project-menu-count")?.text()
        assertEquals("2", badge("Issue"))
        assertEquals("1", badge("Board"))
        assertNull(badge("Milestone"))
        assertNull(badge("Pull request"), "0이면 표시하지 않는다")
    }

    @Test
    fun `설정 메뉴는 수정 권한이 있는 사용자에게만 나온다`() {
        val project = newProject()
        val manager = newUser().also { join(it, project, RoleType.MANAGER) }
        val setting = page(project, login(manager)).selectFirst(".project-setting a")
        assertEquals("/${project.owner}/${project.name}/settingform", setting?.attr("href"))
        assertNull(page(project).selectFirst(".project-setting"))
        assertNull(page(project, login(newUser())).selectFirst(".project-setting"))
    }

    @Test
    fun `설정 메뉴에는 가입 신청자 수가 나온다`() {
        val project = newProject()
        val manager = newUser().also { join(it, project, RoleType.MANAGER) }
        val applicant = newUser()
        applicant.enrolledProjects.add(project)
        users.save(applicant)
        assertEquals("1", page(project, login(manager)).selectFirst(".project-setting .project-menu-count")?.text())
    }

    @Test
    fun `단축키 스크립트가 켜진 메뉴만 포함한다`() {
        val project = newProject { board = false }
        val html = page(project).html()
        assertTrue(html.contains("\"H\":"), html.takeLast(1500))
        assertTrue(html.contains("\"C\":") && html.contains("\"I\":") && html.contains("\"M\":") && html.contains("\"P\":"))
        assertFalse(html.contains("\"B\":"))
        assertTrue(html.contains("project.Global"))
    }

    @Test
    fun `프로젝트 모듈 스크립트에 저장소 주소를 넘긴다`() {
        val project = newProject()
        val html = page(project).html()
        val url = Regex("""sRepoURL"\s*:\s*"([^"]*)"""").find(html)?.groupValues?.get(1)?.replace("\\/", "/")
        assertEquals("http://localhost/${project.owner}/${project.name}", url)
    }
}
