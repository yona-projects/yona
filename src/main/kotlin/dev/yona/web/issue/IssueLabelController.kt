package dev.yona.web.issue

import dev.yona.web.AjaxRequests
import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.IssueLabelStyles
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.HttpPreference
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.IsCreatable
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 이슈 라벨과 라벨 분류 관리이다. v1.6 `IssueLabelApp`(`app/controllers/IssueLabelApp.java`)에 해당한다.
 *
 * 목록과 분류 조회는 프로젝트를 읽을 수 있는 사용자, 관리 화면은 프로젝트를 고칠 수 있는 사용자, 만들기는 프로젝트의 구성원이
 * 할 수 있다. 라벨 편집 화면(`IssueLabelEditor`)의 Ajax 요청과 폼 계약을 그대로 따른다.
 */
@Controller
@AnonymousCheck
class IssueLabelController(
    private val projectService: ProjectService,
    private val labelService: IssueLabelService,
    private val issueService: dev.yona.domain.issue.IssueService,
    private val labels: IssueLabelRepository,
    private val categories: IssueLabelCategoryRepository,
    private val views: ProjectViewFactory,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val messages: MessageSource
) {

    // --- 라벨 ---

    /** 라벨 목록이다. PJAX 요청(`X-PJAX: true`)에는 목록 조각을, 그 밖에는 JSON을 돌려준다. */
    @GetMapping("/{user}/{project}/issue/labels")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun labels(
        @PathVariable("user") owner: String,
        @PathVariable("project") name: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        if (AjaxRequests.isPjax(request)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
            return ModelAndView("project/partial-issue-labels-list :: page")
                .addObject("projectView", views.of(project)).addObject("groups", groups(project))
        }
        if (!HttpPreference.acceptsType(request.getHeader(HttpHeaders.ACCEPT), "application/json")) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        }
        // v1.6는 문자열 본문에 `Content-Type: application/json`을 직접 정해 문자 집합이 없다
        request.setAttribute(dev.yona.web.JsonCharsetFilter.RAW_ATTRIBUTE, true)
        return json(HttpStatus.OK, labelService.labels(project).map { labelMap(it) })
    }

    /** 라벨 관리 화면이다. */
    @GetMapping("/{user}/{project}/issue/labelsform")
    @IsAllowed(Operation.UPDATE)
    @Transactional(readOnly = true)
    fun labelsForm(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val groups = groups(project)
        return ModelAndView("project/issue-labels")
            .addObject("title", "label")
            .addObject("projectView", views.of(project))
            .addObject("canCreate", accessControl.isProjectResourceCreatable(viewer, project, ResourceType.ISSUE_LABEL))
            .addObject("groups", groups)
            .addObject("editableCategories", groups.map { it.category })
    }

    /**
     * 라벨을 만든다. 입력은 `labelName`, `labelColor`, `categoryName`, `categoryIsExclusive`이다.
     * 입력 오류는 400과 항목별 오류 JSON, 같은 분류에 같은 이름이 이미 있으면 204, 만들었으면 201이다
     * (JSON을 받는 요청에는 만든 라벨을 담는다).
     */
    @PostMapping("/{user}/{project}/issue/labels")
    @IsCreatable(ResourceType.ISSUE_LABEL)
    @Transactional
    fun newLabel(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        val labelName = request.getParameter("labelName")?.ifEmpty { null }
        val labelColor = request.getParameter("labelColor")?.ifEmpty { null }
        val categoryName = request.getParameter("categoryName")?.ifEmpty { null }
        val errors = HashMap<String, MutableList<String>>()  // v1.6 Form.errors()는 HashMap이라 해시 순서이다
        if (labelName == null) errors.reject("labelName", "label.error.labelName.empty")
        else if (labelName.length > MAX_LENGTH) errors.reject("labelName", "label.error.labelName.tooLongSize")
        if (labelColor == null) errors.reject("labelColor", "label.error.color.empty")
        if (categoryName == null) errors.reject("categoryName", "label.error.categoryName.empty")
        else if (categoryName.length > MAX_LENGTH) errors.reject("categoryName", "label.error.categoryName.tooLongSize")
        if (errors.isNotEmpty()) return errorsJson(errors)

        val label = labelService.createLabel(
            project, labelName!!, labelColor!!, categoryName!!, toBoolean(request.getParameter("categoryIsExclusive"))
        ) ?: return ResponseEntity.noContent().build<Void>()
        if (!HttpPreference.acceptsType(request.getHeader(HttpHeaders.ACCEPT), "application/json")) {
            return ResponseEntity.status(HttpStatus.CREATED).build<Void>()
        }
        request.setAttribute(dev.yona.web.JsonCharsetFilter.RAW_ATTRIBUTE, true) // v1.6는 Content-Type을 직접 정해 문자 집합이 없다
        return json(HttpStatus.CREATED, labelMap(label))
    }

    /** 라벨을 지운다. 화면이 보내는 `_method=delete`가 없으면 400이다. */
    @PostMapping("/{user}/{project}/issue/label/{id}/delete")
    @IsAllowed(Operation.DELETE, ResourceType.ISSUE_LABEL)
    @Transactional
    fun delete(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("id") id: Long, request: HttpServletRequest): Any {
        if (request.getParameter("_method")?.lowercase() != "delete") return ErrorViews.badRequest("_method must be 'delete'.")
        val label = labels.findById(id).orElse(null) ?: return ErrorViews.notFound()
        issueService.deleteLabel(label)
        return ResponseEntity.ok().build<Void>()
    }

    /** 라벨의 이름, 색, 분류를 바꾼다. */
    @PutMapping("/{user}/{project}/issue/label/{id}")
    @IsAllowed(Operation.UPDATE, ResourceType.ISSUE_LABEL)
    @Transactional
    fun update(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("id") id: Long, request: HttpServletRequest): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        val labelName = request.getParameter("name")?.ifEmpty { null }
        val color = request.getParameter("color")?.ifEmpty { null }
        val categoryId = request.getParameter("category.id")?.ifEmpty { null }
        val errors = HashMap<String, MutableList<String>>()  // v1.6 Form.errors()는 HashMap이라 해시 순서이다
        val category = categoryId?.toLongOrNull()?.let { categories.findById(it).orElse(null) }
        if (categoryId == null) errors.reject("category", "error.required")
        else if (category == null) errors.reject("category", "error.invalid")
        // 화면은 같은 프로젝트의 분류만 고르게 한다. 다른 프로젝트의 분류는 요청을 직접 만들어야 보낼 수 있으므로 거부한다([decisions.md] A-24).
        else if (category.project?.id != project.id) errors.reject("category", "error.invalid")
        if (labelName == null) errors.reject("name", "label.error.labelName.empty")
        else if (labelName.length > MAX_LENGTH) errors.reject("name", "label.error.labelName.tooLongSize")
        if (color == null) errors.reject("color", "label.error.color.empty")
        if (errors.isNotEmpty()) return errorsJson(errors)

        val label = labels.findById(id).orElse(null) ?: return ErrorViews.notFound()
        label.name = labelName
        label.color = color
        label.category = category
        labels.save(label)
        return ResponseEntity.ok().build<Void>()
    }

    /** 라벨 색 스타일 시트이다. 라벨 목록이 바뀌지 않았으면 `ETag`로 304를 돌려준다. */
    @GetMapping("/{user}/{project}/issue/labels.css")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun labelStyles(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        val projectLabels = labelService.labels(project)
        val etag = "\"" + projectLabels.map { listOf(it.id, it.color, it.name) }.hashCode() + "\""
        if (request.getHeader(HttpHeaders.IF_NONE_MATCH) == etag) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).header(HttpHeaders.ETAG, etag).build<Void>()
        }
        // v1.6 IssueLabelApp은 Content-Type을 charset 없이 text/css로 정한다. 바이트로 내보내면 문자열 변환기가 charset을 덧붙이지 않는다
        return ResponseEntity.ok().header(HttpHeaders.ETAG, etag).contentType(MediaType("text", "css"))
            .body(IssueLabelStyles.css(projectLabels).toByteArray(Charsets.UTF_8))
    }

    /** 다른 프로젝트의 라벨을 복사한다. 읽을 수 없는 프로젝트이거나 없는 프로젝트면 아무것도 하지 않는다. */
    @PostMapping("/{user}/{project}/copyLabels")
    @IsCreatable(ResourceType.ISSUE_LABEL)
    @Transactional
    fun copyLabels(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest, response: jakarta.servlet.http.HttpServletResponse
    ): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        val fromOwner = request.getParameter("owner")
        val fromName = request.getParameter("projectName")
        val from = if (fromOwner != null && fromName != null) projectService.findByOwnerAndProjectName(fromOwner, fromName) else null
        if (from != null && accessControl.isAllowed(currentUserProvider.get().user, labelsResource(from), Operation.READ)) {
            labelService.copyLabels(from, project)
        }
        return dev.yona.web.RedirectSupport.see(request, response, "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/issue/labelsform")
    }

    // --- 분류 ---

    /** 분류 목록이다. */
    @GetMapping("/{user}/{project}/issue/label/categories")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun categories(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        if (!HttpPreference.acceptsType(request.getHeader(HttpHeaders.ACCEPT), "application/json")) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        request.setAttribute(dev.yona.web.JsonCharsetFilter.RAW_ATTRIBUTE, true)
        return json(HttpStatus.OK, labelService.categories(project).map { categoryMap(it) })
    }

    /** 분류 하나이다. */
    @GetMapping("/{user}/{project}/issue/label/category/{id}")
    @IsAllowed(Operation.READ, ResourceType.ISSUE_LABEL_CATEGORY)
    @Transactional(readOnly = true)
    fun category(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("id") id: Long, request: HttpServletRequest): Any {
        if (!HttpPreference.acceptsType(request.getHeader(HttpHeaders.ACCEPT), "application/json")) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        val category = categories.findById(id).orElse(null) ?: return ErrorViews.notFound()
        request.setAttribute(dev.yona.web.JsonCharsetFilter.RAW_ATTRIBUTE, true) // v1.6는 Content-Type을 직접 정해 문자 집합이 없다
        return json(HttpStatus.OK, categoryMap(category))
    }

    /** 분류의 이름과 단일 선택 여부를 바꾼다. 같은 프로젝트의 다른 분류와 이름이 같으면 400이다. */
    @PutMapping("/{user}/{project}/issue/label/category/{id}")
    @IsAllowed(Operation.UPDATE, ResourceType.ISSUE_LABEL_CATEGORY)
    @Transactional
    fun updateCategory(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("id") id: Long, request: HttpServletRequest): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        val errors = categoryErrors(request)
        if (errors.isNotEmpty()) return errorsJson(errors)
        val categoryName = request.getParameter("name")
        if (labelService.categories(project).any { it.name == categoryName && it.id != id }) return errorsJson(emptyMap())

        val category = categories.findById(id).orElse(null) ?: return ErrorViews.notFound()
        category.name = categoryName
        category.isExclusive = toBoolean(request.getParameter("isExclusive"))
        categories.save(category)
        return ResponseEntity.ok().build<Void>()
    }

    /** 분류를 만든다. 같은 이름이 있으면 204, 만들었으면 201이다. */
    @PostMapping("/{user}/{project}/issue/label/categories")
    @IsCreatable(ResourceType.ISSUE_LABEL_CATEGORY)
    @Transactional
    fun newCategory(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        if (categoryErrors(request).isNotEmpty()) return ResponseEntity.badRequest().build<Void>()
        val category = labelService.createCategory(project, request.getParameter("name"), toBoolean(request.getParameter("isExclusive")))
            ?: return ResponseEntity.noContent().build<Void>()
        if (!HttpPreference.acceptsType(request.getHeader(HttpHeaders.ACCEPT), "application/json")) return ResponseEntity.status(HttpStatus.CREATED).build<Void>()
        request.setAttribute(dev.yona.web.JsonCharsetFilter.RAW_ATTRIBUTE, true) // v1.6는 Content-Type을 직접 정해 문자 집합이 없다
        return json(HttpStatus.CREATED, categoryMap(category))
    }

    /** 분류를 지운다. 속한 라벨도 함께 지운다. */
    @DeleteMapping("/{user}/{project}/issue/label/category/{id}")
    @IsAllowed(Operation.DELETE, ResourceType.ISSUE_LABEL_CATEGORY)
    @Transactional
    fun deleteCategory(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("id") id: Long): Any {
        val category = categories.findById(id).orElse(null) ?: return ErrorViews.notFound()
        labelService.deleteCategory(category)
        return ResponseEntity.ok().build<Void>()
    }

    // --- 도우미 ---

    private fun findProject(owner: String, name: String): Project? = projectService.findByOwnerAndProjectName(owner, name)

    /** 프로젝트의 라벨을 분류별로 묶고 각 항목에 현재 사용자의 수정, 삭제 권한을 더한다. */
    private fun groups(project: Project): List<LabelGroup> {
        val viewer = currentUserProvider.get().user
        return labelService.labels(project).groupBy { requireNotNull(it.category) }.map { (category, items) ->
            LabelGroup(
                category = CategoryItem(requireNotNull(category.id), category.name.orEmpty(), category.isExclusive == true),
                canUpdate = accessControl.isAllowed(viewer, category.asResource(), Operation.UPDATE),
                labels = items.map {
                    LabelItem(
                        requireNotNull(it.id), it.name.orEmpty(), it.color.orEmpty(),
                        canDelete = accessControl.isAllowed(viewer, it.asResource(), Operation.DELETE),
                        canUpdate = accessControl.isAllowed(viewer, it.asResource(), Operation.UPDATE)
                    )
                }
            )
        }
    }

    private fun labelsResource(project: Project) = ProjectResource(requireNotNull(project.id).toString(), ResourceType.ISSUE_LABEL, project)

    /** v1.6은 `HashMap`에 넣어 JSON으로 내보내므로 필드 순서가 해시 순서이다(`color, name, id, category, categoryId`). */
    private fun labelMap(label: IssueLabel): Map<String, String> = HashMap<String, String>().also {
        it["id"] = label.id.toString(); it["name"] = label.name.orEmpty(); it["color"] = label.color.orEmpty()
        it["category"] = label.category?.name.orEmpty(); it["categoryId"] = label.category?.id.toString()
    }

    private fun categoryMap(category: IssueLabelCategory): Map<String, String> = HashMap<String, String>().also {
        it["id"] = category.id.toString(); it["name"] = category.name.orEmpty(); it["isExclusive"] = (category.isExclusive == true).toString()
    }

    /** 분류 입력의 검사이다. 이름이 필요하고 255자를 넘지 않아야 하며, 화면이 보내는 `project.id`가 있어야 한다. */
    private fun categoryErrors(request: HttpServletRequest): Map<String, List<String>> {
        val errors = HashMap<String, MutableList<String>>()  // v1.6 Form.errors()는 HashMap이라 해시 순서이다
        val categoryName = request.getParameter("name")?.ifEmpty { null }
        if (categoryName == null) errors.reject("name", "label.error.categoryName.empty")
        else if (categoryName.length > MAX_LENGTH) errors.reject("name", "label.error.categoryName.tooLongSize")
        if (request.getParameter("project.id").isNullOrEmpty()) errors.reject("project", "error.required")
        return errors
    }

    private fun MutableMap<String, MutableList<String>>.reject(field: String, key: String) {
        getOrPut(field) { mutableListOf() } += key
    }

    private fun errorsJson(errors: Map<String, List<String>>): ResponseEntity<*> {
        val locale = LocaleContextHolder.getLocale()
        return json(HttpStatus.BAD_REQUEST, errors.mapValues { (_, keys) -> keys.map { messages.getMessage(it, null, it, locale) } })
    }

    private fun json(status: HttpStatus, body: Any): ResponseEntity<*> =
        ResponseEntity.status(status).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(body)

    private fun toBoolean(value: String?): Boolean = value?.lowercase() in setOf("true", "on", "yes", "1")

    private companion object {
        /** 이름의 최대 길이이다. 안내 문구는 250자라고 하지만 v1.6 검사는 255자이다. */
        const val MAX_LENGTH = 255
    }
}

/** 한 분류와 그 분류에 속한 라벨이다. */
data class LabelGroup(val category: CategoryItem, val canUpdate: Boolean, val labels: List<LabelItem>)

/** 화면에 보일 분류이다. */
data class CategoryItem(val id: Long, val name: String, val exclusive: Boolean)

/** 화면에 보일 라벨과 현재 사용자의 권한이다. */
data class LabelItem(val id: Long, val name: String, val color: String, val canDelete: Boolean, val canUpdate: Boolean)
