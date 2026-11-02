package dev.yona.web.api

import dev.yona.domain.issue.TrackerActivityService
import dev.yona.web.api.docs.ApiTags
import dev.yona.web.api.docs.AuthoredCommitsResponse
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
    private val siteUrls: SiteUrls,
    private val emails: dev.yona.domain.user.EmailRepository,
    private val history: dev.yona.repository.CommitHistoryService
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

    /**
     * 사람이 쓴 커밋이다(Magpie `list_authored_commits`). 사람의 대표 이메일과 인증한 추가 이메일(대소문자 무시)로 기본 브랜치의 이력에서 찾고 최근 순으로 `limit`개(1에서 100, 기본 30)를 돌려준다.
     * 이력은 최근 3000개까지만 훑으며 그 안에서 `limit`개를 다 채우지 못하고 이력이 더 있으면 `capped`가 `true`이다. 이메일은 찾는 열쇠이고 응답의 `authorEmail`은 커밋에 적힌 값이다.
     * Git 프로젝트만 다루고 Subversion은 빈 목록이다.
     */
    @io.swagger.v3.oas.annotations.Operation(operationId = "listAuthoredCommits", tags = [ApiTags.ISSUE], summary = "사람이 쓴 커밋", description = "사람의 이메일로 기본 브랜치의 커밋을 최근 순으로 찾는다(Magpie 연동).")
    @io.swagger.v3.oas.annotations.Parameter(name = "author", description = "로그인 ID(필수)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, required = true)
    @io.swagger.v3.oas.annotations.Parameter(name = "limit", description = "개수(1 이상, 100을 넘으면 100, 기본 30)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @ApiResponse(responseCode = "200", description = "커밋 목록", content = [Content(schema = Schema(implementation = AuthoredCommitsResponse::class))])
    @ApiResponse(responseCode = "400", description = "`author`가 없거나 없는 사용자이다. `limit`이 1보다 작다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/activity/commits")
    @Transactional(readOnly = true)
    fun commits(@PathVariable owner: String, @PathVariable projectName: String, @RequestParam(required = false) author: String?, @RequestParam(required = false) limit: Int?, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request)
        target.error?.let { return it }
        val project = requireNotNull(target.project)
        val login = author?.trim()?.takeIf { it.isNotEmpty() } ?: return support.badRequest("author is required")
        val person = users.findByLoginId(login) ?: return support.badRequest("no user by $login")
        if (limit != null && limit < 1) return support.badRequest("limit must be at least 1")
        val wanted = (listOfNotNull(person.email) + emails.findByUserIdOrderById(requireNotNull(person.id)).filter { it.valid == true }.mapNotNull { it.email }).map { it.lowercase() }.toSet()
        val max = (limit ?: 30).coerceAtMost(100)
        val scanned = if (project.vcs == "GIT") history.history(project, 0, COMMIT_SCAN_CAP, null, null).orEmpty() else emptyList()
        val found = scanned.filter { it.authorEmail.lowercase() in wanted }
        val items = found.take(max).map {
            linkedMapOf("sha" to it.id, "authorName" to it.authorName, "authorEmail" to it.authorEmail, "message" to it.message, "committedAt" to ApiInstants.format(it.authorDate))
        }
        return support.ok(linkedMapOf("items" to items, "capped" to (found.size < max && scanned.size >= COMMIT_SCAN_CAP)))
    }

    private companion object {
        /** 한 번에 훑는 이력의 최대 개수이다. */
        const val COMMIT_SCAN_CAP = 3000
    }
}
