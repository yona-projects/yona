package dev.yona.security

import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.site.SiteUserService
import dev.yona.domain.site.TwoFactorEnforcement
import dev.yona.domain.twofactor.TwoFactorService
import dev.yona.web.RedirectSupport
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 관리자가 정한 2단계 인증 강제(추가 개발 항목 X-2, `decisions.md` D-69)를 적용한다. 강제 대상(전체 또는 사이트 관리자)이 2단계 인증을 켜지 않았으면, 로그인한 직후부터 등록 화면
 * (`/user/editform/two-factor`)으로 보낸다. 등록과 로그아웃에 필요한 주소, 관리자가 강제를 되돌릴 수 있는 보안 설정 화면(`/sites/security`), 그리고 정적 자원, 메시지 스크립트는 막지 않는다.
 * 토큰으로 인증한 요청(외부 API, Git과 SVN의 HTTP)은 화면을 거치지 않으므로 대상이 아니다. 익명 요청도 대상이 아니다.
 */
@Component
class TwoFactorEnforcementInterceptor(
    private val settings: SiteSettingService,
    private val twoFactor: TwoFactorService,
    @Lazy private val siteUsers: SiteUserService,
    private val users: dev.yona.domain.user.UserRepository
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication as? YonaAuthentication ?: return true
        if (authentication.viaToken || isExempt(request)) return true
        val enforcement = settings.twoFactorEnforcement()
        if (enforcement == TwoFactorEnforcement.OFF) return true
        val user = users.findById(authentication.principal.userId).orElse(null) ?: return true
        if (enforcement == TwoFactorEnforcement.ADMINS && !siteUsers.isSiteManager(user)) return true
        if (twoFactor.isEnabled(user)) return true
        RedirectSupport.saveFlash(request, response, mapOf(RedirectSupport.WARNING to "twoFactor.enforced"))
        response.status = HttpStatus.SEE_OTHER.value()
        response.setHeader(HttpHeaders.LOCATION, SETUP_PATH)
        return false
    }

    private fun isExempt(request: HttpServletRequest): Boolean {
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
        return path == SETUP_PATH || path == "/sites/security" || path.startsWith("/user/two-factor/") || path == "/logout" || path.startsWith("/users/") || path.startsWith("/assets/") ||
            path == "/messages.js" || path == "/favicon.ico" || path.startsWith("/-_-api")
    }

    companion object {
        /** 강제 대상을 보내는 등록 화면이다. */
        const val SETUP_PATH = "/user/editform/two-factor"
    }
}
