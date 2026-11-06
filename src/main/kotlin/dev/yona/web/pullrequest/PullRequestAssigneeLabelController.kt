package dev.yona.web.pullrequest

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.pullrequest.AssignResult
import dev.yona.domain.pullrequest.LabelResult
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestAssigneeService
import dev.yona.domain.pullrequest.PullRequestLabelService
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.domain.vcs.VcsCapability
import dev.yona.web.access.RequiresVcs
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

/**
 * 풀 리퀘스트의 담당자와 라벨을 바꾸는 요청이다. v1.6에 없는 추가 항목이다(로드맵 X-14, `decisions.md` D-34).
 * 이슈 화면처럼 화면을 다시 불러오지 않고 JSON을 주고받는다(Ajax). 로그인해야 하고 풀 리퀘스트 수정 권한(`UPDATE`)이 필요하다
 * (프로젝트 구성원과 공개, 보호 조직 프로젝트의 조직 구성원). 로그인 검사는 [PullRequestActionController]와 같이 메서드마다 `@IsAllowed` 앞에 둔다.
 */
@Controller
@AnonymousCheck
class PullRequestAssigneeLabelController(
    private val projectService: ProjectService,
    private val pullRequestService: PullRequestService,
    private val assigneeService: PullRequestAssigneeService,
    private val labelService: PullRequestLabelService,
    private val issueLabels: IssueLabelRepository,
    private val users: UserRepository,
    private val avatars: AvatarService,
    private val currentUserProvider: CurrentUserProvider
) {

    /**
     * 담당자를 지정한다. 본문은 `{"assignees":["로그인ID"]}`이고 비어 있으면 해제한다(이슈 화면의 `assignees` 요청과 같은 모양, 한도는 1명).
     * 없는 사용자, 후보가 아닌 사람, 한도를 넘는 수는 400이다. 지금 담당자를 `{"assignee": {...}|null, "assignees": [...]}`로 돌려준다.
     */
    @PostMapping("/{user}/{project}/pullRequest/{id}/assignees", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun assignees(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, @RequestBody(required = false) body: Map<String, Any?>?): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val pullRequest = pullRequestService.findOne(project, id) ?: return ErrorViews.notFound()
        val logins = (body?.get("assignees") as? List<*>).orEmpty().mapNotNull { it?.toString() }.filter { it.isNotBlank() }
        if (logins.size > PullRequestAssigneeService.MAX_ASSIGNEES) return ErrorViews.badRequest("error.validation")
        val assignee = logins.firstOrNull()?.let { users.findByLoginId(it) ?: return ErrorViews.badRequest("error.validation") }
        if (assigneeService.set(pullRequest, assignee, requireNotNull(currentUserProvider.get().user)) == AssignResult.NOT_CANDIDATE) return ErrorViews.badRequest("error.validation")
        return json(assigneesBody(pullRequest))
    }

    /**
     * 라벨을 바꾼다. `attachingLabelIds`(붙일 라벨, 여러 개)와 `detachingLabelIds`(뗄 라벨, 여러 개)를 받는다(이슈 화면의 일괄 변경과 같다).
     * 없는 라벨이나 다른 프로젝트의 붙일 라벨이 하나라도 있으면 400이고 아무것도 바꾸지 않는다.
     */
    @PostMapping("/{user}/{project}/pullRequest/{id}/labels")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun labels(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val pullRequest = pullRequestService.findOne(project, id) ?: return ErrorViews.notFound()
        fun labelsOf(parameter: String): List<dev.yona.domain.issue.IssueLabel>? = request.getParameterValues(parameter).orEmpty().filter { it.isNotBlank() }.map {
            it.toLongOrNull()?.let { labelId -> issueLabels.findById(labelId).orElse(null) } ?: return null
        }
        val attach = labelsOf("attachingLabelIds") ?: return ErrorViews.badRequest("error.validation")
        val detach = labelsOf("detachingLabelIds") ?: return ErrorViews.badRequest("error.validation")
        if (labelService.change(pullRequest, attach, detach, requireNotNull(currentUserProvider.get().user)) == LabelResult.WRONG_PROJECT) return ErrorViews.badRequest("error.validation")
        return json(mapOf("labels" to pullRequest.labels.mapNotNull { it.id }))
    }

    private fun assigneesBody(pullRequest: PullRequest): Map<String, Any?> {
        val assignees = pullRequest.assignees.map { userNode(it) }
        return mapOf("assignee" to assignees.firstOrNull(), "assignees" to assignees)
    }

    private fun userNode(user: User): Map<String, Any?> = mapOf("loginId" to user.loginId, "name" to user.name, "avatarUrl" to avatars.avatarUrl(user, AVATAR_SIZE))

    private fun json(body: Any) = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)

    private companion object {
        const val AVATAR_SIZE = 20
    }
}
