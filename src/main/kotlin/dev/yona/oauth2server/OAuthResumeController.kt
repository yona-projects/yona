package dev.yona.oauth2server

import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * 로그인을 마친 사용자를 보관해 둔 승인 요청으로 돌려보낸다(추가 개발 항목 X-1, `decisions.md` D-71). [OAuthLoginEntryPoint]가 세션에 요청을 보관하고 로그인 화면으로 보낸다.
 * 로그인하지 않았으면 로그인 화면으로 다시 보내고, 보관한 요청이 없으면 홈으로 보낸다. 보관한 값이 `/oauth2/authorize`로 시작하지 않으면 쓰지 않는다(열린 이동 방지).
 */
@Controller
class OAuthResumeController(private val currentUserProvider: CurrentUserProvider) {

    @GetMapping(OAuthLoginEntryPoint.RESUME_PATH)
    fun resume(request: HttpServletRequest, response: HttpServletResponse): Any {
        if (currentUserProvider.get().isAnonymous) return RedirectSupport.see(request, response, "/users/loginform?redirectUrl=" + OAuthLoginEntryPoint.RESUME_PATH)
        val session = request.getSession(false)
        val saved = session?.getAttribute(OAuthLoginEntryPoint.SAVED_REQUEST) as? String
        session?.removeAttribute(OAuthLoginEntryPoint.SAVED_REQUEST)
        if (saved == null || !saved.startsWith("/oauth2/authorize")) return RedirectSupport.see(request, response, "/")
        return RedirectSupport.see(request, response, saved)
    }
}
