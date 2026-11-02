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
class SiteSecurityController(private val settings: SiteSettingService) {

    @GetMapping("/sites/security")
    fun security(): ModelAndView = ModelAndView("site/security").addObject("title", "site.sidebar.security").addObject("enforcement", settings.twoFactorEnforcement().name)
        .addObject("oauthCimd", settings.oauthCimdEnabled()).addObject("oauthUserApps", settings.oauthUserAppsEnabled())
        .addObject("mcpEnabled", settings.mcpEnabled()).addObject("mcpWrite", settings.mcpWriteEnabled())

    /** 강제 방식을 바꾸고 보안 설정 화면으로 돌아간다. 알 수 없는 값은 400이다. */
    @PostMapping("/sites/security")
    fun update(request: HttpServletRequest, response: HttpServletResponse): Any {
        val value = TwoFactorEnforcement.entries.firstOrNull { it.name == request.getParameter("twoFactorEnforcement") } ?: return ErrorViews.badRequest()
        settings.setTwoFactorEnforcement(value)
        return RedirectSupport.see(request, response, "/sites/security", mapOf(RedirectSupport.SUCCESS to "site.security.saved"))
    }
}
