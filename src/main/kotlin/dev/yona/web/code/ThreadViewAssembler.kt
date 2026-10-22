package dev.yona.web.code

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.CommentThread
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.markdown.ProjectMarkdownRenderer
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.web.AttachmentJson
import dev.yona.web.TemplateHelper
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * 코드 리뷰 스레드를 화면 값으로 바꾼다. v1.6 `app/views/partial_comment_thread.scala.html`,
 * `partial_comment_form_on_thread.scala.html`, `code/partial_nonrange_codecomment_thread.scala.html`의 값 계산에 해당한다.
 *
 * 닫힌 코드 범위 스레드는 접어서(`fold`) 보인다. 댓글은 만든 시각 순서이다. 댓글을 지우는 단추는 지울 권한이 있을 때만 나온다.
 * 스레드 상태를 바꾸는 단추는 스레드를 고칠 수 있을 때만 나온다.
 */
@Component
class ThreadViewAssembler(
    private val users: UserRepository,
    private val avatars: AvatarService,
    private val markdown: ProjectMarkdownRenderer,
    private val attachmentJson: AttachmentJson,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val helper: TemplateHelper,
    private val siteUrls: SiteUrls
) {

    private val mapper = JsonMapper.builder().build()

    fun view(thread: CommentThread): ThreadView {
        val project = requireNotNull(thread.project)
        val viewer = currentUserProvider.get().user
        val threadResource = ProjectResource(requireNotNull(thread.id).toString(), ResourceType.COMMENT_THREAD, project, authorId = thread.authorId)
        val open = thread.state == ThreadState.OPEN
        val canChangeState = accessControl.isAllowed(viewer, threadResource, Operation.UPDATE)
        val code = thread as? CodeCommentThread
        return ThreadView(
            id = requireNotNull(thread.id), state = (thread.state ?: ThreadState.OPEN).name.lowercase(), code = code != null,
            fold = code != null && thread.state == ThreadState.CLOSED,
            range = code?.let {
                linkedMapOf(
                    "path" to it.path.orEmpty(), "startSide" to it.startSide?.name.orEmpty(), "startLine" to it.startLine?.toString().orEmpty(), "startColumn" to it.startColumn?.toString().orEmpty(),
                    "endSide" to it.endSide?.name.orEmpty(), "endLine" to it.endLine?.toString().orEmpty(), "endColumn" to it.endColumn?.toString().orEmpty()
                )
            },
            comments = thread.reviewComments.sortedBy { it.createdDate }.map { comment(it, viewer) },
            stateUrl = if (canChangeState) siteUrls.path("/threads/${thread.id}/" + if (open) "close" else "open") else null,
            form = null
        )
    }

    private fun comment(comment: ReviewComment, viewer: User?): ThreadCommentView {
        val project = requireNotNull(comment.thread?.project)
        val author = comment.authorLoginId?.let { users.findByLoginId(it) }
        val resource = ProjectResource(requireNotNull(comment.id).toString(), ResourceType.REVIEW_COMMENT, project, container = null, authorId = comment.authorId)
        return ThreadCommentView(
            id = comment.id!!, loginId = comment.authorLoginId.orEmpty(), name = comment.authorName.orEmpty(),
            avatarUrl = author?.let { avatars.avatarUrl(it, AVATAR_SIZE) }.orEmpty(), userHref = siteUrls.path("/${comment.authorLoginId}"),
            ago = helper.agoOrDateString(comment.createdDate), agoTitle = helper.dateString(comment.createdDate),
            deleteUrl = if (accessControl.isAllowed(viewer, resource, Operation.DELETE)) siteUrls.path("/comments/${ResourceType.REVIEW_COMMENT.resource}/${comment.id}") else null,
            bodyHtml = markdown.render(comment.contents.orEmpty(), project, null),
            attachments = mapper.writeValueAsString(attachmentJson.list(ResourceType.REVIEW_COMMENT, comment.id!!))
        )
    }

    private companion object {
        const val AVATAR_SIZE = 32
    }
}
