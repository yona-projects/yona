package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * yona `utils/RouteUtil.java`(리소스별 URL) + `NotificationEvent.getUrlToView()`/`getProject()`
 * 대응 (P1-27). yona는 Play 라우트 리버스가 없어 컨트롤러의 실제 `@GetMapping` 경로를 그대로 문자열로
 * 구성한다. `CommentThread.urlToContainer()`의 "outdated diff의 특정 커밋으로 링크" 세부 분기
 * (TemplateHelper.scala의 specificChange/isOutdated)는 코드리뷰 화면 내 앵커 정밀도에 관한 것으로,
 * 여기서는 PR/커밋 페이지로 링크하는 것까지만 재현하고 그 세부 분기는 생략한다(기능 누락이 아니라
 * "어느 diff 특정 커밋을 보여줄지"의 UI 라우팅 미세조정).
 */
@Component
class NotificationUrlResolver(
    private val issueRepository: IssueRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val commitCommentRepository: CommitCommentRepository,
    private val reviewCommentRepository: ReviewCommentRepository,
    private val commentThreadRepository: CommentThreadRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val organizationRepository: OrganizationRepository,
    @Value("\${yona.base-url:}")
    private val baseUrl: String
) {
    private val logger = LoggerFactory.getLogger(NotificationUrlResolver::class.java)

    fun getUrlToView(event: NotificationEvent): String? {
        return when (event.eventType) {
            EventType.MEMBER_ENROLL_REQUEST ->
                projectOf(event)?.let { "$baseUrl/${it.owner}/${it.name}/members" }
            EventType.MEMBER_ENROLL_ACCEPT ->
                projectOf(event)?.let { "$baseUrl/${it.owner}/${it.name}" }
            EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST ->
                organizationOf(event)?.let { "$baseUrl/organizations/${it.name}/members" }
            EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT ->
                organizationOf(event)?.let { "$baseUrl/organizations/${it.name}" }
            EventType.NEW_COMMIT ->
                projectOf(event)?.let { "$baseUrl/${it.owner}/${it.name}/commits" }
            else -> getUrl(event.resourceType, event.resourceId)
        }
    }

    private fun projectOf(event: NotificationEvent): Project? {
        return when (event.resourceType) {
            ResourceType.PROJECT -> event.resourceId.toLongOrNull()?.let { projectRepository.findById(it).orElse(null) }
            else -> null
        }
    }

    private fun organizationOf(event: NotificationEvent) =
        event.resourceId.toLongOrNull()?.let { organizationRepository.findById(it).orElse(null) }

    fun getUrl(resourceType: ResourceType, resourceId: String): String? {
        val id = resourceId.toLongOrNull() ?: return null
        return try {
            when (resourceType) {
                ResourceType.ISSUE_POST -> issueRepository.findById(id).orElse(null)?.let { issue ->
                    "$baseUrl/${issue.project.owner}/${issue.project.name}/issue/${issue.number}"
                }
                ResourceType.ISSUE_COMMENT -> issueCommentRepository.findById(id).orElse(null)?.let { comment ->
                    getUrl(ResourceType.ISSUE_POST, comment.issue.id.toString())?.plus("#comment-${comment.id}")
                }
                ResourceType.NONISSUE_COMMENT -> postingCommentRepository.findById(id).orElse(null)?.let { comment ->
                    getUrl(ResourceType.BOARD_POST, comment.posting.id.toString())?.plus("#comment-${comment.id}")
                }
                ResourceType.BOARD_POST -> postingRepository.findById(id).orElse(null)?.let { posting ->
                    "$baseUrl/${posting.project.owner}/${posting.project.name}/post/${posting.number}"
                }
                ResourceType.COMMIT_COMMENT -> commitCommentRepository.findById(id).orElse(null)?.let { comment ->
                    val project = comment.project ?: return null
                    "$baseUrl/${project.owner}/${project.name}/commit/${comment.commitId}#comment-${comment.id}"
                }
                ResourceType.PULL_REQUEST -> pullRequestRepository.findById(id).orElse(null)?.let { pr ->
                    "$baseUrl/${pr.toProject.owner}/${pr.toProject.name}/pull/${pr.number}"
                }
                ResourceType.REVIEW_COMMENT -> reviewCommentRepository.findById(id).orElse(null)?.let { comment ->
                    val thread = comment.thread ?: return null
                    urlToContainer(thread)?.plus("#comment-${comment.id}")
                }
                ResourceType.COMMENT_THREAD -> commentThreadRepository.findById(id).orElse(null)?.let { thread ->
                    urlToContainer(thread)?.plus("#thread-${thread.id}") ?: ""
                }
                ResourceType.USER_AVATAR -> userRepository.findById(id).orElse(null)?.let { user ->
                    "$baseUrl/user/${user.loginId}"
                }
                ResourceType.PROJECT -> projectRepository.findById(id).orElse(null)?.let { project ->
                    "$baseUrl/${project.owner}/${project.name}"
                }
                else -> null
            }
        } catch (e: Exception) {
            logger.error("Failed to get a url to the resource", e)
            null
        }
    }

    private fun urlToContainer(thread: CommentThread): String? {
        val pullRequest = thread.pullRequest
        return if (pullRequest != null) {
            val project = pullRequest.toProject
            "$baseUrl/${project.owner}/${project.name}/pull/${pullRequest.number}"
        } else {
            val project = thread.project ?: return null
            "$baseUrl/${project.owner}/${project.name}/commit/${thread.commitId}"
        }
    }
}
