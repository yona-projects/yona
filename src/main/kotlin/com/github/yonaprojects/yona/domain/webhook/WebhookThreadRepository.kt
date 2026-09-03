package com.github.yonaprojects.yona.domain.webhook

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import org.springframework.data.jpa.repository.JpaRepository

interface WebhookThreadRepository : JpaRepository<WebhookThread, Long> {
    fun findByWebhookIdAndResourceTypeAndResourceId(
        webhookId: Long,
        resourceType: ResourceType,
        resourceId: String
    ): WebhookThread?

    // yona Project.delete()의 webhook 삭제 루프 대응 (P0-19). webhook_id FK가 nullable=false라
    // 웹훅 삭제 전에 반드시 지워야 한다.
    fun findByWebhookId(webhookId: Long): List<WebhookThread>
}
