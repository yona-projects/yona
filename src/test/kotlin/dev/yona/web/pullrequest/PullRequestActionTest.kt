package dev.yona.web.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEventRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.notification.NotificationEventRepository
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
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트 받아들이기, 닫기, 다시 열기, 수정, 보내는 브랜치 삭제와 복원을 검증한다.
 *
 * 원본: `app/controllers/PullRequestApp.java`(`accept`, `close`, `open`, `editPullRequestForm`, `editPullRequest`, `deleteFromBranch`, `restoreFromBranch`),
 * `app/models/PullRequest.java`(`merge`, `close`, `reopen`, `updateWith`), `app/actors/RelatedPullRequestMergingActor.java`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class PullRequestActionTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var pushedBranches: dev.yona.domain.project.PushedBranchRepository
    @Autowired private lateinit var pullRequestEvents: PullRequestEventRepository
    @Autowired private lateinit var notificationEvents: NotificationEventRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var issueEvents: IssueEventRepository

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "pa$n", name = "이름pa$n", email = "pa$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private class Setup(val origin: Project, val fork: Project, val contributor: User, val manager: User, val managerSession: MockHttpSession)

    private fun setup(usingReviewers: Boolean = false): Setup {
        val n = ++sequence
        val origin = projects.save(Project(owner = "pao$n", name = "pap$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC, isUsingReviewerCount = usingReviewers, defaultReviewerCount = 1))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        GitTestSupport.pushCommits(repositories, origin, TestCommit("기반", mapOf("a.txt" to "line1\nline2\nline3\n".toByteArray())))
        val manager = user()
        projectUsers.save(ProjectUser(user = manager, project = origin, role = roles.findById(RoleType.MANAGER.id).get()))
        val contributor = user()
        return Setup(origin, forks.fork(origin, contributor.loginId!!, "paf$n", ProjectScope.PUBLIC, contributor), contributor, manager, login(manager))
    }

    private fun open(s: Setup, fromBranch: String = "refs/heads/master", title: String = "요청", body: String = "본문"): PullRequest {
        mockMvc.perform(
            post("/${s.fork.owner}/${s.fork.name}/pullRequests").session(login(s.contributor)).param("fromProjectId", s.fork.id.toString()).param("toProjectId", s.origin.id.toString())
                .param("fromBranch", fromBranch).param("toBranch", "refs/heads/master").param("title", title).param("body", body)
        ).andExpect(status().isSeeOther)
        return pullRequests.findAll().maxByOrNull { it.id!! }!!
    }

    private fun url(s: Setup, pr: PullRequest, tail: String) = "/${s.origin.owner}/${s.origin.name}/pullRequest/${pr.number}$tail"

    private fun reload(pr: PullRequest) = pullRequests.findById(pr.id!!).get()

    private fun masterHead(project: Project) = repositories.openRepository(project).use { it.resolve("refs/heads/master") }

    private fun addChange(s: Setup, message: String = "새 파일", file: String = "b.txt", content: String = "b\n", branch: String = "master") =
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit(message, mapOf(file to content.toByteArray())), branch = branch)

    @Test
    fun `받아들이면 병합 커밋으로 받는 브랜치가 갱신되고 병합됨으로 바뀌며 이벤트와 알림이 남는다`() {
        val s = setup(); addChange(s)
        val mentioned = user()
        val pr = open(s, body = "@${mentioned.loginId}")
        val before = masterHead(s.origin)
        mockMvc.perform(post(url(s, pr, "/accept")).session(s.managerSession)).andExpect(status().isSeeOther).andExpect(redirectedUrl(url(s, pr, "")))
        val merged = reload(pr)
        assertEquals(State.MERGED, merged.state); assertEquals(s.manager.id, merged.receiver?.id); assertNotNull(merged.received); assertEquals(false, merged.isMerging)
        assertNotEquals(before, masterHead(s.origin)); assertEquals(masterHead(s.origin)!!.name, merged.mergedCommitIdTo); assertEquals(before!!.name, merged.mergedCommitIdFrom)
        assertTrue(pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).any { it.eventType == EventType.PULL_REQUEST_STATE_CHANGED && it.newValue == "merged" })
        val note = notificationEvents.findAll().single { it.eventType == EventType.PULL_REQUEST_STATE_CHANGED }
        assertEquals("open", note.oldValue); assertEquals("merged", note.newValue); assertTrue(note.receivers.any { it.id == mentioned.id })
    }

    @Test
    fun `충돌하면 받아들여도 병합하지 않고 병합 중 표시도 남지 않는다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.origin, TestCommit("원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n".toByteArray())))
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n".toByteArray())))
        val pr = open(s); val before = masterHead(s.origin)
        mockMvc.perform(post(url(s, pr, "/accept")).session(s.managerSession)).andExpect(status().isSeeOther)
        assertEquals(State.OPEN, reload(pr).state); assertEquals(false, reload(pr).isMerging); assertEquals(before, masterHead(s.origin))
    }

    @Test
    fun `리뷰어가 모자라면 400이고 병합 중 표시가 남은 채로 리뷰하면 받아들일 수 있다`() {
        val s = setup(usingReviewers = true); addChange(s)
        val pr = open(s)
        mockMvc.perform(post(url(s, pr, "/accept")).session(s.managerSession)).andExpect(status().isBadRequest)
        // v1.6 accept는 병합 중 표시를 먼저 켜고 저장한 뒤 400을 돌려 표시가 남는다 (R-76)
        assertEquals(State.OPEN, reload(pr).state); assertEquals(true, reload(pr).isMerging)
        mockMvc.perform(post(url(s, pr, "/review")).session(s.managerSession)).andExpect(status().isSeeOther)
        mockMvc.perform(post(url(s, pr, "/accept")).session(s.managerSession)).andExpect(status().isSeeOther)
        assertEquals(State.MERGED, reload(pr).state)
    }

    @Test
    fun `받아들이면 같은 브랜치와 관련된 다른 요청의 충돌을 다시 검사한다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("다른 수정", mapOf("a.txt" to "line1\nY\nline3\n".toByteArray())), branch = "other")
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("첫 수정", mapOf("a.txt" to "line1\nX\nline3\n".toByteArray())))
        val first = open(s, title = "첫째"); val second = open(s, fromBranch = "refs/heads/other", title = "둘째")
        assertEquals(false, reload(second).isConflict)
        mockMvc.perform(post(url(s, first, "/accept")).session(s.managerSession)).andExpect(status().isSeeOther)
        assertEquals(true, reload(second).isConflict); assertEquals(false, reload(second).isMerging)
        assertTrue(pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(second.id!!).any { it.eventType == EventType.PULL_REQUEST_MERGED && it.newValue == "conflict" })
    }

    @Test
    fun `로그인하지 않은 요청은 권한 검사보다 먼저 로그인 폼으로 303 이동한다 (v1_6 메서드 수준 AnonymousCheck가 IsAllowed 앞이다)`() {
        val s = setup(); addChange(s); val pr = open(s)
        val loginForm = { path: String -> "/users/loginform?redirectUrl=${url(s, pr, path)}" }
        listOf("/accept", "/close", "/open", "/edit", "/restorefrombranch").forEach { tail ->
            mockMvc.perform(post(url(s, pr, tail))).andExpect(status().isSeeOther).andExpect(redirectedUrl(loginForm(tail)))
        }
        mockMvc.perform(get(url(s, pr, "/editform"))).andExpect(status().isSeeOther).andExpect(redirectedUrl(loginForm("/editform")))
        mockMvc.perform(delete(url(s, pr, "/deletefrombranch"))).andExpect(status().isSeeOther).andExpect(redirectedUrl(loginForm("/deletefrombranch")))
    }

    @Test
    fun `받아들일 권한이 없으면 403이다`() {
        val s = setup(); addChange(s); val pr = open(s)
        mockMvc.perform(post(url(s, pr, "/accept")).session(login(user()))).andExpect(status().isForbidden)
        assertEquals(State.OPEN, reload(pr).state)
    }

    @Test
    fun `닫으면 닫힘으로 바뀌고 다시 열면 열림으로 돌아오며 병합된 요청은 다시 열 수 없다`() {
        val s = setup(); addChange(s); val pr = open(s)
        mockMvc.perform(post(url(s, pr, "/close")).session(s.managerSession)).andExpect(status().isSeeOther).andExpect(redirectedUrl(url(s, pr, "")))
        val closed = reload(pr)
        assertEquals(State.CLOSED, closed.state); assertEquals(s.manager.id, closed.receiver?.id); assertNotNull(closed.received)
        assertTrue(pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).any { it.eventType == EventType.PULL_REQUEST_STATE_CHANGED && it.oldValue == "open" && it.newValue == "closed" })
        mockMvc.perform(post(url(s, pr, "/open")).session(s.managerSession)).andExpect(status().isSeeOther)
        assertEquals(State.OPEN, reload(pr).state); assertEquals(false, reload(pr).isMerging)
        mockMvc.perform(post(url(s, pr, "/open")).session(s.managerSession)).andExpect(status().isBadRequest)
        pr.state = State.MERGED; pullRequests.save(pr)
        mockMvc.perform(post(url(s, pr, "/open")).session(s.managerSession)).andExpect(status().isBadRequest)
    }

    @Test
    fun `다시 열면 보내는 프로젝트의 같은 이름 최근 푸시 브랜치 기록을 지운다`() {
        val s = setup(); addChange(s); val pr = open(s)
        mockMvc.perform(post(url(s, pr, "/close")).session(s.managerSession)).andExpect(status().isSeeOther)
        // 닫은 뒤 같은 브랜치에 다시 푸시하면 최근 푸시 브랜치 기록이 생긴다
        pushedBranches.save(dev.yona.domain.project.PushedBranch(pushedDate = java.util.Date(), name = pr.fromBranch, project = pr.fromProject))
        mockMvc.perform(post(url(s, pr, "/open")).session(s.managerSession)).andExpect(status().isSeeOther)
        // v1.6 PullRequest.reopen은 PushedBranch.removeByPullRequestFrom을 부른다
        assertNull(pushedBranches.findFirstByProjectIdAndName(pr.fromProject!!.id!!, pr.fromBranch!!))
    }

    @Test
    fun `수정 폼은 제목과 본문, 고정된 브랜치를 보여 준다`() {
        val s = setup(); addChange(s); val pr = open(s, title = "원래 제목", body = "원래 본문")
        val doc = org.jsoup.Jsoup.parse(mockMvc.perform(get(url(s, pr, "/editform")).session(s.managerSession)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("원래 제목", doc.selectFirst("input#title")!!.attr("value"))
        assertEquals("원래 본문", doc.selectFirst("textarea[name=body]")!!.text())
        assertEquals("refs/heads/master", doc.selectFirst("input[type=hidden][name=fromBranch]")!!.attr("value"))
        assertEquals(url(s, pr, "/edit"), doc.selectFirst("form[enctype]")!!.attr("action"))
    }

    @Test
    fun `수정하면 제목과 본문이 바뀌고 이슈 언급 이벤트가 새 내용으로 다시 남는다`() {
        val s = setup(); addChange(s)
        val first = issues.save(Issue(project = s.origin, number = 1, title = "첫 이슈")); val second = issues.save(Issue(project = s.origin, number = 2, title = "둘째 이슈"))
        val pr = open(s, title = "고침 #1")
        assertEquals(1, issueEvents.findByIssueIdOrderByCreatedAscIdAsc(first.id!!).size)
        mockMvc.perform(
            post(url(s, pr, "/edit")).session(s.managerSession).param("title", "새 제목 #2").param("body", "새 본문")
                .param("fromBranch", "refs/heads/master").param("toBranch", "refs/heads/master")
        ).andExpect(status().isSeeOther).andExpect(redirectedUrl(url(s, pr, "")))
        assertEquals("새 제목 #2", reload(pr).title); assertEquals("새 본문", reload(pr).body)
        assertEquals(0, issueEvents.findByIssueIdOrderByCreatedAscIdAsc(first.id!!).size); assertEquals(1, issueEvents.findByIssueIdOrderByCreatedAscIdAsc(second.id!!).size)
    }

    @Test
    fun `브랜치를 바꿔 다른 열린 요청과 겹치면 보내는 프로젝트 주소의 수정 폼으로 되돌려 보낸다`() {
        val s = setup(); addChange(s)
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("다른", mapOf("c.txt" to "c\n".toByteArray())), branch = "other")
        val master = open(s); open(s, fromBranch = "refs/heads/other")
        mockMvc.perform(
            post(url(s, master, "/edit")).session(s.managerSession).param("title", "t").param("body", "b").param("fromBranch", "refs/heads/other").param("toBranch", "refs/heads/master")
        ).andExpect(status().isSeeOther).andExpect(redirectedUrl("/${s.fork.owner}/${s.fork.name}/pullRequest/${master.number}/editform"))
        assertEquals("refs/heads/master", reload(master).fromBranch)
    }

    @Test
    fun `병합된 요청의 보낸 브랜치를 지우고 되살릴 수 있다`() {
        val s = setup(); addChange(s, branch = "feature"); val pr = open(s, fromBranch = "refs/heads/feature")
        pr.state = State.MERGED; pr.receiver = s.manager; pullRequests.save(pr)
        val forkBranches = { repositories.openRepository(s.fork).use { r -> r.refDatabase.getRefsByPrefix("refs/heads/").map { it.name } } }
        assertTrue("refs/heads/feature" in forkBranches())
        mockMvc.perform(delete(url(s, pr, "/deletefrombranch")).session(login(s.manager))).andExpect(status().isSeeOther)
        assertNull(reload(pr).lastCommitId.takeIf { "refs/heads/feature" in forkBranches() })
        assertNotNull(reload(pr).lastCommitId); assertTrue("refs/heads/feature" !in forkBranches())
        mockMvc.perform(post(url(s, pr, "/restorefrombranch")).session(login(s.manager))).andExpect(status().isSeeOther)
        assertTrue("refs/heads/feature" in forkBranches())
    }
}
