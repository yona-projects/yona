package dev.yona.sso

import dev.yona.domain.sso.SsoSettingsService
import dev.yona.util.SiteUrls
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/**
 * SSO 로그인의 시작 주소이다(X-16, `decisions.md` D-80 14번, 23번). 앱의 로그인 단추가 `/sso/oidc/login`을 열고, 회사 포털(IdP)의 아이콘은 `/sso/oidc/initiate`(OIDC의 로그인 시작 주소,
 * Core 4절)를 연다. 둘 다 돌아갈 곳과 시작 방식을 세션에 두고 스프링 시큐리티의 인가 요청 주소로 보내 **평소와 같은 인가 코드 흐름**을 새로 시작한다(요청 없이 도착한 토큰을 받는 길은 없다).
 */
@Controller
class SsoLoginController(private val settings: SsoSettingsService, private val siteUrls: SiteUrls) {

    /** 앱에서 시작하는 로그인이다. 꺼져 있으면 404이다. */
    @GetMapping("/sso/oidc/login")
    @ResponseBody
    fun login(@RequestParam(required = false) returnTo: String?, request: HttpServletRequest): ResponseEntity<Void> {
        if (!enabled()) return ResponseEntity.notFound().build()
        remember(request, SsoReturn.resolve(returnTo, siteUrls.origin()), null, initiated = false)
        return redirect()
    }

    /**
     * 회사 포털이 여는 로그인 시작 주소이다. 설정으로 켠 곳만 받고(꺼져 있으면 404), `iss`가 설정한 발급자와 같아야 한다(아니면 400). `login_hint`는 IdP에 알리고
     * `target_link_uri`는 같은 사이트의 주소만 돌아갈 곳으로 받는다.
     */
    @GetMapping("/sso/oidc/initiate")
    @ResponseBody
    fun initiate(
        @RequestParam(required = false) iss: String?, @RequestParam(name = "login_hint", required = false) loginHint: String?,
        @RequestParam(name = "target_link_uri", required = false) targetLinkUri: String?, request: HttpServletRequest
    ): ResponseEntity<Void> {
        val row = settings.oidc()
        if (row.enabled != true || row.initiateEnabled != true) return ResponseEntity.notFound().build()
        if (iss.isNullOrBlank() || iss != row.issuerUri) return ResponseEntity.badRequest().build()
        remember(request, SsoReturn.resolve(targetLinkUri, siteUrls.origin()), loginHint?.take(255)?.takeIf { it.none { c -> c.isISOControl() } }, initiated = true)
        return redirect()
    }

    private fun enabled() = settings.oidc().enabled == true

    private fun remember(request: HttpServletRequest, returnTo: String?, hint: String?, initiated: Boolean) {
        val session = request.getSession(true)
        session.setAttribute(SsoReturn.RETURN_TO, returnTo ?: "/"); session.setAttribute(SsoReturn.INITIATED, initiated)
        if (hint != null) session.setAttribute(SsoReturn.LOGIN_HINT, hint) else session.removeAttribute(SsoReturn.LOGIN_HINT)
    }

    private fun redirect() = ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, siteUrls.path("${SsoClientRegistrations.AUTHORIZE_BASE}/${SsoClientRegistrations.ID}")).build<Void>()
}
