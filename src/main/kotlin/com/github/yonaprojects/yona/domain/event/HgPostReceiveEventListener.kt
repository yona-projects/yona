package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.IssueEvent
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.issue.IssueReferenceParser
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.vcs.HgBookmarkAncestry
import com.github.yonaprojects.yona.domain.vcs.HgBookmarkChangeType
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.webhook.PushedHgCommits
import com.github.yonaprojects.yona.domain.webhook.WebhookService
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.api.HgCommit as NativeHgCommit
import io.github.search5.hg4j.lib.HgRepository as NativeHgRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.time.Instant

// GitPostReceiveEventListener의 Mercurial 대응 — 최종적으로 호출하는 서비스
// (NotificationEventRecorder/WebhookService/WatchService)는 git 쪽과 동일한 것을 재사용한다.
// 다른 점은 "새로 들여온 커밋 목록을 어떻게 계산하는가"(JGit RevWalk 대신 HgBookmarkAncestry의
// 저수준 Revlog 조상 판정)와 "웹훅 페이로드를 어떤 값 객체로 감싸는가"(PushedCommits 대신
// PushedHgCommits) 뿐이다.
@Component
class HgPostReceiveEventListener(
    private val repositoryService: RepositoryService,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val issueRepository: IssueRepository,
    private val issueEventRepository: IssueEventRepository,
    private val webhookService: WebhookService,
    private val watchService: WatchService,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(HgPostReceiveEventListener::class.java)

    @Async("taskExecutor")
    @EventListener
    @Transactional
    fun handleHgPostReceiveEvent(event: HgPostReceiveEvent) {
        logger.info(
            "Handling HgPostReceiveEvent asynchronously for project: ${event.project.name} bookmark: ${event.move.name} by user: ${event.user.loginId}"
        )

        // git의 isNewOrUpdateCommand() 대응 — 삭제는 새로 알릴 커밋이 없다.
        if (event.move.changeType == HgBookmarkChangeType.DELETE) {
            return
        }

        val playRepo = repositoryService.getRepository(event.project)
        val repoDir = playRepo.getDirectory()
        if (!repoDir.exists()) {
            logger.warn("Repository directory does not exist: ${repoDir.absolutePath}")
            return
        }

        val commits: List<NativeHgCommit> = try {
            parseCommitsFrom(repoDir, event.move.oldNodeHex, event.move.newNodeHex)
        } catch (e: Exception) {
            logger.error("Failed to parse commits from Mercurial repository", e)
            return
        }

        logger.info("Parsed ${commits.size} commits from bookmark: ${event.move.name}")

        val refName = "refs/heads/${event.move.name}"
        processCommitsNotification(commits, listOf(refName), event.project, event.user)
        processIssueReferredFromCommit(commits, event.project, event.user)
    }

    // git RevWalk(markStart(newId)/markUninteresting(oldId))와 동일한 목적 — Wire2Commands.
    // changelog()와 동일한 저수준 Revlog(00changelog.i/.d)로 실제 부모 사슬을 따라간다(hg4j 포셀린
    // HgCommit에는 부모 리비전이 노출되지 않으므로).
    private fun parseCommitsFrom(repoDir: File, oldHex: String, newHex: String): List<NativeHgCommit> {
        val nativeRepo = NativeHgRepository(repoDir)
        try {
            val revlog = HgBookmarkAncestry.changelogRevlogOf(nativeRepo)
            val newRevisions = HgBookmarkAncestry.newlyIntroducedRevisions(revlog, oldHex, newHex)
            if (newRevisions.isEmpty()) return emptyList()

            return Hg.open(repoDir).use { hg ->
                val byRevision = hg.log().call().associateBy { it.revision }
                // git 쪽과 동일하게 최신 커밋이 먼저 오도록(RevWalk의 기본 순회 순서) 내림차순 정렬.
                newRevisions.sortedDescending().mapNotNull { byRevision[it] }
            }
        } finally {
            nativeRepo.close()
        }
    }

    // yona actors/CommitsNotificationActor.java 대응 (git GitPostReceiveEventListener와 동일한 로직).
    internal fun processCommitsNotification(commits: List<NativeHgCommit>, refNames: List<String>, project: Project, sender: User) {
        if (commits.isEmpty()) return

        val title = if (refNames.size == 1) {
            "[${project.name}] ${commits.size}개의 커밋이 ${refNames[0]} 브랜치로 푸시되었습니다."
        } else {
            "[${project.name}] ${commits.size}개의 커밋이 푸시되었습니다."
        }

        val notificationEvent = NotificationEvent(
            title = title,
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.COMMIT,
            resourceId = commits.first().nodeId.toHex(),
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
        logger.info("[NOTIFICATION] Pushed Hg commits notification created and saved: '$title' by ${sender.name}")

        webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, PushedHgCommits(commits, refNames))
    }

    // yona actors/IssueReferredFromCommitEventActor.java 대응.
    private fun processIssueReferredFromCommit(commits: List<NativeHgCommit>, project: Project, sender: User) {
        for (commit in commits) {
            recordReferredIssues(commit.message ?: "", commit.nodeId.toHex(), project, sender)
        }
    }

    internal fun recordReferredIssues(commitMessage: String, commitId: String, project: Project, sender: User) {
        val issueNumbers = IssueReferenceParser.findReferredIssueNumbers(commitMessage)
        for (number in issueNumbers) {
            val issue = issueRepository.findByProjectAndNumber(project, number) ?: continue

            val issueEvent = IssueEvent(
                issue = issue,
                senderLoginId = sender.loginId,
                senderEmail = sender.email,
                newValue = commitId,
                created = Instant.now(),
                eventType = EventType.ISSUE_REFERRED_FROM_COMMIT
            )
            issueEventRepository.save(issueEvent)
            logger.info("[ISSUE REFER] Recorded issue #$number referred from Hg commit $commitId")
        }
    }
}
