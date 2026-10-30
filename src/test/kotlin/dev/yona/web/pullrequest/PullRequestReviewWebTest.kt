package dev.yona.web.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ReviewVerdict
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
import dev.yona.domain.pullrequest.PullRequestEventRepository
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.pullrequest.PullRequestReviewRepository
import dev.yona.domain.pullrequest.PullRequestReviewService
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.ruleset
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트 리뷰 판정의 웹 계약을 검증한다(로드맵 X-12, `decisions.md` D-35 14번). 경로는 `POST .../pullRequest/:id/reviews`(`verdict`, `contents`)와
 * `POST .../pullRequest/:id/reviews/:reviewId/dismissals`(`message`)이고 v1.6의 `review`와 `unreview`는 없다.
 * 댓글(`COMMENTED`)은 읽기 권한이 있는 로그인 사용자, 승인과 변경 요청과 해제는 받아들일 권한(구성원)이 필요하다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class PullRequestReviewWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var branchProtections: dev.yona.domain.project.RulesetRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var reviews: PullRequestReviewRepository
    @Autowired private lateinit var reviewService: PullRequestReviewService
    @Autowired private lateinit var pullRequestEvents: PullRequestEventRepository
    @Autowired private lateinit var commentThreads: dev.yona.domain.comment.CommentThreadRepository

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "rw$n", name = "이름rw$n", email = "rw$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    /** 원본(프로젝트 관리자 [manager], 일반 구성원 [member])과 포크, 작성자 [contributor]이다. */
    private class Setup(val origin: Project, val fork: Project, val contributor: User, val manager: User, val member: User)

    private fun setup(usingReviewers: Boolean = false): Setup {
        val n = ++sequence
        val origin = projects.save(Project(owner = "rwo$n", name = "rwp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        if (usingReviewers) branchProtections.save(ruleset(origin))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        GitTestSupport.pushCommits(repositories, origin, TestCommit("기반", mapOf("a.txt" to "line1\nline2\nline3\n".toByteArray())))
        val manager = user().also { projectUsers.save(ProjectUser(user = it, project = origin, role = roles.findById(RoleType.MANAGER.id).get())) }
        val member = user().also { projectUsers.save(ProjectUser(user = it, project = origin, role = roles.findById(RoleType.MEMBER.id).get())) }
        val contributor = user()
        GitTestSupport.appendCommits(repositories, origin, TestCommit("원본", mapOf("o.txt" to "o\n".toByteArray())))
        val fork = forks.fork(origin, contributor.loginId!!, "rwf$n", ProjectScope.PUBLIC, contributor)
        GitTestSupport.appendCommits(repositories, fork, TestCommit("새 파일\n\n설명", mapOf("b.txt" to "b\n".toByteArray())))
        return Setup(origin, fork, contributor, manager, member)
    }

    private fun open(s: Setup): PullRequest {
        mockMvc.perform(
            post("/${s.fork.owner}/${s.fork.name}/pullRequests").session(login(s.contributor)).param("fromProjectId", s.fork.id.toString()).param("toProjectId", s.origin.id.toString())
                .param("fromBranch", "refs/heads/master").param("toBranch", "refs/heads/master").param("title", "요청 제목").param("body", "본문")
        ).andExpect(status().isSeeOther)
        return pullRequests.findAll().maxByOrNull { it.id!! }!!
    }

    private fun url(s: Setup, pr: PullRequest, tail: String = "") = "/${s.origin.owner}/${s.origin.name}/pullRequest/${pr.number}$tail"

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun submit(s: Setup, pr: PullRequest, session: MockHttpSession?, verdict: String?, contents: String? = null) =
        post(url(s, pr, "/reviews")).apply { session?.let { session(it) }; verdict?.let { param("verdict", it) }; contents?.let { param("contents", it) } }

    private fun reviewsOf(pr: PullRequest) = reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(pr.id!!)

    // --- 제출 ---

    @Test
    fun `구성원이 승인하면 리뷰와 승인 집합과 이벤트가 남고 요청 화면으로 돌아간다`() {
        val s = setup(usingReviewers = true); val pr = open(s)
        mockMvc.perform(submit(s, pr, login(s.member), "APPROVED")).andExpect(status().isSeeOther).andExpect(redirectedUrl(url(s, pr)))
        assertEquals(listOf(ReviewVerdict.APPROVED), reviewsOf(pr).map { it.verdict })
        assertEquals(listOf(s.member.id), pullRequests.findById(pr.id!!).get().reviewers.map { it.id })
        assertTrue(pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).any { it.eventType == EventType.PULL_REQUEST_REVIEW_SUBMITTED })
    }

    @Test
    fun `읽기 권한만 있는 로그인 사용자는 댓글 판정만 할 수 있다`() {
        val s = setup(); val pr = open(s); val reader = login(user())
        mockMvc.perform(submit(s, pr, reader, "COMMENTED", "한 줄 의견")).andExpect(status().isSeeOther)
        mockMvc.perform(submit(s, pr, reader, "APPROVED")).andExpect(status().isForbidden)
        mockMvc.perform(submit(s, pr, reader, "CHANGES_REQUESTED", "문제")).andExpect(status().isForbidden)
        assertEquals(listOf(ReviewVerdict.COMMENTED), reviewsOf(pr).map { it.verdict })
    }

    @Test
    fun `로그인하지 않으면 로그인 화면으로 보내고 작성자는 승인과 변경 요청을 할 수 없다`() {
        val s = setup(); val pr = open(s)
        mockMvc.perform(submit(s, pr, null, "COMMENTED", "의견")).andExpect(status().is3xxRedirection)
        val author = login(s.contributor)
        mockMvc.perform(submit(s, pr, author, "APPROVED")).andExpect(status().isForbidden)
        mockMvc.perform(submit(s, pr, author, "CHANGES_REQUESTED", "고칩니다")).andExpect(status().isForbidden)
        mockMvc.perform(submit(s, pr, author, "COMMENTED", "설명 추가")).andExpect(status().isSeeOther)
        assertEquals(listOf(ReviewVerdict.COMMENTED), reviewsOf(pr).map { it.verdict })
    }

    @Test
    fun `판정이 없거나 모르는 값이거나 본문이 필요한데 비었거나 닫힌 요청이면 400이다`() {
        val s = setup(); val pr = open(s); val member = login(s.member)
        mockMvc.perform(submit(s, pr, member, null)).andExpect(status().isBadRequest)
        mockMvc.perform(submit(s, pr, member, "LGTM")).andExpect(status().isBadRequest)
        mockMvc.perform(submit(s, pr, member, "COMMENTED", " ")).andExpect(status().isBadRequest)
        mockMvc.perform(submit(s, pr, member, "CHANGES_REQUESTED")).andExpect(status().isBadRequest)
        pr.state = State.CLOSED; pullRequests.save(pr)
        mockMvc.perform(submit(s, pr, member, "APPROVED")).andExpect(status().isBadRequest)
        assertTrue(reviewsOf(pr).isEmpty())
    }

    @Test
    fun `v1_6의 리뷰 완료와 취소 경로는 없다`() {
        val s = setup(usingReviewers = true); val pr = open(s)
        mockMvc.perform(post(url(s, pr, "/review")).session(login(s.member))).andExpect(status().is4xxClientError)
        mockMvc.perform(post(url(s, pr, "/unreview")).session(login(s.member))).andExpect(status().is4xxClientError)
    }

    // --- 해제 ---

    @Test
    fun `구성원은 사유를 적어 리뷰를 해제하고 승인 집합에서 빠진다`() {
        val s = setup(usingReviewers = true); val pr = open(s)
        reviewService.submit(pr, s.member, ReviewVerdict.APPROVED, null)
        val review = reviewsOf(pr).single()
        val path = url(s, pr, "/reviews/${review.id}/dismissals")
        mockMvc.perform(post(path).session(login(s.manager))).andExpect(status().isBadRequest)
        mockMvc.perform(post(path).session(login(user())).param("message", "사유")).andExpect(status().isForbidden)
        mockMvc.perform(post(path).session(login(s.manager)).param("message", "검토 끝")).andExpect(status().isSeeOther).andExpect(redirectedUrl(url(s, pr)))
        val saved = reviewsOf(pr).single()
        assertNotNull(saved.dismissedDate); assertEquals(s.manager.loginId, saved.dismissedByLoginId); assertEquals("검토 끝", saved.dismissedReason)
        assertTrue(pullRequests.findById(pr.id!!).get().reviewers.isEmpty())
        mockMvc.perform(post(url(s, pr, "/reviews/999999/dismissals")).session(login(s.manager)).param("message", "사유")).andExpect(status().isNotFound)
    }

    // --- 병합 조건과 우회 ---

    @Test
    fun `일반 구성원은 승인이 모자라거나 변경 요청이 남으면 병합할 수 없고 프로젝트 관리자는 우회한다`() {
        val s = setup(usingReviewers = true); val pr = open(s)
        // v1.6 accept는 병합 중 표시를 먼저 켜고 저장한 뒤 400을 돌려 표시가 남으므로(R-76) 화면을 먼저 확인한다
        val member = page(url(s, pr), login(s.member))
        assertNull(member.selectFirst("#btnAccept"), "승인이 모자란 구성원에게 병합 단추는 막혀 있다"); assertNotNull(member.selectFirst("button.ybtn-disabled"))
        val manager = page(url(s, pr), login(s.manager))
        assertNotNull(manager.selectFirst("#btnAccept")); assertNotNull(manager.selectFirst("#accept-bypass-note"), "요건 미달 안내가 보인다")
        mockMvc.perform(post(url(s, pr, "/accept")).session(login(s.member))).andExpect(status().isBadRequest)
        mockMvc.perform(post(url(s, pr, "/accept")).session(login(s.manager))).andExpect(status().isSeeOther)
        assertEquals(State.MERGED, pullRequests.findById(pr.id!!).get().state)
    }

    @Test
    fun `우회를 허용하지 않는 룰셋은 프로젝트 관리자도 우회하지 못한다 (X-34, D-46)`() {
        val s = setup(usingReviewers = true); val pr = open(s)
        branchProtections.findByProjectIdAndName(s.origin.id!!, "master")!!.also { it.bypassAdmins = false; branchProtections.save(it) }
        val manager = page(url(s, pr), login(s.manager))
        assertNull(manager.selectFirst("#btnAccept"), "관리자에게도 막힌 단추이다"); assertNotNull(manager.selectFirst("button.ybtn-disabled"))
        mockMvc.perform(post(url(s, pr, "/accept")).session(login(s.manager))).andExpect(status().isBadRequest)
        assertEquals(State.OPEN, pullRequests.findById(pr.id!!).get().state)
    }

    @Test
    fun `규칙이 다른 브랜치에만 있으면 받는 브랜치는 승인 없이도 병합할 수 있다 (X-32, D-42)`() {
        val s = setup(usingReviewers = false); val pr = open(s)
        branchProtections.save(ruleset(s.origin, "release/*", approvals = 3))
        mockMvc.perform(post(url(s, pr, "/accept")).session(login(s.member))).andExpect(status().isSeeOther)
        assertEquals(State.MERGED, pullRequests.findById(pr.id!!).get().state)
    }

    @Test
    fun `승인을 채워도 변경 요청이 남아 있으면 일반 구성원은 막히고 해제하면 풀린다`() {
        val s = setup(usingReviewers = true); val pr = open(s)
        val other = user().also { projectUsers.save(ProjectUser(user = it, project = s.origin, role = roles.findById(RoleType.MEMBER.id).get())) }
        reviewService.submit(pr, s.member, ReviewVerdict.APPROVED, null)
        reviewService.submit(pr, other, ReviewVerdict.CHANGES_REQUESTED, "문제")
        mockMvc.perform(post(url(s, pr, "/accept")).session(login(s.member))).andExpect(status().isBadRequest)
        reviewService.dismiss(pr, reviewsOf(pr).last(), s.manager, "해결됨")
        mockMvc.perform(post(url(s, pr, "/accept")).session(login(s.member))).andExpect(status().isSeeOther)
        assertEquals(State.MERGED, pullRequests.findById(pr.id!!).get().state)
    }

    @Test
    fun `대화 해결을 요구하는 규칙은 열린 스레드가 남아 있으면 병합을 막고 닫으면 푼다 (X-33, D-45)`() {
        val s = setup(); val pr = open(s)
        branchProtections.save(ruleset(s.origin, approvals = 0, noChangesRequested = false, conversationResolution = true))
        val thread = commentThreads.save(dev.yona.domain.comment.NonRangedCodeCommentThread(commitId = "abc").also {
            it.project = s.origin; it.pullRequest = pr; it.state = dev.yona.domain.enumeration.ThreadState.OPEN; it.createdDate = java.util.Date()
        })
        val member = page(url(s, pr), login(s.member))
        assertNull(member.selectFirst("#btnAccept"), "열린 대화가 있으면 병합 단추가 막힌다"); assertNotNull(member.selectFirst("button.ybtn-disabled"))
        assertNotNull(page(url(s, pr), login(s.manager)).selectFirst("#accept-bypass-note"), "프로젝트 관리자는 우회할 수 있다")
        // v1.6 accept는 병합 중 표시를 먼저 켜고 저장한 뒤 400을 돌려 표시가 남으므로(R-76) 화면을 먼저 확인했다
        mockMvc.perform(post(url(s, pr, "/accept")).session(login(s.member))).andExpect(status().isBadRequest)
        thread.state = dev.yona.domain.enumeration.ThreadState.CLOSED; commentThreads.save(thread)
        mockMvc.perform(post(url(s, pr, "/accept")).session(login(s.member))).andExpect(status().isSeeOther)
        assertEquals(State.MERGED, pullRequests.findById(pr.id!!).get().state)
    }

    @Test
    fun `대화 해결을 요구하지 않는 규칙은 열린 스레드가 있어도 막지 않는다 (X-33, D-45)`() {
        val s = setup(); val pr = open(s)
        branchProtections.save(ruleset(s.origin, approvals = 0, noChangesRequested = false))
        commentThreads.save(dev.yona.domain.comment.NonRangedCodeCommentThread(commitId = "abc").also {
            it.project = s.origin; it.pullRequest = pr; it.state = dev.yona.domain.enumeration.ThreadState.OPEN; it.createdDate = java.util.Date()
        })
        mockMvc.perform(post(url(s, pr, "/accept")).session(login(s.member))).andExpect(status().isSeeOther)
    }

    @Test
    fun `선형 이력을 요구하는 규칙은 병합 커밋 방식을 막고 스쿼시로는 병합한다 (X-33, D-45)`() {
        val s = setup(); val pr = open(s)
        branchProtections.save(ruleset(s.origin, approvals = 0, noChangesRequested = false, linearHistory = true))
        val member = page(url(s, pr), login(s.member))
        assertEquals(listOf("squash", "rebase"), member.select("#merge-method-menu li").map { it.attr("data-method") }, "병합 커밋은 선택지에 없다")
        mockMvc.perform(post(url(s, pr, "/accept")).session(login(s.member)).param("method", "merge")).andExpect(status().isBadRequest)
        assertEquals(State.OPEN, pullRequests.findById(pr.id!!).get().state)
        mockMvc.perform(post(url(s, pr, "/accept")).session(login(s.member)).param("method", "squash")).andExpect(status().isSeeOther)
        assertEquals(State.MERGED, pullRequests.findById(pr.id!!).get().state)
    }

    @Test
    fun `규칙이 없어도 승인 영역이 보이고 승인할 수 있다 (D-45)`() {
        val s = setup(); val pr = open(s)
        val member = page(url(s, pr), login(s.member))
        assertNotNull(member.selectFirst("#review-form, .review-form, [data-review-form]") ?: member.selectFirst("a[href\$=review-form]"), "승인 영역이 있다")
        mockMvc.perform(submit(s, pr, login(s.member), "APPROVED")).andExpect(status().isSeeOther)
        assertEquals(listOf(ReviewVerdict.APPROVED), reviewsOf(pr).map { it.verdict })
    }

    // --- 화면 ---

    @Test
    fun `개요 오른쪽 열에 리뷰어별 최신 판정이 나오고 해제된 것은 구분한다`() {
        val s = setup(); val pr = open(s)
        reviewService.submit(pr, s.member, ReviewVerdict.APPROVED, null)
        reviewService.submit(pr, s.manager, ReviewVerdict.CHANGES_REQUESTED, "문제")
        reviewService.dismiss(pr, reviewsOf(pr).last(), s.member, "해결")
        val doc = page(url(s, pr), login(s.member))
        val rows = doc.select(".span-right-pane #review-area .reviewer-row").associateBy { it.attr("data-login-id") }
        assertEquals("APPROVED", rows.getValue(s.member.loginId!!).attr("data-verdict"))
        assertEquals("COMMENTED", rows.getValue(s.manager.loginId!!).attr("data-verdict"), "해제된 변경 요청은 댓글로 본다")
        assertNotNull(rows.getValue(s.manager.loginId!!).selectFirst(".dismissed"))
    }

    @Test
    fun `타임라인의 승인은 v1_6 리뷰 완료 문구를 쓰고 변경 요청은 새 문구와 본문을 보인다`() {
        val s = setup(); val pr = open(s)
        reviewService.submit(pr, s.member, ReviewVerdict.APPROVED, null)
        reviewService.submit(pr, s.manager, ReviewVerdict.CHANGES_REQUESTED, "문서를 보강해 주세요")
        val events = page(url(s, pr), login(s.member)).select("#comments li.event").map { it.text() }
        assertTrue(events.any { it.contains("님이 리뷰를 완료했습니다") || it.contains("completed a pull request review") }, events.toString())
        assertTrue(events.any { it.contains("문서를 보강해 주세요") }, events.toString())
    }

    @Test
    fun `판정 폼은 로그인한 사용자에게만 보이고 선택지는 권한과 상태를 따른다`() {
        val s = setup(); val pr = open(s)
        fun options(session: MockHttpSession?) = page(url(s, pr), session).select("#review-form input[name=verdict]").map { it.attr("value") }
        assertEquals(listOf("COMMENTED", "APPROVED", "CHANGES_REQUESTED"), options(login(s.member)))
        assertEquals(listOf("COMMENTED"), options(login(s.contributor)), "작성자는 댓글만")
        assertEquals(listOf("COMMENTED"), options(login(user())), "읽기 권한만 있으면 댓글만")
        assertTrue(page(url(s, pr)).select("#review-form").isEmpty(), "로그인하지 않으면 폼이 없다")
        pr.state = State.CLOSED; pullRequests.save(pr)
        assertEquals(listOf("COMMENTED"), options(login(s.member)), "닫힌 요청은 댓글만")
    }

    @Test
    fun `해제할 수 있는 사람에게만 해제 폼이 보인다`() {
        val s = setup(); val pr = open(s)
        reviewService.submit(pr, s.member, ReviewVerdict.APPROVED, null)
        val review = reviewsOf(pr).single()
        assertNotNull(page(url(s, pr), login(s.manager)).selectFirst("form[action=${url(s, pr, "/reviews/${review.id}/dismissals")}]"))
        assertNull(page(url(s, pr), login(user())).selectFirst("form[action$=/dismissals]"))
    }
}
