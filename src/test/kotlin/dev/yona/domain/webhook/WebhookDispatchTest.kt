package dev.yona.domain.webhook

import com.sun.net.httpserver.HttpServer
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.PullRequestReviewAction
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.enumeration.WebhookType
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueChangedActivity
import dev.yona.domain.issue.IssueCreatedActivity
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.notification.CommitsPushedActivity
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestChangedActivity
import dev.yona.domain.pullrequest.PullRequestCreatedActivity
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.pullrequest.PullRequestReviewedActivity
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.PostReceiveJobs
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 웹훅 전송을 로컬 HTTP 서버로 받아 검증한다.
 *
 * 원본: `app/models/Webhook.java`, `app/models/NotificationEvent.java`(`webhookRequest…`).
 */
@Transactional
class WebhookDispatchTest : AbstractIntegrationTest() {

    private class Received(val headers: Map<String, List<String>>, val body: String)

    @Autowired private lateinit var publisher: ApplicationEventPublisher
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var service: WebhookService
    @Autowired private lateinit var reviewService: dev.yona.domain.pullrequest.PullRequestReviewService
    @Autowired private lateinit var threads: WebhookThreadRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository

    private val mapper = JsonMapper.builder().build()
    private val received = CopyOnWriteArrayList<Received>()
    private lateinit var server: HttpServer
    private var respondWith = "{}"
    private var sequence = 0

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/hook") { exchange ->
            received += Received(exchange.requestHeaders.toMap(), exchange.requestBody.readBytes().toString(Charsets.UTF_8))
            val bytes = respondWith.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stopServer() = server.stop(0)

