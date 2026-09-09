package com.github.yonaprojects.yona.config.oauth2server

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

// RFC7591 Dynamic Client Registration은 스펙 특성상 인증 없이
// 열려있는 게 정상이다(공개 클라이언트가 사전 등록 없이 자동으로 자신을 등록하는 것 자체가 목적).
// 다만 이 자체가 이 앱에 새로 추가하는 위험(무제한 클라이언트 스팸 등록으로 인한 오용/DB 소모)이라
// IP당 고정 윈도(fixed window) 카운터로 최소한의 방어를
// 둔다 — 정교한 알고리즘(토큰 버킷 등)은 과도한 설계라 채택하지 않았다.
@Component
class DcrRateLimitFilter : OncePerRequestFilter() {

    private val counters: Cache<String, AtomicInteger> = Caffeine.newBuilder()
        .expireAfterWrite(WINDOW)
        .build()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.method == "POST" && request.requestURI == "/oauth2/register") {
            val key = request.remoteAddr ?: "unknown"
            val count = counters.get(key) { AtomicInteger(0) }.incrementAndGet()
            if (count > MAX_REGISTRATIONS_PER_WINDOW) {
                response.status = 429
                response.contentType = "application/json;charset=UTF-8"
                response.writer.write("{\"error\":\"too_many_requests\"}")
                response.writer.flush()
                return
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private val WINDOW: Duration = Duration.ofMinutes(1)
        private const val MAX_REGISTRATIONS_PER_WINDOW = 10
    }
}
