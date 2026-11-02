package dev.yona.web.api

import dev.yona.domain.comment.CommentThread
import dev.yona.domain.comment.PendingReviewReader
import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.domain.pullrequest.PullRequestCategory
import dev.yona.domain.pullrequest.PullRequestChangesService
import dev.yona.domain.pullrequest.PullRequestCommitRepository
import dev.yona.domain.pullrequest.PullRequestEventRepository
import dev.yona.domain.pullrequest.PullRequestSearchCondition
import dev.yona.domain.pullrequest.PullRequestSearchService
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.domain.pullrequest.diffable
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * 외부용 풀 리퀘스트 REST API이다(로드맵 X-15, `decisions.md` D-39). v1.6에 없는 추가 항목이다. 인증은 개인용 API 토큰뿐이고(`/-_-api` 필터) 범위(`repo`,
 * 공개 프로젝트는 `public_repo`)에 더해 화면과 같은 사용자 권한(`AccessControl`)을 확인한다. 풀 리퀘스트는 프로젝트 안의 번호(`number`)로 가리킨다.
 * 없는 프로젝트는 존재를 숨기려고 403(v1.6 `AbstractProjectCheckAction`과 같다), 없는 번호는 404이며 오류는 `{"message": 문장}`이다.
 *
 * 이 컨트롤러는 조회이고 쓰기는 [ExternalPullRequestWriteApiController]가 한다. 대상 찾기와 권한 확인은 [PullRequestApiSupport]가 함께 쓴다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalPullRequestApiController(
    private val pullRequestService: PullRequestService,
    private val search: PullRequestSearchService,
    private val changes: PullRequestChangesService,
    private val commits: PullRequestCommitRepository,
    private val events: PullRequestEventRepository,
    private val pendingReader: PendingReviewReader,
    private val requests: ApiRequests,
    private val json: PullRequestJson,
    private val support: PullRequestApiSupport,
    private val reviewRequests: dev.yona.domain.pullrequest.PullRequestReviewRequestService,
    private val assignees: dev.yona.domain.pullrequest.PullRequestAssigneeService,
    private val users: dev.yona.domain.user.UserRepository,
    private val history: dev.yona.repository.CommitHistoryService,
    private val gpgVerification: dev.yona.domain.gpgkey.GpgVerificationService,
    private val trackerActivity: dev.yona.domain.issue.TrackerActivityService
) {

    /**
     * 풀 리퀘스트 목록이다. `state`는 `open`(기본)과 `closed`(닫힌 것과 병합된 것)이고 `page`는 1부터, `pageSize`는 1~100(기본 15)이다. 조건: `q`(낱말), `author`, `assignee`, `reviewRequested`(로그인 ID), `labelIds`(모두 가진 것).
     * 응답은 `{"items": [...], "page", "pageSize", "totalPages", "totalCount"}`이다.
     */
    @ApiOperation(operationId = "listPullRequests", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트 목록", description = "받는 프로젝트가 이 프로젝트인 풀 리퀘스트를 쪽으로 나누어 돌려준다. 코드 접근을 구성원으로 제한한 프로젝트는 구성원만 읽는다.")
    @io.swagger.v3.oas.annotations.Parameter(name = "state", description = "목록의 상태. `closed`는 닫힌 것과 병합된 것, `merged`는 병합된 것만, `all`은 전부", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "open", schema = Schema(allowableValues = ["open", "closed", "merged", "all"]))
    @io.swagger.v3.oas.annotations.Parameter(name = "createdSince", description = "이 시각 이상에 만든 것만(Magpie 연동). `2026-02-01`(UTC 0시) 또는 ISO-8601", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "createdUntil", description = "이 시각 미만에 만든 것만", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "updatedBefore", description = "이 시각 미만에 마지막으로 갱신된 것만(오래된 것)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "mergedSince", description = "이 시각 이상에 병합된 것만", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "mergedUntil", description = "이 시각 미만에 병합된 것만", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "sort", description = "정렬(내림차순): `updated`, `created`. 생략하면 분류의 기본", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "page", description = "쪽 번호(1부터)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "1")
    @io.swagger.v3.oas.annotations.Parameter(name = "pageSize", description = "한 쪽의 개수(1에서 100, 기본 15)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "q", description = "제목, 본문, 커밋 메시지, 리뷰 댓글에서 찾을 낱말", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "author", description = "보낸 사람의 로그인 ID. 없는 사용자는 400", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "assignee", description = "담당자의 로그인 ID. 없는 사용자는 400", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "reviewRequested", description = "리뷰를 요청받은 사람의 로그인 ID. 없는 사용자는 400", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "labelIds", description = "라벨 번호(쉼표로 구분하거나 반복). 모두 가진 풀 리퀘스트만 찾는다", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @ApiResponse(responseCode = "200", description = "풀 리퀘스트 목록", content = [Content(schema = Schema(implementation = PullRequestListResponse::class))])
    @ApiResponse(responseCode = "400", description = "`state`가 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests")
    @Transactional(readOnly = true)
    fun list(
        @PathVariable owner: String, @PathVariable projectName: String, @RequestParam(defaultValue = "open") state: String, @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(required = false) pageSize: Int?, request: HttpServletRequest
    ): Any {
        val target = support.target(owner, projectName, null, request)
        target.error?.let { return it }
        val category = when (state) {
            "open" -> PullRequestCategory.OPEN
            "closed" -> PullRequestCategory.CLOSED
            "merged" -> PullRequestCategory.MERGED
            "all" -> PullRequestCategory.ALL
            else -> return requests.message(HttpStatus.BAD_REQUEST, "state must be open, closed, merged or all")
        }
        // 기간과 정렬 조건(Magpie 연동, X-23): `since` 이상, `until` 미만. 날짜만 쓰면 UTC 0시이다
        fun instant(name: String): java.util.Date? = request.getParameter(name)?.takeIf { it.isNotBlank() }?.let { ApiInstants.parse(it) }
        for (name in listOf("createdSince", "createdUntil", "updatedBefore", "mergedSince", "mergedUntil")) {
            if (!request.getParameter(name).isNullOrBlank() && instant(name) == null) return requests.message(HttpStatus.BAD_REQUEST, "$name could not be read (e.g. 2026-02-01 or 2026-02-01T00:00:00Z)")
        }
        val createdSince = instant("createdSince"); val createdUntil = instant("createdUntil"); val mergedSince = instant("mergedSince"); val mergedUntil = instant("mergedUntil")
        if (createdSince != null && createdUntil != null && !createdSince.before(createdUntil)) return requests.message(HttpStatus.BAD_REQUEST, "createdSince must be earlier than createdUntil")
        if (mergedSince != null && mergedUntil != null && !mergedSince.before(mergedUntil)) return requests.message(HttpStatus.BAD_REQUEST, "mergedSince must be earlier than mergedUntil")
        val orderBy = when (val sort = request.getParameter("sort")?.takeIf { it.isNotBlank() }) {
            null -> null
            "updated" -> "updated"
            "created" -> "created"
            else -> return requests.message(HttpStatus.BAD_REQUEST, "sort must be updated or created")
        }
        val size = (pageSize ?: search.itemsPerPage).coerceIn(1, MAX_PAGE_SIZE)
        // 조건(X-37f, D-52): 낱말, 보낸 사람, 담당자, 리뷰 요청을 받은 사람(로그인 ID), 라벨 번호(모두 가진 것). 없는 사용자는 400이다
        fun userId(name: String): Long? = request.getParameter(name)?.takeIf { it.isNotBlank() }?.let { login -> users.findByLoginId(login)?.id }
        for (name in listOf("author", "assignee", "reviewRequested")) {
            if (!request.getParameter(name).isNullOrBlank() && userId(name) == null) return requests.message(HttpStatus.BAD_REQUEST, "no user by $name")
        }
        val labelIds = request.getParameterValues("labelIds").orEmpty().flatMap { it.split(",") }.mapNotNull { it.trim().toLongOrNull() }.toSet()
        val result = search.search(
            PullRequestSearchCondition(
                target.project, category, filter = request.getParameter("q")?.takeIf { it.isNotBlank() }, contributorId = userId("author"), pageNum = page.coerceAtLeast(1),
                assigneeId = userId("assignee"), labelIds = labelIds, reviewRequestedId = userId("reviewRequested"),
                createdSince = createdSince, createdUntil = createdUntil, updatedBefore = instant("updatedBefore"), mergedSince = mergedSince, mergedUntil = mergedUntil, orderBy = orderBy
            ), size
        )
        // 작성자의 첫 기여 여부(Magpie 연동, X-23): 이 작성자가 이 프로젝트에서 처음 낸 이슈나 풀 리퀘스트인지
        val firsts = trackerActivity.firstContributions(requireNotNull(requireNotNull(target.project).id), result.items.mapNotNull { it.contributor?.id }.toSet())
        val items = result.items.map { pull -> LinkedHashMap(json.of(pull)).also { item -> item["authorFirstTime"] = pull.contributor?.id?.let { id -> firsts[id]?.let { first -> !first.before(pull.created) } } } }
        return support.ok(linkedMapOf("items" to items, "page" to page.coerceAtLeast(1), "pageSize" to size, "totalPages" to result.totalPages, "totalCount" to result.totalCount))
    }

    /** 풀 리퀘스트 하나이다. */
    @ApiOperation(operationId = "getPullRequest", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트 한 건 조회", description = "담당자, 라벨, 리뷰 요청, 승인 현황, 병합 가능 여부, 방식별 병합 가능 여부를 담은 풀 리퀘스트를 돌려준다.")
    @ApiResponse(responseCode = "200", description = "풀 리퀘스트", content = [Content(schema = Schema(implementation = PullRequestDetail::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/{number}")
    @Transactional(readOnly = true)
    fun get(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        return support.ok(json.of(requireNotNull(target.pullRequest)))
    }

    /** 현재 커밋 목록이다(오래된 순서). 응답은 `{"commits": [...]}`이다. */
    @ApiOperation(operationId = "listPullRequestCommits", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트 커밋 목록", description = "현재 커밋을 오래된 순서로 돌려준다.")
    @ApiResponse(responseCode = "200", description = "커밋 목록", content = [Content(schema = Schema(implementation = PullRequestCommitList::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/{number}/commits")
    @Transactional(readOnly = true)
    fun commits(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        val current = commits.findByPullRequestIdAndStateOrderByCreatedDesc(requireNotNull(pullRequest.id), PullRequestCommitState.CURRENT).reversed()
        // 서명 판정(X-4, D-65): 저장소를 한 번만 열어 커밋을 읽고 판정한다. 읽을 수 없는 커밋은 판정을 붙이지 않는다
        val infos = history.commits(requireNotNull(pullRequest.toProject), current.mapNotNull { it.commitId })
        return support.ok(linkedMapOf("commits" to current.map { c -> json.commit(c, infos[c.commitId]?.let { gpgVerification.verifyCommit(it.signature, it.committerEmail) }) }))
    }

    /** 병합 기준 커밋 사이의 변경 파일이다. 병합할 수 없어 변경을 알 수 없으면 빈 목록이다. 응답은 `{"files": [...]}`이다. */
    @ApiOperation(operationId = "listPullRequestFiles", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트 변경 파일", description = "병합 기준 커밋 사이에서 바뀐 파일과 통합 diff(`patch`)이다. 병합할 수 없어 변경을 알 수 없으면 빈 목록이다.")
    @ApiResponse(responseCode = "200", description = "변경 파일 목록", content = [Content(schema = Schema(implementation = PullRequestFileList::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/{number}/files")
    @Transactional(readOnly = true)
    fun files(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        val diffs = if (pullRequest.diffable) changes.diff(pullRequest, null) else null
        return support.ok(linkedMapOf("files" to diffs.orEmpty().map { json.file(it) }))
    }

    /** 이벤트(상태, 커밋, 담당자, 라벨, 리뷰, 리뷰 요청 등)를 만든 순서로 돌려준다. 응답은 `{"events": [...]}`이다. */
    @ApiOperation(operationId = "getPullRequestTimeline", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트 타임라인", description = "상태, 커밋, 담당자, 라벨, 리뷰, 리뷰 요청 등의 이벤트를 만든 순서로 돌려준다.")
    @ApiResponse(responseCode = "200", description = "이벤트 목록", content = [Content(schema = Schema(implementation = PullRequestTimeline::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/{number}/timeline")
    @Transactional(readOnly = true)
    fun timeline(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        return support.ok(linkedMapOf("events" to events.findByPullRequestIdOrderByCreatedAscIdAsc(requireNotNull(pullRequest.id)).map { json.event(it) }))
    }

    /** 리뷰 판정 이력이다(제출한 순서). 응답은 `{"reviews": [...]}`이다. */
    @ApiOperation(operationId = "listPullRequestReviews", tags = [ApiTags.PULL_REQUEST_REVIEW], summary = "리뷰 판정 이력", description = "제출한 리뷰 판정(승인, 변경 요청, 댓글)을 제출한 순서로 돌려준다. 해제된 리뷰도 `dismissed`로 표시되어 나온다.")
    @ApiResponse(responseCode = "200", description = "리뷰 목록", content = [Content(schema = Schema(implementation = ReviewList::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/{number}/reviews")
    @Transactional(readOnly = true)
    fun reviews(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        return support.ok(linkedMapOf("reviews" to pullRequest.reviewHistory.sortedWith(compareBy({ it.createdDate }, { it.id })).map { json.review(it) }))
    }

    /**
     * 리뷰 댓글을 스레드별로 돌려준다. 공개된 스레드에 더해 **호출한 사용자 본인의** 임시 댓글(`pending: true`)이 함께 나온다(`decisions.md` D-38).
     * 응답은 `{"threads": [...]}`이다.
     */
    @ApiOperation(operationId = "listPullRequestComments", tags = [ApiTags.PULL_REQUEST_REVIEW], summary = "코드 리뷰 댓글(스레드별)", description = "스레드별로 댓글을 돌려준다. 공개된 스레드에 더해 호출한 사용자 본인의 임시 댓글(`pending: true`)이 함께 나온다.")
    @ApiResponse(responseCode = "200", description = "스레드 목록", content = [Content(schema = Schema(implementation = ThreadList::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/{number}/comments")
    @Transactional(readOnly = true)
    fun comments(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        val viewer = requireNotNull(target.user)
        val repliesByThread = pendingReader.replies(pullRequest, viewer).groupBy { requireNotNull(requireNotNull(it.thread).id) }
        val visible = pullRequestService.threads(pullRequest).map { json.thread(it, it.reviewComments + repliesByThread[it.id].orEmpty()) }
        val own: List<Map<String, Any?>> = pendingReader.threads(pullRequest, viewer).map { thread: CommentThread -> json.thread(thread, pendingReader.threadComments(thread)) }
        return support.ok(linkedMapOf("threads" to visible + own))
    }

    /**
     * 리뷰를 요청할 수 있는 사람을 찾는다(이 풀 리퀘스트의 프로젝트나 소속 조직의 구성원, 작성자와 이미 요청된 사람은 제외). `query`는 이름이나 로그인 ID의 일부이고
     * 생략하면 후보 전체이다(최대 100명). 풀 리퀘스트를 고칠 수 있는 사람만 조회한다. 응답은 `{"users": [{"loginId", "name"}]}`이다.
     */
    @ApiOperation(operationId = "listReviewerCandidates", tags = [ApiTags.PULL_REQUEST], summary = "리뷰어 후보", description = "리뷰를 요청할 수 있는 사람이다(프로젝트나 소속 조직의 구성원, 작성자와 이미 요청된 사람은 제외). 풀 리퀘스트를 고칠 수 있는 사람만 조회한다. 최대 100명이다.")
    @io.swagger.v3.oas.annotations.Parameter(name = "query", description = "이름이나 로그인 ID의 일부. 생략하면 후보 전체", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @ApiResponse(responseCode = "200", description = "후보 목록", content = [Content(schema = Schema(implementation = AssignableUserList::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/{number}/reviewercandidates")
    @Transactional(readOnly = true)
    fun reviewerCandidates(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @RequestParam(required = false, defaultValue = "") query: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, dev.yona.domain.enumeration.Operation.UPDATE)
        target.error?.let { return it }
        val users = reviewRequests.candidates(requireNotNull(target.pullRequest), query, target.user, listAll = true, limit = MAX_PAGE_SIZE)
        return support.ok(linkedMapOf("users" to users.map { json.user(it) }))
    }

    /** 담당자로 지정할 수 있는 사람을 찾는다(받는 프로젝트의 이슈 담당자 후보 규칙). `query`는 이름이나 로그인 ID의 일부이고 생략하면 전체이다. 응답은 `{"users": [...]}`이다. */
    @ApiOperation(operationId = "listPullRequestAssigneeCandidates", tags = [ApiTags.PULL_REQUEST], summary = "담당자 후보", description = "담당자로 지정할 수 있는 사람이다(받는 프로젝트의 이슈 담당자 후보 규칙). 풀 리퀘스트를 고칠 수 있는 사람만 조회한다. 최대 100명이다.")
    @io.swagger.v3.oas.annotations.Parameter(name = "query", description = "이름이나 로그인 ID의 일부. 생략하면 전체", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @ApiResponse(responseCode = "200", description = "후보 목록", content = [Content(schema = Schema(implementation = AssignableUserList::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/{number}/assigneecandidates")
    @Transactional(readOnly = true)
    fun assigneeCandidates(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @RequestParam(required = false, defaultValue = "") query: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, dev.yona.domain.enumeration.Operation.UPDATE)
        target.error?.let { return it }
        val needle = query.trim().lowercase()
        val users = assignees.candidates(requireNotNull(target.pullRequest), target.user).filter {
            needle.isEmpty() || it.loginId.orEmpty().lowercase().contains(needle) || it.name.orEmpty().lowercase().contains(needle)
        }.take(MAX_PAGE_SIZE)
        return support.ok(linkedMapOf("users" to users.map { json.user(it) }))
    }

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
