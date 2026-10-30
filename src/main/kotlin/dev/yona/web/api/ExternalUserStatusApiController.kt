package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueSearchService
import dev.yona.domain.notification.NotificationEventRepository
import dev.yona.domain.notification.NotificationMessages
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.open
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.web.access.AnonymousCheck
import jakarta.persistence.EntityManager
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * 사용자 현황 REST이다(로드맵 X-37e, `decisions.md` D-52). `gh status`처럼 토큰 주인에게 걸린 일을 한 번에 준다: 담당 이슈, 담당 풀 리퀘스트, 리뷰 요청, 멘션된 이슈, 최근 알림.
 * 각 구역은 열림과 닫힘 수(`openCount`, `closedCount`)와 최근 수정 순서의 항목(`items`, `limit`개까지)이며, 토큰 주인이 읽을 수 없는 프로젝트의 것은 세지도 보이지도 않는다
 * (D-36 4번). 항목의 모양은 이슈 목록(`IssueSummary`)과 풀 리퀘스트(`PullRequestDetail`)와 같다. 개인의 비공개 프로젝트까지 걸리므로 범위는 `repo`이다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalUserStatusApiController(
    private val entityManager: EntityManager,
    private val issues: IssueRepository,
    private val issueSearch: IssueSearchService,
    private val users: UserRepository,
    private val notificationEvents: NotificationEventRepository,
    private val notificationMessages: NotificationMessages,
    private val projectJson: ProjectJson,
    private val pullRequestJson: PullRequestJson,
    private val requests: ApiRequests,
    private val accessControl: AccessControl
) {

    /**
     * 토큰 주인의 현황이다. 쿼리 `limit`(1에서 100, 기본 10)은 구역마다 보여 줄 항목 수이다. 틀리면 400이다.
     * 결과: `{"assignedIssues", "assignedPullRequests", "reviewRequests", "mentionedIssues": {"openCount", "closedCount", "items"}, "notifications": [...]}`.
     */
    @ApiOperation(operationId = "getMyStatus", tags = [ApiTags.USER], summary = "내 현황", description = "토큰 주인에게 걸린 일을 한 번에 돌려준다: 담당 이슈, 담당 풀 리퀘스트, 리뷰 요청, 멘션된 이슈(구역마다 열림과 닫힘 수와 열린 항목), 최근 알림 10건. 토큰 주인이 읽을 수 없는 프로젝트의 풀 리퀘스트는 세지 않는다(담당자로 지정된 이슈는 v1.6처럼 읽을 수 있다). 비공개 프로젝트까지 걸리므로 `repo` 범위가 필요하다.")
    @io.swagger.v3.oas.annotations.Parameter(name = "limit", description = "구역마다 보여 줄 항목 수(1에서 100)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "10")
    @ApiResponse(responseCode = "200", description = "현황", content = [Content(schema = Schema(implementation = UserStatusResponse::class))])
    @ApiResponse(responseCode = "400", description = "`limit`이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/user/status")
    @Transactional(readOnly = true)
    fun status(request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val limit = request.getParameter("limit")?.let { it.toIntOrNull()?.takeIf { n -> n in 1..MAX_LIMIT } ?: return requests.message(HttpStatus.BAD_REQUEST, "limit must be 1 to $MAX_LIMIT") } ?: DEFAULT_LIMIT
        val me = requireNotNull(user.id)

        val assigned = entityManager.createQuery("select i from Issue i join i.assignee a where a.user.id = :me order by i.updatedDate desc, i.id desc", Issue::class.java)
            .setParameter("me", me).resultList
        val mentioned = issues.findAllById(issueSearch.mentioningIssueIds(me)).sortedWith(compareByDescending<Issue> { it.updatedDate }.thenByDescending { it.id })
        val assignedPulls = entityManager.createQuery("select pr from PullRequest pr join pr.assignees a where a.id = :me order by pr.updated desc, pr.id desc", PullRequest::class.java)
            .setParameter("me", me).resultList
        val reviewRequested = entityManager.createQuery("select pr from PullRequest pr join pr.reviewRequests r where r.id = :me order by pr.updated desc, pr.id desc", PullRequest::class.java)
            .setParameter("me", me).resultList

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
            linkedMapOf(
                "assignedIssues" to issueSection(user, assigned, limit), "assignedPullRequests" to pullSection(user, assignedPulls, limit),
                "reviewRequests" to pullSection(user, reviewRequested, limit), "mentionedIssues" to issueSection(user, mentioned, limit),
                "notifications" to notificationEvents.findByReceiver(user, 0, NOTIFICATIONS).map { event ->
                    val sender = event.senderId?.let { users.findById(it).orElse(null) }
                    linkedMapOf(
                        "id" to event.id, "type" to event.eventType?.name, "title" to event.title, "url" to notificationMessages.urlToView(event),
                        "createdAt" to pullRequestJson.iso(event.created), "sender" to pullRequestJson.user(sender)
                    )
                }
            )
        )
    }

    /** 읽을 수 있는 이슈만 세고 담는다. 초안은 뺀다. 열린 것이 앞에 오고(최근 수정 순서) 항목은 열린 것만이다. */
    private fun issueSection(user: User, all: List<Issue>, limit: Int): Map<String, Any?> {
        val visible = all.filter { it.isDraft != true && accessControl.isAllowed(user, it.asResource(), Operation.READ) }
        val open = visible.filter { it.state == State.OPEN }
        return linkedMapOf(
            "openCount" to open.size, "closedCount" to visible.count { it.state == State.CLOSED },
            "items" to open.take(limit).map { projectJson.issueSummary(it, it.authorId?.let { id -> users.findById(id).orElse(null) }) }
        )
    }

    private fun pullSection(user: User, all: List<PullRequest>, limit: Int): Map<String, Any?> {
        val visible = all.filter { accessControl.isAllowed(user, it.asResource(), Operation.READ) }
        val open = visible.filter { it.open }
        return linkedMapOf("openCount" to open.size, "closedCount" to visible.count { !it.open }, "items" to open.take(limit).map { pullRequestJson.of(it) })
    }

    private companion object {
        const val DEFAULT_LIMIT = 10
        const val MAX_LIMIT = 100
        const val NOTIFICATIONS = 10
    }
}
