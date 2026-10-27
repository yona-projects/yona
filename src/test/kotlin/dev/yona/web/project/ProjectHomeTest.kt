package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.project.RecentProjectRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 홈(`GET /:user/:project`)과 설명 수정(`PUT /:user/:project`)을 검증한다.
 *
 * 원본: `app/controllers/ProjectApp.java:85-150`, `app/views/project/home.scala.html`,
 * `app/views/project/partial_readme.scala.html`, `app/views/milestone/partial_status.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-project-home"])
class ProjectHomeTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var recentProjects: RecentProjectRepository

    private var sequence = 0

    @AfterEach
    fun cleanRepositories() {
        File("build/tmp/it-project-home").deleteRecursively()
    }

    private fun newUser(name: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "ph$n", name = name ?: "ph$n", email = "ph$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(
        scope: ProjectScope = ProjectScope.PUBLIC,
        overview: String? = null,
        menu: ProjectMenuSetting.() -> Unit = {}
    ): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "owner$n", name = "proj$n", vcs = "GIT", projectScope = scope, overview = overview))
        menuSettings.save(
            ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true)
                .apply(menu)
        )
        repositories.createRepository(project)
        return project
    }

    private fun join(user: User, project: Project, role: RoleType) {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
    }

    private fun home(project: Project, session: MockHttpSession? = null, query: String = ""): Document {
        val request = get("/${project.owner}/${project.name}$query")
        session?.let { request.session(it) }
        return Jsoup.parse(mockMvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString)
    }

    // --- 접근과 제목 ---

    @Test
    fun `공개 프로젝트는 익명도 열 수 있고 문서 제목은 이름과 홈이며 설명은 메타 설명이 된다`() {
        val project = newProject(overview = "소개 문구")
        val page = home(project)
        // v1.6 레이아웃은 제목을 " |:| "로 나눠 첫 조각만 <title>에 쓰고 마지막 조각을 og:description에 쓴다.
        assertEquals("${project.name} - Home", page.title())
        assertEquals("소개 문구 - ${project.owner}/${project.name}", page.selectFirst("meta[property=og:description]")?.attr("content"))
    }

    @Test
    fun `설명이 없으면 og 설명은 null 문자열이다 (v1_6 문자열 결합)`() {
        val page = home(newProject(overview = null))
        assertTrue(page.selectFirst("meta[property=og:description]")!!.attr("content").startsWith("null"), page.selectFirst("meta[property=og:description]")!!.attr("content"))
    }

    @Test
    fun `비공개 프로젝트는 구성원이 아니면 열 수 없다`() {
        val project = newProject(ProjectScope.PRIVATE)
        mockMvc.perform(get("/${project.owner}/${project.name}").session(login(newUser()))).andExpect(status().isForbidden)
    }

    @Test
    fun `프로젝트 소유자와 이름은 대소문자를 구별하지 않고 찾는다`() {
        val project = newProject()
        mockMvc.perform(get("/${project.owner!!.uppercase()}/${project.name!!.uppercase()}")).andExpect(status().isOk)
    }

    // --- 설명 ---

    @Test
    fun `설명이 비어 있으면 안내 문구를 보여 준다`() {
        val page = home(newProject())
        assertEquals("Enter project description", page.selectFirst("#project-description")?.text())
    }

    @Test
    fun `설명은 마크다운으로 렌더링해 보여 준다`() {
        val page = home(newProject(overview = "**굵게**"))
        assertEquals("굵게", page.selectFirst("#project-description strong")?.text())
    }

    @Test
    fun `설명 수정 버튼은 수정 권한이 있을 때만 보인다`() {
        val project = newProject()
        val manager = newUser().also { join(it, project, RoleType.MANAGER) }
        val outsider = newUser()
        assertNotNull(home(project, login(manager)).selectFirst("button[data-toggle=description-edit]"))
        assertNull(home(project, login(outsider)).selectFirst("button[data-toggle=description-edit]"))
        assertNull(home(project).selectFirst("button[data-toggle=description-edit]"))
    }

    @Test
    fun `설명 수정 입력란은 현재 설명을 담고 저장 주소를 가진다`() {
        val project = newProject(overview = "현재 설명")
        val manager = newUser().also { join(it, project, RoleType.MANAGER) }
        val page = home(project, login(manager))
        assertEquals("현재 설명", page.selectFirst("#project-description-input")?.attr("value"))
        assertEquals("/${project.owner}/${project.name}", page.selectFirst(".project-description-edit form")?.attr("action"))
    }

    // --- 복제 주소와 탭 ---

    @Test
    fun `코드 메뉴가 켜져 있으면 복제 주소를 보여 준다`() {
        val project = newProject()
        val page = home(project)
        assertTrue(page.selectFirst("#cloneURL")?.attr("value")!!.endsWith("/${project.owner}/${project.name}"))
        assertEquals("Copy URL", page.selectFirst("#cloneURLBtn")?.text())
    }

    @Test
    fun `코드 메뉴가 꺼져 있으면 복제 주소가 없다`() {
        assertNull(home(newProject { code = false }).selectFirst("#cloneURL"))
    }

    @Test
    fun `탭은 README 이력 대시보드이고 기본은 README가 활성이다`() {
        val project = newProject()
        val tabs = home(project).select(".span-left-pane ul.nav-tabs li")
        assertEquals(listOf("README", "History", "Dashboard"), tabs.map { it.text() })
        assertEquals(listOf("active", "", ""), tabs.map { it.className().trim() })
        assertEquals(
            listOf("/${project.owner}/${project.name}", "/${project.owner}/${project.name}?tabId=history", "/${project.owner}/${project.name}?tabId=dashboard"),
            tabs.map { it.selectFirst("a")!!.attr("href") }
        )
    }

    // --- README ---

    @Test
    fun `README가 없으면 안내 문구를 보여 준다`() {
        val page = home(newProject())
        assertTrue(page.selectFirst(".readme p.default span")!!.text().startsWith("README.md will be shown here"))
    }

    @Test
    fun `README가 없을 때 쓸 수 있는 사용자에게는 만들기 링크가 보인다`() {
        val project = newProject()
        val member = newUser().also { join(it, project, RoleType.MEMBER) }
        val link = home(project, login(member)).selectFirst(".readme p.default a.ybtn")
        assertEquals("/${project.owner}/${project.name}/postform?readme=true", link?.attr("href"))
        assertEquals("create README", link?.text())
        assertNull(home(project).selectFirst(".readme p.default a.ybtn"))
    }

    @Test
    fun `README가 있으면 파일 이름과 렌더링한 내용을 보여 준다`() {
        val project = newProject()
        GitTestSupport.commitFiles(repositories, project, mapOf("README.md" to "# 소개\n\n내용".toByteArray()))
        val page = home(project)
        assertEquals("README.md", page.selectFirst(".readme-wrap header strong")?.text())
        assertEquals("소개", page.selectFirst(".readme-body h1")?.ownText())
        assertTrue(page.selectFirst(".readme-body")!!.text().contains("내용"))
    }

    @Test
    fun `README가 있을 때 쓸 수 있는 사용자에게는 편집 링크가 보인다`() {
        val project = newProject()
        GitTestSupport.commitFiles(repositories, project, mapOf("README.md" to "x".toByteArray()))
        val member = newUser().also { join(it, project, RoleType.MEMBER) }
        assertNotNull(home(project, login(member)).selectFirst(".readme-wrap header a.ybtn"))
        assertNull(home(project).selectFirst(".readme-wrap header a.ybtn"))
    }

    @Test
    fun `README의 상대 경로 이미지와 링크는 저장소 경로로 바뀐다`() {
        val project = newProject()
        val source = "![로고](./img/logo.png)\n\n[문서](./docs/a.md)"
        GitTestSupport.commitFiles(repositories, project, mapOf("README.md" to source.toByteArray()))
        val body = home(project).selectFirst(".readme-body")!!
        assertEquals("/${project.owner}/${project.name}/files/master/img/logo.png", body.selectFirst("img")?.attr("src"))
        assertEquals("/${project.owner}/${project.name}/code/master/docs/a.md", body.selectFirst("a")?.attr("href"))
    }

    @Test
    fun `코드 메뉴가 꺼져 있으면 README 게시글을 보여 준다`() {
        val project = newProject { code = false }
        GitTestSupport.commitFiles(repositories, project, mapOf("README.md" to "# 파일 README".toByteArray()))
        postings.save(Posting(project = project, title = "README", body = "# 게시글 README", readme = true, number = 1))
        assertEquals("게시글 README", home(project).selectFirst(".readme-body h1")?.ownText())
    }

    // --- 오른쪽 영역 ---

    @Test
    fun `새 이슈와 포크 버튼은 메뉴 설정에 따라 보인다`() {
        val project = newProject()
        val page = home(project)
        assertEquals("/${project.owner}/${project.name}/issueform", page.selectFirst(".project-btn-wrap a.ybtn-success")?.attr("href"))
        assertEquals("/${project.owner}/${project.name}/newFork", page.selectFirst(".project-btn-wrap a.ybtn-inverse")?.attr("href"))
        val off = home(newProject { issue = false; code = false })
        assertNull(off.selectFirst(".project-btn-wrap a.ybtn-success"))
        assertNull(off.selectFirst(".project-btn-wrap a.ybtn-inverse"))
    }

    @Test
    fun `열린 마일스톤이 있으면 요약을 보여 준다`() {
        val project = newProject()
        val milestone = milestones.save(Milestone(title = "첫 마일스톤", state = State.OPEN, project = project))
        repeat(1) { issues.save(Issue(project = project, milestone = milestone, state = State.CLOSED, number = 1, title = "a")) }
        issues.save(Issue(project = project, milestone = milestone, state = State.OPEN, number = 2, title = "b"))
        val info = home(project).selectFirst(".milestone-info")!!
        assertEquals("첫 마일스톤", info.selectFirst("a.title")?.text())
        assertEquals("/${project.owner}/${project.name}/milestone/${milestone.id}", info.selectFirst("a.title")?.attr("href"))
        assertEquals("width: 50%;", info.selectFirst(".bar")?.attr("style"))
        assertEquals("1 / 2", info.selectFirst(".progress-info strong")?.text())
    }

    @Test
    fun `마일스톤 메뉴가 꺼져 있으면 요약이 없다`() {
        val project = newProject { milestone = false }
        milestones.save(Milestone(title = "숨김", state = State.OPEN, project = project))
        assertNull(home(project).selectFirst(".milestone-info"))
    }

    @Test
    fun `마감일이 있는 마일스톤은 마감일과 남은 기간을 보여 준다`() {
        val project = newProject()
        milestones.save(Milestone(title = "기한", state = State.OPEN, project = project, dueDate = Date(System.currentTimeMillis() + 5 * 86_400_000L)))
        val due = home(project).selectFirst(".milestone-info .due-date")!!
        assertTrue(due.text().startsWith("Due Date"), due.text())
        assertFalse(due.hasClass("over"))
    }

    // --- 멤버 ---

    @Test
    fun `멤버 목록에는 사이트 관리자를 뺀 구성원이 이름순으로 나온다`() {
        val project = newProject()
        val second = newUser("나나").also { join(it, project, RoleType.MEMBER) }
        val first = newUser("가가").also { join(it, project, RoleType.MANAGER) }
        join(users.findById(1L).get(), project, RoleType.SITEMANAGER)
        val names = home(project).select(".project-members li.member a.name strong").map { it.text() }
        assertEquals(listOf("가가 (${first.loginId})", "나나 (${second.loginId})"), names)
    }

    @Test
    fun `멤버를 추가하는 링크는 수정 권한이 있을 때만 보인다`() {
        val project = newProject()
        val manager = newUser().also { join(it, project, RoleType.MANAGER) }
        assertEquals("/${project.owner}/${project.name}/members", home(project, login(manager)).selectFirst("#member-add-link")?.attr("href"))
        assertNull(home(project).selectFirst("#member-add-link"))
    }

    @Test
    fun `프로젝트 탈퇴 버튼은 소유자가 아닌 구성원에게만 보인다`() {
        val project = newProject()
        val member = newUser().also { join(it, project, RoleType.MEMBER) }
        val button = home(project, login(member)).selectFirst("#projectLeaveBtn")
        assertEquals("/${project.owner}/${project.name}/member/${member.id}/delete", button?.attr("data-href"))
        assertNull(home(project, login(newUser())).selectFirst("#projectLeaveBtn"))
        assertNull(home(project).selectFirst("#projectLeaveBtn"))
    }

    @Test
    fun `탈퇴 확인 대화상자가 있다`() {
        val dialog = home(newProject()).selectFirst("#alertLeave")!!
        assertEquals("Do you want to leave this project?", dialog.selectFirst(".modal-body p")?.text())
        assertEquals("Yes", dialog.selectFirst("#leaveBtn")?.text())
    }

    // --- 방문 기록 ---

    @Test
    fun `로그인한 사용자가 열면 최근 방문에 기록된다`() {
        val project = newProject()
        val user = newUser()
        home(project, login(user))
        assertEquals(listOf(project.id), recentProjects.findByUserIdOrderByIdDesc(user.id!!).map { it.project?.id })
    }

    @Test
    fun `익명이 열면 기록하지 않는다`() {
        home(newProject())
        assertTrue(recentProjects.findAll().isEmpty())
    }

    // --- 스크립트 ---

    @Test
    fun `프로젝트 홈 스크립트 모듈을 불러온다`() {
        val project = newProject()
        val script = home(project).select("script").map { it.data().replace("\\/", "/") }.first { "project.Home" in it }
        assertTrue("\"sURLProject\"" in script && "/${project.owner}/${project.name}" in script, script)
    }

    // --- 설명 수정 ---

    private fun updateOverview(project: Project, session: MockHttpSession?, overview: String) =
        mockMvc.perform(
            put("/${project.owner}/${project.name}").apply { session?.let { session(it) } }
                .contentType(MediaType.APPLICATION_JSON).content("""{"overview":"$overview"}""")
        )

    @Test
    fun `관리자는 설명을 바꾸고 바뀐 값을 JSON으로 받는다`() {
        val project = newProject(overview = "이전")
        val manager = newUser().also { join(it, project, RoleType.MANAGER) }
        updateOverview(project, login(manager), "새 설명")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.overview").value("새 설명"))
        assertEquals("새 설명", projects.findById(project.id!!).get().overview)
    }

    @Test
    fun `설명 수정은 로그인해야 한다`() {
        val project = newProject()
        updateOverview(project, null, "x").andExpect(status().isSeeOther)
    }

    @Test
    fun `설명 수정 권한이 없으면 거부한다`() {
        val project = newProject()
        updateOverview(project, login(newUser()), "x").andExpect(status().isForbidden)
        assertNull(projects.findById(project.id!!).get().overview)
    }

    // --- 마크다운 미리보기 ---

    @Test
    fun `마크다운 렌더링 요청은 본문을 HTML로 바꿔 돌려준다`() {
        val project = newProject()
        mockMvc.perform(
            post("/markdown/${project.owner}/${project.name}").contentType(MediaType.APPLICATION_JSON)
                .content("""{"body":"**진하게**","breaks":true}""")
        ).andExpect(status().isOk).andExpect(content().string(org.hamcrest.Matchers.containsString("<strong>진하게</strong>")))
    }
}
