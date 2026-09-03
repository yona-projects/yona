package com.github.yonaprojects.yona.domain.webhook

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.WebhookType
import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional

// yona Webhook.java:622-658 sendRequest(payload, webhookId, resource)의
// "WebhookThread.getWebhookThread(webhookId, resource) == null이면 create()" 대응 (P1-143).
class WebhookThreadRecorderSpec : DescribeSpec({
    val webhookThreadRepository = mockk<WebhookThreadRepository>()
    val webhookRepository = mockk<WebhookRepository>()
    val recorder = WebhookThreadRecorder(webhookThreadRepository, webhookRepository)

    beforeTest {
        clearMocks(webhookThreadRepository, webhookRepository)
    }

    describe("recordThreadIfAbsent") {
        val project = Project(id = 1L, name = "test-project", owner = "owner")
        val webhook = Webhook(
            id = 10L, project = project, payloadUrl = "http://localhost:8080/hook",
            webhookType = WebhookType.DETAIL_HANGOUT_CHAT
        )

        it("동일 (webhookId, resourceType, resourceId)의 스레드 캐시가 없으면 새로 저장해야 한다") {
            every {
                webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(10L, ResourceType.ISSUE_POST, "100")
            } returns null
            every { webhookRepository.findById(10L) } returns Optional.of(webhook)
            val saved = slot<WebhookThread>()
            every { webhookThreadRepository.save(capture(saved)) } answers { saved.captured }

            recorder.recordThreadIfAbsent(10L, ResourceType.ISSUE_POST, "100", "spaces/AAA/threads/BBB")

            verify(exactly = 1) { webhookThreadRepository.save(any()) }
            saved.captured.webhook?.id shouldBe 10L
            saved.captured.resourceType shouldBe ResourceType.ISSUE_POST
            saved.captured.resourceId shouldBe "100"
            saved.captured.threadId shouldBe "spaces/AAA/threads/BBB"
        }

        it("이미 동일 리소스의 스레드 캐시가 있으면 저장하지 않아야 한다") {
            val existing = WebhookThread(
                id = 1L, webhook = webhook, resourceType = ResourceType.ISSUE_POST,
                resourceId = "100", threadId = "spaces/AAA/threads/BBB"
            )
            every {
                webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(10L, ResourceType.ISSUE_POST, "100")
            } returns existing

            recorder.recordThreadIfAbsent(10L, ResourceType.ISSUE_POST, "100", "spaces/AAA/threads/CCC")

            verify(exactly = 0) { webhookThreadRepository.save(any()) }
        }

        it("threadId가 비어있으면 저장하지 않아야 한다") {
            recorder.recordThreadIfAbsent(10L, ResourceType.ISSUE_POST, "100", "")

            verify(exactly = 0) { webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(any(), any(), any()) }
            verify(exactly = 0) { webhookThreadRepository.save(any()) }
        }

        it("존재하지 않는 webhookId면 저장하지 않아야 한다") {
            every {
                webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(999L, ResourceType.ISSUE_POST, "100")
            } returns null
            every { webhookRepository.findById(999L) } returns Optional.empty()

            recorder.recordThreadIfAbsent(999L, ResourceType.ISSUE_POST, "100", "spaces/AAA/threads/BBB")

            verify(exactly = 0) { webhookThreadRepository.save(any()) }
        }
    }
})
