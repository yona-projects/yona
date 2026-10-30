package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMemberService
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.JsonNode
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * 프로젝트 구성원을 더하고 역할을 바꾸고 빼는 REST이다(로드맵 X-37b, `decisions.md` D-52). 조회(`GET .../members`)는 X-19에 있다. 웹의
 * [dev.yona.web.project.ProjectMemberController]와 같은 서비스([ProjectMemberService])와 판정이다: 프로젝트를 고칠 수 있는 사용자(프로젝트 관리자, 조직 관리자,
 * 사이트 관리자)가 더하고 바꾸고, 본인은 스스로 나갈 수 있으며, 소유자는 나가거나 멤버가 될 수 없다. 역할 이름은 `manager`와 `member`이다.
 * 범위는 `repo`나 공개 프로젝트의 `public_repo`이다(표는 `ApiScopeRules`).
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalProjectMemberApiController(
    private val projectService: ProjectService,
    private val memberService: ProjectMemberService,
    private val projectUsers: ProjectUserRepository,
    private val users: UserRepository,
    private val requests: ApiRequests,
    private val accessControl: AccessControl
) {

    /**
     * 구성원을 더한다. 본문 `{"loginId"(필수), "role": "member"(기본) 또는 "manager"}`이다. 없는 사용자는 404, 이미 구성원이면 409, 역할이 틀리면 400이다.
     * 더한 사용자는 프로젝트를 지켜보게 된다(웹과 같다). 성공하면 201과 구성원 한 명이다.
     */
    @ApiOperation(operationId = "addProjectMember", tags = [ApiTags.PROJECT], summary = "프로젝트 구성원 더하기", description = "사용자를 프로젝트 구성원으로 더한다(기본 `member`, `manager`로도 가능). 더한 사용자는 프로젝트를 지켜보게 된다. 프로젝트를 고칠 수 있는 사용자(프로젝트 관리자, 조직 관리자, 사이트 관리자)만 한다. 없는 사용자는 404, 이미 구성원이면 409, 역할이 틀리면 400이다.")
    @BodyDoc(description = "더할 구성원", required = true, content = [Content(schema = Schema(implementation = ProjectMemberAddRequest::class))])
    @ApiResponse(responseCode = "201", description = "더한 구성원", content = [Content(schema = Schema(implementation = ProjectMemberResult::class))])
    @ApiResponse(responseCode = "400", description = "`loginId`가 없거나 역할이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 사용자가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "이미 구성원이다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$PROJECT/members")
    @Transactional
    fun add(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return requests.message(HttpStatus.NOT_FOUND, "not found")
        if (!accessControl.isAllowed(user, project.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val loginId = json.get("loginId")?.takeIf { it.isString }?.asString("")?.takeIf { it.isNotBlank() } ?: return requests.message(HttpStatus.BAD_REQUEST, "loginId is required")
        val role = if (json.has("role")) roleOf(json.get("role")) ?: return requests.message(HttpStatus.BAD_REQUEST, "role must be manager or member") else RoleType.MEMBER
        val result = memberService.addMember(project, user, loginId)
        result.failure?.let { failure ->
            return if (failure == ProjectMemberService.AddFailure.ALREADY_MEMBER) requests.message(HttpStatus.CONFLICT, "already a member") else requests.message(HttpStatus.NOT_FOUND, "no user by request")
        }
        val member = requireNotNull(result.newMember)
        if (role == RoleType.MANAGER) memberService.changeRole(project, requireNotNull(member.id), RoleType.MANAGER.id)
        memberService.watch(member, project)
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to memberNode(project, member)))
    }

    /**
     * 구성원의 역할을 바꾼다. 본문 `{"role": "manager" 또는 "member"}`이다. 구성원이 아니면 404이고(웹은 새로 만들지만 여기서는 [add]를 쓴다), 소유자를 멤버로 내리면
     * 400이다. 프로젝트의 마지막 관리자를 내리는 것은 v1.6처럼 막지 않는다(조직 관리자와 사이트 관리자는 계속 관리한다). 결과: `{"result": {...}}`.
     */
    @ApiOperation(operationId = "changeProjectMemberRole", tags = [ApiTags.PROJECT], summary = "프로젝트 구성원 역할 바꾸기", description = "구성원의 역할을 `manager`와 `member` 사이에서 바꾼다. 구성원이 아니면 404이고(더하려면 `POST .../members`) 소유자를 `member`로 내리면 400이다. 마지막 관리자를 내리는 것은 v1.6처럼 막지 않는다.")
    @BodyDoc(description = "새 역할", required = true, content = [Content(schema = Schema(implementation = ProjectMemberRoleRequest::class))])
    @ApiResponse(responseCode = "200", description = "바뀐 구성원", content = [Content(schema = Schema(implementation = ProjectMemberResult::class))])
    @ApiResponse(responseCode = "400", description = "역할이 틀렸거나 소유자를 내리려 했다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 구성원이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PatchMapping("$PROJECT/members/{loginId}")
    @Transactional
    fun changeRole(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable loginId: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return requests.message(HttpStatus.NOT_FOUND, "not found")
        if (!accessControl.isAllowed(user, project.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val target = memberOf(project, loginId) ?: return requests.message(HttpStatus.NOT_FOUND, "no member by request")
        val role = roleOf(json.get("role")) ?: return requests.message(HttpStatus.BAD_REQUEST, "role must be manager or member")
        if (project.isOwner(target) && role != RoleType.MANAGER) return requests.message(HttpStatus.BAD_REQUEST, "the owner must be a manager")
        memberService.changeRole(project, requireNotNull(target.id), role.id)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to memberNode(project, target)))
    }

    /** 구성원을 뺀다. 프로젝트를 고칠 수 있는 사용자나 본인(스스로 나가기)만 한다. 소유자는 나갈 수도 내보낼 수도 없다(403). 구성원이 아니면 404이다. 성공하면 204이다. */
    @ApiOperation(operationId = "removeProjectMember", tags = [ApiTags.PROJECT], summary = "프로젝트 구성원 빼기", description = "구성원을 뺀다. 프로젝트를 고칠 수 있는 사용자나 본인(스스로 나가기)만 한다. 소유자는 나가거나 내보낼 수 없다(403). 구성원이 아니면 404이다.")
    @ApiResponse(responseCode = "204", description = "뺐다.")
    @ApiResponse(responseCode = "404", description = "프로젝트나 구성원이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("$PROJECT/members/{loginId}")
    @Transactional
    fun remove(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable loginId: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return requests.message(HttpStatus.NOT_FOUND, "not found")
        val target = memberOf(project, loginId) ?: return requests.message(HttpStatus.NOT_FOUND, "no member by request")
        if (user.id != target.id && !accessControl.isAllowed(user, project.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (project.isOwner(target)) return requests.message(HttpStatus.FORBIDDEN, "the owner cannot leave the project")
        memberService.removeMember(project, requireNotNull(target.id))
        return ResponseEntity.noContent().build<Void>()
    }

    /** 이 프로젝트의 구성원인 사용자이다(사이트 관리자 역할 행은 구성원으로 세지 않는다). 아니면 `null`이다. */
    private fun memberOf(project: Project, loginId: String): User? {
        val target = users.findByLoginId(loginId) ?: return null
        return target.takeIf { projectUsers.findFirstByUserIdAndProjectIdAndRoleIdNot(requireNotNull(it.id), requireNotNull(project.id), RoleType.SITEMANAGER.id) != null }
    }

    private fun roleOf(node: JsonNode?): RoleType? = when (node?.asString("")) {
        "manager" -> RoleType.MANAGER
        "member" -> RoleType.MEMBER
        else -> null
    }

    private fun memberNode(project: Project, user: User): Map<String, Any?> {
        val role = projectUsers.findFirstByUserIdAndProjectIdAndRoleIdNot(requireNotNull(user.id), requireNotNull(project.id), RoleType.SITEMANAGER.id)?.role?.name
        return linkedMapOf("loginId" to user.loginId, "name" to user.name, "role" to role)
    }

    private companion object {
        const val PROJECT = "/-_-api/v1/owners/{owner}/projects/{projectName}"
    }
}
