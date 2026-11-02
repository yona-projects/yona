package dev.yona.web.api

import dev.yona.domain.pullrequest.PullRequestDiscussionService
import dev.yona.domain.user.UserRepository
import dev.yona.util.SiteUrls
import dev.yona.util.UrlPaths
import dev.yona.web.api.docs.ApiTags
import dev.yona.web.api.docs.PullRequestDiscussionResponse
import dev.yona.web.api.docs.ReviewActivityResponse
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/**
 * Magpie의 변경 요청 계약(`contract:change-request`, 로드맵 X-23)의 `get_discussion`과 `list_reviews_given`을 채우는 읽기 전용 조회이다. 토큰 인증이며 프로젝트를 읽을 수 있어야 한다.
 */
@Controller
@ResponseBody
@dev.yona.web.access.AnonymousCheck
class ExternalPullRequestDiscussionApiController(
    private val support: PullRequestApiSupport,
    private val discussions: PullRequestDiscussionService,
    private val users: UserRepository,
    private val siteUrls: SiteUrls,
    private val members: dev.yona.domain.project.ProjectMemberService
) {

    /**
     * 풀 리퀘스트의 토론이다. 댓글(리뷰 스레드)과 리뷰를 시각 순으로 담고, 종류는 `comment`, `review`, `approval`, `change-request`이다. 열린 스레드 수와 참여자 로그인 ID를 함께 알린다.
     * 해제한 리뷰와 임시 저장 댓글은 담지 않는다. 날짜는 UTC(초 단위)이다.
     */
    @io.swagger.v3.oas.annotations.Operation(operationId = "getPullRequestDiscussion", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트의 토론", description = "댓글과 리뷰를 시각 순으로 담는다(Magpie 연동). 종류는 comment, review, approval, change-request이다.")
    @ApiResponse(responseCode = "200", description = "토론", content = [Content(schema = Schema(implementation = PullRequestDiscussionResponse::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/{number}/discussion")
    @Transactional(readOnly = true)
    fun discussion(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        val result = discussions.discussion(requireNotNull(target.pullRequest))
        return support.ok(
            linkedMapOf(
                "comments" to result.comments.map { linkedMapOf("author" to it.author, "date" to ApiInstants.format(it.date), "body" to it.body, "kind" to it.kind) },
                "participants" to result.participants, "unresolvedCount" to result.unresolvedCount
            )
        )
    }

    /**
     * 사람이 창 안에서 리뷰한 풀 리퀘스트이다(`since` 이상, `until` 미만, `until`을 생략하면 지금). `reviewer`를 생략하면 프로젝트 전체이고 (풀 리퀘스트, 리뷰한 사람)마다 한 줄이며,
     * `membersOnly=true`이면 프로젝트 구성원(manager, member)의 리뷰만이다. 항목은 처음 리뷰한 시각 순이고 `bodyLength`는 창 안의 리뷰 본문 길이의 합,
     * `inlineCommentCount`는 리뷰 스레드에 단 댓글 수이다. 리뷰 본문은 응답에 없다.
     */
    @io.swagger.v3.oas.annotations.Operation(operationId = "listReviewActivity", tags = [ApiTags.PULL_REQUEST], summary = "사람과 기간별 리뷰 활동", description = "사람이 창(since 이상, until 미만) 안에서 리뷰한 풀 리퀘스트이다(Magpie 연동).")
    @io.swagger.v3.oas.annotations.Parameter(name = "reviewer", description = "로그인 ID. 생략하면 프로젝트 전체", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "membersOnly", description = "`true`이면 프로젝트 구성원의 리뷰만", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "since", description = "창의 시작(필수, 이상). `2026-02-01`(UTC 0시) 또는 ISO-8601", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, required = true)
    @io.swagger.v3.oas.annotations.Parameter(name = "until", description = "창의 끝(미만). 생략하면 지금", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @ApiResponse(responseCode = "200", description = "리뷰 활동", content = [Content(schema = Schema(implementation = ReviewActivityResponse::class))])
    @ApiResponse(responseCode = "400", description = "`reviewer`가 없는 사용자이다. `since`나 `until`을 읽을 수 없거나 `since`가 `until`보다 늦다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/activity/reviews")
    @Transactional(readOnly = true)
    fun reviewActivity(
        @PathVariable owner: String, @PathVariable projectName: String, @RequestParam(required = false) reviewer: String?, @RequestParam(required = false) since: String?,
        @RequestParam(required = false) until: String?, @RequestParam(required = false) membersOnly: Boolean?, request: HttpServletRequest
    ): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        val project = requireNotNull(target.project)
        val login = reviewer?.trim()?.takeIf { it.isNotEmpty() }
        if (login != null && users.findByLoginId(login) == null) return support.badRequest("no user by $login")
        val from = ApiInstants.parse(since) ?: return support.badRequest("since is required (e.g. 2026-02-01 or 2026-02-01T00:00:00Z)")
        val to = if (until.isNullOrBlank()) java.util.Date() else ApiInstants.parse(until) ?: return support.badRequest("until could not be read")
        if (!from.before(to)) return support.badRequest("since must be earlier than until")
        val base = UrlPaths.projectPath(project.owner, project.name)
        val roster = if (membersOnly == true) members.members(project).mapNotNull { it.user?.loginId }.toSet() else null
        val items = discussions.reviewsGiven(requireNotNull(project.id), login, from, to).filter { roster == null || it.reviewer in roster }.map {
            linkedMapOf(
                "pullRequestNumber" to it.pullRequestNumber, "url" to siteUrls.path("$base/pullRequest/${it.pullRequestNumber}"), "reviewer" to it.reviewer, "firstReviewAt" to ApiInstants.format(it.firstReviewAt),
                "bodyLength" to it.bodyLength, "inlineCommentCount" to it.inlineCommentCount, "labels" to it.labels
            )
        }
        return support.ok(linkedMapOf("items" to items))
    }
}
