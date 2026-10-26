package dev.yona.web

import jakarta.servlet.http.HttpServletRequest

/**
 * Ajax 요청과 PJAX 요청을 구분한다. v1.6 `utils.HttpUtil.isRequestedWithXHR`, `isPJAXRequest`와 같다.
 *
 * `X-Requested-With` 값은 대소문자를 구분하지 않고 `xmlhttprequest`와 비교한다(`XMLHttpRequest`, `xmlhttprequest`, `XMLHTTPREQUEST`가 모두 Ajax이다).
 * `X-PJAX`는 `Boolean.parseBoolean`이라 `true`를 대소문자 구분 없이 받는다.
 */
object AjaxRequests {

    fun isXhr(request: HttpServletRequest): Boolean = request.getHeader("X-Requested-With")?.lowercase() == "xmlhttprequest"

    fun isPjax(request: HttpServletRequest): Boolean = request.getHeader("X-PJAX").toBoolean()
}
