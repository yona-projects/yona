package dev.yona.domain.webhook

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.PullRequestReviewAction
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.WebhookType
import dev.yona.domain.issue.Issue
import dev.yona.domain.project.Project
import dev.yona.domain.project.Webhook
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.user.User
import dev.yona.repository.PostReceiveJobs
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 웹훅의 등록, 삭제와 이벤트별 전송을 담당한다. v1.6 `Webhook.create`, `delete`, `sendRequestToPayloadUrl…`와
 * `NotificationEvent.webhookRequest…`(`app/models/NotificationEvent.java:601-681`)에 해당한다.
 *
 * 전송은 프로젝트의 모든 웹훅에 한다. 지속적 통합 도구용(`JSON`) 웹훅은 푸시 이벤트만 받고, 다른 형식은 푸시 이벤트를 "Git 푸시 포함"으로 켠 경우만 받는다.
 * 구글 챗 형식은 같은 리소스의 알림이 한 스레드로 묶이도록 저장된 스레드 이름을 본문에 넣는다.
 */
@Service
class WebhookService(
    private val webhooks: WebhookRepository,
    private val threads: WebhookThreadRepository,
    private val projects: ProjectRepository,
    private val payloads: WebhookPayloads,
    private val sender: WebhookSender
) {

    @Transactional(readOnly = true)
    fun list(project: Project): List<Webhook> = webhooks.findByProjectId(requireNotNull(project.id))

    /**
     * 웹훅을 등록한다. 보내는 곳이 비어 있으면 아무것도 만들지 않는다. 토큰이 없으면 빈 문자열, "Git 푸시 포함"이 없으면 `false`, 형식이 없으면 `SIMPLE`이다
     * (`JSON` 형식은 화면이 "Git 푸시 포함"을 켜 둔다).
     */
    @Transactional
    fun create(project: Project, payloadUrl: String, secret: String?, gitPush: Boolean?, type: WebhookType?): Webhook =
        webhooks.save(Webhook(project = project, payloadUrl = payloadUrl, secret = secret ?: "", gitPush = gitPush ?: false, webhookType = type ?: WebhookType.SIMPLE, createdAt = Date()))

    /** 프로젝트에 속한 웹훅을 지운다. 없으면 `false`이다. */
    @Transactional
    fun delete(project: Project, id: Long): Boolean {
        val webhook = webhooks.findByIdAndProjectId(id, requireNotNull(project.id)) ?: return false
        webhooks.delete(webhook)
        return true
    }

    // --- 이슈 ---

    @Transactional(readOnly = true)
    fun issue(eventType: EventType, sender: User, issue: Issue) = each(requireNotNull(issue.project)) { hook ->
        val type = hook.webhookType
        val message = payloads.issueMessage(type, eventType, sender, issue)
        when (type) {
            WebhookType.DETAIL_SLACK -> deliver(hook, payloads.withAttachments(message, payloads.issueAttachments(issue, eventType)))
            WebhookType.DETAIL_HANGOUT_CHAT -> threaded(hook, message, ThreadTarget(ResourceType.ISSUE_POST, requireNotNull(issue.id).toString()))
            else -> deliver(hook, payloads.textOnly(message))
        }
    }

    /** 이슈를 다른 프로젝트로 옮겼을 때이다. [previous]는 옮기기 전 프로젝트 이름이다. */
    @Transactional(readOnly = true)
    fun issueMoved(eventType: EventType, sender: User, issue: Issue, previous: String) = each(requireNotNull(issue.project)) { hook ->
        val type = hook.webhookType
        val message = payloads.issueMovedMessage(type, sender, issue, previous)
        when (type) {
            WebhookType.DETAIL_SLACK -> deliver(hook, payloads.withAttachments(message, payloads.issueAttachments(issue, eventType)))
            WebhookType.DETAIL_HANGOUT_CHAT -> threaded(hook, message, ThreadTarget(ResourceType.ISSUE_POST, requireNotNull(issue.id).toString()))
            else -> deliver(hook, payloads.textOnly(message))
        }
    }

    // --- 글, 댓글 ---

    @Transactional(readOnly = true)
    fun posting(eventType: EventType, sender: User, posting: Posting) = each(requireNotNull(posting.project)) { hook ->
        val message = payloads.postingMessage(hook.webhookType, eventType, sender, posting)
        if (hook.webhookType == WebhookType.DETAIL_HANGOUT_CHAT) threaded(hook, message, ThreadTarget(ResourceType.BOARD_POST, requireNotNull(posting.id).toString()))
        else deliver(hook, payloads.textOnly(message))
    }

    @Transactional(readOnly = true)
    fun issueComment(eventType: EventType, sender: User, comment: IssueComment) {
        val issue = requireNotNull(comment.issue)
        each(requireNotNull(issue.project)) { hook ->
            comment(hook, payloads.issueCommentMessage(hook.webhookType, eventType, sender, comment), comment.contents, eventType, ThreadTarget(ResourceType.ISSUE_POST, requireNotNull(issue.id).toString()))
        }
    }

    @Transactional(readOnly = true)
    fun postingComment(eventType: EventType, sender: User, comment: PostingComment) {
        val posting = requireNotNull(comment.posting)
        each(requireNotNull(posting.project)) { hook ->
            comment(hook, payloads.postingCommentMessage(hook.webhookType, eventType, sender, comment), comment.contents, eventType, ThreadTarget(ResourceType.BOARD_POST, requireNotNull(posting.id).toString()))
        }
    }

    private fun comment(hook: Webhook, message: String, contents: String?, eventType: EventType, target: ThreadTarget) {
        when (hook.webhookType) {
            WebhookType.DETAIL_SLACK -> deliver(hook, payloads.withAttachments(message, payloads.commentAttachments(contents, eventType)))
            WebhookType.DETAIL_HANGOUT_CHAT -> threaded(hook, message, target)
            else -> deliver(hook, payloads.textOnly(message))
        }
    }

    // --- 풀 리퀘스트 ---

    @Transactional(readOnly = true)
    fun pullRequest(eventType: EventType, sender: User, pullRequest: PullRequest) = each(requireNotNull(pullRequest.toProject)) { hook ->
        val message = payloads.pullRequestMessage(hook.webhookType, eventType, sender, pullRequest)
        pullRequestBody(hook, message, pullRequest, eventType)
    }

    @Transactional(readOnly = true)
    fun pullRequestReview(eventType: EventType, sender: User, pullRequest: PullRequest, action: PullRequestReviewAction) = each(requireNotNull(pullRequest.toProject)) { hook ->
        pullRequestBody(hook, payloads.pullRequestReviewMessage(hook.webhookType, sender, pullRequest, action), pullRequest, eventType)
    }

    @Transactional(readOnly = true)
    fun pullRequestComment(eventType: EventType, sender: User, pullRequest: PullRequest, commentUrl: String) = each(requireNotNull(pullRequest.toProject)) { hook ->
        pullRequestBody(hook, payloads.pullRequestCommentMessage(sender, pullRequest, commentUrl), pullRequest, eventType)
    }

    private fun pullRequestBody(hook: Webhook, message: String, pullRequest: PullRequest, eventType: EventType) {
        when (hook.webhookType) {
            WebhookType.DETAIL_SLACK -> deliver(hook, payloads.withAttachments(message, payloads.pullRequestAttachments(pullRequest, eventType)))
            WebhookType.DETAIL_HANGOUT_CHAT -> threaded(hook, message, ThreadTarget(ResourceType.PULL_REQUEST, requireNotNull(pullRequest.id).toString()))
            else -> deliver(hook, payloads.textOnly(message))
        }
    }

    // --- 푸시 ---

    /** 푸시 이벤트이다. `JSON` 형식은 커밋 내용을, 푸시를 켠 다른 형식은 한 줄 글을 받는다. */
    @Transactional(readOnly = true)
    fun push(project: Project, sender: User, commits: List<PostReceiveJobs.CommitDetail>, refNames: List<String>) {
        for (hook in webhooks.findByProjectId(requireNotNull(project.id))) {
            if (hook.webhookType == WebhookType.JSON) {
                if (commits.isNotEmpty()) this.sender.send(hook, payloads.pushJson(project, commits, refNames, sender))
            } else if (hook.gitPush == true && refNames.isNotEmpty()) {
                this.sender.send(hook, payloads.textOnly(payloads.pushMessage(project, commits.size, refNames)))
            }
        }
    }

    // --- 공통 ---

    /** `JSON` 형식을 뺀 프로젝트의 웹훅마다 [action]을 한다. */
    private fun each(project: Project, action: (Webhook) -> Unit) {
        for (hook in webhooks.findByProjectId(requireNotNull(project.id))) {
            if (hook.webhookType != WebhookType.JSON) action(hook)
        }
    }

    private fun deliver(hook: Webhook, body: String) = sender.send(hook, body)

    private fun threaded(hook: Webhook, message: String, target: ThreadTarget) {
        val threadId = threads.findFirstByWebhookIdAndResourceTypeAndResourceId(requireNotNull(hook.id), target.type, target.id)?.threadId
        sender.send(hook, payloads.withThread(message, threadId), target)
    }
}
