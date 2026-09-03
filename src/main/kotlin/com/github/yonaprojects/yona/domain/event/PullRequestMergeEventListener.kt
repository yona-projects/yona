package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEvent
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEventRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestService
import com.github.yonaprojects.yona.domain.user.User
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class PullRequestMergeEventListener(
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val issueRepository: IssueRepository,
    private val issueService: IssueService,
    private val pullRequestService: PullRequestService,
    private val notificationEventRecorder: NotificationEventRecorder,
    private val eventPublisher: ApplicationEventPublisher,
    private val pullRequestEventRepository: PullRequestEventRepository,
    // yona-wiki P3-01(Observability) 계측 지점 5 대응.
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(PullRequestMergeEventListener::class.java)

    // 이슈 자동 닫기 정규식 패턴 (대소문자 구분 없이 close(s/d), fix(es/ed), resolve(s/d) #숫자)
    private val closePattern = "(?i)(?:close[s|d]?|fix[e[s|d]]?|resolve[s|d]?)\\s+#(\\d+)".toRegex()

    @Async("taskExecutor")
    @EventListener
    @Transactional
    fun handlePullRequestMergeEvent(event: PullRequestMergeEvent) {
        logger.info("Handling PullRequestMergeEvent asynchronously for PR ID: ${event.pullRequestId} by user: ${event.sender.loginId}")
        meterRegistry.counter("yona.pullrequest.merge.processed", "trigger", "direct").increment()

        val pullRequestOptional = pullRequestRepository.findById(event.pullRequestId)
        if (!pullRequestOptional.isPresent) {
            logger.warn("PullRequest with ID ${event.pullRequestId} not found")
            return
        }

        val pullRequest = pullRequestOptional.get()
        val oldState = pullRequest.state
        pullRequest.isMerging = true
        pullRequest.state = State.MERGED
        pullRequestRepository.save(pullRequest)

        pullRequestEventRepository.save(
            PullRequestEvent(
                pullRequest = pullRequest,
                senderLoginId = event.sender.loginId,
                eventType = EventType.PULL_REQUEST_STATE_CHANGED,
                oldValue = oldState.toString(),
                newValue = State.MERGED.toString(),
                created = Instant.now()
            )
        )

        logger.info("[PR MERGE] Successfully updated merge state for PR ID: ${event.pullRequestId}")

        try {
            closeReferredIssues(pullRequest, event.sender.loginId)
        } catch (e: Exception) {
            logger.error("Failed to automatically close issues for PR ID: ${event.pullRequestId}", e)
        }
    }

    fun closeReferredIssues(pullRequest: PullRequest, senderLoginId: String) {
        val project = pullRequest.toProject
        val textsToSearch = mutableListOf<String>()

        textsToSearch.add(pullRequest.title)
        pullRequest.body?.let { textsToSearch.add(it) }

        val commits = pullRequestCommitRepository.findByPullRequest(pullRequest)
        for (commit in commits) {
            textsToSearch.add(commit.commitMessage)
        }

        val issueNumbers = mutableSetOf<Long>()
        for (text in textsToSearch) {
            // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md [i] 참고): closePattern("#(\d+)")의 캡처
            // 그룹이 선택적(optional)이 아니라 매치가 성립하면 그룹1도 항상 캡처되므로 아래 null 체크들은
            // 성립할 수 없다.
            closePattern.findAll(text).forEach { matchResult ->
                val numberStr = matchResult.groups[1]?.value
                if (numberStr != null) {
                    numberStr.toLongOrNull()?.let { issueNumbers.add(it) }
                }
            }
        }

        if (issueNumbers.isEmpty()) {
            return
        }

        logger.info("[PR MERGE] Found referred issue numbers to close: $issueNumbers for PR ID: ${pullRequest.id}")

        for (number in issueNumbers) {
            val issue = issueRepository.findByProjectAndNumber(project, number)
            if (issue != null) {
                if (issue.state != State.CLOSED) {
                    logger.info("[PR MERGE] Automatically closing issue #${issue.number} (ID: ${issue.id})")
                    issueService.changeState(issue.id!!, State.CLOSED, senderLoginId)
                } else {
                    logger.info("[PR MERGE] Issue #${issue.number} is already CLOSED")
                }
            } else {
                logger.warn("[PR MERGE] Issue #${number} not found in project: ${project.name}")
            }
        }
    }

    // yona actors/RelatedPullRequestMergingActor.java + PullRequestActor.processPullRequestMerging 대응.
    // 다른 PR과 관련된 브랜치에 push가 발생했을 때, 관련 PR들의 병합/충돌 상태를 다시 검사한다.
    // 이전에는 isMerging=true만 세팅하고 실제 재검사를 하지 않아 상태가 영구히 "병합중"으로
    // 멈춰있던 버그였다(P1-05).
    @Async("taskExecutor")
    @EventListener
    @Transactional
    fun handleRelatedPullRequestMergeEvent(event: RelatedPullRequestMergeEvent) {
        logger.info("Handling RelatedPullRequestMergeEvent asynchronously for project: ${event.project.name}, branch: ${event.branch}")

        val relatedPullRequests = pullRequestRepository.findRelatedPullRequests(event.project, event.branch)
        for (pullRequest in relatedPullRequests) {
            val id = pullRequest.id ?: continue
            val wasConflict = pullRequest.isConflict ?: false

            pullRequest.isMerging = true
            pullRequestRepository.save(pullRequest)
            meterRegistry.counter("yona.pullrequest.merge.processed", "trigger", "related").increment()

            val sample = Timer.start(meterRegistry)
            try {
                // yona RelatedPullRequestMergingActor도 processPullRequestMerging()을 거치므로,
                // 새 커밋 발견 시 PullRequestCommit 영속화/PullRequestEvent 기록/리뷰어 초기화/알림
                // 발행, diff 소멸 시 자동 MERGED 전환까지 processMergeCheck()가 모두 수행한다(P1-52).
                pullRequestService.processMergeCheck(id, event.sender, isNewPullRequest = false)
            } catch (e: Exception) {
                logger.error("[PR MERGE] Failed to re-check merge for related PR ID: $id", e)
            } finally {
                sample.stop(meterRegistry.timer("yona.pullrequest.merge.check.duration"))
            }

            val refreshed = pullRequestRepository.findById(id).orElse(pullRequest)
            val isConflictNow = refreshed.isConflict ?: false

            if (wasConflict != isConflictNow) {
                notifyConflictStateChanged(refreshed, event.sender, isConflictNow)
            }

            refreshed.isMerging = false
            pullRequestRepository.save(refreshed)
        }

        logger.info("[PR MERGE] Successfully re-checked ${relatedPullRequests.size} related PRs for project: ${event.project.name}, branch: ${event.branch}")
    }

    private fun notifyConflictStateChanged(
        pullRequest: PullRequest,
        sender: User,
        isConflictNow: Boolean
    ) {
        meterRegistry.counter(
            "yona.pullrequest.conflict.transition",
            "direction", if (isConflictNow) "into_conflict" else "resolved"
        ).increment()

        val stateLabel = if (isConflictNow) "충돌이 발생했습니다" else "충돌이 해소되었습니다"
        val title = "[${pullRequest.toProject.name}] PR #${pullRequest.number} 관련 브랜치 변경으로 $stateLabel"

        val notificationEvent = NotificationEvent(
            title = title,
            senderId = sender.id,
            created = Instant.now(),
            resourceType = ResourceType.PULL_REQUEST,
            resourceId = pullRequest.id.toString(),
            eventType = EventType.PULL_REQUEST_MERGED,
            newValue = title,
            receivers = mutableSetOf(pullRequest.contributor).apply { removeIf { it.id == sender.id } }
        )
        notificationEventRecorder.record(notificationEvent)?.let { eventPublisher.publishEvent(it) }

        pullRequestEventRepository.save(
            PullRequestEvent(
                pullRequest = pullRequest,
                senderLoginId = sender.loginId,
                eventType = EventType.PULL_REQUEST_MERGED,
                oldValue = (!isConflictNow).toString(),
                newValue = isConflictNow.toString(),
                created = Instant.now()
            )
        )
    }
}
