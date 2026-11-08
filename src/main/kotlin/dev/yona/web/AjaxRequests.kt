package dev.yona.web

import jakarta.servlet.http.HttpServletRequest

/**
 * Ajax 요청과 htmx 요청을 구분한다.
 *
 * - Ajax: v1.6 `utils.HttpUtil.isRequestedWithXHR`와 같다. `X-Requested-With` 값은 대소문자를 구분하지 않고 `xmlhttprequest`와
 *   비교한다(`XMLHttpRequest`, `xmlhttprequest`, `XMLHTTPREQUEST`가 모두 Ajax이다).
 * - htmx: 목록 화면의 영역 교체 요청이다(`decisions.md` D-98). htmx가 모든 요청에 붙이는 `HX-Request: true`를 `Boolean.parseBoolean`으로
 *   읽어 `true`를 대소문자 구분 없이 받는다. v1.6의 `X-PJAX` 헤더(`isPJAXRequest`)는 더 이상 보지 않는다.
 */
object AjaxRequests {

    fun isXhr(request: HttpServletRequest): Boolean = request.getHeader("X-Requested-With")?.lowercase() == "xmlhttprequest"

    fun isHtmx(request: HttpServletRequest): Boolean = request.getHeader("HX-Request").toBoolean()
}
