package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.ProjectNameRules
import dev.yona.domain.project.ProjectService
import dev.yona.domain.pullrequest.ForkService
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * 프로젝트를 포크하는 REST이다(로드맵 X-30d, `decisions.md` D-49). 경로는 GitHub처럼 `POST .../projects/{projectName}/forks`이고 토큰 전용이다(X-5).
 * 웹의 포크([dev.yona.web.pullrequest.ForkController])와 같은 서비스([ForkService])와 규칙을 쓴다: Git 프로젝트만 포크하고, 같은 소유자에게 같은 이름(대소문자 무시)의
 * 프로젝트가 있으면 거절하며, 조직에는 그 조직의 관리자만 만들 수 있고 "그룹 내 공개"(`PROTECTED`)는 조직에서만 쓴다.
 *
 * 범위는 `repo`이거나 공개 프로젝트의 `public_repo`이다(표는 `ApiScopeRules`). 포크는 저장소를 복제하며 실패하면 프로젝트를 남기지 않아야 하므로,
 * 이 처리기는 [ForkService.fork]가 트랜잭션을 맡도록 자체 트랜잭션을 두지 않는다(웹의 `ForkController.doClone`과 같다).
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalForkApiController(
    private val projectService: ProjectService,
    private val forks: ForkService,
    private val projectJson: ProjectJson,
    private val requests: ApiRequests,
    private val accessControl: AccessControl
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 포크를 만든다. 본문의 항목은 모두 선택이다. `owner`는 포크가 놓일 소유자로 내 로그인 ID(기본)이거나 내가 관리하는 조직의 이름이다(다른 값은 403).
     * `name`은 프로젝트 이름 규칙을 지켜야 하고(400) 없으면 소유자에게 비어 있는 이름(원본 이름, 있으면 `이름-1`)이며, 같은 이름이 있으면 409이다. `projectScope`는
     * `PUBLIC`, `PROTECTED`(조직만, 아니면 400), `PRIVATE`이고 없으면 원본을 따르되 `PROTECTED`는 소유자가 조직이 아니면 `PRIVATE`이다. 성공하면 201과 만든 프로젝트이다.
     */
    @ApiOperation(operationId = "createFork", tags = [ApiTags.FORK], summary = "프로젝트 포크하기", description = "프로젝트를 사용자나 조직으로 포크한다. Git 프로젝트만 되고, 포크할 권한이 있어야 한다. 같은 소유자에게 같은 이름(대소문자 무시)이 있으면 409, 내가 관리하지 않는 곳에 만들려 하면 403, `PROTECTED`를 조직이 아닌 곳에 쓰면 400이다. 본문은 모두 선택이다.")
    @BodyDoc(description = "포크 설정", required = false, content = [Content(schema = Schema(implementation = ForkRequest::class))])
    @ApiResponse(responseCode = "201", description = "만든 포크 프로젝트", content = [Content(schema = Schema(implementation = ProjectResult::class))])
    @ApiResponse(responseCode = "400", description = "Git 프로젝트가 아니거나 이름, 공개 범위가 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "같은 이름의 프로젝트가 있다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "500", description = "저장소 복제에 실패했다. 프로젝트는 남지 않는다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/forks")
    fun fork(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val original = projectService.findByOwnerAndProjectName(owner, projectName) ?: return requests.message(HttpStatus.NOT_FOUND, "not found")
        if (!accessControl.isProjectResourceCreatable(user, original, ResourceType.FORK)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (original.vcs != "GIT") return requests.message(HttpStatus.BAD_REQUEST, "only Git projects can be forked")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")

        val ownerNode = json.get("owner")?.takeIf { !it.isNull }
        if (ownerNode != null && !ownerNode.isString) return requests.message(HttpStatus.BAD_REQUEST, "owner must be a login id or an organization name")
        val wanted = ownerNode?.asString("")
        val destination = when {
            wanted == null || wanted.equals(user.loginId, ignoreCase = true) -> requireNotNull(user.loginId)
            else -> forks.adminOrganizationNames(user).firstOrNull { it.equals(wanted, ignoreCase = true) } ?: return requests.message(HttpStatus.FORBIDDEN, "you cannot fork into $wanted")
        }
        val organization = forks.isOrganization(destination)

        val nameNode = json.get("name")?.takeIf { !it.isNull }
        if (nameNode != null && !nameNode.isString) return requests.message(HttpStatus.BAD_REQUEST, "name must be a string")
        val name = if (nameNode == null) forks.suggestName(original, destination) else nameNode.asString("")
        if (ProjectNameRules.violations(name).isNotEmpty()) return requests.message(HttpStatus.BAD_REQUEST, "invalid project name")
        if (forks.nameTaken(destination, name)) return requests.message(HttpStatus.CONFLICT, "a project with the same name exists")

        val scopeNode = json.get("projectScope")?.takeIf { !it.isNull }
        val scope = if (scopeNode == null) {
            if (original.projectScope == ProjectScope.PROTECTED && !organization) ProjectScope.PRIVATE else original.projectScope ?: ProjectScope.PUBLIC
        } else {
            val requested = ProjectScope.entries.firstOrNull { it.name == scopeNode.asString("") } ?: return requests.message(HttpStatus.BAD_REQUEST, "projectScope must be PUBLIC, PROTECTED or PRIVATE")
            if (requested == ProjectScope.PROTECTED && !organization) return requests.message(HttpStatus.BAD_REQUEST, "PROTECTED is only for organizations")
            requested
        }

        return try {
            val fork = forks.fork(original, destination, name, scope, user)
            ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to projectJson.project(fork)))
        } catch (e: Exception) {
            log.error("Failed to fork \"{}/{}\"", original.owner, original.name, e)
            requests.message(HttpStatus.INTERNAL_SERVER_ERROR, "failed to fork the project")
        }
    }
}
