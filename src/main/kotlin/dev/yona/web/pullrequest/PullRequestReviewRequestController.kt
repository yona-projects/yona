package dev.yona.web.pullrequest

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.ProjectService
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestReviewRequestService
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.domain.pullrequest.ReviewRequestResult
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.domain.vcs.VcsCapability
import dev.yona.web.access.RequiresVcs
import dev.yona.web.error.ErrorViews
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

/**
 * 풀 리퀘스트의 리뷰 요청, 취소, 후보 검색 요청이다. v1.6에 없는 추가 항목이다(로드맵 X-27, `decisions.md` D-37). 담당자와 라벨처럼
 * 화면을 다시 불러오지 않고 JSON을 주고받으며(Ajax), 로그인해야 하고 풀 리퀘스트 수정 권한(`UPDATE`)이 필요하다.
 * 성공하면 지금 대기 중인 사람들을 `{"pending": [{"loginId", "name", "avatarUrl"}]}`로 돌려준다.
 */
@Controller
@AnonymousCheck
class PullRequestReviewRequestController(
    private val projectService: ProjectService,
    private val pullRequestService: PullRequestService,
    private val reviewRequests: PullRequestReviewRequestService,
    private val users: UserRepository,
    private val avatars: AvatarService,
    private val currentUserProvider: CurrentUserProvider
) {

    /** 리뷰를 요청한다. 본문은 `{"reviewer": "로그인ID"}`이다. 없는 사용자, 후보가 아닌 사람, 닫힌 풀 리퀘스트는 400이고 이미 대기 중이면 그대로 성공이다. */
    @PostMapping("/{user}/{project}/pullRequest/{id}/reviewRequests", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun request(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, @RequestBody(required = false) body: Map<String, Any?>?): Any {
        val pullRequest = find(owner, name, id) ?: return ErrorViews.notFound()
        val login = body?.get("reviewer")?.toString()?.takeIf { it.isNotBlank() } ?: return ErrorViews.badRequest("error.validation")
        val reviewer = users.findByLoginId(login) ?: return ErrorViews.badRequest("error.validation")
        val result = reviewRequests.request(pullRequest, reviewer, requireNotNull(currentUserProvider.get().user))
        if (result == ReviewRequestResult.NOT_CANDIDATE || result == ReviewRequestResult.NOT_OPEN) return ErrorViews.badRequest("error.validation")
        return json(pendingBody(pullRequest))
    }

    /** 대기 중인 요청을 취소한다. 대기 중이 아니면 아무 일도 하지 않고 지금 목록을 돌려준다. */
    @PostMapping("/{user}/{project}/pullRequest/{id}/reviewRequests/{reviewer}/delete")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun cancel(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, @PathVariable reviewer: String): Any {
        val pullRequest = find(owner, name, id) ?: return ErrorViews.notFound()
        val target = users.findByLoginId(reviewer) ?: return ErrorViews.notFound()
        reviewRequests.cancel(pullRequest, target, requireNotNull(currentUserProvider.get().user))
        return json(pendingBody(pullRequest))
    }

    /** 리뷰를 요청할 사람을 이름이나 로그인 ID로 찾는다(최대 20명, 빈 검색어는 빈 목록). 응답은 `[{"loginId", "name", "avatarUrl"}]`이다. */
    @GetMapping("/{user}/{project}/pullRequest/{id}/reviewRequestCandidates")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    @Transactional(readOnly = true)
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun candidates(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, @RequestParam(required = false, defaultValue = "") query: String): Any {
        val pullRequest = find(owner, name, id) ?: return ErrorViews.notFound()
        return json(reviewRequests.candidates(pullRequest, query, currentUserProvider.get().user).map { userNode(it) })
    }

    private fun find(owner: String, name: String, id: Long): PullRequest? =
        projectService.findByOwnerAndProjectName(owner, name)?.let { pullRequestService.findOne(it, id) }

    private fun pendingBody(pullRequest: PullRequest): Map<String, Any?> = mapOf("pending" to pullRequest.reviewRequests.sortedBy { it.id }.map { userNode(it) })

    private fun userNode(user: User): Map<String, Any?> = mapOf("loginId" to user.loginId, "name" to user.name, "avatarUrl" to avatars.avatarUrl(user, AVATAR_SIZE))

    private fun json(body: Any) = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)

    private companion object {
        const val AVATAR_SIZE = 20
    }
}
