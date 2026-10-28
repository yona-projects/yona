package dev.yona.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.Part
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import dev.yona.web.error.BindFailureException
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * 해석할 수 없는 요청 값을 v1.6(Play 2.3, Netty)처럼 다룬다.
 *
 * - **질의 문자열**: Tomcat 11은 잘못된 UTF-8(`?a=%FF`)이나 이름 없는 값(`?=`)이 있으면 값을 읽을 때 예외를 던져 500이 되지만, Play는 잘못된 UTF-8을 대체 문자로 읽고
 *   이름 없는 값은 건너뛰어 200으로 답한다. 잘못된 퍼센트 열(`?a=%ZZ`, `?a=100%`)만 400이다. 컨테이너가 예외를 던질 때만 질의 문자열을 직접 읽어 같은 규칙으로 값을 만든다.
 * - **멀티파트 본문**: 경계가 없거나 잘려 읽을 수 없는 본문은 Play가 값이 없는 요청으로 보고 각 액션의 검증이 400 화면을 내지만, 스프링은 멀티파트 해석 예외로 500을 낸다.
 *   읽을 수 없으면 멀티파트가 아닌 요청으로 감싼다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
class LenientParametersFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        var target = request
        if (request.contentType?.startsWith("multipart/", ignoreCase = true) == true) {
            try {
                request.parts
            } catch (e: Exception) {
                logger.debug("읽을 수 없는 멀티파트 본문은 값이 없는 요청으로 본다: ${e.message}")
                target = BrokenMultipartRequest(request)
            }
        }
        chain.doFilter(LenientRequest(target), response)
    }

    /** 멀티파트가 아닌, 본문 값이 없는 요청이다. */
    private class BrokenMultipartRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {
        override fun getContentType(): String? = null
        override fun getParts(): Collection<Part> = emptyList()
        override fun getPart(name: String?): Part? = null
    }

    /** 컨테이너가 질의 문자열을 읽지 못하면 직접 읽은 값을 돌려준다. */
    private class LenientRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

        private val fallback: Map<String, Array<String>>? by lazy {
            try {
                super.getParameterMap()
                null
            } catch (e: IllegalStateException) {
                parseQuery(queryString.orEmpty())
            }
        }

        override fun getParameter(name: String?): String? = fallback?.get(name)?.firstOrNull() ?: if (fallback == null) super.getParameter(name) else null
        override fun getParameterValues(name: String?): Array<String>? = fallback?.get(name) ?: if (fallback == null) super.getParameterValues(name) else null
        override fun getParameterNames(): java.util.Enumeration<String> = fallback?.let { java.util.Collections.enumeration(it.keys) } ?: super.getParameterNames()
        override fun getParameterMap(): Map<String, Array<String>> = fallback ?: super.getParameterMap()
    }

    companion object {
        /** Play처럼 `&`로 나누고, 이름이 비어 있는 조각은 건너뛰며, 값은 UTF-8로 읽어 잘못된 바이트는 대체 문자로 둔다. 퍼센트 열이 틀리면 400이다. */
        fun parseQuery(query: String): Map<String, Array<String>> {
            val result = LinkedHashMap<String, MutableList<String>>()
            for (chunk in query.split('&')) {
                if (chunk.isEmpty()) continue
                val eq = chunk.indexOf('=')
                val rawName = if (eq < 0) chunk else chunk.substring(0, eq)
                if (rawName.isEmpty()) continue
                val rawValue = if (eq < 0) "" else chunk.substring(eq + 1)
                result.getOrPut(decode(rawName)) { mutableListOf() } += decode(rawValue)
            }
            return result.mapValues { it.value.toTypedArray() }
        }

        private fun decode(text: String): String {
            val bytes = ByteArrayOutputStream(text.length)
            var i = 0
            while (i < text.length) {
                when (val c = text[i]) {
                    '+' -> { bytes.write(' '.code); i++ }
                    '%' -> {
                        val hex = text.substring(i + 1, minOf(i + 3, text.length))
                        if (hex.length < 2 || !hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) throw BindFailureException("잘못된 퍼센트 열: $text")
                        bytes.write(hex.toInt(16)); i += 3
                    }
                    else -> { c.toString().toByteArray(StandardCharsets.UTF_8).forEach { bytes.write(it.toInt()) }; i++ }
                }
            }
            return String(bytes.toByteArray(), StandardCharsets.UTF_8)
        }
    }
}
