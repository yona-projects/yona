package dev.yona.web.api

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueSharer
import dev.yona.domain.issue.IssueSharerRepository
import dev.yona.domain.notification.Mention
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
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Date

/**
 * 사용자 현황 REST(로드맵 X-37e, `decisions.md` D-52)를 검증한다. `GET /user/status`는 토큰 주인의 담당 이슈, 담당 풀 리퀘스트, 리뷰 요청, 멘션된 이슈, 최근 알림을 한 번에 주고
 * (`gh status`), `GET /user/issues`는 `commented`(댓글 단)와 `shared`(공유받은) 필터를 더 받는다. 토큰 주인이 읽을 수 없는 프로젝트의 것은 세지 않는다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalUserStatusApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var comments: IssueCommentRepository
    @Autowired private lateinit var sharers: IssueSharerRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()

    private fun newUser(scopes: Collection<String>? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "us$n", name = "us$n", email = "us$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        tokens[saved.id!!] = "ust$n"
        if (scopes == null) dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!)) else dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!), scopes)
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "uso$n", name = "usp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun member(project: Project, user: User) { projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }

    private fun issue(project: Project, author: User, assignee: User? = null, state: State = State.OPEN, title: String = "이슈"): Issue {
        val number = (project.lastIssueNumber ?: 0) + 1; project.lastIssueNumber = number; projects.save(project)
        val saved = issues.save(Issue(project = project, number = number, title = title, body = "본문", state = state, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, createdDate = Date(), updatedDate = Date()))
        if (assignee != null) { saved.assignee = assignees.save(Assignee(user = assignee, project = project)); issues.save(saved) }
        return saved
    }

    private fun pull(project: Project, from: Project, author: User, state: State = State.OPEN, assignee: User? = null, reviewer: User? = null): PullRequest {
        val number = (project.lastIssueNumber ?: 0) + 1; project.lastIssueNumber = number; projects.save(project)
        return pullRequests.save(PullRequest(title = "요청", body = "본문", toProject = project, fromProject = from, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature", contributor = author, created = Date(), updated = Date(), state = state, number = number)
            .also { pr -> assignee?.let { pr.assignees.add(it) }; reviewer?.let { pr.reviewRequests.add(it) } })
    }

    private fun json(path: String, token: String?) = mockMvc.perform(get(path).apply { token?.let { header("Authorization", "token $it") } }.accept(MediaType.APPLICATION_JSON))

    // --- 현황 ---

    @Test
    fun `현황은 담당 이슈와 담당 풀 리퀘스트와 리뷰 요청과 멘션된 이슈를 열림과 닫힘 수와 함께 준다`() {
        val project = newProject(); val me = newUser(); val other = newUser(); member(project, me); member(project, other)
        issue(project, other, me, title = "담당 열림 1"); issue(project, other, me, title = "담당 열림 2"); issue(project, other, me, State.CLOSED, "담당 닫힘"); issue(project, me, null, title = "내가 쓴 것")
        pull(project, project, other, assignee = me); pull(project, project, other, State.MERGED, assignee = me); pull(project, project, other, reviewer = me)
        val mentioned = issue(project, other, null, title = "멘션됨"); entityManager.persist(Mention(resourceType = ResourceType.ISSUE_POST, resourceId = mentioned.id.toString(), user = me))
        entityManager.flush()
        json("/-_-api/v1/user/status", tokenOf(me)).andExpect(status().isOk)
            .andExpect(jsonPath("$.assignedIssues.openCount").value(2)).andExpect(jsonPath("$.assignedIssues.closedCount").value(1)).andExpect(jsonPath("$.assignedIssues.items.length()").value(2)).andExpect(jsonPath("$.assignedIssues.items[0].title").exists())
            .andExpect(jsonPath("$.assignedPullRequests.openCount").value(1)).andExpect(jsonPath("$.assignedPullRequests.closedCount").value(1)).andExpect(jsonPath("$.assignedPullRequests.items.length()").value(1)).andExpect(jsonPath("$.assignedPullRequests.items[0].toProject.name").value(project.name))
            .andExpect(jsonPath("$.reviewRequests.openCount").value(1)).andExpect(jsonPath("$.reviewRequests.items.length()").value(1))
            .andExpect(jsonPath("$.mentionedIssues.openCount").value(1)).andExpect(jsonPath("$.mentionedIssues.items[0].title").value("멘션됨"))
            .andExpect(jsonPath("$.notifications").isArray)
        json("/-_-api/v1/user/status?limit=1", tokenOf(me)).andExpect(jsonPath("$.assignedIssues.items.length()").value(1)).andExpect(jsonPath("$.assignedIssues.openCount").value(2))
        json("/-_-api/v1/user/status", tokenOf(other)).andExpect(jsonPath("$.assignedIssues.openCount").value(0)).andExpect(jsonPath("$.assignedPullRequests.openCount").value(0)).andExpect(jsonPath("$.reviewRequests.openCount").value(0))
    }

    @Test
    fun `읽을 수 없는 프로젝트의 풀 리퀘스트는 세지 않고(담당자로 지정된 이슈는 v1_6처럼 읽을 수 있다) limit이 틀리면 거절한다`() {
        val secret = newProject(ProjectScope.PRIVATE); val me = newUser(); val other = newUser(); member(secret, other)
        issue(secret, other, me); pull(secret, secret, other, assignee = me)
        // 이슈는 담당자에게 읽기 권한이 있어(`AccessControl`의 담당자 규칙, v1.6) 비공개 프로젝트여도 보인다. 풀 리퀘스트는 프로젝트를 읽을 수 없으면 보이지 않는다
        json("/-_-api/v1/user/status", tokenOf(me)).andExpect(jsonPath("$.assignedIssues.openCount").value(1)).andExpect(jsonPath("$.assignedPullRequests.openCount").value(0))
        json("/-_-api/v1/user/status?limit=0", tokenOf(me)).andExpect(status().isBadRequest)
        json("/-_-api/v1/user/status?limit=x", tokenOf(me)).andExpect(status().isBadRequest)
        json("/-_-api/v1/user/status?limit=101", tokenOf(me)).andExpect(status().isBadRequest)
        json("/-_-api/v1/user/status", null).andExpect(status().isUnauthorized)
        json("/-_-api/v1/user/status", tokenOf(newUser(listOf("public_repo")))).andExpect(status().isForbidden)
    }

    // --- 내 이슈의 새 필터 ---

    @Test
    fun `내 이슈는 댓글 단 이슈와 공유받은 이슈 필터를 더 받는다`() {
        val project = newProject(); val me = newUser(); val other = newUser(); member(project, me); member(project, other)
        val commented = issue(project, other, null, title = "댓글 단 이슈"); issue(project, other, null, title = "무관한 이슈")
        comments.save(IssueComment(contents = "내 댓글", createdDate = Date(), authorId = me.id, authorLoginId = me.loginId, authorName = me.name, issue = commented, projectId = project.id!!))
        val shared = issue(project, other, null, title = "공유받은 이슈"); sharers.save(IssueSharer(created = LocalDate.now(), loginId = me.loginId, user = me, issue = shared))
        json("/-_-api/v1/user/issues?filter=commented", tokenOf(me)).andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(1)).andExpect(jsonPath("$.result[0].title").value("댓글 단 이슈"))
        json("/-_-api/v1/user/issues?filter=shared", tokenOf(me)).andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(1)).andExpect(jsonPath("$.result[0].title").value("공유받은 이슈"))
        json("/-_-api/v1/user/issues?filter=nothing", tokenOf(me)).andExpect(status().isBadRequest)
    }
}
