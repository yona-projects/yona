package dev.yona.domain.project

import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.MilestoneStatusService
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.User
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 담당자별 열린 이슈 한 줄이다. [percent]는 열린 최상위 이슈 수 대비 비율이다. */
data class DashboardAssigneeRow(val user: User, val count: Long, val percent: Int)

/** 마일스톤별 열린 이슈 한 줄이다. [completionRate]는 마일스톤 전체 이슈 대비 닫힌 이슈 비율이다. */
data class DashboardMilestoneRow(val milestoneId: Long, val title: String, val count: Long, val completionRate: Int)

/** 라벨별 열린 이슈 한 줄이다. */
data class DashboardLabelRow(val labelId: Long, val name: String, val count: Long)

/** 분류별 라벨 묶음이다. */
data class DashboardLabelGroup(val category: String, val labels: List<DashboardLabelRow>)

/**
 * 대시보드의 담당자별 영역이다.
 *
 * @property empty 열린 이슈(하위 이슈 제외)가 하나도 없으면 `true`이며 이때 [rows], [noAssignee]는 쓰지 않는다
 * @property noAssignee 담당자가 없는 열린 이슈 수와 비율이다
 */
data class DashboardByAssignee(val empty: Boolean, val rows: List<DashboardAssigneeRow>, val noAssigneeCount: Long, val noAssigneePercent: Int)

/**
 * 대시보드의 마일스톤별 영역이다.
 *
 * @property empty 열린 마일스톤이 없으면 `true`이다
 * @property noMilestoneCount 마일스톤이 없는 열린 이슈 수이다
 */
data class DashboardByMilestone(val empty: Boolean, val rows: List<DashboardMilestoneRow>, val noMilestoneCount: Long)

/** 대시보드의 풀 리퀘스트 영역이다. [openCount]는 받은 열린 풀 리퀘스트 전체 수이다. */
data class DashboardPullRequests(val recent: List<PullRequest>, val openCount: Long)

/**
 * 프로젝트 홈 대시보드 탭의 집계이다. v1.6 `partial_dashboard*.scala.html`과 `Issue.countOpenIssuesBy*`에 해당한다.
 *
 * 담당자별과 라벨별 수는 하위 이슈를 포함하지만 비율의 분모는 하위 이슈를 뺀 열린 이슈 수라 100%를 넘을 수 있다(R-68).
 * v1.6의 라벨 분류 순서는 해시 순서라 v2는 이름순으로 정한다(A-102).
 */
@Service
class ProjectDashboardService(
    private val issues: IssueRepository,
    private val assignees: AssigneeRepository,
    private val labels: IssueLabelRepository,
    private val milestones: MilestoneStatusService,
    private val pullRequests: PullRequestRepository
) {

    /** 열린 최상위 이슈 수이다. 비율의 분모로 쓴다 (v1.6 `Issue.countIssues(project.id, State.OPEN)`). */
    @Transactional(readOnly = true)
    fun openTopLevelCount(project: Project): Long = issues.countByProjectIdAndParentIsNullAndState(requireNotNull(project.id), State.OPEN)

    @Transactional(readOnly = true)
    fun byAssignee(project: Project): DashboardByAssignee {
        val projectId = requireNotNull(project.id)
        val total = openTopLevelCount(project)
        if (total == 0L) return DashboardByAssignee(true, emptyList(), 0, 0)
        val rows = assignees.findByProjectIdOrderByIdAsc(projectId).mapNotNull { assignee ->
            val count = issues.countByProjectIdAndAssigneeIdAndState(projectId, requireNotNull(assignee.id), State.OPEN)
            val user = assignee.user
            if (count > 0 && user != null) DashboardAssigneeRow(user, count, percent(count, total)) else null
        }
        val none = issues.countByProjectIdAndAssigneeIsNullAndState(projectId, State.OPEN)
        return DashboardByAssignee(false, rows, none, percent(none, total))
    }

    @Transactional(readOnly = true)
    fun byMilestone(project: Project): DashboardByMilestone {
        val projectId = requireNotNull(project.id)
        val open = milestones.findOpenMilestones(projectId)
        if (open.isEmpty()) return DashboardByMilestone(true, emptyList(), 0)
        val rows = open.mapNotNull { milestone ->
            val count = issues.countByProjectIdAndMilestoneIdAndState(projectId, requireNotNull(milestone.id), State.OPEN)
            if (count > 0) DashboardMilestoneRow(requireNotNull(milestone.id), milestone.title.orEmpty(), count, milestones.statusOf(milestone).completionRate) else null
        }
        return DashboardByMilestone(false, rows, issues.countByProjectIdAndMilestoneIsNullAndState(projectId, State.OPEN))
    }

    /** 최근 받은 열린 풀 리퀘스트 10개와 열린 요청 전체 수이다. */
    @Transactional(readOnly = true)
    fun pullRequests(project: Project): DashboardPullRequests {
        val projectId = requireNotNull(project.id)
        val newest = PageRequest.of(0, RECENT_PULL_REQUESTS, Sort.by(Sort.Order.desc("created"), Sort.Order.desc("id")))
        return DashboardPullRequests(pullRequests.findByToProjectIdAndState(projectId, State.OPEN, newest), pullRequests.countByToProjectIdAndState(projectId, State.OPEN))
    }

    /** 라벨별 열린 이슈 수를 분류 이름, 라벨 이름 순으로 묶는다. */
    @Transactional(readOnly = true)
    fun byLabel(project: Project): List<DashboardLabelGroup> {
        val projectId = requireNotNull(project.id)
        return labels.findByProjectIdOrderByCategoryNameAscNameAsc(projectId)
            .groupBy { it.category?.name.orEmpty() }
            .map { (category, group) ->
                DashboardLabelGroup(category, group.map { DashboardLabelRow(requireNotNull(it.id), it.name.orEmpty(), issues.countByLabel(projectId, requireNotNull(it.id), State.OPEN)) })
            }
    }

    /** v1.6 `getPercent(수, 분모)`: 나눈 값의 정수 부분이다. 분모가 0이면 0이다. */
    private fun percent(count: Long, total: Long): Int = if (total == 0L) 0 else (count.toDouble() / total.toDouble() * 100).toInt()

    private companion object {
        const val RECENT_PULL_REQUESTS = 10
    }
}
