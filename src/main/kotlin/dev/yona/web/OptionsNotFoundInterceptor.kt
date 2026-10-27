package dev.yona.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * `OPTIONS` 요청을 라우트가 없는 요청으로 다룬다. Play 2.3은 `OPTIONS`를 자동으로 처리하지 않고 어느 라우트에도 `OPTIONS`가 없어
 * "Page not found" 화면(`404`)으로 답한다. 스프링은 기본으로 `200`과 `Allow` 헤더로 답하므로 접근 검사보다 먼저 막는다.
 * 오류 화면은 [dev.yona.web.error.GlobalErrorHandler]가 그린다.
 */
@Component
class OptionsNotFoundInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method == "OPTIONS") throw NoResourceFoundException(HttpMethod.OPTIONS, request.requestURI, request.requestURI)
        return true
    }
}

/**
 * `OPTIONS` 요청에 스프링이 `Allow` 헤더를 덧붙이지 못하게 한다. 디스패처 서블릿은 응답에 `Allow`가 없으면 모든 메서드를 나열한 헤더를
 * 붙이는데([OptionsNotFoundInterceptor]의 `404` 화면에도 붙는다), Play 2.3의 `404`에는 없다. 응답이 이미 `Allow`를 가진 것처럼 답해
 * 디스패처 서블릿이 헤더를 붙이는 단계를 건너뛰게 한다.
 */
@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 21)
class OptionsAllowFilter : org.springframework.web.filter.OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: jakarta.servlet.FilterChain) {
        if (request.method != "OPTIONS") return chain.doFilter(request, response)
        chain.doFilter(request, object : jakarta.servlet.http.HttpServletResponseWrapper(response) {
            override fun containsHeader(name: String?): Boolean = name.equals("Allow", ignoreCase = true) || super.containsHeader(name)
        })
    }
}
