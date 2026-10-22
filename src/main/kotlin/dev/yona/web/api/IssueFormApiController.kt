package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.IssueListService
import dev.yona.domain.issue.TitleHeadService
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

/**
 * 이슈 폼의 자동 완성 API이다. v1.6 `ProjectApi.titleHeads`와 `IssueApi.findAssignableUsersOfProject`
 * (`app/controllers/api/ProjectApi.java:527-582`, `app/controllers/api/IssueApi.java:738-800`)에 해당한다.
 *
 * 프로젝트를 읽을 수 있어야 하고 JSON을 받는 요청에만 답한다(아니면 `406`).
 */
@Controller
@AnonymousCheck
class IssueFormApiController(
    private val projectService: ProjectService,
    private val titleHeadService: TitleHeadService,
    private val labelService: IssueLabelService,
    private val listService: IssueListService,
    private val accessControl: AccessControl,
    private val nodesOf: ApiUserNodes,
    private val currentUserProvider: CurrentUserProvider
) {

    /** 제목 머리말 자동 완성 후보이다. 검색어가 들어 있는 머리말 키워드와 프로젝트의 모든 라벨을 `{"result": [...]}`로 돌려준다. */
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/titleHeads")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun titleHeads(
        @PathVariable owner: String, @PathVariable projectName: String,
        @RequestParam(name = "query", defaultValue = "") query: String, request: HttpServletRequest
    ): Any {
        if (!nodesOf.acceptsJson(request)) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val nodes = mutableListOf<Map<String, Any?>>()
        titleHeadService.search(project, query).forEach {
            nodes += linkedMapOf("name" to it.headKeyword, "frequency" to it.frequency, "category" to "", "searchText" to it.headKeyword)
        }
        labelService.labels(project).forEach { label ->
            nodes += linkedMapOf(
                "name" to label.name, "frequency" to 0, "category" to label.category?.name, "categoryId" to label.category?.id, "id" to label.id,
                "labelColor" to label.color, "isExclusive" to (label.category?.isExclusive == true), "searchText" to "${label.name}/${label.category?.name}"
            )
        }
        return nodesOf.json(mapOf("result" to nodes))
    }

    /**
     * 프로젝트의 담당자 후보이다. 검색어가 없으면 "나에게 할당"과 담당할 수 있는 사용자, 있으면 활성 사용자 검색 결과(최대 10명)이다.
     * 비공개 프로젝트의 검색 결과는 프로젝트(와 조직) 구성원으로 좁힌다.
     */
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/assignableUsers")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun assignableUsers(
        @PathVariable owner: String, @PathVariable projectName: String,
        @RequestParam(name = "query", defaultValue = "") query: String,
        @RequestParam(name = "type", required = false) type: String?, request: HttpServletRequest
    ): Any {
        if (!nodesOf.acceptsJson(request)) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val nodes = mutableListOf<Map<String, Any?>>()

        if (query.isEmpty()) {
            viewer?.let { nodesOf.customNode(it.loginId, nodesOf.message("issue.assignToMe"), nodes) }
            listService.assignableUsers(project, viewer, accessControl.isSiteManager(viewer)).forEach { nodesOf.userNode(it, viewer, nodes) }
            return nodesOf.json(nodes)
        }

        val found = nodesOf.search(query, type)
        val shown = found.take(ApiUserNodes.MAX_FETCH_USERS)
        shown.filter { nodesOf.isCandidate(project, it) }.forEach { nodesOf.userNode(it, viewer, nodes) }
        val builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        if (found.size > ApiUserNodes.MAX_FETCH_USERS) builder.header(HttpHeaders.CONTENT_RANGE, "items ${ApiUserNodes.MAX_FETCH_USERS}/${found.size}")
        return builder.body(nodes)
    }

}
