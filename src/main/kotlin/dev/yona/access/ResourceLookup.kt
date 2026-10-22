package dev.yona.access

import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.UserRepository
import org.springframework.stereotype.Component

/**
 * 종류와 번호로 권한 검사의 대상 리소스를 찾는다. v1.6 `models.resource.Resource.get(type, id)`와
 * 첨부의 `Attachment.asResource()`에 해당한다.
 *
 * 지금은 이식이 끝난 종류만 지원한다. 지원하지 않는 종류는 v1.6처럼 [IllegalArgumentException]이고, 번호에 해당하는
 * 대상이 없으면 v1.6의 `NullPointerException`처럼 오류이다. 새로운 종류를 이식할 때마다 [get]에 추가한다.
 */
@Component
class ResourceLookup(
    private val projects: ProjectRepository,
    private val issues: IssueRepository,
    private val issueComments: IssueCommentRepository,
    private val postings: PostingRepository,
    private val postingComments: dev.yona.domain.board.PostingCommentRepository,
    private val milestones: MilestoneRepository,
    private val users: UserRepository,
    private val organizations: OrganizationRepository,
    private val pullRequests: dev.yona.domain.pullrequest.PullRequestRepository
) {

    /** 리소스가 아직 있으면 `true`이다 (v1.6 `Resource.exists`). */
    fun exists(type: ResourceType, id: String): Boolean = try {
        get(type, id)
        true
    } catch (e: RuntimeException) {
        false
    }

    /** [type]과 [id]에 해당하는 리소스를 찾는다. */
    fun get(type: ResourceType, id: String): Resource {
        if (type == ResourceType.COMMIT) return commit(id)
        val longId = id.toLong()
        return when (type) {
            ResourceType.ISSUE_POST -> require(issues.findById(longId)).asResource()
            ResourceType.ISSUE_COMMENT -> require(issueComments.findById(longId)).asResource()
            ResourceType.NONISSUE_COMMENT -> require(postingComments.findById(longId)).asResource()
            ResourceType.BOARD_POST -> require(postings.findById(longId)).asResource()
            ResourceType.MILESTONE -> require(milestones.findById(longId)).asResource()
            ResourceType.USER -> require(users.findById(longId)).asResource()
            ResourceType.USER_AVATAR -> require(users.findById(longId)).avatarAsResource()
            ResourceType.PROJECT -> require(projects.findById(longId)).asResource()
            ResourceType.ORGANIZATION -> require(organizations.findById(longId)).asResource()
            ResourceType.PULL_REQUEST -> require(pullRequests.findById(longId)).asResource()
            else -> throw IllegalArgumentException("Unsupported resource type $type")
        }
    }

    /**
     * 첨부를 권한 검사의 대상으로 바꾼다 (v1.6 `Attachment.asResource`). 컨테이너가 프로젝트에 속하면 프로젝트 리소스,
     * 아니면 전역 리소스이다. 컨테이너를 담은 프로젝트를 잃었으면 오류이다.
     */
    fun attachmentResource(attachment: Attachment): Resource {
        val attachmentId = requireNotNull(attachment.id).toString()
        val containerType = requireNotNull(attachment.containerType)
        val containerId = attachment.containerId.toString()

        if (containerType == ResourceType.PROJECT) {
            val project = projects.findById(attachment.containerId).orElse(null)
                ?: throw RuntimeException(lostProjectMessage(attachment))
            return ProjectResource(attachmentId, ResourceType.ATTACHMENT, project, container = project.asResource())
        }

        val container = get(containerType, containerId)
        if (container !is GlobalResource) {
            val project = container.project ?: throw RuntimeException(lostProjectMessage(attachment))
            return ProjectResource(attachmentId, ResourceType.ATTACHMENT, project, container = container)
        }
        return GlobalResource(attachmentId, ResourceType.ATTACHMENT, container = container)
    }

    /**
     * 커밋 리소스이다. 번호는 `프로젝트번호:커밋해시`이다 (v1.6 `Commit.getAsResource`). 프로젝트가 없으면 오류이고, 커밋이 저장소에 있는지는 보지 않는다.
     */
    private fun commit(id: String): Resource {
        val project = projects.findById(id.substringBefore(':').toLong()).orElseThrow { NullPointerException("대상 리소스가 없습니다.") }
        return ProjectResource(id, ResourceType.COMMIT, project)
    }

    private fun <T> require(found: java.util.Optional<T>): T =
        found.orElseThrow { NullPointerException("대상 리소스가 없습니다.") }

    private fun lostProjectMessage(attachment: Attachment) = "An attachment '${attachment.name}' lost the project it belongs to"
}
