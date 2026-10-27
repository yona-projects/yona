package dev.yona.web.access

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
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
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * v1.6의 프로젝트 검사 액션을 검증한다.
 *
 * 원본: `app/actions/{AbstractProjectCheckAction,IsAllowedAction,IsCreatableAction,IsOnlyGitAvailableAction,
 * CodeAccessCheckAction,DefaultProjectCheckAction,NullProjectCheckAction,GuestProhibitAction}.java`,
 * `app/models/resource/Resource.java:226-247`(URL 구간으로 리소스 찾기).
 */
@AutoConfigureMockMvc
@Transactional
class ProjectChecksTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository

    private var sequence = 0

    private fun newUser(guest: Boolean = false): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "chk$n", name = "chk$n", email = "chk$n@example.com", state = UserState.ACTIVE, isGuest = guest,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC, vcs: String = "GIT", codeMemberOnly: Boolean = false): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "chkowner$n", name = "chkproj$n", vcs = vcs, projectScope = scope, isCodeAccessibleMemberOnly = codeMemberOnly))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project, role: RoleType = RoleType.MEMBER) {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
    }

    private fun call(path: String, session: MockHttpSession? = null): MvcResult {
        val request = get(path)
        session?.let { request.session(it) }
        return mockMvc.perform(request).andReturn()
    }

    private fun doc(result: MvcResult): Document = Jsoup.parse(result.response.contentAsString)

    private fun base(project: Project) = "/${project.owner}/${project.name}"

    // --- 프로젝트 존재와 읽기 권한 (AbstractProjectCheckAction) ---

    @Test
    fun `프로젝트가 없으면 익명에게는 로그인 화면을 403으로 보여 준다`() {
        val result = call("/nobody/nothing/__t/default")
        assertEquals(403, result.response.status)
        val page = doc(result)
        assertEquals("error.fobidden", page.title(), "v1.6의 오타 키가 그대로 제목이 된다")
        assertEquals("/users/login", page.selectFirst(".login-form-wrap form")?.attr("action"))
        assertEquals("/nobody/nothing/__t/default", page.selectFirst("input[name=redirectUrl]")?.attr("value"))
        assertTrue(result.response.contentAsString.contains("You are not authorized to access this page or not logged in."), "failed 안내")
    }

    @Test
    fun `앞 요청이 남긴 안내는 failed 안내와 함께 보이고 다음 응답에도 남는다 (v1_6 flash는 응답이 다시 심는다)`() {
        val session = MockHttpSession()
        mockMvc.perform(get("/projectform").session(session)).andExpect(status().isSeeOther)
        val first = mockMvc.perform(get("/nobody/nothing/__t/default").session(session)).andReturn().response.contentAsString
        val warning = first.indexOf("Please log in.")
        val failed = first.indexOf("You are not authorized to access this page or not logged in.")
        assertTrue(warning in 0 until failed, "로그인 안내가 failed 안내보다 먼저 보인다")
        val second = mockMvc.perform(get("/nobody/nothing/__t/default").session(session)).andReturn().response.contentAsString
        assertTrue(second.contains("Please log in."), "다음 응답에도 남는다")
    }

    @Test
    fun `새 안내를 심는 리다이렉트는 앞 요청의 안내를 이어 받는다`() {
        val session = MockHttpSession()
        mockMvc.perform(get("/nobody/nothing/__t/default").session(session)).andExpect(status().isForbidden)
        mockMvc.perform(get("/projectform").session(session)).andExpect(status().isSeeOther)
        val page = mockMvc.perform(get("/nobody/nothing/__t/default").session(session)).andReturn().response.contentAsString
        assertTrue(page.contains("Please log in.") && page.contains("You are not authorized to access this page or not logged in."), "두 안내가 함께 보인다")
    }

    @Test
    fun `프로젝트가 없으면 로그인한 사용자에게는 찾을 수 없음을 403으로 알린다`() {
        val result = call("/nobody/nothing/__t/default", login(newUser()))
        assertEquals(403, result.response.status)
        assertEquals("Project does not exist or user unauthorized", doc(result).selectFirst(".error-wrap p")?.text())
    }

    @Test
    fun `읽기 권한이 없는 프로젝트는 익명에게 로그인 화면을 403으로 보여 준다`() {
        val project = newProject(ProjectScope.PRIVATE)
        val result = call("${base(project)}/__t/default")
        assertEquals(403, result.response.status)
        assertEquals("${base(project)}/__t/default", doc(result).selectFirst("input[name=redirectUrl]")?.attr("value"))
    }

    @Test
    fun `읽기 권한이 없는 프로젝트는 로그인한 사용자에게 권한 없음을 403으로 알린다`() {
        val project = newProject(ProjectScope.PRIVATE)
        val result = call("${base(project)}/__t/default", login(newUser()))
        assertEquals(403, result.response.status)
        assertEquals("Project does not exist or user unauthorized", doc(result).selectFirst(".error-wrap p")?.text())
        // v1.6 AbstractProjectCheckAction은 읽기 권한이 없으면 로그인 여부와 관계없이 flash("failed")를 심고, 같은 응답에서 알림으로 보인다
        assertTrue(result.response.contentAsString.contains("You are not authorized to access this page or not logged in."), "failed 안내")
    }

    @Test
    fun `읽을 수 있는 프로젝트는 통과한다`() {
        val project = newProject()
        assertEquals(200, call("${base(project)}/__t/default").response.status)
        val privateProject = newProject(ProjectScope.PRIVATE)
        val member = newUser().also { join(it, privateProject) }
        assertEquals(200, call("${base(privateProject)}/__t/default", login(member)).response.status)
    }

    @Test
    fun `소유자와 이름은 대소문자와 URL 인코딩을 가리지 않는다`() {
        val project = newProject()
        val upper = "/${project.owner!!.uppercase()}/${project.name!!.uppercase()}/__t/default"
        assertEquals(200, call(upper).response.status)
    }

    @Test
    fun `바뀌기 전 이름으로도 접근되고 리다이렉트하지 않는다`() {
        val project = newProject()
        project.previousOwnerLoginId = "oldowner"
        project.previousName = "oldname"
        project.previousNameChangedTime = 1
        projects.save(project)
        val result = call("/oldowner/oldname/__t/default")
        // v1.6의 Project.findByOwnerAndProjectName이 이전 위치로도 찾으므로 검사 액션의 리다이렉트 분기는 실행되지 않는다.
        assertEquals(200, result.response.status)
    }

    @Test
    fun `API 경로에서는 owners와 projects 다음 구간을 소유자와 이름으로 읽는다`() {
        val project = newProject()
        assertEquals(200, call("/-_-api/v1/owners/${project.owner}/projects/${project.name}/__t/api").response.status)
        assertEquals(403, call("/-_-api/v1/owners/none/projects/none/__t/api").response.status)
    }

    // --- NullProjectCheck: 존재만 확인 ---

    @Test
    fun `존재만 확인하는 검사는 읽기 권한을 보지 않는다`() {
        val project = newProject(ProjectScope.PRIVATE)
        assertEquals(200, call("${base(project)}/__t/null-check").response.status)
        assertEquals(403, call("/nobody/nothing/__t/null-check").response.status)
    }

    // --- IsAllowed ---

    @Test
    fun `프로젝트 읽기 권한 검사를 통과하면 컨트롤러가 실행된다`() {
        val project = newProject()
        assertEquals(200, call("${base(project)}/__t/project-read").response.status)
    }

    @Test
    fun `수정 권한이 없으면 프로젝트 화면 안에서 403으로 알린다`() {
        val project = newProject()
        val result = call("${base(project)}/__t/project-update", login(newUser()))
        assertEquals(403, result.response.status)
        val page = doc(result)
        assertNotNull(page.selectFirst(".project-menu-outer"))
        assertEquals("You are not authorized", page.selectFirst(".error-wrap p")?.text())
    }

    @Test
    fun `프로젝트 수정은 매니저만 통과하고 일반 멤버는 막는다`() {
        val project = newProject()
        val manager = newUser().also { join(it, project, RoleType.MANAGER) }
        val member = newUser().also { join(it, project) }
        assertEquals(200, call("${base(project)}/__t/project-update", login(manager)).response.status)
        assertEquals(403, call("${base(project)}/__t/project-update", login(member)).response.status)
    }

    @Test
    fun `URL의 번호로 이슈를 찾아 권한을 검사한다`() {
        val project = newProject(ProjectScope.PRIVATE)
        val author = newUser().also { join(it, project) }
        val issue = issues.save(Issue(project = project, number = 7, state = State.OPEN, authorId = author.id))
        assertEquals(200, call("${base(project)}/issue/${issue.number}/__t", login(author)).response.status)
        val stranger = newUser().also { join(it, project) }
        // 멤버는 수정할 수 있다. 읽기만 가능한 사용자는 아래 별도 시나리오에서 확인한다.
        assertEquals(200, call("${base(project)}/issue/${issue.number}/__t", login(stranger)).response.status)
    }

    @Test
    fun `없는 이슈 번호는 프로젝트 화면 안에서 404로 알린다`() {
        val project = newProject()
        val result = call("${base(project)}/issue/999/__t", login(newUser()))
        assertEquals(404, result.response.status)
        val page = doc(result)
        assertEquals("Issue does not exist", page.selectFirst(".error-wrap p")?.text())
        assertEquals("${base(project)}/issues?state=all", page.selectFirst(".error-wrap a.ybtn-primary")?.attr("href"))
    }

    @Test
    fun `읽기만 가능한 사용자가 이슈를 수정하려 하면 403이다`() {
        val project = newProject()
        val issue = issues.save(Issue(project = project, number = 1, state = State.OPEN))
        val result = call("${base(project)}/issue/${issue.number}/__t", login(newUser()))
        assertEquals(403, result.response.status)
    }

    @Test
    fun `게시글 번호로 게시글을 찾는다`() {
        val project = newProject()
        postings.save(Posting(project = project, number = 3))
        assertEquals(200, call("${base(project)}/post/3/__t").response.status)
        val missing = call("${base(project)}/post/4/__t")
        assertEquals(404, missing.response.status)
        assertEquals("Post does not exist", doc(missing).selectFirst(".error-wrap p")?.text())
    }

    @Test
    fun `풀 리퀘스트는 받는 프로젝트와 번호로 찾는다`() {
        val project = newProject()
        pullRequests.save(PullRequest(toProject = project, fromProject = newProject(), number = 5, state = State.OPEN))
        assertEquals(200, call("${base(project)}/pullRequest/5/__t").response.status)
        assertEquals(404, call("${base(project)}/pullRequest/6/__t").response.status)
    }

    /**
     * v1.6 결함 재현: 마일스톤, 라벨, 라벨 분류는 번호만으로 찾아서 다른 프로젝트의 것도 이 프로젝트의 경로에서 통과한다
     * ([refactoring-backlog.md] R-12).
     */
    @Test
    fun `마일스톤과 라벨과 분류는 번호만으로 찾는다`() {
        val project = newProject()
        val other = newProject()
        val member = newUser().also { join(it, other); join(it, project) }
        val session = login(member)
        val milestone = milestones.save(Milestone(project = other, title = "m", state = State.OPEN))
        val category = categories.save(IssueLabelCategory(project = other, name = "c"))
        val label = labels.save(IssueLabel(project = other, name = "l", category = category, color = "#fff"))
        assertEquals(200, call("${base(project)}/milestone/${milestone.id}/__t", session).response.status)
        assertEquals(200, call("${base(project)}/issue/label/${label.id}/__t", session).response.status)
        assertEquals(200, call("${base(project)}/issue/label/category/${category.id}/__t", session).response.status)
        assertEquals(404, call("${base(project)}/milestone/999999/__t", session).response.status)
    }

    // --- IsCreatable ---

    @Test
    fun `만들 권한이 없으면 403이고 있으면 통과한다`() {
        val publicProject = newProject()
        assertEquals(403, call("${base(publicProject)}/__t/creatable").response.status, "익명은 만들 수 없다")
        assertEquals(200, call("${base(publicProject)}/__t/creatable", login(newUser())).response.status)
    }

    // --- IsOnlyGitAvailable ---

    @Test
    fun `Git 전용 기능은 다른 VCS 프로젝트에서 400이다`() {
        val svn = newProject(vcs = "Subversion")
        val result = call("${base(svn)}/__t/git-only")
        assertEquals(400, result.response.status)
        assertEquals("This request is only supported in a git project.", doc(result).selectFirst(".error-wrap p")?.text())
        assertEquals(200, call("${base(newProject())}/__t/git-only").response.status)
    }

    // --- CodeAccessCheck ---

    @Test
    fun `코드 열람이 멤버 전용이면 비멤버를 막는다`() {
        val project = newProject(codeMemberOnly = true)
        assertEquals(403, call("${base(project)}/__t/code").response.status)
        assertEquals(403, call("${base(project)}/__t/code", login(newUser())).response.status)
        val member = newUser().also { join(it, project) }
        assertEquals(200, call("${base(project)}/__t/code", login(member)).response.status)
    }

    @Test
    fun `코드 열람 제한이 없으면 누구나 통과한다`() {
        assertEquals(200, call("${base(newProject())}/__t/code").response.status)
    }

    // --- 지원하지 않는 리소스 종류 ---

    @Test
    fun `지원하지 않는 리소스 종류는 v1_6처럼 오류이다`() {
        val project = newProject()
        val result = call("${base(project)}/__t/unsupported-type")
        assertEquals(500, result.response.status)
    }

    // --- GuestProhibit ---

    @Test
    fun `게스트는 홈으로 보내고 안내를 남긴다`() {
        val guest = newUser(guest = true)
        mockMvc.perform(get("/__t/guest-prohibit").session(login(guest)))
            .andExpect(status().isSeeOther).andExpect(header().string("Location", "/"))
            .andExpect(flash().attribute("warning", "error.forbidden.or.not.allowed"))
    }

    @Test
    fun `안내를 끄면 flash 없이 홈으로 보낸다`() {
        val guest = newUser(guest = true)
        mockMvc.perform(get("/__t/guest-prohibit-silent").session(login(guest)))
            .andExpect(status().isSeeOther).andExpect(flash().attributeCount(0))
    }

    @Test
    fun `게스트가 아니면 통과한다`() {
        assertEquals(200, call("/__t/guest-prohibit", login(newUser())).response.status)
        assertEquals(200, call("/__t/guest-prohibit").response.status, "익명도 게스트가 아니다")
    }
}
