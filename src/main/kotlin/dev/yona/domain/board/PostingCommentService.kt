package dev.yona.domain.board

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.IssueService
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.notification.MentionService
import dev.yona.domain.user.User
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 게시글 댓글의 등록, 수정, 삭제이다. v1.6 `BoardApp.newComment`, `deleteComment`와 `Comment`, `PostingComment`, `BoardApi.updatePostingComment`
 * (`app/controllers/BoardApp.java:260-340`)에 해당한다. 권한 확인은 호출하는 쪽이 한다.
 */
@Service
class PostingCommentService(
    private val postingComments: PostingCommentRepository,
    private val postings: PostingRepository,
    private val mentions: MentionService,
    private val attachments: AttachmentService,
    private val issueService: IssueService,
    private val publisher: ApplicationEventPublisher
) {

    /**
     * 댓글을 단다. [parentCommentId]를 주면 그 댓글에 달리는 한 줄 댓글이다. 멘션을 기록하고 글의 댓글 수와 수정 시각을 맞추며
     * [PostingCommentActivity]를 발행한다.
     */
    @Transactional
    fun add(posting: Posting, author: User, contents: String, parentCommentId: Long?): PostingComment {
        val parent = parentCommentId?.let { postingComments.findById(it).orElse(null) }
        val previous = previousContents(posting, parent)
        val comment = postingComments.save(
            PostingComment(
                contents = contents, createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                posting = posting, projectId = requireNotNull(posting.project?.id), parentComment = parent
            )
        )
        mentions.update(ResourceType.NONISSUE_COMMENT, requireNotNull(comment.id).toString(), contents)
        posting.updatedDate = Date()
        syncCount(posting)
        publisher.publishEvent(PostingCommentActivity(comment, previous, true, author))
        return comment
    }

    /** 댓글을 고친다. [notifyMail]이 켜져 있거나 [actor]가 작성자가 아니면 [PostingCommentActivity]를 발행한다. 글의 수정 시각은 바꾸지 않는다. */
    @Transactional
    fun update(comment: PostingComment, actor: User, contents: String, notifyMail: Boolean): PostingComment {
        comment.contents = contents
        postingComments.save(comment)
        mentions.update(ResourceType.NONISSUE_COMMENT, requireNotNull(comment.id).toString(), contents)
        if (notifyMail || comment.authorLoginId != actor.loginId) publisher.publishEvent(PostingCommentActivity(comment, null, false, actor))
        return comment
    }

    /** 댓글 내용만 바꾼다(체크리스트 갱신). 활동을 발행하지 않는다. 멘션 기록을 맞추고 글의 댓글 수를 다시 센다. */
    @Transactional
    fun replaceContents(comment: PostingComment, contents: String): PostingComment {
        comment.contents = contents
        postingComments.save(comment)
        mentions.update(ResourceType.NONISSUE_COMMENT, requireNotNull(comment.id).toString(), contents)
        comment.posting?.let { syncCount(it) }
        return comment
    }

    /** 댓글을 지운다. 첨부와 알림 이벤트를 지우고, 달린 한 줄 댓글은 일반 댓글로 남기며, 글의 댓글 수를 맞춘다. 멘션 기록은 남긴다([refactoring-backlog.md] R-26). */
    @Transactional
    fun delete(comment: PostingComment) {
        val id = requireNotNull(comment.id)
        val posting = comment.posting
        attachments.deleteAll(ResourceType.NONISSUE_COMMENT, id)
        issueService.deleteNotificationEvents(ResourceType.NONISSUE_COMMENT, id.toString())
        postingComments.findByParentCommentIdOrderByIdAsc(id).forEach { it.parentComment = null }
        postingComments.flush()
        postingComments.delete(comment)
        postingComments.flush()
        posting?.let { syncCount(it) }
    }

    private fun syncCount(posting: Posting) {
        posting.numOfComments = postingComments.countByPostingId(requireNotNull(posting.id)).toInt()
        postings.save(posting)
    }

    /** 메일 본문에 덧붙이는 "이전 내용"이다 (v1.6 `BoardApp.AddPreviousContent`). 첫 댓글은 원래 글, 한 줄 댓글은 앞의 한 줄 댓글이나 부모 댓글, 그 밖은 바로 앞의 댓글이다. */
    private fun previousContents(posting: Posting, parent: PostingComment?): String {
        if ((posting.numOfComments ?: 0) == 0) return previous("Original posting", posting.body, posting.updatedDate, posting.authorLoginId)
        val before = if (parent != null) {
            postingComments.findByParentCommentIdOrderByIdAsc(requireNotNull(parent.id)).lastOrNull() ?: parent
        } else {
            postingComments.findByPostingIdOrderByCreatedDateAscIdAsc(requireNotNull(posting.id)).last()
        }
        return previous("Previous comment", before.contents, before.createdDate, before.authorLoginId)
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
