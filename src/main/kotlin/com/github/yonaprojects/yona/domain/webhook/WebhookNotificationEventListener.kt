package com.github.yonaprojects.yona.domain.webhook

import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * yona의 NotificationEvent.webhookRequest(...)에 대응.
 * 이슈/댓글 등 도메인 서비스가 이미 publish하고 있던 NotificationEvent를
 * 구독해 해당 프로젝트에 등록된 웹훅으로 실제 전송한다(이전에는 아무도 구독하지 않아 미발송이었음).
 *
 * PULL_REQUEST는 P1-26에서 지원 추가됨(WebhookServiceImpl.buildPayload/getResourceType 포함).
 * REVIEW_COMMENT/COMMIT_COMMENT는 P1-69에서 지원 추가됨(yona
 * NotificationEvent.java:756 webhookRequest(NEW_REVIEW_COMMENT, ...)/:780 webhookRequest(NEW_COMMENT, ...) 대응).
 * 그 외 아직 payload를 만들 수 없는 리소스 타입(예: COMMIT — P1-25에서 별도 직접 경로로 처리됨)은
 * 조용히 스킵한다.
 */
@Component
class WebhookNotificationEventListener(
    private val webhookService: WebhookService,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val commitCommentRepository: CommitCommentRepository
) {
    private val logger = LoggerFactory.getLogger(WebhookNotificationEventListener::class.java)

    @Async("taskExecutor")
    @EventListener
    @Transactional(readOnly = true)
    fun handleNotificationEvent(event: NotificationEvent) {
        val sender = event.senderId?.let { userRepository.findById(it).orElse(null) } ?: return
        val (project, resource) = resolveResource(event.resourceType, event.resourceId) ?: run {
            logger.debug("웹훅 대상 리소스를 찾지 못해 스킵: resourceType=${event.resourceType}, resourceId=${event.resourceId}")
            return
        }

        webhookService.sendWebhook(project, event.eventType, sender, resource)
    }

    private fun resolveResource(resourceType: ResourceType, resourceId: String): Pair<Project, Any>? {
        val id = resourceId.toLongOrNull() ?: return null
        return when (resourceType) {
            ResourceType.ISSUE_POST ->
                issueRepository.findById(id).orElse(null)?.let { it.project to it }

            ResourceType.BOARD_POST ->
                postingRepository.findById(id).orElse(null)?.let { it.project to it }

            ResourceType.ISSUE_COMMENT ->
                issueCommentRepository.findById(id).orElse(null)?.let { it.issue.project to it }

            ResourceType.NONISSUE_COMMENT ->
                postingCommentRepository.findById(id).orElse(null)?.let { it.posting.project to it }

            ResourceType.PULL_REQUEST ->
                pullRequestRepository.findById(id).orElse(null)?.let { it.toProject to it }

            ResourceType.REVIEW_COMMENT ->
                reviewCommentRepository.findById(id).orElse(null)?.let { comment ->
                    comment.thread?.project?.let { project -> project to comment }
                }

            ResourceType.COMMIT_COMMENT ->
                commitCommentRepository.findById(id).orElse(null)?.let { comment ->
                    comment.project?.let { project -> project to comment }
                }

            else -> null
        }
    }
}
