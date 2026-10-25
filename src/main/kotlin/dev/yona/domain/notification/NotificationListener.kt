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
import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.ReviewCommentedActivity
import dev.yona.domain.comment.ReviewThreadStateActivity
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestChangedActivity
import dev.yona.domain.pullrequest.PullRequestCommitRepository
import dev.yona.domain.pullrequest.PullRequestCreatedActivity
import dev.yona.domain.pullrequest.PullRequestReviewedActivity
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
    private val messages: org.springframework.context.MessageSource,
    private val siteUrls: dev.yona.util.SiteUrls,
    private val history: dev.yona.repository.CommitHistoryService,
    private val mentionService: MentionService
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

    // --- 프로젝트 ---

    @EventListener
    fun onCommitsPushed(activity: CommitsPushedActivity) {
        val project = activity.project
        val locale = java.util.Locale.getDefault()
        val title = if (activity.refNames.size == 1) {
            messages.getMessage("notification.pushed.commits.to", arrayOf<Any>(project.name.orEmpty(), activity.commits.size, activity.refNames[0]), locale)
        } else {
            messages.getMessage("notification.pushed.commits", arrayOf<Any>(project.name.orEmpty(), activity.commits.size), locale)
        }
        events.add(
            NewNotification(
                activity.sender, ResourceType.PROJECT, requireNotNull(project.id).toString(), EventType.NEW_COMMIT, title, null, newCommitsMessage(activity),
                receivers.forProjectCommits(project, EventType.NEW_COMMIT, activity.sender)
            )
        )
    }

    /** 새 커밋 알림의 본문이다. 커밋 목록(코드 블록)과 푸시한 브랜치 링크 목록이다 (v1.6 `newCommitsMessage`). */
    private fun newCommitsMessage(activity: CommitsPushedActivity): String {
        val locale = java.util.Locale.getDefault()
        val builder = StringBuilder()
        if (activity.commits.isNotEmpty()) {
            builder.append("### ").append(messages.getMessage("notification.pushed.newcommits", null, locale)).append("\n```\n")
            activity.commits.forEach { builder.append(it.shortId).append(' ').append(it.shortMessage).append('\n') }
            builder.append("```\n\n")
        }
        if (activity.refNames.isNotEmpty()) {
            builder.append("### ").append(messages.getMessage("notification.pushed.branches", null, locale)).append('\n')
            val project = activity.project
            activity.refNames.forEach { ref ->
                builder.append('[').append(ref).append("](").append(siteUrls.path("/${project.owner}/${project.name}/commits/${dev.yona.util.UrlPaths.routeSegment(ref)}/")).append(")\n")
            }
        }
        return builder.toString()
    }

    // --- 코드 리뷰 ---

    /**
     * 코드 리뷰 댓글 알림이다. 풀 리퀘스트의 댓글은 본문에서 멘션된 사용자와 풀 리퀘스트 수신자(구독자, 본문의 멘션)가, 커밋의 댓글은 커밋 구독자와 댓글에서 멘션된
     * 사용자가 받는다. 댓글 작성자는 뺀다 (v1.6 `forNewComment`, `forNewCommitComment`).
     */
    @EventListener
    fun onReviewCommented(activity: ReviewCommentedActivity) {
        val comment = activity.comment
        val pullRequest = activity.pullRequest
        val receivers = LinkedHashMap<Long, User>()
        fun add(list: Collection<User>) = list.forEach { u -> u.id?.let { receivers.putIfAbsent(it, u) } }
        add(mentionService.mentionedUsers(comment.contents.orEmpty()))
        val title: String
        if (pullRequest != null) {
            title = "Re: " + pullRequestTitle(pullRequest)
            add(this.receivers.forPullRequest(pullRequest, activity.actor))
        } else {
            val project = requireNotNull(comment.thread?.project)
            val commit = requireNotNull(history.commit(project, requireNotNull(activity.commitId)))
            title = "Re: [${project.name}] ${commit.shortMessage} (${commit.shortId})"
            add(this.receivers.commitWatchers(project, commit.id, commit.authorEmail, true))
        }
        // 댓글을 쓴 사람은 받지 않는다.
        receivers.remove(comment.authorId ?: activity.actor.id)
        events.add(NewNotification(activity.actor, ResourceType.REVIEW_COMMENT, requireNotNull(comment.id).toString(), EventType.NEW_REVIEW_COMMENT, title, null, comment.contents, receivers.values))
    }

    /**
     * Subversion 커밋에 코멘트를 달았을 때의 알림이다. 커밋 작성자, 이 커밋에 코멘트를 단 사용자, 프로젝트와 커밋 구독자, 본문에서 멘션된 사용자가
     * 받고 작성자는 뺀다 (v1.6 `forNewSVNCommitComment`).
     */
    @EventListener
    fun onCommitCommented(activity: dev.yona.domain.comment.CommitCommentedActivity) {
        val comment = activity.comment
        val project = requireNotNull(comment.project)
        val commit = requireNotNull(history.commit(project, requireNotNull(comment.commitId)))
        val watchers = LinkedHashMap<Long, User>()
        (this.receivers.svnCommitWatchers(project, requireNotNull(comment.commitId), commit.authorName, true) + mentionService.mentionedUsers(comment.contents.orEmpty()))
            .forEach { u -> u.id?.let { watchers.putIfAbsent(it, u) } }
        watchers.remove(activity.author.id)
        events.add(
            NewNotification(
                activity.author, ResourceType.COMMIT_COMMENT, requireNotNull(comment.id).toString(), EventType.NEW_COMMENT,
                "Re: [${project.name}] ${commit.shortMessage} (${commit.shortId})", null, comment.contents, watchers.values
            )
        )
    }

    /** 코드 리뷰 스레드를 열거나 닫았을 때의 알림이다. 풀 리퀘스트의 구독자나 커밋의 구독자가 받고 바꾼 사람은 뺀다 (v1.6 `afterStateChanged(ThreadState, CommentThread)`). */
    @EventListener
    fun onReviewThreadState(activity: ReviewThreadStateActivity) {
        val thread = activity.thread
        val pullRequest = thread.pullRequest
        val title: String
        val receivers: Set<User>
        if (pullRequest != null) {
            title = "Re: " + pullRequestTitle(pullRequest)
            receivers = this.receivers.pullRequestWatchers(pullRequest)
        } else {
            val project = requireNotNull(thread.project)
            val commitId = (thread as? CodeCommentThread)?.commitId ?: (thread as NonRangedCodeCommentThread).commitId
            val commit = requireNotNull(history.commit(project, requireNotNull(commitId)))
            title = "Re: [${project.name}] ${commit.shortMessage} (${commit.shortId})"
            receivers = this.receivers.commitWatchers(project, commit.id, commit.authorEmail, true)
        }
        events.add(
            NewNotification(
                activity.actor, ResourceType.COMMENT_THREAD, requireNotNull(thread.id).toString(), EventType.REVIEW_THREAD_STATE_CHANGED, title,
                activity.previous.name, thread.state?.name, receivers.filter { it.id != activity.actor.id }
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

    @EventListener
    fun onPullRequestReviewed(activity: PullRequestReviewedActivity) {
        val pullRequest = activity.pullRequest
        val actor = activity.actor
        // 구독자에 보낸 사람을 더하고 리뷰한 사람을 뺀다. 본문의 멘션은 받지 않는다 (v1.6 `afterReviewed`).
        val receivers = receivers.pullRequestWatchers(pullRequest).toMutableSet()
        pullRequest.contributor?.let { receivers += it }
        receivers.removeAll { it.id == actor.id }
        events.add(
            pullRequestNote(actor, pullRequest, EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, "Re: " + pullRequestTitle(pullRequest), activity.action.opposite.name, activity.action.name, receivers)
        )
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
