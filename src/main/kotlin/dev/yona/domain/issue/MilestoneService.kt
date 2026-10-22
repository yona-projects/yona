package dev.yona.domain.issue

import dev.yona.domain.enumeration.State
import dev.yona.domain.project.Project
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Calendar
import java.util.Date

/**
 * 마일스톤의 조회와 저장을 담당한다. v1.6 `Milestone`, `MilestoneApp`(`app/models/Milestone.java`,
 * `app/controllers/MilestoneApp.java`)에 해당한다. 진행 현황 계산은 [MilestoneStatusService]가 맡는다.
 */
@Service
class MilestoneService(
    private val milestones: MilestoneRepository,
    private val issues: IssueRepository,
    private val statuses: MilestoneStatusService
) {

    /**
     * 프로젝트의 마일스톤을 조회한다.
     *
     * @param state `ALL`이면 모든 상태
     * @param orderBy `dueDate`, `title`, `completionRate` 중 하나. 그 밖의 값은 마감일이다
     * @param descending 내림차순이면 `true`. 마감일이 없는 마일스톤은 오름차순이면 먼저, 내림차순이면 나중에 나온다(v1.6이 쓰던 MariaDB와 같다)
     */
    @Transactional(readOnly = true)
    fun list(project: Project, state: State, orderBy: String, descending: Boolean): List<Milestone> {
        val projectId = requireNotNull(project.id)
        val byCompletion = orderBy == COMPLETION_RATE
        val sort = when {
            byCompletion -> Sort.by("id")
            orderBy == "title" -> Sort.by(if (descending) Sort.Order.desc("title") else Sort.Order.asc("title"))
            else -> Sort.by(
                if (descending) Sort.Order.desc("dueDate").nullsLast() else Sort.Order.asc("dueDate").nullsFirst()
            ).and(Sort.by("id"))
        }
        val found = if (state == State.ALL) milestones.findByProjectId(projectId, sort) else milestones.findByProjectIdAndState(projectId, state, sort)
        if (!byCompletion) return found
        // 완료율은 계산해야 알 수 있으므로 메모리에서 정렬한다. 같은 완료율은 번호 순을 지킨다.
        val rates = found.associateWith { statuses.statusOf(it).completionRate }
        return if (descending) found.sortedByDescending { rates.getValue(it) } else found.sortedBy { rates.getValue(it) }
    }

    /** 프로젝트에서 [title]이 아직 쓰이지 않았으면 `true`이다. */
    @Transactional(readOnly = true)
    fun isUniqueTitle(project: Project, title: String?): Boolean =
        title == null || !milestones.existsByProjectIdAndTitle(requireNotNull(project.id), title)

    /** 마일스톤을 만든다. 마감일은 그날의 마지막 초(23:59:59)로 맞춘다. */
    @Transactional
    fun create(project: Project, title: String, contents: String?, state: State, dueDate: Date?): Milestone =
        milestones.save(Milestone(project = project, title = title, contents = contents, state = state, dueDate = lastSecondOfDay(dueDate)))

    /** 마일스톤의 제목, 내용, 상태, 마감일을 바꾼다. 마감일은 그날의 마지막 초로 맞춘다. */
    @Transactional
    fun update(milestone: Milestone, title: String, contents: String?, state: State, dueDate: Date?) {
        milestone.title = title
        milestone.contents = contents
        milestone.state = state
        milestone.dueDate = lastSecondOfDay(dueDate)
        milestones.save(milestone)
    }

    /** 마일스톤을 지운다. 속한 이슈의 마일스톤은 비운다. */
    @Transactional
    fun delete(milestone: Milestone) {
        issues.clearMilestone(requireNotNull(milestone.id))
        milestones.delete(milestone)
    }

    /** 마일스톤을 연다. */
    @Transactional
    fun open(milestone: Milestone) {
        milestone.state = State.OPEN
        milestones.save(milestone)
    }

    /** 마일스톤을 닫는다. */
    @Transactional
    fun close(milestone: Milestone) {
        milestone.state = State.CLOSED
        milestones.save(milestone)
    }

    companion object {
        const val COMPLETION_RATE = "completionRate"

        /** [date]의 시, 분, 초를 23:59:59로 바꾼다. 없으면 `null`이다 (v1.6 `JodaDateUtil.lastSecondOfDay`). */
        fun lastSecondOfDay(date: Date?): Date? = date?.let {
            Calendar.getInstance().apply { time = it; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.time
        }
    }
}
