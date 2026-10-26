package dev.yona.domain.issue

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.State
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 일괄 변경에서 한 이슈에 적용할 값이다. 값이 `null`이면 그 항목은 바꾸지 않는다 (v1.6 `IssueMassUpdate`).
 *
 * @property assigneeId 담당자의 사용자 번호. [NONE]이면 담당자를 비운다
 * @property milestoneId 마일스톤 번호. [NONE]이면 마일스톤을 비운다
 * @property dueDateChanged `true`이면 [dueDate]를 반영한다 (`null`이면 마감일을 지운다)
 */
data class MassEdit(
    val state: State? = null,
    val assigneeId: Long? = null,
    val milestoneId: Long? = null,
    val dueDateChanged: Boolean = false,
    val dueDate: Date? = null,
    val attachingLabelIds: List<Long> = emptyList(),
    val detachingLabelIds: List<Long> = emptyList()
) {
    companion object {
        /** 담당자나 마일스톤을 "없음"으로 지정하는 값이다 (v1.6 `Milestone.NULL_MILESTONE_ID`, 익명 사용자 번호). */
        const val NONE = -1L
    }
}

/**
 * 이슈 여러 개 또는 한 개의 담당자, 상태, 마일스톤, 라벨, 마감일을 바꾼다. v1.6 `IssueApp.massUpdate`와 `update*IfChanged`
 * (`app/controllers/IssueApp.java:389-560`)에 해당한다. 권한 확인은 호출하는 쪽이 한다.
 */
@Service
class IssueMassUpdateService(
    private val issues: IssueRepository,
    private val issueService: IssueService,
    private val users: UserRepository,
    private val milestones: MilestoneRepository,
    private val labels: IssueLabelRepository,
    private val events: IssueEventService,
    private val publisher: ApplicationEventPublisher
) {

    /** [issue]에 [edit]를 적용하고 수정 시각을 갱신한다. 바뀐 항목마다 타임라인 이벤트를 남기고 [IssueChangedActivity]를 발행한다. */
    @Transactional
    fun apply(issue: Issue, actor: User, edit: MassEdit) {
        val project = requireNotNull(issue.project)
        edit.assigneeId?.let { id ->
            val old = issue.assignee?.user
            val loginId = if (id == MassEdit.NONE) null else users.findById(id).orElse(null)?.loginId
            val new = issueService.findOrCreateAssignee(project, loginId)
            if (old?.id != new?.user?.id) {
                issue.assignee = new
                record(issue, actor, EventType.ISSUE_ASSIGNEE_CHANGED, old?.loginId, new?.user?.loginId)
            }
        }
        edit.state?.let { next ->
            val old = issue.state
            if (old != next) {
                issue.state = next
                record(issue, actor, EventType.ISSUE_STATE_CHANGED, old?.state, next.state)
            }
        }
        edit.milestoneId?.let { id ->
            val old = issue.milestone
            val new = if (id == MassEdit.NONE) null else milestones.findById(id).orElse(null)
            // v1.6 isMilestoneChanged: 옛 마일스톤과 같은 번호만 변경이 아니다. "없음"(-1)은 번호가 -1인 마일스톤으로 들어오므로 이미 없는 이슈에도 변경이다.
            if (id == MassEdit.NONE || new != null && old?.id != new.id) {
                issue.milestone = new
                record(issue, actor, EventType.ISSUE_MILESTONE_CHANGED, (old?.id ?: MassEdit.NONE).toString(), (new?.id ?: MassEdit.NONE).toString())
            }
        }
        if (edit.attachingLabelIds.isNotEmpty() || edit.detachingLabelIds.isNotEmpty()) {
            val added = edit.attachingLabelIds.mapNotNull { labels.findById(it).orElse(null) }
            val removed = edit.detachingLabelIds.mapNotNull { labels.findById(it).orElse(null) }
            issue.labels.addAll(added)
            issue.labels.removeAll(removed.toSet())
            // v1.6은 라벨을 이어 붙일 때 구분자를 넣지 않고, 중간 값을 합치지 않는 이벤트로 남긴다.
            record(issue, actor, EventType.ISSUE_LABEL_CHANGED, removed.joinToString("") { describe(it) }, added.joinToString("") { describe(it) }, skipWaypoints = false)
        }
        if (edit.dueDateChanged) issue.dueDate = MilestoneService.lastSecondOfDay(edit.dueDate)
        issue.updatedDate = Date()
        issues.save(issue)
    }

    private fun describe(label: IssueLabel) = "${label.category?.name} - ${label.name} ${label.color}"

    private fun record(issue: Issue, actor: User, type: EventType, old: String?, new: String?, skipWaypoints: Boolean = true) {
        events.record(issue, type, old, new, actor.loginId, skipWaypoints)
        publisher.publishEvent(IssueChangedActivity(issue, type, old, new, actor))
    }
}
