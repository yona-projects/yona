package dev.yona.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 컨트롤러가 처리하는 모든 응답에 `Cache-Control: no-cache`를 먼저 붙인다. v1.6 `Global.getDefaultAction`이 요청마다 하던 일이다
 * (`ctx.response().setHeader("Cache-Control", "no-cache")`). 특정 응답이 다른 값을 정하면(로그인 화면의 `no-cache, no-store, must-revalidate` 등)
 * 컨트롤러가 나중에 덮어쓴다.
 */
@Component
class DefaultHeadersInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        response.setHeader("Cache-Control", "no-cache")
        return true
    }
}
