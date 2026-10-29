package dev.yona.web.pullrequest

import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestAssigneeService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.security.AvatarService
import dev.yona.util.SiteUrls
import dev.yona.util.UrlPaths
import dev.yona.web.issue.IssueFormOptions
import dev.yona.web.issue.IssueLabelTag
import dev.yona.web.issue.LabelGroupOption
import org.springframework.stereotype.Component

/** 담당자 선택 상자의 항목 하나이다. */
data class AssigneeOption(val id: Long, val displayName: String, val loginId: String, val avatarUrl: String, val selected: Boolean)

/** 읽기만 하는 화면에 보일 담당자이다. */
data class AssigneeView(val href: String, val avatarUrl: String, val displayName: String, val loginId: String)

/**
 * 풀 리퀘스트 보기의 담당자 영역이다(로드맵 X-14, `decisions.md` D-34).
 *
 * @property canEdit 풀 리퀘스트 수정 권한이 있으면 선택 상자를, 없으면 읽기만 보인다
 * @property action 선택을 보내는 주소
 * @property current 지금 담당자. 없으면 `null`
 * @property options 담당자 후보. 지금 담당자가 후보가 아니어도 목록에 들어 있다
 */
data class PullRequestAssigneeArea(val canEdit: Boolean, val action: String, val current: AssigneeView?, val options: List<AssigneeOption>)

/**
 * 풀 리퀘스트 보기의 라벨 영역이다.
 *
 * @property groups 받는 프로젝트의 라벨을 분류별로 묶은 선택지
 * @property selectedIds 붙어 있는 라벨 번호
 * @property tags 읽기만 하는 화면이 보일 라벨 칩(분류 이름, 라벨 이름 순)
 * @property listUrl 칩을 누르면 가는 풀 리퀘스트 목록 주소(라벨로 거른다)
 */
data class PullRequestLabelArea(
    val canEdit: Boolean, val action: String, val groups: List<LabelGroupOption>, val selectedIds: Set<Long>, val tags: List<IssueLabelTag>, val listUrl: String, val hasLabels: Boolean
)

/** 풀 리퀘스트 보기와 목록이 쓰는 담당자와 라벨 값을 만든다(로드맵 X-14, `decisions.md` D-34). */
@Component
class PullRequestMetaAssembler(
    private val assigneeService: PullRequestAssigneeService,
    private val labelService: IssueLabelService,
    private val formOptions: IssueFormOptions,
    private val avatars: AvatarService,
    private val siteUrls: SiteUrls
) {

    fun assignee(pullRequest: PullRequest, viewer: User?, canEdit: Boolean): PullRequestAssigneeArea {
        val project = requireNotNull(pullRequest.toProject)
        val base = siteUrls.path("${UrlPaths.projectPath(project.owner, project.name)}/pullRequest/${pullRequest.number}")
        val current = pullRequest.assignees.firstOrNull()
        val candidates = if (canEdit) assigneeService.candidates(pullRequest, viewer) else emptyList()
        val users = if (current != null && candidates.none { it.id == current.id }) candidates + current else candidates
        return PullRequestAssigneeArea(
            canEdit, "$base/assignee", current?.let { view(it, viewer) },
            users.map { AssigneeOption(requireNotNull(it.id), UserDisplay.of(it, viewer?.lang), it.loginId.orEmpty(), avatars.avatarUrl(it, AVATAR_SIZE), it.id == current?.id) }
        )
    }

    fun labels(pullRequest: PullRequest, canEdit: Boolean): PullRequestLabelArea {
        val project = requireNotNull(pullRequest.toProject)
        val base = siteUrls.path(UrlPaths.projectPath(project.owner, project.name))
        val projectLabels = if (canEdit) labelService.labels(project) else emptyList()
        return PullRequestLabelArea(
            canEdit, "$base/pullRequest/${pullRequest.number}/labels", formOptions.labelGroups(projectLabels), pullRequest.labels.mapNotNull { it.id }.toSet(),
            pullRequest.labels.sortedWith(compareBy({ it.category?.name }, { it.name })).map {
                IssueLabelTag(requireNotNull(it.id), it.category?.id, it.category?.name.orEmpty(), it.category?.isExclusive == true, it.name.orEmpty(), it.color.orEmpty())
            },
            "$base/pullRequests", projectLabels.isNotEmpty()
        )
    }

    private fun view(user: User, viewer: User?) = AssigneeView(siteUrls.path("/${user.loginId}"), avatars.avatarUrl(user, AVATAR_SIZE), UserDisplay.of(user, viewer?.lang), user.loginId.orEmpty())

    private companion object {
        const val AVATAR_SIZE = 20
    }
}
