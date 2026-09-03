package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class BootstrapSetupInterceptor(
    private val userRepository: UserRepository
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val uri = request.requestURI

        // 1. 초기 관리자 생성 주소, 정적 리소스, 에러 페이지 등은 리다이렉션에서 제외
        if (uri == "/bootstrap-setup" ||
            uri.startsWith("/css/") ||
            uri.startsWith("/js/") ||
            uri.startsWith("/images/") ||
            uri.startsWith("/bootstrap/") ||
            uri.startsWith("/stylesheets/") ||
            uri.startsWith("/javascripts/") ||
            uri.startsWith("/webjars/") ||
            uri == "/error" ||
            uri == "/favicon.ico"
        ) {
            return true
        }

        // 2. DB에 등록된 회원수가 0명인 경우 무조건 /bootstrap-setup 으로 이동
        if (userRepository.count() == 0L) {
            response.sendRedirect("/bootstrap-setup")
            return false
        }

        return true
    }
}
