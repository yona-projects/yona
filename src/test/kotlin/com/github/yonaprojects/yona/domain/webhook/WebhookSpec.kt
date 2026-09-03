package com.github.yonaprojects.yona.domain.webhook

import com.github.yonaprojects.yona.domain.enumeration.WebhookType
import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class WebhookSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val webhook = Webhook()

            webhook.id shouldBe null
            webhook.project shouldBe null
            webhook.payloadUrl shouldBe ""
            webhook.secret shouldBe null
            webhook.gitPush shouldBe false
            webhook.webhookType shouldBe WebhookType.SIMPLE

            val project = Project(id = 1L, name = "p", owner = "owner")
            val createdAt = Instant.parse("2026-01-01T00:00:00Z")

            webhook.id = 10L
            webhook.project = project
            webhook.payloadUrl = "https://example.com/hook"
            webhook.secret = "s3cr3t"
            webhook.gitPush = true
            webhook.webhookType = WebhookType.DETAIL_SLACK
            webhook.createdAt = createdAt

            webhook.id shouldBe 10L
            webhook.project shouldBe project
            webhook.payloadUrl shouldBe "https://example.com/hook"
            webhook.secret shouldBe "s3cr3t"
            webhook.gitPush shouldBe true
            webhook.webhookType shouldBe WebhookType.DETAIL_SLACK
            webhook.createdAt shouldBe createdAt
        }

        it("생성자에 모든 인자를 전달해도 정상 생성돼야 한다") {
            val project = Project(id = 2L, name = "p2", owner = "owner2")
            val createdAt = Instant.parse("2025-06-15T12:30:00Z")

            val webhook = Webhook(
                id = 5L,
                project = project,
                payloadUrl = "https://hooks.example.com/x",
                secret = null,
                gitPush = false,
                webhookType = WebhookType.JSON,
                createdAt = createdAt
            )

            webhook.id shouldBe 5L
            webhook.project shouldBe project
            webhook.payloadUrl shouldBe "https://hooks.example.com/x"
            webhook.secret shouldBe null
            webhook.gitPush shouldBe false
            webhook.webhookType shouldBe WebhookType.JSON
            webhook.createdAt shouldBe createdAt
        }
    }
})
