package com.github.yonaprojects.yona.domain.webhook

import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.WebhookType
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.notification.NotificationUrlResolver
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.pullrequest.CodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CommitComment
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserState
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.TreeFormatter
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import tools.jackson.databind.ObjectMapper
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private fun testCommit(message: String, authorName: String = "tester", authorEmail: String = "tester@yona.io"): RevCommit {
    val repo = InMemoryRepository(DfsRepositoryDescription())
    val inserter = repo.newObjectInserter()
    val blobId = inserter.insert(Constants.OBJ_BLOB, "content".toByteArray())
    val tree = TreeFormatter()
    tree.append("file.txt", FileMode.REGULAR_FILE, blobId)
    val treeId = inserter.insert(tree)
    val commitBuilder = CommitBuilder()
    commitBuilder.setTreeId(treeId)
    val ident = PersonIdent(authorName, authorEmail)
    commitBuilder.author = ident
    commitBuilder.committer = ident
    commitBuilder.message = message
    val commitId = inserter.insert(commitBuilder)
    inserter.flush()
    return RevWalk(repo).use { it.parseCommit(commitId) }
}

// sender.name/project.name처럼 Kotlin 타입상 non-null인 필드라도, Hibernate가 프록시/리플렉션으로
// 값을 null로 주입할 가능성에 대비해 방어적으로 `?: ""` 처리된 분기가 실제 코드에 존재한다. 이런
// 분기는 Kotlin 컴파일러가 non-null 타입에 null을 대입하는 것 자체를 막기 때문에 일반적인 생성자
// 호출이나 mockk의 every {} returns null로는 재현할 수 없다 — 리플렉션으로 필드를 직접 덮어써서
// 검증한다(실제 Hibernate가 하는 일과 동일한 방식).
private fun forceNullField(target: Any, fieldName: String) {
    val field = target.javaClass.getDeclaredField(fieldName)
    field.isAccessible = true
    field.set(target, null)
}

// sendRequestAsync()의 실제 HTTP 왕복(비동기 콜백/헤더/상태코드 분기)을 검증하기 위한 실제 로컬
// HTTP 서버. mockk로는 WebhookServiceImpl 내부에서 직접 new한 HttpClient를 가로챌 수 없어
// (외부에서 주입되는 의존성이 아님) 실제 소켓 통신으로 검증한다.
private data class CapturedRequest(val authorizationHeader: String?, val body: String)

private fun startCapturingHttpServer(statusCode: Int, responseBody: String): Triple<HttpServer, CountDownLatch, MutableList<CapturedRequest>> {
    val received = mutableListOf<CapturedRequest>()
    val latch = CountDownLatch(1)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/hook") { exchange ->
        exchange.use {
            val body = it.requestBody.readBytes().toString(Charsets.UTF_8)
            synchronized(received) {
                received.add(CapturedRequest(it.requestHeaders.getFirst("Authorization"), body))
            }
            val responseBytes = responseBody.toByteArray(Charsets.UTF_8)
            it.sendResponseHeaders(statusCode, responseBytes.size.toLong())
            it.responseBody.write(responseBytes)
        }
        latch.countDown()
    }
    server.start()
    return Triple(server, latch, received)
}

// 아무도 듣고 있지 않은(연결 거부되는) 포트를 얻기 위해 잠깐 열었다 바로 닫는다.
private fun findUnusedPort(): Int {
    ServerSocket(0).use { return it.localPort }
}

// yona-wiki P3-01(Observability) 계측 지점 6 검증용 — 서버가 요청을 받았다는 latch(요청 수신 시점)와
// 클라이언트의 thenAccept/exceptionally 콜백(응답 수신 후, 카운터 증가 시점)은 서로 다른 시점이라
// latch.await()만으로는 카운터 반영을 보장하지 못한다(실제로 이 순서로 처음 실패해 확인). 콜백이
// 비동기로 완료될 때까지 짧게 폴링한다.
private fun awaitCounterAtLeast(supplier: () -> Double, expected: Double, timeoutMs: Long = 5000) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (supplier() < expected && System.currentTimeMillis() < deadline) {
        Thread.sleep(20)
    }
}

