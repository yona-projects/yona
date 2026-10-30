package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueService
import dev.yona.domain.issue.IssueSharerRepository
import dev.yona.domain.issue.IssueSharerService
import dev.yona.domain.misc.WatchService
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.UserRepository
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.JsonNode

/**
 * 이슈의 Yona 고유 쓰기 기능을 토큰으로 쓰는 REST이다(로드맵 X-29d, `decisions.md` D-47): 공유자, 가중치, 지켜보기. 웹 화면에만 있던
 * `.../issue/{n}/share`, `upvoteWeight`, `downvoteWeight`, `/watch`의 토큰 버전이고 GitHub에는 없는 기능이라 Yona 방식으로 만들었다.
 * 경로는 v1.6의 `/-_-api/v1/owners/{owner}/projects/{projectName}` 접두사에 자원 경로를 얹은 것이다(X-5).
 *
 * 토큰이 할 수 있는 일은 범위와 사용자의 실제 권한의 교집합이다(D-36 4번). 공유자와 가중치는 이슈를 고칠 수 있어야 하고(`UPDATE`),
 * 지켜보기는 읽을 수 있으면 된다(`READ`). 웹 화면과 같은 판정이다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalIssueExtrasApiController(
    private val projectService: ProjectService,
    private val projects: ProjectRepository,
    private val issues: IssueRepository,
    private val issueService: IssueService,
    private val sharers: IssueSharerRepository,
    private val sharerService: IssueSharerService,
    private val watchService: WatchService,
    private val users: UserRepository,
    private val requests: ApiRequests,
    private val accessControl: AccessControl
) {

    // --- 공유자 ---

    /** 이슈의 공유자 목록이다. 추가한 순서이고 `{"loginId", "name"}`을 담는다. 읽을 수 있어야 한다. 결과: `{"result": [...]}`. */
    @GetMapping("$PROJECT/issues/{number}/sharers")
    @Transactional(readOnly = true)
    fun listSharers(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.READ)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        return ok(linkedMapOf("result" to sharersOf(issue)))
    }

    /**
     * 공유자를 더한다. 본문 `{"sharers": [로그인 ID], "projects": [프로젝트 번호]}` 중 하나 이상이다. 프로젝트를 주면 그 구성원이 모두 공유자가 된다(웹의
     * "프로젝트로 공유"와 같다). 없는 사용자나 읽을 수 없는 프로젝트가 하나라도 있으면 아무것도 바꾸지 않고 400이다. 이슈를 고칠 수 있어야 한다.
     * 결과는 바뀐 뒤의 공유자 목록이다.
     */
    @PostMapping("$PROJECT/issues/{number}/sharers")
    @Transactional
    fun addSharers(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any = changeSharers(owner, projectName, number, request, true)

    /** 공유자를 뺀다. 본문은 [addSharers]와 같다. 공유자가 아니어도 그대로 성공한다. */
    @DeleteMapping("$PROJECT/issues/{number}/sharers")
    @Transactional
    fun removeSharers(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any = changeSharers(owner, projectName, number, request, false)

    private fun changeSharers(owner: String, projectName: String, number: Long, request: HttpServletRequest, add: Boolean): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val loginIds = list(json, "sharers") { it.asString("").takeIf { text -> text.isNotBlank() } } ?: return requests.message(HttpStatus.BAD_REQUEST, "sharers must be a list of login ids")
        val projectIds = list(json, "projects") { if (it.isNumber) it.asLong() else null } ?: return requests.message(HttpStatus.BAD_REQUEST, "projects must be a list of project numbers")
        if (loginIds.isEmpty() && projectIds.isEmpty()) return requests.message(HttpStatus.BAD_REQUEST, "sharers or projects is required")
        loginIds.firstOrNull { users.findByLoginIdIgnoreCase(it) == null }?.let { return requests.message(HttpStatus.BAD_REQUEST, "no user by request: $it") }
        projectIds.firstOrNull { id -> projects.findById(id).orElse(null)?.let { accessControl.isAllowed(user, it.asResource(), Operation.READ) } != true }
            ?.let { return requests.message(HttpStatus.BAD_REQUEST, "no project by request: $it") }
        loginIds.forEach { sharerService.changeByUser(issue, user, it, add, user.lang) }
        projectIds.forEach { sharerService.changeByProject(issue, user, it, add) }
        return ok(linkedMapOf("result" to sharersOf(issue)))
    }

    /** 본문의 [key] 항목을 목록으로 읽는다. 없으면 빈 목록이고, 배열이 아니거나 [convert]가 `null`을 돌려주는 값이 있으면 `null`이다. */
    private fun <T : Any> list(json: JsonNode, key: String, convert: (JsonNode) -> T?): List<T>? {
        if (!json.has(key)) return emptyList()
        val node = json.get(key)
        if (!node.isArray) return null
        val converted = node.toList().map(convert)
        return if (converted.any { it == null }) null else converted.filterNotNull().distinct()
    }

    private fun sharersOf(issue: Issue): List<Map<String, Any?>> =
        sharers.findByIssueId(requireNotNull(issue.id)).sortedBy { it.id }.map { linkedMapOf("loginId" to it.loginId, "name" to it.user?.name) }

    // --- 가중치 ---

    /** 이슈의 가중치(중요도)를 올리거나 내린다. 본문 `{"delta": 1}` 또는 `{"delta": -1}`만 받는다(웹의 위, 아래 단추와 같다). 이슈를 고칠 수 있어야 한다. 결과: `{"result": {"weight": 새 값}}`. */
    @PostMapping("$PROJECT/issues/{number}/weight")
    @Transactional
    fun weight(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val delta = json.get("delta")?.takeIf { it.isNumber }?.asInt()?.takeIf { it == 1 || it == -1 } ?: return requests.message(HttpStatus.BAD_REQUEST, "delta must be 1 or -1")
        return ok(linkedMapOf("result" to linkedMapOf("weight" to issueService.adjustWeight(issue, delta))))
    }

    // --- 지켜보기 ---

    /** 이슈를 지켜본다. 이미 지켜보고 있어도 그대로 성공한다. 읽을 수 있어야 한다. 결과: `{"result": {"watching": true}}`. */
    @PostMapping("$PROJECT/issues/{number}/watchers")
    @Transactional
    fun watch(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.READ)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        watchService.watch(user, ResourceType.ISSUE_POST, requireNotNull(issue.id).toString())
        return ok(linkedMapOf("result" to linkedMapOf("watching" to true)))
    }

    /** 이슈 지켜보기를 그만둔다. 이미 그만둔 상태여도 그대로 성공한다. 읽을 수 있어야 한다. 결과: `{"result": {"watching": false}}`. */
    @DeleteMapping("$PROJECT/issues/{number}/watchers")
    @Transactional
    fun unwatch(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.READ)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        watchService.unwatch(user, ResourceType.ISSUE_POST, requireNotNull(issue.id).toString())
        return ok(linkedMapOf("result" to linkedMapOf("watching" to false)))
    }

    private fun find(owner: String, projectName: String, number: Long): Issue? {
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return null
        return issues.findByProjectIdAndNumber(requireNotNull(project.id), number)
    }

    private fun ok(body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)

    private companion object {
        const val PROJECT = "/-_-api/v1/owners/{owner}/projects/{projectName}"
    }
}
