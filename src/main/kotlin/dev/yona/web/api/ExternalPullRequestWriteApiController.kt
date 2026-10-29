package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.pullrequest.AcceptResult
import dev.yona.domain.pullrequest.AssignResult
import dev.yona.domain.pullrequest.LabelResult
import dev.yona.domain.pullrequest.PullRequestAssigneeService
import dev.yona.domain.pullrequest.PullRequestLabelService
import dev.yona.domain.pullrequest.PullRequestReviewRequestService
import dev.yona.domain.pullrequest.PullRequestWorkflow
import dev.yona.domain.pullrequest.ReviewRequestResult
import dev.yona.domain.pullrequest.merged
import dev.yona.domain.pullrequest.open
import dev.yona.domain.user.UserRepository
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.JsonNode

/**
 * 외부용 풀 리퀘스트 REST API의 쓰기이다(로드맵 X-15, `decisions.md` D-39). 화면과 같은 동작(닫기, 다시 열기, 병합, 출발 브랜치 지우기와 되살리기, 담당자, 라벨, 리뷰 요청)을
 * 같은 서비스와 같은 권한(`AccessControl`)으로 노출하고, 성공하면 바뀐 풀 리퀘스트 전체(`GET .../pullrequests/{number}`의 모양)를 돌려준다.
 * 상태가 맞지 않아 할 수 없는 일은 409, 입력이 잘못되면 400이다. 병합은 `method` 필드를 받지만 지금은 `merge`만 받는다(스쿼시와 리베이스는 로드맵 X-28).
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalPullRequestWriteApiController(
    private val support: PullRequestApiSupport,
    private val workflow: PullRequestWorkflow,
    private val assignees: PullRequestAssigneeService,
    private val labelService: PullRequestLabelService,
    private val reviewRequests: PullRequestReviewRequestService,
    private val issueLabels: IssueLabelRepository,
    private val users: UserRepository,
    private val accessControl: AccessControl,
    private val requests: ApiRequests,
    private val json: PullRequestJson
) {

    private companion object {
        const val BASE = "/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/{number}"
    }

    /** 풀 리퀘스트를 닫는다. 병합된 것은 409이고 이미 닫힌 것은 그대로 성공이다. */
    @PostMapping("$BASE/close")
    @Transactional
    fun close(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.CLOSE)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        if (pullRequest.merged) return support.conflict("pull request is already merged")
        if (pullRequest.open) workflow.close(pullRequest, requireNotNull(target.user))
        return support.ok(json.of(pullRequest))
    }

    /** 닫힌 풀 리퀘스트를 다시 연다. 이미 열렸거나 병합된 것은 409이다. */
    @PostMapping("$BASE/reopen")
    @Transactional
    fun reopen(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.REOPEN)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        if (!workflow.reopen(pullRequest, requireNotNull(target.user))) return support.conflict("pull request is not closed")
        return support.ok(json.of(pullRequest))
    }

    /**
     * 병합한다. 본문 `{"method": "merge"}`는 생략할 수 있고 `merge` 외의 값은 400 `Unsupported merge method`이다(로드맵 X-28에서 연다).
     * 열려 있지 않거나, 병합 중이거나, 충돌하거나, 리뷰 조건을 채우지 못했으면 409이다. 프로젝트 관리자와 사이트 관리자는 화면처럼 리뷰 조건을 건너뛴다.
     * v1.6처럼 병합 중 표시를 먼저 저장하므로 이 메서드에는 트랜잭션을 걸지 않는다(`refactoring-backlog.md` R-76).
     */
    @PostMapping("$BASE/merge")
    fun merge(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.ACCEPT)
        target.error?.let { return it }
        val method = requests.json(request)?.get("method")?.asString()
        if (method != null && method != "merge") return support.badRequest("Unsupported merge method: $method")
        val pullRequest = requireNotNull(target.pullRequest)
        if (!pullRequest.open) return support.conflict("pull request is not open")
        if (pullRequest.isMerging == true) return support.conflict("pull request is being merged")
        if (pullRequest.isConflict == true) return support.conflict("pull request has conflicts")
        val user = requireNotNull(target.user)
        return when (workflow.accept(pullRequest, user, accessControl.canBypassReview(user, requireNotNull(target.project)))) {
            AcceptResult.MERGED -> support.ok(json.of(pullRequest))
            AcceptResult.CONFLICT -> support.conflict("pull request has conflicts")
            AcceptResult.NOT_REVIEWED -> support.conflict("not enough review approvals")
        }
    }

    /** 병합된 풀 리퀘스트의 출발 브랜치를 지운다(화면의 "브랜치 삭제"). */
    @DeleteMapping("$BASE/fromBranch")
    @Transactional
    fun deleteFromBranch(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        workflow.deleteFromBranch(pullRequest)
        return support.ok(json.of(pullRequest))
    }

    /** 지운 출발 브랜치를 되살린다. */
    @PostMapping("$BASE/fromBranch/restore")
    @Transactional
    fun restoreFromBranch(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        workflow.restoreFromBranch(pullRequest)
        return support.ok(json.of(pullRequest))
    }

    /** 담당자를 지정한다. 본문 `{"assignees": ["로그인ID"]}`이고 비우면 해제한다(지금은 1명까지). 없는 사용자와 후보가 아닌 사람은 400이다. */
    @PostMapping("$BASE/assignees")
    @Transactional
    fun setAssignees(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val node: JsonNode = requests.json(request)?.get("assignees")?.takeIf { it.isArray } ?: return support.badRequest("assignees must be an array")
        val logins = node.toList().mapNotNull { it.asString() }.filter { it.isNotBlank() }
        if (logins.size > PullRequestAssigneeService.MAX_ASSIGNEES) return support.badRequest("too many assignees")
        val assignee = logins.firstOrNull()?.let { users.findByLoginId(it) ?: return support.badRequest("no user by $it") }
        val pullRequest = requireNotNull(target.pullRequest)
        if (assignees.set(pullRequest, assignee, requireNotNull(target.user)) == AssignResult.NOT_CANDIDATE) return support.badRequest("not an assignable user")
        return support.ok(json.of(pullRequest))
    }

    /**
     * 라벨을 바꾼다. 본문 `{"attachingLabelIds": [...], "detachingLabelIds": [...]}`(둘 다 생략할 수 있다)이다. 없는 라벨이나 받는 프로젝트의 라벨이 아닌 것은
     * 400이고 아무것도 바꾸지 않는다.
     */
    @PostMapping("$BASE/labels")
    @Transactional
    fun changeLabels(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val body = requests.json(request) ?: return support.badRequest("Expecting Json data")
        fun labelsOf(key: String): List<dev.yona.domain.issue.IssueLabel>? = body.get(key)?.toList().orEmpty().map { issueLabels.findById(it.asLong(0)).orElse(null) ?: return null }
        val attach = labelsOf("attachingLabelIds") ?: return support.badRequest("no label by attachingLabelIds")
        val detach = labelsOf("detachingLabelIds") ?: return support.badRequest("no label by detachingLabelIds")
        val pullRequest = requireNotNull(target.pullRequest)
        if (labelService.change(pullRequest, attach, detach, requireNotNull(target.user)) == LabelResult.WRONG_PROJECT) return support.badRequest("label belongs to another project")
        return support.ok(json.of(pullRequest))
    }

    /** 리뷰를 요청한다. 본문 `{"reviewer": "로그인ID"}`이다. 없는 사용자, 후보가 아닌 사람(작성자, 프로젝트나 조직에 속하지 않은 사람), 열려 있지 않은 풀 리퀘스트는 400이다. */
    @PostMapping("$BASE/requestedreviewers")
    @Transactional
    fun requestReview(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val login = requests.json(request)?.get("reviewer")?.asString()?.takeIf { it.isNotBlank() } ?: return support.badRequest("reviewer is required")
        val reviewer = users.findByLoginId(login) ?: return support.badRequest("no user by $login")
        val pullRequest = requireNotNull(target.pullRequest)
        val result = reviewRequests.request(pullRequest, reviewer, requireNotNull(target.user))
        if (result == ReviewRequestResult.NOT_CANDIDATE || result == ReviewRequestResult.NOT_OPEN) return support.badRequest("cannot request a review from $login")
        return support.ok(json.of(pullRequest))
    }

    /** 대기 중인 리뷰 요청을 취소한다. 그 사용자가 없으면 404이고 대기 중이 아니면 그대로 성공이다. */
    @DeleteMapping("$BASE/requestedreviewers/{reviewer}")
    @Transactional
    fun cancelReviewRequest(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable reviewer: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val user = users.findByLoginId(reviewer) ?: return requests.message(HttpStatus.NOT_FOUND, "no user by $reviewer")
        val pullRequest = requireNotNull(target.pullRequest)
        reviewRequests.cancel(pullRequest, user, requireNotNull(target.user))
        return support.ok(json.of(pullRequest))
    }
}
