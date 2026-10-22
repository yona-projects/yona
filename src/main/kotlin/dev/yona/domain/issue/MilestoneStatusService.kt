package dev.yona.domain.issue

import dev.yona.domain.enumeration.State
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.text.SimpleDateFormat
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 마일스톤의 진행 현황이다. v1.6 `Milestone`의 계산 메서드들이 만들던 값을 한데 모은 것이다.
 *
 * @property milestone 대상 마일스톤
 * @property closedCount 닫힌 이슈 수 (`getNumClosedIssues`)
 * @property openCount 열린 이슈 수 (`getNumOpenIssues`)
 * @property completionRate 완료율(%). 닫힌 이슈 수를 마일스톤의 전체 이슈 수로 나눠 정수 부분만 취한다 (`getCompletionRate`)
 * @property dueDateString `yyyy-MM-dd` 형식의 마감일. 마감일이 없으면 `null` (`getDueDateString`)
 * @property until 마감일까지 남았거나 지난 기간을 설명하는 문구. 마감일이 없으면 `null` (`until`)
 * @property overDue 현재 시각이 마감일을 지났는지 여부 (`isOverDueDate`)
 */
data class MilestoneStatus(
    val milestone: Milestone,
    val closedCount: Int,
    val openCount: Int,
    val completionRate: Int,
    val dueDateString: String?,
    val until: String?,
    val overDue: Boolean
) {
    val id: Long? get() = milestone.id
    val title: String? get() = milestone.title
    val state: State? get() = milestone.state
}

/** 마일스톤의 조회와 진행 현황 계산을 맡는다. */
@Service
class MilestoneStatusService(
    private val milestones: MilestoneRepository,
    private val issues: IssueRepository,
    private val messages: MessageSource,
    private val clock: Clock
) {

    /**
     * 프로젝트의 열린 마일스톤을 마감일 오름차순으로 조회한다 (v1.6 `Milestone.findOpenMilestones`).
     * v1.6이 쓰던 MariaDB처럼 마감일이 없는 마일스톤이 먼저 나온다.
     */
    fun findOpenMilestones(projectId: Long): List<Milestone> =
        milestones.findByProjectIdAndState(projectId, State.OPEN, Sort.by(Sort.Order.asc("dueDate").nullsFirst()))

    /** [milestone]의 현황을 계산한다. 시각 기준은 주입된 시계이다. */
    fun statusOf(milestone: Milestone, now: Clock = clock): MilestoneStatus {
        val id = requireNotNull(milestone.id) { "저장되지 않은 마일스톤입니다." }
        val closed = issues.countByMilestoneIdAndState(id, State.CLOSED).toInt()
        val open = issues.countByMilestoneIdAndState(id, State.OPEN).toInt()
        val total = issues.countByMilestoneId(id).toInt()
        // v1.6은 0/0을 (int) NaN으로 계산해 0이 된다.
        val rate = if (total == 0) 0 else (closed.toDouble() / total.toDouble() * 100).toInt()
        val due = milestone.dueDate
        return MilestoneStatus(
            milestone = milestone,
            closedCount = closed,
            openCount = open,
            completionRate = rate,
            dueDateString = due?.let { SimpleDateFormat("yyyy-MM-dd").apply { timeZone = java.util.TimeZone.getTimeZone(now.zone) }.format(it) },
            until = due?.let { until(it, now) },
            overDue = due?.let { now.instant().isAfter(it.toInstant()) } ?: false
        )
    }

    private fun until(due: java.util.Date, now: Clock): String {
        val locale = LocaleContextHolder.getLocale()
        val dueDay = LocalDate.ofInstant(due.toInstant(), now.zone)
        val today = LocalDate.now(now)
        return when {
            dueDay == today -> messages.getMessage("common.time.today", null, locale)
            now.instant().isAfter(due.toInstant()) ->
                messages.getMessage("common.time.overday", arrayOf(ChronoUnit.DAYS.between(dueDay, today)), locale)
            else -> messages.getMessage("common.time.leftday", arrayOf(ChronoUnit.DAYS.between(today, dueDay)), locale)
        }
    }
}