    private val url get() = "http://127.0.0.1:${server.address.port}/hook"

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "wd$n", name = "이름wd$n", email = "wd$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "wdo$n", name = "wdp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return p
    }

    private fun issue(p: Project) = issues.save(Issue(project = p, number = 7, title = "제목", body = "내용", state = State.OPEN, createdDate = Date()))

    private fun json(index: Int = 0) = mapper.readTree(received[index].body)

    private fun tools.jackson.databind.JsonNode.items() = (0 until size()).map { get(it) }

    @Test
    fun `새 이슈는 간단한 형식에 글만 보낸다`() {
        val p = project(); val sender = user(); service.create(p, url, "abc", false, WebhookType.SIMPLE)
        publisher.publishEvent(IssueCreatedActivity(issue(p), sender))
        assertEquals(1, received.size)
        val message = json().path("text").asString("")
        assertTrue(message.startsWith("[${p.name}] ${sender.name} "), message)
        assertTrue(message.endsWith(" <http://localhost:9000/${p.owner}/${p.name}/issue/7|#7: 제목>"), message)
        assertTrue(json().path("attachments").isMissingNode)
        assertEquals(listOf("token abc"), received[0].headers["Authorization"])  // 서버가 값의 끝 공백을 잘라 낸다
        assertEquals(listOf("Yobi-Hookshot"), received[0].headers["User-agent"])
        assertEquals(listOf("application/json; charset=utf-8"), received[0].headers["Content-type"]) // Play WS가 문자열 본문을 보낼 때 붙이는 값이다
    }

    @Test
    fun `토큰이 비어 있으면 인증 헤더를 보내지 않는다`() {
        val p = project(); service.create(p, url, "", false, WebhookType.SIMPLE)
        publisher.publishEvent(IssueCreatedActivity(issue(p), user()))
        assertTrue(received[0].headers["Authorization"] == null)
    }

    @Test
    fun `슬랙 형식은 첨부에 본문과 항목을 담는다`() {
        val p = project(); service.create(p, url, null, false, WebhookType.DETAIL_SLACK)
        publisher.publishEvent(IssueChangedActivity(issue(p), EventType.ISSUE_STATE_CHANGED, "open", "closed", user()))
        val attachment = json().path("attachments").get(0)
        assertEquals("내용", attachment.path("text").asString(""))
        assertTrue(attachment.path("fields").items().any { it.path("value").asString("") == "OPEN" })
        assertTrue(json().path("text").asString("").contains(" <http://localhost:9000/${p.owner}/${p.name}/issue/7|#7: 제목>"))
        assertEquals("", attachment.path("color").asString(""))
    }

    @Test
    fun `구글 챗 형식은 응답의 스레드를 저장하고 다음 알림에 담는다`() {
        val p = project(); service.create(p, url, null, false, WebhookType.DETAIL_HANGOUT_CHAT)
        val issue = issue(p)
        respondWith = """{"thread":{"name":"spaces/A/threads/B"}}"""
        publisher.publishEvent(IssueCreatedActivity(issue, user()))
        assertTrue(json().path("thread").path("name").isMissingNode)
        val saved = threads.findAll().single()
        assertEquals("spaces/A/threads/B", saved.threadId); assertEquals(ResourceType.ISSUE_POST, saved.resourceType); assertEquals(issue.id.toString(), saved.resourceId)
        publisher.publishEvent(IssueChangedActivity(issue, EventType.ISSUE_STATE_CHANGED, "open", "closed", user()))
        assertEquals("spaces/A/threads/B", json(1).path("thread").path("name").asString(""))
        assertEquals(1, threads.count())
    }

    @Test
    fun `새 커밋이 없는 푸시도 지속적 통합 형식에는 빈 커밋 목록으로 보낸다`() {
        val p = project(); val sender = user()
        service.create(p, url, null, true, WebhookType.JSON)
        // 브랜치 삭제 푸시: v1.6 NotificationEvent.webhookRequest는 커밋 수와 관계없이 JSON 형식에 보낸다
        publisher.publishEvent(CommitsPushedActivity(p, sender, emptyList(), listOf("refs/heads/gone")))
        val body = json()
        assertEquals(listOf("refs/heads/gone"), body.path("ref").items().map { it.asString("") })
        assertEquals(0, body.path("commits").size())
        assertTrue(body.path("head_commit").isNull)
    }

    @Test
    fun `푸시 본문의 sender는 사이트 관리자 여부를 site_admin에 담는다`() {
        val p = project(); val sender = user(); val admin = user()
        siteAdmins.save(SiteAdmin(admin = admin))
        service.create(p, url, null, true, WebhookType.JSON)
        publisher.publishEvent(CommitsPushedActivity(p, sender, emptyList(), listOf("refs/heads/a")))
        publisher.publishEvent(CommitsPushedActivity(p, admin, emptyList(), listOf("refs/heads/b")))
        // v1.6 Webhook.buildSenderJSON: site_admin = sender.isSiteManager()
        assertEquals(false, json(0).path("sender").path("site_admin").asBoolean(true))
        assertEquals(true, json(1).path("sender").path("site_admin").asBoolean(false))
    }

    @Test
    fun `지속적 통합 형식은 푸시만 받고 다른 형식은 푸시를 켠 경우에만 한 줄을 받는다`() {
        val p = project(); val sender = user()
        service.create(p, url, null, true, WebhookType.JSON)
        publisher.publishEvent(IssueCreatedActivity(issue(p), sender))
        assertTrue(received.isEmpty())
        val commit = PostReceiveJobs.CommitDetail("a".repeat(40), "aaaaaaa", "요약", "요약\n\n설명", "작성자", "a@example.com", "커미터", "c@example.com", Date(1_700_000_000_000L))
        publisher.publishEvent(CommitsPushedActivity(p, sender, listOf(commit), listOf("refs/heads/master")))
        val body = json()
        assertEquals(listOf("refs/heads/master"), body.path("ref").items().map { it.asString("") })
        assertEquals("a".repeat(40), body.path("commits").get(0).path("id").asString(""))
        assertEquals("http://localhost:9000/${p.owner}/${p.name}/commit/${"a".repeat(40)} ", body.path("commits").get(0).path("url").asString(""))
        assertEquals(body.path("commits").get(0), body.path("head_commit"))
        assertEquals(sender.loginId, body.path("sender").path("login").asString("")); assertEquals(sender.email, body.path("pusher").path("email").asString(""))
        assertEquals(p.name, body.path("repository").path("name").asString("")); assertEquals(false, body.path("repository").path("private").asBoolean())

        received.clear()
        service.create(p, url, null, false, WebhookType.SIMPLE)
        publisher.publishEvent(CommitsPushedActivity(p, sender, listOf(commit), listOf("refs/heads/master")))
        assertEquals(1, received.size)
        assertTrue(received.single().body.contains("\"ref\""))

        received.clear()
        service.create(p, url, null, true, WebhookType.SIMPLE)
        publisher.publishEvent(CommitsPushedActivity(p, sender, listOf(commit), listOf("refs/heads/master")))
        assertEquals(2, received.size)
        val simple = received.single { it.body.contains("\"text\"") }
        assertTrue(simple.body.contains("refs/heads/master"), simple.body)
    }

    @Test
    fun `풀 리퀘스트는 생성, 병합, 리뷰를 보내고 닫기는 보내지 않는다`() {
        val p = project(); val sender = user()
        service.create(p, url, null, false, WebhookType.DETAIL_SLACK)
        val pr = pullRequests.save(PullRequest(title = "요청", body = "본문", toProject = p, fromProject = p, toBranch = "refs/heads/master", fromBranch = "refs/heads/dev", contributor = sender, state = State.OPEN, number = 3))
        publisher.publishEvent(PullRequestCreatedActivity(pr, sender))
        assertTrue(json(0).path("text").asString("").endsWith("|#3: 요청>"))
        assertEquals(listOf("refs/heads/dev", "refs/heads/master"), json(0).path("attachments").get(0).path("fields").items().drop(1).map { it.path("value").asString("") })
        publisher.publishEvent(PullRequestChangedActivity(pr, EventType.PULL_REQUEST_STATE_CHANGED, "open", "closed", sender))
        publisher.publishEvent(PullRequestChangedActivity(pr, EventType.PULL_REQUEST_COMMIT_CHANGED, null, null, sender))
        assertEquals(1, received.size)
        publisher.publishEvent(PullRequestChangedActivity(pr, EventType.PULL_REQUEST_STATE_CHANGED, "open", "merged", sender))
        publisher.publishEvent(PullRequestReviewedActivity(pr, PullRequestReviewAction.DONE, sender))
        assertEquals(3, received.size)
        assertNotNull(json(2).path("text").asString(""))
    }

    @Test
    fun `풀 리퀘스트 담당자 변경은 웹훅을 보내고 라벨 변경은 보내지 않는다 (X-14)`() {
        val p = project(); val sender = user()
        service.create(p, url, null, false, WebhookType.SIMPLE)
        val pr = pullRequests.save(PullRequest(title = "요청", body = "본문", toProject = p, fromProject = p, toBranch = "refs/heads/master", fromBranch = "refs/heads/dev", contributor = sender, state = State.OPEN, number = 5))
        publisher.publishEvent(PullRequestChangedActivity(pr, EventType.PULL_REQUEST_LABEL_CHANGED, null, "분류 - 이름 #ccc", sender))
        assertEquals(0, received.size, "라벨 변경은 웹훅을 보내지 않는다")
        publisher.publishEvent(PullRequestChangedActivity(pr, EventType.PULL_REQUEST_ASSIGNEE_CHANGED, null, sender.loginId, sender))
        assertEquals(1, received.size)
        val text = json(0).path("text").asString("")
        assertTrue(text.contains("#5: 요청"), text)
        assertTrue(text.contains(sender.name!!), text)
    }

    @Test
    fun `리뷰 판정 제출과 해제는 웹훅을 보내고 새 커밋으로 인한 시스템 해제는 보내지 않는다 (X-12)`() {
        val p = project(); val sender = user(); val reviewer = user()
        service.create(p, url, null, false, WebhookType.SIMPLE)
        val pr = pullRequests.save(PullRequest(title = "요청", body = "본문", toProject = p, fromProject = p, toBranch = "refs/heads/master", fromBranch = "refs/heads/dev", contributor = sender, state = State.OPEN, number = 5, created = java.util.Date()))
        reviewService.submit(pr, reviewer, dev.yona.domain.enumeration.ReviewVerdict.APPROVED, null)
        assertEquals(1, received.size)
        val text = json(0).path("text").asString("")
        assertTrue(text.contains("#5: 요청") && text.contains(reviewer.name!!), text)
        reviewService.dismiss(pr, pr.reviewHistory.single(), sender, "철회")
        assertEquals(2, received.size)
        reviewService.submit(pr, reviewer, dev.yona.domain.enumeration.ReviewVerdict.APPROVED, null)
        assertEquals(3, received.size)
        reviewService.dismissStaleApprovals(pr)
        assertEquals(3, received.size, "시스템 해제는 웹훅을 보내지 않는다")
    }

    @Test
    fun `연결할 수 없는 주소여도 요청은 실패하지 않는다`() {
        val p = project()
        service.create(p, "http://127.0.0.1:1/none", null, false, WebhookType.SIMPLE)
        publisher.publishEvent(IssueCreatedActivity(issue(p), user()))
        assertTrue(received.isEmpty())
    }
}
