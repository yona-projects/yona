package dev.yona.web.issue

import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueListService
import dev.yona.domain.issue.MilestoneService
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.security.AvatarService
import org.springframework.stereotype.Component

/** 선택 상자에 보일 사용자이다. */
data class UserOption(val id: Long, val name: String, val displayName: String, val loginId: String, val avatarUrl: String)

/** 선택 상자에 보일 마일스톤이다. */
data class MilestoneOption(val id: Long, val title: String, val state: String)

/** 선택 상자에 보일 라벨이다. */
data class LabelOption(val id: Long, val name: String)

/** 분류별로 묶은 라벨이다. */
data class LabelGroupOption(val id: Long, val name: String, val exclusive: Boolean, val labels: List<LabelOption>)

/** 일괄 변경 막대의 선택지이다. */
data class MassUpdate(val assignable: List<UserOption>, val openMilestones: List<MilestoneOption>, val labels: List<LabelGroupOption>, val hasLabels: Boolean)

/** 이슈 목록, 마일스톤 보기 화면이 함께 쓰는 선택지를 만든다. */
@Component
class IssueFormOptions(
    private val listService: IssueListService,
    private val milestoneService: MilestoneService,
    private val accessControl: AccessControl,
    private val avatars: AvatarService
) {

    /** 일괄 변경 막대의 선택지이다. 담당자 후보, 열린 마일스톤, 라벨이다. */
    fun massUpdate(project: Project, viewer: User?, labels: List<IssueLabel>): MassUpdate {
        val assignable = listService.assignableUsers(project, viewer, accessControl.isSiteManager(viewer)).map {
            UserOption(requireNotNull(it.id), it.name.orEmpty(), UserDisplay.of(it, viewer?.lang), it.loginId.orEmpty(), avatars.avatarUrl(it, 32))
        }
        val open = milestoneService.list(project, State.OPEN, "dueDate", false).map { MilestoneOption(requireNotNull(it.id), it.title.orEmpty(), "OPEN") }
        return MassUpdate(assignable, open, labelGroups(labels), labels.isNotEmpty())
    }

    /** 라벨을 분류별로 묶는다. */
    fun labelGroups(labels: List<IssueLabel>): List<LabelGroupOption> =
        labels.groupBy { requireNotNull(it.category) }.map { (category, items) ->
            LabelGroupOption(requireNotNull(category.id), category.name.orEmpty(), category.isExclusive == true, items.map { LabelOption(requireNotNull(it.id), it.name.orEmpty()) })
        }
}
