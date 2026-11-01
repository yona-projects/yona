package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.repository.RepositoryService
import dev.yona.repository.TagCreateError
import dev.yona.repository.TagInfo
import dev.yona.repository.TagService
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import org.eclipse.jgit.lib.PersonIdent
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
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * Git 태그 REST이다(로드맵 X-37c, `decisions.md` D-52). 목록과 지우기는 웹의 태그 화면([dev.yona.web.code.TagController], X-18)에 있고, 만들기는 REST에만 있다.
 * 읽기는 코드를 읽을 수 있어야 하고(코드를 구성원에게만 여는 프로젝트는 구성원), 만들기는 코드를 고칠 수 있는 사용자(푸시할 수 있는 구성원)이며, 지우기는 웹처럼 프로젝트를
 * 지울 수 있는 사용자(프로젝트 관리자)이다. Git 프로젝트만 된다. 범위는 `repo`나 공개 프로젝트의 `public_repo`이다(표는 `ApiScopeRules`).
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalTagApiController(
    private val tagService: TagService,
    private val support: PullRequestApiSupport,
    private val requests: ApiRequests,
    private val json: PullRequestJson,
    private val accessControl: AccessControl,
    private val verification: dev.yona.domain.gpgkey.GpgVerificationService
) {

    /** 태그 목록이다. 태그 시각이 최근인 순서이다. 결과: `{"tags": [...]}`. */
    @ApiOperation(operationId = "listTags", tags = [ApiTags.PROJECT], summary = "Git 태그 목록", description = "저장소의 태그를 태그 시각이 최근인 순서로 돌려준다. 코드를 읽을 수 있어야 한다(코드를 구성원에게만 여는 프로젝트는 구성원). Git 프로젝트만 된다.")
    @ApiResponse(responseCode = "200", description = "태그 목록", content = [Content(schema = Schema(implementation = TagList::class))])
    @ApiResponse(responseCode = "400", description = "Git 프로젝트가 아니다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("$PROJECT/tags")
    @Transactional(readOnly = true)
    fun list(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request)
        target.error?.let { return it }
        val project = requireNotNull(target.project)
        if (project.vcs != RepositoryService.VCS_GIT) return requests.message(HttpStatus.BAD_REQUEST, "only Git projects have tags")
        return support.ok(linkedMapOf("tags" to tagService.tags(project).map { node(it) }))
    }

    /**
     * 태그를 만든다. 본문 `{"name"(필수), "target", "message"}`이다. `target`은 브랜치, 태그, 커밋 번호(앞부분만 가능)이고 없으면 기본 브랜치의 끝이다. `message`가 있으면
     * 주석 태그(태그를 단 사람과 시각이 남는다), 없으면 경량 태그이다. 이름 규칙 위반, 없는 대상, 커밋이 없는 저장소는 400이고 같은 이름이 있으면 409이다.
     * 성공하면 201과 만든 태그이다.
     */
    @ApiOperation(operationId = "createTag", tags = [ApiTags.PROJECT], summary = "Git 태그 만들기", description = "대상(브랜치, 태그, 커밋 번호, 생략하면 기본 브랜치의 끝)에 태그를 만든다. `message`가 있으면 주석 태그, 없으면 경량 태그이다. 이름 규칙 위반, 없는 대상, 커밋이 없는 저장소는 400이고 같은 이름이 있으면 409이다. 코드를 고칠 수 있는 사용자(푸시할 수 있는 구성원)만 한다.")
    @BodyDoc(description = "만들 태그", required = true, content = [Content(schema = Schema(implementation = TagCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "만든 태그", content = [Content(schema = Schema(implementation = TagResult::class))])
    @ApiResponse(responseCode = "400", description = "이름이나 대상이 틀렸거나 커밋이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "같은 이름의 태그가 있다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$PROJECT/tags")
    @Transactional
    fun create(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request)
        target.error?.let { return it }
        val project = requireNotNull(target.project)
        val user = requireNotNull(target.user)
        if (project.vcs != RepositoryService.VCS_GIT) return requests.message(HttpStatus.BAD_REQUEST, "only Git projects have tags")
        if (!accessControl.isAllowed(user, ProjectResource(requireNotNull(project.id).toString(), ResourceType.CODE, project), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val body = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val name = body.get("name")?.takeIf { it.isString }?.asString("")?.takeIf { it.isNotBlank() } ?: return requests.message(HttpStatus.BAD_REQUEST, "name is required")
        val revision = body.get("target")?.takeIf { !it.isNull }?.let { if (it.isString) it.asString("") else return requests.message(HttpStatus.BAD_REQUEST, "target must be a string") }
        val message = body.get("message")?.takeIf { !it.isNull }?.let { if (it.isString) it.asString("") else return requests.message(HttpStatus.BAD_REQUEST, "message must be a string") }
        val tagger = PersonIdent(user.name.orEmpty().ifBlank { user.loginId.orEmpty() }, user.email.orEmpty().ifBlank { "${user.loginId}@localhost" })
        val result = tagService.create(project, name, revision, message, tagger)
        result.error?.let { error ->
            return when (error) {
                TagCreateError.ALREADY_EXISTS -> requests.message(HttpStatus.CONFLICT, "a tag with the same name exists")
                TagCreateError.INVALID_NAME -> requests.message(HttpStatus.BAD_REQUEST, "invalid tag name")
                TagCreateError.NO_TARGET -> requests.message(HttpStatus.BAD_REQUEST, "no commit by target")
                TagCreateError.EMPTY_REPOSITORY -> requests.message(HttpStatus.BAD_REQUEST, "the repository has no commits")
            }
        }
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to node(requireNotNull(result.tag))))
    }

    /** 태그를 지운다. 프로젝트를 지울 수 있는 사용자만 한다. 이름에 슬래시가 들어 있어도 된다(`release/1.0`). 없는 태그나 태그가 아닌 이름은 404이다. 성공하면 204이다. */
    @ApiOperation(operationId = "deleteTag", tags = [ApiTags.PROJECT], summary = "Git 태그 지우기", description = "태그를 지운다. 이름에 슬래시가 있어도 된다(`.../tags/release/1.0`). 웹의 태그 화면처럼 프로젝트를 지울 수 있는 사용자(프로젝트 관리자)만 한다. 없는 태그나 브랜치 이름은 404이다.")
    @ApiResponse(responseCode = "204", description = "지웠다.")
    @ApiResponse(responseCode = "404", description = "태그가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("$PROJECT/tags/{*name}")
    @Transactional
    fun delete(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable name: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request)
        target.error?.let { return it }
        val project = requireNotNull(target.project)
        if (project.vcs != RepositoryService.VCS_GIT) return requests.message(HttpStatus.BAD_REQUEST, "only Git projects have tags")
        if (!accessControl.isAllowed(requireNotNull(target.user), project.asResource(), Operation.DELETE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (!tagService.delete(project, name.removePrefix("/"))) return requests.message(HttpStatus.NOT_FOUND, "no tag by request")
        return ResponseEntity.noContent().build<Void>()
    }

    /** 태그 한 개의 JSON이다. 경량 태그는 `message`와 `tagger`가 없고, 커밋이 아닌 대상을 가리키면 `commit`이 없다. */
    private fun node(tag: TagInfo): Map<String, Any?> {
        val node = linkedMapOf<String, Any?>("name" to tag.shortName, "annotated" to tag.annotated)
        tag.message?.let { node["message"] = it }
        if (tag.taggerName != null || tag.taggerDate != null) node["tagger"] = linkedMapOf("name" to tag.taggerName, "date" to json.iso(tag.taggerDate))
        tag.commitId?.let { node["commit"] = linkedMapOf("id" to it, "date" to json.iso(tag.commitDate)) }
        // 서명 판정(X-4, D-65): 주석 태그에만 있다. 서명이 없으면 `unsigned`이다
        if (tag.annotated) node["verification"] = json.verification(verification.verifyTag(tag.signature, tag.taggerEmail))
        return node
    }

    private companion object {
        const val PROJECT = "/-_-api/v1/owners/{owner}/projects/{projectName}"
    }
}
