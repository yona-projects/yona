package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.project.GitService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.issue.IssueEvent
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.issue.IssueReferenceParser
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.webhook.WebhookService
import com.github.yonaprojects.yona.domain.webhook.PushedCommits
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.ReceiveCommand
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.io.IOException
import java.time.Instant

@Component
class GitPostReceiveEventListener(
    private val gitService: GitService,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val issueRepository: IssueRepository,
    private val issueEventRepository: IssueEventRepository,
    private val webhookService: WebhookService,
    private val watchService: WatchService,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(GitPostReceiveEventListener::class.java)

    @Async("taskExecutor")
    @EventListener
    @Transactional
    fun handleGitPostReceiveEvent(event: GitPostReceiveEvent) {
        logger.info("Handling GitPostReceiveEvent asynchronously for project: ${event.project.name} by user: ${event.user.loginId}")
        
        val commits = mutableListOf<RevCommit>()
        val refNames = mutableListOf<String>()

        val repoDir = gitService.getRepositoryPath(event.project.owner ?: "", event.project.name)
        if (!repoDir.exists()) {
            logger.warn("Repository directory does not exist: ${repoDir.absolutePath}")
            return
        }

        try {
            val repository = RepositoryBuilder().setGitDir(repoDir).build()
            repository.use { repo ->
                for (command in event.commands) {
                    if (isNewOrUpdateCommand(command)) {
                        commits.addAll(parseCommitsFrom(command, repo))
                        refNames.add(command.refName)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse commits from git repository", e)
            return
        }

        logger.info("Parsed ${commits.size} commits from ref: $refNames")

        // 1. CommitsNotificationActor 로직 (신규 커밋 알림)
        processCommitsNotification(commits, refNames, event.project, event.user)

        // 2. IssueReferredFromCommitEventActor 로직 (이슈 언급 처리)
        processIssueReferredFromCommit(commits, event.project, event.user)
    }

    private fun isNewOrUpdateCommand(command: ReceiveCommand): Boolean {
        return command.type == ReceiveCommand.Type.CREATE ||
                command.type == ReceiveCommand.Type.UPDATE ||
                command.type == ReceiveCommand.Type.UPDATE_NONFASTFORWARD
    }

    private fun parseCommitsFrom(command: ReceiveCommand, repository: Repository): Collection<RevCommit> {
        val list = mutableListOf<RevCommit>()
        try {
            val endRange = command.newId
            val startRange = command.oldId

            RevWalk(repository).use { rw ->
                rw.markStart(rw.parseCommit(endRange))
                if (startRange.equals(ObjectId.zeroId())) {
                    list.add(rw.parseCommit(endRange))
                    return list
                } else {
                    rw.markUninteresting(rw.parseCommit(startRange))
                }

                for (rev in rw) {
                    list.add(rev)
                }
            }
        } catch (e: IOException) {
            logger.error("Failed to walk commits in Repository", e)
        }
        return list
    }

    internal fun processCommitsNotification(commits: List<RevCommit>, refNames: List<String>, project: Project, sender: User) {
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
            resourceId = commits.first().name,
            eventType = EventType.NEW_COMMIT,
            newValue = title
        )

        // yona NotificationEvent(push 메일 경로) 대응. 수신자를 계산해야
        // NotificationEventRecorder가 NotificationMail 대기열에 올리고, WebhookNotificationEventListener가
        // publish된 이벤트를 구독해 웹훅도 즉시 보낼 수 있다.
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
        logger.info("[NOTIFICATION] Pushed commits notification created and saved: '$title' by ${sender.name}")

        // yona Webhook.sendRequestToPayloadUrl(commits, refNames, sender) 대응.
        // 커밋은 DB 엔티티가 아니라 NotificationEvent.resourceId(커밋 SHA)만으로는 프로젝트를 되짚어
        // 재조회할 수 없으므로, WebhookNotificationEventListener(비동기, resourceId 기반 재조회)를
        // 거치지 않고 project/commits를 이미 들고 있는 이 지점에서 직접 웹훅을 보낸다.
        webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, PushedCommits(commits, refNames))
    }

    // yona actors/IssueReferredFromCommitEventActor.java 대응.
    private fun processIssueReferredFromCommit(commits: List<RevCommit>, project: Project, sender: User) {
        for (commit in commits) {
            recordReferredIssues(commit.fullMessage, commit.name, project, sender)
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
            logger.info("[ISSUE REFER] Recorded issue #$number referred from commit $commitId")
        }
    }
}
