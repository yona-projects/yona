package dev.yona.mailbox

import com.googlecode.htmlcompressor.compressor.HtmlCompressor
import dev.yona.access.AccessControl
import dev.yona.access.Resource
import dev.yona.domain.board.PostingCommentService
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.comment.ReviewCommentService
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueCommentService
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueService
import dev.yona.domain.issue.NewIssue
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.notification.NotificationEvents
import dev.yona.domain.notification.NotificationExclusions
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.domain.user.UserLookup
import dev.yona.util.SiteUrls
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimePart
import jakarta.mail.internet.MimeUtility
import org.jsoup.Jsoup
import org.springframework.context.MessageSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

/**
 * 만들어진 댓글이다.
 *
 * @property type 댓글의 종류(이슈 댓글 또는 게시글 댓글)
 * @property id 댓글 번호
 * @property contents 저장한 내용
 * @property authorLoginId 작성자
 */
data class MailComment(val type: ResourceType, val id: Long?, val contents: String?, val authorLoginId: String?)

/**
 * 받은 메일에서 이슈, 댓글, 리뷰 댓글을 만든다. v1.6 `mailbox.CreationViaEmail`(`app/mailbox/CreationViaEmail.java`)에 해당한다.
 *
 * 보낸 사람이 그 프로젝트에 리소스를 만들 권한이 있어야 한다. 메일 본문이 HTML이면 `cid:` 주소를 첨부 주소로 바꾸고(이슈와 댓글은 태그 사이의
 * 개행도 없앤다) 알림 본문도 같은 내용으로 맞춘다. 만든 리소스는 원본 메일의 메시지 ID와 함께 기록해(원본 메일 기록은 만든 뒤 [MailHandler]가 따로 남긴다) 그 메일에 대한
 * 답장을 같은 리소스에 이어 달고 같은 메일을 두 번 처리하지 않는다. 원본 메일 기록은 만든 뒤 [MailHandler]가 따로 남긴다. 메일을 이미 받은 사람(보낸 사람, 받는 사람, 참조)에게는 알림 메일을 또 보내지 않는다.
 *
 * v1.6은 첨부를 저장하는 `Attachment.save`가 HTTP 요청이 없는 메일 스레드에서 호출되어 항상 실패한다. v2는 정상으로 저장한다(D-25, R-61).
 */