class WebhookServiceSpec : DescribeSpec({
    val webhookRepository = mockk<WebhookRepository>()
    val webhookThreadRepository = mockk<WebhookThreadRepository>()
    val projectRepository = mockk<ProjectRepository>()
    // relaxed=true: 대부분의 테스트는 리소스 링크와 무관하므로 stub하지 않은 호출은 null(링크 없음)로
    // 흘러가게 두고, 링크 자체를 검증하는 테스트에서만 개별적으로 every {}를 재정의한다.
    val notificationUrlResolver = mockk<NotificationUrlResolver>(relaxed = true)
    val webhookThreadRecorder = mockk<WebhookThreadRecorder>(relaxed = true)
    val meterRegistry = SimpleMeterRegistry()

    val webhookService = WebhookServiceImpl(
        webhookRepository, webhookThreadRepository, "https://yona.example.com", notificationUrlResolver, webhookThreadRecorder, meterRegistry
    )

    beforeTest {
        clearMocks(webhookRepository, webhookThreadRepository, projectRepository, notificationUrlResolver, webhookThreadRecorder)
        meterRegistry.clear()
    }

    describe("WebhookService 비즈니스 테스트") {
        val project = Project(id = 1L, name = "test-project", owner = "owner", overview = "테스트 프로젝트 설명")
        val webhook = Webhook(
            id = 10L,
            project = project,
            payloadUrl = "http://localhost:8080/hook",
            secret = "mysecret",
            gitPush = true,
            webhookType = WebhookType.SIMPLE
        )

        describe("createWebhook") {
            it("전달된 정보로 Webhook 엔티티를 생성하고 저장한다") {
                every { webhookRepository.save(any()) } returns webhook

                val created = webhookService.createWebhook(
                    project = project,
                    payloadUrl = "http://localhost:8080/hook",
                    secret = "mysecret",
                    gitPush = true,
                    webhookType = WebhookType.SIMPLE
                )

                created.payloadUrl shouldBe "http://localhost:8080/hook"
                created.secret shouldBe "mysecret"
                created.gitPush shouldBe true
                verify(exactly = 1) { webhookRepository.save(any()) }
            }
        }

        describe("deleteWebhook") {
            it("주어진 ID의 Webhook 엔티티를 레포지토리에서 삭제한다") {
                every { webhookRepository.findById(10L) } returns Optional.of(webhook)
                every { webhookRepository.delete(webhook) } returns Unit

                webhookService.deleteWebhook(10L)

                verify(exactly = 1) { webhookRepository.delete(webhook) }
            }
        }

        describe("findByProject") {
            it("프로젝트 ID에 해당하는 등록된 웹훅 목록을 반환한다") {
                every { webhookRepository.findByProjectId(1L) } returns listOf(webhook)

                val list = webhookService.findByProject(1L)

                list.size shouldBe 1
                list[0].payloadUrl shouldBe "http://localhost:8080/hook"
            }
        }

        describe("sendWebhook") {
            it("프로젝트에 등록된 웹훅을 찾아 이벤트를 전송한다") {
                every { webhookRepository.findByProjectId(1L) } returns listOf(webhook)
                
                // mock eventUser
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = Issue(
                    id = 100L,
                    title = "웹훅 테스트 이슈",
                    body = "내용",
                    project = project,
                    number = 10,
                    authorId = sender.id,
                    authorLoginId = sender.loginId,
                    authorName = sender.name
                )

                // sendWebhook 호출 시 내부적으로 repository 조회 및 http 발송 처리가 일어남.
                // 비동기로 HttpClient 호출이 전개되나, Mocking 환경 하에서 예외 없이 로직이 흘러가는지 검증.
                webhookService.sendWebhook(
                    project = project,
                    eventType = EventType.NEW_ISSUE,
                    sender = sender,
                    resource = issue
                )

                verify(exactly = 1) { webhookRepository.findByProjectId(1L) }
            }
        }

        describe("shouldDeliverToWebhook (gitPush 필터 정책)") {
            fun webhookOf(gitPush: Boolean, type: WebhookType) = Webhook(
                id = 20L, project = project, payloadUrl = "http://localhost:8080/hook",
                gitPush = gitPush, webhookType = type
            )

            it("push(NEW_COMMIT)가 아닌 이벤트는 gitPush 설정과 무관하게 항상 전송되어야 한다") {
                val gitPushOnlyWebhook = webhookOf(gitPush = true, type = WebhookType.SIMPLE)

                webhookService.shouldDeliverToWebhook(gitPushOnlyWebhook, EventType.NEW_ISSUE) shouldBe true
                webhookService.shouldDeliverToWebhook(gitPushOnlyWebhook, EventType.NEW_COMMENT) shouldBe true
                webhookService.shouldDeliverToWebhook(gitPushOnlyWebhook, EventType.NEW_POSTING) shouldBe true
                webhookService.shouldDeliverToWebhook(gitPushOnlyWebhook, EventType.NEW_PULL_REQUEST) shouldBe true
            }

            it("gitPush=false인 SIMPLE/SLACK 웹훅은 NEW_COMMIT 이벤트를 받지 않아야 한다") {
                val noGitPushWebhook = webhookOf(gitPush = false, type = WebhookType.SIMPLE)

                webhookService.shouldDeliverToWebhook(noGitPushWebhook, EventType.NEW_COMMIT) shouldBe false
            }

            it("gitPush=true인 웹훅은 NEW_COMMIT 이벤트를 받아야 한다") {
                val gitPushWebhook = webhookOf(gitPush = true, type = WebhookType.SIMPLE)

                webhookService.shouldDeliverToWebhook(gitPushWebhook, EventType.NEW_COMMIT) shouldBe true
            }

            it("JSON 포맷 웹훅은 gitPush 설정과 무관하게 NEW_COMMIT 이벤트를 항상 받아야 한다") {
                val jsonWebhookNoGitPush = webhookOf(gitPush = false, type = WebhookType.JSON)

                webhookService.shouldDeliverToWebhook(jsonWebhookNoGitPush, EventType.NEW_COMMIT) shouldBe true
            }
        }

        // yona Webhook.java의 push용 buildRequestBody(commits, refNames, sender) 대응 (P2-04).
        describe("buildPayload - JSON 포맷 push 페이로드에 커밋 목록이 포함돼야 한다") {
            it("PushedCommits 리소스면 ref/commits/head_commit/sender/pusher/repository를 포함해야 한다") {
                val jsonWebhook = Webhook(
                    id = 30L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.JSON
                )
                val sender = User(id = 5L, loginId = "gildong", name = "홍길동", email = "gildong@yona.io")
                val commit = testCommit("fix bug", authorName = "gildong", authorEmail = "gildong@yona.io")
                val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("ref").get(0).asText() shouldBe "refs/heads/master"
                json.get("commits").size() shouldBe 1
                json.get("commits").get(0).get("id").asText() shouldBe commit.name
                json.get("commits").get(0).get("message").asText() shouldBe "fix bug"
                json.get("commits").get(0).get("author").get("name").asText() shouldBe "gildong"
                json.get("head_commit").get("id").asText() shouldBe commit.name
                json.get("sender").get("login").asText() shouldBe "gildong"
                json.get("pusher").get("email").asText() shouldBe "gildong@yona.io"
                json.get("repository").get("name").asText() shouldBe "test-project"
            }

            // yona Webhook.java의 buildSenderJSON()/buildRepositoryJSON()/buildJSONFromCommit() 필드
            // 4곳(site_admin/overview/절대 URL/timestamp 포맷) 대응 (P2-08).
            it("sender.site_admin/repository.overview/절대 URL/timestamp 포맷까지 legacy와 일치해야 한다") {
                val jsonWebhook = Webhook(
                    id = 31L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.JSON
                )
                val siteAdminSender = User(
                    id = 6L, loginId = "admin", name = "관리자", email = "admin@yona.io",
                    state = UserState.SITE_ADMIN
                )
                val commit = testCommit("admin commit", authorName = "admin", authorEmail = "admin@yona.io")
                val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, siteAdminSender, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("sender").get("site_admin").asBoolean() shouldBe true
                json.get("repository").get("overview").asText() shouldBe "테스트 프로젝트 설명"
                json.get("repository").get("html_url").asText() shouldBe "https://yona.example.com/owner/test-project"
                json.get("commits").get(0).get("url").asText() shouldBe
                    "https://yona.example.com/owner/test-project/commit/${commit.name}"

                val expectedTimestamp = commit.authorIdent.`when`.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ssZ"))
                json.get("commits").get(0).get("timestamp").asText() shouldBe expectedTimestamp
            }

            it("일반 사용자가 push하면 sender.site_admin은 false여야 한다") {
                val jsonWebhook = Webhook(
                    id = 32L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.JSON
                )
                val normalSender = User(id = 7L, loginId = "gildong", name = "홍길동", email = "gildong@yona.io")
                val commit = testCommit("normal commit")
                val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, normalSender, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("sender").get("site_admin").asBoolean() shouldBe false
            }
        }

        // yona-wiki P3-22 — buildPushPayload(PushedCommits)와 동일한 JSON 스키마를 Mercurial push에도
        // 적용하는 buildPushPayloadForHg()/PushedHgCommits 검증. 실제 hg4j 저장소에 커밋 하나를 만들어
        // 진짜 io.github.search5.hg4j.api.HgCommit 값 객체로 채운다(mock으로는 nodeId/author 등 값
        // 필드 조합을 재현하기 번거롭고, 실제 페이로드 빌더가 그 값 객체를 어떻게 읽는지가 검증
        // 대상이므로 진짜 객체를 쓰는 편이 더 정확하다).
        describe("buildPayload - Mercurial push 페이로드(PushedHgCommits)도 동일한 JSON 스키마를 가져야 한다 (P3-22)") {
            fun realHgCommit(repoDir: java.io.File, message: String, author: String = "gildong <gildong@yona.io>"): io.github.search5.hg4j.api.HgCommit {
                val file = java.io.File(repoDir, "a.txt")
                file.writeText(message)
                return io.github.search5.hg4j.api.Hg.open(repoDir).use { hg ->
                    hg.add().addFile("a.txt").call()
                    hg.commit().setAuthor(author).setMessage(message).call()
                    hg.log().call().first()
                }
            }

            it("ref/commits/head_commit/sender/pusher/repository를 포함하고 author==committer여야 한다") {
                val repoDir = java.nio.file.Files.createTempDirectory("webhook-hg-test").toFile()
                io.github.search5.hg4j.api.Hg.init().setDirectory(repoDir).call()
                val commit = realHgCommit(repoDir, "fix bug via hg")

                val jsonWebhook = Webhook(
                    id = 60L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.JSON
                )
                val sender = User(id = 5L, loginId = "gildong", name = "홍길동", email = "gildong@yona.io")
                val pushed = PushedHgCommits(listOf(commit), listOf("refs/heads/main"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)

                val hex = commit.nodeId.toHex()
                json.get("ref").get(0).asText() shouldBe "refs/heads/main"
                json.get("commits").size() shouldBe 1
                json.get("commits").get(0).get("id").asText() shouldBe hex
                json.get("commits").get(0).get("message").asText() shouldBe "fix bug via hg"
                json.get("commits").get(0).get("author").get("name").asText() shouldBe "gildong"
                json.get("commits").get(0).get("author").get("email").asText() shouldBe "gildong@yona.io"
                json.get("commits").get(0).get("committer").get("name").asText() shouldBe "gildong"
                json.get("commits").get(0).get("committer").get("email").asText() shouldBe "gildong@yona.io"
                json.get("head_commit").get("id").asText() shouldBe hex
                json.get("sender").get("login").asText() shouldBe "gildong"
                json.get("pusher").get("email").asText() shouldBe "gildong@yona.io"
                json.get("repository").get("name").asText() shouldBe "test-project"
            }

            // getResourceType()/getResourceId()는 private라 직접 호출할 수 없으므로, 이미 그 값을
            // 실제로 소비하는 경로(WebhookType.JSON의 raw-JSON 폴백 분기가 resourceId/resourceType을
            // 채우는 것과 동일한 구조)로 우회 검증한다 — DETAIL_HANGOUT_CHAT의 스레드 키 계산이
            // getResourceType()/getResourceId()를 그대로 재사용하므로, 커밋이 없을 때/있을 때 모두
            // 예외 없이 동작하고 스레드 조회 여부가 갈리는지로 간접 확인한다.
            it("빈 커밋 목록이어도 buildPayload가 예외 없이 빈 commits 배열을 반환해야 한다") {
                val jsonWebhook = Webhook(
                    id = 61L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.JSON
                )
                val sender = User(id = 5L, loginId = "gildong", name = "홍길동", email = "gildong@yona.io")
                val pushed = PushedHgCommits(emptyList(), listOf("refs/heads/main"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("commits").size() shouldBe 0
                json.has("head_commit") shouldBe false
            }
        }

        // yona Webhook.java:182-192 buildRequestMessage() 대응 (P1-132) — 텍스트 메시지에 리소스 링크가 [GL-models_Webhook-017;GL-models_Webhook-018]
        // 전혀 없던 것을 Slack 링크 문법(" <url|text>")으로 붙이도록 수정.
        describe("buildPayload - 텍스트 메시지 리소스 링크 (P1-132)") {
            it("SIMPLE 웹훅은 텍스트 메시지 끝에 이슈 링크를 붙여야 한다") {
                val simpleWebhook = Webhook(
                    id = 40L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.SIMPLE
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = Issue(
                    id = 100L, title = "웹훅 테스트 이슈", body = "내용", project = project, number = 10
                )
                every {
                    notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, "100")
                } returns "https://yona.example.com/owner/test-project/issue/10"

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe
                    "[test-project] 송신자님이 새 이슈를 등록했습니다. <https://yona.example.com/owner/test-project/issue/10|#10: 웹훅 테스트 이슈>"
            }

            it("DETAIL_SLACK 웹훅은 링크 텍스트의 '>'를 '&gt;'로 이스케이프해야 한다") {
                val slackWebhook = Webhook(
                    id = 41L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_SLACK
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = Issue(
                    id = 101L, title = "A > B 비교", body = "내용", project = project, number = 11
                )
                every {
                    notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, "101")
                } returns "https://yona.example.com/owner/test-project/issue/11"

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe
                    "[test-project] 송신자님이 새 이슈를 등록했습니다. <https://yona.example.com/owner/test-project/issue/11|#11: A &gt; B 비교>"
            }

            it("이슈 댓글은 댓글 자신이 아니라 부모 이슈의 #번호: 제목을 링크 텍스트로 써야 한다") {
                val simpleWebhook = Webhook(
                    id = 42L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.SIMPLE
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val parentIssue = Issue(
                    id = 200L, title = "부모 이슈", body = "내용", project = project, number = 20
                )
                val comment = IssueComment(
                    id = 300L, contents = "댓글 내용입니다", issue = parentIssue
                )
                every {
                    notificationUrlResolver.getUrl(ResourceType.ISSUE_COMMENT, "300")
                } returns "https://yona.example.com/owner/test-project/issue/20#comment-300"

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_COMMENT, sender, comment)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe
                    "[test-project] 송신자님이 새 댓글을 등록했습니다. <https://yona.example.com/owner/test-project/issue/20#comment-300|#20: 부모 이슈>"
            }

            it("리소스 URL을 찾지 못하면 링크 없이 본문 텍스트만 반환해야 한다") {
                val simpleWebhook = Webhook(
                    id = 43L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.SIMPLE
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = Issue(
                    id = 102L, title = "삭제된 이슈", body = "내용", project = project, number = 12
                )
                every { notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, "102") } returns null

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe "[test-project] 송신자님이 새 이슈를 등록했습니다."
            }
        }

        // yona Webhook.java:284-298 buildIssueDetails() / :502-515 buildJsonWithPullReqtuestDetails() 대응 [GL-models_Webhook-024]
        // (P1-133) — DETAIL_SLACK attachment의 이슈 필드(마일스톤/담당자/상태)가 "State" 하나로 축소돼
        // 있었고, PR attachment는 아예 미지원이었다.
        describe("buildPayload - DETAIL_SLACK attachment 필드 (P1-133)") {
            it("이슈는 마일스톤(있을 때만)/담당자/상태 필드를 모두 포함해야 한다") {
                val slackWebhook = Webhook(
                    id = 50L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_SLACK
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val assigneeUser = User(id = 8L, loginId = "assignee", name = "담당자이름")
                val milestone = Milestone(id = 500L, title = "1.0 릴리즈", project = project)
                val issue = Issue(
                    id = 103L, title = "필드 테스트 이슈", body = "이슈 본문", project = project, number = 13,
                    milestone = milestone,
                    assignee = Assignee(id = 1L, user = assigneeUser, project = project)
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = ObjectMapper().readTree(payload)
                val fields = json.get("attachments").get(0).get("fields")

                json.get("attachments").get(0).get("text").asText() shouldBe "이슈 본문"
                fields.get(0).get("title").asText() shouldBe "마일 스톤 변경"
                fields.get(0).get("value").asText() shouldBe "1.0 릴리즈"
                fields.get(1).get("title").asText() shouldBe ""
                fields.get(1).get("value").asText() shouldBe "담당자이름"
                fields.get(2).get("title").asText() shouldBe "상태"
                fields.get(2).get("value").asText() shouldBe issue.state.toString()
            }

            it("마일스톤이 없는 이슈는 마일스톤 필드 없이 담당자/상태 필드만 포함해야 한다") {
                val slackWebhook = Webhook(
                    id = 51L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_SLACK
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = Issue(
                    id = 104L, title = "마일스톤 없는 이슈", body = "본문", project = project, number = 14
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = ObjectMapper().readTree(payload)
                val fields = json.get("attachments").get(0).get("fields")

                fields.size() shouldBe 2
                fields.get(0).get("title").asText() shouldBe ""
                fields.get(1).get("title").asText() shouldBe "상태"
            }

            it("풀 리퀘스트는 보낸사람/보낸브랜치/받는브랜치 필드를 포함해야 한다 (yona 원본은 미지원이었음)") {
                val slackWebhook = Webhook(
                    id = 52L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_SLACK
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val contributor = User(id = 9L, loginId = "contributor", name = "기여자")
                val pullRequest = PullRequest(
                    id = 60L, title = "PR 제목", body = "PR 본문",
                    toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature/x",
                    contributor = contributor, number = 3
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_PULL_REQUEST, sender, pullRequest)
                val json = ObjectMapper().readTree(payload)
                val attachment = json.get("attachments").get(0)
                val fields = attachment.get("fields")

                attachment.get("text").asText() shouldBe "PR 본문"
                fields.get(0).get("title").asText() shouldBe "보낸 사람"
                fields.get(0).get("value").asText() shouldBe "기여자"
                fields.get(1).get("title").asText() shouldBe "코드 보내는 곳"
                fields.get(1).get("value").asText() shouldBe "feature/x"
                fields.get(2).get("title").asText() shouldBe "코드 받을 곳"
                fields.get(2).get("value").asText() shouldBe "master"
            }

            // CommitComment는 yona Webhook.java에 대응 오버로드 자체가 없는 yona 전용 리소스라, 링크나
            // 필드를 새로 만들어 붙이지 않아야 한다(레거시에 없는 동작 추가 금지).
            it("CommitComment는 링크나 attachment 필드를 새로 만들지 않아야 한다") {
                val slackWebhook = Webhook(
                    id = 53L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_SLACK
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val commitComment = CommitComment(
                    id = 70L, project = project, contents = "커밋 댓글 내용"
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_COMMENT, sender, commitComment)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe "[test-project] 송신자님이 새 댓글을 등록했습니다."
                json.get("attachments").get(0).get("text").asText() shouldBe ""
                json.get("attachments").get(0).get("fields").size() shouldBe 0
            }

            // yona Webhook.java:299-317 Posting 오버로드에는 DETAIL_SLACK 전용 분기가 없어(다른
            // 타입과 달리) SLACK 웹훅이어도 attachments 없이 텍스트만 보낸다 (P2-36).
            it("Posting(게시글)은 SLACK 웹훅이어도 attachments 없이 텍스트만 보내야 한다") {
                val slackWebhook = Webhook(
                    id = 54L, project = project, payloadUrl = "http://localhost:8080/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_SLACK
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val posting = Posting(
                    id = 80L, title = "게시글 제목", body = "게시글 본문", project = project, number = 4
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_POSTING, sender, posting)
                val json = ObjectMapper().readTree(payload)

                json.has("attachments") shouldBe false
            }
        }

        // yona Webhook.java:643-648 — Hangout Chat 응답의 thread.name을 파싱해 WebhookThread로
        // 저장하는 쓰기 경로가 yona에 전혀 없던 것(P1-143)을 추가.
        describe("recordHangoutThreadIfNeeded (P1-143)") {
            it("DETAIL_HANGOUT_CHAT 웹훅이고 응답에 thread.name이 있으면 저장을 요청해야 한다") {
                val hangoutWebhook = Webhook(
                    id = 60L, project = project, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.DETAIL_HANGOUT_CHAT
                )
                val issue = Issue(
                    id = 105L, title = "스레드 테스트 이슈", body = "내용", project = project, number = 15
                )
                val responseBody = """{"thread":{"name":"spaces/AAA/threads/BBB"}}"""

                webhookService.recordHangoutThreadIfNeeded(hangoutWebhook, issue, responseBody)

                verify(exactly = 1) {
                    webhookThreadRecorder.recordThreadIfAbsent(60L, ResourceType.ISSUE_POST, "105", "spaces/AAA/threads/BBB")
                }
            }

            it("DETAIL_HANGOUT_CHAT이 아닌 웹훅이면 저장을 요청하지 않아야 한다") {
                val slackWebhook = Webhook(
                    id = 61L, project = project, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.DETAIL_SLACK
                )
                val issue = Issue(
                    id = 106L, title = "이슈", body = "내용", project = project, number = 16
                )
                val responseBody = """{"thread":{"name":"spaces/AAA/threads/BBB"}}"""

                webhookService.recordHangoutThreadIfNeeded(slackWebhook, issue, responseBody)

                verify(exactly = 0) { webhookThreadRecorder.recordThreadIfAbsent(any(), any(), any(), any()) }
            }

            it("응답 본문이 JSON이 아니면 예외 없이 저장을 요청하지 않아야 한다") {
                val hangoutWebhook = Webhook(
                    id = 62L, project = project, webhookType = WebhookType.DETAIL_HANGOUT_CHAT
                )
                val issue = Issue(
                    id = 107L, title = "이슈", body = "내용", project = project, number = 17
                )

                webhookService.recordHangoutThreadIfNeeded(hangoutWebhook, issue, "not a json")

                verify(exactly = 0) { webhookThreadRecorder.recordThreadIfAbsent(any(), any(), any(), any()) }
            }
        }

        // yona Webhook.java:346 eventComment.getParent().asResource() / :480 eventPullRequest.asResource()
        // 대응 (P1-134) — 댓글 이벤트의 Hangout 스레드 키는 댓글 자신이 아니라 부모 리소스 기준이어야
        // 같은 이슈/게시글/PR에 달리는 댓글들이 한 스레드로 묶인다.
        describe("Hangout Chat 스레드 키는 댓글 자신이 아니라 부모 리소스를 써야 한다 (P1-134)") {
            val hangoutWebhook = Webhook(
                id = 70L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.DETAIL_HANGOUT_CHAT
            )
            val sender = User(id = 2L, loginId = "sender", name = "송신자")

            it("이슈 댓글은 부모 이슈(ISSUE_POST)를 스레드 키로 조회/저장해야 한다") {
                val parentIssue = Issue(
                    id = 200L, title = "부모 이슈", body = "내용", project = project, number = 20
                )
                val comment = IssueComment(
                    id = 300L, contents = "댓글", issue = parentIssue
                )
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.ISSUE_POST, "200")
                } returns null

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_COMMENT, sender, comment)
                verify(exactly = 1) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.ISSUE_POST, "200")
                }
                verify(exactly = 0) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.ISSUE_COMMENT, "300")
                }

                webhookService.recordHangoutThreadIfNeeded(
                    hangoutWebhook, comment, """{"thread":{"name":"spaces/AAA/threads/BBB"}}"""
                )
                verify(exactly = 1) {
                    webhookThreadRecorder.recordThreadIfAbsent(70L, ResourceType.ISSUE_POST, "200", "spaces/AAA/threads/BBB")
                }
            }

            it("PR 리뷰 댓글은 부모 풀 리퀘스트(PULL_REQUEST)를 스레드 키로 조회/저장해야 한다") {
                val contributor = User(id = 9L, loginId = "contributor", name = "기여자")
                val pullRequest = PullRequest(
                    id = 60L, title = "PR", body = "본문",
                    toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature/x",
                    contributor = contributor, number = 3
                )
                val thread = CodeCommentThread(
                    id = 400L, pullRequest = pullRequest, project = project
                )
                val reviewComment = ReviewComment(
                    id = 500L, contents = "리뷰 댓글", thread = thread
                )
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.PULL_REQUEST, "60")
                } returns null

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_REVIEW_COMMENT, sender, reviewComment)
                verify(exactly = 1) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.PULL_REQUEST, "60")
                }

                webhookService.recordHangoutThreadIfNeeded(
                    hangoutWebhook, reviewComment, """{"thread":{"name":"spaces/AAA/threads/CCC"}}"""
                )
                verify(exactly = 1) {
                    webhookThreadRecorder.recordThreadIfAbsent(70L, ResourceType.PULL_REQUEST, "60", "spaces/AAA/threads/CCC")
                }
            }

            // CommitComment는 yona Webhook.java에 대응 이벤트 자체가 없어(P2-18) 부모 매핑 규칙이
            // 없다 — 레거시에 없는 동작을 새로 추가하지 않도록 자기 자신의 키를 그대로 써야 한다.
            it("CommitComment는 부모 매핑 규칙이 없어 자기 자신의 키(COMMIT_COMMENT)를 그대로 써야 한다") {
                val commitComment = CommitComment(
                    id = 600L, project = project, contents = "커밋 댓글"
                )
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.COMMIT_COMMENT, "600")
                } returns null

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_COMMENT, sender, commitComment)
                verify(exactly = 1) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(70L, ResourceType.COMMIT_COMMENT, "600")
                }
            }
        }

        // 커버리지 보강: sendWebhook()의 조기 종료/빈 목록/필터링 분기.
        describe("sendWebhook 추가 분기 처리") {
            it("project.id가 null이면 웹훅 조회 자체를 하지 않고 종료해야 한다") {
                val projectWithoutId = Project(id = null, name = "no-id-project", owner = "owner")
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = Issue(id = 100L, title = "이슈", body = "내용", project = projectWithoutId, number = 1)

                webhookService.sendWebhook(projectWithoutId, EventType.NEW_ISSUE, sender, issue)

                verify(exactly = 0) { webhookRepository.findByProjectId(any()) }
            }

            it("등록된 웹훅이 없으면 조회만 하고 이후 처리는 하지 않아야 한다") {
                every { webhookRepository.findByProjectId(1L) } returns emptyList()
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = Issue(id = 100L, title = "이슈", body = "내용", project = project, number = 1)

                webhookService.sendWebhook(project, EventType.NEW_ISSUE, sender, issue)

                verify(exactly = 1) { webhookRepository.findByProjectId(1L) }
            }

            it("gitPush=false 웹훅은 건너뛰고, 전송 대상 웹훅에만 실제 HTTP 요청을 보내야 한다") {
                val (server, latch, received) = startCapturingHttpServer(200, "{}")
                try {
                    val deliveredWebhook = Webhook(
                        id = 80L, project = project,
                        payloadUrl = "http://127.0.0.1:${server.address.port}/hook",
                        gitPush = true, webhookType = WebhookType.SIMPLE
                    )
                    val skippedWebhook = Webhook(
                        id = 81L, project = project,
                        payloadUrl = "http://127.0.0.1:${server.address.port}/hook",
                        gitPush = false, webhookType = WebhookType.SIMPLE
                    )
                    every { webhookRepository.findByProjectId(1L) } returns listOf(skippedWebhook, deliveredWebhook)
                    val sender = User(id = 2L, loginId = "sender", name = "송신자")
                    val commit = testCommit("push commit")
                    val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                    webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, pushed)

                    latch.await(5, TimeUnit.SECONDS) shouldBe true
                    // 필터링된 웹훅이 요청을 보냈다면 2건 이상 수신되므로, 잠깐 더 대기해 추가 요청이
                    // 없는지 확인한다.
                    Thread.sleep(300)
                    synchronized(received) { received.size } shouldBe 1
                } finally {
                    server.stop(0)
                }
            }
        }

        // 커버리지 보강: deleteWebhook()이 존재하지 않는 ID를 받았을 때의 분기.
        describe("deleteWebhook 추가 분기 처리") {
            it("존재하지 않는 ID면 삭제를 호출하지 않아야 한다") {
                every { webhookRepository.findById(999L) } returns Optional.empty()

                webhookService.deleteWebhook(999L)

                verify(exactly = 0) { webhookRepository.delete(any()) }
            }
        }

        // 커버리지 보강: buildPayload()의 DETAIL_HANGOUT_CHAT 스레드 조회 가드 분기들.
        describe("buildPayload - DETAIL_HANGOUT_CHAT 스레드 조회 추가 분기") {
            val hangoutWebhook = Webhook(
                id = 90L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.DETAIL_HANGOUT_CHAT
            )
            val sender = User(id = 2L, loginId = "sender", name = "송신자")

            it("알 수 없는 타입(NOT_A_RESOURCE)의 리소스면 스레드 저장소를 조회하지 않아야 한다") {
                webhookService.buildPayload(hangoutWebhook, EventType.NEW_COMMENT, sender, "정체불명의 리소스")

                verify(exactly = 0) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(any(), any(), any())
                }
            }

            it("리소스 ID가 비어있으면(id가 null) 스레드 저장소를 조회하지 않아야 한다") {
                val issueWithoutId = Issue(id = null, title = "ID 없는 이슈", body = "내용", project = project, number = 21)

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_ISSUE, sender, issueWithoutId)

                verify(exactly = 0) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(any(), any(), any())
                }
            }

            it("webhook.id가 null이면 스레드 저장소를 0L 기준으로 조회해야 한다") {
                val hangoutWebhookWithoutId = Webhook(
                    id = null, project = project, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.DETAIL_HANGOUT_CHAT
                )
                val issue = Issue(id = 110L, title = "이슈", body = "내용", project = project, number = 22)
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(0L, ResourceType.ISSUE_POST, "110")
                } returns null

                webhookService.buildPayload(hangoutWebhookWithoutId, EventType.NEW_ISSUE, sender, issue)

                verify(exactly = 1) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(0L, ResourceType.ISSUE_POST, "110")
                }
            }

            it("기존에 저장된 스레드가 있으면 응답 JSON의 thread.name에 담아야 한다") {
                val issue = Issue(id = 111L, title = "이슈", body = "내용", project = project, number = 23)
                val existingThread = WebhookThread(
                    id = 1000L, webhook = hangoutWebhook, resourceType = ResourceType.ISSUE_POST,
                    resourceId = "111", threadId = "spaces/EXIST/threads/ABC"
                )
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(90L, ResourceType.ISSUE_POST, "111")
                } returns existingThread

                val payload = webhookService.buildPayload(hangoutWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = ObjectMapper().readTree(payload)

                json.get("thread").get("name").asText() shouldBe "spaces/EXIST/threads/ABC"
            }
        }

        // 커버리지 보강: buildPayload()의 JSON 포맷 중 PushedCommits가 아닌 리소스(raw JSON) 분기.
        describe("buildPayload - JSON 포맷 raw JSON (push 아닌 리소스)") {
            it("PushedCommits가 아닌 리소스는 event/sender/project/resourceId/resourceType을 담은 raw JSON을 반환해야 한다") {
                val jsonWebhook = Webhook(
                    id = 91L, project = project, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.JSON
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = Issue(id = 112L, title = "raw json 이슈", body = "내용", project = project, number = 24)

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = ObjectMapper().readTree(payload)

                json.get("event").asText() shouldBe "NEW_ISSUE"
                json.get("sender").asText() shouldBe "송신자"
                json.get("project").asText() shouldBe "test-project"
                json.get("resourceId").asText() shouldBe "112"
                json.get("resourceType").asText() shouldBe "ISSUE_POST"
            }

            it("webhook.project가 null이면 project 필드는 빈 문자열이어야 한다") {
                val jsonWebhookNoProject = Webhook(
                    id = 92L, project = null, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.JSON
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = Issue(id = 113L, title = "이슈", body = "내용", project = project, number = 25)

                val payload = webhookService.buildPayload(jsonWebhookNoProject, EventType.NEW_ISSUE, sender, issue)
                val json = ObjectMapper().readTree(payload)

                json.get("project").asText() shouldBe ""
            }
        }

        // 커버리지 보강: buildAttachmentJSON()의 IssueComment/PostingComment/ReviewComment 분기
        // (DETAIL_SLACK 웹훅에서만 buildAttachmentJSON이 호출된다).
        describe("buildAttachmentJSON - 댓글/리뷰댓글 DETAIL_SLACK 분기") {
            val slackWebhook = Webhook(
                id = 93L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.DETAIL_SLACK
            )
            val sender = User(id = 2L, loginId = "sender", name = "송신자")

            it("IssueComment는 attachment text에 댓글 내용을 담아야 한다") {
                val parentIssue = Issue(id = 210L, title = "부모 이슈", body = "내용", project = project, number = 26)
                val comment = IssueComment(id = 310L, contents = "이슈 댓글 내용", issue = parentIssue)

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_COMMENT, sender, comment)
                val json = ObjectMapper().readTree(payload)

                json.get("attachments").get(0).get("text").asText() shouldBe "이슈 댓글 내용"
                json.get("attachments").get(0).get("fields").size() shouldBe 0
            }

            it("PostingComment는 attachment text에 댓글 내용을 담아야 한다") {
                val parentPosting = Posting(id = 220L, title = "부모 게시글", body = "내용", project = project, number = 27)
                val comment = PostingComment(id = 320L, contents = "게시글 댓글 내용", posting = parentPosting)

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_COMMENT, sender, comment)
                val json = ObjectMapper().readTree(payload)

                json.get("attachments").get(0).get("text").asText() shouldBe "게시글 댓글 내용"
                json.get("attachments").get(0).get("fields").size() shouldBe 0
            }

            it("ReviewComment는 부모 풀 리퀘스트가 있으면 본문과 보낸사람/브랜치 필드를 포함해야 한다") {
                val contributor = User(id = 9L, loginId = "contributor", name = "기여자")
                val pullRequest = PullRequest(
                    id = 61L, title = "PR", body = "PR 본문2",
                    toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature/y",
                    contributor = contributor, number = 5
                )
                val thread = CodeCommentThread(id = 401L, pullRequest = pullRequest, project = project)
                val reviewComment = ReviewComment(id = 501L, contents = "리뷰 댓글", thread = thread)

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_REVIEW_COMMENT, sender, reviewComment)
                val json = ObjectMapper().readTree(payload)
                val attachment = json.get("attachments").get(0)
                val fields = attachment.get("fields")

                attachment.get("text").asText() shouldBe "PR 본문2"
                fields.get(0).get("title").asText() shouldBe "보낸 사람"
                fields.get(0).get("value").asText() shouldBe "기여자"
                fields.get(1).get("value").asText() shouldBe "feature/y"
                fields.get(2).get("value").asText() shouldBe "master"
            }

            it("ReviewComment는 부모 스레드가 없으면(thread null) 본문/필드 없이 빈 텍스트여야 한다") {
                val reviewComment = ReviewComment(id = 502L, contents = "리뷰 댓글", thread = null)

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_REVIEW_COMMENT, sender, reviewComment)
                val json = ObjectMapper().readTree(payload)
                val attachment = json.get("attachments").get(0)

                attachment.get("text").asText() shouldBe ""
                attachment.get("fields").size() shouldBe 0
            }
        }

        // 커버리지 보강: buildResourceLink()의 PostingComment/ReviewComment(부모 없음) 분기.
        describe("buildResourceLink - PostingComment/ReviewComment 추가 분기") {
            val simpleWebhook = Webhook(
                id = 94L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.SIMPLE
            )
            val sender = User(id = 2L, loginId = "sender", name = "송신자")

            it("게시글 댓글은 댓글 자신이 아니라 부모 게시글의 #번호: 제목을 링크 텍스트로 써야 한다") {
                val parentPosting = Posting(id = 230L, title = "부모 게시글", body = "내용", project = project, number = 28)
                val comment = PostingComment(id = 330L, contents = "댓글", posting = parentPosting)
                every {
                    notificationUrlResolver.getUrl(ResourceType.NONISSUE_COMMENT, "330")
                } returns "https://yona.example.com/owner/test-project/posting/28#comment-330"

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_COMMENT, sender, comment)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe
                    "[test-project] 송신자님이 새 댓글을 등록했습니다. <https://yona.example.com/owner/test-project/posting/28#comment-330|#28: 부모 게시글>"
            }

            it("리뷰 댓글의 부모 스레드가 없으면 링크 없이 본문 텍스트만 반환해야 한다") {
                val reviewComment = ReviewComment(id = 503L, contents = "리뷰 댓글", thread = null)

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_REVIEW_COMMENT, sender, reviewComment)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe "[test-project] 송신자님이 새 리뷰 댓글을 등록했습니다."
            }
        }

        // 커버리지 보강: buildTextMessage()의 EventType when절 나머지 분기들.
        describe("buildTextMessage - 나머지 EventType 분기") {
            val simpleWebhook = Webhook(
                id = 95L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.SIMPLE
            )
            val sender = User(id = 2L, loginId = "sender", name = "송신자")
            val issue = Issue(id = 114L, title = "이벤트 테스트 이슈", body = "내용", project = project, number = 29)

            val expectedActionMessages = mapOf(
                EventType.ISSUE_STATE_CHANGED to "이슈 상태를 변경했습니다",
                EventType.PULL_REQUEST_MERGED to "풀 리퀘스트를 병합했습니다",
                EventType.PULL_REQUEST_STATE_CHANGED to "풀 리퀘스트 상태를 변경했습니다",
                EventType.PULL_REQUEST_COMMIT_CHANGED to "풀 리퀘스트에 커밋을 추가했습니다",
                EventType.PULL_REQUEST_REVIEW_STATE_CHANGED to "풀 리퀘스트 리뷰 상태를 변경했습니다"
            )

            expectedActionMessages.forEach { (eventType, expectedMessage) ->
                it("$eventType 이벤트는 '$expectedMessage' 액션 메시지를 사용해야 한다") {
                    // relaxed mock의 기본값(빈 문자열)이 아니라 "링크 없음(null)"을 명시적으로 고정해,
                    // 액션 메시지 문구만 검증할 수 있도록 한다.
                    every { notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, "114") } returns null

                    val payload = webhookService.buildPayload(simpleWebhook, eventType, sender, issue)
                    val json = ObjectMapper().readTree(payload)

                    json.get("text").asText() shouldBe "[test-project] 송신자님이 $expectedMessage."
                }
            }

            it("when절에 정의되지 않은 EventType은 기본 메시지('이벤트를 트리거했습니다')를 사용해야 한다") {
                every { notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, "114") } returns null

                val payload = webhookService.buildPayload(simpleWebhook, EventType.MEMBER_ENROLL_REQUEST, sender, issue)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe "[test-project] 송신자님이 이벤트를 트리거했습니다."
            }
        }

        // 커버리지 보강: buildPushPayload()의 빈 커밋 목록 / null author·committer / project null 분기,
        // 그리고 buildTextMessage()의 refNames 빈 목록 분기.
        describe("buildPushPayload 추가 분기") {
            val jsonWebhook = Webhook(
                id = 96L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.JSON
            )
            val sender = User(id = 5L, loginId = "gildong", name = "홍길동", email = "gildong@yona.io")

            it("커밋이 없는 push면 head_commit 필드를 만들지 않아야 한다") {
                val pushed = PushedCommits(emptyList(), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("commits").size() shouldBe 0
                json.has("head_commit") shouldBe false
            }

            it("커밋 작성자/커미터 정보가 없으면 빈 문자열로 채워야 한다") {
                val commitWithoutIdent = mockk<RevCommit>(relaxed = true)
                every { commitWithoutIdent.name } returns "deadbeef"
                every { commitWithoutIdent.fullMessage } returns "메시지 없음 커밋"
                every { commitWithoutIdent.authorIdent } returns null
                every { commitWithoutIdent.committerIdent } returns null
                val pushed = PushedCommits(listOf(commitWithoutIdent), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)
                val commitNode = json.get("commits").get(0)

                commitNode.get("timestamp").asText() shouldBe ""
                commitNode.get("author").get("name").asText() shouldBe ""
                commitNode.get("author").get("email").asText() shouldBe ""
                commitNode.get("committer").get("name").asText() shouldBe ""
                commitNode.get("committer").get("email").asText() shouldBe ""
            }

            it("webhook.project가 null이면 repository 필드가 기본값이어야 한다") {
                val jsonWebhookNoProject = Webhook(
                    id = 97L, project = null, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.JSON
                )
                val commit = testCommit("no project commit")
                val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhookNoProject, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)
                val repositoryNode = json.get("repository")

                repositoryNode.get("id").asLong() shouldBe 0L
                repositoryNode.get("name").asText() shouldBe ""
                repositoryNode.get("owner").asText() shouldBe ""
                repositoryNode.get("overview").asText() shouldBe ""
                repositoryNode.get("html_url").asText() shouldBe "https://yona.example.com"
                repositoryNode.get("private").asBoolean() shouldBe true
            }

            it("refNames가 비어있으면 텍스트 메시지에 브랜치명 없이 표시해야 한다") {
                val simpleWebhook = Webhook(
                    id = 98L, project = project, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.SIMPLE
                )
                val commit = testCommit("no ref commit")
                val pushed = PushedCommits(listOf(commit), emptyList())

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe "[test-project] 홍길동님이 커밋을 푸시했습니다. 1개의 커밋을  브랜치로 푸시했습니다"
            }
        }

        // 커버리지 보강: Project.projectScope == PUBLIC이면 repository.private가 false여야 하는 분기.
        describe("buildPushPayload - ProjectScope PUBLIC 분기") {
            it("공개(PUBLIC) 프로젝트는 repository.private가 false여야 한다") {
                val publicProject = Project(
                    id = 2L, name = "public-project", owner = "owner", projectScope = ProjectScope.PUBLIC
                )
                val jsonWebhook = Webhook(
                    id = 99L, project = publicProject, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.JSON
                )
                val sender = User(id = 5L, loginId = "gildong", name = "홍길동")
                val commit = testCommit("public project commit")
                val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("repository").get("private").asBoolean() shouldBe false
            }
        }

        // 커버리지 보강: recordHangoutThreadIfNeeded()의 webhook.id null 분기.
        describe("recordHangoutThreadIfNeeded 추가 분기") {
            it("webhook.id가 null이면 스레드 저장을 요청하지 않아야 한다") {
                val hangoutWebhookWithoutId = Webhook(
                    id = null, project = project, webhookType = WebhookType.DETAIL_HANGOUT_CHAT
                )
                val issue = Issue(id = 115L, title = "이슈", body = "내용", project = project, number = 30)
                val responseBody = """{"thread":{"name":"spaces/AAA/threads/BBB"}}"""

                webhookService.recordHangoutThreadIfNeeded(hangoutWebhookWithoutId, issue, responseBody)

                verify(exactly = 0) { webhookThreadRecorder.recordThreadIfAbsent(any(), any(), any(), any()) }
            }
        }

        // 커버리지 보강: sendRequestAsync()의 실제 HTTP 왕복(헤더/상태코드/예외) 분기들.
        // WebhookServiceImpl이 HttpClient를 직접 new하므로 목킹 대신 실제 로컬 HTTP 서버로 검증한다.
        describe("sendRequestAsync - 실제 HTTP 통신 분기") {
            it("secret이 있으면 Authorization 헤더를 담아 전송해야 한다") {
                val (server, latch, received) = startCapturingHttpServer(200, "{}")
                try {
                    val secretWebhook = Webhook(
                        id = 100L, project = project,
                        payloadUrl = "http://127.0.0.1:${server.address.port}/hook",
                        secret = "supersecret", gitPush = true, webhookType = WebhookType.SIMPLE
                    )
                    every { webhookRepository.findByProjectId(1L) } returns listOf(secretWebhook)
                    val sender = User(id = 2L, loginId = "sender", name = "송신자")
                    val issue = Issue(id = 116L, title = "이슈", body = "내용", project = project, number = 31)

                    webhookService.sendWebhook(project, EventType.NEW_ISSUE, sender, issue)

                    latch.await(5, TimeUnit.SECONDS) shouldBe true
                    synchronized(received) { received[0].authorizationHeader } shouldBe "token supersecret"
                    // yona-wiki P3-01(Observability) 계측 지점 6 검증 — 2xx 응답은 status=200 태그로 집계된다.
                    awaitCounterAtLeast({ meterRegistry.counter("yona.webhook.sent", "status", "200").count() }, 1.0)
                    meterRegistry.counter("yona.webhook.sent", "status", "200").count() shouldBe 1.0
                    meterRegistry.timer("yona.webhook.duration").count() shouldBe 1L
                } finally {
                    server.stop(0)
                }
            }

            it("secret이 없으면 Authorization 헤더를 담지 않아야 한다") {
                val (server, latch, received) = startCapturingHttpServer(200, "{}")
                try {
                    val noSecretWebhook = Webhook(
                        id = 101L, project = project,
                        payloadUrl = "http://127.0.0.1:${server.address.port}/hook",
                        secret = null, gitPush = true, webhookType = WebhookType.SIMPLE
                    )
                    every { webhookRepository.findByProjectId(1L) } returns listOf(noSecretWebhook)
                    val sender = User(id = 2L, loginId = "sender", name = "송신자")
                    val issue = Issue(id = 117L, title = "이슈", body = "내용", project = project, number = 32)

                    webhookService.sendWebhook(project, EventType.NEW_ISSUE, sender, issue)

                    latch.await(5, TimeUnit.SECONDS) shouldBe true
                    synchronized(received) { received[0].authorizationHeader } shouldBe null
                } finally {
                    server.stop(0)
                }
            }

            it("HTTP 응답이 2xx가 아니면 스레드 저장을 호출하지 않아야 한다") {
                val (server, latch, _) = startCapturingHttpServer(500, """{"thread":{"name":"spaces/X/threads/Y"}}""")
                try {
                    val hangoutWebhook = Webhook(
                        id = 102L, project = project,
                        payloadUrl = "http://127.0.0.1:${server.address.port}/hook",
                        gitPush = true, webhookType = WebhookType.DETAIL_HANGOUT_CHAT
                    )
                    every { webhookRepository.findByProjectId(1L) } returns listOf(hangoutWebhook)
                    every {
                        webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(102L, ResourceType.ISSUE_POST, "118")
                    } returns null
                    val sender = User(id = 2L, loginId = "sender", name = "송신자")
                    val issue = Issue(id = 118L, title = "이슈", body = "내용", project = project, number = 33)

                    webhookService.sendWebhook(project, EventType.NEW_ISSUE, sender, issue)

                    latch.await(5, TimeUnit.SECONDS) shouldBe true
                    // 실패 응답 처리(println 분기)가 끝날 시간을 잠깐 더 준 뒤에도 저장 요청이 없어야 한다.
                    Thread.sleep(300)
                    verify(exactly = 0) { webhookThreadRecorder.recordThreadIfAbsent(any(), any(), any(), any()) }
                    awaitCounterAtLeast({ meterRegistry.counter("yona.webhook.sent", "status", "500").count() }, 1.0)
                    meterRegistry.counter("yona.webhook.sent", "status", "500").count() shouldBe 1.0
                } finally {
                    server.stop(0)
                }
            }

            it("2xx 응답이고 DETAIL_HANGOUT_CHAT이면 스레드 저장 콜백이 호출되어야 한다") {
                val (server, _, _) = startCapturingHttpServer(200, """{"thread":{"name":"spaces/REAL/threads/HTTP"}}""")
                try {
                    val hangoutWebhook = Webhook(
                        id = 103L, project = project,
                        payloadUrl = "http://127.0.0.1:${server.address.port}/hook",
                        gitPush = true, webhookType = WebhookType.DETAIL_HANGOUT_CHAT
                    )
                    every { webhookRepository.findByProjectId(1L) } returns listOf(hangoutWebhook)
                    every {
                        webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(103L, ResourceType.ISSUE_POST, "119")
                    } returns null
                    val recorderLatch = CountDownLatch(1)
                    every {
                        webhookThreadRecorder.recordThreadIfAbsent(103L, ResourceType.ISSUE_POST, "119", "spaces/REAL/threads/HTTP")
                    } answers { recorderLatch.countDown() }
                    val sender = User(id = 2L, loginId = "sender", name = "송신자")
                    val issue = Issue(id = 119L, title = "이슈", body = "내용", project = project, number = 34)

                    webhookService.sendWebhook(project, EventType.NEW_ISSUE, sender, issue)

                    recorderLatch.await(5, TimeUnit.SECONDS) shouldBe true
                    verify(exactly = 1) {
                        webhookThreadRecorder.recordThreadIfAbsent(103L, ResourceType.ISSUE_POST, "119", "spaces/REAL/threads/HTTP")
                    }
                } finally {
                    server.stop(0)
                }
            }

            it("연결할 수 없는 URL이면 예외 없이 처리되어야 한다 (exceptionally 분기)") {
                val unusedPort = findUnusedPort()
                val unreachableWebhook = Webhook(
                    id = 104L, project = project,
                    payloadUrl = "http://127.0.0.1:$unusedPort/hook",
                    gitPush = true, webhookType = WebhookType.DETAIL_HANGOUT_CHAT
                )
                every { webhookRepository.findByProjectId(1L) } returns listOf(unreachableWebhook)
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(104L, ResourceType.ISSUE_POST, "120")
                } returns null
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = Issue(id = 120L, title = "이슈", body = "내용", project = project, number = 35)

                // 연결 실패는 비동기 콜백(exceptionally)에서 로그만 남기고 삼켜야 하므로, 호출 자체는
                // 예외 없이 즉시 반환되어야 한다.
                webhookService.sendWebhook(project, EventType.NEW_ISSUE, sender, issue)

                Thread.sleep(1000)
                verify(exactly = 0) { webhookThreadRecorder.recordThreadIfAbsent(any(), any(), any(), any()) }
                awaitCounterAtLeast({ meterRegistry.counter("yona.webhook.sent", "status", "connection_error").count() }, 1.0)
                meterRegistry.counter("yona.webhook.sent", "status", "connection_error").count() shouldBe 1.0
            }

            it("payloadUrl이 잘못된 URI 형식이면 동기적으로 예외를 삼키고 처리되어야 한다") {
                val malformedWebhook = Webhook(
                    id = 105L, project = project,
                    payloadUrl = "http://exa mple .com/hook",
                    gitPush = true, webhookType = WebhookType.SIMPLE
                )
                every { webhookRepository.findByProjectId(1L) } returns listOf(malformedWebhook)
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = Issue(id = 121L, title = "이슈", body = "내용", project = project, number = 36)

                webhookService.sendWebhook(project, EventType.NEW_ISSUE, sender, issue)

                verify(exactly = 1) { webhookRepository.findByProjectId(1L) }
                verify(exactly = 0) { webhookThreadRecorder.recordThreadIfAbsent(any(), any(), any(), any()) }
                meterRegistry.counter("yona.webhook.sent", "status", "client_error").count() shouldBe 1.0
            }
        }

        // 커버리지 최종 보강: sender.name/webhook.project?.name처럼 Kotlin 타입상으로는 non-null이지만
        // 방어적으로 ?: 처리된 필드들. Hibernate가 프록시/리플렉션으로 null을 주입할 가능성에 대비한
        // 코드라 mockk로 non-null 타입 필드에 강제로 null을 주입해 검증한다(임의 스킵 아님).
        describe("커버리지 최종 보강 - non-null 타입 필드에 대한 방어적 분기") {
            it("sender.name이 null이면(방어적 분기) raw JSON의 sender 필드가 빈 문자열이어야 한다") {
                val jsonWebhook = Webhook(
                    id = 111L, project = project, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.JSON
                )
                val senderWithNullName = User(id = 2L, loginId = "sender", name = "송신자")
                forceNullField(senderWithNullName, "name")
                val issue = Issue(id = 130L, title = "이슈", body = "내용", project = project, number = 50)

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_ISSUE, senderWithNullName, issue)
                val json = ObjectMapper().readTree(payload)

                json.get("sender").asText() shouldBe ""
            }

            it("webhook.project.name이 null이면(방어적 분기) raw JSON의 project 필드가 빈 문자열이어야 한다") {
                val projectWithNullName = Project(id = 4L, name = "임시", owner = "owner")
                forceNullField(projectWithNullName, "name")
                val jsonWebhook = Webhook(
                    id = 112L, project = projectWithNullName, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.JSON
                )
                val sender = User(id = 2L, loginId = "sender", name = "송신자")
                val issue = Issue(id = 131L, title = "이슈", body = "내용", project = project, number = 51)

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_ISSUE, sender, issue)
                val json = ObjectMapper().readTree(payload)

                json.get("project").asText() shouldBe ""
            }
        }

        // 커버리지 최종 보강: buildPushPayload()의 sender.id/project.id/project.owner/project.name(방어적)
        // null 분기, 그리고 커밋 author/committer의 name·email·when이 모두 null인 조합.
        describe("커버리지 최종 보강 - buildPushPayload 나머지 null 분기") {
            val jsonWebhook = Webhook(
                id = 113L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.JSON
            )

            it("sender.id가 null이면 sender.id 필드는 0이어야 한다") {
                val senderWithoutId = User(id = null, loginId = "noid", name = "아이디없음")
                val commit = testCommit("no sender id commit")
                val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, senderWithoutId, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("sender").get("id").asLong() shouldBe 0L
            }

            it("project는 존재하지만 id/owner가 null이면 repository.id는 0, repository.owner는 빈 문자열이어야 한다") {
                val projectWithoutIdAndOwner = Project(id = null, name = "no-id-owner", owner = null)
                val jsonWebhookNoIdOwner = Webhook(
                    id = 114L, project = projectWithoutIdAndOwner, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.JSON
                )
                val sender = User(id = 5L, loginId = "gildong", name = "홍길동")
                val commit = testCommit("no project id/owner commit")
                val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhookNoIdOwner, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("repository").get("id").asLong() shouldBe 0L
                json.get("repository").get("owner").asText() shouldBe ""
            }

            it("project.name이 null이면(방어적 분기) repository.name이 빈 문자열이어야 한다") {
                val projectWithNullName = Project(id = 8L, name = "임시", owner = "owner")
                forceNullField(projectWithNullName, "name")
                val jsonWebhookNullName = Webhook(
                    id = 115L, project = projectWithNullName, payloadUrl = "http://localhost:8080/hook",
                    webhookType = WebhookType.JSON
                )
                val sender = User(id = 5L, loginId = "gildong", name = "홍길동")
                val commit = testCommit("null project name commit")
                val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhookNullName, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("repository").get("name").asText() shouldBe ""
            }

            it("커밋 author/committer의 name·email·when이 모두 null이면(플랫폼 타입 방어 분기) 빈 문자열로 채워야 한다") {
                val identAllNull = mockk<PersonIdent>(relaxed = true)
                every { identAllNull.name } returns null
                every { identAllNull.emailAddress } returns null
                every { identAllNull.`when` } returns null
                val commitWithNullFields = mockk<RevCommit>(relaxed = true)
                every { commitWithNullFields.name } returns "deadbeef2"
                every { commitWithNullFields.fullMessage } returns "필드 없음 커밋"
                every { commitWithNullFields.authorIdent } returns identAllNull
                every { commitWithNullFields.committerIdent } returns identAllNull
                val pushed = PushedCommits(listOf(commitWithNullFields), listOf("refs/heads/master"))
                val sender = User(id = 5L, loginId = "gildong", name = "홍길동")

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)
                val commitNode = json.get("commits").get(0)

                commitNode.get("timestamp").asText() shouldBe ""
                commitNode.get("author").get("name").asText() shouldBe ""
                commitNode.get("author").get("email").asText() shouldBe ""
                commitNode.get("committer").get("name").asText() shouldBe ""
                commitNode.get("committer").get("email").asText() shouldBe ""
            }

            it("sender.email이 null이면(방어적 분기) pusher.email이 빈 문자열이어야 한다") {
                val senderWithNullEmail = User(id = 5L, loginId = "gildong", name = "홍길동", email = "real@yona.io")
                forceNullField(senderWithNullEmail, "email")
                val commit = testCommit("null email commit")
                val pushed = PushedCommits(listOf(commit), listOf("refs/heads/master"))

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, senderWithNullEmail, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("pusher").get("email").asText() shouldBe ""
            }

            // commit.authorIdent.when.toInstant() 이후의 ?.atZone()?.format() 체인은 java.time.Instant/
            // ZonedDateTime의 JDK 계약(never null)에 의해 정상적으로는 도달 불가능하지만, mockk가 이
            // final JDK 클래스까지 모킹 가능한 것을 확인해 실제로 null을 주입해 검증한다.
            it("authorInstant.atZone()이 null을 반환하면(플랫폼 타입 방어 분기) timestamp가 빈 문자열이어야 한다") {
                val instantWithNullZone = mockk<Instant>(relaxed = true)
                every { instantWithNullZone.atZone(any()) } returns null
                val dateReturningMockInstant = mockk<Date>(relaxed = true)
                every { dateReturningMockInstant.toInstant() } returns instantWithNullZone
                val identWithMockDate = mockk<PersonIdent>(relaxed = true)
                every { identWithMockDate.`when` } returns dateReturningMockInstant
                val commitMock = mockk<RevCommit>(relaxed = true)
                every { commitMock.name } returns "mockedsha1"
                every { commitMock.fullMessage } returns "메시지1"
                every { commitMock.authorIdent } returns identWithMockDate
                every { commitMock.committerIdent } returns identWithMockDate
                val pushed = PushedCommits(listOf(commitMock), listOf("refs/heads/master"))
                val sender = User(id = 5L, loginId = "gildong", name = "홍길동")

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("commits").get(0).get("timestamp").asText() shouldBe ""
            }

            it("authorInstant.atZone().format()이 null을 반환하면(플랫폼 타입 방어 분기) timestamp가 빈 문자열이어야 한다") {
                val zonedDateTimeWithNullFormat = mockk<ZonedDateTime>(relaxed = true)
                every { zonedDateTimeWithNullFormat.format(any()) } returns null
                val instantWithNullFormat = mockk<Instant>(relaxed = true)
                every { instantWithNullFormat.atZone(any<ZoneId>()) } returns zonedDateTimeWithNullFormat
                val dateReturningMockInstant = mockk<Date>(relaxed = true)
                every { dateReturningMockInstant.toInstant() } returns instantWithNullFormat
                val identWithMockDate = mockk<PersonIdent>(relaxed = true)
                every { identWithMockDate.`when` } returns dateReturningMockInstant
                val commitMock = mockk<RevCommit>(relaxed = true)
                every { commitMock.name } returns "mockedsha2"
                every { commitMock.fullMessage } returns "메시지2"
                every { commitMock.authorIdent } returns identWithMockDate
                every { commitMock.committerIdent } returns identWithMockDate
                val pushed = PushedCommits(listOf(commitMock), listOf("refs/heads/master"))
                val sender = User(id = 5L, loginId = "gildong", name = "홍길동")

                val payload = webhookService.buildPayload(jsonWebhook, EventType.NEW_COMMIT, sender, pushed)
                val json = ObjectMapper().readTree(payload)

                json.get("commits").get(0).get("timestamp").asText() shouldBe ""
            }
        }

        // 커버리지 최종 보강: buildAttachmentJSON()의 Issue.body null 분기, 그리고 ReviewComment의
        // "부모 스레드는 있지만 pullRequest가 없는" 분기(buildAttachmentJSON/buildResourceLink/threadKeyOf
        // 세 곳 모두에 영향을 준다).
        describe("커버리지 최종 보강 - Issue.body null / ReviewComment 부모 PR 없음") {
            val slackWebhook = Webhook(
                id = 120L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.DETAIL_SLACK
            )
            val simpleWebhook = Webhook(
                id = 121L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.SIMPLE
            )
            val hangoutWebhook = Webhook(
                id = 122L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.DETAIL_HANGOUT_CHAT
            )
            val sender = User(id = 2L, loginId = "sender", name = "송신자")

            it("Issue.body가 null이면 attachment text가 빈 문자열이어야 한다") {
                val issueWithoutBody = Issue(id = 140L, title = "본문 없는 이슈", body = null, project = project, number = 60)

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_ISSUE, sender, issueWithoutBody)
                val json = ObjectMapper().readTree(payload)

                json.get("attachments").get(0).get("text").asText() shouldBe ""
            }

            it("PullRequest.body가 null이면 attachment text가 빈 문자열이어야 한다") {
                val contributor = User(id = 9L, loginId = "contributor", name = "기여자")
                val prWithoutBody = PullRequest(
                    id = 63L, title = "PR", body = null,
                    toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature/no-body",
                    contributor = contributor, number = 11
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_PULL_REQUEST, sender, prWithoutBody)
                val json = ObjectMapper().readTree(payload)

                json.get("attachments").get(0).get("text").asText() shouldBe ""
            }

            // Assignee.user는 Kotlin 타입상 non-null이지만, Hibernate가 프록시/리플렉션으로 null을
            // 주입할 가능성에 대비한 방어적 분기(resource.assignee?.user?.name)가 실제 코드에 존재한다.
            it("Assignee.user가 null이면(방어적 분기) attachment 담당자 필드가 빈 문자열이어야 한다") {
                val assigneeUser = User(id = 8L, loginId = "assignee", name = "담당자이름")
                val assignee = Assignee(id = 1L, user = assigneeUser, project = project)
                forceNullField(assignee, "user")
                val issueWithBrokenAssignee = Issue(
                    id = 141L, title = "담당자 깨진 이슈", body = "내용", project = project, number = 61,
                    assignee = assignee
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_ISSUE, sender, issueWithBrokenAssignee)
                val json = ObjectMapper().readTree(payload)
                val fields = json.get("attachments").get(0).get("fields")

                fields.get(0).get("value").asText() shouldBe ""
            }

            it("Assignee.user.name이 null이면(방어적 분기) attachment 담당자 필드가 빈 문자열이어야 한다") {
                val assigneeUser = User(id = 8L, loginId = "assignee", name = "담당자이름")
                forceNullField(assigneeUser, "name")
                val assignee = Assignee(id = 1L, user = assigneeUser, project = project)
                val issueWithNamelessAssignee = Issue(
                    id = 142L, title = "담당자 이름 없는 이슈", body = "내용", project = project, number = 62,
                    assignee = assignee
                )

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_ISSUE, sender, issueWithNamelessAssignee)
                val json = ObjectMapper().readTree(payload)
                val fields = json.get("attachments").get(0).get("fields")

                fields.get(0).get("value").asText() shouldBe ""
            }

            val threadWithoutPR = CodeCommentThread(id = 410L, pullRequest = null, project = project)
            val reviewCommentNoParent = ReviewComment(id = 510L, contents = "리뷰 댓글", thread = threadWithoutPR)

            it("buildAttachmentJSON: 부모 스레드는 있지만 pullRequest가 없으면 본문/필드가 비어야 한다") {
                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_REVIEW_COMMENT, sender, reviewCommentNoParent)
                val json = ObjectMapper().readTree(payload)
                val attachment = json.get("attachments").get(0)

                attachment.get("text").asText() shouldBe ""
                attachment.get("fields").size() shouldBe 0
            }

            it("buildResourceLink: 부모 스레드는 있지만 pullRequest가 없으면 링크 없이 본문 텍스트만 반환해야 한다") {
                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_REVIEW_COMMENT, sender, reviewCommentNoParent)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe "[test-project] 송신자님이 새 리뷰 댓글을 등록했습니다."
            }

            it("threadKeyOf: 부모 스레드는 있지만 pullRequest가 없으면 자기 자신의 키(REVIEW_COMMENT)를 사용해야 한다") {
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(122L, ResourceType.REVIEW_COMMENT, "510")
                } returns null

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_REVIEW_COMMENT, sender, reviewCommentNoParent)

                verify(exactly = 1) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(122L, ResourceType.REVIEW_COMMENT, "510")
                }
            }

            it("threadKeyOf: 부모 풀 리퀘스트의 id가 null이면 resourceId가 비어 스레드 조회를 하지 않아야 한다") {
                val contributor = User(id = 9L, loginId = "contributor", name = "기여자")
                val prWithoutId = PullRequest(
                    id = null, title = "PR", body = "본문",
                    toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature/z",
                    contributor = contributor, number = 8
                )
                val threadWithPRNoId = CodeCommentThread(id = 411L, pullRequest = prWithoutId, project = project)
                val reviewComment = ReviewComment(id = 511L, contents = "리뷰 댓글", thread = threadWithPRNoId)

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_REVIEW_COMMENT, sender, reviewComment)

                verify(exactly = 0) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(any(), any(), any())
                }
            }

            it("threadKeyOf: ReviewComment의 부모 스레드 자체가 null이면 자기 자신의 키(REVIEW_COMMENT)를 사용해야 한다") {
                val reviewCommentNoThread = ReviewComment(id = 512L, contents = "리뷰 댓글", thread = null)
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(122L, ResourceType.REVIEW_COMMENT, "512")
                } returns null

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_REVIEW_COMMENT, sender, reviewCommentNoThread)

                verify(exactly = 1) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(122L, ResourceType.REVIEW_COMMENT, "512")
                }
            }

            it("ReviewComment의 부모 PR.body가 null이면 attachment text가 빈 문자열이어야 한다") {
                val contributor = User(id = 9L, loginId = "contributor", name = "기여자")
                val pullRequestNoBody = PullRequest(
                    id = 64L, title = "PR", body = null,
                    toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature/pr-no-body",
                    contributor = contributor, number = 12
                )
                val threadWithBodylessPR = CodeCommentThread(id = 413L, pullRequest = pullRequestNoBody, project = project)
                val reviewComment = ReviewComment(id = 513L, contents = "리뷰 댓글", thread = threadWithBodylessPR)

                val payload = webhookService.buildPayload(slackWebhook, EventType.NEW_REVIEW_COMMENT, sender, reviewComment)
                val json = ObjectMapper().readTree(payload)

                json.get("attachments").get(0).get("text").asText() shouldBe ""
            }
        }

        // 커버리지 최종 보강: getResourceId()의 각 리소스 타입별 id=null 분기. Posting/IssueComment/
        // PostingComment/ReviewComment/PullRequest는 buildResourceLink 경로로, CommitComment/PushedCommits는
        // threadKeyOf(DETAIL_HANGOUT_CHAT) 경로로 도달한다. 후자는 resourceId가 빈 문자열이 되어
        // buildPayload의 스레드 조회 가드(resId.isNotBlank())에 걸려 저장소 조회 자체는 일어나지 않는다.
        describe("커버리지 최종 보강 - getResourceId() id=null 분기") {
            val simpleWebhook = Webhook(
                id = 130L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.SIMPLE
            )
            val hangoutWebhook = Webhook(
                id = 131L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.DETAIL_HANGOUT_CHAT
            )
            val sender = User(id = 2L, loginId = "sender", name = "송신자")

            it("Posting의 id가 null이면 resourceId로 빈 문자열을 사용해야 한다") {
                val postingWithoutId = Posting(id = null, title = "ID없는 게시글", body = "내용", project = project, number = 70)
                every { notificationUrlResolver.getUrl(ResourceType.BOARD_POST, "") } returns "https://yona.example.com/posting"

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_POSTING, sender, postingWithoutId)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe
                    "[test-project] 송신자님이 새 게시글을 작성했습니다. <https://yona.example.com/posting|#70: ID없는 게시글>"
            }

            it("IssueComment 자신의 id가 null이면 resourceId로 빈 문자열을 사용해야 한다") {
                val parentIssue = Issue(id = 150L, title = "부모 이슈", body = "내용", project = project, number = 71)
                val commentWithoutId = IssueComment(id = null, contents = "댓글", issue = parentIssue)
                every { notificationUrlResolver.getUrl(ResourceType.ISSUE_COMMENT, "") } returns "https://yona.example.com/comment"

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_COMMENT, sender, commentWithoutId)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe
                    "[test-project] 송신자님이 새 댓글을 등록했습니다. <https://yona.example.com/comment|#71: 부모 이슈>"
            }

            it("PostingComment 자신의 id가 null이면 resourceId로 빈 문자열을 사용해야 한다") {
                val parentPosting = Posting(id = 160L, title = "부모 게시글", body = "내용", project = project, number = 72)
                val commentWithoutId = PostingComment(id = null, contents = "댓글", posting = parentPosting)
                every { notificationUrlResolver.getUrl(ResourceType.NONISSUE_COMMENT, "") } returns "https://yona.example.com/pcomment"

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_COMMENT, sender, commentWithoutId)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe
                    "[test-project] 송신자님이 새 댓글을 등록했습니다. <https://yona.example.com/pcomment|#72: 부모 게시글>"
            }

            it("ReviewComment 자신의 id가 null이면 resourceId로 빈 문자열을 사용해야 한다") {
                val contributor = User(id = 9L, loginId = "contributor", name = "기여자")
                val pullRequest = PullRequest(
                    id = 62L, title = "PR", body = "본문",
                    toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature/id-null",
                    contributor = contributor, number = 9
                )
                val thread = CodeCommentThread(id = 412L, pullRequest = pullRequest, project = project)
                val reviewCommentWithoutId = ReviewComment(id = null, contents = "리뷰", thread = thread)
                every { notificationUrlResolver.getUrl(ResourceType.REVIEW_COMMENT, "") } returns "https://yona.example.com/review"

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_REVIEW_COMMENT, sender, reviewCommentWithoutId)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe
                    "[test-project] 송신자님이 새 리뷰 댓글을 등록했습니다. <https://yona.example.com/review|#9: PR>"
            }

            it("PullRequest의 id가 null이면 resourceId로 빈 문자열을 사용해야 한다") {
                val contributor = User(id = 9L, loginId = "contributor", name = "기여자")
                val prWithoutId = PullRequest(
                    id = null, title = "ID없는 PR", body = "내용",
                    toProject = project, fromProject = project,
                    toBranch = "master", fromBranch = "feature/pr-id-null",
                    contributor = contributor, number = 10
                )
                every { notificationUrlResolver.getUrl(ResourceType.PULL_REQUEST, "") } returns "https://yona.example.com/pr"

                val payload = webhookService.buildPayload(simpleWebhook, EventType.NEW_PULL_REQUEST, sender, prWithoutId)
                val json = ObjectMapper().readTree(payload)

                json.get("text").asText() shouldBe
                    "[test-project] 송신자님이 새 풀 리퀘스트를 생성했습니다. <https://yona.example.com/pr|#10: ID없는 PR>"
            }

            it("CommitComment의 id가 null이면(threadKeyOf 경로) resourceId가 비어 스레드 조회를 하지 않아야 한다") {
                val commitCommentWithoutId = CommitComment(id = null, project = project, contents = "커밋 댓글")

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_COMMENT, sender, commitCommentWithoutId)

                verify(exactly = 0) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(any(), any(), any())
                }
            }

            it("PushedCommits는(threadKeyOf 경로) 커밋이 없으면 스레드 조회를 하지 않고, 있으면 첫 커밋 이름으로 조회해야 한다") {
                val pushedEmpty = PushedCommits(emptyList(), listOf("refs/heads/master"))

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_COMMIT, sender, pushedEmpty)

                verify(exactly = 0) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(any(), any(), any())
                }

                val commit = testCommit("threadkey push commit")
                val pushedNonEmpty = PushedCommits(listOf(commit), listOf("refs/heads/master"))
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(131L, ResourceType.COMMIT, commit.name)
                } returns null

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_COMMIT, sender, pushedNonEmpty)

                verify(exactly = 1) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(131L, ResourceType.COMMIT, commit.name)
                }
            }

            // RevCommit.getName()은 JGit 클래스의 메서드라 Kotlin이 플랫폼 타입(String!)으로 보고,
            // Long.toString()류의 JDK 표준 라이브러리 정적 메서드(항상 non-null 보장)와 달리 mockk로
            // null을 주입할 수 있다 — 커밋이 있어도 이름이 null이면 resourceId가 빈 문자열이어야 한다.
            it("PushedCommits의 첫 커밋 이름(RevCommit.name)이 null이면 resourceId로 빈 문자열을 사용해야 한다") {
                val commitWithNullName = mockk<RevCommit>(relaxed = true)
                every { commitWithNullName.name } returns null
                val pushedNullName = PushedCommits(listOf(commitWithNullName), listOf("refs/heads/master"))

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_COMMIT, sender, pushedNullName)

                verify(exactly = 0) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(any(), any(), any())
                }
            }
        }

        // 커버리지 최종 보강: threadKeyOf()의 PostingComment 분기(기존에 IssueComment/ReviewComment/
        // CommitComment만 검증돼 있었음)와 그 부모 게시글 id=null 조합.
        describe("커버리지 최종 보강 - threadKeyOf PostingComment 분기") {
            val hangoutWebhook = Webhook(
                id = 132L, project = project, payloadUrl = "http://localhost:8080/hook",
                webhookType = WebhookType.DETAIL_HANGOUT_CHAT
            )
            val sender = User(id = 2L, loginId = "sender", name = "송신자")

            it("게시글 댓글은 부모 게시글(BOARD_POST)을 스레드 키로 조회해야 한다") {
                val parentPosting = Posting(id = 240L, title = "부모 게시글", body = "내용", project = project, number = 41)
                val comment = PostingComment(id = 340L, contents = "댓글", posting = parentPosting)
                every {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(132L, ResourceType.BOARD_POST, "240")
                } returns null

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_COMMENT, sender, comment)

                verify(exactly = 1) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(132L, ResourceType.BOARD_POST, "240")
                }
            }

            it("게시글 댓글의 부모 게시글 id가 null이면 resourceId가 비어 스레드 조회를 하지 않아야 한다") {
                val parentPostingNoId = Posting(id = null, title = "ID없는 부모 게시글", body = "내용", project = project, number = 42)
                val comment = PostingComment(id = 341L, contents = "댓글", posting = parentPostingNoId)

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_COMMENT, sender, comment)

                verify(exactly = 0) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(any(), any(), any())
                }
            }

            it("이슈 댓글의 부모 이슈 id가 null이면 resourceId가 비어 스레드 조회를 하지 않아야 한다") {
                val parentIssueNoId = Issue(id = null, title = "ID없는 부모 이슈", body = "내용", project = project, number = 43)
                val comment = IssueComment(id = 350L, contents = "댓글", issue = parentIssueNoId)

                webhookService.buildPayload(hangoutWebhook, EventType.NEW_COMMENT, sender, comment)

                verify(exactly = 0) {
                    webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(any(), any(), any())
                }
            }
        }

        // 커버리지 최종 보강: sendRequestAsync()의 secret이 빈 문자열(blank)인 분기, 그리고 콜백 람다의
        // statusCode < 200(1xx 정보성 응답) 분기.
        describe("커버리지 최종 보강 - sendRequestAsync 나머지 분기") {
            it("secret이 빈 문자열(blank)이면 Authorization 헤더를 담지 않아야 한다") {
                val (server, latch, received) = startCapturingHttpServer(200, "{}")
                try {
                    val blankSecretWebhook = Webhook(
                        id = 142L, project = project,
                        payloadUrl = "http://127.0.0.1:${server.address.port}/hook",
                        secret = "   ", gitPush = true, webhookType = WebhookType.SIMPLE
                    )
                    every { webhookRepository.findByProjectId(1L) } returns listOf(blankSecretWebhook)
                    val sender = User(id = 2L, loginId = "sender", name = "송신자")
                    val issue = Issue(id = 122L, title = "이슈", body = "내용", project = project, number = 37)

                    webhookService.sendWebhook(project, EventType.NEW_ISSUE, sender, issue)

                    latch.await(5, TimeUnit.SECONDS) shouldBe true
                    synchronized(received) { received[0].authorizationHeader } shouldBe null
                } finally {
                    server.stop(0)
                }
            }

            // sendRequestAsync$lambda$0의 statusCode < 200(1xx) 분기는 실측 결과 도달 불가능으로
            // 판단해 테스트를 작성하지 않는다 — 근거: com.sun.net.httpserver.HttpServer는 rCode가
            // 1xx면 "forcing contentLen = -1"로 강제해 응답 본문 쓰기 자체가 IOException(stream
            // closed)으로 실패하고, 설령 서버가 1xx만 보내고 끝내더라도 java.net.http.HttpClient는
            // RFC 9110에 따라 1xx 중간 응답을 최종 응답으로 절대 넘기지 않고 내부적으로 계속 대기한다
            // (실제로 재현: sendAsync 대신 client.send()로 직접 확인한 결과 응답이 영원히 도달하지
            // 않고 타임아웃). 즉 HttpResponse.statusCode()가 200 미만 값을 반환하는 상황 자체가 이
            // JDK HttpClient 구현에서는 발생할 수 없다.
        }
    }
})
