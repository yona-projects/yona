package dev.yona.web

import dev.yona.config.YonaProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.UriComponentsBuilder
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.Collections

/**
 * 폼 본문이 너무 크면 폼 값을 읽지 못한 것처럼 처리한다. v1.6 Play의 `parsers.text.maxLength`(기본 100KB)에 해당한다.
 *
 * Play는 `application/x-www-form-urlencoded` 본문이 한도를 넘으면 본문을 해석하지 않아 컨트롤러가 빈 폼을 받았다.
 * 이슈와 게시글, 마일스톤은 본문(`body`, `contents`)이 비어 있으면 `413 Request text entity too large` 화면을 보여 주는데,
 * 그 화면이 이 동작에 기대고 있다. 같은 결과를 내려고 한도를 넘는 폼 본문은 비어 있는 것으로 보이게 한다.
 * 쿼리 문자열의 값은 그대로 읽을 수 있다. 파일 올리기(`multipart/form-data`)에는 적용하지 않는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class TextBodyLimitFilter(private val properties: YonaProperties) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val method = request.method
        if (method != HttpMethod.POST.name() && method != HttpMethod.PUT.name() && method != HttpMethod.PATCH.name()) return true
        val contentType = request.contentType?.let { runCatching { MediaType.parseMediaType(it) }.getOrNull() } ?: return true
        return !contentType.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED) || request.contentLengthLong <= properties.parsersTextMaxLength
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        chain.doFilter(QueryOnlyRequest(request), response)
    }

    /** 본문을 비우고 쿼리 문자열의 값만 보여 주는 요청이다. */
    private class QueryOnlyRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

        private val parameters: Map<String, Array<String>> = UriComponentsBuilder.newInstance()
            .query(request.queryString).build().queryParams
            .mapValues { (_, values) -> values.map { java.net.URLDecoder.decode(it, Charsets.UTF_8) }.toTypedArray() }

        override fun getParameter(name: String): String? = parameters[name]?.firstOrNull()
        override fun getParameterValues(name: String): Array<String>? = parameters[name]
        override fun getParameterNames(): java.util.Enumeration<String> = Collections.enumeration(parameters.keys)
        override fun getParameterMap(): Map<String, Array<String>> = parameters
        override fun getContentLength(): Int = 0
        override fun getContentLengthLong(): Long = 0

        override fun getInputStream(): ServletInputStream {
            val empty = ByteArrayInputStream(ByteArray(0))
            return object : ServletInputStream() {
                override fun read(): Int = empty.read()
                override fun isFinished(): Boolean = true
                override fun isReady(): Boolean = true
                override fun setReadListener(listener: ReadListener?) {}
            }
        }

        override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(ByteArrayInputStream(ByteArray(0))))
    }
}
