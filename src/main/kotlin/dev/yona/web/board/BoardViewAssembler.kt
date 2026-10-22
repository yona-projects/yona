package dev.yona.web.board

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.misc.UnwatchRepository
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.markdown.MarkdownRenderer
import dev.yona.markdown.ProjectMarkdownRenderer
import dev.yona.repository.ProjectReadmeService
import dev.yona.security.AvatarService
import dev.yona.web.AttachmentListing
import dev.yona.web.TemplateHelper
import dev.yona.web.issue.ChildCommentView
import dev.yona.web.issue.CommentView
import dev.yona.web.issue.IssueFormOptions
import dev.yona.web.issue.IssueLabelTag
import dev.yona.web.issue.PersonView
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

/** 게시글 보기 화면이 필요로 하는 값을 모은다. v1.6 `board/view.scala.html`, `partial_comments.scala.html`이 계산하던 값이다. */
@Component
class BoardViewAssembler(
    private val postingComments: PostingCommentRepository,
    private val users: UserRepository,
    private val watches: WatchRepository,
    private val unwatches: UnwatchRepository,
    private val accessControl: AccessControl,
    private val avatars: AvatarService,
    private val markdown: ProjectMarkdownRenderer,
    private val rawMarkdown: MarkdownRenderer,
    private val attachmentListing: AttachmentListing,
    private val labelService: IssueLabelService,
    private val formOptions: IssueFormOptions,
    private val readme: ProjectReadmeService,
    private val helper: TemplateHelper,
    private val entityManager: EntityManager
) {

    fun assemble(project: Project, post: Posting, viewer: User?): PostDetail {
        val postId = requireNotNull(post.id)
        val resource = post.asResource()
        val lang = viewer?.lang
        // README 글은 저장소 README를 본문으로 보여 준다. 저장소에 README가 없으면 빈 본문이다 (v1.6 `StringUtils.defaultString(BareRepository.readREADME(project))`).
        val body = if (post.readme == true && project.vcs == "GIT") readme.readFirstFound(project).orEmpty() else post.body.orEmpty()
        val author = post.authorLoginId?.let { users.findByLoginIdIgnoreCase(it) }
        val allComments = postingComments.findByPostingIdOrderByCreatedDateAscIdAsc(postId)
        val projectLabels = labelService.labels(project)
        val tags = post.labels.sortedWith(compareBy({ it.category?.name }, { it.name })).map {
            IssueLabelTag(requireNotNull(it.id), it.category?.id, it.category?.name.orEmpty(), it.category?.isExclusive == true, it.name.orEmpty(), it.color.orEmpty())
        }
        val history = post.history
        return PostDetail(
            id = postId, number = post.number ?: 0, title = post.title.orEmpty(), body = body,
            bodyHtml = if (body.isEmpty()) null else markdown.render(body, project, lang),
            historyHtml = if (history.isNullOrEmpty()) null else rawMarkdown.sanitize(history),
            createdTitle = helper.dateString(post.createdDate), createdAgo = helper.agoOrDateString(post.createdDate),
            authorLoginId = post.authorLoginId, authorName = author?.name ?: post.authorName, authorAvatarUrl = author?.let { avatars.avatarUrl(it) }.orEmpty(),
            viewerAnonymous = viewer == null,
            canUpdate = accessControl.isAllowed(viewer, resource, Operation.UPDATE), canDelete = accessControl.isAllowed(viewer, resource, Operation.DELETE),
            canWatch = accessControl.isAllowed(viewer, resource, Operation.WATCH), watching = viewer != null && isWatching(viewer, postId),
            canComment = accessControl.isResourceCreatable(viewer, resource, ResourceType.NONISSUE_COMMENT),
            labels = tags, labelGroups = formOptions.labelGroups(projectLabels), selectedLabelIds = LinkedHashSet(tags.map { it.id }),
            hasProjectLabels = projectLabels.isNotEmpty(), attachmentsJson = attachmentListing.fileListJson(ResourceType.BOARD_POST, postId),
            comments = allComments.filter { it.parentComment == null }.map { commentView(project, post, it, viewer) }, commentCount = allComments.size
        )
    }

    private fun commentView(project: Project, post: Posting, comment: PostingComment, viewer: User?): CommentView {
        val lang = viewer?.lang
        val resource = comment.asResource()
        val id = requireNotNull(comment.id)
        val author = comment.authorLoginId?.let { users.findByLoginIdIgnoreCase(it) }
        val children = postingComments.findByParentCommentIdOrderByIdAsc(id)
        val originalEmail = entityManager.createQuery(
            "select count(o) from OriginalEmail o where o.resourceType = :t and o.resourceId = :i", java.lang.Long::class.java
        ).setParameter("t", ResourceType.NONISSUE_COMMENT).setParameter("i", id.toString()).singleResult.toLong() > 0
        return CommentView(
            id = id, author = author?.let { person(it, lang) } ?: PersonView(comment.authorLoginId, comment.authorName, comment.authorName.orEmpty(), comment.authorName.orEmpty(), "", null),
            createdTitle = helper.dateString(comment.createdDate), createdAgo = helper.agoOrDateString(comment.createdDate),
            contents = comment.contents.orEmpty(), contentsHtml = markdown.render(comment.contents.orEmpty(), project, lang),
            own = viewer != null && comment.authorLoginId == viewer.loginId,
            canRead = accessControl.isAllowed(viewer, resource, Operation.READ), canUpdate = accessControl.isAllowed(viewer, resource, Operation.UPDATE),
            canDelete = accessControl.isAllowed(viewer, resource, Operation.DELETE),
            voters = emptyList(), votedByMe = false, viewerAnonymous = viewer == null,
            attachmentsJson = attachmentListing.fileListJson(ResourceType.NONISSUE_COMMENT, id),
            attachments = attachmentListing.fileList(ResourceType.NONISSUE_COMMENT, id)?.get("attachments").orEmpty(), viaEmail = originalEmail,
            childAnchorIds = children.mapNotNull { it.id },
            children = children.map { child ->
                val childAuthor = child.authorId?.let { users.findById(it).orElse(null) }
                ChildCommentView(
                    requireNotNull(child.id), markdown.render(child.contents.orEmpty(), project, lang), childAuthor?.loginId, childAuthor?.let { UserDisplay.pureName(it, lang) }.orEmpty(),
                    helper.dateString(child.createdDate), helper.agoOrDateString(child.createdDate), accessControl.isAllowed(viewer, child.asResource(), Operation.DELETE)
                )
            },
            canReply = accessControl.isResourceCreatable(viewer, post.asResource(), ResourceType.NONISSUE_COMMENT)
        )
    }

    private fun person(user: User, lang: String?) = PersonView(user.loginId, user.name, UserDisplay.of(user, lang), UserDisplay.pureName(user, lang), avatars.avatarUrl(user), user.email)

    private fun isWatching(viewer: User, postId: Long): Boolean {
        val userId = requireNotNull(viewer.id)
        return watches.existsByUserIdAndResourceTypeAndResourceId(userId, ResourceType.BOARD_POST, postId.toString()) &&
            !unwatches.existsByUserIdAndResourceTypeAndResourceId(userId, ResourceType.BOARD_POST, postId.toString())
    }

}
