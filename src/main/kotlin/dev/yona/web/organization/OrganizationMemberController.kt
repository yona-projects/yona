package dev.yona.web.organization

import dev.yona.domain.project.OrganizationManagementService
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.user.UserDisplay
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import dev.yona.domain.enumeration.Operation
import dev.yona.access.AccessControl
import dev.yona.access.asResource
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 조직 구성원 관리와 탈퇴이다. v1.6 `OrganizationApp.members`, `addMember`, `editMember`, `deleteMember`, `leave`
 * (`app/controllers/OrganizationApp.java:126-295, 313-336`)에 해당한다.
 *
 * Ajax 요청의 거부 이유는 `200`과 이동할 주소(`{"location": ...}`)로 알리고 안내는 flash로 다음 화면에서 보여 준다.
 */
@Controller
@AnonymousCheck
class OrganizationMemberController(
    private val organizationService: OrganizationService,
    private val management: OrganizationManagementService,
    private val orgViews: OrganizationViewFactory,
    private val currentUserProvider: CurrentUserProvider,
    private val accessControl: AccessControl,
    private val avatars: AvatarService
) {

    /** 구성원 목록의 한 줄이다. */
    data class Row(val userId: Long, val loginId: String, val displayName: String, val avatarUrl: String, val roleName: String)

    /** 가입 신청 목록의 한 줄이다. */
    data class Applicant(val loginId: String, val displayName: String, val avatarUrl: String)

    /** 역할 선택지이다. */
    data class RoleItem(val id: Long, val name: String)

    @GetMapping("/organizations/{organizationName}/members")
    fun members(@PathVariable organizationName: String): Any {
        val organization = organizationService.findByName(organizationName)
            ?: return ErrorViews.notFound("organization.member.unknownOrganization")
        val viewer = currentUserProvider.get().user
        if (!accessControl.isAllowed(viewer, organization.asResource(), Operation.UPDATE)) return ErrorViews.forbidden("error.forbidden", orgViews.of(organization))

        val rows = orgViews.of(organization).let { _ ->
            management.members(organization).mapNotNull { ou ->
                val user = ou.user ?: return@mapNotNull null
                Row(requireNotNull(user.id), user.loginId.orEmpty(), UserDisplay.of(user, viewer?.lang), avatars.avatarUrl(user), ou.role?.name.orEmpty())
            }
        }
        return ModelAndView("organization/members")
            .addObject("title", organization.name)
            .addObject("orgView", orgViews.of(organization))
            .addObject("rows", rows)
            .addObject("roles", management.organizationRoles().map { RoleItem(requireNotNull(it.id), it.name.orEmpty()) })
            .addObject("applicants", management.enrolledUsers(organization).map {
                Applicant(it.loginId.orEmpty(), UserDisplay.of(it, viewer?.lang), avatars.avatarUrl(it))
            })
    }

    @PostMapping("/organizations/{organizationName}/members")
    @Transactional
    fun add(@PathVariable organizationName: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val organization = organizationService.findByName(organizationName)
        val membersUrl = "/organizations/$organizationName/members"
        val denied = management.addMember(organization, currentUserProvider.get().user, request.getParameter("loginId"))
        return RedirectSupport.see(request, response, membersUrl, if (denied != null) mapOf(RedirectSupport.WARNING to denied) else emptyMap())
    }

    @PostMapping("/organizations/{organizationName}/member/{userId}/edit")
    @Transactional
    fun edit(
        @PathVariable organizationName: String,
        @PathVariable userId: Long,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val organization = organizationService.findByName(organizationName)
            ?: return ErrorViews.notFound("organization.member.unknownOrganization")
        val denied = management.editMember(organization, currentUserProvider.get().user, userId, request.getParameter("id"))
        if (denied != null) return rejected(request, response, organizationName, denied)
        return ResponseEntity.noContent().build<Void>()
    }

    @DeleteMapping("/organizations/{organizationName}/member/{userId}/delete")
    @Transactional
    fun delete(
        @PathVariable organizationName: String,
        @PathVariable userId: Long,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val organization = organizationService.findByName(organizationName)
            ?: return ErrorViews.notFound("organization.member.unknownOrganization")
        val viewer = currentUserProvider.get().user
        val denied = management.removeMember(organization, viewer, userId)
        if (denied != null) return rejected(request, response, organizationName, denied)

        val location = if (viewer?.id == userId) "/organizations/$organizationName" else "/organizations/$organizationName/members"
        return json(HttpStatus.OK, mapOf("location" to location))
    }

    @DeleteMapping("/organizations/{organizationName}/member/leave")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun leave(@PathVariable organizationName: String): Any {
        val organization = organizationService.findByName(organizationName)
            ?: return json(HttpStatus.NOT_FOUND, mapOf("errorMsg" to "organization.member.unknownOrganization"))
        val denied = management.leave(organization, requireNotNull(currentUserProvider.get().user))
        if (denied != null) return json(HttpStatus.FORBIDDEN, mapOf("errorMsg" to denied))
        return json(HttpStatus.OK, mapOf("location" to "/organizations/$organizationName"))
    }

    /** 거부 이유를 다음 화면의 안내로 남기고 구성원 화면 주소를 JSON으로 알린다. */
    private fun rejected(request: HttpServletRequest, response: HttpServletResponse, organizationName: String, key: String): ResponseEntity<Map<String, String>> {
        RedirectSupport.saveFlash(request, response, mapOf(RedirectSupport.WARNING to key))
        return json(HttpStatus.OK, mapOf("location" to "/organizations/$organizationName/members"))
    }

    private fun json(status: HttpStatus, body: Map<String, String>): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body)
}
