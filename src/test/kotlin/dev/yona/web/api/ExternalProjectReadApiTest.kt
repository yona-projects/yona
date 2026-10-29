package dev.yona.web.api

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
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
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 외부용 일반 REST API 1차의 조회를 검증한다(로드맵 X-19, `decisions.md` D-40): 프로젝트 정보와 목록, 구성원과 담당자 후보, 라벨, 마일스톤, 이슈 목록과 검색.
 * 개인용 API 토큰(범위 `repo`, 공개 프로젝트는 `public_repo`)과 화면과 같은 읽기 권한으로 읽는다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-external-read"])
class ExternalProjectReadApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var tokens: ApiTokenService
    @Autowired private lateinit var failures: dev.yona.security.TokenFailureLimiter
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "erd$n", name = "이름erd$n", email = "erd$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private class Fixture(val project: Project, val manager: User, val member: User) {
        val api get() = "/-_-api/v1/owners/${project.owner}/projects/${project.name}"
    }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC, ownerLogin: String? = null): Fixture {
        val n = ++sequence
        val project = projects.save(Project(owner = ownerLogin ?: "erdo$n", name = "erdp$n", vcs = "GIT", projectScope = scope, overview = "개요 $n", createdDate = Date(1_700_000_000_000L)))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        val manager = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MANAGER.id).get())) }
        val member = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        return Fixture(project, manager, member)
    }

    private fun token(user: User, vararg scopes: String): String =
        (tokens.create(user, "t", (scopes.toList().ifEmpty { listOf("repo") }).toSet(), null) as TokenCreation.Created).value

    private fun call(builder: MockHttpServletRequestBuilder, token: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.also { failures.clear() })

    private fun issue(f: Fixture, title: String, author: User, state: State = State.OPEN, assignee: User? = null, labels: List<IssueLabel> = emptyList(), milestone: Milestone? = null, created: Long = 1_700_000_000_000L): Issue {
        val number = (issues.findAll().filter { it.project?.id == f.project.id }.maxOfOrNull { it.number ?: 0 } ?: 0) + 1
        val saved = issues.save(
            Issue(
                project = f.project, number = number, title = title, body = "$title 본문", state = state, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                numOfComments = 0, weight = 0, createdDate = Date(created), updatedDate = Date(created), milestone = milestone,
                assignee = assignee?.let { Assignee(user = it, project = f.project).also { a -> em.persist(a) } }
            )
        )
        saved.labels.addAll(labels)
        em.flush()
        return saved
    }

    // --- 프로젝트 ---

    @Test
    fun `프로젝트 정보를 돌려주고 비공개 프로젝트는 구성원만 읽는다`() {
        val f = project()
        call(get(f.api), token(user())).andExpect(status().isOk).andExpect(jsonPath("$.owner").value(f.project.owner)).andExpect(jsonPath("$.name").value(f.project.name))
            .andExpect(jsonPath("$.overview").value(f.project.overview)).andExpect(jsonPath("$.vcs").value("GIT")).andExpect(jsonPath("$.scope").value("PUBLIC"))
            .andExpect(jsonPath("$.id").value(f.project.id!!.toInt())).andExpect(jsonPath("$.createdAt").exists()).andExpect(jsonPath("$.menus.issue").value(true)).andExpect(jsonPath("$.menus.board").value(true))
        val priv = project(ProjectScope.PRIVATE)
        call(get(priv.api), token(user())).andExpect(status().isForbidden)
        call(get(priv.api), token(priv.member)).andExpect(status().isOk).andExpect(jsonPath("$.scope").value("PRIVATE"))
        call(get(priv.api), token(priv.member, "public_repo")).andExpect(status().isForbidden)
        call(get("/-_-api/v1/owners/nobody/projects/nothing"), token(f.manager)).andExpect(status().isForbidden)
        call(get(f.api), null).andExpect(status().isUnauthorized)
        call(get(f.api), token(f.manager, "user")).andExpect(status().isForbidden)
    }

    @Test
    fun `프로젝트 구성원 목록은 역할과 함께 돌려주고 사이트 관리자 역할은 뺀다`() {
        val f = project()
        val siteManager = user().also { projectUsers.save(ProjectUser(user = it, project = f.project, role = roles.findById(RoleType.SITEMANAGER.id).get())) }
        em.flush()
        call(get("${f.api}/members"), token(f.member)).andExpect(status().isOk).andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[?(@.loginId=='${f.manager.loginId}')].role").value("manager")).andExpect(jsonPath("$.members[?(@.loginId=='${f.member.loginId}')].role").value("member"))
            .andExpect(jsonPath("$.members[?(@.loginId=='${siteManager.loginId}')]").isEmpty)
    }

    @Test
    fun `담당자 후보는 구성원 중에서 찾고 query로 거른다`() {
        val f = project(); val outsider = user()
        call(get("${f.api}/assignableusers"), token(outsider)).andExpect(status().isOk).andExpect(jsonPath("$.users.length()").value(2))
        call(get("${f.api}/assignableusers").param("query", f.member.loginId!!), token(f.manager)).andExpect(jsonPath("$.users.length()").value(1)).andExpect(jsonPath("$.users[0].loginId").value(f.member.loginId))
        call(get("${f.api}/assignableusers").param("query", "이름erd"), token(f.manager)).andExpect(jsonPath("$.users.length()").value(2))
    }

    @Test
    fun `내가 속한 프로젝트와 소유자의 프로젝트를 돌려준다`() {
        val mine = project(ownerLogin = null); val other = project(); val priv = project(ProjectScope.PRIVATE)
        priv.manager.let { m -> projectUsers.save(ProjectUser(user = m, project = mine.project, role = roles.findById(RoleType.MEMBER.id).get())) }; em.flush()
        call(get("/-_-api/v1/user/projects"), token(priv.manager)).andExpect(status().isOk).andExpect(jsonPath("$.projects.length()").value(2))
            .andExpect(jsonPath("$.projects[?(@.name=='${priv.project.name}')].scope").value("PRIVATE"))
        call(get("/-_-api/v1/user/projects"), token(priv.manager, "public_repo")).andExpect(jsonPath("$.projects.length()").value(1)).andExpect(jsonPath("$.projects[0].name").value(mine.project.name))
        val sameOwnerPrivate = projects.save(Project(owner = other.project.owner, name = "secret${++sequence}", vcs = "GIT", projectScope = ProjectScope.PRIVATE))
        call(get("/-_-api/v1/owners/${other.project.owner}/projects"), token(user())).andExpect(status().isOk).andExpect(jsonPath("$.projects.length()").value(1)).andExpect(jsonPath("$.projects[0].name").value(other.project.name))
        call(get("/-_-api/v1/owners/${other.project.owner}/projects"), token(other.manager)).andExpect(jsonPath("$.projects.length()").value(1))
        assert(sameOwnerPrivate.id != null)
    }

    // --- 라벨과 마일스톤 ---

    @Test
    fun `라벨 목록은 번호와 분류와 함께 돌려준다`() {
        val f = project()
        val category = IssueLabelCategory(project = f.project, name = "종류", isExclusive = true).also { em.persist(it) }
        val bug = IssueLabel(category = category, color = "#ff0000", name = "bug", project = f.project).also { em.persist(it) }
        em.flush()
        call(get("${f.api}/labels"), token(user())).andExpect(status().isOk).andExpect(jsonPath("$.labels.length()").value(1))
            .andExpect(jsonPath("$.labels[0].id").value(bug.id!!.toInt())).andExpect(jsonPath("$.labels[0].labelName").value("bug")).andExpect(jsonPath("$.labels[0].labelColor").value("#ff0000"))
            .andExpect(jsonPath("$.labels[0].category").value("종류")).andExpect(jsonPath("$.labels[0].categoryId").value(category.id!!.toInt())).andExpect(jsonPath("$.labels[0].isExclusive").value(true))
        call(get("${project().api}/labels"), token(user())).andExpect(jsonPath("$.labels.length()").value(0))
    }

    @Test
    fun `마일스톤 목록은 상태로 거르고 기본은 열린 것이다`() {
        val f = project()
        val open = Milestone(title = "열린 마일스톤", contents = "설명", state = State.OPEN, project = f.project, dueDate = Date(1_800_000_000_000L)).also { em.persist(it) }
        Milestone(title = "닫힌 마일스톤", contents = "", state = State.CLOSED, project = f.project).also { em.persist(it) }
        em.flush()
        call(get("${f.api}/milestones"), token(user())).andExpect(status().isOk).andExpect(jsonPath("$.milestones.length()").value(1))
            .andExpect(jsonPath("$.milestones[0].id").value(open.id!!.toInt())).andExpect(jsonPath("$.milestones[0].title").value("열린 마일스톤")).andExpect(jsonPath("$.milestones[0].state").value("open"))
            .andExpect(jsonPath("$.milestones[0].description").value("설명")).andExpect(jsonPath("$.milestones[0].dueDate").exists())
        call(get("${f.api}/milestones").param("state", "closed"), token(user())).andExpect(jsonPath("$.milestones.length()").value(1)).andExpect(jsonPath("$.milestones[0].title").value("닫힌 마일스톤"))
        call(get("${f.api}/milestones").param("state", "all"), token(user())).andExpect(jsonPath("$.milestones.length()").value(2))
        call(get("${f.api}/milestones").param("state", "nope"), token(user())).andExpect(status().isBadRequest)
    }

    // --- 이슈 ---

    @Test
    fun `이슈 목록은 열린 것을 최근 순으로 보여 주고 상태로 거른다`() {
        val f = project()
        issue(f, "첫 이슈", f.manager, created = 1_700_000_000_000L); issue(f, "둘째 이슈", f.member, created = 1_700_100_000_000L); issue(f, "닫힌 이슈", f.member, state = State.CLOSED)
        call(get("${f.api}/issues"), token(user())).andExpect(status().isOk).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].title").value("둘째 이슈"))
            .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.totalCount").value(2)).andExpect(jsonPath("$.totalPages").value(1)).andExpect(jsonPath("$.pageSize").value(15))
            .andExpect(jsonPath("$.items[0].number").value(2)).andExpect(jsonPath("$.items[0].state").value("open")).andExpect(jsonPath("$.items[0].author.loginId").value(f.member.loginId))
            .andExpect(jsonPath("$.items[0].createdAt").exists()).andExpect(jsonPath("$.items[0].url").value(org.hamcrest.Matchers.endsWith("/issue/2")))
        call(get("${f.api}/issues").param("state", "closed"), token(user())).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].title").value("닫힌 이슈"))
        call(get("${f.api}/issues").param("state", "all"), token(user())).andExpect(jsonPath("$.totalCount").value(3))
        call(get("${f.api}/issues").param("sort", "number").param("order", "asc"), token(user())).andExpect(jsonPath("$.items[0].title").value("첫 이슈"))
        call(get("${f.api}/issues").param("state", "nope"), token(user())).andExpect(status().isBadRequest)
        call(get("${f.api}/issues").param("sort", "password"), token(user())).andExpect(status().isBadRequest)
    }

    @Test
    fun `이슈를 담당자와 작성자와 라벨과 마일스톤과 검색어로 거른다`() {
        val f = project()
        val category = IssueLabelCategory(project = f.project, name = "종류").also { em.persist(it) }
        val bug = IssueLabel(category = category, color = "#f00", name = "bug", project = f.project).also { em.persist(it) }
        val doc = IssueLabel(category = category, color = "#0f0", name = "doc", project = f.project).also { em.persist(it) }
        val ms = Milestone(title = "m1", contents = "", state = State.OPEN, project = f.project).also { em.persist(it) }
        em.flush()
        issue(f, "로그인 오류", f.manager, assignee = f.member, labels = listOf(bug), milestone = ms)
        issue(f, "문서 보강", f.member, labels = listOf(doc))
        issue(f, "화면 깨짐", f.member, assignee = f.manager, labels = listOf(bug, doc))
        val t = token(user())
        fun titles(vararg params: Pair<String, String>) = call(get("${f.api}/issues").apply { params.forEach { (k, v) -> param(k, v) } }, t)
        titles("assignee" to f.member.loginId!!).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].title").value("로그인 오류")).andExpect(jsonPath("$.items[0].assignees[0].loginId").value(f.member.loginId))
        titles("author" to f.member.loginId!!).andExpect(jsonPath("$.totalCount").value(2))
        titles("labelIds" to bug.id.toString()).andExpect(jsonPath("$.totalCount").value(2))
        titles("labelIds" to "${bug.id},${doc.id}").andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].title").value("화면 깨짐")).andExpect(jsonPath("$.items[0].labels.length()").value(2))
        titles("milestoneId" to ms.id.toString()).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].milestone.title").value("m1")).andExpect(jsonPath("$.items[0].milestone.id").value(ms.id!!.toInt()))
        titles("q" to "문서").andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].title").value("문서 보강"))
        titles("assignee" to "nobody").andExpect(status().isBadRequest)
        titles("author" to "nobody").andExpect(status().isBadRequest)
    }

    @Test
    fun `이슈 목록은 쪽으로 나누고 쪽 크기는 1에서 100이다`() {
        val f = project()
        (1..5).forEach { issue(f, "이슈 $it", f.manager, created = 1_700_000_000_000L + it) }
        val t = token(user())
        call(get("${f.api}/issues").param("pageSize", "2"), t).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.totalCount").value(5)).andExpect(jsonPath("$.pageSize").value(2))
        call(get("${f.api}/issues").param("pageSize", "2").param("page", "3"), t).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.page").value(3))
        call(get("${f.api}/issues").param("pageSize", "1000"), t).andExpect(jsonPath("$.pageSize").value(100))
    }

    @Test
    fun `이슈 목록도 프로젝트 읽기 권한과 토큰 범위를 따른다`() {
        val priv = project(ProjectScope.PRIVATE)
        issue(priv, "비공개 이슈", priv.manager)
        call(get("${priv.api}/issues"), token(user())).andExpect(status().isForbidden)
        call(get("${priv.api}/issues"), token(priv.member)).andExpect(status().isOk).andExpect(jsonPath("$.totalCount").value(1))
        call(get("${priv.api}/issues"), token(priv.member, "user")).andExpect(status().isForbidden)
        call(get("${priv.api}/issues"), null).andExpect(status().isUnauthorized)
    }
}
