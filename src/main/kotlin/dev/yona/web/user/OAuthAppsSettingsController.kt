package dev.yona.web.user

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.oauth2server.AuthorizedApp
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.ClientEdit
import dev.yona.domain.oauth2server.OAuthAuthorizedAppsService
import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.oauth2server.OAuthScopes
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 설정의 "OAuth 앱" 화면이다(추가 개발 항목 X-1, `decisions.md` D-71). Yona가 OAuth 제공자이므로 앱을 만드는 쪽(내가 만든 앱)과 앱을 승인하는 쪽(승인한 앱)을 **한 화면**에서 다룬다(Forgejo와 같다).
 * 앱 만들기와 수정은 따로 있는 화면이다. 기밀 앱의 비밀 값은 만든 직후와 다시 만든 직후에 **한 번만** 보이고(이동 없이 곧바로 그려 주소와 기록에 남지 않게 하며 캐시를 막는다) 다시 볼 수 없다.
 * 앱은 소유자만 고치고 지운다(남의 앱은 404). 관리자 설정으로 사용자의 앱 만들기를 끌 수 있고, 끄면 새로 만들 수 없지만 이미 만든 앱은 보고 지울 수 있다.
 */
@Controller
class OAuthAppsSettingsController(
    private val currentUserProvider: CurrentUserProvider,
    private val users: UserRepository,
    private val apps: OAuthClientService,
    private val authorized: OAuthAuthorizedAppsService,
    private val settings: SiteSettingService,
    private val helper: TemplateHelper,
    private val oidc: dev.yona.oauth2server.oidc.OidcSettings
) {

    /** 내가 만든 앱의 한 줄이다. */
    data class AppRow(val id: Long, val name: String, val clientId: String, val confidential: Boolean, val redirectCount: Int, val createdText: String)

    /** 승인한 앱의 한 줄이다. */
    data class AuthorizedRow(val clientRowId: Long, val name: String, val ownerLoginId: String?, val host: String?, val scopes: List<String>, val approvedText: String)

    @GetMapping("/user/editform/oauth-apps")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun list(): ModelAndView {
        val user = currentUser()
        return base("user/edit-oauth-apps").addObject("apps", apps.list(user).map(::appRow)).addObject("authorized", authorized.list(user).map(::authorizedRow))
            .addObject("userAppsEnabled", settings.oauthUserAppsEnabled())
    }

    @GetMapping("/user/editform/oauth-apps/new")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun newForm(): Any {
        if (!settings.oauthUserAppsEnabled()) return ErrorViews.forbidden()
        return formView(null, null, Form())
    }

    @PostMapping("/user/oauth-apps")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun create(request: HttpServletRequest, response: HttpServletResponse): Any {
        if (!settings.oauthUserAppsEnabled()) return ErrorViews.forbidden()
        val user = currentUser(); val form = Form.of(request)
        return when (val result = apps.create(user, form.name, form.description, form.homeUrl, form.redirectUris, form.confidential, form.scopes)) {
            is ClientCreation.Rejected -> formView(null, result.messageKey, form).also { it.status = HttpStatus.BAD_REQUEST }
            is ClientCreation.Created ->
                if (result.secret != null) noCache(secretView(result.client, result.secret, "oauth.secret.created"), response)
                else RedirectSupport.see(request, response, "/user/editform/oauth-apps/${result.client.id}")
        }
    }

    @GetMapping("/user/editform/oauth-apps/{id}")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional(readOnly = true)
    fun editForm(@PathVariable id: Long): Any {
        val client = apps.find(currentUser(), id) ?: return ErrorViews.notFound()
        return formView(client, null, Form.of(client))
    }

    @PostMapping("/user/oauth-apps/{id}")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun update(@PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val user = currentUser(); val client = apps.find(user, id) ?: return ErrorViews.notFound()
        val form = Form.of(request)
        return when (val result = apps.update(user, id, form.name, form.description, form.homeUrl, form.redirectUris, form.scopes)) {
            is ClientEdit.Rejected -> formView(client, result.messageKey, form).also { it.status = HttpStatus.BAD_REQUEST }
            is ClientEdit.Done -> RedirectSupport.see(request, response, "/user/editform/oauth-apps/$id", mapOf(RedirectSupport.SUCCESS to "oauth.apps.saved"))
        }
    }

    /** 기밀 앱의 비밀 값을 다시 만들어 한 번 보인다. 공개 앱이거나 남의 앱이면 404이다. */
    @PostMapping("/user/oauth-apps/{id}/secret")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun regenerate(@PathVariable id: Long, response: HttpServletResponse): Any {
        val user = currentUser(); val client = apps.find(user, id) ?: return ErrorViews.notFound()
        val secret = apps.regenerateSecret(user, id) ?: return ErrorViews.notFound()
        return noCache(secretView(client, secret, "oauth.secret.regenerated"), response)
    }

    @PostMapping("/user/oauth-apps/{id}/delete")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun delete(@PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        if (!apps.delete(currentUser(), id)) return ErrorViews.notFound()
        return RedirectSupport.see(request, response, "/user/editform/oauth-apps")
    }

    /** 사용자가 승인한 앱의 접근을 취소한다. 승인한 적이 없으면 404이다. */
    @PostMapping("/user/oauth-apps/authorized/{clientRowId}/revoke")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun revoke(@PathVariable clientRowId: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        if (!authorized.revoke(currentUser(), clientRowId)) return ErrorViews.notFound()
        return RedirectSupport.see(request, response, "/user/editform/oauth-apps")
    }

    private fun currentUser(): User = users.findById(requireNotNull(currentUserProvider.get().user?.id)).get()

    private fun base(template: String) = ModelAndView(template).addObject("title", "userinfo.oauth").addObject("tabId", "oauth-apps")

    private fun appRow(c: OAuthClient) = AppRow(requireNotNull(c.id), c.name.orEmpty(), c.clientId.orEmpty(), c.confidential == true, dev.yona.domain.oauth2server.RedirectUris.parse(c.redirectUris).size, helper.dateString(c.createdDate))

    private fun authorizedRow(a: AuthorizedApp) = AuthorizedRow(a.clientRowId, a.name, a.ownerLoginId, a.host, a.scopes, helper.dateString(a.approvedDate))

    private fun formView(client: OAuthClient?, errorKey: String?, form: Form): ModelAndView = base("user/edit-oauth-app-form")
        .addObject("client", client?.let { mapOf("id" to it.id, "clientId" to it.clientId, "confidential" to (it.confidential == true)) }).addObject("form", form).addObject("formError", errorKey)
        .addObject("scopeChoices", OAuthScopes.forApps(oidc.enabled)).addObject("oidcInfo", oidcInfo(client))

    /** OIDC로 로그인을 붙이는 앱 개발자에게 알려 줄 주소이다(X-40, D-77). OIDC가 켜져 있고 이미 만든 앱의 수정 화면일 때만 있다. */
    private fun oidcInfo(client: OAuthClient?): Map<String, String>? {
        if (client == null || !oidc.enabled) return null
        val request = (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() as? org.springframework.web.context.request.ServletRequestAttributes)?.request ?: return null
        val base = dev.yona.oauth2server.ProtectedResources.base(request)
        return linkedMapOf("issuer" to base, "discovery" to "$base/.well-known/openid-configuration")
    }

    private fun secretView(client: OAuthClient, secret: String, noticeKey: String): ModelAndView =
        base("user/edit-oauth-app-secret").addObject("clientName", client.name).addObject("clientId", client.clientId).addObject("secret", secret).addObject("noticeKey", noticeKey).addObject("appId", client.id)

    private fun noCache(view: ModelAndView, response: HttpServletResponse): ModelAndView = view.also {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader(HttpHeaders.EXPIRES, "0")
    }

    /** 만들기와 수정 화면의 입력이다. 오류일 때 입력을 되돌려 주는 데도 쓴다. */
    data class Form(
        val name: String = "", val description: String = "", val homeUrl: String = "", val redirectUris: String = "", val confidential: Boolean = false, val scopes: Set<String> = setOf("read:user")
    ) {
        companion object {
            fun of(request: HttpServletRequest) = Form(
                request.getParameter("name").orEmpty(), request.getParameter("description").orEmpty(), request.getParameter("homeUrl").orEmpty(), request.getParameter("redirectUris").orEmpty(),
                request.getParameter("type") == "confidential", request.getParameterValues("scope")?.toCollection(LinkedHashSet()) ?: linkedSetOf()
            )

            fun of(client: OAuthClient) = Form(
                client.name.orEmpty(), client.description.orEmpty(), client.homeUrl.orEmpty(), client.redirectUris.orEmpty(), client.confidential == true, OAuthScopes.parseAllowed(client.allowedScopes, true)
            )
        }
    }
}
