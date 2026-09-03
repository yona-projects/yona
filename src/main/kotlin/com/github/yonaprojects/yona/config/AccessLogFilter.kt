package com.github.yonaprojects.yona.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * yona utils/AccessLogger.java 대응 (P2-48). Apache Combined Log Format으로 매 요청을 로깅한다.
 * legacy는 Global.onRequest()/onError()/onBadRequest()/onHandlerNotFound()에서 예외 없이 항상
 * 이 로그를 남겼으므로, 이 필터도 경로 제외 없이 모든 요청에 적용된다. legacy는 요청 경로별로
 * 별도 Logger("access." + uri)를 동적으로 만들었지만, Spring/Logback에서는 로거 하나로 남기고
 * 필요하면 로그 패턴/MDC로 경로별 라우팅을 하는 쪽이 더 관례적이라 단일 "access" 로거로 이식한다.
 */
@Component
class AccessLogFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val startTimeMillis = System.currentTimeMillis()
        try {
            filterChain.doFilter(request, response)
        } finally {
            val username = SecurityContextHolder.getContext().authentication?.name
            val elapsedMs = System.currentTimeMillis() - startTimeMillis
            val entry = String.format(
                "%s - %s [%s] \"%s %s %s\" %d - %s %s %dms",
                request.remoteAddr,
                orHyphen(username),
                DATE_FORMAT.get().format(java.util.Date()),
                request.method,
                request.requestURI,
                request.protocol,
                response.status,
                quotedOrHyphen(request.getHeader("Referer")),
                quotedOrHyphen(request.getHeader("User-Agent")),
                elapsedMs
            )
            accessLogger.info(entry)
        }
    }

    companion object {
        // OncePerRequestFilter(정확히는 그 상위 GenericFilterBean)가 이미 `logger`라는 이름의
        // protected 필드를 갖고 있어(https://youtrack.jetbrains.com/issue/KT-56386), 같은 이름을
        // 쓰면 Kotlin 컴파일러가 바이트코드를 잘못 생성한다 — 다른 이름으로 피한다.
        private val accessLogger = LoggerFactory.getLogger("access")

        // SimpleDateFormat은 스레드-세이프하지 않으므로 요청마다 새로 만들지 않고 스레드별로 재사용한다.
        private val DATE_FORMAT = ThreadLocal.withInitial {
            java.text.SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z", java.util.Locale.ENGLISH)
        }

        fun orHyphen(value: String?): String = if (value.isNullOrEmpty()) "-" else value

        fun quotedOrHyphen(value: String?): String =
            if (value == null) "-" else "\"" + value.replace("\"", "\\\"") + "\""
    }
}
