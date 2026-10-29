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

/** 읽기만 하는 화면과 선택 상자의 현재 값에 쓰는 담당자이다. [name]은 사용자 이름, [displayName]은 화면 표시 이름이다. */
data class AssigneeView(val href: String, val avatarUrl: String, val displayName: String, val loginId: String, val name: String)

/**
 * 풀 리퀘스트 보기의 담당자 영역이다(로드맵 X-14, `decisions.md` D-34). 이슈 화면처럼 선택하면 Ajax로 JSON을 보낸다.
 *
 * @property canEdit 풀 리퀘스트 수정 권한이 있으면 선택 상자를, 없으면 읽기만 보인다
 * @property assigneesUrl 선택한 담당자를 JSON(`{"assignees":["로그인ID"]}`)으로 보내는 주소
 * @property candidatesUrl 담당자 후보를 검색하는 프로젝트 단위 API 주소(이슈 화면과 같다)
 * @property timelineUrl 변경 뒤 다시 불러올 타임라인 조각의 주소
 * @property current 지금 담당자. 없으면 `null`
 */
data class PullRequestAssigneeArea(val canEdit: Boolean, val assigneesUrl: String, val candidatesUrl: String, val timelineUrl: String, val current: AssigneeView?)

/**
 * 풀 리퀘스트 보기의 라벨 영역이다.
 *
 * @property labelsUrl 붙인 라벨과 뗀 라벨(`attachingLabelIds`, `detachingLabelIds`)을 보내는 주소
 * @property groups 받는 프로젝트의 라벨을 분류별로 묶은 선택지
 * @property selectedIds 붙어 있는 라벨 번호
 * @property tags 읽기만 하는 화면이 보일 라벨 칩(분류 이름, 라벨 이름 순)
 * @property listUrl 칩을 누르면 가는 풀 리퀘스트 목록 주소(라벨로 거른다)
 */
data class PullRequestLabelArea(
    val canEdit: Boolean, val labelsUrl: String, val groups: List<LabelGroupOption>, val selectedIds: Set<Long>, val tags: List<IssueLabelTag>, val listUrl: String, val hasLabels: Boolean
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
        val candidates = siteUrls.path("/${UrlPaths.encode(project.owner.orEmpty())}/${UrlPaths.encode(project.name.orEmpty())}/assignableUsers")
        return PullRequestAssigneeArea(canEdit, "$base/assignees", candidates, "$base/timeline", pullRequest.assignees.firstOrNull()?.let { view(it, viewer) })
    }

    fun labels(pullRequest: PullRequest, canEdit: Boolean): PullRequestLabelArea {
        val project = requireNotNull(pullRequest.toProject)
        val base = siteUrls.path(UrlPaths.projectPath(project.owner, project.name))
        val projectLabels = if (canEdit) labelService.labels(project) else emptyList()
        return PullRequestLabelArea(
            canEdit, "$base/pullRequest/${pullRequest.number}/labels", formOptions.labelGroups(projectLabels), // 비어 있어도 `kotlin.collections.EmptySet`이 아니라 일반 집합을 넘긴다(Thymeleaf의 `contains` 호출이 EmptySet에서 실패한다)
            pullRequest.labels.mapNotNullTo(LinkedHashSet()) { it.id },
            labelTagsOf(pullRequest),
            "$base/pullRequests", projectLabels.isNotEmpty()
        )
    }

    /** 생성 폼의 담당자 영역이다. 아직 풀 리퀘스트가 없으므로 후보 검색 주소만 있다(고른 값은 폼과 함께 보낸다). */
    fun formAssignee(project: dev.yona.domain.project.Project): PullRequestAssigneeArea =
        PullRequestAssigneeArea(true, "", siteUrls.path("/${UrlPaths.encode(project.owner.orEmpty())}/${UrlPaths.encode(project.name.orEmpty())}/assignableUsers"), "", null)

    /** 생성 폼의 라벨 영역이다. 받는 프로젝트의 라벨을 고르게 하고 아무것도 골라져 있지 않다. */
    fun formLabels(project: dev.yona.domain.project.Project): PullRequestLabelArea {
        val projectLabels = labelService.labels(project)
        return PullRequestLabelArea(true, "", formOptions.labelGroups(projectLabels), LinkedHashSet(), emptyList(), "", projectLabels.isNotEmpty())
    }

    private fun view(user: User, viewer: User?) = AssigneeView(siteUrls.path("/${user.loginId}"), avatars.avatarUrl(user, AVATAR_SIZE), UserDisplay.of(user, viewer?.lang), user.loginId.orEmpty(), user.name.orEmpty())

    private companion object {
        const val AVATAR_SIZE = 20
    }
}
