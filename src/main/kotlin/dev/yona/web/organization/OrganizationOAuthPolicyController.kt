package dev.yona.web.organization

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.oauth2server.ApprovalState
import dev.yona.domain.oauth2server.OAuthOrgPolicyService
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.OAuthAppViewSupport
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 조직 설정의 "OAuth 앱 정책" 탭이다(추가 개발 항목 X-39, `decisions.md` D-78). 조직 관리자(와 사이트 관리자)가 제한을 켜고 끄며,
 * 조직 구성원이 승인한 적이 있는 앱을 앱마다 승인하거나 거부한다. 제한한 조직에서는 승인하지 않은 앱의 OAuth 토큰이 그 조직의 비공개 자원에 닿지 못한다.
 */
@Controller
@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
class OrganizationOAuthPolicyController(
    private val organizationService: OrganizationService,
    private val orgViews: OrganizationViewFactory,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val users: UserRepository,
    private val policy: OAuthOrgPolicyService,
    private val support: OAuthAppViewSupport
) {

    /** 정책 화면의 앱 한 줄이다. [state]는 `APPROVED`, `DENIED`, 대기이면 `PENDING`이다. */
    data class Row(val clientRowId: Long, val name: String, val clientId: String, val ownerLabel: String, val ownerOrganization: Boolean, val members: Int, val state: String)

    @GetMapping("/organizations/{organizationName}/oauth-policy")
    @Transactional(readOnly = true)
    fun form(@PathVariable organizationName: String): Any {
        val organization = guard(organizationName) ?: return notFoundOrForbidden(organizationName)
        val rows = policy.candidates(organization).map { Row(requireNotNull(it.client.id), it.client.name.orEmpty(), it.client.clientId.orEmpty(), support.ownerLabel(it.client), support.ownerIsOrganization(it.client), it.members, it.state?.name ?: "PENDING") }
        return ModelAndView("organization/oauth-policy").addObject("title", organization.name).addObject("orgView", orgViews.of(organization))
            .addObject("restricted", policy.restricted(requireNotNull(organization.id))).addObject("rows", rows)
    }

    @PostMapping("/organizations/{organizationName}/oauth-policy")
    @Transactional
    fun save(@PathVariable organizationName: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val organization = guard(organizationName) ?: return notFoundOrForbidden(organizationName)
        policy.setRestricted(organization, request.getParameter("restricted") == "true", actor())
        return RedirectSupport.see(request, response, "/organizations/${organization.name}/oauth-policy", mapOf(RedirectSupport.SUCCESS to "oauth.policy.saved"))
    }

    @PostMapping("/organizations/{organizationName}/oauth-policy/apps/{clientRowId}")
    @Transactional
    fun decide(@PathVariable organizationName: String, @PathVariable clientRowId: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val organization = guard(organizationName) ?: return notFoundOrForbidden(organizationName)
        val state = when (val value = request.getParameter("state")) {
            "PENDING" -> null
            else -> ApprovalState.entries.firstOrNull { it.name == value } ?: return ErrorViews.badRequest()
        }
        if (!policy.decide(organization, clientRowId, state, actor())) return ErrorViews.notFound()
        return RedirectSupport.see(request, response, "/organizations/${organization.name}/oauth-policy", mapOf(RedirectSupport.SUCCESS to "oauth.policy.decided"))
    }

    private fun guard(name: String): Organization? {
        val organization = organizationService.findByName(name) ?: return null
        return organization.takeIf { accessControl.isAllowed(currentUserProvider.get().user, it.asResource(), Operation.UPDATE) }
    }

    private fun notFoundOrForbidden(name: String): ModelAndView =
        organizationService.findByName(name)?.let { ErrorViews.forbidden("error.forbidden", orgViews.of(it)) } ?: ErrorViews.notFound("organization.member.unknownOrganization")

    private fun actor(): User = users.findById(requireNotNull(currentUserProvider.get().user?.id)).get()
}
