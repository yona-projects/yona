package dev.yona.web.site

import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.site.TwoFactorEnforcement
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.SiteManagerOnly
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 사이트 관리의 보안 설정이다(추가 개발 항목 X-2, `decisions.md` D-69). 2단계 인증을 누구에게 강제할지(끔, 사이트 관리자만, 모두)를 정한다. 사이트 관리자만 쓴다.
 */
@Controller
@SiteManagerOnly
@AnonymousCheck
class SiteSecurityController(private val settings: SiteSettingService, private val oidc: dev.yona.oauth2server.oidc.OidcSettings) {

    @GetMapping("/sites/security")
    fun security(request: HttpServletRequest): ModelAndView = ModelAndView("site/security").addObject("title", "site.sidebar.security").addObject("enforcement", settings.twoFactorEnforcement().name)
        .addObject("oauthCimd", settings.oauthCimdEnabled()).addObject("oauthUserApps", settings.oauthUserAppsEnabled())
        .addObject("mcpEnabled", settings.mcpEnabled()).addObject("mcpWrite", settings.mcpWriteEnabled()).addObject("oidc", oidcStatus(request))

    /** OIDC 제공자의 상태이다(X-40, D-77). 서명 키가 있으면 켜진 것이고 키 번호와 주소를 보여 준다. 키는 파일로만 정하므로 화면에서 바꾸지 않는다. */
    private fun oidcStatus(request: HttpServletRequest): Map<String, Any?> {
        val base = dev.yona.oauth2server.ProtectedResources.base(request)
        val key = oidc.signingKey
        return linkedMapOf("enabled" to (key != null), "kid" to key?.keyID, "algorithm" to dev.yona.oauth2server.oidc.OidcSettings.ALGORITHM.name, "issuer" to base, "discovery" to "$base/.well-known/openid-configuration", "jwks" to "$base/oauth2/jwks")
    }

    /** 강제 방식을 바꾸고 보안 설정 화면으로 돌아간다. 알 수 없는 값은 400이다. */
    @PostMapping("/sites/security")
    fun update(request: HttpServletRequest, response: HttpServletResponse): Any {
        val value = TwoFactorEnforcement.entries.firstOrNull { it.name == request.getParameter("twoFactorEnforcement") } ?: return ErrorViews.badRequest()
        settings.setTwoFactorEnforcement(value)
        return RedirectSupport.see(request, response, "/sites/security", mapOf(RedirectSupport.SUCCESS to "site.security.saved"))
    }
}
