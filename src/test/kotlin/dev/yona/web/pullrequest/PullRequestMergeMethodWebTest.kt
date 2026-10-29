package dev.yona.web.pullrequest

import dev.yona.support.selectFirst
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
import dev.yona.support.protectionRule
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
import org.jsoup.Jsoup
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트 병합 방식 선택(스쿼시, 리베이스)과 방식별 가능 여부 표시를 웹 요청으로 검증한다(로드맵 X-28, `decisions.md` D-41).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-merge-method-web"])
class PullRequestMergeMethodWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var branchProtections: dev.yona.domain.project.BranchProtectionRuleRepository
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

    @jakarta.persistence.PersistenceContext private lateinit var entityManager: jakarta.persistence.EntityManager

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
        val origin = projects.save(Project(owner = "pao$n", name = "pap$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        if (usingReviewers) branchProtections.save(protectionRule(origin))
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

    private fun accept(s: Setup, pr: PullRequest, vararg params: Pair<String, String>) = mockMvc.perform(
        post(url(s, pr, "/accept")).session(s.managerSession).also { r -> params.forEach { (k, v) -> r.param(k, v) } }
    )

    private fun commitOf(project: Project, id: String) = repositories.openRepository(project).use { r -> org.eclipse.jgit.revwalk.RevWalk(r).use { it.parseCommit(r.resolve(id)) } }

    private fun page(s: Setup, pr: PullRequest) = Jsoup.parse(mockMvc.perform(get(url(s, pr, "")).session(s.managerSession).header("Accept-Language", "ko-KR")).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `스쿼시로 받아들이면 부모가 하나인 커밋이 생기고 이벤트에 방식이 남는다`() {
        val s = setup(); addChange(s, "첫째", "b.txt", "b\n"); addChange(s, "둘째", "c.txt", "c\n")
        val pr = open(s, title = "제목")
        val before = masterHead(s.origin)!!.name
        accept(s, pr, "method" to "squash").andExpect(status().isSeeOther)
        val merged = reload(pr)
        assertEquals(State.MERGED, merged.state)
        val head = commitOf(s.origin, masterHead(s.origin)!!.name)
        assertEquals(1, head.parentCount); assertEquals(before, head.getParent(0).name); assertEquals(head.name, merged.mergedCommitIdTo)
        assertTrue(head.fullMessage.startsWith("제목 (#${pr.number})"), head.fullMessage)
        assertEquals(s.contributor.name, head.authorIdent.name); assertEquals(s.manager.name, head.committerIdent.name)
        val event = pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).single { it.eventType == EventType.PULL_REQUEST_STATE_CHANGED && it.newValue == "merged" }
        assertEquals("squash", event.oldValue)
        assertTrue(page(s, pr).select("#comments").text().contains("스쿼시로 병합했습니다"), page(s, pr).select("#comments").text())
    }

    @Test
    fun `리베이스로 받아들이면 병합 커밋 없이 원래 커밋이 받는 브랜치 위에 놓인다`() {
        val s = setup(); addChange(s, "첫째", "b.txt", "b\n"); addChange(s, "둘째", "c.txt", "c\n")
        GitTestSupport.appendCommits(repositories, s.origin, TestCommit("원본 앞서감", mapOf("o.txt" to "o\n".toByteArray())))
        val pr = open(s)
        accept(s, pr, "method" to "rebase").andExpect(status().isSeeOther)
        assertEquals(State.MERGED, reload(pr).state)
        val tip = commitOf(s.origin, masterHead(s.origin)!!.name)
        assertEquals("둘째", tip.shortMessage); assertEquals("첫째", commitOf(s.origin, tip.getParent(0).name).shortMessage)
        assertEquals(1, tip.parentCount); assertEquals("tester@example.com", tip.authorIdent.emailAddress, "원래 작성자를 지킨다")
        assertEquals("rebase", pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).single { it.eventType == EventType.PULL_REQUEST_STATE_CHANGED && it.newValue == "merged" }.oldValue)
        assertTrue(page(s, pr).select("#comments").text().contains("리베이스로 병합했습니다"))
    }

    @Test
    fun `병합 커밋 방식은 이벤트를 v1_6과 같게 남기고 방식을 보내지 않으면 프로젝트의 기본 방식을 쓴다`() {
        val s = setup(); addChange(s)
        val pr = open(s)
        accept(s, pr).andExpect(status().isSeeOther)
        assertEquals(2, commitOf(s.origin, masterHead(s.origin)!!.name).parentCount)
        assertNull(pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).single { it.eventType == EventType.PULL_REQUEST_STATE_CHANGED && it.newValue == "merged" }.oldValue)

        val s2 = setup(); s2.origin.defaultMergeMethod = "squash"; projects.save(s2.origin); addChange(s2)
        val pr2 = open(s2)
        accept(s2, pr2).andExpect(status().isSeeOther)
        assertEquals(1, commitOf(s2.origin, masterHead(s2.origin)!!.name).parentCount, "기본 방식이 스쿼시이다")
    }

    @Test
    fun `허용하지 않았거나 모르는 방식이면 400이고 아무것도 바뀌지 않는다`() {
        val s = setup(); s.origin.allowSquashMerge = false; projects.save(s.origin); addChange(s)
        val pr = open(s); val before = masterHead(s.origin)!!.name
        accept(s, pr, "method" to "squash").andExpect(status().isBadRequest)
        accept(s, pr, "method" to "fast-forward").andExpect(status().isBadRequest)
        assertEquals(State.OPEN, reload(pr).state); assertEquals(before, masterHead(s.origin)!!.name); assertEquals(false, reload(pr).isMerging)
    }

    @Test
    fun `스쿼시에 제목과 본문을 직접 줄 수 있다`() {
        val s = setup(); addChange(s)
        val pr = open(s)
        accept(s, pr, "method" to "squash", "commitTitle" to "직접 제목", "commitMessage" to "직접 본문").andExpect(status().isSeeOther)
        val message = commitOf(s.origin, masterHead(s.origin)!!.name).fullMessage
        assertTrue(message.startsWith("직접 제목\n\n직접 본문"), message)
    }

    @Test
    fun `리베이스할 수 없는 요청은 병합 검사가 이유를 저장하고 리베이스 요청은 아무것도 바꾸지 않는다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.origin, TestCommit("원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n".toByteArray())))
        addChange(s, "충돌 없는", "b.txt", "b\n"); addChange(s, "충돌하는", "a.txt", "line1\nFORK\nline3\n")
        val pr = open(s)
        // 두 커밋을 합친 결과도 같은 줄을 고쳐 병합 전체도 충돌한다. 병합 자체는 막히고 리베이스 이유는 conflict이다
        assertEquals("conflict", reload(pr).rebaseBlocked)
        val before = masterHead(s.origin)!!.name
        accept(s, pr, "method" to "rebase").andExpect(status().isSeeOther)
        assertEquals(State.OPEN, reload(pr).state); assertEquals(before, masterHead(s.origin)!!.name)
    }

    @Test
    fun `충돌이 없는 요청은 리베이스 불가 이유가 비어 있다`() {
        val s = setup(); addChange(s)
        assertNull(reload(open(s)).rebaseBlocked)
    }

    @Test
    fun `보기 화면은 허용한 방식을 보이고 가능하지 않은 방식은 이유와 함께 막는다`() {
        val s = setup(); addChange(s)
        val pr = open(s)
        reload(pr).also { it.rebaseBlocked = "merge-commits"; pullRequests.save(it) }
        val doc = page(s, pr)
        assertEquals(listOf("merge", "squash", "rebase"), doc.select("#merge-method-menu li").map { it.attr("data-method") })
        assertTrue(doc.select("#merge-method-menu").hasClass("dropdown-menu") && doc.select("#btnMergeOptions").hasClass("dropdown-toggle"), "부트스트랩 드롭다운을 쓴다")
        assertEquals("요청 (#${pr.number})", doc.select("#merge-method-menu li[data-method=squash]").single().attr("data-title"))
        assertTrue(doc.select("#merge-method-menu li[data-method=squash]").single().attr("data-message").contains("* 새 파일"))
        assertEquals("", doc.select("#merge-method-menu li[data-method=rebase]").single().attr("data-title"))
        assertTrue(doc.selectFirst("#merge-options input[name=commitTitle]")!!.attr("value").startsWith("Merge branch"), "기본 방식의 제목을 미리 채운다")
        assertTrue(doc.selectFirst("#merge-options textarea[name=commitMessage]")!!.text().contains("from pull-request ${pr.number}"))
        val rebase = doc.select("#merge-method-menu li[data-method=rebase]").single()
        assertTrue(rebase.hasClass("disabled")); assertTrue(rebase.text().contains("병합 커밋"), rebase.text())
        assertEquals("merge", doc.select("#merge-method-menu li:has(.merge-method-check)").single().attr("data-method"))
        assertEquals("merge", doc.selectFirst("#merge-options input[name=method]")!!.attr("value"))
        assertEquals(url(s, pr, "/accept"), doc.selectFirst("#btnAccept")!!.attr("data-url"))
        assertTrue(doc.select("#merge-options input[name=commitTitle]").isNotEmpty())
        assertEquals(url(s, pr, "/accept"), doc.selectFirst("form#merge-options")!!.attr("action"), "선택 폼은 병합 요청 주소로 보낸다")
    }

    @Test
    fun `병합 방식을 병합 커밋만 허용한 프로젝트의 보기 화면은 선택 항목이 하나이다`() {
        val s = setup(); s.origin.allowSquashMerge = false; s.origin.allowRebaseMerge = false; projects.save(s.origin); addChange(s)
        val doc = page(s, open(s))
        assertTrue(doc.select("#merge-method-menu").isEmpty(), "방식이 하나면 선택 목록과 화살표가 없다")
        assertTrue(doc.select("#btnMergeOptions").isEmpty())
        assertEquals("merge", doc.selectFirst("#merge-options input[name=method]")!!.attr("value"))
    }
}
