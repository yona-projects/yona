package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectCreateDenied
import dev.yona.domain.project.ProjectCreateService
import dev.yona.domain.project.ProjectDeletionService
import dev.yona.domain.project.ProjectForm
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectSettingForm
import dev.yona.domain.project.ProjectSettingService
import dev.yona.domain.user.User
import dev.yona.repository.RepositoryService
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
 * 프로젝트를 만들고 설정을 바꾸고 지우는 REST이다(로드맵 X-37a, `decisions.md` D-52). 일반 사용자도 토큰으로 프로젝트를 만든다. 경로는 GitHub처럼
 * `POST /user/projects`(내 계정), `POST /orgs/{org}/projects`(조직 관리자)이고 설정과 삭제는 프로젝트 경로(`PATCH`, `DELETE`)이다. v1.6에서 옮긴
 * `POST /owners/{owner}/projects`(사이트 관리자 전용, 이슈 이전용)는 그대로 둔다. 웹의 [dev.yona.web.project.ProjectCreateController],
 * [dev.yona.web.project.ProjectSettingController], [dev.yona.web.project.ProjectDeleteController]와 같은 서비스와 판정을 쓴다.
 *
 * 범위는 만들기와 지우기가 `repo`(공개 범위를 가리지 않는 일이라 `public_repo`로는 안 된다), 설정 바꾸기가 `repo`나 공개 프로젝트의 `public_repo`이다
 * (표는 `ApiScopeRules`). 토큰이 할 수 있는 일은 범위와 사용자의 실제 권한의 교집합이다(D-36 4번).
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalProjectManageApiController(
    private val projectService: ProjectService,
    private val createService: ProjectCreateService,
    private val settingService: ProjectSettingService,
    private val deletion: ProjectDeletionService,
    private val repositories: RepositoryService,
    private val favorites: FavoriteProjectRepository,
    private val menuSettings: ProjectMenuSettingRepository,
    private val organizationService: OrganizationService,
    private val projectJson: ProjectJson,
    private val requests: ApiRequests,
    private val accessControl: AccessControl,
    private val properties: YonaProperties
) {

    // --- 만들기 ---

    /**
     * 내 계정에 프로젝트를 만든다. 본문 `{"name"(필수), "description", "scope", "vcs", "menus"}`이다. `scope`는 `PUBLIC`, `PROTECTED`, `PRIVATE`이고 없으면 사이트 설정의
     * 기본값, `vcs`는 `GIT`(기본)이나 `Subversion`, `menus`는 `{"code", "issue", "pullRequest", "review", "milestone", "board": 불리언}` 중 보낸 항목만 바꾼
     * 기본 메뉴이다. 이름 규칙 위반은 400, 같은 이름(대소문자 무시)이 있으면 409이다. 만든 사람이 프로젝트 관리자이다. 성공하면 201과 만든 프로젝트이다.
     */
    @ApiOperation(operationId = "createMyProject", tags = [ApiTags.PROJECT], summary = "내 계정에 프로젝트 만들기", description = "로그인한 토큰 주인의 계정에 프로젝트를 만들고 만든 사람을 프로젝트 관리자로 둔다. 공개 범위와 저장소 종류와 메뉴는 생략하면 사이트 설정의 기본값이다. 이름 규칙을 어기면 400이고 같은 이름이 있으면 409이다. 공개 범위를 가리지 않는 일이라 `repo` 범위가 필요하다(`public_repo`로는 안 된다). 사이트 관리자가 다른 사람의 이슈를 옮기려고 쓰는 `POST /owners/{owner}/projects`(v1.6 호환)와 다르다.")
    @BodyDoc(description = "만들 프로젝트", required = true, content = [Content(schema = Schema(implementation = ProjectManageCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "만든 프로젝트", content = [Content(schema = Schema(implementation = ProjectResult::class))])
    @ApiResponse(responseCode = "400", description = "이름이나 값이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "같은 이름의 프로젝트가 있다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/user/projects")
    @Transactional
    fun createForMe(request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        return create(user, requireNotNull(user.loginId), request)
    }

    /** 조직에 프로젝트를 만든다. 조직 관리자만 한다(403). 본문은 [createForMe]와 같다. 없는 조직은 404이다. */
    @ApiOperation(operationId = "createOrganizationProject", tags = [ApiTags.PROJECT], summary = "조직에 프로젝트 만들기", description = "조직의 프로젝트를 만든다. 조직 관리자만 한다. 본문과 규칙은 내 계정에 만들기와 같다.")
    @BodyDoc(description = "만들 프로젝트", required = true, content = [Content(schema = Schema(implementation = ProjectManageCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "만든 프로젝트", content = [Content(schema = Schema(implementation = ProjectResult::class))])
    @ApiResponse(responseCode = "400", description = "이름이나 값이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "조직이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "같은 이름의 프로젝트가 있다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/orgs/{org}/projects")
    @Transactional
    fun createForOrganization(@PathVariable org: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val organization = organizationService.findByName(org) ?: return requests.message(HttpStatus.NOT_FOUND, "not found")
        return create(user, requireNotNull(organization.name), request)
    }

    private fun create(user: User, owner: String, request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        if (createService.checkPermission(user, owner) != null) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val name = json.get("name")?.takeIf { it.isString }?.asString("") ?: return requests.message(HttpStatus.BAD_REQUEST, "name is required")
        val overview = optionalString(json, "description") { return it }
        val scope = scopeOf(json) { return it } ?: ProjectScope.entries.firstOrNull { it.name.equals(properties.projectDefaultScope, ignoreCase = true) } ?: ProjectScope.PUBLIC
        val vcs = optionalString(json, "vcs") { return it }?.also { if (it != RepositoryService.VCS_GIT && it != RepositoryService.VCS_SUBVERSION) return requests.message(HttpStatus.BAD_REQUEST, "vcs must be GIT or Subversion") }
            ?: RepositoryService.VCS_GIT
        val defaults = properties.projectDefaultMenus.replace(" ", "").split(",").filter { it in ProjectForm.MENU_NAMES }.toSet()
        val menus = menusOf(json, defaults) { return it }
        val form = ProjectForm(owner = owner, name = name, overview = overview, projectScope = scope.name, vcs = vcs, menus = menus)
        createService.validate(form, user).let { errors ->
            if (errors.hasErrors()) return invalid(errors.fields())
        }
        val project = createService.create(form, user)
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to projectJson.project(project)))
    }

    // --- 설정 바꾸기 ---

    /**
     * 프로젝트 설정을 바꾼다. 본문의 `name`, `description`, `scope`, `isCodeAccessibleMemberOnly`, `isDismissingStaleApprovals`, `allowMergeCommit`, `allowSquashMerge`,
     * `allowRebaseMerge`, `defaultMergeMethod`(`merge`, `squash`, `rebase`), `defaultBranch`(Git, 있는 브랜치), `menus`(보낸 메뉴만) 중 보낸 항목만 바꾼다. 이름이 바뀌면
     * 저장소 디렉터리도 옮긴다. 값이 틀리면 400, 바꾼 이름이 같은 소유자의 다른 프로젝트와 겹치면 409이다. 프로젝트를 고칠 수 있어야 한다(프로젝트 관리자, 조직 관리자,
     * 사이트 관리자). 결과: `{"result": {...}}`.
     */
    @ApiOperation(operationId = "updateProject", tags = [ApiTags.PROJECT], summary = "프로젝트 설정 바꾸기", description = "이름, 설명, 공개 범위, 코드 접근 제한, 병합 방식 설정, 기본 브랜치, 메뉴 중 보낸 항목만 바꾼다. 웹 화면의 설정과 같은 규칙이다. 프로젝트를 고칠 수 있는 사용자(프로젝트 관리자, 조직 관리자, 사이트 관리자)만 한다.")
    @BodyDoc(description = "바꿀 항목", required = true, content = [Content(schema = Schema(implementation = ProjectUpdateRequest::class))])
    @ApiResponse(responseCode = "200", description = "바뀐 프로젝트", content = [Content(schema = Schema(implementation = ProjectResult::class))])
    @ApiResponse(responseCode = "400", description = "바꿀 항목이 없거나 값이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "같은 소유자에게 같은 이름이 있다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PatchMapping("/-_-api/v1/owners/{owner}/projects/{projectName}")
    @Transactional
    fun update(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return requests.message(HttpStatus.NOT_FOUND, "not found")
        if (!accessControl.isAllowed(user, project.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (FIELDS.none { json.has(it) }) return requests.message(HttpStatus.BAD_REQUEST, "no updatable field: ${FIELDS.joinToString(", ")}")

        val name = optionalString(json, "name") { return it }
        val overview = optionalString(json, "description") { return it }
        val scope = scopeOf(json) { return it }
        val codeMemberOnly = optionalBoolean(json, "isCodeAccessibleMemberOnly") { return it }
        val dismissing = optionalBoolean(json, "isDismissingStaleApprovals") { return it }
        val merge = optionalBoolean(json, "allowMergeCommit") { return it }
        val squash = optionalBoolean(json, "allowSquashMerge") { return it }
        val rebase = optionalBoolean(json, "allowRebaseMerge") { return it }
        val defaultMethod = optionalString(json, "defaultMergeMethod") { return it }
        val defaultBranch = optionalString(json, "defaultBranch") { return it }?.let { branch ->
            if (project.vcs != RepositoryService.VCS_GIT) return requests.message(HttpStatus.BAD_REQUEST, "defaultBranch is only for Git projects")
            val ref = if (branch.startsWith("refs/heads/")) branch else "refs/heads/$branch"
            if (ref !in repositories.refNames(project)) return requests.message(HttpStatus.BAD_REQUEST, "no branch by request: $branch")
            ref
        }
        val currentMenus = menuSettings.findFirstByProjectId(requireNotNull(project.id))
            ?.let { s -> ProjectForm.MENU_NAMES.filter { n -> when (n) { "code" -> s.code; "issue" -> s.issue; "pullRequest" -> s.pullRequest; "review" -> s.review; "milestone" -> s.milestone; else -> s.board } == true }.toSet() }.orEmpty()
        val menus = menusOf(json, currentMenus) { return it }

        val form = ProjectSettingForm(
            name = name ?: project.name, overview = overview, projectScope = scope?.name, isCodeAccessibleMemberOnly = codeMemberOnly, isDismissingStaleApprovals = dismissing,
            allowMergeCommit = merge, allowSquashMerge = squash, allowRebaseMerge = rebase, defaultMergeMethod = defaultMethod, defaultBranch = defaultBranch, menus = menus
        )
        settingService.validate(project, form, null).let { validation ->
            if (validation.hasErrors()) return invalid(validation.errors.fields())
        }
        settingService.update(project, form, null, user.loginId)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to projectJson.project(project)))
    }

    // --- 지우기 ---

    /** 프로젝트를 지운다. 저장소와 이슈, 글, 풀 리퀘스트 등 속한 것이 모두 지워진다. 프로젝트를 지울 수 있는 사용자(프로젝트 관리자, 조직 관리자, 사이트 관리자)만 한다. 성공하면 204이다. */
    @ApiOperation(operationId = "deleteProject", tags = [ApiTags.PROJECT], summary = "프로젝트 지우기", description = "프로젝트와 저장소, 이슈, 글, 풀 리퀘스트 등 속한 것을 모두 지운다. 되돌릴 수 없다. 프로젝트를 지울 수 있는 사용자(프로젝트 관리자, 조직 관리자, 사이트 관리자)만 하고 `repo` 범위가 필요하다.")
    @ApiResponse(responseCode = "204", description = "지웠다.")
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("/-_-api/v1/owners/{owner}/projects/{projectName}")
    @Transactional
    fun delete(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return requests.message(HttpStatus.NOT_FOUND, "not found")
        if (!accessControl.isAllowed(user, project.asResource(), Operation.DELETE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val projectId = requireNotNull(project.id)
        // 지우는 사용자의 즐겨찾기를 먼저 정리한다 (웹의 `ProjectDeleteController`와 같다)
        favorites.findByUserIdAndProjectId(requireNotNull(user.id), projectId).forEach { favorites.delete(it) }
        favorites.flush()
        deletion.delete(project)
        repositories.deleteRepository(project)
        return ResponseEntity.noContent().build<Void>()
    }

    // --- 입력 읽기 ---

    /** 서비스가 돌려주는 필드별 오류를 응답으로 바꾼다. 같은 이름이 이미 있으면 409, 그 밖은 400이다. */
    private fun invalid(fields: Map<String, List<String>>): ResponseEntity<*> {
        val duplicate = fields.values.flatten().any { it == "project.name.duplicate" }
        return if (duplicate) requests.message(HttpStatus.CONFLICT, "a project with the same name exists")
        else requests.message(HttpStatus.BAD_REQUEST, "invalid value: " + fields.entries.joinToString(", ") { (field, keys) -> "$field(${keys.first()})" })
    }

    /** 문자열 항목이다. 키가 없으면 `null`이고, JSON `null`은 빈 문자열(비움)이며, 문자열이 아니면 [reject]로 400을 돌려준다. */
    private inline fun optionalString(json: JsonNode, key: String, reject: (ResponseEntity<*>) -> Nothing): String? {
        if (!json.has(key)) return null
        val node = json.get(key)
        if (node.isNull) return ""
        if (!node.isString) reject(requests.message(HttpStatus.BAD_REQUEST, "$key must be a string"))
        return node.asString("")
    }

    /** 불리언 항목을 서비스가 받는 문자열(`true`, `false`)로 바꾼다. 키가 없으면 `null`이고, 불리언이 아니면 400이다. */
    private inline fun optionalBoolean(json: JsonNode, key: String, reject: (ResponseEntity<*>) -> Nothing): String? {
        if (!json.has(key)) return null
        val node = json.get(key)
        if (!node.isBoolean) reject(requests.message(HttpStatus.BAD_REQUEST, "$key must be a boolean"))
        return node.asBoolean().toString()
    }

    private inline fun scopeOf(json: JsonNode, reject: (ResponseEntity<*>) -> Nothing): ProjectScope? {
        if (!json.has("scope")) return null
        val text = json.get("scope").takeIf { it.isString }?.asString("")
        return ProjectScope.entries.firstOrNull { it.name == text } ?: reject(requests.message(HttpStatus.BAD_REQUEST, "scope must be PUBLIC, PROTECTED or PRIVATE"))
    }

    /** 본문의 `menus`(메뉴 이름: 불리언)를 [base] 위에 겹친 메뉴 집합이다. 모르는 메뉴 이름이나 불리언이 아닌 값은 400이다. */
    private inline fun menusOf(json: JsonNode, base: Set<String>, reject: (ResponseEntity<*>) -> Nothing): Set<String> {
        if (!json.has("menus")) return base
        val node = json.get("menus")
        if (!node.isObject) reject(requests.message(HttpStatus.BAD_REQUEST, "menus must be an object of menu names and booleans"))
        val result = base.toMutableSet()
        node.properties().forEach { (menu, value) ->
            if (menu !in ProjectForm.MENU_NAMES) reject(requests.message(HttpStatus.BAD_REQUEST, "unknown menu: $menu"))
            if (!value.isBoolean) reject(requests.message(HttpStatus.BAD_REQUEST, "menus.$menu must be a boolean"))
            if (value.asBoolean()) result += menu else result -= menu
        }
        return result
    }

    private companion object {
        val FIELDS = listOf(
            "name", "description", "scope", "isCodeAccessibleMemberOnly", "isDismissingStaleApprovals", "allowMergeCommit", "allowSquashMerge", "allowRebaseMerge",
            "defaultMergeMethod", "defaultBranch", "menus"
        )
    }
}
