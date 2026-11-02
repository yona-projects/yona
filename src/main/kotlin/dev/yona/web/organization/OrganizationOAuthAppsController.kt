package dev.yona.web.organization

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.ClientEdit
import dev.yona.domain.oauth2server.ClientOwner
import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.oauth2server.OAuthScopes
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.OAuthAppUrls
import dev.yona.web.OAuthAppViewSupport
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.user.OAuthAppsSettingsController
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 조직 설정의 "OAuth 앱" 탭이다(추가 개발 항목 X-39, `decisions.md` D-78). 조직이 소유하는 OAuth 앱을 만들고 고치고 비밀 값을 다시 만들고 지운다.
 * 화면과 규칙은 사용자 설정의 앱 화면([OAuthAppsSettingsController])과 같고 소유자만 조직이다. 조직을 고칠 수 있는 사용자(조직 관리자, 사이트 관리자)만 쓸 수 있고,
 * 다른 조직의 앱은 404이다. 관리자 설정으로 앱 만들기를 끄면 조직도 새로 만들 수 없지만 이미 있는 앱은 보고 고치고 지울 수 있다.
 */
@Controller
@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
class OrganizationOAuthAppsController(
    private val organizationService: OrganizationService,
    private val orgViews: OrganizationViewFactory,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val users: UserRepository,
    private val apps: OAuthClientService,
    private val settings: SiteSettingService,
    private val support: OAuthAppViewSupport,
    private val oidc: dev.yona.oauth2server.oidc.OidcSettings
) {

    @GetMapping("/organizations/{organizationName}/oauth-apps")
    @Transactional(readOnly = true)
    fun list(@PathVariable organizationName: String): Any {
        val organization = guard(organizationName) ?: return notFoundOrForbidden(organizationName)
        return base(organization, "organization/oauth-apps").addObject("apps", apps.listFor(ClientOwner.of(organization)).map(support::appRow)).addObject("userAppsEnabled", settings.oauthUserAppsEnabled())
    }

    @GetMapping("/organizations/{organizationName}/oauth-apps/new")
    @Transactional(readOnly = true)
    fun newForm(@PathVariable organizationName: String): Any {
        val organization = guard(organizationName) ?: return notFoundOrForbidden(organizationName)
        if (!settings.oauthUserAppsEnabled()) return ErrorViews.forbidden("error.forbidden", orgViews.of(organization))
        return formView(organization, null, null, OAuthAppsSettingsController.Form())
    }

    @PostMapping("/organizations/{organizationName}/oauth-apps")
    @Transactional
    fun create(@PathVariable organizationName: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val organization = guard(organizationName) ?: return notFoundOrForbidden(organizationName)
        if (!settings.oauthUserAppsEnabled()) return ErrorViews.forbidden("error.forbidden", orgViews.of(organization))
        val form = OAuthAppsSettingsController.Form.of(request)
        return when (val result = apps.createFor(ClientOwner.of(organization), actor(), form.name, form.description, form.homeUrl, form.redirectUris, form.confidential, form.scopes)) {
            is ClientCreation.Rejected -> formView(organization, null, result.messageKey, form).also { it.status = HttpStatus.BAD_REQUEST }
            is ClientCreation.Created ->
                if (result.secret != null) support.noCache(secretView(organization, result.client, result.secret, "oauth.secret.created"), response)
                else RedirectSupport.see(request, response, "${basePath(organization)}/${result.client.id}")
        }
    }

    @GetMapping("/organizations/{organizationName}/oauth-apps/{id}")
    @Transactional(readOnly = true)
    fun editForm(@PathVariable organizationName: String, @PathVariable id: Long): Any {
        val organization = guard(organizationName) ?: return notFoundOrForbidden(organizationName)
        val client = apps.findFor(ClientOwner.of(organization), id) ?: return ErrorViews.notFound()
        return formView(organization, client, null, OAuthAppsSettingsController.Form.of(client))
    }

    @PostMapping("/organizations/{organizationName}/oauth-apps/{id}")
    @Transactional
    fun update(@PathVariable organizationName: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val organization = guard(organizationName) ?: return notFoundOrForbidden(organizationName)
        val owner = ClientOwner.of(organization); val client = apps.findFor(owner, id) ?: return ErrorViews.notFound()
        val form = OAuthAppsSettingsController.Form.of(request)
        return when (val result = apps.updateFor(owner, actor(), id, form.name, form.description, form.homeUrl, form.redirectUris, form.scopes)) {
            is ClientEdit.Rejected -> formView(organization, client, result.messageKey, form).also { it.status = HttpStatus.BAD_REQUEST }
            is ClientEdit.Done -> RedirectSupport.see(request, response, "${basePath(organization)}/$id", mapOf(RedirectSupport.SUCCESS to "oauth.apps.saved"))
        }
    }

    /** 기밀 앱의 비밀 값을 다시 만들어 한 번 보인다. 공개 앱이거나 다른 조직의 앱이면 404이다. */
    @PostMapping("/organizations/{organizationName}/oauth-apps/{id}/secret")
    @Transactional
    fun regenerate(@PathVariable organizationName: String, @PathVariable id: Long, response: HttpServletResponse): Any {
        val organization = guard(organizationName) ?: return notFoundOrForbidden(organizationName)
        val owner = ClientOwner.of(organization); val client = apps.findFor(owner, id) ?: return ErrorViews.notFound()
        val secret = apps.regenerateSecretFor(owner, actor(), id) ?: return ErrorViews.notFound()
        return support.noCache(secretView(organization, client, secret, "oauth.secret.regenerated"), response)
    }

    @PostMapping("/organizations/{organizationName}/oauth-apps/{id}/delete")
    @Transactional
    fun delete(@PathVariable organizationName: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val organization = guard(organizationName) ?: return notFoundOrForbidden(organizationName)
        if (!apps.deleteFor(ClientOwner.of(organization), actor(), id)) return ErrorViews.notFound()
        return RedirectSupport.see(request, response, basePath(organization))
    }

    /** 조직 설정을 고칠 수 있는 사용자일 때만 조직을 돌려준다. */
    private fun guard(name: String): Organization? {
        val organization = organizationService.findByName(name) ?: return null
        return organization.takeIf { accessControl.isAllowed(currentUserProvider.get().user, it.asResource(), Operation.UPDATE) }
    }

    private fun notFoundOrForbidden(name: String): ModelAndView =
        organizationService.findByName(name)?.let { ErrorViews.forbidden("error.forbidden", orgViews.of(it)) } ?: ErrorViews.notFound("organization.member.unknownOrganization")

    private fun actor(): User = users.findById(requireNotNull(currentUserProvider.get().user?.id)).get()

    private fun basePath(organization: Organization) = "${dev.yona.util.UrlPaths.organizationPath(requireNotNull(organization.name))}/oauth-apps"

    private fun base(organization: Organization, template: String) = ModelAndView(template).addObject("title", organization.name)
        .addObject("orgView", orgViews.of(organization)).addObject("urls", OAuthAppUrls.organization(basePath(organization)))

    private fun formView(organization: Organization, client: OAuthClient?, errorKey: String?, form: OAuthAppsSettingsController.Form): ModelAndView = base(organization, "organization/oauth-app-form")
        .addObject("client", client?.let { mapOf("id" to it.id, "clientId" to it.clientId, "confidential" to (it.confidential == true)) }).addObject("form", form).addObject("formError", errorKey)
        .addObject("scopeChoices", OAuthScopes.forApps(oidc.enabled)).addObject("oidcInfo", support.oidcInfo(client))

    private fun secretView(organization: Organization, client: OAuthClient, secret: String, noticeKey: String): ModelAndView =
        base(organization, "organization/oauth-app-secret").addObject("clientName", client.name).addObject("clientId", client.clientId).addObject("secret", secret).addObject("noticeKey", noticeKey).addObject("appId", client.id)
}
