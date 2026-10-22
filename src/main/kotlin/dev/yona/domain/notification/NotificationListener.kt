package dev.yona.domain.notification

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingCommentActivity
import dev.yona.domain.board.PostingCreatedActivity
import dev.yona.domain.board.PostingDeletedActivity
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.board.PostingUpdatedActivity
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.RequestState
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueChangedActivity
import dev.yona.domain.issue.IssueCommentActivity
import dev.yona.domain.issue.IssueCreatedActivity
import dev.yona.domain.issue.IssueDeletedActivity
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestChangedActivity
import dev.yona.domain.pullrequest.PullRequestCommitRepository
import dev.yona.domain.pullrequest.PullRequestCreatedActivity
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 애플리케이션 활동(D-14)을 받아 알림 이벤트를 만든다. v1.6 `NotificationEvent`의 `after…` 메서드들이 하던 일이다. 활동을 일으킨 요청과 같은
 * 스레드, 같은 트랜잭션에서 실행한다(v1.6도 요청 안에서 이벤트를 저장하고 메일은 스케줄러가 나중에 보낸다).
 *
 * 제목은 새 글이면 `[프로젝트] 제목 (번호)`, 답이나 변경이면 앞에 `Re: `를 붙인다. 이슈 번호에는 `#`이 붙는다. 웹훅 호출은 5단계에서 더한다.
 */
