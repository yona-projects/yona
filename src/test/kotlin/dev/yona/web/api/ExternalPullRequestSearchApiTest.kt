package dev.yona.web.api

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 풀 리퀘스트 검색 REST(로드맵 X-37f, `decisions.md` D-52)를 검증한다: 사이트 전체 검색(`GET /search/pullrequests`)과 프로젝트의 풀 리퀘스트 목록 조건(`q`, `author`, `assignee`,
 * `reviewRequested`, `labelIds`). 사이트 검색은 토큰 주인이 읽을 수 있는 프로젝트의 풀 리퀘스트만 찾는다. 웹 검색은 그대로 둔다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalPullRequestSearchApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()
    private val word = "prq${System.nanoTime()}"

    private fun newUser(scopes: Collection<String>? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "ps$n", name = "ps$n", email = "ps$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        tokens[saved.id!!] = "pst$n"
        if (scopes == null) dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!)) else dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!), scopes)
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "pso$n", name = "psp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun member(project: Project, user: User) { projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }

    private fun pull(project: Project, author: User, title: String, state: State = State.OPEN, assignee: User? = null, reviewer: User? = null, label: IssueLabel? = null): PullRequest {
        val number = (project.lastIssueNumber ?: 0) + 1; project.lastIssueNumber = number; projects.save(project)
        return pullRequests.save(PullRequest(title = title, body = "본문", toProject = project, fromProject = project, toBranch = "refs/heads/master", fromBranch = "refs/heads/f$number", contributor = author, created = Date(), updated = Date(), state = state, number = number)
            .also { pr -> assignee?.let { pr.assignees.add(it) }; reviewer?.let { pr.reviewRequests.add(it) }; label?.let { pr.labels.add(it) } })
    }

    private fun json(path: String, token: String?) = mockMvc.perform(get(path).apply { token?.let { header("Authorization", "token $it") } }.accept(MediaType.APPLICATION_JSON))

    // --- 사이트 전체 ---

    @Test
    fun `풀 리퀘스트를 낱말과 상태로 찾고 읽을 수 있는 프로젝트의 것만 나온다`() {
        val open = newProject(); val secret = newProject(ProjectScope.PRIVATE); val me = newUser(); val author = newUser(); member(secret, author)
        pull(open, author, "열린 $word 요청"); pull(open, author, "닫힌 $word 요청", State.CLOSED); pull(open, author, "병합된 $word 요청", State.MERGED); pull(open, author, "상관없는 요청"); pull(secret, author, "비밀 $word 요청")
        json("/-_-api/v1/search/pullrequests?keyword=$word", tokenOf(me)).andExpect(status().isOk).andExpect(jsonPath("$.totalCount").value(3)).andExpect(jsonPath("$.items.length()").value(3)).andExpect(jsonPath("$.keyword").value(word)).andExpect(jsonPath("$.page").value(1))
        json("/-_-api/v1/search/pullrequests?keyword=$word&state=open", tokenOf(me)).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].title").value("열린 $word 요청")).andExpect(jsonPath("$.items[0].toProject.name").value(open.name))
        json("/-_-api/v1/search/pullrequests?keyword=$word&state=closed", tokenOf(me)).andExpect(jsonPath("$.totalCount").value(2))
        json("/-_-api/v1/search/pullrequests?keyword=$word", tokenOf(author)).andExpect(jsonPath("$.totalCount").value(4))
        json("/-_-api/v1/search/pullrequests?keyword=nothing-$word", tokenOf(me)).andExpect(status().isOk).andExpect(jsonPath("$.totalCount").value(0)).andExpect(jsonPath("$.items.length()").value(0))
    }

    @Test
    fun `쪽을 나누고 틀린 요청과 범위를 거절한다`() {
        val project = newProject(); val me = newUser(); (1..21).forEach { pull(project, me, "쪽 $word $it") }
        json("/-_-api/v1/search/pullrequests?keyword=$word", tokenOf(me)).andExpect(jsonPath("$.items.length()").value(20)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.totalCount").value(21))
        json("/-_-api/v1/search/pullrequests?keyword=$word&pageNum=2", tokenOf(me)).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.page").value(2))
        json("/-_-api/v1/search/pullrequests", tokenOf(me)).andExpect(status().isBadRequest)
        json("/-_-api/v1/search/pullrequests?keyword=", tokenOf(me)).andExpect(status().isBadRequest)
        json("/-_-api/v1/search/pullrequests?keyword=$word&state=merged", tokenOf(me)).andExpect(status().isBadRequest)
        json("/-_-api/v1/search/pullrequests?keyword=$word&pageNum=0", tokenOf(me)).andExpect(status().isBadRequest)
        json("/-_-api/v1/search/pullrequests?keyword=$word", null).andExpect(status().isUnauthorized)
        json("/-_-api/v1/search/pullrequests?keyword=$word", tokenOf(newUser(listOf("public_repo")))).andExpect(status().isForbidden)
    }

    // --- 프로젝트 목록 조건 ---

    @Test
    fun `프로젝트의 풀 리퀘스트 목록은 낱말과 작성자와 담당자와 리뷰 요청과 라벨로 거른다`() {
        val project = newProject(); val a = newUser(); val b = newUser(); val c = newUser(); member(project, a); member(project, b)
        val category = categories.save(IssueLabelCategory(project = project, name = "종류", isExclusive = false)); val bug = labels.save(IssueLabel(project = project, category = category, name = "bug", color = "#f00"))
        pull(project, a, "알파 $word", assignee = b); pull(project, b, "베타 $word", reviewer = c, label = bug); pull(project, a, "감마")
        val base = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/pullrequests"
        json("$base?q=$word", tokenOf(a)).andExpect(status().isOk).andExpect(jsonPath("$.totalCount").value(2))
        json("$base?author=${a.loginId}", tokenOf(a)).andExpect(jsonPath("$.totalCount").value(2))
        json("$base?assignee=${b.loginId}", tokenOf(a)).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].title").value("알파 $word"))
        json("$base?reviewRequested=${c.loginId}", tokenOf(a)).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].title").value("베타 $word"))
        json("$base?labelIds=${bug.id}", tokenOf(a)).andExpect(jsonPath("$.totalCount").value(1))
        json("$base?q=$word&author=${b.loginId}", tokenOf(a)).andExpect(jsonPath("$.totalCount").value(1))
        json("$base?author=nobody-$word", tokenOf(a)).andExpect(status().isBadRequest)
        json("$base?assignee=nobody-$word", tokenOf(a)).andExpect(status().isBadRequest)
        json("$base?reviewRequested=nobody-$word", tokenOf(a)).andExpect(status().isBadRequest)
    }
}
