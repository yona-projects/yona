package com.github.yonaprojects.yona.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView

// web/*.kt 20개 파일에 `return "error/404"`가 91곳 있는데, 대부분 응답 상태코드를 직접
// 설정하지 않아 실제로는 200으로 나간다(뷰 이름 문자열만 반환하는 것으로는 상태코드가 안
// 바뀜) — 삭제된 유저/프로젝트 등 404여야 할 응답이 SEO/모니터링/API 클라이언트에는
// "정상 200"으로 보이는 시스템 전역 버그. 91곳을 각각 고치는 대신, Thymeleaf 뷰가
// 실제로 렌더링되기 직전(postHandle)에 뷰 이름이 "error/404"면 상태코드를 일괄 보정한다.
// 이미 올바르게 404를 설정해둔 소수의 호출부(예: UserViewController.verifyUser)는
// response.status가 이미 200이 아니므로 건드리지 않는다. 다른 error/* 뷰(403/400/500 등)는
// 이번 조사 범위 밖이라 손대지 않는다.
@Component
class ErrorViewStatusInterceptor : HandlerInterceptor {

    override fun postHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        modelAndView: ModelAndView?
    ) {
        if (modelAndView?.viewName != "error/404") return
        if (response.isCommitted) return
        if (response.status == HttpServletResponse.SC_OK) {
            response.status = HttpServletResponse.SC_NOT_FOUND
        }
    }
}
