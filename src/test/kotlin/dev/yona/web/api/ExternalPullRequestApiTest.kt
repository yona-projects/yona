package dev.yona.web.api

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
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
    @Autowired private lateinit var rulesetRepository: dev.yona.domain.project.RulesetRepository
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean private lateinit var mergerSpy: dev.yona.domain.pullrequest.PullRequestMerger
    @Autowired private lateinit var tokens: ApiTokenService
    @Autowired private lateinit var attachmentService: dev.yona.domain.misc.AttachmentService
    @Autowired private lateinit var attachmentRepository: dev.yona.domain.misc.AttachmentRepository
    @Autowired private lateinit var assigneeService: dev.yona.domain.pullrequest.PullRequestAssigneeService
    @Autowired private lateinit var failures: dev.yona.security.TokenFailureLimiter
    @PersistenceContext private lateinit var em: EntityManager

    // 병합 응답 시험이 데이터를 확정하므로 다른 시험과 이름이 겹치지 않게 순번을 시험마다 다른 값에서 시작한다
    private var sequence = (System.nanoTime() % 1_000_000_000L).toInt()

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
            // 서명 판정(X-4): 서명이 없는 커밋은 unsigned이다
            .andExpect(jsonPath("$.commits[0].verification.verified").value(false)).andExpect(jsonPath("$.commits[0].verification.reason").value("unsigned"))
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

    // --- 쓰기: 상태 ---

    private fun json(builder: MockHttpServletRequestBuilder, body: String? = null) =
        builder.contentType("application/json").apply { body?.let { content(it) } }

    @Test
    fun `닫고 다시 열 수 있고 이미 열린 것을 다시 열거나 병합된 것을 닫으면 409이다`() {
        val s = setup(); val t = token(s.manager)
        call(post("${s.api}/${s.pr.number}/reopen"), t).andExpect(status().isConflict)
        call(post("${s.api}/${s.pr.number}/close"), t).andExpect(status().isOk).andExpect(jsonPath("$.state").value("closed")).andExpect(jsonPath("$.receiver.loginId").value(s.manager.loginId))
        call(post("${s.api}/${s.pr.number}/reopen"), t).andExpect(status().isOk).andExpect(jsonPath("$.state").value("open"))
        em.flush(); em.clear()
        val pr = pullRequests.findById(s.pr.id!!).get(); pr.state = State.MERGED; pullRequests.save(pr); em.flush()
        call(post("${s.api}/${s.pr.number}/close"), t).andExpect(status().isConflict)
    }

    @Test
    fun `쓰기 권한이 없으면 403이다`() {
        val s = setup(); val outsider = user(); val t = token(outsider)
        call(post("${s.api}/${s.pr.number}/close"), t).andExpect(status().isForbidden)
        call(post("${s.api}/${s.pr.number}/merge"), t).andExpect(status().isForbidden)
        call(json(post("${s.api}/${s.pr.number}/assignees"), """{"assignees":[]}"""), t).andExpect(status().isForbidden)
        call(post("${s.api}/${s.pr.number}/reopen"), token(s.manager, "user")).andExpect(status().isForbidden)
    }

    // 병합 메서드는 트랜잭션을 걸지 않으므로(R-76) 응답을 만들 때 지연 로딩 컬렉션이 세션 밖에서 읽히면 500이 난다. 클래스의 시험 트랜잭션이
    // 이를 가리므로, 준비한 데이터를 확정하고 트랜잭션 없이 호출해 운영과 같은 조건을 만든다(실서버 종단 시험에서 발견했다).
    @Test
    fun `병합 응답은 트랜잭션 밖에서도 담당자와 라벨 같은 지연 컬렉션을 읽는다`() {
        val s = setup(); val t = token(s.manager)
        org.springframework.test.context.transaction.TestTransaction.flagForCommit()
        org.springframework.test.context.transaction.TestTransaction.end()
        call(json(post("${s.api}/${s.pr.number}/merge"), """{"method":"merge"}"""), t).andExpect(status().isOk).andExpect(jsonPath("$.state").value("merged")).andExpect(jsonPath("$.assignees").isArray)
    }

    @Test
    fun `병합하면 병합됨 상태와 받은 사람과 병합 방식을 돌려준다`() {
        val s = setup(); val t = token(s.manager)
        call(json(post("${s.api}/${s.pr.number}/merge"), """{"method":"fast-forward"}"""), t).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Unsupported merge method: fast-forward"))
        call(json(post("${s.api}/${s.pr.number}/merge"), """{"method":"merge"}"""), t).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("merged")).andExpect(jsonPath("$.receiver.loginId").value(s.manager.loginId)).andExpect(jsonPath("$.mergedAt").isNotEmpty)
            .andExpect(jsonPath("$.mergeMethod").value("merge"))
        call(post("${s.api}/${s.pr.number}/merge"), t).andExpect(status().isConflict)
    }

    @Test
    fun `스쿼시와 리베이스로 병합하고 제목과 본문을 줄 수 있다 (X-28)`() {
        val squash = setup(); val t = token(squash.manager)
        call(json(post("${squash.api}/${squash.pr.number}/merge"), """{"method":"squash","commitTitle":"직접 제목","commitMessage":"직접 본문"}"""), t).andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("merged")).andExpect(jsonPath("$.mergeMethod").value("squash"))
        val rebase = setup()
        call(json(post("${rebase.api}/${rebase.pr.number}/merge"), """{"method":"rebase"}"""), token(rebase.manager)).andExpect(status().isOk).andExpect(jsonPath("$.mergeMethod").value("rebase"))
    }

    @Test
    fun `프로젝트가 허용하지 않은 병합 방식은 400이고 기본 방식을 정하면 방식을 생략해도 그 방식이다 (X-28)`() {
        val s = setup(); val t = token(s.manager)
        s.origin.allowSquashMerge = false; em.persist(s.origin); em.flush()
        call(json(post("${s.api}/${s.pr.number}/merge"), """{"method":"squash"}"""), t).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Merge method not allowed: squash"))
        s.origin.defaultMergeMethod = "rebase"; em.persist(s.origin); em.flush()
        call(post("${s.api}/${s.pr.number}/merge"), t).andExpect(status().isOk).andExpect(jsonPath("$.mergeMethod").value("rebase"))
    }

    @Test
    fun `상세는 방식별 허용 여부와 가능 여부와 이유를 담는다 (X-28)`() {
        val s = setup(); val t = token(s.manager)
        s.origin.allowSquashMerge = false; em.persist(s.origin)
        s.pr.rebaseBlocked = "merge-commits"; em.persist(s.pr); em.flush()
        call(get("${s.api}/${s.pr.number}"), t).andExpect(status().isOk)
            .andExpect(jsonPath("$.mergeMethods.merge.allowed").value(true)).andExpect(jsonPath("$.mergeMethods.merge.available").value(true)).andExpect(jsonPath("$.mergeMethods.merge.reason").isEmpty)
            .andExpect(jsonPath("$.mergeMethods.squash.allowed").value(false))
            .andExpect(jsonPath("$.mergeMethods.rebase.allowed").value(true)).andExpect(jsonPath("$.mergeMethods.rebase.available").value(false)).andExpect(jsonPath("$.mergeMethods.rebase.reason").value("merge-commits"))
            .andExpect(jsonPath("$.mergeMethod").isEmpty)
    }

    @Test
    fun `규칙 묶음이 막은 병합은 병합 중 표시를 남기지 않아 승인한 뒤 다시 병합할 수 있다`() {
        val s = setup()
        rulesetRepository.save(dev.yona.domain.project.Ruleset(project = s.origin, name = "master", pattern = "master", requiredApprovals = 1, createdDate = java.util.Date()))
        em.flush(); em.refresh(s.origin)
        call(post("${s.api}/${s.pr.number}/merge"), token(s.member)).andExpect(status().isConflict).andExpect(jsonPath("$.message").value("not enough review approvals"))
        em.flush(); em.refresh(s.pr)
        kotlin.test.assertNotEquals(true, s.pr.isMerging, "막힌 병합이 병합 중 표시를 남기면 승인한 뒤에도 병합할 수 없다")
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{"verdict":"APPROVED"}"""), token(s.manager)).andExpect(status().isCreated)
        call(post("${s.api}/${s.pr.number}/merge"), token(s.member)).andExpect(status().isOk)
    }

    @Test
    fun `병합기가 예외를 던져도 병합 중 표시를 남기지 않는다`() {
        val s = setup()
        // 코틀린의 널 불가 인자에는 Mockito 매처가 돌려주는 null을 그대로 넘길 수 없어 형 소거를 이용한 도우미를 쓴다
        @Suppress("UNCHECKED_CAST") fun <T> anything(): T { org.mockito.ArgumentMatchers.any<Any>(); return null as T }
        org.mockito.Mockito.doThrow(IllegalStateException("저장소를 읽지 못했다")).`when`(mergerSpy).merge(anything(), anything(), anything(), anything(), anything())
        try {
            call(post("${s.api}/${s.pr.number}/merge"), token(s.manager))
        } catch (expected: Exception) {
            // 예외는 요청 밖으로 나가도 된다. 중요한 것은 표시가 남지 않는 것이다
        }
        em.flush(); em.refresh(s.pr)
        kotlin.test.assertEquals(dev.yona.domain.enumeration.State.OPEN, s.pr.state, "병합기가 예외를 던졌으므로 병합되지 않아야 한다(스텁이 적용되었는지 확인한다)")
        kotlin.test.assertNotEquals(true, s.pr.isMerging, "병합기 예외 뒤에도 병합 중 표시가 남으면 요청이 굳는다")
        org.mockito.Mockito.reset(mergerSpy)
    }

    @Test
    fun `닫힌 풀 리퀘스트는 병합할 수 없다`() {
        val s = setup(); val t = token(s.manager)
        call(post("${s.api}/${s.pr.number}/close"), t).andExpect(status().isOk)
        call(post("${s.api}/${s.pr.number}/merge"), t).andExpect(status().isConflict)
    }

    @Test
    fun `병합한 뒤 출발 브랜치를 지우고 되살릴 수 있다`() {
        val s = setup(); val t = token(s.manager)
        call(post("${s.api}/${s.pr.number}/merge"), t).andExpect(status().isOk)
        call(delete("${s.api}/${s.pr.number}/fromBranch"), t).andExpect(status().isOk)
        call(post("${s.api}/${s.pr.number}/fromBranch/restore"), t).andExpect(status().isOk)
    }

    // --- 쓰기: 담당자, 라벨, 리뷰 요청 ---

    @Test
    fun `담당자를 지정하고 비울 수 있고 후보가 아니면 400이다`() {
        val s = setup(); val t = token(s.manager)
        call(json(post("${s.api}/${s.pr.number}/assignees"), """{"assignees":["${s.member.loginId}"]}"""), t).andExpect(status().isOk).andExpect(jsonPath("$.assignees[0].loginId").value(s.member.loginId))
        call(json(post("${s.api}/${s.pr.number}/assignees"), """{"assignees":[]}"""), t).andExpect(status().isOk).andExpect(jsonPath("$.assignees.length()").value(0))
        call(json(post("${s.api}/${s.pr.number}/assignees"), """{"assignees":["${user().loginId}"]}"""), t).andExpect(status().isBadRequest)
        call(json(post("${s.api}/${s.pr.number}/assignees"), """{"assignees":["nobody"]}"""), t).andExpect(status().isBadRequest)
        call(json(post("${s.api}/${s.pr.number}/assignees"), """{"assignees":["a","b"]}"""), t).andExpect(status().isBadRequest)
        call(json(post("${s.api}/${s.pr.number}/assignees"), """{}"""), t).andExpect(status().isBadRequest)
    }

    @Test
    fun `라벨을 붙이고 떼며 다른 프로젝트의 라벨은 400이다`() {
        val s = setup(); val t = token(s.manager)
        val category = IssueLabelCategory(project = s.origin, name = "분류${++sequence}").also { em.persist(it) }
        val bug = IssueLabel(category = category, color = "#33aa77", name = "bug", project = s.origin).also { em.persist(it) }
        val stray = IssueLabel(category = IssueLabelCategory(project = s.fork, name = "분류${++sequence}").also { em.persist(it) }, color = "#111111", name = "stray", project = s.fork).also { em.persist(it) }
        em.flush()
        call(json(post("${s.api}/${s.pr.number}/labels"), """{"attachingLabelIds":[${bug.id}]}"""), t).andExpect(status().isOk)
            .andExpect(jsonPath("$.labels[0].labelName").value("bug")).andExpect(jsonPath("$.labels[0].id").value(bug.id!!.toInt()))
        call(json(post("${s.api}/${s.pr.number}/labels"), """{"attachingLabelIds":[${stray.id}]}"""), t).andExpect(status().isBadRequest)
        call(json(post("${s.api}/${s.pr.number}/labels"), """{"attachingLabelIds":[999999]}"""), t).andExpect(status().isBadRequest)
        call(json(post("${s.api}/${s.pr.number}/labels"), """{"detachingLabelIds":[${bug.id}]}"""), t).andExpect(status().isOk).andExpect(jsonPath("$.labels.length()").value(0))
    }

    @Test
    fun `리뷰를 요청하고 취소할 수 있으며 작성자와 외부인은 요청할 수 없다`() {
        val s = setup(); val t = token(s.manager)
        call(json(post("${s.api}/${s.pr.number}/requestedreviewers"), """{"reviewer":"${s.member.loginId}"}"""), t).andExpect(status().isOk)
            .andExpect(jsonPath("$.requestedReviewers[0].loginId").value(s.member.loginId))
        call(json(post("${s.api}/${s.pr.number}/requestedreviewers"), """{"reviewer":"${s.contributor.loginId}"}"""), t).andExpect(status().isBadRequest)
        call(json(post("${s.api}/${s.pr.number}/requestedreviewers"), """{"reviewer":"${user().loginId}"}"""), t).andExpect(status().isBadRequest)
        call(json(post("${s.api}/${s.pr.number}/requestedreviewers"), """{"reviewer":"nobody"}"""), t).andExpect(status().isBadRequest)
        call(delete("${s.api}/${s.pr.number}/requestedreviewers/${s.member.loginId}"), t).andExpect(status().isOk).andExpect(jsonPath("$.requestedReviewers.length()").value(0))
        call(delete("${s.api}/${s.pr.number}/requestedreviewers/nobody"), t).andExpect(status().isNotFound)
    }

    // --- 쓰기: 리뷰 ---

    private fun lineBody(text: String, extra: String = "") = """{"body":"$text","path":"b.txt","startSide":"B","startLine":1,"startColumn":0,"endSide":"B","endLine":1,"endColumn":1$extra}"""

    @Test
    fun `리뷰를 제출하면 201과 리뷰를 돌려주고 승인 수가 늘며 잘못된 입력은 400이다`() {
        val s = setup(); val t = token(s.member)
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{"verdict":"APPROVED"}"""), t).andExpect(status().isCreated)
            .andExpect(jsonPath("$.review.verdict").value("APPROVED")).andExpect(jsonPath("$.review.author.loginId").value(s.member.loginId)).andExpect(jsonPath("$.publishedComments").value(0))
        call(get("${s.api}/${s.pr.number}"), t).andExpect(jsonPath("$.approvals.count").value(1))
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{}"""), t).andExpect(status().isBadRequest)
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{"verdict":"NOPE"}"""), t).andExpect(status().isBadRequest)
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{"verdict":"COMMENTED"}"""), t).andExpect(status().isBadRequest)
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{"verdict":"APPROVED"}"""), token(s.contributor)).andExpect(status().isForbidden)
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{"verdict":"COMMENTED","body":"의견입니다"}"""), token(user())).andExpect(status().isCreated)
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{"verdict":"APPROVED"}"""), token(user())).andExpect(status().isForbidden)
    }

    @Test
    fun `리뷰를 해제하려면 사유가 필요하고 받아들일 권한이 필요하다`() {
        val s = setup(); val member = token(s.member); val manager = token(s.manager)
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{"verdict":"APPROVED"}"""), member).andExpect(status().isCreated)
        em.flush(); em.clear()
        val reviewId = pullRequests.findById(s.pr.id!!).get().reviewHistory.single().id
        call(json(post("${s.api}/${s.pr.number}/reviews/$reviewId/dismissals"), """{"message":""}"""), manager).andExpect(status().isBadRequest)
        call(json(post("${s.api}/${s.pr.number}/reviews/$reviewId/dismissals"), """{"message":"잘못 승인"}"""), token(user())).andExpect(status().isForbidden)
        call(json(post("${s.api}/${s.pr.number}/reviews/999999/dismissals"), """{"message":"x"}"""), manager).andExpect(status().isNotFound)
        call(json(post("${s.api}/${s.pr.number}/reviews/$reviewId/dismissals"), """{"message":"잘못 승인"}"""), manager).andExpect(status().isOk)
            .andExpect(jsonPath("$.review.dismissed").value(true)).andExpect(jsonPath("$.review.dismissedReason").value("잘못 승인"))
        call(json(post("${s.api}/${s.pr.number}/reviews/$reviewId/dismissals"), """{"message":"또"}"""), manager).andExpect(status().isBadRequest)
    }

    // --- 쓰기: 리뷰 댓글 ---

    @Test
    fun `줄 댓글을 달면 201과 스레드 번호를 돌려주고 스레드에 이어 달 수 있다`() {
        val s = setup(); val t = token(s.member)
        val created = call(json(post("${s.api}/${s.pr.number}/comments"), lineBody("첫 줄 댓글")), t).andExpect(status().isCreated)
            .andExpect(jsonPath("$.comment.body").value("첫 줄 댓글")).andExpect(jsonPath("$.comment.pending").value(false)).andExpect(jsonPath("$.threadId").exists()).andReturn().response.contentAsString
        val threadId = Regex("\"threadId\":(\\d+)").find(created)!!.groupValues[1]
        call(json(post("${s.api}/${s.pr.number}/comments"), """{"body":"이어서","threadId":$threadId}"""), token(s.manager)).andExpect(status().isCreated)
        call(get("${s.api}/${s.pr.number}/comments"), t).andExpect(jsonPath("$.threads.length()").value(1)).andExpect(jsonPath("$.threads[0].comments.length()").value(2))
        call(json(post("${s.api}/${s.pr.number}/comments"), """{"body":"없는 스레드","threadId":999999}"""), t).andExpect(status().isNotFound)
        call(json(post("${s.api}/${s.pr.number}/comments"), """{"body":" "}"""), t).andExpect(status().isBadRequest)
        call(json(post("${s.api}/${s.pr.number}/comments"), lineBody("읽기 전용")), null).andExpect(status().isUnauthorized)
    }

    @Test
    fun `임시로 단 댓글은 본인의 조회에만 보이고 리뷰를 제출하면 공개된다`() {
        val s = setup(); val member = token(s.member); val manager = token(s.manager)
        call(json(post("${s.api}/${s.pr.number}/comments"), lineBody("임시 댓글", ""","pending":true""")), member).andExpect(status().isCreated).andExpect(jsonPath("$.comment.pending").value(true))
        em.flush(); em.clear()
        call(get("${s.api}/${s.pr.number}/comments"), member).andExpect(jsonPath("$.threads[0].pending").value(true)).andExpect(jsonPath("$.threads[0].comments[0].body").value("임시 댓글"))
        call(get("${s.api}/${s.pr.number}/comments"), manager).andExpect(jsonPath("$.threads.length()").value(0))
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{"verdict":"COMMENTED"}"""), member).andExpect(status().isCreated).andExpect(jsonPath("$.publishedComments").value(1))
        em.flush(); em.clear()
        call(get("${s.api}/${s.pr.number}/comments"), manager).andExpect(jsonPath("$.threads[0].comments[0].body").value("임시 댓글")).andExpect(jsonPath("$.threads[0].pending").value(false))
    }

    @Test
    fun `댓글을 고치면 수정 시각이 남고 임시 댓글은 작성자만 고친다`() {
        val s = setup(); val member = token(s.member); val manager = token(s.manager)
        val publicId = Regex("\"comment\":\\{\"id\":(\\d+)").find(call(json(post("${s.api}/${s.pr.number}/comments"), lineBody("원문")), member).andReturn().response.contentAsString)!!.groupValues[1]
        call(json(patch("${s.api}/${s.pr.number}/comments/$publicId"), """{"body":"고친 내용"}"""), manager).andExpect(status().isOk).andExpect(jsonPath("$.comment.body").value("고친 내용")).andExpect(jsonPath("$.comment.editedAt").isNotEmpty)
        call(json(patch("${s.api}/${s.pr.number}/comments/$publicId"), """{"body":" "}"""), member).andExpect(status().isBadRequest)
        call(json(patch("${s.api}/${s.pr.number}/comments/$publicId"), """{"body":"외부인"}"""), token(user())).andExpect(status().isForbidden)
        // 수정은 GitHub처럼 다른 구성원에게도 열려 있다(지우기만 작성자와 관리자, D-48 확장)
        val peer = user().also { projectUsers.save(ProjectUser(user = it, project = s.origin, role = roles.findById(RoleType.MEMBER.id).get())) }
        call(json(patch("${s.api}/${s.pr.number}/comments/$publicId"), """{"body":"다른 구성원이 고침"}"""), token(peer)).andExpect(status().isOk)
        em.flush(); em.clear()
        val pendingId = Regex("\"comment\":\\{\"id\":(\\d+)").find(call(json(post("${s.api}/${s.pr.number}/comments"), lineBody("임시", ""","pending":true""")), member).andReturn().response.contentAsString)!!.groupValues[1]
        em.flush(); em.clear()
        call(json(patch("${s.api}/${s.pr.number}/comments/$pendingId"), """{"body":"남의 임시"}"""), manager).andExpect(status().isNotFound)
        call(json(patch("${s.api}/${s.pr.number}/comments/$pendingId"), """{"body":"내 임시 수정"}"""), member).andExpect(status().isOk).andExpect(jsonPath("$.comment.pending").value(true))
    }

    @Test
    fun `댓글을 지우고 임시 리뷰를 버릴 수 있다`() {
        val s = setup(); val member = token(s.member); val manager = token(s.manager)
        val publicId = Regex("\"comment\":\\{\"id\":(\\d+)").find(call(json(post("${s.api}/${s.pr.number}/comments"), lineBody("지울 댓글")), member).andReturn().response.contentAsString)!!.groupValues[1]
        call(delete("${s.api}/${s.pr.number}/comments/$publicId"), token(user())).andExpect(status().isForbidden)
        call(delete("${s.api}/${s.pr.number}/comments/$publicId"), manager).andExpect(status().isOk)
        call(delete("${s.api}/${s.pr.number}/comments/$publicId"), manager).andExpect(status().isNotFound)
        call(json(post("${s.api}/${s.pr.number}/comments"), lineBody("임시 하나", ""","pending":true""")), member).andExpect(status().isCreated)
        call(json(post("${s.api}/${s.pr.number}/comments"), lineBody("임시 둘", ""","pending":true""")), member).andExpect(status().isCreated)
        em.flush(); em.clear()
        call(delete("${s.api}/${s.pr.number}/pendingreview"), manager).andExpect(status().isOk).andExpect(jsonPath("$.discarded").value(0))
        call(delete("${s.api}/${s.pr.number}/pendingreview"), member).andExpect(status().isOk).andExpect(jsonPath("$.discarded").value(2))
        call(get("${s.api}/${s.pr.number}/comments"), member).andExpect(jsonPath("$.threads.length()").value(0))
    }

    @Test
    fun `스레드를 닫고 다시 열 수 있다`() {
        val s = setup(); val member = token(s.member); val manager = token(s.manager)
        val threadId = Regex("\"threadId\":(\\d+)").find(call(json(post("${s.api}/${s.pr.number}/comments"), lineBody("스레드")), member).andReturn().response.contentAsString)!!.groupValues[1]
        call(post("${s.api}/${s.pr.number}/threads/$threadId/close"), manager).andExpect(status().isOk).andExpect(jsonPath("$.thread.state").value("closed"))
        call(post("${s.api}/${s.pr.number}/threads/$threadId/open"), manager).andExpect(status().isOk).andExpect(jsonPath("$.thread.state").value("open"))
        call(post("${s.api}/${s.pr.number}/threads/$threadId/close"), token(user())).andExpect(status().isForbidden)
        call(post("${s.api}/${s.pr.number}/threads/999999/close"), manager).andExpect(status().isNotFound)
    }

    // --- 만들기와 고치기 ---

    /** 포크에 새 브랜치 `topic`을 만든다(이미 있는 풀 리퀘스트와 다른 브랜치 쌍이 필요하다). */
    private fun topicBranch(s: Setup, name: String = "topic") {
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("주제 커밋", mapOf("c.txt" to "c\n".toByteArray())), branch = name, baseBranch = "master")
    }

    private fun createBody(s: Setup, extra: String = "", branch: String = "topic", title: String = "REST로 만든 요청") =
        """{"title":"$title","body":"본문 **내용**","fromProject":{"owner":"${s.fork.owner}","name":"${s.fork.name}"},"fromBranch":"$branch","toBranch":"master"$extra}"""

    @Test
    fun `풀 리퀘스트를 만들면 201과 새 번호와 위치를 돌려준다`() {
        val s = setup(); topicBranch(s)
        val r = call(json(post(s.api), createBody(s)), token(s.contributor)).andExpect(status().isCreated)
            .andExpect(jsonPath("$.number").value(s.pr.number!!.toInt() + 1)).andExpect(jsonPath("$.state").value("open")).andExpect(jsonPath("$.title").value("REST로 만든 요청"))
            .andExpect(jsonPath("$.author.loginId").value(s.contributor.loginId)).andExpect(jsonPath("$.fromBranch").value("refs/heads/topic")).andExpect(jsonPath("$.toBranch").value("refs/heads/master"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", org.hamcrest.Matchers.endsWith("/pullRequest/${s.pr.number!! + 1}")))
        assertTrue(r.andReturn().response.contentAsString.contains("fromProject"))
    }

    @Test
    fun `같은 브랜치 쌍의 열린 풀 리퀘스트가 있으면 409이고 그 번호를 알려 준다`() {
        val s = setup()
        call(json(post(s.api), createBody(s, branch = "master")), token(s.contributor)).andExpect(status().isConflict)
            .andExpect(jsonPath("$.pullRequest.number").value(s.pr.number!!.toInt())).andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `만들 때 잘못된 입력은 400이고 출발 프로젝트를 쓸 수 없는 사람은 403이다`() {
        val s = setup(); topicBranch(s); val t = token(s.contributor)
        call(json(post(s.api), createBody(s, title = "")), t).andExpect(status().isBadRequest)
        call(json(post(s.api), createBody(s, title = "가".repeat(256))), t).andExpect(status().isBadRequest)
        call(json(post(s.api), createBody(s, branch = "nope")), t).andExpect(status().isBadRequest)
        call(json(post(s.api), """{"title":"t","body":"","fromProject":{"owner":"nobody","name":"nothing"},"fromBranch":"topic","toBranch":"master"}"""), t).andExpect(status().isBadRequest)
        call(json(post(s.api), """{"title":"t","body":"","fromBranch":"topic","toBranch":"master"}"""), token(s.manager)).andExpect(status().isBadRequest) // 출발 프로젝트를 생략하면 받는 프로젝트이고 거기에는 topic이 없다
        call(json(post(s.api), """{"title":"t","body":"","fromBranch":"topic","toBranch":"master"}"""), t).andExpect(status().isForbidden) // 포크 주인이라도 원본의 구성원이 아니면(게스트) 원본의 브랜치로는 만들 수 없다
        call(json(post(s.api), createBody(s)), token(user())).andExpect(status().isForbidden)
        call(json(post(s.api), createBody(s)), null).andExpect(status().isUnauthorized)
    }

    @Test
    fun `담당자와 라벨은 받는 프로젝트를 고칠 수 있는 사람만 정하며 만들 때 함께 지정한다`() {
        val s = setup(); topicBranch(s)
        val category = IssueLabelCategory(project = s.origin, name = "분류${++sequence}").also { em.persist(it) }
        val bug = IssueLabel(category = category, color = "#33aa77", name = "bug", project = s.origin).also { em.persist(it) }
        em.flush()
        call(json(post(s.api), createBody(s, ""","assignees":["${s.member.loginId}"]""")), token(s.contributor)).andExpect(status().isForbidden)
        projectUsers.save(ProjectUser(user = s.contributor, project = s.origin, role = roles.findById(RoleType.MEMBER.id).get())); em.flush()
        call(json(post(s.api), createBody(s, ""","assignees":["${s.member.loginId}"],"labelIds":[${bug.id}]""")), token(s.contributor)).andExpect(status().isCreated)
            .andExpect(jsonPath("$.assignees[0].loginId").value(s.member.loginId)).andExpect(jsonPath("$.labels[0].labelName").value("bug"))
    }

    @Test
    fun `만들 때 임시로 올린 첨부를 붙일 수 있다`() {
        val s = setup(); topicBranch(s)
        val file = java.io.File.createTempFile("att", ".png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val temp = attachmentService.store(file, "사진.png", dev.yona.domain.enumeration.ResourceType.USER, s.contributor.id!!, s.contributor.loginId).attachment
        val created = call(json(post(s.api), createBody(s, ""","temporaryUploadFiles":[${temp.id}]""")), token(s.contributor)).andExpect(status().isCreated).andReturn().response.contentAsString
        val id = Regex("\"id\":(\\d+)").find(created)!!.groupValues[1].toLong()
        assertTrue(attachmentRepository.findByContainerTypeAndContainerIdOrderById(dev.yona.domain.enumeration.ResourceType.PULL_REQUEST, id).size == 1)
    }

    @Test
    fun `제목과 본문과 브랜치를 고칠 수 있고 입력 오류와 중복은 거절한다`() {
        val s = setup(); topicBranch(s); topicBranch(s, "other-topic"); val t = token(s.manager)
        call(json(patch("${s.api}/${s.pr.number}"), """{"title":"고친 제목","body":"고친 본문"}"""), t).andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("고친 제목")).andExpect(jsonPath("$.body").value("고친 본문")).andExpect(jsonPath("$.fromBranch").value("refs/heads/master"))
        call(json(patch("${s.api}/${s.pr.number}"), """{"fromBranch":"topic"}"""), t).andExpect(status().isOk).andExpect(jsonPath("$.fromBranch").value("refs/heads/topic")).andExpect(jsonPath("$.title").value("고친 제목"))
        call(json(post(s.api), createBody(s, branch = "other-topic")), token(s.contributor)).andExpect(status().isCreated)
        call(json(patch("${s.api}/${s.pr.number}"), """{"fromBranch":"other-topic"}"""), t).andExpect(status().isConflict)
        call(json(patch("${s.api}/${s.pr.number}"), """{"title":""}"""), t).andExpect(status().isBadRequest)
        call(json(patch("${s.api}/${s.pr.number}"), """{"title":"${"가".repeat(256)}"}"""), t).andExpect(status().isBadRequest)
        call(json(patch("${s.api}/${s.pr.number}"), """{"toBranch":"nope"}"""), t).andExpect(status().isBadRequest)
        call(json(patch("${s.api}/${s.pr.number}"), """{}"""), t).andExpect(status().isBadRequest)
        call(json(patch("${s.api}/${s.pr.number}"), """{"title":"외부인"}"""), token(user())).andExpect(status().isForbidden)
    }

    @Test
    fun `줄 댓글에도 임시로 올린 첨부를 붙일 수 있다`() {
        val s = setup()
        val file = java.io.File.createTempFile("att", ".png").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val temp = attachmentService.store(file, "스크린샷.png", dev.yona.domain.enumeration.ResourceType.USER, s.member.id!!, s.member.loginId).attachment
        val created = call(json(post("${s.api}/${s.pr.number}/comments"), lineBody("첨부가 있는 댓글", ""","temporaryUploadFiles":[${temp.id}]""")), token(s.member)).andExpect(status().isCreated).andReturn().response.contentAsString
        val id = Regex("\"comment\":\\{\"id\":(\\d+)").find(created)!!.groupValues[1].toLong()
        assertTrue(attachmentRepository.findByContainerTypeAndContainerIdOrderById(dev.yona.domain.enumeration.ResourceType.REVIEW_COMMENT, id).size == 1)
    }

    @Test
    fun `받아들일 권한이 있는 작성자도 자기 풀 리퀘스트를 승인하거나 변경 요청할 수 없고 댓글만 남긴다`() {
        val s = setup()
        // 작성자를 원본의 구성원으로 만들어 받아들일 권한(ACCEPT)이 있게 한다. 그래도 작성자 규칙(D-35)이 막아야 한다
        projectUsers.save(ProjectUser(user = s.contributor, project = s.origin, role = roles.findById(RoleType.MEMBER.id).get())); em.flush()
        val t = token(s.contributor)
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{"verdict":"APPROVED"}"""), t).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("the author cannot approve or request changes on their own pull request"))
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{"verdict":"CHANGES_REQUESTED","body":"스스로 변경 요청"}"""), t).andExpect(status().isForbidden)
        call(json(post("${s.api}/${s.pr.number}/reviews"), """{"verdict":"COMMENTED","body":"작성자의 설명"}"""), t).andExpect(status().isCreated)
        call(get("${s.api}/${s.pr.number}"), t).andExpect(jsonPath("$.approvals.count").value(0))
    }

    // --- 리뷰어와 담당자 후보 찾기 ---

    @Test
    fun `리뷰어 후보는 프로젝트와 조직의 구성원 중에서 찾고 작성자와 이미 요청된 사람을 뺀다`() {
        val s = setup(); val t = token(s.manager)
        call(get("${s.api}/${s.pr.number}/reviewercandidates"), t).andExpect(status().isOk)
            .andExpect(jsonPath("$.users.length()").value(2)).andExpect(jsonPath("$.users[?(@.loginId=='${s.manager.loginId}')].name").value(s.manager.name))
            .andExpect(jsonPath("$.users[?(@.loginId=='${s.member.loginId}')]").isNotEmpty).andExpect(jsonPath("$.users[?(@.loginId=='${s.contributor.loginId}')]").isEmpty)
        call(get("${s.api}/${s.pr.number}/reviewercandidates").param("query", s.member.loginId!!), t).andExpect(jsonPath("$.users.length()").value(1)).andExpect(jsonPath("$.users[0].loginId").value(s.member.loginId))
        call(get("${s.api}/${s.pr.number}/reviewercandidates").param("query", "없는사람"), t).andExpect(jsonPath("$.users.length()").value(0))
        call(json(post("${s.api}/${s.pr.number}/requestedreviewers"), """{"reviewer":"${s.member.loginId}"}"""), t).andExpect(status().isOk)
        call(get("${s.api}/${s.pr.number}/reviewercandidates"), t).andExpect(jsonPath("$.users.length()").value(1))
    }

    @Test
    fun `담당자 후보도 같은 방식으로 찾는다`() {
        val s = setup(); val t = token(s.manager)
        call(get("${s.api}/${s.pr.number}/assigneecandidates"), t).andExpect(status().isOk).andExpect(jsonPath("$.users[?(@.loginId=='${s.member.loginId}')]").isNotEmpty)
        call(get("${s.api}/${s.pr.number}/assigneecandidates").param("query", s.manager.loginId!!), t).andExpect(jsonPath("$.users.length()").value(1))
    }

    @Test
    fun `후보 조회는 풀 리퀘스트를 고칠 수 있는 사람만 하고 범위가 모자라면 403이다`() {
        val s = setup()
        call(get("${s.api}/${s.pr.number}/reviewercandidates"), token(user())).andExpect(status().isForbidden)
        call(get("${s.api}/${s.pr.number}/assigneecandidates"), token(user())).andExpect(status().isForbidden)
        call(get("${s.api}/${s.pr.number}/reviewercandidates"), token(s.manager, "user")).andExpect(status().isForbidden)
        call(get("${s.api}/${s.pr.number}/reviewercandidates"), null).andExpect(status().isUnauthorized)
    }
}
