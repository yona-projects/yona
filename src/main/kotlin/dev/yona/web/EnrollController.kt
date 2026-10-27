package dev.yona.web

import dev.yona.security.SessionPermissions
import dev.yona.domain.enumeration.RequestState
import dev.yona.domain.notification.OrganizationMemberRequestEvent
import dev.yona.domain.notification.ProjectMemberRequestEvent
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.DefaultProjectCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

/**
 * 프로젝트와 조직의 가입 신청, 신청 취소이다. v1.6 `EnrollProjectApp`, `EnrollOrganizationApp`에 해당한다.
 *
 * 조직에 속하지 않은(게스트) 로그인 사용자만 신청하거나 취소할 수 있다. 이미 신청한 사용자가 다시 신청하거나
 * 신청하지 않은 사용자가 취소하면 아무것도 하지 않고 성공으로 답한다. 신청과 취소는 이벤트로 알린다(A-25).
 */
@Controller
@AnonymousCheck
class EnrollController(
    private val projectService: ProjectService,
    private val organizationService: OrganizationService,
    private val organizationUsers: OrganizationUserRepository,
    private val permissions: SessionPermissions,
    private val users: UserRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val events: ApplicationEventPublisher
) {

    // --- 프로젝트 ---

    @PostMapping("/{user}/{project}/enroll")
    // v1.6 EnrollProjectApp: 클래스의 @AnonymousCheck(requiresLogin)가 메서드의 @With(DefaultProjectCheckAction) 안쪽이다. 같은 순서로 맞춘다
    @DefaultProjectCheck
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun enrollProject(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): Any {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound()
        val user = requireNotNull(currentUserProvider.get().user)
        if (!projectService.isGuest(project, user)) return ResponseEntity.badRequest().build<Void>()

        if (!users.hasEnrolled(requireNotNull(user.id), requireNotNull(project.id))) {
            val managed = users.findById(user.id!!).get()
            managed.enrolledProjects.add(project)
            users.save(managed)
            events.publishEvent(ProjectMemberRequestEvent(project, managed, RequestState.REQUEST, managed))
        }
        return ResponseEntity.ok().build<Void>()
    }

    @PostMapping("/{user}/{project}/cancel/enroll")
    // v1.6 EnrollProjectApp: 클래스의 @AnonymousCheck(requiresLogin)가 메서드의 @With(DefaultProjectCheckAction) 안쪽이다. 같은 순서로 맞춘다
    @DefaultProjectCheck
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun cancelProjectEnroll(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): Any {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound()
        val user = requireNotNull(currentUserProvider.get().user)
        if (!projectService.isGuest(project, user)) return ResponseEntity.badRequest().build<Void>()

        if (users.hasEnrolled(requireNotNull(user.id), requireNotNull(project.id))) {
            val managed = users.findById(user.id!!).get()
            managed.enrolledProjects.removeIf { it.id == project.id }
            users.save(managed)
            events.publishEvent(ProjectMemberRequestEvent(project, managed, RequestState.CANCEL, managed))
        }
        return ResponseEntity.ok().build<Void>()
    }

    // --- 조직 ---

    @PostMapping("/organizations/{organizationName}/enroll")
    @Transactional
    fun enrollOrganization(@PathVariable organizationName: String, request: HttpServletRequest): Any {
        val organization = organizationService.findByName(organizationName) ?: return ResponseEntity.badRequest().build<Void>()
        val user = currentUserProvider.get().user
        if (!isOrganizationGuest(organization.id, user?.id)) return ResponseEntity.badRequest().build<Void>()

        val orgId = requireNotNull(organization.id)
        if (!users.hasEnrolledOrganization(requireNotNull(user?.id), orgId)) {
            val managed = users.findById(user!!.id!!).get()
            managed.enrolledOrganizations.add(organization)
            users.save(managed)
            events.publishEvent(OrganizationMemberRequestEvent(organization, managed, RequestState.REQUEST, managed))
        }
        return accepted("You enrolled in $organizationName.", organizationName, request)
    }

    @PostMapping("/organizations/{organizationName}/cancel/enroll")
    @Transactional
    fun cancelOrganizationEnroll(@PathVariable organizationName: String, request: HttpServletRequest): Any {
        val organization = organizationService.findByName(organizationName) ?: return ResponseEntity.badRequest().build<Void>()
        val user = currentUserProvider.get().user
        if (!isOrganizationGuest(organization.id, user?.id)) return ResponseEntity.badRequest().build<Void>()

        val orgId = requireNotNull(organization.id)
        if (users.hasEnrolledOrganization(requireNotNull(user?.id), orgId)) {
            val managed = users.findById(user!!.id!!).get()
            managed.enrolledOrganizations.removeIf { it.id == orgId }
            users.save(managed)
            events.publishEvent(OrganizationMemberRequestEvent(organization, managed, RequestState.CANCEL, managed))
        }
        return accepted("You canceled to enroll in $organizationName.", organizationName, request)
    }

    /** 조직에 속하지 않은 로그인 사용자이면 `true`이다. 사이트 관리자와 익명은 해당하지 않는다 (v1.6 `OrganizationUser.isGuest`). */
    private fun isOrganizationGuest(organizationId: Long?, userId: Long?): Boolean {
        if (organizationId == null || userId == null) return false
        return !permissions.siteManager(userId) && !organizationUsers.existsByOrganizationIdAndUserId(organizationId, userId)
    }

    private fun accepted(message: String, organizationName: String, request: HttpServletRequest): ResponseEntity<Map<String, String>> {
        // v1.6는 `response().setHeader("Content-Type", "application/json")`이라 문자 집합이 없다
        request.setAttribute(JsonCharsetFilter.RAW_ATTRIBUTE, true)
        return acceptedBody(message, organizationName, request)
    }

    private fun acceptedBody(message: String, organizationName: String, request: HttpServletRequest): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("message" to message, "statusMonitorUrl" to "http://${host(request)}" + dev.yona.util.UrlPaths.withContext(request.contextPath, "/organizations/$organizationName")))

    /** 요청의 `Host` 헤더 값이다 (v1.6 `request().host()`). 헤더가 없으면 서버 이름과, 기본 포트가 아닐 때 포트이다. */
    private fun host(request: HttpServletRequest): String =
        request.getHeader("Host") ?: if (request.serverPort in listOf(80, 443)) request.serverName else "${request.serverName}:${request.serverPort}"
}
