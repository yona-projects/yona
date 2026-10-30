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
    private val attachmentListing: dev.yona.web.AttachmentListing,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val helper: TemplateHelper,
    private val siteUrls: SiteUrls,
    private val originalEmails: dev.yona.domain.notification.OriginalEmailRepository
) {

    private val mapper = JsonMapper.builder().build()

    fun view(thread: CommentThread): ThreadView {
        val project = requireNotNull(thread.project)
        val viewer = currentUserProvider.get().user
        val threadResource = ProjectResource(requireNotNull(thread.id).toString(), ResourceType.COMMENT_THREAD, project, authorId = thread.authorId)
        val open = thread.state == ThreadState.OPEN
        val canChangeState = accessControl.isAllowed(viewer, threadResource, Operation.UPDATE)
        val code = thread as? CodeCommentThread
        // 임시 저장 리뷰(X-26, D-38): 내 임시 스레드의 댓글과 공개 스레드에 단 내 임시 답글은 요청 속성의 덧붙임에서 온다
        val overlay = PendingOverlay.current()
        val pendingThread = thread.isPending
        val shown = if (pendingThread) overlay?.commentsByPendingThread?.get(thread.id).orEmpty() else thread.reviewComments + overlay?.repliesByThread?.get(thread.id).orEmpty()
        return ThreadView(
            id = requireNotNull(thread.id), state = (thread.state ?: ThreadState.OPEN).name.lowercase(), code = code != null,
            fold = code != null && thread.state == ThreadState.CLOSED,
            range = code?.let {
                linkedMapOf(
                    "path" to it.path.orEmpty(), "startSide" to it.startSide?.name.orEmpty(), "startLine" to it.startLine?.toString().orEmpty(), "startColumn" to it.startColumn?.toString().orEmpty(),
                    "endSide" to it.endSide?.name.orEmpty(), "endLine" to it.endLine?.toString().orEmpty(), "endColumn" to it.endColumn?.toString().orEmpty()
                )
            },
            comments = shown.sortedBy { it.createdDate }.map { comment(it, viewer, overlay) },
            stateUrl = if (canChangeState && !pendingThread) siteUrls.path("/threads/${thread.id}/" + if (open) "close" else "open") else null,
            form = form(thread, project, viewer),
            pending = pendingThread
        )
    }

    /** 스레드 아래의 답글 입력 영역이다. 풀 리퀘스트의 스레드는 리뷰 댓글, 커밋의 스레드는 커밋 코멘트를 만들 권한이 있을 때만 나온다 (v1.6 `partial_comment_form_on_thread`). */
    private fun form(thread: CommentThread, project: dev.yona.domain.project.Project, viewer: User?): ThreadFormView? {
        val pullRequest = thread.pullRequest
        val type = if (pullRequest != null) ResourceType.REVIEW_COMMENT else ResourceType.COMMIT_COMMENT
        if (viewer == null || !accessControl.isProjectResourceCreatable(viewer, project, type)) return null
        val commitId = (thread as? CodeCommentThread)?.commitId ?: (thread as? dev.yona.domain.comment.NonRangedCodeCommentThread)?.commitId.orEmpty()
        val action = if (pullRequest != null) {
            siteUrls.path("${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/pullRequest/${pullRequest.id}/comments") + (if (commitId.isNotEmpty()) "?commitId=$commitId" else "?commitId=")
        } else {
            siteUrls.path("${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/commit/$commitId/comments")
        }
        // 풀 리퀘스트 스레드에는 "리뷰에 추가"(임시 저장) 단추도 둔다. 임시 스레드의 답글은 서버가 항상 임시로 받으므로 단추가 필요 없다
        return ThreadFormView(action, requireNotNull(thread.id), type.name, avatars.avatarUrl(viewer, AVATAR_SIZE), siteUrls.path("/${viewer.loginId}"), viewer.name.orEmpty(), false, pendingButton = pullRequest != null && !thread.isPending)
    }

    private fun comment(comment: ReviewComment, viewer: User?, overlay: PendingOverlay?): ThreadCommentView {
        val project = requireNotNull(comment.thread?.project)
        val author = comment.authorLoginId?.let { users.findByLoginId(it) }
        val resource = ProjectResource(requireNotNull(comment.id).toString(), ResourceType.REVIEW_COMMENT, project, container = null, authorId = comment.authorId)
        return ThreadCommentView(
            id = comment.id!!, loginId = comment.authorLoginId.orEmpty(), name = comment.authorName.orEmpty(),
            avatarUrl = author?.let { avatars.avatarUrl(it, AVATAR_SIZE) }.orEmpty(), userHref = siteUrls.path("/${comment.authorLoginId}"),
            ago = helper.agoOrDateString(comment.createdDate), agoTitle = helper.dateString(comment.createdDate),
            // 임시 댓글은 본인만 보고 전용 주소로 지운다(공개 댓글 삭제 경로는 숨겨진 행을 찾지 못한다)
            deleteUrl = if (comment.isPending) overlay?.let { "${it.deleteBase}/${comment.id}/delete" }
                else if (accessControl.isAllowed(viewer, resource, Operation.DELETE)) siteUrls.path("/comments/${ResourceType.REVIEW_COMMENT.resource}/${comment.id}") else null,
            bodyHtml = markdown.render(comment.contents.orEmpty(), project, null),
            attachments = attachmentListing.fileListJson(ResourceType.REVIEW_COMMENT, comment.id!!),
            viaEmail = originalEmails.findByResourceTypeAndResourceId(ResourceType.REVIEW_COMMENT, comment.id.toString()).isNotEmpty(),
            pending = comment.isPending,
            // 공개 댓글은 작성자와 지울 권한이 있는 사람(관리자)이, 임시 댓글은 작성자만 고친다(GitHub 기준, D-38 7번)
            editUrl = if (viewer != null && (if (comment.isPending) comment.authorId == viewer.id else accessControl.isAllowed(viewer, resource, Operation.UPDATE))) siteUrls.path("/comments/review_comment/${comment.id}/edit") else null,
            rawContents = comment.contents.orEmpty(), edited = comment.editedDate != null
        )
    }

    private companion object {
        const val AVATAR_SIZE = 64
    }
}
