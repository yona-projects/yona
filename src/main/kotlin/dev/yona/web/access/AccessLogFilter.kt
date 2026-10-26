package dev.yona.web.access

import dev.yona.security.CurrentUserProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.Date

/**
 * 요청마다 접근 로그를 한 줄 남긴다. v1.6 `utils.AccessLogger`와 `Global`의 기본 액션에 해당한다.
 *
 * 로거 이름은 `access`로 고정한다. v1.6은 `access.<URI>`라서 URI마다 로거 객체가 쌓였고, 파일에는 이름이 찍히지 않으므로(`%message%n`)
 * `access.log`의 내용은 같다 (결정 기록 A-nn). 스프링 시큐리티 필터 체인보다 안쪽(`@Order(-50)`)에 두어 사용자를 구할 수 있다.
 * 처리 중 예외가 나면 v1.6 `onError`처럼 사용자와 소요 시간을 `-`로, 상태를 500으로 기록한다.
 */
@Component
@Order(-50)
class AccessLogFilter(private val currentUser: CurrentUserProvider) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger("access")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val start = System.currentTimeMillis()
        try {
            chain.doFilter(request, response)
        } catch (e: Throwable) {
            write(request, null, 500, null)
            throw e
        }
        val user = runCatching { currentUser.get().user?.loginId }.getOrNull()
        write(request, user, response.status, System.currentTimeMillis() - start)
    }

    private fun write(request: HttpServletRequest, user: String?, status: Int, elapsed: Long?) {
        if (!log.isInfoEnabled) return
        val uri = request.requestURI + (request.queryString?.let { "?$it" } ?: "")
        log.info(AccessLogFormat.format(request.remoteAddr, user, Date(), request.method, uri, request.protocol, status,
            request.getHeader("Referer"), request.getHeader("User-Agent"), elapsed))
    }
}
