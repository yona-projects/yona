package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.IssueListService
import dev.yona.domain.issue.TitleHeadService
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.HttpPreference
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import jakarta.persistence.EntityManager
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
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
    private val avatars: AvatarService,
    private val currentUserProvider: CurrentUserProvider,
    private val projectUsers: ProjectUserRepository,
    private val organizationUsers: OrganizationUserRepository,
    private val entityManager: EntityManager,
    private val messages: MessageSource
) {

    /** 제목 머리말 자동 완성 후보이다. 검색어가 들어 있는 머리말 키워드와 프로젝트의 모든 라벨을 `{"result": [...]}`로 돌려준다. */
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/titleHeads")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun titleHeads(
        @PathVariable owner: String, @PathVariable projectName: String,
        @RequestParam(name = "query", defaultValue = "") query: String, request: HttpServletRequest
    ): Any {
        if (!acceptsJson(request)) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
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
        return json(mapOf("result" to nodes))
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
        if (!acceptsJson(request)) return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build<Void>()
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val nodes = mutableListOf<Map<String, Any?>>()

        if (query.isEmpty()) {
            viewer?.let { customNode(it, message("issue.assignToMe"), nodes) }
            listService.assignableUsers(project, viewer, accessControl.isSiteManager(viewer)).forEach { userNode(it, viewer, nodes) }
            return json(nodes)
        }

        val found = search(query, type)
        val shown = found.take(MAX_FETCH_USERS)
        shown.filter { isCandidate(project, it) }.forEach { userNode(it, viewer, nodes) }
        val builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        if (found.size > MAX_FETCH_USERS) builder.header(HttpHeaders.CONTENT_RANGE, "items $MAX_FETCH_USERS/${found.size}")
        return builder.body(nodes)
    }

    /** 활성 사용자를 로그인 ID, 이름, 영문 이름에서 찾는다. [type]이 있으면 그 항목이 [query]와 같은 사용자만이다. */
    private fun search(query: String, type: String?): List<User> {
        val where = if (type.isNullOrBlank()) {
            "(lower(u.loginId) like lower(concat('%', :q, '%')) or lower(u.name) like lower(concat('%', :q, '%')) or lower(u.englishName) like lower(concat('%', :q, '%')))"
        } else {
            val field = TYPES[type] ?: return emptyList()
            "u.$field = :q"
        }
        return entityManager.createQuery("select u from User u where u.state = :state and $where order by u.id", User::class.java)
            .setParameter("state", UserState.ACTIVE).setParameter("q", query).resultList
    }

    private fun isCandidate(project: Project, user: User): Boolean {
        if (project.isPublic()) return true
        val userId = requireNotNull(user.id)
        val organizationId = project.organization?.id
        return projectUsers.existsByUserIdAndProjectId(userId, requireNotNull(project.id)) ||
            (organizationId != null && organizationUsers.existsByOrganizationIdAndUserId(organizationId, userId))
    }

    private fun userNode(user: User, viewer: User?, nodes: MutableList<Map<String, Any?>>) {
        val node = linkedMapOf<String, Any?>(
            "loginId" to user.loginId, "name" to UserDisplay.of(user, viewer?.lang), "pureNameOnly" to UserDisplay.pureName(user, viewer?.lang),
            "avatarUrl" to avatars.avatarUrl(user), "type" to "user"
        )
        if (node !in nodes) nodes += node
    }

    private fun customNode(user: User, name: String, nodes: MutableList<Map<String, Any?>>) {
        val node = linkedMapOf<String, Any?>("loginId" to user.loginId, "name" to name, "avatarUrl" to "")
        if (node !in nodes) nodes += node
    }

    private fun acceptsJson(request: HttpServletRequest) = HttpPreference.acceptsType(request.getHeader(HttpHeaders.ACCEPT), "application/json")

    private fun message(key: String) = messages.getMessage(key, null, key, LocaleContextHolder.getLocale()) ?: key

    private fun json(body: Any): ResponseEntity<*> = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)

    private companion object {
        /** 화면의 자동 완성 입력란이 한 번에 보여 주는 개수와 같다 (v1.6 `UserApp.MAX_FETCH_USERS`). */
        const val MAX_FETCH_USERS = 10

        /** `type`으로 지정할 수 있는 항목이다. 화면은 `loginId`만 쓴다([decisions.md] A-24). */
        val TYPES = mapOf("loginId" to "loginId", "name" to "name", "email" to "email", "englishName" to "englishName")
    }
}
