package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.wiki.WikiConflictException
import dev.yona.domain.wiki.WikiException
import dev.yona.domain.wiki.WikiPageExistsException
import dev.yona.domain.wiki.WikiPageNotFoundException
import dev.yona.domain.wiki.WikiService
import dev.yona.domain.wiki.WikiTitleInvalidException
import dev.yona.domain.wiki.WikiUnavailableException
import dev.yona.repository.CommitInfo
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc
import io.swagger.v3.oas.annotations.responses.ApiResponse
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import io.swagger.v3.oas.annotations.Operation as ApiOperation

/**
 * 프로젝트 위키 REST이다(로드맵 X-17, `decisions.md` D-60). 읽기는 프로젝트를 읽을 수 있어야 하고(코드 접근을 구성원으로 제한한 프로젝트라도 위키는 코드가 아니므로
 * 그 제한을 받지 않는다), 만들기와 고치기와 지우기는 프로젝트 구성원이다(`ResourceType.WIKI_PAGE`의 `UPDATE`). 범위는 `repo`나 공개 프로젝트의 `public_repo`이다(표는 `ApiScopeRules`).
 *
 * 페이지는 경로의 이름(제목의 공백이 `-`인 값)으로 가리킨다. 이름에는 슬래시가 없으므로 한 경로 구간이다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalWikiApiController(
    private val wikiService: WikiService,
    private val support: PullRequestApiSupport,
    private val requests: ApiRequests,
    private val json: PullRequestJson,
    private val accessControl: AccessControl
) {

    @ApiOperation(operationId = "listWikiPages", tags = [ApiTags.WIKI], summary = "위키 페이지 목록", description = "제목순(대소문자 무시)이며 `_Sidebar`와 `_Footer`는 뺀다. `q`를 주면 제목에 그 글이 들어 있는 페이지만 돌려준다(대소문자 무시). 위키가 아직 없으면 빈 목록이다.")
    @ApiResponse(responseCode = "200", description = "페이지 목록", content = [Content(schema = Schema(implementation = WikiPageList::class))])
    @io.swagger.v3.oas.annotations.Parameter(name = "q", description = "제목에 들어 있어야 하는 글(대소문자 무시). 생략하면 전부", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @GetMapping("$PROJECT/wiki/pages")
    @Transactional(readOnly = true)
    fun list(@PathVariable owner: String, @PathVariable projectName: String, @RequestParam(required = false) q: String?, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        val project = requireNotNull(target.project)
        if (!wikiService.enabled(project)) return disabled()
        val pages = if (q.isNullOrBlank()) wikiService.pages(project) else wikiService.search(project, q)
        return support.ok(linkedMapOf("pages" to pages.map { linkedMapOf("name" to it.name, "title" to it.title) }))
    }

    @ApiOperation(operationId = "getWikiPage", tags = [ApiTags.WIKI], summary = "위키 페이지 한 건", description = "마크다운 본문과 그 내용을 마지막으로 바꾼 커밋을 돌려준다. `revision`(커밋 번호)을 주면 그 시점의 내용이다. 페이지나 리비전이 없으면 404이다. 이름은 공백 표기와 하이픈 표기를 모두 받는다.")
    @ApiResponse(responseCode = "200", description = "페이지", content = [Content(schema = Schema(implementation = WikiPageResult::class))])
    @ApiResponse(responseCode = "404", description = "페이지나 리비전이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @io.swagger.v3.oas.annotations.Parameter(name = "revision", description = "읽을 시점의 커밋 번호(앞부분만 가능). 생략하면 최신", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @GetMapping("$PROJECT/wiki/pages/{name}")
    @Transactional(readOnly = true)
    fun get(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable name: String, @RequestParam(required = false) revision: String?, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        val project = requireNotNull(target.project)
        if (!wikiService.enabled(project)) return disabled()
        val page = wikiService.page(project, name, revision?.takeIf { it.isNotBlank() }) ?: return requests.message(HttpStatus.NOT_FOUND, "no wiki page by request")
        val node = linkedMapOf<String, Any?>("name" to page.name, "title" to page.title, "content" to page.content)
        page.lastCommit?.let { node["lastCommit"] = commit(it) }
        return support.ok(linkedMapOf("page" to node))
    }

    @ApiOperation(operationId = "createWikiPage", tags = [ApiTags.WIKI], summary = "위키 페이지 만들기", description = "새 페이지를 만든다. 위키 저장소가 아직 없으면 이때 만든다. 제목 규칙을 어기면 400, 같은 이름(공백과 하이픈은 같다)이 있으면 409이다. 프로젝트 구성원만 한다.")
    @BodyDoc(description = "만들 페이지", required = true, content = [Content(schema = Schema(implementation = WikiCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "만든 페이지", content = [Content(schema = Schema(implementation = WikiPageRefResult::class))])
    @ApiResponse(responseCode = "400", description = "제목이 규칙을 어겼다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "같은 이름의 페이지가 있거나 이 프로젝트의 위키를 쓸 수 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$PROJECT/wiki/pages")
    @Transactional
    fun create(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val allowed = writer(owner, projectName, request)
        if (allowed !is Allowed) return allowed
        val project = allowed.project
        val user = allowed.user
        val body = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val title = body.get("title")?.takeIf { it.isString }?.asString("")?.takeIf { it.isNotBlank() } ?: return requests.message(HttpStatus.BAD_REQUEST, "title is required")
        val content = optionalString(body, "content") ?: return requests.message(HttpStatus.BAD_REQUEST, "content must be a string")
        val message = optionalString(body, "message") ?: return requests.message(HttpStatus.BAD_REQUEST, "message must be a string")
        return try {
            val page = wikiService.create(project, user, title, content.value.orEmpty(), message.value)
            ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("page" to linkedMapOf("name" to page.name, "title" to page.title)))
        } catch (e: WikiException) {
            failure(e)
        }
    }

    @ApiOperation(operationId = "updateWikiPage", tags = [ApiTags.WIKI], summary = "위키 페이지 고치기와 이름 바꾸기", description = "`content`를 주면 본문을 바꾸고 `title`을 주면 이름을 바꾼다(둘 다 가능, 한 커밋). 하나도 없으면 400이다. 없는 페이지는 404, 바꾸려는 이름이 이미 있으면 409이다. 저장하는 사이 다른 커밋이 들어오면 409이다. 프로젝트 구성원만 한다.")
    @BodyDoc(description = "바꿀 내용", required = true, content = [Content(schema = Schema(implementation = WikiUpdateRequest::class))])
    @ApiResponse(responseCode = "200", description = "고친 페이지", content = [Content(schema = Schema(implementation = WikiPageRefResult::class))])
    @ApiResponse(responseCode = "404", description = "페이지가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "바꾸려는 이름이 있거나 저장하는 사이 위키가 바뀌었다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PatchMapping("$PROJECT/wiki/pages/{name}")
    @Transactional
    fun update(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable name: String, request: HttpServletRequest): Any {
        val allowed = writer(owner, projectName, request)
        if (allowed !is Allowed) return allowed
        val project = allowed.project
        val user = allowed.user
        val body = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val title = optionalString(body, "title") ?: return requests.message(HttpStatus.BAD_REQUEST, "title must be a string")
        val content = optionalString(body, "content") ?: return requests.message(HttpStatus.BAD_REQUEST, "content must be a string")
        val message = optionalString(body, "message") ?: return requests.message(HttpStatus.BAD_REQUEST, "message must be a string")
        if (title.value == null && content.value == null) return requests.message(HttpStatus.BAD_REQUEST, "title or content is required")
        return try {
            val text = content.value ?: (wikiService.page(project, name)?.content ?: throw WikiPageNotFoundException("no wiki page by request"))
            val page = wikiService.update(project, user, name, title.value, text, message.value)
            support.ok(linkedMapOf("page" to linkedMapOf("name" to page.name, "title" to page.title)))
        } catch (e: WikiException) {
            failure(e)
        }
    }

    @ApiOperation(operationId = "deleteWikiPage", tags = [ApiTags.WIKI], summary = "위키 페이지 지우기", description = "페이지를 지운다. 지운 기록은 커밋으로 남고 이력에서 볼 수 있다. `message`로 커밋 메시지를 줄 수 있다. 없는 페이지는 404이다. 프로젝트 구성원만 한다.")
    @ApiResponse(responseCode = "204", description = "지웠다.")
    @ApiResponse(responseCode = "404", description = "페이지가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @io.swagger.v3.oas.annotations.Parameter(name = "message", description = "커밋 메시지. 생략하면 `Delete <이름>`", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @DeleteMapping("$PROJECT/wiki/pages/{name}")
    @Transactional
    fun delete(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable name: String, @RequestParam(required = false) message: String?, request: HttpServletRequest): Any {
        val allowed = writer(owner, projectName, request)
        if (allowed !is Allowed) return allowed
        val project = allowed.project
        val user = allowed.user
        return try {
            wikiService.delete(project, user, name, message)
            ResponseEntity.noContent().build<Void>()
        } catch (e: WikiException) {
            failure(e)
        }
    }

    @ApiOperation(operationId = "listWikiPageHistory", tags = [ApiTags.WIKI], summary = "위키 페이지 이력", description = "페이지 파일을 바꾼 커밋을 최신순으로 돌려준다. 지워진 페이지의 이력도 나온다. 이름을 바꾸기 전의 이력은 따라가지 않는다. `page`는 0부터, `size`는 기본 30이고 최대 100이다.")
    @ApiResponse(responseCode = "200", description = "커밋 목록", content = [Content(schema = Schema(implementation = WikiHistoryList::class))])
    @io.swagger.v3.oas.annotations.Parameter(name = "page", description = "쪽 번호(0부터)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "0")
    @io.swagger.v3.oas.annotations.Parameter(name = "size", description = "한 쪽의 개수(기본 30, 최대 100)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "30")
    @GetMapping("$PROJECT/wiki/pages/{name}/history")
    @Transactional(readOnly = true)
    fun history(
        @PathVariable owner: String, @PathVariable projectName: String, @PathVariable name: String,
        @RequestParam(required = false, defaultValue = "0") page: Int, @RequestParam(required = false, defaultValue = "30") size: Int, request: HttpServletRequest
    ): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        val project = requireNotNull(target.project)
        if (!wikiService.enabled(project)) return disabled()
        val commits = wikiService.history(project, name, page.coerceAtLeast(0), size.coerceIn(1, 100))
        return support.ok(linkedMapOf("history" to commits.map { commit(it) }))
    }

    @ApiOperation(operationId = "getWikiPageDiff", tags = [ApiTags.WIKI], summary = "위키 페이지의 diff와 두 리비전 비교", description = "`commit`을 주면 그 커밋이 이 페이지에 가한 변경이고(첫 커밋이면 전부 추가), `from`과 `to`를 주면 두 리비전 사이의 변경이다. 둘 중 하나는 있어야 하며 없으면 400이다. 리비전이 없으면 404이다. 결과는 통합 diff 글이다.")
    @ApiResponse(responseCode = "200", description = "diff", content = [Content(schema = Schema(implementation = WikiDiffResult::class))])
    @ApiResponse(responseCode = "400", description = "`commit`이나 `from`과 `to`가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "리비전이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @io.swagger.v3.oas.annotations.Parameter(name = "commit", description = "이 커밋이 페이지에 가한 변경을 본다", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "from", description = "비교의 시작 리비전. `to`와 함께 쓴다", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "to", description = "비교의 끝 리비전. `from`과 함께 쓴다", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @GetMapping("$PROJECT/wiki/pages/{name}/diff")
    @Transactional(readOnly = true)
    fun diff(
        @PathVariable owner: String, @PathVariable projectName: String, @PathVariable name: String,
        @RequestParam(required = false) commit: String?, @RequestParam(required = false) from: String?, @RequestParam(required = false) to: String?, request: HttpServletRequest
    ): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        val project = requireNotNull(target.project)
        if (!wikiService.enabled(project)) return disabled()
        return try {
            val text = when {
                !commit.isNullOrBlank() -> wikiService.diff(project, name, commit)
                !from.isNullOrBlank() && !to.isNullOrBlank() -> wikiService.compare(project, name, from, to)
                else -> return requests.message(HttpStatus.BAD_REQUEST, "commit or from and to are required")
            }
            support.ok(linkedMapOf("diff" to text))
        } catch (e: WikiException) {
            failure(e)
        }
    }

    /** 쓰기가 허용된 요청의 프로젝트와 사용자이다. */
    private class Allowed(val project: Project, val user: dev.yona.domain.user.User)

    /** 쓸 수 있으면 [Allowed], 아니면 그대로 돌려줄 오류 응답(인증 없음 401, 프로젝트를 못 읽음 403, 구성원이 아님 403)이다. */
    private fun writer(owner: String, projectName: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        val project = requireNotNull(target.project)
        if (!wikiService.enabled(project)) return disabled()
        val user = requireNotNull(target.user)
        if (!accessControl.isAllowed(user, ProjectResource(requireNotNull(project.id).toString(), ResourceType.WIKI_PAGE, project), Operation.UPDATE)) {
            return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        }
        return Allowed(project, user)
    }

    private fun disabled() = requests.message(HttpStatus.NOT_FOUND, "wiki is disabled")

    /** 선택 문자열 필드이다. 필드가 없거나 `null`이면 값이 없고, 문자열이 아니면 `null`(오류)이다. 값 없음과 오류를 구분하려고 [Opt]로 감싼다. */
    private class Opt(val value: String?)

    private fun optionalString(body: tools.jackson.databind.JsonNode, field: String): Opt? {
        val node = body.get(field)
        if (node == null || node.isNull) return Opt(null)
        return if (node.isString) Opt(node.asString("")) else null
    }

    private fun failure(e: WikiException): ResponseEntity<Map<String, String>> = when (e) {
        is WikiTitleInvalidException -> requests.message(HttpStatus.BAD_REQUEST, e.message.orEmpty())
        is WikiPageNotFoundException -> requests.message(HttpStatus.NOT_FOUND, "no wiki page by request")
        is WikiPageExistsException -> requests.message(HttpStatus.CONFLICT, "a wiki page with the same name exists")
        is WikiUnavailableException, is WikiConflictException -> requests.message(HttpStatus.CONFLICT, e.message.orEmpty())
    }

    private fun commit(info: CommitInfo): Map<String, Any?> =
        linkedMapOf("id" to info.id, "message" to info.shortMessage, "author" to linkedMapOf("name" to info.authorName), "date" to json.iso(info.authorDate))

    private companion object {
        const val PROJECT = "/-_-api/v1/owners/{owner}/projects/{projectName}"
    }
}
