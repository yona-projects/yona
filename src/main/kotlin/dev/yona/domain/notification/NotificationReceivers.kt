package dev.yona.domain.notification

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.misc.UnwatchRepository
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 알림을 반드시 받아야 하는 사용자를 계산한다. v1.6 `NotificationEvent.getMandatoryReceivers`와 `UserProjectNotification`의 이벤트별 설정
 * (`app/models/NotificationEvent.java:1024-1103`, `app/models/UserProjectNotification.java:148-167`)에 해당한다.
 */
@Service
class NotificationReceivers(
    private val watches: WatchRepository,
    private val unwatches: UnwatchRepository,
    private val projectNotifications: UserProjectNotificationRepository,
    private val users: UserRepository,
    private val issueComments: IssueCommentRepository,
    private val mentions: MentionService
) {

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
        val result = linkedMapOf<Long, User>()
        fun add(user: User?) { user?.id?.let { result.putIfAbsent(it, user) } }
        fun addAll(list: Collection<User>) = list.forEach { add(it) }
        fun remove(user: User?) { user?.id?.let { result.remove(it) } }
        fun removeAll(list: Collection<User>) = list.forEach { remove(it) }

        val resourceId = requireNotNull(issue.id).toString()
        val projectId = requireNotNull(issue.project?.id)
        addAll(watches.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, resourceId).mapNotNull { it.user })
        add(issue.authorLoginId?.let { users.findByLoginIdIgnoreCase(it) })
        add(issue.assignee?.user)
        if (parentComment != null) {
            remove(issue.authorId?.let { users.findById(it).orElse(null) })
            add(parentComment.authorId?.let { users.findById(it).orElse(null) })
            addAll(mentions.mentionedUsers(parentComment.contents.orEmpty()))
            if (parentComment.authorId == commentAuthorId) {
                issueComments.findByParentCommentIdOrderByIdAsc(requireNotNull(parentComment.id)).forEach { sibling ->
                    add(sibling.authorId?.let { users.findById(it).orElse(null) })
                }
            }
        }
        addAll(eventUsers(projectId, eventType, true))
        removeAll(unwatches.findByResourceTypeAndResourceId(ResourceType.ISSUE_POST, resourceId).mapNotNull { it.user })
        removeAll(eventUsers(projectId, eventType, false))
        removeAll(result.values.filter { it.state != UserState.ACTIVE })
        // 로그인하지 않았으면 시스템이 작성자를 대신해 처리하는 것으로 보고 작성자를 뺀다.
        remove(current ?: commentAuthorId?.let { users.findById(it).orElse(null) })
        addAll(mentions.mentionedUsers(contents))
        return LinkedHashSet(result.values)
    }

    private fun eventUsers(projectId: Long, eventType: EventType, allowed: Boolean): List<User> =
        projectNotifications.findByProjectIdAndNotificationTypeAndAllowed(projectId, eventType, allowed).mapNotNull { it.user }
}
