package dev.yona.web.api

import dev.yona.domain.issue.TrackerActivityService
import dev.yona.web.api.docs.ApiTags
import dev.yona.web.api.docs.TrackerActivityResponse
import dev.yona.domain.user.UserRepository
import dev.yona.util.SiteUrls
import dev.yona.util.UrlPaths
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
 * 사람과 기간별 활동 조회이다(로드맵 X-23, Magpie `contract:tracker`의 `list_triaged`와 `list_commented`). 토큰 인증이며 프로젝트를 읽을 수 있고 토큰 범위가 맞아야 한다.
 */
@Controller
@ResponseBody
@dev.yona.web.access.AnonymousCheck
class ExternalTrackerActivityApiController(
    private val support: PullRequestApiSupport,
    private val activity: TrackerActivityService,
    private val users: UserRepository,
    private val siteUrls: SiteUrls
) {

    /**
     * 사람이 창 안에서 댓글을 단 이슈와 풀 리퀘스트를 처음 댓글을 단 시각 순으로 돌려준다. 창은 `since` 이상 `until` 미만이고 `until`을 생략하면 지금이다.
     * `kind`가 `commented`(기본)이면 이슈와 풀 리퀘스트 모두이고, `triaged`이면 **다른 사람이 연 이슈**만이다. `phrases`(쉼표로 나눈 문구)와 `maintainers`(쉼표로 나눈 로그인 ID)를 함께 주면
     * 스레드마다 구성원이 처음 남긴 문구 댓글의 주소를 `pushbackCandidate`에 담는다(댓글 본문은 응답에 없다).
     */
    @io.swagger.v3.oas.annotations.Operation(operationId = "listTrackerActivity", tags = [ApiTags.ISSUE], summary = "사람과 기간별 댓글 활동", description = "사람이 창(since 이상, until 미만) 안에서 댓글을 단 이슈와 풀 리퀘스트이다. 날짜는 처음 댓글을 단 시각이다(Magpie 연동).")
    @io.swagger.v3.oas.annotations.Parameter(name = "actor", description = "로그인 ID(필수)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, required = true)
    @io.swagger.v3.oas.annotations.Parameter(name = "since", description = "창의 시작(필수, 이상). `2026-02-01`(UTC 0시) 또는 ISO-8601", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, required = true)
    @io.swagger.v3.oas.annotations.Parameter(name = "until", description = "창의 끝(미만). 생략하면 지금", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "kind", description = "`commented`(기본) 또는 `triaged`", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "phrases", description = "밀어내기 문구(쉼표로 나눔)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "maintainers", description = "메인테이너 로그인 ID(쉼표로 나눔)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @ApiResponse(responseCode = "200", description = "활동 목록", content = [Content(schema = Schema(implementation = TrackerActivityResponse::class))])
    @ApiResponse(responseCode = "400", description = "`actor`가 없거나 없는 사용자이다. `since`나 `until`을 읽을 수 없거나 `since`가 `until`보다 늦다. `kind`가 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/activity")
    @Transactional(readOnly = true)
    fun activity(
        @PathVariable owner: String, @PathVariable projectName: String, @RequestParam(required = false) actor: String?, @RequestParam(required = false) since: String?,
        @RequestParam(required = false) until: String?, @RequestParam(defaultValue = "commented") kind: String, @RequestParam(required = false) phrases: String?,
        @RequestParam(required = false) maintainers: String?, request: HttpServletRequest
    ): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        val project = requireNotNull(target.project)
        if (kind != "commented" && kind != "triaged") return support.badRequest("kind must be commented or triaged")
        val login = actor?.trim()?.takeIf { it.isNotEmpty() } ?: return support.badRequest("actor is required")
        if (users.findByLoginId(login) == null) return support.badRequest("no user by $login")
        val from = ApiInstants.parse(since) ?: return support.badRequest("since is required (e.g. 2026-02-01 or 2026-02-01T00:00:00Z)")
        val to = if (until.isNullOrBlank()) java.util.Date() else ApiInstants.parse(until) ?: return support.badRequest("until could not be read")
        if (!from.before(to)) return support.badRequest("since must be earlier than until")
        val phraseList = phrases?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val roster = maintainers?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        val base = UrlPaths.projectPath(project.owner, project.name)
        val items = activity.commented(requireNotNull(project.id), login, from, to, triagedOnly = kind == "triaged", maintainers = roster, phrases = phraseList).map { thread ->
            val url = siteUrls.path("$base/${if (thread.isPullRequest) "pullRequest" else "issue"}/${thread.number}")
            linkedMapOf(
                "number" to thread.number, "isPullRequest" to thread.isPullRequest, "url" to url, "firstActivity" to ApiInstants.format(thread.firstActivity), "labels" to thread.labels,
                "pushbackCandidate" to thread.pushbackCommentId?.let { "$url#comment-$it" }
            )
        }
        return support.ok(linkedMapOf("items" to items))
    }
}
