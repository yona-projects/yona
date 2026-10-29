package dev.yona.web.api

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.pullrequest.ForkService
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertTrue

/**
 * 외부용 풀 리퀘스트 REST API의 조회를 검증한다(로드맵 X-15, `decisions.md` D-39): 목록, 상세, 커밋, 변경 파일, 타임라인, 리뷰, 리뷰 댓글.
 * 개인용 API 토큰으로만 인증하고(범위와 사용자 권한을 함께 본다) 응답은 Yona 자체 JSON 모양(카멜 규칙)이다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-external-pr"])
class ExternalPullRequestApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var tokens: ApiTokenService
    @Autowired private lateinit var assigneeService: dev.yona.domain.pullrequest.PullRequestAssigneeService
    @Autowired private lateinit var failures: dev.yona.security.TokenFailureLimiter
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "epr$n", name = "이름epr$n", email = "epr$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    /** 원본(매니저와 구성원 한 명씩)과 포크를 만들고 포크에서 원본으로 보낸 열린 풀 리퀘스트 하나가 있다. */
    private class Setup(val origin: Project, val fork: Project, val contributor: User, val manager: User, val member: User, val pr: PullRequest) {
        val api get() = "/-_-api/v1/owners/${origin.owner}/projects/${origin.name}/pullrequests"
    }

    private fun setup(scope: ProjectScope = ProjectScope.PUBLIC): Setup {
        val n = ++sequence
        val origin = projects.save(Project(owner = "epo$n", name = "epp$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        GitTestSupport.pushCommits(repositories, origin, TestCommit("기반", mapOf("a.txt" to "line1\nline2\nline3\n".toByteArray())))
        val manager = user().also { projectUsers.save(ProjectUser(user = it, project = origin, role = roles.findById(RoleType.MANAGER.id).get())) }
        val member = user().also { projectUsers.save(ProjectUser(user = it, project = origin, role = roles.findById(RoleType.MEMBER.id).get())) }
        val contributor = user()
        val fork = forks.fork(origin, contributor.loginId!!, "epf$n", scope, contributor)
        GitTestSupport.appendCommits(repositories, fork, TestCommit("새 파일", mapOf("b.txt" to "b\n".toByteArray())))
        mockMvc.perform(
            post("/${fork.owner}/${fork.name}/pullRequests").session(login(contributor)).param("fromProjectId", fork.id.toString()).param("toProjectId", origin.id.toString())
                .param("fromBranch", "refs/heads/master").param("toBranch", "refs/heads/master").param("title", "요청 제목").param("body", "요청 **본문**")
        ).andExpect(status().isSeeOther)
        return Setup(origin, fork, contributor, manager, member, pullRequests.findAll().maxByOrNull { it.id!! }!!)
    }

    /** 사용자에게 주는 개인용 토큰이다. 범위를 주지 않으면 `repo`이다. */
    private fun token(user: User, vararg scopes: String): String =
        (tokens.create(user, "t", (scopes.toList().ifEmpty { listOf("repo") }).toSet(), null) as TokenCreation.Created).value

    private fun call(builder: MockHttpServletRequestBuilder, token: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.also { failures.clear() })

    // --- 인증 ---

    @Test
    fun `토큰이 없으면 401이고 저장소 범위가 없는 토큰은 403이다`() {
        val s = setup()
        call(get(s.api), null).andExpect(status().isUnauthorized)
        call(get(s.api), token(s.manager, "user")).andExpect(status().isForbidden)
        call(get(s.api), token(s.manager)).andExpect(status().isOk)
    }

    @Test
    fun `비공개 프로젝트는 구성원이 아닌 사용자에게 403이고 public repo 토큰은 비공개에 쓸 수 없다`() {
        val s = setup(ProjectScope.PRIVATE)
        val outsider = user()
        call(get(s.api), token(outsider)).andExpect(status().isForbidden)
        call(get(s.api), token(s.manager, "public_repo")).andExpect(status().isForbidden)
        call(get(s.api), token(s.member)).andExpect(status().isOk)
    }

    @Test
    fun `없는 프로젝트는 403이고 없는 풀 리퀘스트 번호는 404이다`() {
        val s = setup()
        call(get("/-_-api/v1/owners/nobody/projects/nothing/pullrequests"), token(s.manager)).andExpect(status().isForbidden)
        call(get("${s.api}/999"), token(s.manager)).andExpect(status().isNotFound).andExpect(jsonPath("$.message").exists())
    }

    // --- 목록 ---

    @Test
    fun `목록은 열린 풀 리퀘스트를 기본으로 보여 주고 닫힌 것은 state closed로 본다`() {
        val s = setup()
        val t = token(s.manager)
        call(get(s.api), t).andExpect(status().isOk).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].number").value(s.pr.number!!.toInt()))
            .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.totalPages").value(1))
        call(get(s.api).param("state", "closed"), t).andExpect(jsonPath("$.items.length()").value(0))
        s.pr.state = State.CLOSED; pullRequests.save(s.pr); em.flush()
        call(get(s.api), t).andExpect(jsonPath("$.items.length()").value(0))
        call(get(s.api).param("state", "closed"), t).andExpect(jsonPath("$.items[0].state").value("closed"))
        call(get(s.api).param("state", "nope"), t).andExpect(status().isBadRequest)
    }

    // --- 상세 ---

    @Test
    fun `상세는 Yona 자체 모양의 JSON이다`() {
        val s = setup()
        call(get("${s.api}/${s.pr.number}"), token(s.manager)).andExpect(status().isOk)
            .andExpect(jsonPath("$.number").value(s.pr.number!!.toInt())).andExpect(jsonPath("$.id").value(s.pr.id!!.toInt()))
            .andExpect(jsonPath("$.title").value("요청 제목")).andExpect(jsonPath("$.body").value("요청 **본문**")).andExpect(jsonPath("$.state").value("open"))
            .andExpect(jsonPath("$.author.loginId").value(s.contributor.loginId)).andExpect(jsonPath("$.author.name").value(s.contributor.name))
            .andExpect(jsonPath("$.fromProject.owner").value(s.fork.owner)).andExpect(jsonPath("$.fromProject.name").value(s.fork.name))
            .andExpect(jsonPath("$.toProject.owner").value(s.origin.owner)).andExpect(jsonPath("$.toProject.name").value(s.origin.name))
            .andExpect(jsonPath("$.fromBranch").value("refs/heads/master")).andExpect(jsonPath("$.toBranch").value("refs/heads/master"))
            .andExpect(jsonPath("$.createdAt").exists()).andExpect(jsonPath("$.updatedAt").exists())
            .andExpect(jsonPath("$.assignees.length()").value(0)).andExpect(jsonPath("$.labels.length()").value(0)).andExpect(jsonPath("$.requestedReviewers.length()").value(0))
            .andExpect(jsonPath("$.isConflict").value(false)).andExpect(jsonPath("$.approvals.count").value(0)).andExpect(jsonPath("$.approvals.required").value(0))
            .andExpect(jsonPath("$.mergeable").exists()).andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.endsWith("/pullRequest/${s.pr.number}")))
    }

    // --- 커밋과 변경 파일 ---

    @Test
    fun `커밋 목록과 변경 파일 목록을 돌려준다`() {
        val s = setup()
        val t = token(s.manager)
        call(get("${s.api}/${s.pr.number}/commits"), t).andExpect(status().isOk).andExpect(jsonPath("$.commits.length()").value(1))
            .andExpect(jsonPath("$.commits[0].commitId").exists()).andExpect(jsonPath("$.commits[0].message").value("새 파일"))
        call(get("${s.api}/${s.pr.number}/files"), t).andExpect(status().isOk).andExpect(jsonPath("$.files.length()").value(1))
            .andExpect(jsonPath("$.files[0].path").value("b.txt")).andExpect(jsonPath("$.files[0].status").value("added"))
            .andExpect(jsonPath("$.files[0].additions").value(1)).andExpect(jsonPath("$.files[0].deletions").value(0))
            .andExpect(jsonPath("$.files[0].patch").value(org.hamcrest.Matchers.containsString("+b")))
    }

    // --- 타임라인, 리뷰, 댓글 ---

    @Test
    fun `타임라인은 이벤트를 만든 순서로 돌려준다`() {
        val s = setup()
        val t = token(s.manager)
        assigneeService.set(pullRequests.findById(s.pr.id!!).get(), s.member, s.manager)
        em.flush(); em.clear()
        call(get("${s.api}/${s.pr.number}/timeline"), t).andExpect(status().isOk)
            .andExpect(jsonPath("$.events[?(@.type=='PULL_REQUEST_ASSIGNEE_CHANGED')].newValue").value(s.member.loginId))
            .andExpect(jsonPath("$.events[0].sender").exists()).andExpect(jsonPath("$.events[0].createdAt").exists())
    }

    @Test
    fun `리뷰 목록은 판정 이력과 해제 여부를 돌려준다`() {
        val s = setup()
        mockMvcReview(s, s.member, "APPROVED", null)
        call(get("${s.api}/${s.pr.number}/reviews"), token(s.manager)).andExpect(status().isOk)
            .andExpect(jsonPath("$.reviews.length()").value(1)).andExpect(jsonPath("$.reviews[0].verdict").value("APPROVED"))
            .andExpect(jsonPath("$.reviews[0].author.loginId").value(s.member.loginId)).andExpect(jsonPath("$.reviews[0].dismissed").value(false))
    }

    @Test
    fun `리뷰 댓글은 스레드별로 돌려주고 남의 임시 댓글은 보이지 않는다`() {
        val s = setup()
        mockMvcLine(s, s.manager, "공개 줄 댓글", pending = false)
        mockMvcLine(s, s.member, "내 임시 댓글", pending = true)
        em.flush(); em.clear()
        val body = call(get("${s.api}/${s.pr.number}/comments"), token(s.manager)).andExpect(status().isOk).andExpect(jsonPath("$.threads.length()").value(1))
            .andExpect(jsonPath("$.threads[0].comments[0].body").value("공개 줄 댓글")).andExpect(jsonPath("$.threads[0].path").value("b.txt")).andReturn().response.contentAsString
        assertTrue(!body.contains("내 임시 댓글"))
    }

    private fun mockMvcReview(s: Setup, by: User, verdict: String, contents: String?) {
        mockMvc.perform(post("/${s.origin.owner}/${s.origin.name}/pullRequest/${s.pr.number}/reviews").session(login(by)).param("verdict", verdict).apply { contents?.let { param("contents", it) } })
        em.flush(); em.clear()
    }

    private fun mockMvcLine(s: Setup, by: User, text: String, pending: Boolean) {
        mockMvc.perform(
            post("/${s.origin.owner}/${s.origin.name}/pullRequest/${s.pr.id}/comments").session(login(by)).param("contents", text).param("path", "b.txt")
                .param("startSide", "B").param("startLine", "1").param("startColumn", "0").param("endSide", "B").param("endLine", "1").param("endColumn", "1").apply { if (pending) param("pending", "true") }
        )
    }
}
