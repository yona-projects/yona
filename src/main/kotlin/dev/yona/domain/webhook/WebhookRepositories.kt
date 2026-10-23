package dev.yona.domain.webhook

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Webhook
import dev.yona.domain.project.WebhookThread
import org.springframework.data.jpa.repository.JpaRepository

/** 웹훅 저장소이다. */
interface WebhookRepository : JpaRepository<Webhook, Long> {

    /** 프로젝트의 웹훅이다 (v1.6 `Webhook.findByProject`). */
    fun findByProjectId(projectId: Long): List<Webhook>

    /** 프로젝트에 속한 웹훅 하나이다. */
    fun findByIdAndProjectId(id: Long, projectId: Long): Webhook?
}

/** 웹훅 스레드 저장소이다. */
interface WebhookThreadRepository : JpaRepository<WebhookThread, Long> {

    /** 웹훅과 리소스에 대응하는 스레드이다 (v1.6 `WebhookThread.getWebhookThread`). */
    fun findFirstByWebhookIdAndResourceTypeAndResourceId(webhookId: Long, resourceType: ResourceType, resourceId: String): WebhookThread?
}
