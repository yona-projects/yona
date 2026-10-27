package dev.yona.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * JSON 응답의 내용 형식에 문자 집합을 붙인다(`application/json;charset=utf-8`). v1.6 Play의 `ok(json)`이 그렇게 내보낸다.
 * 스프링은 JSON에 문자 집합을 붙이지 않는다(RFC 8259는 UTF-8로 정해 두었다). 형식이 정확히 `application/json`일 때만 붙이고,
 * 다른 형식이나 이미 문자 집합이 있는 값은 건드리지 않는다.
 *
 * v1.6에서 문자열 본문에 `Content-Type: application/json`을 직접 정해 돌려주는 액션(`IssueLabelApp.labels` 등)은 문자 집합이 없다.
 * 그런 응답은 요청 속성 [RAW_ATTRIBUTE]를 `true`로 두어 그대로 내보낸다.
 *
 * 같은 감싸기에서 스프링의 파일 내려받기 공격 방지 헤더(`Content-Disposition: inline;filename=f.txt`)도 걸러낸다. 확장자가 있는 주소
 * (`/rawcode/master/README.md`)에 스프링이 붙이는 헤더인데 v1.6에는 없다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class JsonCharsetFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        chain.doFilter(request, object : HttpServletResponseWrapper(response) {
            override fun setContentType(type: String?) = super.setContentType(withCharset(request, type))
            override fun setHeader(name: String, value: String?) {
                if (isRfdHeader(name, value)) return
                super.setHeader(name, if (name.equals("Content-Type", ignoreCase = true)) withCharset(request, value) else value)
            }
            override fun addHeader(name: String, value: String?) {
                if (isRfdHeader(name, value)) return
                super.addHeader(name, if (name.equals("Content-Type", ignoreCase = true)) withCharset(request, value) else value)
            }
        })
    }

    private fun isRfdHeader(name: String, value: String?): Boolean =
        name.equals("Content-Disposition", ignoreCase = true) && value == "inline;filename=f.txt"

    private fun withCharset(request: HttpServletRequest, type: String?): String? =
        if (type != null && type.trim().equals("application/json", ignoreCase = true) && request.getAttribute(RAW_ATTRIBUTE) != true) "application/json;charset=utf-8" else type

    companion object {
        /** `true`이면 JSON 내용 형식에 문자 집합을 붙이지 않는다. */
        const val RAW_ATTRIBUTE = "dev.yona.web.JsonCharsetFilter.raw"
    }
}
