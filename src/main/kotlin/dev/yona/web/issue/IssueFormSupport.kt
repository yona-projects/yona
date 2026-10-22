package dev.yona.web.issue

import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssueListService
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.MilestoneService
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.web.project.ProjectViewFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.ModelAndView

/** 하위 이슈 선택 상자의 프로젝트 선택지이다. */
data class ProjectOption(val id: Long, val owner: String, val name: String, val logoUrl: String)

/** 하위 이슈 선택 상자의 부모 이슈 선택지이다. */
data class ParentOption(val id: Long, val number: Long, val title: String)

/**
 * 이슈 등록, 수정 폼이 함께 쓰는 선택지와 모델을 만든다. v1.6 `issue/create`, `issue/edit` 템플릿이 템플릿 안에서 하던 조회를 묶었다.
 */
@Component
class IssueFormSupport(
    private val listService: IssueListService,
    private val labelService: IssueLabelService,
    private val milestoneService: MilestoneService,
    private val formOptions: IssueFormOptions,
    private val accessControl: AccessControl,
    private val views: ProjectViewFactory,
    private val issues: dev.yona.domain.issue.IssueRepository
) {

    /** 현재 요청의 사용자가 보는 [project]의 화면 정보이다. */
    fun projectView(project: Project) = views.of(project)

    /**
     * 폼 화면의 공통 모델을 채운다.
     *
     * @param currentIssueId 수정 중인 이슈의 번호(PK). 등록이면 `null`
     * @param parentIssueId 미리 고른 부모 이슈의 번호(PK)
     * @param selectedLabelIds 처음 선택해 둘 라벨
     */
    fun fill(
        mav: ModelAndView, project: Project, viewer: User?, currentIssueId: Long?, parentIssueId: Long?, selectedLabelIds: Set<Long> = emptySet()
    ): ModelAndView {
        val projectView = views.of(project)
        val parentOptions = listService.parentCandidates(project, PARENT_CANDIDATE_LIMIT)
            .filter { it.id != currentIssueId && it.id != parentIssueId }
            .map { ParentOption(requireNotNull(it.id), it.number ?: 0, it.title.orEmpty()) }
        val selectedParent = parentIssueId?.let { issues.findById(it).orElse(null) }
        return mav
            .addObject("projectView", projectView)
            .addObject("canAssign", accessControl.isProjectResourceCreatable(viewer, project, ResourceType.ISSUE_ASSIGNEE))
            .addObject("canMilestone", projectView.menuSetting.milestone == true && accessControl.isProjectResourceCreatable(viewer, project, ResourceType.ISSUE_MILESTONE))
            .addObject("openMilestones", milestoneService.list(project, State.OPEN, "dueDate", false).map { MilestoneOption(requireNotNull(it.id), it.title.orEmpty(), "OPEN") })
            .addObject("closedMilestones", milestoneService.list(project, State.CLOSED, "dueDate", false).map { MilestoneOption(requireNotNull(it.id), it.title.orEmpty(), "CLOSED") })
            .addObject("hasMilestones", milestoneService.list(project, State.ALL, "dueDate", false).isNotEmpty())
            .addObject("labelGroups", formOptions.labelGroups(labelService.labels(project)))
            .addObject("selectedLabelIds", java.util.LinkedHashSet(selectedLabelIds))
            .addObject("movableProjects", (viewer?.let { listService.movableProjects(it) } ?: emptyList()).map {
                ProjectOption(requireNotNull(it.id), it.owner.orEmpty(), it.name.orEmpty(), views.of(it).logoUrl)
            })
            .addObject("parentOptions", parentOptions)
            .addObject("selectedParent", selectedParent?.let { ParentOption(requireNotNull(it.id), it.number ?: 0, it.title.orEmpty()) })
            .addObject("showSubtask", parentIssueId != null || currentIssueId != null)
            .addObject("logoUrl", projectView.logoUrl)
    }

    private companion object {
        const val PARENT_CANDIDATE_LIMIT = 300
    }
}
