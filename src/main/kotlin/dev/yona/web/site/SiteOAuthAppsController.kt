package dev.yona.web.site

import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.oauth2server.OAuthConsentRepository
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.UserRepository
import dev.yona.web.RedirectSupport
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.SiteManagerOnly
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
 * 사이트 관리의 OAuth 앱 화면이다(추가 개발 항목 X-1, `decisions.md` D-71). 사이트 전체의 앱(사용자가 만든 앱과 CIMD 문서에서 가져온 앱)을 소유자와 등록 방식과 승인한 사용자 수와 함께 보이고,
 * 문제 있는 앱을 지울 수 있다(그 앱의 토큰이 모두 쓸 수 없게 된다). "보안" 화면의 OAuth 설정(CIMD 허용, 사용자의 앱 만들기 허용)도 여기서 받는다. 사이트 관리자만 쓴다.
 */
@Controller
@SiteManagerOnly
@AnonymousCheck
class SiteOAuthAppsController(
    private val apps: OAuthClientService,
    private val consents: OAuthConsentRepository,
    private val users: UserRepository,
    private val organizations: dev.yona.domain.project.OrganizationRepository,
    private val settings: SiteSettingService,
    private val helper: TemplateHelper
) {

    /** 목록의 한 줄이다. [owner]는 사용자가 만든 앱의 소유자 로그인 ID이고, 조직이 소유한 앱은 조직 이름이며([ownerOrganization]이 참), CIMD 앱은 클라이언트 ID의 호스트이다. */
    data class Row(val id: Long, val name: String, val clientId: String, val owner: String, val ownerOrganization: Boolean, val registration: String, val users: Long, val createdText: String, val confidential: Boolean)

    @GetMapping("/sites/oauth-apps")
    @Transactional(readOnly = true)
    fun list(): ModelAndView = ModelAndView("site/oauth-apps").addObject("title", "site.sidebar.oauthApps").addObject("rows", apps.listAll().map(::row))

    @PostMapping("/sites/oauth-apps/{id}/delete")
    @Transactional
    fun delete(@PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        if (!apps.deleteAsAdmin(id)) return ErrorViews.notFound()
        return RedirectSupport.see(request, response, "/sites/oauth-apps")
    }

    /** "보안" 화면의 OAuth 설정을 저장한다. 체크하지 않은 항목은 끈다. */
    @PostMapping("/sites/security/oauth")
    fun saveSettings(request: HttpServletRequest, response: HttpServletResponse): Any {
        settings.setOauthCimdEnabled(request.getParameter("cimd") == "true")
        settings.setOauthUserAppsEnabled(request.getParameter("userApps") == "true")
        return RedirectSupport.see(request, response, "/sites/security", mapOf(RedirectSupport.SUCCESS to "site.security.saved"))
    }

    /** MCP 서버 사용과 쓰기 도구 허용을 저장하고 보안 설정 화면으로 돌아간다(X-6, D-72). 체크하지 않은 항목은 끈다. */
    @PostMapping("/sites/security/mcp")
    fun saveMcpSettings(request: HttpServletRequest, response: HttpServletResponse): Any {
        settings.setMcpEnabled(request.getParameter("enabled") == "true")
        settings.setMcpWriteEnabled(request.getParameter("write") == "true")
        return RedirectSupport.see(request, response, "/sites/security", mapOf(RedirectSupport.SUCCESS to "site.security.saved"))
    }

    private fun row(c: OAuthClient): Row {
        val ofOrganization = c.registration != OAuthClient.CIMD && c.ownerType == OAuthClient.ORGANIZATION
        val owner = when {
            c.registration == OAuthClient.CIMD -> runCatching { java.net.URI(c.clientId.orEmpty()).host }.getOrNull().orEmpty()
            ofOrganization -> c.ownerId?.let { organizations.findById(it).orElse(null)?.name }.orEmpty()
            else -> c.ownerId?.let { users.findById(it).orElse(null)?.loginId }.orEmpty()
        }
        return Row(requireNotNull(c.id), c.name.orEmpty(), c.clientId.orEmpty(), owner, ofOrganization, c.registration.orEmpty(), consents.countByClientRowId(requireNotNull(c.id)), helper.dateString(c.createdDate), c.confidential == true)
    }
}
