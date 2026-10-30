package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationManagementService
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.domain.user.LoginIdRules
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
 * 조직 REST이다(로드맵 X-30a, `decisions.md` D-49). 경로는 GitHub처럼 `/-_-api/v1/orgs/{org}`이고 토큰 전용이다(X-5). 조직의 프로젝트 목록은 이미 있는
 * `GET /-_-api/v1/owners/{owner}/projects`가 맡는다. 웹 화면의 [dev.yona.web.organization.OrganizationMemberController],
 * [dev.yona.web.organization.OrganizationSettingController]와 같은 서비스와 판정을 쓴다.
 *
 * 범위는 읽기 `read:org`, 구성원 변경 `write:org`, 조직 만들기와 설정과 삭제 `admin:org`이다(표는 `ApiScopeRules`). 토큰이 할 수 있는 일은 범위와
 * 사용자의 실제 권한의 교집합이다(D-36 4번). 구성원 목록은 웹 화면처럼 조직 관리자만 본다. 역할 이름은 GitHub처럼 `admin`과 `member`이다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalOrganizationApiController(
    private val organizationService: OrganizationService,
    private val management: OrganizationManagementService,
    private val organizationUsers: OrganizationUserRepository,
    private val users: UserRepository,
    private val requests: ApiRequests,
    private val accessControl: AccessControl,
    private val pullRequestJson: PullRequestJson
) {

    // --- 조회 ---

    /** 내가 속한 조직이다. 결과: `{"result": [{"name", "description", "createdAt", "projectCount", "role"}]}`. */
    @ApiOperation(operationId = "listMyOrganizations", tags = [ApiTags.ORGANIZATION], summary = "내가 속한 조직", description = "토큰 주인이 구성원인 조직과 그 안에서의 역할이다.")
    @ApiResponse(responseCode = "200", description = "조직 목록", content = [Content(schema = Schema(implementation = OrganizationListResult::class))])
    @GetMapping("/-_-api/v1/user/organizations")
    @Transactional(readOnly = true)
    fun mine(request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val rows = organizationUsers.findByUserId(requireNotNull(user.id)).mapNotNull { row -> row.organization?.let { organizationNode(it, user) } }
        return ok(linkedMapOf("result" to rows))
    }

    /**
     * 조직 한 건이다. 누구나 읽을 수 있고 `projectCount`는 토큰 사용자가 볼 수 있는 프로젝트 수이며, `role`은 조직 구성원일 때만 있다(`admin`, `member`).
     * 결과: `{"result": {...}}`.
     */
    @ApiOperation(operationId = "getOrganization", tags = [ApiTags.ORGANIZATION], summary = "조직 한 건 조회", description = "조직 정보이다. 누구나 읽는다. `projectCount`는 토큰 주인이 볼 수 있는 프로젝트 수이고 `role`은 구성원일 때만 있다. 조직의 프로젝트 목록은 `GET /owners/{owner}/projects`로 얻는다.")
    @ApiResponse(responseCode = "200", description = "조직", content = [Content(schema = Schema(implementation = OrganizationResult::class))])
    @ApiResponse(responseCode = "404", description = "조직이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/orgs/{org}")
    @Transactional(readOnly = true)
    fun get(@PathVariable org: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val organization = organizationService.findByName(org) ?: return ErrorViews.notFound()
        return ok(linkedMapOf("result" to organizationNode(organization, user)))
    }

    /** 조직 구성원과 역할이다. 가입한 순서이다. 조직 관리자만 본다(웹 화면과 같다). 결과: `{"result": [{"loginId", "name", "role"}]}`. */
    @ApiOperation(operationId = "listOrganizationMembers", tags = [ApiTags.ORGANIZATION], summary = "조직 구성원 목록", description = "구성원과 역할을 가입한 순서로 돌려준다. 웹 화면처럼 조직 관리자만 본다.")
    @ApiResponse(responseCode = "200", description = "구성원 목록", content = [Content(schema = Schema(implementation = OrganizationMemberListResult::class))])
    @ApiResponse(responseCode = "404", description = "조직이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/orgs/{org}/members")
    @Transactional(readOnly = true)
    fun members(@PathVariable org: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val organization = organizationService.findByName(org) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, organization.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        return ok(linkedMapOf("result" to management.members(organization).mapNotNull { memberNode(it) }))
    }

    // --- 구성원 변경 ---

    /**
     * 구성원을 더한다. 본문 `{"loginId": 로그인 ID, "role": "member"(기본) 또는 "admin"}`이다. 조직 관리자만 하고, 없는 사용자는 404, 게스트는 403,
     * 이미 구성원이면 409, 역할이 틀리면 400이다. 성공하면 201과 구성원 한 건이다.
     */
    @ApiOperation(operationId = "addOrganizationMember", tags = [ApiTags.ORGANIZATION], summary = "조직 구성원 더하기", description = "사용자를 구성원으로 더한다. 조직 관리자만 한다. 없는 사용자는 404, 게스트는 403, 이미 구성원이면 409, 역할이 틀리면 400이다.")
    @BodyDoc(description = "더할 구성원", required = true, content = [Content(schema = Schema(implementation = MemberAddRequest::class))])
    @ApiResponse(responseCode = "201", description = "더한 구성원", content = [Content(schema = Schema(implementation = OrganizationMemberResult::class))])
    @ApiResponse(responseCode = "400", description = "`loginId`가 없거나 역할이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "조직이나 사용자가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "이미 구성원이다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/orgs/{org}/members")
    @Transactional
    fun addMember(@PathVariable org: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val organization = organizationService.findByName(org) ?: return ErrorViews.notFound()
        val loginId = json.get("loginId")?.asString("")?.takeIf { it.isNotBlank() } ?: return requests.message(HttpStatus.BAD_REQUEST, "loginId is required")
        val roleType = if (json.has("role")) roleOf(json.get("role")) ?: return requests.message(HttpStatus.BAD_REQUEST, "role must be admin or member") else RoleType.ORG_MEMBER
        // 권한과 대상 사용자는 서비스의 검사 순서(사용자, 게스트, 조직, 권한, 이미 구성원)를 따른다
        denial(management.addMember(organization, user, loginId))?.let { return it }
        val target = requireNotNull(users.findByLoginId(loginId))
        if (roleType == RoleType.ORG_ADMIN) denial(management.editMember(organization, user, requireNotNull(target.id), RoleType.ORG_ADMIN.id.toString()))?.let { return it }
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to memberNode(organization, target)))
    }

    /** 구성원의 역할을 바꾼다. 본문 `{"role": "admin" 또는 "member"}`이다. 조직 관리자만 하고, 마지막 관리자를 구성원으로 내릴 수는 없다(409). */
    @ApiOperation(operationId = "changeOrganizationMemberRole", tags = [ApiTags.ORGANIZATION], summary = "조직 구성원 역할 바꾸기", description = "구성원의 역할을 바꾼다. 조직 관리자만 한다. 마지막 관리자를 구성원으로 내리면 409이다.")
    @BodyDoc(description = "새 역할", required = true, content = [Content(schema = Schema(implementation = MemberRoleRequest::class))])
    @ApiResponse(responseCode = "200", description = "바뀐 구성원", content = [Content(schema = Schema(implementation = OrganizationMemberResult::class))])
    @ApiResponse(responseCode = "400", description = "역할이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "조직이나 사용자가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "마지막 관리자이다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PatchMapping("/-_-api/v1/orgs/{org}/members/{loginId}")
    @Transactional
    fun editMember(@PathVariable org: String, @PathVariable loginId: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val organization = organizationService.findByName(org) ?: return ErrorViews.notFound()
        val target = users.findByLoginId(loginId) ?: return requests.message(HttpStatus.NOT_FOUND, "no user by request")
        val roleType = roleOf(json.get("role")) ?: return requests.message(HttpStatus.BAD_REQUEST, "role must be admin or member")
        denial(management.editMember(organization, user, requireNotNull(target.id), roleType.id.toString()))?.let { return it }
        return ok(linkedMapOf("result" to memberNode(organization, target)))
    }

    /** 구성원을 뺀다. 본인이면 스스로 나가는 것이다. 조직 관리자나 본인만 하고, 마지막 관리자는 뺄 수 없다(409). 성공하면 204이다. */
    @ApiOperation(operationId = "removeOrganizationMember", tags = [ApiTags.ORGANIZATION], summary = "조직 구성원 빼기", description = "구성원을 뺀다. 본인이면 스스로 나가는 것이다. 조직 관리자나 본인만 하고 마지막 관리자는 뺄 수 없다(409).")
    @ApiResponse(responseCode = "204", description = "뺐다.")
    @ApiResponse(responseCode = "404", description = "조직이나 사용자나 구성원이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "마지막 관리자이다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("/-_-api/v1/orgs/{org}/members/{loginId}")
    @Transactional
    fun removeMember(@PathVariable org: String, @PathVariable loginId: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val organization = organizationService.findByName(org) ?: return ErrorViews.notFound()
        val target = users.findByLoginId(loginId) ?: return requests.message(HttpStatus.NOT_FOUND, "no user by request")
        denial(management.removeMember(organization, user, requireNotNull(target.id)))?.let { return it }
        return ResponseEntity.noContent().build<Void>()
    }

    // --- 만들기, 설정, 삭제 ---

    /**
     * 조직을 만들고 만든 사람을 조직 관리자로 둔다. 본문 `{"name", "description"}`이다. 이름은 로그인 ID와 같은 규칙이고(형식과 예약어는 400,
     * 사용자나 다른 조직과 겹치면 409), 게스트는 만들 수 없다(403). 성공하면 201이다.
     */
    @ApiOperation(operationId = "createOrganization", tags = [ApiTags.ORGANIZATION], summary = "조직 만들기", description = "조직을 만들고 만든 사람을 조직 관리자로 둔다. 이름은 로그인 ID와 같은 규칙이다(형식과 예약어는 400, 사용자나 다른 조직과 겹치면 409). 게스트는 만들 수 없다.")
    @BodyDoc(description = "만들 조직", required = true, content = [Content(schema = Schema(implementation = OrganizationCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "만든 조직", content = [Content(schema = Schema(implementation = OrganizationResult::class))])
    @ApiResponse(responseCode = "400", description = "이름의 형식이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "이름이 이미 쓰인다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/orgs")
    @Transactional
    fun create(request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        if (user.isGuest == true) return requests.message(HttpStatus.FORBIDDEN, "guests cannot create organizations")
        val name = json.get("name")?.asString("")
        organizationService.validateNewName(name)?.let { error ->
            return if (error == OrganizationService.NameError.DUPLICATE) requests.message(HttpStatus.CONFLICT, "the name is already used") else requests.message(HttpStatus.BAD_REQUEST, "invalid organization name")
        }
        val descr = if (json.has("description")) json.get("description").let { if (it.isNull) null else it.asString("") } else null
        val organization = organizationService.create(requireNotNull(name), descr, user)
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to organizationNode(organization, user)))
    }

    /**
     * 이름이나 설명을 바꾼다. 본문의 `name`, `description` 중 보낸 항목만 바꾼다(로고는 웹 화면에서만). 이름이 바뀌면 조직의 모든 프로젝트 소유자가 바뀌고
     * 저장소도 옮겨진다. 이름의 형식이 틀리면 400, 사용자나 다른 조직과 겹치면 409, 조직 관리자가 아니면 403이다. 결과: `{"result": {...}}`.
     */
    @ApiOperation(operationId = "updateOrganization", tags = [ApiTags.ORGANIZATION], summary = "조직 설정 바꾸기", description = "이름이나 설명 중 보낸 항목만 바꾼다. 이름이 바뀌면 조직의 모든 프로젝트 소유자가 바뀌고 저장소도 옮겨진다. 조직 관리자만 한다.")
    @BodyDoc(description = "바꿀 항목", required = true, content = [Content(schema = Schema(implementation = OrganizationPatchRequest::class))])
    @ApiResponse(responseCode = "200", description = "바뀐 조직", content = [Content(schema = Schema(implementation = OrganizationResult::class))])
    @ApiResponse(responseCode = "400", description = "보낸 항목이 없거나 이름의 형식이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "조직이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "이름이 이미 쓰인다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PatchMapping("/-_-api/v1/orgs/{org}")
    @Transactional
    fun update(@PathVariable org: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val organization = organizationService.findByName(org) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, organization.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (!json.has("name") && !json.has("description")) return requests.message(HttpStatus.BAD_REQUEST, "no updatable field: name, description")
        val name = if (json.has("name")) json.get("name").asString("").takeIf { it.isNotEmpty() && LoginIdRules.isValidFormat(it) && !LoginIdRules.isReserved(it) }
            ?: return requests.message(HttpStatus.BAD_REQUEST, "invalid organization name") else organization.name.orEmpty()
        if (management.isDuplicateName(organization, name)) return requests.message(HttpStatus.CONFLICT, "the name is already used")
        val descr = if (json.has("description")) json.get("description").let { if (it.isNull) null else it.asString("") } else organization.descr
        management.update(organization, name, descr, null, null, user.loginId)
        return ok(linkedMapOf("result" to organizationNode(organization, user)))
    }

    /** 조직을 지운다. 조직 관리자만 하고(403) 프로젝트가 남아 있으면 지울 수 없다(409). 성공하면 204이다. */
    @ApiOperation(operationId = "deleteOrganization", tags = [ApiTags.ORGANIZATION], summary = "조직 지우기", description = "조직을 지운다. 조직 관리자만 하고 프로젝트가 남아 있으면 지울 수 없다(409).")
    @ApiResponse(responseCode = "204", description = "지웠다.")
    @ApiResponse(responseCode = "404", description = "조직이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "프로젝트가 남아 있다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("/-_-api/v1/orgs/{org}")
    @Transactional
    fun delete(@PathVariable org: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val organization = organizationService.findByName(org) ?: return ErrorViews.notFound()
        management.checkDeletable(organization, user)?.let {
            return if (it == OrganizationManagementService.DeleteDenied.HAS_PROJECTS) requests.message(HttpStatus.CONFLICT, "the organization still has projects") else requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        }
        management.delete(organization)
        return ResponseEntity.noContent().build<Void>()
    }

    // --- 도우미 ---

    /** 서비스가 돌려주는 거부 이유(메시지 키)를 HTTP 응답으로 바꾼다. 성공(`null`)이면 `null`이다. */
    private fun denial(key: String?): ResponseEntity<*>? = when (key) {
        null -> null
        "organization.member.unknownUser", "organization.member.unknownOrganization", "organization.member.isNotAMember" -> requests.message(HttpStatus.NOT_FOUND, "not found")
        "error.forbidden.to.guest.user", "organization.member.needManagerRole" -> requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        "organization.member.alreadyMember", "organization.member.atLeastOneAdmin" -> requests.message(HttpStatus.CONFLICT, if (key.endsWith("alreadyMember")) "already a member" else "an organization needs at least one admin")
        else -> requests.message(HttpStatus.BAD_REQUEST, "invalid request")
    }

    /** 본문의 `role`을 읽는다. `admin`이나 `member`만 받고 그 밖은 `null`이다. */
    private fun roleOf(node: JsonNode?): RoleType? = when (node?.asString("")) {
        "admin" -> RoleType.ORG_ADMIN
        "member" -> RoleType.ORG_MEMBER
        else -> null
    }

    private fun roleName(roleId: Long?): String? = when (roleId) {
        RoleType.ORG_ADMIN.id -> "admin"
        RoleType.ORG_MEMBER.id -> "member"
        else -> null
    }

    private fun organizationNode(organization: Organization, viewer: User): Map<String, Any?> {
        val node = linkedMapOf<String, Any?>(
            "name" to organization.name, "description" to organization.descr, "createdAt" to pullRequestJson.iso(organization.created),
            "projectCount" to organizationService.visibleProjects(organization, viewer).size
        )
        organizationUsers.findFirstByOrganizationIdAndUserId(requireNotNull(organization.id), requireNotNull(viewer.id))?.let { node["role"] = roleName(it.role?.id) }
        return node
    }

    private fun memberNode(row: OrganizationUser): Map<String, Any?>? =
        row.user?.let { linkedMapOf("loginId" to it.loginId, "name" to it.name, "role" to roleName(row.role?.id)) }

    private fun memberNode(organization: Organization, user: User): Map<String, Any?> =
        linkedMapOf("loginId" to user.loginId, "name" to user.name, "role" to roleName(organizationUsers.findFirstByOrganizationIdAndUserId(requireNotNull(organization.id), requireNotNull(user.id))?.role?.id))

    private fun ok(body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)
}
