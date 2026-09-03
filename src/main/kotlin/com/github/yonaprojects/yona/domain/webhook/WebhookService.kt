package com.github.yonaprojects.yona.domain.webhook

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.WebhookType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User

interface WebhookService {
    fun findByProject(projectId: Long): List<Webhook>
    fun createWebhook(
        project: Project,
        payloadUrl: String,
        secret: String?,
        gitPush: Boolean,
        webhookType: WebhookType
    ): Webhook
    fun deleteWebhook(id: Long)
    fun sendWebhook(
        project: Project,
        eventType: EventType,
        sender: User,
        resource: Any
    )
}
