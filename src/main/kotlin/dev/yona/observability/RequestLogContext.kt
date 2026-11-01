package dev.yona.observability

import dev.yona.security.YonaAuthentication
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/** 요청 번호의 이름과 규칙이다. 로그의 MDC 키, 요청 속성, 감사 기록(`audit_log.request_id`)이 같은 값을 쓴다. */
object RequestIds {

    /** 요청 속성 이름이다. [dev.yona.domain.audit.AuditRecorder]도 이 속성으로 같은 번호를 읽는다. */
    const val ATTRIBUTE = "dev.yona.domain.audit.RequestId"

    /** 로그의 MDC 키이다. */
    const val MDC_KEY = "request_id"

    private const val MAX_LENGTH = 64

    /**
     * 요청의 번호를 돌려준다. 이미 정해졌으면 그 값이고, 아니면 `X-Request-Id` 헤더(64자 이하, 글자와 숫자와 `._-`만)를 쓰거나 새로 만들어 속성에 담는다.
     * 헤더는 앞단(프록시, 수집기)이 정한 번호를 잇기 위한 것이며 로그 위조를 막으려고 허용하는 글자를 제한한다.
     */
    fun of(request: HttpServletRequest): String =
        (request.getAttribute(ATTRIBUTE) as? String) ?: run {
            val supplied = request.getHeader("X-Request-Id")?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH && it.all { c -> c.isLetterOrDigit() || c in "._-" } }
            (supplied ?: UUID.randomUUID().toString()).also { request.setAttribute(ATTRIBUTE, it) }
        }
}

/**
 * 요청마다 요청 번호를 정해 MDC(`request_id`)에 넣는다(로드맵 X-10 C3, `decisions.md` D-64). 구조화 로그(JSON)와 OTLP 로그가 이 값을 담아
 * 감사 기록의 `request_id`에서 그 요청의 로그로 바로 넘어갈 수 있다. 요청이 끝나면(예외가 나도) 지운다. 응답 헤더는 더하지 않는다(v1.6 응답을 바꾸지 않는다).
 */
@Component
class RequestIdFilter : OncePerRequestFilter(), Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 10

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        MDC.put(RequestIds.MDC_KEY, RequestIds.of(request))
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(RequestIds.MDC_KEY)
        }
    }
}

/**
 * 인증된 사용자의 번호를 MDC(`user_id`)에 넣는다. 이름, 로그인 ID, 이메일은 넣지 않는다(D-64 1번). 인증 필터 뒤의 보안 필터 사슬에 둔다.
 * `yona.observability-identity-in-logs`가 꺼져 있으면 넣지 않는다.
 */
class UserLogContextFilter(private val enabled: Boolean) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val userId = if (enabled) (SecurityContextHolder.getContext().authentication as? YonaAuthentication)?.principalInfo?.userId else null
        if (userId == null) {
            chain.doFilter(request, response)
            return
        }
        MDC.put(USER_KEY, userId.toString())
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(USER_KEY)
        }
    }

    companion object {
        const val USER_KEY = "user_id"
    }
}
