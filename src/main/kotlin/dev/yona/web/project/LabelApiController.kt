package dev.yona.web.project

import dev.yona.access.AccessControl
import dev.yona.access.labelsAsResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.issue.Label
import dev.yona.domain.issue.LabelRepository
import dev.yona.domain.issue.LabelService
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.HttpPreference
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.DefaultProjectCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 라벨 JSON API이다. 전역 라벨의 타입어헤드 검색(`GET /labels`, `GET /categories`)과 프로젝트에 라벨을 붙이고 떼는
 * 요청(`/{user}/{project}/labels`)을 처리한다.
 *
 * v1.6 `LabelApp`(`app/controllers/LabelApp.java`)과 `ProjectApp.labels`, `attachLabel`, `detachLabel`에 해당한다.
 * 화면이 직접 부르지 않는 보조 API이며 요청과 응답 계약만 그대로 따른다.
 */
@Controller
@AnonymousCheck
class LabelApiController(
    private val labelService: LabelService,
    private val labels: LabelRepository,
    private val projectService: ProjectService,
    private val views: ProjectViewFactory,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider
) {

    /**
     * 라벨 이름 목록이다. `limit`은 필수이며 없으면 `400 No limit`, JSON을 받지 않으면 `406`이다.
     * 결과가 한도보다 많으면 한도만큼만 주고 `Content-Range: items 한도/전체`를 붙인다.
     */
    @GetMapping("/labels")
    @Transactional(readOnly = true)
    fun labels(
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(defaultValue = "") category: String,
        @RequestParam(required = false) limit: Int?,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        if (!acceptsJson(request)) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        if (limit == null) return noLimit()
        return ranged(labelService.searchNames(category, query, limit), limit)
    }

    /** 라벨 분류 목록이다. 계약은 [labels]와 같고, 한도는 [MAX_FETCH_LABELS]를 넘지 못한다. */
    @GetMapping("/categories")
    @Transactional(readOnly = true)
    fun categories(
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(required = false) limit: Int?,
        request: HttpServletRequest
    ): ResponseEntity<*> {
        if (!acceptsJson(request)) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        if (limit == null) return noLimit()
        val capped = minOf(limit, MAX_FETCH_LABELS)
        return ranged(labelService.searchCategories(query, capped), capped)
    }

    /** 프로젝트에 붙은 라벨을 `{라벨 id: {category, name}}` 형태로 돌려준다. */
    @GetMapping("/{user}/{project}/labels")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun projectLabels(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest): ResponseEntity<*> {
        if (!acceptsJson(request)) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ResponseEntity.notFound().build<Void>()
        return json(HttpStatus.OK, project.labels.associate { requireNotNull(it.id) to labelMap(it) })
    }

    /**
     * 프로젝트에 라벨을 붙인다. `name`은 필수이다. 라벨이 없으면 만들고(`201`), 있으면 붙이며(`200`),
     * 이미 붙어 있으면 `204`이다. 프로젝트의 라벨을 고칠 수 없으면 `403`이다.
     */
    @PostMapping("/{user}/{project}/labels")
    @DefaultProjectCheck
    @Transactional
    fun attach(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        if (!canUpdate(project)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        val labelName = request.getParameter("name")
        if (labelName.isNullOrEmpty()) return ErrorViews.badRequest("Label name is missing.", views.of(project), "PROJECT_HOME")
        val (label, created) = labelService.findOrCreate(request.getParameter("category"), labelName)
        if (!labelService.attach(project, label)) return ResponseEntity.noContent().build<Void>()
        return json(if (created) HttpStatus.CREATED else HttpStatus.OK, mapOf(requireNotNull(label.id) to labelMap(label)))
    }

    /** 프로젝트에서 라벨을 뗀다. `_method=delete`가 없으면 `400`, 라벨이 없으면 `404`이다. 쓰는 프로젝트가 없어지면 라벨도 지운다. */
    @PostMapping("/{user}/{project}/labels/{id}")
    @DefaultProjectCheck
    @Transactional
    fun detach(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("id") id: Long, request: HttpServletRequest): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        if (!canUpdate(project)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        if (request.getParameter("_method")?.lowercase() != "delete") {
            return ErrorViews.badRequest("_method must be 'delete'.", views.of(project), "PROJECT_HOME")
        }
        val label = labels.findById(id).orElse(null) ?: return ErrorViews.notFound("error.notfound")
        labelService.detach(project, label)
        return ResponseEntity.noContent().build<Void>()
    }

    private fun canUpdate(project: dev.yona.domain.project.Project) =
        accessControl.isAllowed(currentUserProvider.get().user, project.labelsAsResource(), Operation.UPDATE)

    private fun acceptsJson(request: HttpServletRequest) = HttpPreference.acceptsType(request.getHeader(HttpHeaders.ACCEPT), "application/json")

    private fun noLimit(): ResponseEntity<*> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.valueOf("text/plain;charset=UTF-8")).body("No limit")

    private fun ranged(page: LabelService.Page<String>, limit: Int): ResponseEntity<*> {
        val builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        if (page.total > limit) builder.header("Content-Range", "items $limit/${page.total}")
        return builder.body(page.items)
    }

    private fun labelMap(label: Label) = mapOf("category" to label.category, "name" to label.name)

    private fun json(status: HttpStatus, body: Any): ResponseEntity<*> = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body)

    private companion object {
        /** `categories`가 한 번에 돌려주는 최대 개수이다 (v1.6 `LabelApp.MAX_FETCH_LABELS`). */
        const val MAX_FETCH_LABELS = 1000
    }
}
