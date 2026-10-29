package dev.yona.web.pullrequest

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
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
 * 풀 리퀘스트 보기, 상태 조각, 변경 내용을 검증한다. 리뷰 판정은 `PullRequestReviewWebTest`가 검증한다(v1.6의 리뷰 완료와 취소는 X-12로 대체되었다).
 *
 * 원본: `app/controllers/PullRequestApp.java`(`pullRequest`, `pullRequestState`, `pullRequestChanges`, `specificChange`), `ReviewApp.java`,
 * `app/views/git/view.scala.html`, `viewChanges.scala.html`, `partial_info.scala.html`, `partial_state.scala.html`, `partial_pull_request_event.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class PullRequestViewTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var threads: dev.yona.domain.comment.CommentThreadRepository
    @Autowired private lateinit var reviewComments: dev.yona.domain.comment.ReviewCommentRepository
    @Autowired private lateinit var entityManager: jakarta.persistence.EntityManager
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var pullRequestEvents: PullRequestEventRepository
    @Autowired private lateinit var notificationEvents: NotificationEventRepository

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "pv$n", name = "이름pv$n", email = "pv$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private class Setup(val origin: Project, val fork: Project, val contributor: User, val manager: User)

    private fun setup(usingReviewers: Boolean = false): Setup {
        val n = ++sequence
        val origin = projects.save(Project(owner = "pvo$n", name = "pvp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC, isUsingReviewerCount = usingReviewers, defaultReviewerCount = 1))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        GitTestSupport.pushCommits(repositories, origin, TestCommit("기반", mapOf("a.txt" to "line1\nline2\nline3\n".toByteArray())))
        val manager = user()
        projectUsers.save(ProjectUser(user = manager, project = origin, role = roles.findById(RoleType.MANAGER.id).get()))
        val contributor = user()
        return Setup(origin, forks.fork(origin, contributor.loginId!!, "pvf$n", ProjectScope.PUBLIC, contributor), contributor, manager)
    }

    /** 포크에 커밋을 더하고 풀 리퀘스트를 만든다. */
    private fun open(s: Setup, fromBranch: String = "refs/heads/master", title: String = "요청 제목", body: String = "**본문**"): PullRequest {
        mockMvc.perform(
            post("/${s.fork.owner}/${s.fork.name}/pullRequests").session(login(s.contributor)).param("fromProjectId", s.fork.id.toString()).param("toProjectId", s.origin.id.toString())
                .param("fromBranch", fromBranch).param("toBranch", "refs/heads/master").param("title", title).param("body", body)
        ).andExpect(status().isSeeOther)
        return pullRequests.findAll().maxByOrNull { it.id!! }!!
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun url(s: Setup, pr: PullRequest, tail: String = "") = "/${s.origin.owner}/${s.origin.name}/pullRequest/${pr.number}$tail"

    private fun withCommit(s: Setup, branch: String = "master"): Setup {
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("새 파일\n\n설명", mapOf("b.txt" to "b\n".toByteArray())), branch = branch)
        return s
    }

    @Test
    fun `보기는 제목, 상태 뱃지, 본문, 보낸 사람, 브랜치 정보, 안전 안내를 보여 준다`() {
        val s = withCommit(setup()); val pr = open(s)
        val doc = page(url(s, pr), login(s.manager))
        assertEquals("#1 요청 제목", doc.selectFirst(".board-header .title")!!.text())
        assertEquals("Open", doc.selectFirst(".board-header .badge")!!.text()); assertTrue(doc.selectFirst(".board-header .badge")!!.hasClass("badge-issue-open"))
        assertEquals("본문", doc.selectFirst(".board-body .content strong")!!.text())
        assertEquals("/${s.contributor.loginId}", doc.selectFirst(".board-body .author-info a.usf-group")!!.attr("href"))
        val branches = doc.selectFirst(".pullRequest-branchInfo")!!
        assertEquals("${s.fork.owner}/${s.fork.name}:master", branches.selectFirst("code.from")!!.text().replace(" ", ""))
        assertEquals("/${s.fork.owner}/${s.fork.name}/code/refs%252Fheads%252Fmaster", branches.selectFirst("code.from a.branchName")!!.attr("href"))
        assertNotNull(doc.selectFirst("#state .alert-success"))
    }

    @Test
    fun `머리 영역의 탭과 받아들이기 단추는 권한에 따라 나온다`() {
        val s = withCommit(setup()); val pr = open(s)
        val manager = page(url(s, pr), login(s.manager))
        assertEquals(listOf("Overview", "Changes"), manager.select(".project-page-wrap > ul.nav-tabs li").map { it.text() })
        assertEquals(url(s, pr, "/accept"), manager.selectFirst("#btnAccept")!!.attr("href"))
        assertNull(page(url(s, pr), login(s.contributor)).selectFirst("#btnAccept"))
        assertNull(page(url(s, pr)).selectFirst("#btnAccept"))
    }

    @Test
    fun `충돌한 요청은 받아들이기가 막히고 보낸 사람에게 해결 방법을 알려 준다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.origin, TestCommit("원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n".toByteArray())))
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n".toByteArray())))
        val pr = open(s)
        val manager = page(url(s, pr), login(s.manager))
        assertEquals("Conflict", manager.selectFirst(".board-header .badge")!!.text()); assertTrue(manager.selectFirst(".board-header .badge")!!.hasClass("badge-issue-conflict"))
        assertEquals("There are conflicts.", manager.selectFirst("button.ybtn-disabled")!!.attr("title"))
        assertNull(manager.selectFirst(".howto-resolve-conflict"))
        val help = page(url(s, pr), login(s.contributor)).selectFirst(".howto-resolve-conflict")!!
        assertEquals("git checkout master", help.select("code")[0].text())
        assertTrue(help.select("code")[1].text().startsWith("git remote add upstream http"))
        assertEquals("git push -f origin master", help.select("code").last()!!.text())
    }

    @Test
    fun `타임라인은 새 커밋 이벤트를 커밋 목록과 함께 보여 준다`() {
        val s = withCommit(setup()); val pr = open(s)
        val event = page(url(s, pr), login(s.manager)).select("#comments li.event").single()
        assertEquals("Committed", event.selectFirst(".state")!!.text())
        val commit = event.selectFirst("ul.commit-list li")!!
        assertEquals(7, commit.selectFirst("a.commit-id")!!.text().length)
        assertEquals("새 파일", commit.selectFirst("a.commitMsg.short")!!.text())
        assertNotNull(commit.selectFirst("pre.commitMsg.desc.hidden"))
        assertTrue(commit.selectFirst("a.commit-id")!!.attr("href").startsWith(url(s, pr, "/changes/")))
        assertNull(event.selectFirst("a.ybtn-mini"))
    }

    @Test
    fun `병합된 요청은 받은 사람을 보여 주고 보낸 사람에게 보낸 브랜치 삭제를 권한다`() {
        val s = setup(); withCommit(s, "feature"); val pr = open(s, fromBranch = "refs/heads/feature")
        pr.state = State.MERGED; pr.receiver = s.manager; pullRequests.save(pr)
        val doc = page(url(s, pr), login(s.contributor))
        val alert = doc.selectFirst("#state .alert-info")!!
        assertEquals("/${s.manager.loginId}", alert.selectFirst("a.usf-group")!!.attr("href"))
        assertEquals(url(s, pr, "/deletefrombranch"), alert.selectFirst("button[data-request-method=delete]")!!.attr("data-request-uri"))
        assertNull(alert.selectFirst("a[data-request-method=post]"))
        assertNull(page(url(s, pr), login(s.manager)).selectFirst("#state button[data-request-method=delete]"))
    }

    @Test
    fun `하단 단추는 권한에 따라 수정, 닫기, 다시 열기가 나오고 구독 단추도 나온다`() {
        val s = withCommit(setup()); val pr = open(s)
        val manager = page(url(s, pr), login(s.manager))
        assertEquals(url(s, pr, "/editform"), manager.selectFirst(".board-footer a.ybtn[href$=editform]")!!.attr("href"))
        assertEquals(url(s, pr, "/close"), manager.selectFirst(".board-footer a[data-request-method=post]")!!.attr("href"))
        assertNotNull(manager.selectFirst("#watch-button"))
        pr.state = State.CLOSED; pullRequests.save(pr)
        assertEquals(url(s, pr, "/open"), page(url(s, pr), login(s.manager)).selectFirst(".board-footer a[data-request-method=post]")!!.attr("href"))
        assertNull(page(url(s, pr)).selectFirst(".board-footer a.ybtn"))
    }

    @Test
    fun `상태 조각은 Ajax이면 JSON, 아니면 HTML 조각이다`() {
        val s = withCommit(setup()); val pr = open(s)
        val json = mockMvc.perform(get(url(s, pr, "/state")).header("X-Requested-With", "XMLHttpRequest").session(login(s.manager))).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(json.startsWith("""{"id":1,"isOpen":true,"isClosed":false,"isMerged":false,"isMerging":false,"isConflict":false,"canDeleteBranch":false,"canRestoreBranch":false,"html":"""), json)
        assertTrue(json.contains("alert-success"))
        assertNotNull(page(url(s, pr, "/state"), login(s.manager)).selectFirst(".alert-success"))
    }

    @Test
    fun `변경 화면은 차이와 커밋 선택 상자를 보여 주고 커밋 하나의 변경도 볼 수 있다`() {
        val s = withCommit(setup()); val pr = open(s)
        val doc = page(url(s, pr, "/changes"), login(s.manager))
        assertTrue(doc.selectFirst(".diff-body")!!.text().contains("b.txt"))
        assertEquals("All commit changes", doc.selectFirst("#commits .d-label")!!.text().trim())
        val choice = doc.select("#commits ul.dropdown-menu li[data-value]").last()!!
        assertEquals(7, choice.selectFirst(".commit-hash")!!.text().length)
        assertEquals("새 파일", choice.selectFirst("span")!!.text())
        val one = page(choice.selectFirst("a")!!.attr("href"), login(s.manager))
        assertEquals("새 파일", one.selectFirst("#commits .d-label span")!!.text())
        assertTrue(one.selectFirst(".diff-body")!!.text().contains("b.txt"))
        // v1.6 viewChanges: 커밋 하나를 고르면 작성자, 시각, 커밋 메시지 블록이 나오고 전체 보기에는 없다
        assertNotNull(one.selectFirst("p.commitInfo .ago"), one.select(".codediff-wrap").html())
        assertTrue(one.selectFirst("pre.commitMsg.mt5")!!.text().startsWith("새 파일"), one.selectFirst("pre.commitMsg.mt5")!!.text())
        assertNull(doc.selectFirst("p.commitInfo"))
        assertNull(doc.selectFirst("pre.commitMsg.mt5"))
        mockMvc.perform(get(url(s, pr, "/changes/${"0".repeat(40)}")).session(login(s.manager))).andExpect(status().isNotFound)
    }

    @Test
    fun `충돌한 요청의 변경 화면에는 차이를 보여 주지 않는다`() {
        val s = setup()
        GitTestSupport.appendCommits(repositories, s.origin, TestCommit("원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n".toByteArray())))
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit("포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n".toByteArray())))
        val pr = open(s)
        assertEquals("", page(url(s, pr, "/changes"), login(s.manager)).selectFirst(".diff-body table")?.text().orEmpty())
    }

    @Test
    fun `풀 리퀘스트 화면의 멘션 후보에는 보낸 사람과 구성원이 나온다`() {
        val s = withCommit(setup()); val pr = open(s)
        val body = mockMvc.perform(get("/${s.origin.owner}/${s.origin.name}/mentionListAtPullRequest?pullRequestId=${pr.id}&mentionType=user").session(login(s.manager)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(body.contains("\"loginid\":\"${s.contributor.loginId}\""), body)
        assertTrue(body.contains("\"loginid\":\"${s.manager.loginId}\""), body)
        mockMvc.perform(get("/${s.origin.owner}/${s.origin.name}/mentionListAtPullRequest?pullRequestId=99999&mentionType=user")).andExpect(status().isNotFound)
    }

    @Test
    fun `멘션 후보는 리뷰 댓글을 최근에 단 사람이 먼저 나온다`() {
        val s = withCommit(setup()); val pr = open(s)
        val first = user(); val second = user()
        val thread = threads.save(dev.yona.domain.comment.NonRangedCodeCommentThread(commitId = "c").also { it.project = s.origin; it.pullRequest = pr; it.state = dev.yona.domain.enumeration.ThreadState.OPEN; it.createdDate = java.util.Date() })
        listOf(first, second).forEach { u -> reviewComments.save(dev.yona.domain.comment.ReviewComment(contents = "c", createdDate = java.util.Date(), authorId = u.id, authorLoginId = u.loginId, authorName = u.name, thread = thread)) }
        entityManager.flush(); entityManager.clear()
        val body = mockMvc.perform(get("/${s.origin.owner}/${s.origin.name}/mentionListAtPullRequest?pullRequestId=${pr.id}&mentionType=user").session(login(s.manager)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        // v1.6 addCommentAuthors는 마지막에 Collections.reverse를 부른다.
        assertTrue(body.indexOf("\"loginid\":\"${second.loginId}\"") in 0 until body.indexOf("\"loginid\":\"${first.loginId}\""), body)
    }

    @Test
    fun `멘션 후보는 스레드 순서로 훑고 스레드 안에서는 댓글 순서로 훑는다 (v1_6 addCommentAuthors)`() {
        val s = withCommit(setup()); val pr = open(s)
        val a = user(); val b = user(); val c = user()
        fun newThread() = threads.save(dev.yona.domain.comment.NonRangedCodeCommentThread(commitId = "c").also { it.project = s.origin; it.pullRequest = pr; it.state = dev.yona.domain.enumeration.ThreadState.OPEN; it.createdDate = java.util.Date() })
        fun comment(u: dev.yona.domain.user.User, t: dev.yona.domain.comment.CommentThread) = reviewComments.save(dev.yona.domain.comment.ReviewComment(contents = "c", createdDate = java.util.Date(), authorId = u.id, authorLoginId = u.loginId, authorName = u.name, thread = t))
        val first = newThread(); val second = newThread()
        // 저장 순서는 a(첫 스레드), b(둘째 스레드), c(첫 스레드)이다. 스레드별로 훑으면 a, c, b이고 뒤집으면 b, c, a이다
        comment(a, first); comment(b, second); comment(c, first)
        entityManager.flush(); entityManager.clear()
        val body = mockMvc.perform(get("/${s.origin.owner}/${s.origin.name}/mentionListAtPullRequest?pullRequestId=${pr.id}&mentionType=user").session(login(s.manager)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val order = listOf(a, b, c).sortedBy { body.indexOf("\"loginid\":\"${it.loginId}\"") }.map { it.loginId }
        assertEquals(listOf(b, c, a).map { it.loginId }, order, body)
    }

    @Test
    fun `없는 번호는 404이다`() {
        val s = setup()
        mockMvc.perform(get(url(s, PullRequest(number = 9)))).andExpect(status().isNotFound)
    }
}
