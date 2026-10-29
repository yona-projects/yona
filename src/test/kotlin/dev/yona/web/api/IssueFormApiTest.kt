package dev.yona.web.api

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.project.TitleHead
import dev.yona.domain.project.TitleHeadRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 이슈 폼의 자동 완성 API를 검증한다. 제목 머리말(`titleHeads`)과 담당자 후보(`assignableUsers`).
 *
 * 원본: `app/controllers/api/ProjectApi.java:527-582`, `app/controllers/api/IssueApi.java:738-800`.
 */
@AutoConfigureMockMvc
@Transactional
class IssueFormApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var titleHeads: TitleHeadRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository

    private var sequence = 0

    private fun newUser(name: String? = null, state: UserState = UserState.ACTIVE): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "fa$n", name = name ?: "fa$n", email = "fa$n@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project =
        projects.save(Project(owner = "fo${++sequence}", name = "fp$sequence", vcs = "GIT", projectScope = scope))

    private fun join(user: User, project: Project, role: RoleType = RoleType.MEMBER): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user
    }

    /** 일반 웹 경로 이름으로 옮긴 AJAX 주소이다(X-5, D-36). 시험이 쓰던 옛 `owners/.../projects/...` 꼬리(`issues/1/...`, `postlabel/1`, `posts/1/content`)를 새 주소로 바꾼다. */
    private fun api(project: Project, path: String) = "/${project.owner}/${project.name}/" +
        path.replaceFirst(Regex("^issues/"), "issue/").replace(Regex("^postlabel/(\\d+)"), "post/$1/label").replace(Regex("^posts/(\\d+)/content"), "post/$1/content")

    // --- 제목 머리말 ---

    @Test
    fun `제목 머리말과 프로젝트 라벨을 합쳐 돌려준다`() {
        val project = newProject()
        titleHeads.save(TitleHead(project = project, headKeyword = "버그", frequency = 3))
        titleHeads.save(TitleHead(project = project, headKeyword = "기능", frequency = 1))
        val category = categories.save(IssueLabelCategory(project = project, name = "종류", isExclusive = true))
        val label = labels.save(IssueLabel(project = project, category = category, name = "긴급", color = "#f00"))
        mockMvc.perform(get(api(project, "titleHeads")).param("query", "버").header(HttpHeaders.ACCEPT, "application/json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result[0].name").value("버그"))
            .andExpect(jsonPath("$.result[0].frequency").value(3))
            .andExpect(jsonPath("$.result[0].category").value(""))
            .andExpect(jsonPath("$.result[0].searchText").value("버그"))
            .andExpect(jsonPath("$.result.length()").value(2))
            .andExpect(jsonPath("$.result[1].name").value("긴급"))
            .andExpect(jsonPath("$.result[1].frequency").value(0))
            .andExpect(jsonPath("$.result[1].category").value("종류"))
            .andExpect(jsonPath("$.result[1].categoryId").value(category.id))
            .andExpect(jsonPath("$.result[1].id").value(label.id))
            .andExpect(jsonPath("$.result[1].labelColor").value("#f00"))
            .andExpect(jsonPath("$.result[1].isExclusive").value(true))
            .andExpect(jsonPath("$.result[1].searchText").value("긴급/종류"))
    }

    @Test
    fun `프로젝트 라벨은 분류나 이름이 아니라 번호순으로 돌려준다 (v1_6 project_issueLabels의 DB 순서)`() {
        val project = newProject()
        val type = categories.save(IssueLabelCategory(project = project, name = "Type", isExclusive = false))
        val area = categories.save(IssueLabelCategory(project = project, name = "Area", isExclusive = false))
        labels.save(IssueLabel(project = project, category = type, name = "bug", color = "#f00"))
        labels.save(IssueLabel(project = project, category = type, name = "feature", color = "#0f0"))
        labels.save(IssueLabel(project = project, category = area, name = "ui", color = "#00f"))
        mockMvc.perform(get(api(project, "titleHeads")).header(HttpHeaders.ACCEPT, "application/json"))
            .andExpect(jsonPath("$.result.length()").value(3))
            .andExpect(jsonPath("$.result[0].name").value("bug")).andExpect(jsonPath("$.result[1].name").value("feature")).andExpect(jsonPath("$.result[2].name").value("ui"))
    }

    @Test
    fun `JSON을 받지 못하면 406이고 비공개 프로젝트는 막힌다`() {
        val project = newProject()
        mockMvc.perform(get(api(project, "titleHeads")).header(HttpHeaders.ACCEPT, "text/html")).andExpect(status().isNotAcceptable)
        val secret = newProject(ProjectScope.PRIVATE)
        mockMvc.perform(get(api(secret, "titleHeads")).header(HttpHeaders.ACCEPT, "application/json")).andExpect(status().isForbidden)
    }

    // --- 담당자 후보 ---

    @Test
    fun `검색어가 없으면 나와 담당할 수 있는 사용자를 돌려준다`() {
        val project = newProject()
        val me = join(newUser("나"), project)
        val other = join(newUser("너"), project, RoleType.MANAGER)
        mockMvc.perform(get(api(project, "assignableUsers")).session(login(me)).header(HttpHeaders.ACCEPT, "application/json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].loginId").value(me.loginId))
            .andExpect(jsonPath("$[0].name").value("Assign to me"))
            .andExpect(jsonPath("$[0].avatarUrl").value(""))
            // 담당할 수 있는 사용자는 이름 순이고 나도 그 안에 한 번 더 들어 있다.
            .andExpect(jsonPath("$[1].loginId").value(me.loginId))
            .andExpect(jsonPath("$[1].name").value("나"))
            .andExpect(jsonPath("$[2].loginId").value(other.loginId))
            .andExpect(jsonPath("$[2].name").value("너"))
            .andExpect(jsonPath("$[2].pureNameOnly").value("너"))
            .andExpect(jsonPath("$[2].type").value("user"))
            .andExpect(jsonPath("$[2].avatarUrl").exists())
            .andExpect(jsonPath("$.length()").value(3))
    }

    @Test
    fun `검색어가 있으면 활성 사용자를 이름이나 아이디로 찾는다`() {
        val project = newProject()
        val hit = newUser("찾는사람"); newUser("찾는잠김", UserState.LOCKED); newUser("다른사람")
        mockMvc.perform(get(api(project, "assignableUsers")).param("query", "찾는").session(login(newUser())).header(HttpHeaders.ACCEPT, "application/json"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].loginId").value(hit.loginId))
    }

    @Test
    fun `비공개 프로젝트에서는 검색 결과를 구성원으로 좁힌다`() {
        val project = newProject(ProjectScope.PRIVATE)
        val member = join(newUser("멤버후보"), project)
        newUser("멤버후보아님")
        mockMvc.perform(get(api(project, "assignableUsers")).param("query", "멤버후보").session(login(member)).header(HttpHeaders.ACCEPT, "application/json"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].loginId").value(member.loginId))
    }

    @Test
    fun `열 명을 넘으면 열 명만 돌려주고 전체 개수를 알린다`() {
        val project = newProject()
        (1..12).forEach { newUser("대량$it") }
        mockMvc.perform(get(api(project, "assignableUsers")).param("query", "대량").session(login(newUser())).header(HttpHeaders.ACCEPT, "application/json"))
            .andExpect(jsonPath("$.length()").value(10))
            .andExpect(header().string("Content-Range", "items 10/12"))
    }

    @Test
    fun `type을 지정하면 그 항목이 같은 사용자를 찾는다`() {
        val project = newProject()
        val target = newUser("정확")
        newUser("정확하지 않음")
        mockMvc.perform(get(api(project, "assignableUsers")).param("query", target.loginId!!).param("type", "loginId").session(login(newUser())).header(HttpHeaders.ACCEPT, "application/json"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].loginId").value(target.loginId))
    }

    @Test
    fun `담당자 후보도 JSON을 받지 못하면 406이다`() {
        val project = newProject()
        mockMvc.perform(get(api(project, "assignableUsers")).header(HttpHeaders.ACCEPT, "text/html")).andExpect(status().isNotAcceptable)
    }
}