@Component
class NotificationListener(
    private val events: NotificationEvents,
    private val receivers: NotificationReceivers,
    private val issues: IssueRepository,
    private val postings: PostingRepository,
    private val users: UserRepository,
    private val watches: dev.yona.domain.misc.WatchRepository,
    private val unwatches: dev.yona.domain.misc.UnwatchRepository,
    private val pullRequestCommits: PullRequestCommitRepository,
    private val messages: org.springframework.context.MessageSource
) {

    // --- 이슈 ---

    @EventListener
    fun onIssueCreated(activity: IssueCreatedActivity) {
        val issue = activity.issue
        events.add(issueNote(activity.actor, issue, EventType.NEW_ISSUE, newTitle(issue), null, issue.body, receivers.forNewIssue(issue, activity.actor)))
    }

    @EventListener
    fun onIssueChanged(activity: IssueChangedActivity) {
        val issue = activity.issue
        val actor = activity.actor ?: return
        val title = replyTitle(issue)
        fun note(receivers: Collection<User>) = issueNote(actor, issue, activity.type, title, activity.oldValue, activity.newValue, receivers)
        when (activity.type) {
            EventType.ISSUE_STATE_CHANGED, EventType.ISSUE_MILESTONE_CHANGED -> events.add(note(receivers.mandatoryForIssue(issue, activity.type, actor)))
            EventType.ISSUE_ASSIGNEE_CHANGED -> events.add(note(receivers.forAssigneeChanged(issue, activity.oldValue, actor)))
            EventType.ISSUE_BODY_CHANGED -> events.add(note(receivers.forBodyChanged(issue, activity.oldValue.orEmpty(), actor)))
            EventType.ISSUE_MOVED -> events.add(note(activity.receivers.orEmpty()))
            EventType.ISSUE_SHARER_CHANGED -> {
                val sharer = (activity.newValue?.takeIf { it.isNotBlank() } ?: activity.oldValue)?.let { users.findByLoginIdIgnoreCase(it) }
                events.addWithoutSkip(note(listOfNotNull(sharer)))
            }
            // 라벨 변경은 받는 사람이 없어 저장하지 않는다(v1.6 `receivers = null`).
            EventType.ISSUE_LABEL_CHANGED -> events.addWithoutSkip(note(emptyList()))
            else -> Unit
        }
    }

    @EventListener
    fun onIssueDeleted(activity: IssueDeletedActivity) {
        val issue = issues.findByProjectIdAndNumber(activity.projectId, activity.issueNumber)
        val project = issue?.project ?: return
        val title = "[${project.name}] ${activity.title} (#${activity.issueNumber})"
        events.add(
            NewNotification(activity.actor, ResourceType.PROJECT, activity.projectId.toString(), EventType.RESOURCE_DELETED, title, activity.body, activity.actor.loginId, receivers.forDeletedIssue(issue, activity.actor))
        )
    }

    @EventListener
    fun onIssueComment(activity: IssueCommentActivity) {
        val comment = activity.comment
        val issue = comment.issue ?: return
        val type = if (activity.created) EventType.NEW_COMMENT else EventType.COMMENT_UPDATED
        events.add(
            NewNotification(
                activity.actor, ResourceType.ISSUE_COMMENT, requireNotNull(comment.id).toString(), type, replyTitle(issue), activity.previousContents, comment.contents,
                receivers.forIssueComment(issue, comment.authorId, comment.contents.orEmpty(), comment.parentComment, type, activity.actor)
            )
        )
    }

    // --- 글 ---

    @EventListener
    fun onPostingCreated(activity: PostingCreatedActivity) {
        val posting = activity.posting
        events.add(postingNote(activity.actor, posting, EventType.NEW_POSTING, null, posting.body, receivers.forPosting(posting, activity.actor)))
    }

    @EventListener
    fun onPostingUpdated(activity: PostingUpdatedActivity) {
        val posting = activity.posting
        events.add(postingNote(activity.actor, posting, EventType.POSTING_BODY_CHANGED, activity.oldBody, posting.body, receivers.mandatoryForPosting(posting, EventType.POSTING_BODY_CHANGED, activity.actor)))
    }

    @EventListener
    fun onPostingDeleted(activity: PostingDeletedActivity) {
        val posting = postings.findByProjectIdAndNumber(activity.projectId, activity.number) ?: return
        val project = posting.project ?: return
        events.add(
            NewNotification(
                activity.actor, ResourceType.PROJECT, activity.projectId.toString(), EventType.RESOURCE_DELETED, "[${project.name}] ${activity.title} (${activity.number})",
                activity.body, activity.actor.loginId, receivers.forPosting(posting, activity.actor)
            )
        )
    }

    @EventListener
    fun onPostingComment(activity: PostingCommentActivity) {
        val comment = activity.comment
        val posting = comment.posting ?: return
        val type = if (activity.created) EventType.NEW_COMMENT else EventType.COMMENT_UPDATED
        events.add(
            NewNotification(
                activity.actor, ResourceType.NONISSUE_COMMENT, requireNotNull(comment.id).toString(), type, "Re: ${postingTitle(posting)}", activity.previousContents, comment.contents,
                receivers.forPostingComment(posting, comment.authorId, comment.contents.orEmpty(), comment.parentComment, type, activity.actor)
            )
        )
    }

    // --- 풀 리퀘스트 ---

    @EventListener
    fun onPullRequestCreated(activity: PullRequestCreatedActivity) {
        val pullRequest = activity.pullRequest
        events.add(pullRequestNote(activity.actor, pullRequest, EventType.NEW_PULL_REQUEST, pullRequestTitle(pullRequest), null, pullRequest.body, receivers.forPullRequestWithRelatedAuthors(pullRequest, activity.actor)))
    }

    @EventListener
    fun onPullRequestChanged(activity: PullRequestChangedActivity) {
        val pullRequest = activity.pullRequest
        val actor = activity.actor
        val title = "Re: " + pullRequestTitle(pullRequest)
        when (activity.type) {
            EventType.PULL_REQUEST_STATE_CHANGED ->
                events.add(pullRequestNote(actor, pullRequest, activity.type, title, activity.oldValue, activity.newValue, receivers.forPullRequest(pullRequest, actor)))
            EventType.PULL_REQUEST_COMMIT_CHANGED ->
                events.add(pullRequestNote(actor, pullRequest, activity.type, title, null, currentCommitsMessage(pullRequest), receivers.forPullRequest(pullRequest, actor)))
            EventType.PULL_REQUEST_MERGED -> {
                val merged = activity.newValue == "merged"
                events.add(
                    pullRequestNote(
                        actor, pullRequest, activity.type, title, null, activity.newValue,
                        if (merged) receivers.forPullRequestWithRelatedAuthors(pullRequest, actor) else receivers.forPullRequest(pullRequest, actor)
                    )
                )
            }
            else -> Unit
        }
    }

    /** 새 커밋 알림의 본문이다. 현재 커밋을 작성일 내림차순으로 `짧은 해시 짧은 메시지` 줄로 나열한다 (v1.6 `newPullRequestCommitChangedMessage`). */
    private fun currentCommitsMessage(pullRequest: PullRequest): String {
        val builder = StringBuilder("### ").append(messages.getMessage("notification.pullrequest.current.commits", null, java.util.Locale.getDefault())).append("\n")
        pullRequestCommits.findByPullRequestId(requireNotNull(pullRequest.id))
            .filter { it.state == dev.yona.domain.enumeration.PullRequestCommitState.CURRENT }.sortedByDescending { it.authorDate }
            .forEach { builder.append(it.commitShortId).append(' ').append(it.commitMessage.orEmpty().substringBefore('\n')).append('\n') }
        return builder.toString()
    }

    private fun pullRequestNote(sender: User, pullRequest: PullRequest, type: EventType, title: String, old: String?, new: String?, receivers: Collection<User>) =
        NewNotification(sender, ResourceType.PULL_REQUEST, requireNotNull(pullRequest.id).toString(), type, title, old, new, receivers)

    private fun pullRequestTitle(pullRequest: PullRequest) = "[${pullRequest.toProject?.name}] ${pullRequest.title} (#${pullRequest.number})"

    // --- 가입 신청 ---

    @EventListener
    fun onProjectMemberRequest(event: ProjectMemberRequestEvent) {
        val project = event.project
        val sender = event.actor ?: event.user
        val projectId = requireNotNull(project.id)
        // 프로젝트를 구독 중인(구독을 해제하지 않은) 관리자만 받는다 (v1.6 `getReceivers(project)`).
        val managers = users.findUsersByProjectRole(projectId, RoleType.MANAGER.id).filter { manager ->
            val managerId = requireNotNull(manager.id)
            watches.existsByUserIdAndResourceTypeAndResourceId(managerId, ResourceType.PROJECT, projectId.toString()) &&
                !unwatches.existsByUserIdAndResourceTypeAndResourceId(managerId, ResourceType.PROJECT, projectId.toString())
        }
        enrollNote(sender, ResourceType.PROJECT, requireNotNull(project.id).toString(), event.user, event.state, managers, EventType.MEMBER_ENROLL_REQUEST, EventType.MEMBER_ENROLL_ACCEPT,
            "notification.member.request.title", "notification.member.request.cancel.title", "notification.member.request.accept.title", project.name.orEmpty())
    }

    @EventListener
    fun onOrganizationMemberRequest(event: OrganizationMemberRequestEvent) {
        val organization = event.organization
        val sender = event.actor ?: event.user
        val admins = users.findUsersByOrganizationRole(requireNotNull(organization.id), RoleType.ORG_ADMIN.id)
        enrollNote(sender, ResourceType.ORGANIZATION, requireNotNull(organization.id).toString(), event.user, event.state, admins, EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT,
            "notification.organization.member.request.title", "notification.member.request.cancel.title", "notification.member.request.accept.title", organization.name.orEmpty())
    }

    /**
     * 가입 신청, 취소, 수락 알림을 만든다. 수락과 거절이면 처리한 사용자를 수신자에서 빼고 신청한 사용자를 더한다. 신청과 취소는 서로의 이전 값이다.
     * 거절은 v1.6처럼 제목과 이전 값이 없다.
     */
    private fun enrollNote(
        sender: User, type: ResourceType, resourceId: String, applicant: User, state: RequestState, base: Collection<User>, requestType: EventType, acceptType: EventType,
        requestTitleKey: String, cancelTitleKey: String, acceptTitleKey: String, name: String
    ) {
        val receivers = LinkedHashSet(base)
        if (state == RequestState.ACCEPT || state == RequestState.REJECT) { receivers.removeIf { it.id == sender.id }; receivers.add(applicant) }
        val locale = org.springframework.context.i18n.LocaleContextHolder.getLocale()
        val title = { key: String -> messages.getMessage(key, arrayOf<Any>(name, applicant.loginId.orEmpty()), locale) }
        val (eventType, eventTitle, old) = when (state) {
            RequestState.REQUEST -> Triple(requestType, title(requestTitleKey), RequestState.CANCEL.name)
            RequestState.CANCEL -> Triple(requestType, title(cancelTitleKey), RequestState.REQUEST.name)
            RequestState.ACCEPT -> Triple(acceptType, title(acceptTitleKey), RequestState.REQUEST.name)
            else -> Triple(requestType, null, null)
        }
        events.add(NewNotification(sender, type, resourceId, eventType, eventTitle, old, state.name, receivers))
    }

    // --- 도우미 ---

    private fun issueNote(sender: User, issue: Issue, type: EventType, title: String, old: String?, new: String?, receivers: Collection<User>) =
        NewNotification(sender, ResourceType.ISSUE_POST, requireNotNull(issue.id).toString(), type, title, old, new, receivers)

    private fun postingNote(sender: User, posting: Posting, type: EventType, old: String?, new: String?, receivers: Collection<User>) =
        NewNotification(sender, ResourceType.BOARD_POST, requireNotNull(posting.id).toString(), type, postingTitle(posting), old, new, receivers)

    private fun newTitle(issue: Issue) = "[${issue.project?.name}] ${issue.title} (#${issue.number})"

    private fun replyTitle(issue: Issue) = "Re: " + newTitle(issue)

    private fun postingTitle(posting: Posting) = "[${posting.project?.name}] ${posting.title} (${posting.number})"
}
