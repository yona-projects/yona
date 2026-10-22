package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueCommentService
import dev.yona.domain.issue.IssueListService
import dev.yona.domain.issue.IssueMassUpdateService
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueSharerRepository
import dev.yona.domain.issue.IssueSharerService
import dev.yona.domain.issue.IssueService
import dev.yona.domain.issue.MassEdit
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import jakarta.persistence.EntityManager
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
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
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest

/**
 * 이슈 화면이 쓰는 JSON API이다. v1.6 `IssueApi`(`app/controllers/api/IssueApi.java`)의 담당자, 공유자, 가중치, 변경 감지, 본문과 댓글 갱신에 해당한다.
 *
 * JSON을 요구하는 요청에 JSON이 아닌 본문이 오면 `400`(`Expecting Json data`)이다. 다른 사람이 먼저 고친 본문을 덮어쓰려 하면 `409`이다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class IssueApiController(
    private val projectService: ProjectService,
    private val issues: IssueRepository,
    private val comments: IssueCommentRepository,
    private val sharers: IssueSharerRepository,
    private val sharerService: IssueSharerService,
    private val issueService: IssueService,
    private val commentService: IssueCommentService,
    private val massService: IssueMassUpdateService,
    private val listService: IssueListService,
    private val nodesOf: ApiUserNodes,
    private val receivers: dev.yona.domain.notification.NotificationReceivers,
    private val issueJson: IssueJson,
    private val users: UserRepository,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val entityManager: EntityManager
) {
    private val mapper = JsonMapper.builder().build()

    // --- 담당자 ---

    /**
     * 이슈의 담당자 후보이다. 검색어가 없으면 "나에게 할당", 작성자, "담당자 없음", 현재 담당자, 담당할 수 있는 사용자 순이고,
     * 있으면 활성 사용자 검색 결과(최대 10명)이다.
     */
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/assignableUsers")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun assignableUsers(
        @PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long,
        @RequestParam(name = "query", defaultValue = "") query: String, @RequestParam(name = "type", required = false) type: String?, request: HttpServletRequest
    ): Any {
        if (!nodesOf.acceptsJson(request)) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val nodes = mutableListOf<Map<String, Any?>>()

        if (query.isEmpty()) {
            val author = issue.authorId?.let { users.findById(it).orElse(null) }
            val assignee = issue.assignee?.user
            val viewerLogin = viewer?.loginId.orEmpty()
            if (assignee != null) {
                if (viewer != null && viewerLogin != assignee.loginId) nodesOf.customNode(viewerLogin, nodesOf.message("issue.assignToMe"), nodes)
                if (author != null && author.loginId != viewerLogin && author.loginId != assignee.loginId) nodesOf.customNode(author.loginId, nodesOf.message("issue.assignToAuthor"), nodes)
                nodesOf.customNode("", nodesOf.message("issue.noAssignee"), nodes)
                nodesOf.userNode(assignee, viewer, nodes)
            } else {
                if (viewer != null) nodesOf.customNode(viewerLogin, nodesOf.message("issue.assignToMe"), nodes)
                if (author != null && author.loginId != viewerLogin) nodesOf.customNode(author.loginId, nodesOf.message("issue.assignToAuthor"), nodes)
            }
            val assignable = listService.assignableUsers(project, viewer, accessControl.isSiteManager(viewer))
            (assignable + listOfNotNull(assignee).filter { a -> assignable.none { it.id == a.id } }).forEach { nodesOf.userNode(it, viewer, nodes) }
            return nodesOf.json(nodes)
        }

        val found = nodesOf.search(query, type)
        found.take(ApiUserNodes.MAX_FETCH_USERS).filter { nodesOf.isCandidate(project, it) }.forEach { nodesOf.userNode(it, viewer, nodes) }
        val builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        if (found.size > ApiUserNodes.MAX_FETCH_USERS) builder.header(HttpHeaders.CONTENT_RANGE, "items ${ApiUserNodes.MAX_FETCH_USERS}/${found.size}")
        return builder.body(nodes)
    }

    /**
     * 담당자를 바꾼다. 본문은 `{"assignees": ["로그인아이디"]}`이다. 로그인 아이디가 비었거나 없는 사용자이면 담당자를 비운다.
     * 수정 권한이 없으면 아무것도 바꾸지 않고 이슈 주소만 돌려준다(v1.6과 같다).
     */
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/assignees")
    @Transactional
    fun updateAssignees(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val json = body(request) ?: return message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val (project, issue) = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val result = linkedMapOf<String, Any?>()
        if (viewer != null && accessControl.isAllowed(viewer, issue.asResource(), Operation.UPDATE)) {
            val assignees = json.findValue("assignees")
            if (assignees == null || assignees.size() == 0) return message(HttpStatus.BAD_REQUEST, "No assignee")
            for (item in assignees) {
                val target = users.findByLoginIdIgnoreCase(item.asString(""))
                massService.apply(issue, viewer, MassEdit(assigneeId = target?.id ?: MassEdit.NONE))
                result["assignee"] = linkedMapOf("loginId" to target?.loginId.orEmpty(), "name" to (target?.let { UserDisplay.of(it, viewer.lang) } ?: nodesOf.message("common.none")))
            }
        }
        result["issue"] = "/${project.owner}/${project.name}/issue/$number"
        return nodesOf.json(result)
    }

    // --- 공유자 ---

    /** 로그인 아이디(쉼표로 구분)로 이슈의 공유자를 찾아 추가한 날짜 순으로 돌려준다. */
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/findSharer")
    @Transactional(readOnly = true)
    fun findSharer(
        @PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long,
        @RequestParam(name = "query", defaultValue = "") query: String, request: HttpServletRequest
    ): Any {
        if (!nodesOf.acceptsJson(request)) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        val (_, issue) = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val nodes = mutableListOf<Map<String, Any?>>()
        sharers.findByIssueIdAndLoginIdInOrderByCreatedAscIdAsc(requireNotNull(issue.id), query.split(",")).mapNotNull { it.user }.forEach { nodesOf.userNode(it, viewer, nodes) }
        return nodesOf.json(nodes)
    }

    /** 공유할 수 있는 사용자(활성 사용자)와 공개 프로젝트를 이름으로 찾는다. 합쳐서 10개를 넘으면 각각 5개까지만 보이고 `Content-Range`를 붙인다. */
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/sharableUsers")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun sharableUsers(
        @PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long,
        @RequestParam(name = "query", defaultValue = "") query: String, @RequestParam(name = "type", required = false) type: String?, request: HttpServletRequest
    ): Any {
        if (!nodesOf.acceptsJson(request)) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        val viewer = currentUserProvider.get().user
        val foundUsers = nodesOf.search(query, type)
        val foundProjects = entityManager.createQuery(
            "select p from Project p where p.projectScope = :scope and lower(p.name) like lower(concat('%', :q, '%')) order by p.id", Project::class.java
        ).setParameter("scope", ProjectScope.PUBLIC).setParameter("q", query).resultList
        val total = foundUsers.size + foundProjects.size
        val limit = if (total > ApiUserNodes.MAX_FETCH_USERS) ApiUserNodes.MAX_FETCH_USERS / 2 else Int.MAX_VALUE
        val nodes = mutableListOf<Map<String, Any?>>()
        foundUsers.take(limit).forEach { nodesOf.userNode(it, viewer, nodes) }
        foundProjects.take(limit).forEach {
            val node = linkedMapOf<String, Any?>("loginId" to it.id, "name" to "${it.owner}/${it.name}", "avatarUrl" to "", "type" to "project")
            if (node !in nodes) nodes += node
        }
        val builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        if (total > ApiUserNodes.MAX_FETCH_USERS) builder.header(HttpHeaders.CONTENT_RANGE, "items ${ApiUserNodes.MAX_FETCH_USERS}/$total")
        return builder.body(nodes)
    }

    /** 공유자를 추가하거나 삭제한다. 본문은 `{"sharer": {"loginId", "type": "user|project"}, "action": "add|delete"}`이다. */
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/share")
    @Transactional
    fun updateSharer(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val json = body(request) ?: return message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val (_, issue) = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        if (viewer == null || !accessControl.isAllowed(viewer, issue.asResource(), Operation.UPDATE)) return message(HttpStatus.FORBIDDEN, "Permission denied")
        val sharer = json.findValue("sharer")
        if (sharer == null || sharer.size() == 0) return message(HttpStatus.BAD_REQUEST, "No sharer")
        val action = json.findValue("action")?.asString("").orEmpty()
        val add = when {
            action.equals("add", ignoreCase = true) -> true
            action.equals("delete", ignoreCase = true) -> false
            else -> return message(HttpStatus.BAD_REQUEST, "Unsupported action: $action")
        }
        val loginId = sharer.findValue("loginId")?.asString("").orEmpty()
        val change = if (sharer.findValue("type")?.asString("") == "project") {
            loginId.toLongOrNull()?.let { sharerService.changeByProject(issue, viewer, it, add) }
        } else {
            sharerService.changeByUser(issue, viewer, loginId, add, viewer.lang)
        } ?: return message(HttpStatus.BAD_REQUEST, "Wrong sharer: $loginId")
        return nodesOf.json(linkedMapOf("action" to change.action, "sharer" to change.name))
    }

    // --- 가중치 ---

    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/upvoteWeight")
    @Transactional
    fun upvoteWeight(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long): Any = weight(owner, projectName, number, 1)

    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/downvoteWeight")
    @Transactional
    fun downvoteWeight(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long): Any = weight(owner, projectName, number, -1)

    private fun weight(owner: String, projectName: String, number: Long, delta: Int): Any {
        val (_, issue) = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        if (viewer == null || !accessControl.isAllowed(viewer, issue.asResource(), Operation.UPDATE)) return message(HttpStatus.FORBIDDEN, "Permission denied")
        return nodesOf.json(linkedMapOf("weight" to issueService.adjustWeight(issue, delta)))
    }

    // --- 변경 감지 ---

    /** 열려 있는 이슈 화면이 주기적으로 부르는 변경 감지이다. 본문 체크섬과 댓글 수를 비교해 결과를 돌려준다. */
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/detectChange")
    @Transactional(readOnly = true)
    fun detectChange(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val viewer = currentUserProvider.get().user ?: return message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = body(request) ?: return message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val (_, issue) = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val result = linkedMapOf<String, Any?>()
        val received = json.findValue("issueBodyChecksum")?.asString("").orEmpty()
        val receivedCount = json.findValue("numOfComments")?.asInt(0) ?: 0
        val all = comments.findByIssueIdOrderByCreatedDateAscIdAsc(requireNotNull(issue.id))
        if (receivedCount < all.size) {
            val last = all.last()
            result["commentAuthorName"] = last.authorLoginId?.let { users.findByLoginIdIgnoreCase(it) }?.let { UserDisplay.of(it, viewer.lang) }
        }
        val hex = sha1(issue.body.orEmpty())
        result["issueBodyChanged"] = hex != received
        result["numOfComments"] = all.size
        result["issueBodyChecksum"] = hex
        result["issueUpdateDate"] = issue.updatedDate?.time
        result["result"] = "ok"
        return nodesOf.json(result)
    }

    // --- 댓글 알림 수신자 미리 보기 ---

    /**
     * 댓글을 쓰는 중에 알림을 받을 사용자를 미리 보여 준다. 본문: `{"comment", "parentCommentId"}`(한 줄 댓글이면 부모 댓글 번호).
     * 응답: `{"receivers": [사용자 노드]}`. v1.6은 권한을 확인하지 않는다.
     */
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/commentNotiReceivers")
    @Transactional(readOnly = true)
    fun commentNotiReceivers(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val json = body(request) ?: return message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val (_, issue) = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val parentId = json.findValue("parentCommentId")?.asString("")?.takeIf { it.isNotBlank() }?.toLongOrNull()
        val parent = parentId?.let { comments.findById(it).orElse(null) }
        val set = receivers.forIssueComment(issue, viewer?.id, json.findValue("comment")?.asString("").orEmpty(), parent, dev.yona.domain.enumeration.EventType.NEW_COMMENT, viewer)
        val nodes = mutableListOf<Map<String, Any?>>()
        set.forEach { nodesOf.userNode(it, viewer, nodes) }
        return nodesOf.json(linkedMapOf("receivers" to nodes))
    }

    // --- 본문, 댓글 갱신 ---

    /** 체크리스트 갱신으로 이슈 본문을 바꾼다. 본문: `{"content", "original"}`. `original`이 저장된 본문과 다르면 `409`이다. */
    @PatchMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/content")
    @Transactional
    fun updateIssueContent(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val viewer = currentUserProvider.get().user ?: return message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = body(request) ?: return message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val (_, issue) = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(viewer, issue.asResource(), Operation.UPDATE)) return message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (modifiedByOthers(issue.body.orEmpty(), json.findValue("original")?.asString("").orEmpty())) return conflicted(issue.body.orEmpty())
        issueService.replaceBody(issue, json.findValue("content")?.asString("").orEmpty())
        return nodesOf.json(issueJson.of(issue, request))
    }

    /** 체크리스트 갱신으로 댓글 내용을 바꾼다. 화면은 `PATCH /:user/:project/issue/:number/comments/:id`, 외부 API는 `PUT`을 쓴다. */
    @PatchMapping("/{owner}/{projectName}/issue/{number:[0-9]+}/comments/{commentId}")
    @Transactional
    fun updateIssueComment(
        @PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable commentId: Long, request: HttpServletRequest
    ): Any = replaceComment(owner, projectName, number, commentId, request)

    /** 외부 API용 같은 처리이다. */
    @PutMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/comments/{commentId}")
    @Transactional
    fun updateIssueCommentByApi(
        @PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable commentId: Long, request: HttpServletRequest
    ): Any = replaceComment(owner, projectName, number, commentId, request)

    private fun replaceComment(
        @PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable commentId: Long, request: HttpServletRequest
    ): Any {
        val viewer = currentUserProvider.get().user ?: return message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = body(request) ?: return message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val (_, issue) = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val comment = comments.findById(commentId).orElse(null)?.takeIf { it.issue?.id == issue.id } ?: return ErrorViews.notFound()
        if (modifiedByOthers(comment.contents.orEmpty(), json.findValue("original")?.asString("").orEmpty())) return conflicted(comment.contents.orEmpty())
        if (!accessControl.isAllowed(viewer, comment.asResource(), Operation.UPDATE)) return message(HttpStatus.FORBIDDEN, "Forbidden request")
        commentService.replaceContents(comment, json.findValue("content")?.asString("").orEmpty())
        val node = linkedMapOf<String, Any?>("id" to comment.id, "contents" to comment.contents, "createdDate" to issueJson.iso(comment.createdDate))
        node["author"] = linkedMapOf("id" to viewer.id, "loginId" to viewer.loginId, "name" to viewer.name)
        return nodesOf.json(linkedMapOf("result" to node))
    }

    // --- 도우미 ---

    private fun find(owner: String, projectName: String, number: Long): Pair<Project, Issue>? {
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return null
        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return null
        return project to issue
    }

    /** JSON 본문을 읽는다. 내용 형식이 JSON이 아니거나 읽을 수 없으면 `null`이다 (Play의 `request().body().asJson()`). */
    private fun body(request: HttpServletRequest): JsonNode? {
        if (request.contentType?.contains("json", ignoreCase = true) != true) return null
        return try { mapper.readTree(request.inputStream) } catch (e: Exception) { null }
    }

    private fun message(status: HttpStatus, text: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(mapOf("message" to text))

    private fun conflicted(stored: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(mapOf("message" to "Already modified by someone.", "storedContent" to stored))

    /** 화면의 텍스트 영역은 줄 끝 `\r`을 지우므로, 두 본문에서 `\r`을 지우고 앞뒤 공백을 뗀 SHA-1을 비교한다 (v1.6 `isModifiedByOthers`). */
    private fun modifiedByOthers(current: String, fromView: String): Boolean =
        sha1(current.replace("\r", "").trim()) != sha1(fromView.replace("\r", "").trim())

    private fun sha1(text: String): String = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