@Service
class MailResourceCreator(
    private val issueService: IssueService,
    private val issueCommentService: IssueCommentService,
    private val postingCommentService: PostingCommentService,
    private val reviewCommentService: ReviewCommentService,
    private val reviewComments: ReviewCommentRepository,
    private val threads: CommentThreadRepository,
    private val issues: IssueRepository,
    private val postings: PostingRepository,
    private val attachments: AttachmentService,
    private val events: NotificationEvents,
    private val exclusions: NotificationExclusions,
    private val accessControl: AccessControl,
    private val userLookup: UserLookup,
    private val messages: MessageSource,
    private val siteUrls: SiteUrls
) {

    /** 메일로 [project]에 이슈를 만든다. 제목은 메일 제목, 본문은 메일 본문이다. */
    @Transactional
    fun saveIssue(message: MimeMessage, project: Project): Issue {
        val sender = senderOf(message)
        if (sender == null || !accessControl.isProjectResourceCreatable(sender, project, ResourceType.ISSUE_POST)) {
            throw PermissionDenied(cannotCreate(sender, project, ResourceType.ISSUE_POST))
        }
        val content = MailContent.extract(message)
        val issue = exclusions.without(recipientIds(message, sender)) {
            issueService.create(project, sender, NewIssue(message.subject.orEmpty(), content.body, null, false, null, null, null, emptySet()))
        }
        val issueId = requireNotNull(issue.id)
        val stored = saveAttachments(content.attachments, ResourceType.ISSUE_POST, issueId, sender)
        if (content.isHtml) {
            val processed = postprocess(issue.body.orEmpty(), stored)
            issueService.replaceBody(issue, processed)
            events.replaceNewValue(ResourceType.ISSUE_POST, issueId.toString(), EventType.NEW_ISSUE, processed)
        }
        return issue
    }

    /** 메일로 [target](이슈 또는 게시글)에 댓글을 단다. */
    @Transactional
    fun saveComment(message: MimeMessage, target: Resource): MailComment {
        val sender = senderOf(message)
        val project = requireNotNull(target.project)
        if (sender == null || !accessControl.isProjectResourceCreatable(sender, project, target.type)) {
            throw PermissionDenied(cannotCreate(sender, project, target.type))
        }
        val content = MailContent.extract(message)
        val targetId = target.id.toLong()
        val recipients = recipientIds(message, sender)
        return when (target.type) {
            ResourceType.ISSUE_POST -> {
                val issue = issues.findById(targetId).orElseThrow { IssueNotFound(targetId) }
                val comment = exclusions.without(recipients) { issueCommentService.add(issue, sender, content.body, null, false) }
                val commentId = requireNotNull(comment.id)
                val stored = saveAttachments(content.attachments, ResourceType.ISSUE_COMMENT, commentId, sender)
                if (content.isHtml) {
                    val processed = postprocess(comment.contents.orEmpty(), stored)
                    issueCommentService.replaceContents(comment, processed)
                    events.replaceNewValue(ResourceType.ISSUE_COMMENT, commentId.toString(), EventType.NEW_COMMENT, processed)
                }
                MailComment(ResourceType.ISSUE_COMMENT, commentId, comment.contents, comment.authorLoginId)
            }
            ResourceType.BOARD_POST -> {
                val posting = postings.findById(targetId).orElseThrow { PostingNotFound(targetId) }
                val comment = exclusions.without(recipients) { postingCommentService.add(posting, sender, content.body, null) }
                val commentId = requireNotNull(comment.id)
                val stored = saveAttachments(content.attachments, ResourceType.NONISSUE_COMMENT, commentId, sender)
                if (content.isHtml) {
                    val processed = postprocess(comment.contents.orEmpty(), stored)
                    postingCommentService.replaceContents(comment, processed)
                    events.replaceNewValue(ResourceType.NONISSUE_COMMENT, commentId.toString(), EventType.NEW_COMMENT, processed)
                }
                MailComment(ResourceType.NONISSUE_COMMENT, commentId, comment.contents, comment.authorLoginId)
            }
            else -> throw IllegalArgumentException("Unsupported resource type: ${target.type}")
        }
    }

    /** 메일로 [target](리뷰 스레드)에 리뷰 댓글을 단다. */
    @Transactional
    fun saveReviewComment(message: MimeMessage, target: Resource): ReviewComment {
        val sender = senderOf(message)
        val project = requireNotNull(target.project)
        if (sender == null || !accessControl.isProjectResourceCreatable(sender, project, ResourceType.REVIEW_COMMENT)) {
            throw PermissionDenied(cannotCreate(sender, project, target.type))
        }
        val content = MailContent.extract(message)
        val thread = threads.findById(target.id.toLong()).orElseThrow { IllegalArgumentException() }
        val comment = exclusions.without(recipientIds(message, sender)) { reviewCommentService.commentByMail(thread, sender, content.body) }
        val commentId = requireNotNull(comment.id)
        val stored = saveAttachments(content.attachments, ResourceType.REVIEW_COMMENT, commentId, sender)
        if (content.isHtml) {
            // 리뷰 댓글은 cid만 바꾸고 본문을 압축하지 않는다.
            comment.contents = replaceCid(comment.contents.orEmpty(), stored)
            reviewComments.save(comment)
            events.replaceNewValue(ResourceType.REVIEW_COMMENT, commentId.toString(), EventType.NEW_REVIEW_COMMENT, comment.contents)
        }
        return comment
    }

    /** 보낸 사람 중 사용자인 첫 사람이다 (v1.6 `IMAPMessageUtil.extractSender`). */
    private fun senderOf(message: MimeMessage): User? =
        message.from?.firstNotNullOfOrNull { userLookup.findByEmail((it as InternetAddress).address) }

    /** 이미 메일을 받은 사람들이다: 보낸 사람과 받는 사람, 참조 (v1.6 `addEvent`). */
    private fun recipientIds(message: MimeMessage, sender: User): Set<Long> {
        val ids = mutableSetOf<Long>()
        sender.id?.let { ids += it }
        message.allRecipients?.forEach { address -> userLookup.findByEmail((address as InternetAddress).address)?.id?.let { ids += it } }
        return ids
    }

    private fun cannotCreate(user: User?, project: Project, type: ResourceType): String {
        val locale = Locale.forLanguageTag(user?.lang ?: Locale.getDefault().language)
        val who = if (user == null) messages.getMessage("user.role.anonymous", null, locale) else "${user.name}(${user.loginId})"
        val typeName = messages.getMessage("resource.${type.resource}", null, type.resource, locale).orEmpty()
        return messages.getMessage("viaEmail.error.cannotCreate", arrayOf<Any>(who, typeName, "${project.owner}/${project.name}"), locale)
    }

    /** 첨부로 볼 파트를 저장한다. 콘텐츠 ID가 있는 것은 `cid`(꺾쇠 제외)로 찾을 수 있게 모아 돌려준다. */
    private fun saveAttachments(parts: Collection<MimePart>, containerType: ResourceType, containerId: Long, sender: User): Map<String, Attachment> {
        val result = mutableMapOf<String, Attachment>()
        for (part in parts) {
            val name = part.fileName?.let { MimeUtility.decodeText(it) }
            val attachment = attachments.store(part.inputStream, name, containerType, containerId, sender.loginId).attachment
            part.contentID?.let { result[it.trim().replace("<", "").replace(">", "")] = attachment }
        }
        return result
    }

    /** HTML 본문의 `cid:`를 첨부 주소로 바꾸고 태그 사이의 개행을 없앤다 (v1.6 `postprocessForHTML`). */
    private fun postprocess(html: String, stored: Map<String, Attachment>): String = HtmlCompressor().compress(replaceCid(html, stored))

    private fun replaceCid(html: String, stored: Map<String, Attachment>): String {
        val doc = Jsoup.parse(html).also { it.outputSettings().prettyPrint(false) }
        for (attribute in listOf("src", "href")) {
            for (tag in doc.select("*[$attribute]")) {
                val uri = tag.attr(attribute).trim()
                if (!uri.lowercase().startsWith("cid:")) continue
                val attachment = stored[uri.substring("cid:".length)] ?: continue
                tag.attr(attribute, siteUrls.path("/files/${attachment.id}"))
            }
        }
        val bodies = doc.getElementsByTag("body")
        return if (bodies.size > 0) bodies[0].html() else doc.html()
    }
}
