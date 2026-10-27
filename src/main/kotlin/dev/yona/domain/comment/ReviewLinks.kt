package dev.yona.domain.comment

import dev.yona.domain.pullrequest.PullRequestChangesService
import dev.yona.util.SiteUrls
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 코드 리뷰 스레드와 댓글이 보이는 화면 주소이다. v1.6 `TemplateHelper.DiffRenderer.urlToContainer`, `urlToPullRequest`, `urlToCommit`,
 * `urlToCommentThread`와 `RouteUtil.getUrl(CommentThread)`, `getUrl(ReviewComment)`에 해당한다.
 *
 * - 풀 리퀘스트의 코드 범위 스레드: 두 기준 커밋 사이의 전체 변경(어긋난 스레드는 스레드의 커밋 변경)이나 커밋 하나의 변경. 파일 단위 스레드는 커밋 하나의 변경.
 *   그 밖은 변경 화면.
 * - 커밋의 스레드: 커밋 보기.
 */
@Component
class ReviewLinks(private val changes: PullRequestChangesService, private val siteUrls: SiteUrls) {

    /** 스레드가 들어 있는 화면(앵커 없음)이다. 컨텍스트 경로를 포함한다. 알 수 없으면 빈 문자열이다. */
    @Transactional(readOnly = true)
    fun container(thread: CommentThread): String = relativeContainer(thread).let { if (it.isEmpty()) it else siteUrls.path(it) }

    /** 스레드 앵커가 붙은 주소이다 (`#thread-번호`). */
    fun thread(thread: CommentThread): String = relativeContainer(thread).let { if (it.isEmpty()) "" else siteUrls.path(it) } + "#thread-" + thread.id

    /** 리뷰 댓글 앵커가 붙은 주소이다 (`#comment-번호`). */
    fun comment(comment: ReviewComment): String = container(requireNotNull(comment.thread)) + "#comment-" + comment.id

    /** 컨텍스트 경로를 뺀 스레드 주소이다. 알림의 "보기" 링크처럼 호출하는 쪽이 컨텍스트 경로를 붙일 때 쓴다. */
    @Transactional(readOnly = true)
    fun relativeThread(thread: CommentThread): String = relativeContainer(thread) + "#thread-" + thread.id

    /** 컨텍스트 경로를 뺀 리뷰 댓글 주소이다. */
    @Transactional(readOnly = true)
    fun relativeComment(comment: ReviewComment): String = relativeContainer(requireNotNull(comment.thread)) + "#comment-" + comment.id

    private fun relativeContainer(thread: CommentThread): String {
        val pullRequest = thread.pullRequest
        if (pullRequest != null) {
            val project = requireNotNull(pullRequest.toProject)
            val root = "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/pullRequest/${pullRequest.number}"
            return (
                when {
                    thread is CodeCommentThread && !thread.commitId.isNullOrEmpty() && !thread.prevCommitId.isNullOrEmpty() ->
                        "$root/changes/" + (if (changes.isOutdated(thread)) thread.commitId else "")
                    thread is CodeCommentThread && !thread.commitId.isNullOrEmpty() -> "$root/changes/${thread.commitId}"
                    thread is NonRangedCodeCommentThread && !thread.commitId.isNullOrEmpty() -> "$root/changes/${thread.commitId}"
                    else -> "$root/changes"
                }
            )
        }
        val project = requireNotNull(thread.project)
        val commitId = (thread as? CodeCommentThread)?.commitId ?: (thread as? NonRangedCodeCommentThread)?.commitId ?: return ""
        return "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/commit/$commitId"
    }
}
