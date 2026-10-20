package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.CommitIssueReferenceService
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommit
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommits
import com.github.yonaprojects.yona.domain.webhook.WebhookService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.tmatesoft.svn.core.SVNLogEntry
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import java.io.File
import java.time.Instant

// GitPostReceiveEventListener/HgPostReceiveEventListener의 SVN 대응. 같은 알림·이슈 참조·웹훅 경로를
// 쓰되, SVN revision 번호는 프로젝트마다 겹치므로 알림은 COMMIT이 아니라 PROJECT를 대상으로 한다
// (NotificationUrlResolver가 NEW_COMMIT을 프로젝트 커밋 목록으로 연결한다).
@Component
class SvnPostCommitEventListener(
    @Value("\${yona.svn.base-dir:/tmp/yona/svn}") private val baseDir: String,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val commitIssueReferenceService: CommitIssueReferenceService,
    private val webhookService: WebhookService,
    private val watchService: WatchService,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(SvnPostCommitEventListener::class.java)

    @Async("taskExecutor")
    @EventListener
    @Transactional
    fun handleSvnPostCommitEvent(event: SvnPostCommitEvent) {
        val entries = try {
            readLog(event.project, event.fromRevision, event.toRevision)
        } catch (e: Exception) {
            logger.error("Failed to read SVN revisions r${event.fromRevision}-r${event.toRevision} of ${event.project.owner}/${event.project.name}", e)
            return
        }
        if (entries.isEmpty()) return
        processCommitsNotification(entries, event.project, event.user)
        for (entry in entries) {
            commitIssueReferenceService.record(event.project, event.user, entry.revision.toString(), entry.message ?: "")
        }
    }

    private fun readLog(project: Project, from: Long, to: Long): List<SVNLogEntry> {
        FSRepositoryFactory.setup()
        val repository = SVNRepositoryFactory.create(SVNURL.fromFile(File(baseDir, "${project.owner}/${project.name}")))
        try {
            val entries = mutableListOf<SVNLogEntry>()
            repository.log(arrayOf(""), from, to, false, false) { entries.add(it) }
            // Newest first, like the RevWalk order used for Git pushes.
            return entries.sortedByDescending { it.revision }
        } finally {
            repository.closeSession()
        }
    }

    internal fun processCommitsNotification(entries: List<SVNLogEntry>, project: Project, sender: User) {
        val title = "[${project.name}] ${entries.size}개의 커밋이 푸시되었습니다."
        val notificationEvent = NotificationEvent(
            title = title,
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.PROJECT,
            resourceId = project.id.toString(),
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

        val commits = entries.map {
            PushedVcsCommit(
                id = it.revision.toString(),
                message = it.message ?: "",
                authorName = it.author ?: "",
                authorEmail = null,
                timestamp = it.date?.toInstant() ?: Instant.now()
            )
        }
        webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, PushedVcsCommits(commits, emptyList()))
    }
}
