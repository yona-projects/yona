package dev.yona.domain.misc

import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.CommitCommentRepository
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.comment.ReviewLinks
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.UserRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 리소스 종류와 번호로 그 리소스를 보여 주는 화면의 주소를 만든다. v1.6 `utils.RouteUtil.getUrl(resourceType, resourceId)`이다
 * (`app/utils/RouteUtil.java:26-62`).
 *
 * 알림 메일의 "보기" 링크와 내 파일 목록의 위치 열이 쓴다. 주소는 컨텍스트 경로를 뺀 경로이다.
 */
@Component
class ResourceUrls(
    private val users: UserRepository,
    private val projects: ProjectRepository,
    private val issues: IssueRepository,
    private val issueComments: IssueCommentRepository,
    private val postings: PostingRepository,
    private val postingComments: PostingCommentRepository,
    private val pullRequests: PullRequestRepository,
    private val reviewComments: ReviewCommentRepository,
    private val commitComments: CommitCommentRepository,
    private val commentThreads: CommentThreadRepository,
    private val reviewLinks: ReviewLinks
) {

    /**
     * 리소스의 화면 주소이다. 지원하지 않는 종류, 숫자가 아닌 번호, 없는 대상은 `null`이다
     * (v1.6은 지원하지 않는 종류에서 오류를 기록하고 `null`을 돌려준다).
     */
    @Transactional(readOnly = true)
    fun urlOf(type: ResourceType?, resourceId: String): String? {
        val id = resourceId.toLongOrNull() ?: return null
        return when (type) {
            ResourceType.ISSUE_POST -> issues.findById(id).orElse(null)?.let { "/${it.project?.owner}/${it.project?.name}/issue/${it.number}" }
            ResourceType.ISSUE_COMMENT -> issueComments.findById(id).orElse(null)?.let { c ->
                c.issue?.let { "/${it.project?.owner}/${it.project?.name}/issue/${it.number}#comment-${c.id}" }
            }
            ResourceType.BOARD_POST -> postings.findById(id).orElse(null)?.let { "/${it.project?.owner}/${it.project?.name}/post/${it.number}" }
            ResourceType.NONISSUE_COMMENT -> postingComments.findById(id).orElse(null)?.let { c ->
                c.posting?.let { "/${it.project?.owner}/${it.project?.name}/post/${it.number}#comment-${c.id}" }
            }
            ResourceType.PROJECT -> projects.findById(id).orElse(null)?.let { "/${it.owner}/${it.name}" }
            ResourceType.REVIEW_COMMENT -> reviewComments.findById(id).orElse(null)?.let { reviewLinks.relativeComment(it) }
            ResourceType.COMMENT_THREAD -> commentThreads.findById(id).orElse(null)?.let { reviewLinks.relativeThread(it) }
            ResourceType.COMMIT_COMMENT -> commitComments.findById(id).orElse(null)?.let { c -> c.project?.let { "/${it.owner}/${it.name}/commit/${c.commitId}#comment-${c.id}" } }
            ResourceType.PULL_REQUEST -> pullRequests.findById(id).orElse(null)?.let { r -> r.toProject?.let { "/${it.owner}/${it.name}/pullRequest/${r.number}" } }
            ResourceType.USER_AVATAR -> users.findById(id).orElse(null)?.let { "/${it.loginId}" }
            else -> null
        }
    }
}
