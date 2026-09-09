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
            // `/internal/ssh/**`(SshInternalController)는 시스템 sshd의 AuthorizedKeysCommand
            // 훅이 호출하는 JSON API다. 브라우저 세션이 아니라 로컬 머신 프로세스 간 호출이라
            // 302로 HTML 설정 페이지에 리다이렉트하는 게 아무 의미가 없고, 호출자(Go CLI)가 JSON을
            // 기대하는데 HTML 리다이렉트를 받으면 오동작한다 — 이 컨트롤러 자체의 루프백 주소 +
            // 공유 시크릿 검사가 인가를 담당한다.
            uri.startsWith("/internal/") ||
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
