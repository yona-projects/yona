package dev.yona.web.user

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * 비밀번호 로그인 필터([PasswordLoginFilter])가 맡을 요청을 가린다(로드맵 X-43 3단계, `decisions.md` D-129). `POST /users/login`이고 로그인 ID(`loginIdOrEmail`)와
 * 비밀번호(`password`)가 모두 비어 있지 않을 때만 맡는다. 입력이 빠진 요청은 v1.6처럼 로그인 화면을 400으로 다시 그리거나(폼) 400 JSON으로 알려야(AJAX) 하므로,
 * 화면을 그릴 수 있는 [LoginController]가 맡는다.
 */
class PasswordLoginRequestMatcher : RequestMatcher {

    override fun matches(request: HttpServletRequest): Boolean {
        if (request.method != "POST") return false
        if (request.requestURI.removePrefix(request.contextPath.orEmpty()) != LOGIN_PATH) return false
        return !request.getParameter("loginIdOrEmail").isNullOrEmpty() && !request.getParameter("password").isNullOrEmpty()
    }

    companion object {
        /** 비밀번호 로그인 처리 주소이다. */
        const val LOGIN_PATH = "/users/login"
    }
}
