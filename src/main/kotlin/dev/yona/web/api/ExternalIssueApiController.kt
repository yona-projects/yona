package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.descrKey
import dev.yona.domain.issue.ExternalIssue
import dev.yona.domain.issue.ExternalIssueService
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEventRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.PostAttachments
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsCreatable
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.JsonNode
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 외부 도구(마이그레이션, 자동화)가 쓰는 이슈 REST API이다. v1.6 `IssueApi`의 `getIssue`, `newIssues`, `updateIssue`, `updateIssueState`,
 * `newIssueComment`, `updateIssueLabel`, `imports`(`app/controllers/api/IssueApi.java:58-235, 246-476, 515-535, 637-672`)에 해당한다.
 *
 * 인증은 `Authorization: token <값>` 헤더의 개인 토큰이다. 이슈 만들기, 라벨 바꾸기, 가져오기는 마이그레이션 화면이 세션 로그인으로도 쓴다.
 * 토큰으로만 인증하는 경로(조회, 수정, 상태 변경, 토큰 댓글)는 v1.6처럼 개인 토큰이 유효하면 프로젝트 권한을 확인하지 않는다.
 * 토큰은 사용자가 외부 도구에 맡긴 보안 자격이라고 보기 때문이다([decisions.md] D-20). 세션이나 토큰 어느 쪽으로도 부르는 라벨 바꾸기와
 * 글 가져오기는 v1.6에 인증 확인이 없었으므로 프로젝트 권한을 확인한다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalIssueApiController(
    private val projectService: ProjectService,
    private val issues: IssueRepository,
    private val issueEvents: IssueEventRepository,
    private val postings: PostingRepository,
    private val comments: IssueCommentRepository,
    private val labels: IssueLabelRepository,
    private val service: ExternalIssueService,
    private val issueJson: IssueJson,
    private val apiUsers: ApiUsers,
    private val requests: ApiRequests,
    private val users: UserRepository,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val attachments: PostAttachments,
    private val messages: MessageSource,
    private val views: dev.yona.web.project.ProjectViewFactory
) {

    // --- 조회 ---

    /** 이슈 하나와 그 이벤트를 JSON으로 돌려준다. 토큰이 필요하다. */
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}")
    @Transactional(readOnly = true)
    fun getIssue(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return requests.message(HttpStatus.BAD_REQUEST, "no project by request")
        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return requests.message(HttpStatus.BAD_REQUEST, "no issue by request")
        return result(HttpStatus.OK, issue, user, request)
    }

    // --- 등록 ---

    /**
     * 이슈 여러 개를 만든다. 본문: `{"issues": [...], "sendNotification": 불리언}`. 항목마다 `{"status": 201, "location": 주소}`를 담아 `201`로 답한다.
     * `number`를 주면 그 번호로 만들고(이슈 가져오기), `author`는 이메일로 찾고 없으면 사용자를 만든다.
     */
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues")
    @IsCreatable(ResourceType.ISSUE_POST)
    @Transactional
    fun newIssues(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issuesNode = json.findValue("issues")
        if (issuesNode == null || !issuesNode.isArray) return requests.message(HttpStatus.BAD_REQUEST, "No issues key exists or value wasn't array!")
        val notify = json.findValue("sendNotification")?.asBoolean(false) == true
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")

        val created = issuesNode.toList().map { node ->
            val title = node.findValue("title")?.asString() ?: return requests.message(HttpStatus.BAD_REQUEST, "title is required")
            val author = findAuthor(node.findValue("author"), viewer) ?: return requests.message(HttpStatus.BAD_REQUEST, "The author could not be found or created")
            val issue = service.create(
                project, author,
                ExternalIssue(
                    title = title, body = node.findValue("body")?.asString().orEmpty(), state = state(node), createdDate = parseDate(node.findValue("createdAt")),
                    updatedDate = parseDate(node.findValue("updatedAt")), assignee = assigneeByEmail(node.findValue("assignees")), milestoneTitle = node.findValue("milestoneTitle")?.asString(),
                    dueDate = parseDate(node.findValue("dueDate")), labels = labelPairs(node.findValue("labels")), number = node.findValue("number")?.asLong(0)?.takeIf { it > 0 }
                ),
                notify
            )
            attachments.attachIds(viewer, ids(node.findValue("temporaryUploadFiles")), ResourceType.ISSUE_POST, requireNotNull(issue.id))
            linkedMapOf("status" to 201, "location" to "/${project.owner}/${project.name}/issue/${issue.number}")
        }
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(created)
    }

    // --- 수정 ---

    /** 이슈의 제목, 본문, 마일스톤, 상태, 담당자를 한꺼번에 바꾼다. 상태와 담당자가 바뀌면 이벤트를 남긴다. 토큰이 필요하다. */
    @PutMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}")
    @Transactional
    fun updateIssue(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val title = json.findValue("title")?.asString()
        val body = json.findValue("body")?.asString()
        val assignees = json.findValue("assignees")
        if (title == null || body == null || json.findValue("state") == null || assignees == null) return requests.message(HttpStatus.BAD_REQUEST, "title, body, state and assignees are required")
        service.update(issue, user, title, body, json.findValue("milestoneTitle")?.asString(), state(json), assigneeByEmail(assignees))
        return result(HttpStatus.OK, issue, user, request)
    }

    /** 이슈의 상태만 바꾼다. `state`가 `OPEN`(대소문자 무시)이 아니면 닫힘이다. 바뀌면 이벤트를 남긴다. 토큰이 필요하다. */
    @PatchMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}")
    @Transactional
    fun updateIssueState(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        service.updateState(issue, user, state(json))
        return result(HttpStatus.OK, issue, user, request)
    }

    // --- 댓글 ---

    /**
     * 이슈에 댓글을 단다. `Authorization` 헤더가 있으면 토큰으로 `{"comment": 내용}`을 받아 이슈 결과를 `201`로 돌려주고,
     * 없으면 로그인한 사용자가 `{"body", "createdAt", "author", "temporaryUploadFiles"}`를 보내 `{"status": 201, "location": 주소}`로 답한다.
     * 가져오기용이라 알림은 보내지 않는다.
     */
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/comments")
    @Transactional
    fun newIssueComment(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return ResponseEntity.badRequest().contentType(MediaType("text", "plain", Charsets.UTF_8)).body("Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val project = requireNotNull(issue.project)
        if (request.getHeader("Authorization") != null) {
            val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
            val text = json.findValue("comment")?.asString() ?: return requests.message(HttpStatus.BAD_REQUEST, "comment is required")
            service.addComment(issue, user, text, null)
            return result(HttpStatus.CREATED, issue, user, request)
        }
        val viewer = currentUserProvider.get().user
        if (!accessControl.isResourceCreatable(viewer, issue.asResource(), ResourceType.ISSUE_COMMENT)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        val author = findAuthor(json.findValue("author"), requireNotNull(viewer)) ?: return requests.message(HttpStatus.BAD_REQUEST, "The author could not be found or created")
        val comment = service.addComment(issue, author, json.findValue("body")?.asString().orEmpty(), parseDate(json.findValue("createdAt")))
        attachments.attachIds(viewer, ids(json.findValue("temporaryUploadFiles")), ResourceType.ISSUE_COMMENT, requireNotNull(comment.id))
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(linkedMapOf("status" to 201, "location" to "/${project.owner}/${project.name}/issue/${issue.number}#comment-${comment.id}"))
    }

    // --- 라벨 ---

    /** 이슈의 라벨을 본문의 라벨 번호 배열로 바꾼다. 이슈를 고칠 수 있어야 한다. 결과: `{"id": 소유자, "labels": 개수}`. */
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issuelabel/{number}")
    @Transactional
    fun updateIssueLabel(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return ResponseEntity.badRequest().contentType(MediaType("text", "plain", Charsets.UTF_8)).body("Expecting Json data")
        val viewer = currentUserProvider.get().user ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(viewer, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val found = json.toList().mapNotNull { it.asString("").toLongOrNull()?.let { id -> labels.findById(id).orElse(null) } }.toSet()
        service.replaceLabels(issue, found)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("id" to issue.project?.owner, "labels" to issue.labels.size))
    }

    // --- 가져오기 ---

    /**
     * 게시글을 이슈로 옮긴다. `postNumber`로 글을 고른다. 글의 댓글과 첨부가 이슈로 옮겨지고 글은 지워진다. 결과: `{"number": 새 이슈 번호}`.
     * 글을 지울 수 있고 이슈를 만들 수 있어야 한다.
     */
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/imports")
    @Transactional
    fun imports(@PathVariable owner: String, @PathVariable projectName: String, @RequestParam(required = false) postNumber: String?): Any {
        val viewer = currentUserProvider.get().user ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val number = postNumber?.toLongOrNull() ?: return ResponseEntity.badRequest().build<Void>()
        val posting = postings.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return ErrorViews.notFound()
        if (!accessControl.isProjectResourceCreatable(viewer, project, ResourceType.ISSUE_POST) || !accessControl.isAllowed(viewer, posting.asResource(), Operation.DELETE)) {
            return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        }
        val issue = service.importFromPosting(posting)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("number" to issue.number))
    }

    // --- 도우미 ---

    private fun find(owner: String, projectName: String, number: Long): Issue? {
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return null
        return issues.findByProjectIdAndNumber(requireNotNull(project.id), number)
    }

    /** `{"result": 이슈 + 이벤트}` 응답이다. 이벤트가 하나도 없으면 `events` 키를 뺀다. */
    private fun result(status: HttpStatus, issue: Issue, viewer: User, request: HttpServletRequest): ResponseEntity<*> {
        val node = LinkedHashMap(issueJson.of(issue, request))
        val events = issueEvents.findByIssueIdOrderByCreatedAscIdAsc(requireNotNull(issue.id))
        if (events.isNotEmpty()) {
            node["events"] = events.map { event ->
                val sender = event.senderLoginId?.let { users.findByLoginId(it) }
                linkedMapOf(
                    "id" to event.id, "createdDate" to issueJson.iso(event.created), "eventType" to event.eventType?.name,
                    "eventDescription" to event.eventType?.let { messages.getMessage(it.descrKey, null, LocaleContextHolder.getLocale()) },
                    "oldValue" to event.oldValue, "newValue" to event.newValue,
                    "actor" to linkedMapOf("name" to sender?.let { UserDisplay.pureName(it, viewer.lang) }, "loginId" to sender?.loginId, "englishName" to sender?.englishName)
                )
            }
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to node))
    }

    /** `state`가 없으면 열림, `OPEN`(대소문자 무시)이면 열림, 그 밖은 닫힘이다 (v1.6 `findIssueState`). */
    private fun state(json: JsonNode): State {
        val node = json.findValue("state") ?: return State.OPEN
        return if (node.asString("").equals("OPEN", ignoreCase = true)) State.OPEN else State.CLOSED
    }

    /** `yyyy-MM-dd a hh:mm:ss Z` 형식(영어)의 시각이다. 없거나 읽을 수 없으면 `null`이다 (v1.6 `parseDateString`). */
    private fun parseDate(node: JsonNode?): Date? {
        if (node == null) return null
        return try { SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH).parse(node.asString()) } catch (e: ParseException) { null }
    }

    /** 담당자 배열의 첫 항목을 이메일로 찾는다 (v1.6 `findAssginee`). 없으면 `null`이다. */
    private fun assigneeByEmail(node: JsonNode?): User? {
        if (node == null || !node.isArray || node.size() == 0) return null
        val email = node.get(0).findValue("email")?.asString() ?: return null
        return users.findByEmail(email)
    }

    private fun labelPairs(node: JsonNode?): List<Pair<String, String>> {
        if (node == null || !node.isArray) return emptyList()
        return node.toList().mapNotNull { n ->
            val name = n.findValue("labelName")?.asString() ?: return@mapNotNull null
            val category = n.findValue("category")?.asString() ?: return@mapNotNull null
            name to category
        }
    }

    private fun ids(node: JsonNode?): List<String> = if (node == null || !node.isArray) emptyList() else node.toList().map { it.asString("") }

    /**
     * 작성자를 정한다. `author`가 있으면 이메일로 찾고 없으면 사용자를 만든다. `author`가 없으면 요청한 사용자이다 (v1.6 `findAuthor`).
     * 찾지도 만들지도 못하면 `null`이다.
     */
    private fun findAuthor(node: JsonNode?, viewer: User): User? {
        if (node == null) return viewer
        val email = node.findValue("email")?.asString() ?: return null
        users.findByEmail(email)?.let { return it }
        apiUsers.create(node.findValue("loginId")?.asString().orEmpty(), node.findValue("name")?.asString().orEmpty(), email)
        return users.findByEmail(email)
    }
}
