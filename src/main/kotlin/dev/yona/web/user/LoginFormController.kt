package dev.yona.web.user

import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 로그인 화면이다. v1.6 `UserApp.loginForm`(`app/controllers/UserApp.java:132-152`)에 해당한다.
 *
 * - 이미 로그인했으면 홈으로 보낸다.
 * - 이동 대상(`redirectUrl`)이 없고 Referer가 이 화면 자신이 아니면 Referer를 이동 대상으로 삼는다.
 * - 응답은 캐시하지 않는다.
 *
 */
@Controller
class LoginFormController(
    private val currentUserProvider: CurrentUserProvider,
    private val socialSession: dev.yona.web.social.SocialSession,
    private val social: dev.yona.domain.social.SocialAccountService,
    private val loginSession: dev.yona.security.LoginSessionService,
    private val twoFactorLogin: dev.yona.security.TwoFactorLogin,
    private val socialProviders: dev.yona.domain.social.SocialProviders
) {

    @GetMapping("/users/loginform")
    fun loginForm(request: HttpServletRequest, response: HttpServletResponse): Any {
        noCache(response)
        if (!currentUserProvider.get().isAnonymous) {
            return RedirectSupport.see(request, response, "/")
        }

        // 이동 대상은 이 사이트 안의 경로만 쓴다. v1.6은 값을 검사하지 않아 외부 주소로 보낼 수 있었다(R-59-10)
        var redirectUrl = RedirectSupport.localTarget(request.getParameter("redirectUrl"), request)
        val referer = request.getHeader(HttpHeaders.REFERER)
        // v1.6은 절대 주소인 Referer를 경로 문자열과 비교해 항상 달랐고, 로그인 화면 자신이 이동 대상이 되었다(C142). 주소의 경로로 비교한다
        if (redirectUrl.isNullOrEmpty() && pathOf(referer) != LOGIN_FORM_PATH) {
            redirectUrl = RedirectSupport.localTarget(referer, request)
        }
        // 소셜 로그인은 마쳤지만 로컬 세션이 없는 상태이면 연결된 사용자를 이어받는다(v1.6 `linkWithExistedOrCreateLocalUser`).
        // 이동할 주소가 없으면 v1.6은 오류가 난다. 홈으로 보낸다([refactoring-backlog.md] R-59).
        socialSession.current(request)?.takeIf { !socialProviders.blockedByEnterpriseLogin() }?.let { current ->
            val resumed = social.resume(dev.yona.domain.social.SocialIdentity(current.provider, current.providerUserId, null, null)).user
            if (resumed != null && twoFactorLogin.required(resumed, request)) {
                twoFactorLogin.begin(request, resumed, "social", true, if (redirectUrl.isNullOrEmpty()) "/" else redirectUrl)
                return RedirectSupport.see(request, response, dev.yona.security.TwoFactorLogin.CHALLENGE_PATH)
            }
            resumed?.let { loginSession.start(request, response, it) }
            return RedirectSupport.see(request, response, if (redirectUrl.isNullOrEmpty()) "/" else redirectUrl)
        }
        return loginPage(redirectUrl)
    }

    /** 절대 주소나 경로에서 경로 부분을 뽑는다. 주소로 읽을 수 없으면 값 그대로이다. */
    private fun pathOf(location: String?): String? = location?.let { runCatching { java.net.URI(it).path }.getOrNull() ?: it }

    private fun noCache(response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader(HttpHeaders.EXPIRES, "0")
    }

    companion object {
        const val LOGIN_FORM_PATH = "/users/loginform"

        /**
         * 로그인 화면을 그린다. 입력이 빠진 로그인 요청의 400 응답과, 로그인해야 볼 수 있는 화면의 403 응답에서도 쓴다.
         *
         * @param titleKey 문서 제목으로 쓸 메시지 키
         */
        fun loginPage(redirectUrl: String?, titleKey: String = "title.login"): ModelAndView =
            ModelAndView("user/login").addObject("redirectUrl", redirectUrl ?: "").addObject("title", titleKey)
    }
}
