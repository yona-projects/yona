package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.WebhookType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.webhook.WebhookService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

// v1.6 NotificationEvent.afterResourceDeleted: item instanceof Issue이면 webhookRequest(RESOURCE_DELETED, issue). 삭제 전에 호출되므로
// 페이로드가 이슈 정보를 담는다. next는 이슈 삭제에 웹훅도 알림도 발행하지 않았다.
class IssueDeleteWebhookIntegrationSpec @Autowired constructor(
    private val issueService: IssueService,
    private val issueRepository: IssueRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val webhookService: WebhookService,
    private val webhookRepository: com.github.yonaprojects.yona.domain.webhook.WebhookRepository
) : AbstractIntegrationTest() {
    init {
        describe("IssueService.deleteIssue") {
            it("이슈를 삭제하면 RESOURCE_DELETED 웹훅이 이슈 정보와 함께 발송되고 이슈가 지워진다") {
                val author = userRepository.save(User(loginId = "del-hook-author", name = "삭제자", email = "del-hook@yona.io"))
                val project = projectRepository.save(Project(name = "del-hook-proj", owner = author.loginId))
                val issue = issueService.createIssue(Issue(title = "지워질 이슈", body = "본문", project = project), author = author)

                val received = LinkedBlockingQueue<String>()
                val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
                server.createContext("/hook") { exchange ->
                    received.add(String(exchange.requestBody.readAllBytes()))
                    exchange.sendResponseHeaders(200, -1)
                    exchange.close()
                }
                server.start()
                try {
                    webhookService.createWebhook(project, "http://127.0.0.1:${server.address.port}/hook", null, false, WebhookType.JSON)

                    issueService.deleteIssue(issue, author)

                    val body = received.poll(10, TimeUnit.SECONDS)
                    body shouldNotBe null
                    // JSON 웹훅의 본문은 event/resourceId 등 식별 정보다 - 삭제 전에 만들어졌으므로 이슈 id가 담겨 있다.
                    body!!.contains("RESOURCE_DELETED") shouldBe true
                    body.contains("\"resourceId\":\"${issue.id}\"") shouldBe true
                    issueRepository.findById(issue.id!!).isPresent shouldBe false
                } finally {
                    server.stop(0)
                    // 공유 DB를 쓰는 다른 스펙이 이 프로젝트를 지울 때 WEBHOOK FK가 걸리지 않게 웹훅과 프로젝트를 정리한다.
                    webhookRepository.findByProjectId(project.id!!).forEach { webhookRepository.delete(it) }
                    projectRepository.findById(project.id!!).ifPresent { projectRepository.delete(it) }
                    userRepository.findByLoginId("del-hook-author").ifPresent { userRepository.delete(it) }
                }
            }
        }
    }
}
