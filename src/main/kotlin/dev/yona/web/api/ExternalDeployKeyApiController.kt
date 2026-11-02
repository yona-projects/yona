package dev.yona.web.api

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.sshkey.DeployKey
import dev.yona.domain.sshkey.DeployKeyAddition
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.repository.RepositoryService
import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
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
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * 배포 키 REST이다(추가 개발 항목 X-3, `decisions.md` D-66). GitHub의 `/repos/{owner}/{repo}/keys`에 해당하며 프로젝트의 배포 키 목록, 한 건 조회, 등록, 삭제가 있다.
 * 범위는 읽기 `read:deploy_key`, 등록 `write:deploy_key`, 삭제 `admin:deploy_key`이고 앞의 범위는 뒤의 범위가 포함한다. 토큰이 할 수 있는 일은 범위와 사용자의 실제 권한의
 * 교집합이므로 토큰 주인이 프로젝트 관리자(설정을 고칠 수 있는 사용자)여야 한다. Git 프로젝트에서만 쓴다. 웹의 [dev.yona.web.project.DeployKeyController]와 같은 서비스를 쓴다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalDeployKeyApiController(
    private val projectService: ProjectService,
    private val keys: DeployKeyService,
    private val accessControl: AccessControl,
    private val requests: ApiRequests,
    private val json: PullRequestJson,
    private val users: dev.yona.domain.user.UserRepository
) {

    @ApiOperation(operationId = "listDeployKeys", tags = [ApiTags.PROJECT], summary = "프로젝트의 배포 키 목록", description = "프로젝트의 배포 키를 최근에 등록한 순서로 돌려준다. 프로젝트 관리자만 읽는다. Git 프로젝트가 아니면 400이다. 필요한 범위는 `read:deploy_key`이다.")
    @ApiResponse(responseCode = "200", description = "키 목록", content = [Content(schema = Schema(implementation = DeployKeyList::class))])
    @ApiResponse(responseCode = "400", description = "Git 프로젝트가 아니다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "401", description = "토큰이 없거나 올바르지 않다.")
    @ApiResponse(responseCode = "403", description = "범위가 모자라거나 프로젝트 관리자가 아니다.")
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/deploy_keys")
    @Transactional(readOnly = true)
    fun list(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val project = authorize(owner, projectName, request) { return it }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("keys" to keys.list(project).map(::node)))
    }

    @ApiOperation(operationId = "getDeployKey", tags = [ApiTags.PROJECT], summary = "배포 키 한 건", description = "프로젝트의 배포 키 한 개를 돌려준다. 없거나 다른 프로젝트의 키이면 404이다. 필요한 범위는 `read:deploy_key`이다.")
    @ApiResponse(responseCode = "200", description = "키", content = [Content(schema = Schema(implementation = DeployKeyResult::class))])
    @ApiResponse(responseCode = "401", description = "토큰이 없거나 올바르지 않다.")
    @ApiResponse(responseCode = "403", description = "범위가 모자라거나 프로젝트 관리자가 아니다.")
    @ApiResponse(responseCode = "404", description = "프로젝트나 키가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/deploy_keys/{id}")
    @Transactional(readOnly = true)
    fun get(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable id: Long, request: HttpServletRequest): Any {
        val project = authorize(owner, projectName, request) { return it }
        val key = keys.find(project, id) ?: return requests.message(HttpStatus.NOT_FOUND, "no deploy key by request")
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to node(key)))
    }

    @ApiOperation(operationId = "createDeployKey", tags = [ApiTags.PROJECT], summary = "배포 키 등록", description = "본문 `{\"name\", \"publicKey\"(필수), \"readOnly\"}`로 배포 키를 등록한다. `readOnly`를 생략하면 읽기 전용이다. 받을 수 없는 키는 400이고 이미 등록된 키(지문이 같은 키, 사용자 키 포함)는 409이다. 저장소당 50개까지이다. 필요한 범위는 `write:deploy_key`이다.")
    @BodyDoc(description = "등록할 키", required = true, content = [Content(schema = Schema(implementation = DeployKeyCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "등록한 키", content = [Content(schema = Schema(implementation = DeployKeyResult::class))])
    @ApiResponse(responseCode = "400", description = "받을 수 없는 키이거나 본문이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "401", description = "토큰이 없거나 올바르지 않다.")
    @ApiResponse(responseCode = "403", description = "범위가 모자라거나 프로젝트 관리자가 아니다.")
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "이미 등록된 키이다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/deploy_keys")
    @Transactional
    fun create(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val project = authorize(owner, projectName, request) { return it }
        val body = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val publicKey = body.get("publicKey")?.takeIf { it.isString }?.asString("")?.takeIf { it.isNotBlank() } ?: return requests.message(HttpStatus.BAD_REQUEST, "publicKey is required")
        val name = body.get("name")?.takeIf { !it.isNull }?.let { if (it.isString) it.asString("") else return requests.message(HttpStatus.BAD_REQUEST, "name must be a string") }
        val readOnly = body.get("readOnly")?.takeIf { !it.isNull }?.let { if (it.isBoolean) it.asBoolean() else return requests.message(HttpStatus.BAD_REQUEST, "readOnly must be a boolean") } ?: true
        val caller = request.getAttribute(CALLER) as? dev.yona.domain.user.User
        return when (val result = keys.add(project, name, publicKey, writable = !readOnly, createdBy = caller)) {
            is DeployKeyAddition.Added -> ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to node(result.key)))
            is DeployKeyAddition.Rejected ->
                if (result.code == "deployKey.error.duplicate") requests.message(HttpStatus.CONFLICT, "the ssh key is already registered")
                else requests.message(HttpStatus.BAD_REQUEST, result.code.removePrefix("deployKey.error.").replace('_', ' '))
        }
    }

    @ApiOperation(operationId = "deleteDeployKey", tags = [ApiTags.PROJECT], summary = "배포 키 삭제", description = "프로젝트의 배포 키를 지운다. 이 키를 쓰는 서버는 저장소에 접근할 수 없게 된다. 없거나 다른 프로젝트의 키이면 404이다. 필요한 범위는 `admin:deploy_key`이다.")
    @ApiResponse(responseCode = "204", description = "지웠다.")
    @ApiResponse(responseCode = "401", description = "토큰이 없거나 올바르지 않다.")
    @ApiResponse(responseCode = "403", description = "범위가 모자라거나 프로젝트 관리자가 아니다.")
    @ApiResponse(responseCode = "404", description = "프로젝트나 키가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/deploy_keys/{id}")
    @Transactional
    fun delete(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable id: Long, request: HttpServletRequest): Any {
        val project = authorize(owner, projectName, request) { return it }
        if (!keys.delete(project, id)) return requests.message(HttpStatus.NOT_FOUND, "no deploy key by request")
        return ResponseEntity.noContent().build<Void>()
    }

    /** 토큰 주인과 프로젝트를 확인한다. 토큰이 없으면 401, 프로젝트가 없으면 404, 설정을 고칠 수 없으면 403, Git 프로젝트가 아니면 400이다. */
    private inline fun authorize(owner: String, projectName: String, request: HttpServletRequest, reject: (Any) -> Nothing): Project {
        val user = requests.tokenUser(request) ?: reject(requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request"))
        request.setAttribute(CALLER, user)
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: reject(requests.message(HttpStatus.NOT_FOUND, "not found"))
        if (!accessControl.isAllowed(user, project.asResource(), Operation.UPDATE)) reject(requests.message(HttpStatus.FORBIDDEN, "Forbidden request"))
        if (project.vcs != RepositoryService.VCS_GIT) reject(requests.message(HttpStatus.BAD_REQUEST, "deploy keys are only for Git projects"))
        return project
    }

    private fun node(key: DeployKey): Map<String, Any?> = linkedMapOf(
        "id" to key.id, "name" to key.name, "keyType" to key.keyType, "fingerprint" to key.fingerprint, "publicKey" to key.publicKey, "readOnly" to (key.readOnly != false),
        "createdBy" to key.createdById?.let { users.findById(it).orElse(null)?.loginId }, "createdAt" to json.iso(key.createdDate)
    )

    private companion object {
        const val CALLER = "dev.yona.web.api.ExternalDeployKeyApiController.caller"
    }
}
