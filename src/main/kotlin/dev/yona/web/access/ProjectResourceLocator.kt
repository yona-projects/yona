package dev.yona.web.access

import dev.yona.access.Resource
import dev.yona.access.asResource
import dev.yona.domain.comment.CommitCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.project.Project
import dev.yona.domain.pullrequest.PullRequestRepository
import org.springframework.stereotype.Component

/**
 * 요청 경로의 구간 위치로 권한 검사의 대상 리소스를 찾는다. v1.6 `Resource.getResourceObject`
 * (`app/models/resource/Resource.java:226-247`)이다.
 *
 * | 종류 | 식별자 위치(0부터, 0=소유자 1=프로젝트) | 예 |
 * |---|---|---|
 * | `PROJECT` | (이미 찾은 프로젝트) | |
 * | `MILESTONE` | 3, 마일스톤 번호 | `/:user/:project/milestone/:id` |
 * | `BOARD_POST` | 3, 게시글 번호 | `/:user/:project/post/:number` |
 * | `ISSUE_POST` | 3, 이슈 번호 | `/:user/:project/issue/:number` |
 * | `PULL_REQUEST` | 3, 풀 리퀘스트 번호 | `/:user/:project/pullRequest/:number` |
 * | `ISSUE_LABEL` | 4, 라벨 번호 | `/:user/:project/issue/label/:id` |
 * | `COMMIT_COMMENT` | 5, 코멘트 번호 | `/:user/:project/commit/:commitId/comments/:id/delete` |
 * | `ISSUE_LABEL_CATEGORY` | 5, 분류 번호 | `/:user/:project/issue/label/category/:id` |
 *
 * 마일스톤, 라벨, 분류, 코멘트는 번호만으로 찾아서 다른 프로젝트의 것도 찾는다(v1.6 결함, R-12).
 */
@Component
class ProjectResourceLocator(
    private val issues: IssueRepository,
    private val postings: PostingRepository,
    private val milestones: MilestoneRepository,
    private val labels: IssueLabelRepository,
    private val categories: IssueLabelCategoryRepository,
    private val pullRequests: PullRequestRepository,
    private val commitComments: CommitCommentRepository
) {

    /**
     * @param segments 요청 경로를 `/`로 나눈 구간(빈 구간 제외)
     * @return 찾은 리소스. 대상이 없으면 `null`
     * @throws UnsupportedOperationException 이 종류는 경로로 식별할 수 없을 때(v1.6의 `IllegalAccessError`)
     * @throws NumberFormatException 식별자 구간이 숫자가 아닐 때 (v1.6도 `NumberFormatException`)
     */
    fun locate(segments: List<String>, project: Project, type: ResourceType): Resource? {
        val projectId = requireNotNull(project.id)
        return when (type) {
            ResourceType.PROJECT -> project.asResource()
            ResourceType.MILESTONE -> milestones.findById(segments[3].toLong()).orElse(null)?.asResource()
            ResourceType.BOARD_POST -> postings.findByProjectIdAndNumber(projectId, segments[3].toLong())?.asResource()
            ResourceType.ISSUE_POST -> issues.findByProjectIdAndNumber(projectId, segments[3].toLong())?.asResource()
            ResourceType.ISSUE_LABEL -> labels.findById(segments[4].toLong()).orElse(null)?.asResource()
            ResourceType.PULL_REQUEST -> pullRequests.findByToProjectIdAndNumber(projectId, segments[3].toLong())?.asResource()
            ResourceType.COMMIT_COMMENT -> commitComments.findById(segments[5].toLong()).orElse(null)?.asResource()
            ResourceType.ISSUE_LABEL_CATEGORY -> categories.findById(segments[5].toLong()).orElse(null)?.asResource()
            else -> throw UnsupportedOperationException("경로로 식별할 수 없는 리소스 종류입니다: $type")
        }
    }
}
