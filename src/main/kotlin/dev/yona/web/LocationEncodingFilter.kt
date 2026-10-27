package dev.yona.web

import dev.yona.util.UrlPaths
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 응답의 `Location` 헤더에서 ASCII 바깥의 글자를 퍼센트 인코딩하고, 사이트 안의 절대 경로에는 컨텍스트 경로(`application.context`)를 붙인다.
 *
 * v1.6(Play 2.3)의 역방향 라우트는 경로 구간을 인코딩해 주소를 만든다. v2는 컨트롤러 여러 곳이 `Location`을 직접 문자열로 만들어
 * 한글 소유자, 프로젝트, 조직 이름이 그대로 들어가면 Tomcat이 헤더를 통째로 지워 브라우저가 이동하지 못한다. 헤더를 쓰는 곳마다 고치는
 * 대신 응답 한 곳에서 보정한다. 이미 인코딩된 값은 그대로 두므로(멱등) 두 번 적용해도 같다([UrlPaths.encodeLocation]).
 */
@Component
class LocationEncodingFilter : OncePerRequestFilter(), Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 10

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        chain.doFilter(request, EncodingResponse(request, response, request.contextPath.orEmpty()))
    }

    private class EncodingResponse(private val request: HttpServletRequest, response: HttpServletResponse, private val contextPath: String) : HttpServletResponseWrapper(response) {
        override fun setHeader(name: String, value: String?) = super.setHeader(name, encode(name, value))
        override fun addHeader(name: String, value: String?) = super.addHeader(name, encode(name, value))

        private fun encode(name: String, value: String?): String? =
            if (value != null && name.equals("Location", ignoreCase = true) && request.getAttribute(RAW_ATTRIBUTE) != true) withContext(UrlPaths.encodeLocation(value)) else value

        /** 사이트 안의 절대 경로(`/`로 시작)에는 컨텍스트 경로를 붙인다. 이미 붙어 있거나 절대 주소(`http://...`)이면 그대로이다. */
        private fun withContext(location: String): String {
            if (contextPath.isEmpty() || !location.startsWith("/") || location.startsWith("//")) return location
            if (location == contextPath || location.startsWith("$contextPath/") || location.startsWith("$contextPath?")) return location
            return contextPath + location
        }
    }

    companion object {
        /** `true`로 두면 이 요청의 `Location`을 고치지 않고 그대로 낸다(v1.6가 인코딩하지 않은 값을 내는 결함 재현용). */
        const val RAW_ATTRIBUTE = "dev.yona.web.LocationEncodingFilter.RAW"
    }
}
