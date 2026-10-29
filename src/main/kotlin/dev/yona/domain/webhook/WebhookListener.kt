package dev.yona.domain.webhook

import dev.yona.domain.board.PostingCommentActivity
import dev.yona.domain.board.PostingCreatedActivity
import dev.yona.domain.comment.ReviewCommentedActivity
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.issue.IssueChangedActivity
import dev.yona.domain.issue.IssueCommentActivity
import dev.yona.domain.issue.IssueCreatedActivity
import dev.yona.domain.issue.IssueDeletedActivity
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.notification.CommitsPushedActivity
import dev.yona.domain.pullrequest.PullRequestChangedActivity
import dev.yona.domain.pullrequest.PullRequestCreatedActivity
import dev.yona.domain.pullrequest.PullRequestReviewedActivity
import dev.yona.domain.comment.ReviewLinks
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 애플리케이션 활동을 받아 웹훅을 보낸다. v1.6에서는 `NotificationEvent`의 `after…`가 알림을 저장하면서 `webhookRequest…`로 함께 보냈다.
 * 알림 수신자가 없어도 웹훅은 보낸다. 웹훅을 보내다 실패해도 요청은 그대로 진행한다.
 *
 * v1.6에서 웹훅을 보내는 이벤트와 같게 보낸다.
 * - 이슈: 새 이슈, 상태, 담당자, 본문, 마일스톤 변경, 삭제, 다른 프로젝트로 이동. 라벨과 공유자 변경은 보내지 않는다.
 * - 새 글, 새 댓글과 댓글 수정(이슈와 글)
 * - 풀 리퀘스트: 새 풀 리퀘스트, 병합됨, 리뷰 상태 변경, 새 리뷰 댓글. 닫기와 다시 열기, 새 커밋은 보내지 않는다([refactoring-backlog.md] R-45).
 * - 푸시
 *
 * v1.6은 보낸 사람으로 요청을 보낸 현재 사용자를 썼다. 병합 검사처럼 요청 밖에서 도는 일에는 현재 사용자가 없으므로 활동을 일으킨 사용자를 쓴다([decisions.md] A-81).
 */
@Component
class WebhookListener(
    private val service: WebhookService,
    private val issues: IssueRepository,
    private val links: ReviewLinks
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private fun safely(description: String, block: () -> Unit) {
        try { block() } catch (e: Exception) { log.warn("웹훅을 보내지 못했다: {}", description, e) }
    }

    @EventListener
    fun onIssueCreated(activity: IssueCreatedActivity) = safely("새 이슈") { service.issue(EventType.NEW_ISSUE, activity.actor, activity.issue) }

    @EventListener
    fun onIssueChanged(activity: IssueChangedActivity) = safely("이슈 변경 ${activity.type}") {
        val actor = activity.actor ?: return@safely
        when (activity.type) {
            EventType.ISSUE_STATE_CHANGED, EventType.ISSUE_ASSIGNEE_CHANGED, EventType.ISSUE_BODY_CHANGED, EventType.ISSUE_MILESTONE_CHANGED ->
                service.issue(activity.type, actor, activity.issue)
            EventType.ISSUE_MOVED -> service.issueMoved(activity.type, actor, activity.issue, activity.oldValue.orEmpty().substringAfter('/'))
            else -> Unit
        }
    }

    @EventListener
    fun onIssueDeleted(activity: IssueDeletedActivity) = safely("이슈 삭제") {
        issues.findByProjectIdAndNumber(activity.projectId, activity.issueNumber)?.let { service.issue(EventType.RESOURCE_DELETED, activity.actor, it) }
    }

    @EventListener
    fun onPostingCreated(activity: PostingCreatedActivity) = safely("새 글") { service.posting(EventType.NEW_POSTING, activity.actor, activity.posting) }

    @EventListener
    fun onIssueComment(activity: IssueCommentActivity) = safely("이슈 댓글") {
        service.issueComment(if (activity.created) EventType.NEW_COMMENT else EventType.COMMENT_UPDATED, activity.actor, activity.comment)
    }

    @EventListener
    fun onPostingComment(activity: PostingCommentActivity) = safely("글 댓글") {
        service.postingComment(if (activity.created) EventType.NEW_COMMENT else EventType.COMMENT_UPDATED, activity.actor, activity.comment)
    }

    @EventListener
    fun onPullRequestCreated(activity: PullRequestCreatedActivity) = safely("새 풀 리퀘스트") {
        service.pullRequest(EventType.NEW_PULL_REQUEST, activity.actor, activity.pullRequest)
    }

    @EventListener
    fun onPullRequestChanged(activity: PullRequestChangedActivity) = safely("풀 리퀘스트 변경 ${activity.type}") {
        if (activity.type == EventType.PULL_REQUEST_STATE_CHANGED && activity.newValue == "merged") {
            service.pullRequest(EventType.PULL_REQUEST_MERGED, activity.actor, activity.pullRequest)
        }
        // 추가 항목(X-14): 담당자 변경은 이슈와 같이 웹훅을 보낸다. 라벨 변경은 보내지 않는다(v1.6 이슈 라벨 변경과 같다).
        if (activity.type == EventType.PULL_REQUEST_ASSIGNEE_CHANGED) {
            service.pullRequest(EventType.PULL_REQUEST_ASSIGNEE_CHANGED, activity.actor, activity.pullRequest)
        }
    }

    @EventListener
    fun onPullRequestReviewed(activity: PullRequestReviewedActivity) = safely("풀 리퀘스트 리뷰") {
        service.pullRequestReview(EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, activity.actor, activity.pullRequest, activity.action)
    }

    @EventListener
    fun onReviewCommented(activity: ReviewCommentedActivity) = safely("리뷰 댓글") {
        val pullRequest = activity.pullRequest ?: return@safely
        service.pullRequestComment(EventType.NEW_REVIEW_COMMENT, activity.actor, pullRequest, links.relativeComment(activity.comment))
    }

    @EventListener
    fun onCommitsPushed(activity: CommitsPushedActivity) = safely("푸시") { service.push(activity.project, activity.sender, activity.commits, activity.refNames) }
}
