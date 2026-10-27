package dev.yona.domain.issue

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.notification.MentionService
import dev.yona.domain.user.User
import jakarta.persistence.EntityManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 이슈 댓글의 등록, 수정, 삭제와 추천을 담당한다. v1.6 `IssueApp.newComment`, `deleteComment`와 `Comment`, `IssueComment`, `VoteApp`에 해당한다.
 */
@Service
class IssueCommentService(
    private val comments: IssueCommentRepository,
    private val issues: IssueRepository,
    private val issueService: IssueService,
    private val mentions: MentionService,
    private val attachments: AttachmentService,
    private val publisher: ApplicationEventPublisher,
    private val entityManager: EntityManager
) {

    /**
     * 댓글을 단다. [parentCommentId]를 주면 그 댓글에 달리는 한 줄 댓글이다. 멘션을 기록하고 이슈의 댓글 수를 맞추며 [IssueCommentActivity]를
     * 발행한다. [withStateTransition]이면 이슈를 다음 상태로 바꾸고, 아니면 [touchUpdated]일 때 이슈의 수정 시각만 갱신한다.
     */
    @Transactional
    fun add(issue: Issue, author: User, contents: String, parentCommentId: Long?, withStateTransition: Boolean, touchUpdated: Boolean = true): IssueComment {
        val parent = parentCommentId?.let { comments.findById(it).orElse(null) }
        val previous = previousContents(issue, parent)
        val comment = comments.save(
            IssueComment(
                contents = contents, createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                issue = issue, projectId = requireNotNull(issue.project?.id), parentComment = parent
            )
        )
        mentions.update(ResourceType.ISSUE_COMMENT, requireNotNull(comment.id).toString(), contents)
        syncCommentCount(issue)
        publisher.publishEvent(IssueCommentActivity(comment, previous, true, author))
        // 파생 이슈 댓글(v1.6 newReferComment)은 이슈의 수정 시각을 바꾸지 않는다
        if (touchUpdated || withStateTransition) touchOrTransition(issue, author, withStateTransition)
        return comment
    }

    /**
     * 댓글을 고친다. 멘션 기록을 새 내용에 맞추고, [notifyMail]이 켜져 있거나 [actor]가 작성자가 아니면 [IssueCommentActivity]를 발행한다.
     */
    @Transactional
    fun update(comment: IssueComment, actor: User, contents: String, notifyMail: Boolean, withStateTransition: Boolean): IssueComment {
        comment.contents = contents
        comments.save(comment)
        mentions.update(ResourceType.ISSUE_COMMENT, requireNotNull(comment.id).toString(), contents)
        if (notifyMail || comment.authorLoginId != actor.loginId) publisher.publishEvent(IssueCommentActivity(comment, null, false, actor))
        touchOrTransition(requireNotNull(comment.issue), actor, withStateTransition)
        return comment
    }

    /** 댓글 내용만 바꾼다 (v1.6 `IssueApi.updateIssueComment`, 체크리스트 갱신). 활동을 발행하지 않고 멘션 기록을 맞추며 이슈의 댓글 수를 다시 센다. */
    @Transactional
    fun replaceContents(comment: IssueComment, contents: String): IssueComment {
        comment.contents = contents
        comments.save(comment)
        mentions.update(ResourceType.ISSUE_COMMENT, requireNotNull(comment.id).toString(), contents)
        comment.issue?.let { syncCommentCount(it) }
        return comment
    }

    /**
     * 댓글을 지운다. 첨부와 알림 이벤트를 지우고, 이 댓글에 달린 한 줄 댓글은 일반 댓글로 남기며, 이슈의 댓글 수를 맞춘다.
     * v1.6처럼 멘션 기록은 지우지 않는다([refactoring-backlog.md] R-26).
     */
    @Transactional
    fun delete(comment: IssueComment) {
        val id = requireNotNull(comment.id)
        val issue = comment.issue
        attachments.deleteAll(ResourceType.ISSUE_COMMENT, id)
        issueService.deleteResourceRelations(ResourceType.ISSUE_COMMENT, id.toString())
        comments.findByParentCommentIdOrderByIdAsc(id).forEach { it.parentComment = null }
        entityManager.flush()
        comment.voters.clear()
        comments.delete(comment)
        comments.flush()
        issue?.let { syncCommentCount(it) }
    }

    /** [user]가 이슈를 추천한다. 이미 추천했으면 그대로이다 (v1.6 `Issue.addVoter`). */
    @Transactional
    fun vote(issue: Issue, user: User) {
        // v1.6 `Issue.addVoter`는 추천자가 실제로 바뀐 때만 `update()`를 부른다
        if (issue.voters.none { it.id == user.id }) { issue.voters.add(user); syncCommentCount(issue) }
    }

    /** [user]의 이슈 추천을 취소한다. */
    @Transactional
    fun unvote(issue: Issue, user: User) {
        if (issue.voters.removeIf { it.id == user.id }) syncCommentCount(issue)
    }

    /** [user]가 댓글을 추천한다. */
    @Transactional
    fun voteComment(comment: IssueComment, user: User) {
        // v1.6 `IssueComment.addVoter`는 바뀐 때만 `Comment.update()`를 불러 멘션 기록을 맞춘다
        if (comment.voters.none { it.id == user.id }) { comment.voters.add(user); comments.save(comment); resyncMentions(comment) }
    }

    /** [user]의 댓글 추천을 취소한다. */
    @Transactional
    fun unvoteComment(comment: IssueComment, user: User) {
        if (comment.voters.removeIf { it.id == user.id }) { comments.save(comment); resyncMentions(comment) }
    }

    private fun resyncMentions(comment: IssueComment) =
        mentions.update(ResourceType.ISSUE_COMMENT, requireNotNull(comment.id).toString(), comment.contents)

    /** 이슈의 댓글 수를 실제 댓글 수에 맞춘다 (v1.6 `AbstractPosting.update`가 `numOfComments`를 다시 계산한다). */
    private fun syncCommentCount(issue: Issue) {
        issueService.resync(issue)
    }

    private fun touchOrTransition(issue: Issue, actor: User, withStateTransition: Boolean) {
        if (withStateTransition) {
            // v1.6 newComment/editComment는 Issue.toNextState만 부르므로 닫힌 상위 이슈는 열지 않는다 (nextState 액션만 연다)
            issueService.toNextState(issue, actor, reopenParent = false)
        } else {
            issue.updatedDate = Date()
            issues.save(issue)
        }
    }

    /**
     * 메일 본문에 덧붙이는 "이전 내용"이다 (v1.6 `IssueApp.AddPreviousContent`). 첫 댓글이면 원래 이슈, 한 줄 댓글이면 앞의 한 줄 댓글(없으면
     * 부모 댓글), 아니면 바로 앞의 댓글이다. 이슈의 저장된 댓글 수와 실제 댓글 수가 다르면 실제 수로 바로잡는다.
     */
    private fun previousContents(issue: Issue, parent: IssueComment?): String {
        if ((issue.numOfComments ?: 0) == 0) return previous("Original issue", issue.body, issue.updatedDate, issue.authorLoginId)
        if (parent != null) {
            val before = comments.findByParentCommentIdOrderByIdAsc(requireNotNull(parent.id)).lastOrNull() ?: parent
            return previous("Previous comment", before.contents, before.createdDate, before.authorLoginId)
        }
        val all = comments.findByIssueIdOrderByCreatedDateAscIdAsc(requireNotNull(issue.id))
        if (issue.numOfComments != all.size) { issue.numOfComments = all.size; issues.save(issue) }
        val last = all.lastOrNull() ?: return previous("Issue", issue.body, issue.updatedDate, issue.authorLoginId)
        return previous("Previous comment", last.contents, last.createdDate, last.authorLoginId)
    }

    private fun previous(title: String, contents: String?, date: Date?, authorLoginId: String?): String =
        "\n\n<br />\n\n--- $title from @$authorLoginId  ${optionalShortDate(date)} ---\n\n<br />\n\n$contents"

    /** 올해이고 오늘이면 `at h:mm a`, 올해이면 `MMM d at h:mm a`, 아니면 `yy.MM.dd at h:mm a`이다 (v1.6 `JodaDateUtil.getOptionalShortDate`). */
    private fun optionalShortDate(date: Date?): String {
        if (date == null) return ""
        val target = Calendar.getInstance().apply { time = date }
        val now = Calendar.getInstance()
        val locale = Locale.getDefault()
        return when {
            target.get(Calendar.YEAR) != now.get(Calendar.YEAR) -> SimpleDateFormat("yy.MM.dd 'at' h:mm a", locale).format(date)
            target.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> SimpleDateFormat("'at' h:mm a", locale).format(date)
            else -> SimpleDateFormat("MMM d 'at' h:mm a", locale).format(date)
        }
    }
}
