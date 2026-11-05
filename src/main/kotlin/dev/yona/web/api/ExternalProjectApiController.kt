package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.issue.MilestoneService
import dev.yona.domain.misc.Watch
import dev.yona.domain.notification.NotificationReceivers
import dev.yona.domain.project.ExternalProject
import dev.yona.domain.project.ExternalProjectService
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMemberService
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.IsCreatable
import dev.yona.web.error.ErrorViews
import jakarta.persistence.EntityManager
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.JsonNode
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * 외부 도구가 쓰는 프로젝트 REST API이다. v1.6 `ProjectApi`의 `newProject`, `newLabel`, `exports`, `MilestoneApi.newMilestone`, `WatcherApi.getWatchers`,
 * `GlobalApi.hello`(`app/controllers/api/`)에 해당한다.
 */
@Controller
@ResponseBody
class ExternalProjectApiController(
    private val projectService: ProjectService,
    private val projects: ProjectRepository,
    private val externalProjects: ExternalProjectService,
    private val organizations: OrganizationRepository,
    private val organizationUsers: OrganizationUserRepository,
    private val labelService: IssueLabelService,
    private val labels: IssueLabelRepository,
    private val milestoneService: MilestoneService,
    private val milestones: MilestoneRepository,
    private val issues: IssueRepository,
    private val postings: PostingRepository,
    private val assignees: AssigneeRepository,
    private val memberService: ProjectMemberService,
    private val userLookup: dev.yona.domain.user.UserLookup,
    private val receivers: NotificationReceivers,
    private val issueJson: IssueJson,
    private val users: UserRepository,
    private val requests: ApiRequests,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val siteUrls: SiteUrls,
    private val messages: MessageSource,
    private val entityManager: EntityManager
) {

    private fun created(body: Any) = ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(body)

    private fun ok(body: Any) = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)

    /** 서버가 살아 있는지 확인한다. */
    @ApiOperation(operationId = "hello", tags = [ApiTags.SYSTEM], summary = "서버 상태 확인", description = "서버가 살아 있으면 `{\"message\": \"I'm alive!\", \"ok\": true}`를 돌려준다. 토큰이 필요 없다.")
    @ApiResponse(responseCode = "200", description = "서버가 살아 있다.", content = [Content(schema = Schema(implementation = HelloResponse::class))])
    @GetMapping("/-_-api/v1/hello")
    fun hello(): Any = ok(linkedMapOf("message" to "I'm alive!", "ok" to true))

    // --- 프로젝트 만들기 ---

    /**
     * 프로젝트를 만든다. 사이트 관리자만 쓸 수 있다. 본문: `{"projectName", "projectDescription", "projectVcs", "projectScope", "members": [{"email", "role"}]}`.
     * 같은 이름이 있으면 `400`과 `{"status": 409}`, 소유자가 조직이면 조직 관리자여야 한다.
     */
    @ApiOperation(operationId = "createProject", tags = [ApiTags.PROJECT], summary = "프로젝트 만들기(사이트 관리자)", description = "프로젝트를 만든다. 사이트 관리자만 쓸 수 있다. 소유자가 조직이면 조직 관리자여야 한다. 같은 이름이 있으면 400과 `{\"status\": 409}`를 돌려준다. `members`에는 이메일로 구성원을 넣는다.")
    @BodyDoc(description = "만들 프로젝트", required = true, content = [Content(schema = Schema(implementation = ProjectCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "만든 프로젝트", content = [Content(schema = Schema(implementation = ProjectCreated::class))])
    @ApiResponse(responseCode = "400", description = "필수 항목이 빠졌거나 사이트 관리자가 아니거나 같은 이름이 있다(그때는 `status` 409와 이미 있는 프로젝트).", content = [Content(schema = Schema(implementation = ProjectCreateError::class))])
    @PostMapping("/-_-api/v1/owners/{owner}/projects")
    @Transactional
    fun newProject(@PathVariable owner: String, request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val viewer = currentUserProvider.get().user
        if (viewer == null || !accessControl.isSiteManager(viewer)) return requests.message(HttpStatus.BAD_REQUEST, "User creation with api is allowed by Site admin only.")
        val name = json.findValue("projectName")?.asString() ?: return requests.message(HttpStatus.BAD_REQUEST, "projectName is required")
        projectService.findByOwnerAndProjectName(owner, name)?.let { existing ->
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("status" to 409, "reason" to "Conflict", "project" to projectNode(existing)))
        }
        val organization = organizations.findByNameIgnoreCase(owner)
        if (organization != null && !organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(requireNotNull(organization.id), requireNotNull(viewer.id), RoleType.ORG_ADMIN.id)) {
            return requests.message(HttpStatus.FORBIDDEN, "'${viewer.name}' has no permission")
        }
        val members = json.findValue("members")?.takeIf { it.isArray }?.toList().orEmpty().mapNotNull { node ->
            val user = node.findValue("email")?.asString()?.let { userLookup.findByEmail(it) } ?: return@mapNotNull null
            user to node.findValue("role")?.asString().orEmpty()
        }
        val project = externalProjects.create(
            ExternalProject(owner, name, json.findValue("projectDescription")?.asString().orEmpty(), json.findValue("projectVcs")?.asString() ?: "GIT", scope(json), members, createdDate(json)),
            viewer
        )
        return created(projectNode(project))
    }

    /** 요청의 `projectCreatedDate`(`yyyy-MM-dd a hh:mm:ss Z`, 영어)이다. 없거나 형식이 틀리면 `null`이라 지금 시각을 쓴다. */
    private fun createdDate(json: JsonNode): java.util.Date? =
        json.findValue("projectCreatedDate")?.asString()?.let { text -> try { SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH).parse(text) } catch (e: ParseException) { null } }

    private fun scope(json: JsonNode): ProjectScope = when (json.findValue("projectScope")?.asString()) {
        "PUBLIC" -> ProjectScope.PUBLIC
        "PROTECTED" -> ProjectScope.PROTECTED
        else -> ProjectScope.PRIVATE
    }

    private fun projectNode(project: Project) = linkedMapOf("id" to project.id, "owner" to project.owner, "name" to project.name, "overview" to project.overview, "vcs" to project.vcs)

    // --- 라벨 만들기 ---

    /**
     * 라벨 여러 개를 만든다. 본문: `{"labels": [{"labelName", "labelColor", "category", "isExclusive"}]}`. 분류가 없으면 만들고, 새 분류의 단일 선택 여부는 `isExclusive`(없으면 `false`)를 따른다. 이미 있는 분류는 바꾸지 않는다.
     * 항목마다 `{"status": 201, ...}`, 이미 있으면 `{"status": 409, ...}`를 모아 `201`로 답한다.
     */
    @ApiOperation(operationId = "createLabels", tags = [ApiTags.LABEL], summary = "라벨 여러 개 만들기", description = "라벨을 한 번에 여러 개 만든다. 분류가 없으면 `isExclusive`(없으면 `false`)를 단일 선택 여부로 하여 만든다. 이미 있는 분류는 바꾸지 않는다. 항목마다 만들었으면 `status` 201, 이미 있으면 409를 모아 `201`로 답한다. 라벨을 만들 수 있어야 한다.")
    @BodyDoc(description = "만들 라벨", required = true, content = [Content(schema = Schema(implementation = LabelsCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "항목마다 결과", content = [Content(array = ArraySchema(schema = Schema(implementation = LabelCreatedItem::class)))])
    @ApiResponse(responseCode = "400", description = "`labels`가 배열이 아니거나 항목에 필수 값이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/labels")
    @IsCreatable(ResourceType.ISSUE_LABEL)
    @Transactional
    fun newLabel(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val labelsNode = json.findValue("labels")
        if (labelsNode == null || !labelsNode.isArray) return requests.message(HttpStatus.BAD_REQUEST, "No issues key exists or value wasn't array!")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val result = labelsNode.toList().map { node ->
            val name = node.findValue("labelName")?.asString() ?: return requests.message(HttpStatus.BAD_REQUEST, "labelName is required")
            val color = node.findValue("labelColor")?.asString() ?: return requests.message(HttpStatus.BAD_REQUEST, "labelColor is required")
            val categoryName = node.findValue("category")?.asString() ?: return requests.message(HttpStatus.BAD_REQUEST, "category is required")
            val label = labelService.createLabel(project, name, color, categoryName, node.findValue("isExclusive")?.asBoolean(false) ?: false)
            if (label == null) {
                linkedMapOf("status" to 409, "reason" to "Conflict", "message" to messages.getMessage("label.error.duplicated", null, LocaleContextHolder.getLocale()), "user" to node)
            } else {
                linkedMapOf("status" to 201, "label" to label.name, "category" to label.category?.name, "labelColor" to label.color, "isExclusive" to label.category?.isExclusive)
            }
        }
        return created(result)
    }

    // --- 마일스톤 만들기 ---

    /**
     * 마일스톤 여러 개를 만든다. 본문: `{"milestones": [{"title", "description", "due_on", "state"}]}`. 같은 제목이 있으면 만들지 않고
     * `{"milestone": 입력, "message": 안내}`를 넣는다. `due_on`은 그날의 마지막 초로 맞춘다. 모두 `201`로 답한다.
     */
    @ApiOperation(operationId = "createMilestones", tags = [ApiTags.MILESTONE], summary = "마일스톤 여러 개 만들기", description = "마일스톤을 한 번에 여러 개 만든다. 같은 제목이 있으면 만들지 않고 안내를 담는다. `due_on`은 그날의 마지막 초로 맞춘다. 모두 `201`로 답한다.")
    @BodyDoc(description = "만들 마일스톤", required = true, content = [Content(schema = Schema(implementation = MilestonesCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "항목마다 만든 마일스톤이나 안내", content = [Content(array = ArraySchema(schema = Schema(implementation = MilestoneCreatedItem::class)))])
    @ApiResponse(responseCode = "400", description = "`milestones`가 배열이 아니다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/milestones")
    @IsCreatable(ResourceType.MILESTONE)
    @Transactional
    fun newMilestone(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val milestonesNode = json.findValue("milestones")
        if (milestonesNode == null || !milestonesNode.isArray) return requests.message(HttpStatus.BAD_REQUEST, "No milestones key exists or value wasn't array!")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val result = milestonesNode.toList().map { node ->
            val title = node.findValue("title")?.asString() ?: "No title"
            if (milestones.existsByProjectIdAndTitle(requireNotNull(project.id), title)) {
                linkedMapOf("milestone" to node, "message" to messages.getMessage("milestone.title.duplicated", null, LocaleContextHolder.getLocale()))
            } else {
                val state = if (node.findValue("state")?.asString().equals("closed", ignoreCase = true)) State.CLOSED else State.OPEN
                val milestone = milestoneService.create(project, title, node.findValue("description")?.asString().orEmpty(), state, node.findValue("due_on")?.asString()?.let { ApiDueDates.parse(it) })
                milestoneNode(milestone)
            }
        }
        return created(result)
    }

    private fun milestoneNode(m: dev.yona.domain.issue.Milestone): Map<String, Any?> {
        val node = linkedMapOf<String, Any?>("id" to m.id, "title" to m.title, "state" to m.state?.state, "description" to m.contents)
        m.dueDate?.let { node["due_on"] = LocalDateTime.ofInstant(it.toInstant(), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")) }
        return node
    }

    // --- 내보내기 ---

    /** 프로젝트의 구성원, 라벨, 이슈, 글, 마일스톤을 모두 내보낸다. 프로젝트를 지울 수 있는 사용자만 쓸 수 있다. */
    @ApiOperation(operationId = "exportProject", tags = [ApiTags.PROJECT], summary = "프로젝트 내보내기", description = "프로젝트의 구성원, 라벨, 이슈(본문과 댓글 포함), 글, 마일스톤을 모두 내보낸다. 이슈 이전 도구가 쓴다. 프로젝트를 지울 수 있는 사용자만 쓸 수 있다.")
    @ApiResponse(responseCode = "200", description = "프로젝트 전체", content = [Content(schema = Schema(implementation = ProjectExport::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/exports")
    @IsAllowed(Operation.DELETE)
    @Transactional(readOnly = true)
    fun exports(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val projectId = requireNotNull(project.id)
        val members = memberService.members(project)
        val allIssues = issues.findByProjectIdOrderByIdAsc(projectId)
        val allPosts = postings.findByProjectIdOrderByIdAsc(projectId)
        val allMilestones = milestones.findByProjectId(projectId, org.springframework.data.domain.Sort.by("id"))
        val node = linkedMapOf<String, Any?>(
            "owner" to project.owner, "projectName" to project.name, "projectDescription" to project.overview, "projectCreatedDate" to issueJson.longDate(project.createdDate),
            "projectVcs" to project.vcs, "projectScope" to project.projectScope?.name,
            "assignees" to assignees.findByProjectIdOrderByIdAsc(projectId).mapNotNull { it.user }.map { person(it) },
            "authors" to authors(projectId).map { person(it) },
            "memberCount" to members.size,
            "members" to members.mapNotNull { pu -> pu.user?.let { linkedMapOf("loginId" to it.loginId, "name" to it.name, "role" to pu.role?.name, "email" to it.email) } },
            "issueCount" to allIssues.size, "postCount" to allPosts.size, "milestoneCount" to allMilestones.size,
            "labels" to labels.findByProjectIdOrderByCategoryNameAscNameAsc(projectId).sortedBy { it.id }.map {
                linkedMapOf("labelName" to it.name, "labelColor" to it.color, "category" to it.category?.name, "isExclusive" to it.category?.isExclusive)
            },
            "issues" to allIssues.map { issueJson.of(it, request) },
            "posts" to allPosts.map { issueJson.ofPosting(it) },
            "milestones" to allMilestones.map { m ->
                linkedMapOf<String, Any?>("id" to m.id, "title" to m.title, "state" to m.state?.state, "description" to m.contents).also { n -> m.dueDate?.let { n["dueDate"] = issueJson.longDate(it) } }
            }
        )
        return ok(node)
    }

    private fun person(user: User) = linkedMapOf("loginId" to user.loginId, "name" to user.name, "email" to user.email)

    /** 이슈나 글을 쓴 사용자와 이 프로젝트로 풀 리퀘스트를 보낸 사용자이다 (v1.6 `Project.findAuthors`). */
    private fun authors(projectId: Long): List<User> {
        val ids = LinkedHashSet<Long>()
        entityManager.createQuery("select distinct i.authorId from Issue i where i.project.id = :id and i.authorId is not null order by i.authorId", java.lang.Long::class.java).setParameter("id", projectId).resultList.forEach { ids.add(it.toLong()) }
        entityManager.createQuery("select distinct p.authorId from Posting p where p.project.id = :id and p.authorId is not null order by p.authorId", java.lang.Long::class.java).setParameter("id", projectId).resultList.forEach { ids.add(it.toLong()) }
        entityManager.createQuery("select distinct pr.contributor.id from PullRequest pr where pr.toProject.id = :id and pr.contributor is not null order by pr.contributor.id", java.lang.Long::class.java).setParameter("id", projectId).resultList.forEach { ids.add(it.toLong()) }
        return ids.mapNotNull { users.findById(it).orElse(null) }
    }

    // --- 구독자 목록 ---

    /**
     * 이슈나 글의 구독자를 보여 준다(`type=issues` 또는 `posts`). 최대 100명까지 `{"name", "url"}`로 담고 전체 수를 함께 준다.
     * `type`이 없거나 다르면 빈 `200`이다.
     */
    @ApiOperation(operationId = "listWatchers", tags = [ApiTags.BOARD], summary = "구독자 목록", description = "이슈나 글을 지켜보는 사람을 최대 100명까지 돌려준다. `type`이 `issues`나 `posts`가 아니면 본문 없는 빈 `200`이다. 경로의 `number`는 `type`에 따라 이슈 번호나 글 번호이다.")
    @io.swagger.v3.oas.annotations.Parameter(name = "type", description = "구독자를 볼 대상", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "posts", schema = Schema(allowableValues = ["issues", "posts"]))
    @ApiResponse(responseCode = "200", description = "구독자 목록", content = [Content(schema = Schema(implementation = WatchersResponse::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈나 글이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number:[0-9]+}/watchers")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun getWatchers(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @RequestParam(required = false) type: String?): Any {
        if (type != "issues" && type != "posts") return ResponseEntity.ok().build<Void>()
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val watchers = if (type == "issues") {
            receivers.issueWatchers(issues.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return ErrorViews.notFound())
        } else {
            val posting = postings.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return ErrorViews.notFound()
            receivers.actualWatchers(ResourceType.BOARD_POST, requireNotNull(posting.id).toString(), listOfNotNull(posting.authorLoginId?.let { users.findByLoginIdIgnoreCase(it) }))
        }
        val listed = watchers.take(LIMIT).map { linkedMapOf("name" to it.name, "url" to siteUrls.path("/${it.loginId}")) }
        return ok(linkedMapOf("totalWatchers" to watchers.size, "watchersInList" to listed.size, "watchers" to listed))
    }

    private companion object {
        const val LIMIT = 100
    }
}
