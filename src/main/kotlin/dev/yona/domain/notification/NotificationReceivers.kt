package dev.yona.domain.notification

import dev.yona.access.AccessControl
import dev.yona.access.GlobalResource
import dev.yona.access.ResourceLookup
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueSharerRepository
import dev.yona.domain.misc.UnwatchRepository
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 알림을 받을 사용자를 계산한다. v1.6 `NotificationEvent`의 `getReceivers`, `getMandatoryReceivers`, `getReceiversForIssueBodyChanged`,
 * `getReceiversWhenAssigneeChanged`와 `Watch.findActualWatchers`, `AbstractPosting.getWatchers`(`app/models/NotificationEvent.java:1024-1150, 1377-1390`,
 * `app/models/Watch.java`)에 해당한다.
 *
 * 구독자의 "실제" 범위(프로젝트 구독자와 리소스 구독자에서 구독 해제자를 뺀 것)는 새 글과 삭제에 쓰고, 변경과 댓글에는 리소스를 직접 구독한 사용자와
 * 작성자, 담당자, 공유자, 프로젝트에서 이 이벤트를 받겠다고 한 사용자(필수 수신자)를 쓴다.
 */
@Service
class NotificationReceivers(
    private val watches: WatchRepository,
    private val unwatches: UnwatchRepository,
    private val projectNotifications: UserProjectNotificationRepository,
    private val users: UserRepository,
    private val issueSharers: IssueSharerRepository,
    private val issueComments: IssueCommentRepository,
    private val postingComments: PostingCommentRepository,
    private val lookup: ResourceLookup,
    private val accessControl: AccessControl,
    private val mentions: MentionService
) {

    /** 중복 없이 사용자를 모으는 집합이다. 사용자 번호가 같으면 같은 사용자이다. */
    private class Group {
        private val map = linkedMapOf<Long, User>()
        fun add(user: User?) { user?.id?.let { map.putIfAbsent(it, user) } }
        fun addAll(list: Collection<User>) = list.forEach { add(it) }
        fun remove(user: User?) { user?.id?.let { map.remove(it) } }
        fun removeAll(list: Collection<User>) = list.forEach { remove(it) }
        fun retain(predicate: (User) -> Boolean) { map.values.removeIf { !predicate(it) } }
        fun toSet(): Set<User> = LinkedHashSet(map.values)
    }

    // --- 구독자 ---

    /**
     * [type], [resourceId] 리소스의 실제 구독자이다(v1.6 `Watch.findActualWatchers`). [base]에 프로젝트 구독자와 리소스를 직접 구독한 사용자를 더하고
     * 리소스의 구독 해제자를 뺀 뒤 리소스를 읽을 수 없는 사용자를 거른다.
     */
    @Transactional(readOnly = true)
    fun actualWatchers(type: ResourceType, resourceId: String, base: Collection<User>): Set<User> {
        val resource = lookup.get(type, resourceId)
        val group = Group().also { it.addAll(base) }
        val project = if (resource is GlobalResource) null else resource.project
        project?.id?.let { group.addAll(watches.findByResourceTypeAndResourceId(ResourceType.PROJECT, it.toString()).mapNotNull { w -> w.user }) }
        group.addAll(watches.findByResourceTypeAndResourceId(type, resourceId).mapNotNull { it.user })
        group.removeAll(unwatches.findByResourceTypeAndResourceId(type, resourceId).mapNotNull { it.user })
        group.retain { accessControl.isAllowed(it, resource, Operation.READ) }
        return group.toSet()
    }

    /** 이슈의 실제 구독자이다(작성자 포함). 이슈를 옮기기 전에 구독자를 정할 때도 쓴다 (v1.6 `Issue.getWatchers`). */
    @Transactional(readOnly = true)
    fun issueWatchers(issue: Issue): Set<User> = actualWatchers(ResourceType.ISSUE_POST, requireNotNull(issue.id).toString(), listOfNotNull(author(issue.authorLoginId)))

    // --- 이슈 ---

    /** 새 이슈를 받을 사용자이다. 실제 구독자와 본문에서 멘션된 사용자에서 [author]를 뺀다 (v1.6 `getReceivers(issue, author)`). */
    @Transactional(readOnly = true)
    fun forNewIssue(issue: Issue, author: User): Set<User> = Group().also {
        it.addAll(issueWatchers(issue)); it.addAll(mentions.mentionedUsers(issue.body.orEmpty())); it.remove(author)
    }.toSet()

    /**
     * 이슈의 상태, 담당자, 마일스톤, 본문이 바뀔 때 받을 필수 수신자이다. 이슈를 직접 구독한 사용자, 작성자, 공유자, 담당자와 프로젝트에서 [eventType]을
     * 받겠다고 한 사용자에서 구독 해제자, 이를 받지 않겠다고 한 사용자, [actor]를 뺀다.
     */
    @Transactional(readOnly = true)
    fun mandatoryForIssue(issue: Issue, eventType: EventType, actor: User?): Set<User> {
        val issueId = requireNotNull(issue.id)
        val projectId = requireNotNull(issue.project?.id)
        val group = Group()
        group.addAll(watches.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, issueId.toString()).mapNotNull { it.user })
        group.add(author(issue.authorLoginId))
        issueSharers.findByIssueId(issueId).forEach { group.add(it.loginId?.let { login -> users.findByLoginIdIgnoreCase(login) }) }
        group.add(issue.assignee?.user)
        group.addAll(eventUsers(projectId, eventType, true))
        group.removeAll(unwatches.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, issueId.toString()).mapNotNull { it.user })
        group.removeAll(eventUsers(projectId, eventType, false))
        group.remove(actor ?: issue.authorId?.let { users.findById(it).orElse(null) })
        return group.toSet()
    }

    /** 담당자가 바뀔 때 받을 사용자이다. 필수 수신자에 이전 담당자(익명이 아니고 [actor]가 아닐 때)를 더한다. */
    @Transactional(readOnly = true)
    fun forAssigneeChanged(issue: Issue, oldAssigneeLoginId: String?, actor: User?): Set<User> {
        val group = Group().also { it.addAll(mandatoryForIssue(issue, EventType.ISSUE_ASSIGNEE_CHANGED, actor)) }
        val old = oldAssigneeLoginId?.let { author(it) }
        if (old != null && old.loginId != actor?.loginId) group.add(old)
        return group.toSet()
    }

    /** 본문이 바뀔 때 받을 사용자이다. 필수 수신자에 새로 멘션된 사용자를 더하고 [actor]를 뺀다 (v1.6 `getReceiversForIssueBodyChanged`). */
    @Transactional(readOnly = true)
    fun forBodyChanged(issue: Issue, oldBody: String, actor: User?): Set<User> {
        val group = Group().also { it.addAll(mandatoryForIssue(issue, EventType.ISSUE_BODY_CHANGED, actor)) }
        group.addAll(mentions.mentionedUsers(issue.body.orEmpty()) - mentions.mentionedUsers(oldBody).toSet())
        group.remove(actor ?: issue.authorId?.let { users.findById(it).orElse(null) })
        return group.toSet()
    }

    // --- 글 ---

    /** 새 글이나 삭제를 받을 사용자이다. 실제 구독자와 본문에서 멘션된 사용자에서 [except]를 뺀다 (v1.6 `getReceivers(abstractPosting, except)`). */
    @Transactional(readOnly = true)
    fun forPosting(posting: Posting, except: User?): Set<User> = Group().also {
        it.addAll(actualWatchers(ResourceType.BOARD_POST, requireNotNull(posting.id).toString(), listOfNotNull(author(posting.authorLoginId))))
        it.addAll(mentions.mentionedUsers(posting.body.orEmpty())); it.remove(except)
    }.toSet()

    /** 이슈 삭제를 받을 사용자이다 ([forPosting]과 같은 규칙). */
    @Transactional(readOnly = true)
    fun forDeletedIssue(issue: Issue, except: User?): Set<User> = Group().also {
        it.addAll(issueWatchers(issue)); it.addAll(mentions.mentionedUsers(issue.body.orEmpty())); it.remove(except)
    }.toSet()

    /** 글 본문이 바뀔 때 받을 필수 수신자이다. 글을 직접 구독한 사용자, 작성자와 프로젝트에서 이 이벤트를 받겠다고 한 사용자에서 구독 해제자 등을 뺀다. */
    @Transactional(readOnly = true)
    fun mandatoryForPosting(posting: Posting, eventType: EventType, actor: User?): Set<User> {
        val id = requireNotNull(posting.id).toString()
        val projectId = requireNotNull(posting.project?.id)
        val group = Group()
        group.addAll(watches.findByResourceTypeAndResourceId(ResourceType.BOARD_POST, id).mapNotNull { it.user })
        group.add(author(posting.authorLoginId))
        group.addAll(eventUsers(projectId, eventType, true))
        group.removeAll(unwatches.findByResourceTypeAndResourceId(ResourceType.BOARD_POST, id).mapNotNull { it.user })
        group.removeAll(eventUsers(projectId, eventType, false))
        group.remove(actor ?: posting.authorId?.let { users.findById(it).orElse(null) })
        return group.toSet()
    }

    // --- 댓글 ---

    /**
     * 이슈에 댓글이 달릴 때 알림을 받을 사용자이다. 저장되지 않은 댓글에도 쓴다(댓글 입력 중 수신자 미리 보기).
     *
     * 이슈의 구독자, 작성자, 담당자와 프로젝트에서 이 이벤트를 받겠다고 한 사용자가 받는다. [parentComment]가 있으면(한 줄 댓글) 이슈 작성자 대신
     * 부모 댓글의 작성자와 부모 댓글에서 멘션된 사용자가 받고, 부모 댓글 작성자가 답할 때는 앞의 한 줄 댓글 작성자도 받는다.
     * 구독을 해제했거나, 이 이벤트를 받지 않겠다고 했거나, 활성 상태가 아닌 사용자와 댓글을 쓰는 사람은 빠지고, 댓글에서 멘션된 사용자는 마지막에 더해진다.
     *
     * @param commentAuthorId 댓글을 쓰는 사용자의 번호
     * @param current 지금 로그인한 사용자. 없으면 시스템이 처리하는 것으로 보고 댓글 작성자를 뺀다
     */
    @Transactional(readOnly = true)
    fun forIssueComment(issue: Issue, commentAuthorId: Long?, contents: String, parentComment: IssueComment?, eventType: EventType, current: User?): Set<User> {
        val resourceId = requireNotNull(issue.id).toString()
        return commentReceivers(
            ResourceType.ISSUE_POST, resourceId, requireNotNull(issue.project?.id), issue.authorLoginId, issue.authorId, issue.assignee?.user,
            commentAuthorId, contents, parentComment?.let { Parent(it.authorId, it.contents.orEmpty(), issueComments.findByParentCommentIdOrderByIdAsc(requireNotNull(it.id)).mapNotNull { c -> c.authorId }) },
            eventType, current
        )
    }

    /** 글에 댓글이 달릴 때 알림을 받을 사용자이다. 규칙은 [forIssueComment]와 같고 담당자가 없다. */
    @Transactional(readOnly = true)
    fun forPostingComment(posting: Posting, commentAuthorId: Long?, contents: String, parentComment: PostingComment?, eventType: EventType, current: User?): Set<User> =
        commentReceivers(
            ResourceType.BOARD_POST, requireNotNull(posting.id).toString(), requireNotNull(posting.project?.id), posting.authorLoginId, posting.authorId, null,
            commentAuthorId, contents, parentComment?.let { Parent(it.authorId, it.contents.orEmpty(), postingComments.findByParentCommentIdOrderByIdAsc(requireNotNull(it.id)).mapNotNull { c -> c.authorId }) },
            eventType, current
        )

    private class Parent(val authorId: Long?, val contents: String, val childAuthorIds: List<Long>)

    private fun commentReceivers(
        type: ResourceType, resourceId: String, projectId: Long, postAuthorLogin: String?, postAuthorId: Long?, assignee: User?,
        commentAuthorId: Long?, contents: String, parent: Parent?, eventType: EventType, current: User?
    ): Set<User> {
        val group = Group()
        group.addAll(watches.findByResourceTypeAndResourceId(type, resourceId).mapNotNull { it.user })
        group.add(author(postAuthorLogin))
        group.add(assignee)
        if (parent != null) {
            group.remove(postAuthorId?.let { users.findById(it).orElse(null) })
            group.add(parent.authorId?.let { users.findById(it).orElse(null) })
            group.addAll(mentions.mentionedUsers(parent.contents))
            if (parent.authorId == commentAuthorId) parent.childAuthorIds.forEach { group.add(users.findById(it).orElse(null)) }
        }
        group.addAll(eventUsers(projectId, eventType, true))
        group.removeAll(unwatches.findByResourceTypeAndResourceId(type, resourceId).mapNotNull { it.user })
        group.removeAll(eventUsers(projectId, eventType, false))
        group.retain { it.state == UserState.ACTIVE }
        // 로그인하지 않았으면 시스템이 작성자를 대신해 처리하는 것으로 보고 작성자를 뺀다.
        group.remove(current ?: commentAuthorId?.let { users.findById(it).orElse(null) })
        group.addAll(mentions.mentionedUsers(contents))
        return group.toSet()
    }

    private fun author(loginId: String?): User? = loginId?.let { users.findByLoginIdIgnoreCase(it) }

    private fun eventUsers(projectId: Long, eventType: EventType, allowed: Boolean): List<User> =
        projectNotifications.findByProjectIdAndNotificationTypeAndAllowed(projectId, eventType, allowed).mapNotNull { it.user }
}
