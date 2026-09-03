package com.github.yonaprojects.yona.domain.webhook

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class WebhookThreadSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val webhookThread = WebhookThread()

            val webhook = Webhook()
            val now = Instant.now()

            webhookThread.id = 1L
            webhookThread.webhook = webhook
            webhookThread.resourceType = ResourceType.ISSUE_POST
            webhookThread.resourceId = "123"
            webhookThread.threadId = "thread-abc"
            webhookThread.createdAt = now

            webhookThread.id shouldBe 1L
            webhookThread.webhook shouldBe webhook
            webhookThread.resourceType shouldBe ResourceType.ISSUE_POST
            webhookThread.resourceId shouldBe "123"
            webhookThread.threadId shouldBe "thread-abc"
            webhookThread.createdAt shouldBe now
        }

        it("기본값으로 생성하면 resourceType/resourceId/threadId가 각각 NOT_A_RESOURCE/빈 문자열이어야 한다") {
            val webhookThread = WebhookThread()

            webhookThread.id shouldBe null
            webhookThread.webhook shouldBe null
            webhookThread.resourceType shouldBe ResourceType.NOT_A_RESOURCE
            webhookThread.resourceId shouldBe ""
            webhookThread.threadId shouldBe ""
        }
    }
})
