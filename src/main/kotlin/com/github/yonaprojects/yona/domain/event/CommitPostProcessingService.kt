package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.CommitIssueReferenceService
import com.github.yonaprojects.yona.domain.issue.CommitReference
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommits
import com.github.yonaprojects.yona.domain.webhook.WebhookService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CommitPostProcessingService(
    private val notificationEventRecorder: NotificationEventRecorder,
    private val commitIssueReferenceService: CommitIssueReferenceService,
    private val webhookService: WebhookService,
    private val watchService: WatchService,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(CommitPostProcessingService::class.java)

    fun process(project: Project, pushed: PushedVcsCommits, sender: User) {
        if (pushed.commits.isEmpty()) return

        notifyCommits(project, pushed, sender)
        // Commit IDs alone cannot resolve the project for the asynchronous webhook listener.
        webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, pushed)
        for (commit in pushed.commits) {
            commitIssueReferenceService.record(
                project,
                CommitReference(commit.id, commit.message, sender)
            )
        }
    }

    private fun notifyCommits(project: Project, pushed: PushedVcsCommits, sender: User) {
        val title = if (pushed.refNames.size == 1) {
            "[${project.name}] ${pushed.commits.size}개의 커밋이 ${pushed.refNames[0]} 브랜치로 푸시되었습니다."
        } else {
            "[${project.name}] ${pushed.commits.size}개의 커밋이 푸시되었습니다."
        }
        // SVN revision numbers are only unique within a project.
        val isSvn = project.vcs.equals("SVN", ignoreCase = true) || project.vcs.equals("SUBVERSION", ignoreCase = true)
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = sender.id,
            created = Instant.now(),
            resourceType = if (isSvn) ResourceType.PROJECT else ResourceType.COMMIT,
            resourceId = if (isSvn) project.id.toString() else pushed.commits.first().id,
            eventType = EventType.NEW_COMMIT,
            newValue = title
        )
        val receivers = watchService.findActualWatchers(
            baseWatchers = emptySet(),
            resourceType = ResourceType.PROJECT,
            resourceId = project.id.toString(),
            projectId = project.id,
            eventType = EventType.NEW_COMMIT
        ).toMutableSet()
        receivers.removeIf { it.id == sender.id }
        notificationEvent.receivers = receivers
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }
        logger.info("[NOTIFICATION] Pushed commits notification created and saved: '{}' by {}", title, sender.name)
    }
}
