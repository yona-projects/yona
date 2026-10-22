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
