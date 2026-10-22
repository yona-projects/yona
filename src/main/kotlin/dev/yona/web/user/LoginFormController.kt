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
 * 아직 구현하지 않은 부분: 소셜 로그인 계정 연결 흐름(`PlayAuthenticate.isLoggedIn`).
 */
@Controller
class LoginFormController(private val currentUserProvider: CurrentUserProvider) {

    @GetMapping("/users/loginform")
    fun loginForm(request: HttpServletRequest, response: HttpServletResponse): Any {
        noCache(response)
        if (!currentUserProvider.get().isAnonymous) {
            return RedirectSupport.see(request, response, "/")
        }

        var redirectUrl = request.getParameter("redirectUrl")
        val referer = request.getHeader(HttpHeaders.REFERER)
        if (redirectUrl.isNullOrEmpty() && referer != LOGIN_FORM_PATH) {
            redirectUrl = referer
        }
        return loginPage(redirectUrl)
    }

    private fun noCache(response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader(HttpHeaders.EXPIRES, "0")
    }

    companion object {
        const val LOGIN_FORM_PATH = "/users/loginform"

        /** 로그인 화면을 그린다. 입력이 빠진 로그인 요청의 400 응답에서도 쓴다. */
        fun loginPage(redirectUrl: String?): ModelAndView =
            ModelAndView("user/login").addObject("redirectUrl", redirectUrl ?: "")
    }
}
