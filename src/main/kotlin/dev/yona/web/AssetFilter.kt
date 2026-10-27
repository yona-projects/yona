package dev.yona.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.UriUtils

/**
 * `/assets/…` 정적 자원과 `/favicon.ico`를 보안 필터와 디스패처보다 먼저 가로채 [StaticAssets]의 v1.6 규칙대로 응답한다. v1.6의 `GET /assets/` 아래 와일드카드 라우트는
 * `conf/routes`의 앞쪽에 있어 `/:user/:project` 같은 뒤의 라우트보다 우선한다. 스프링의 라우트 우선순위로는 그렇게 만들 수 없어(두 구간짜리 `/assets/nope.js`가
 * 프로젝트 홈으로 간다) 필터로 둔다. v1.6의 자원 응답에는 보안 헤더(`X-Frame-Options` 등)도 없으므로 보안 필터보다 앞이다.
 *
 * `/assets/`만 있는 주소는 이 라우트가 아니라 끝 슬래시 제거 라우트로 가므로 `/assets`로 303 이동시킨다.
 */
@Component
class AssetFilter(private val assets: StaticAssets) : OncePerRequestFilter(), Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 10

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val prefix = request.contextPath + "/assets/"
        return request.method !in setOf("GET", "HEAD") || !(request.requestURI.startsWith(prefix) || request.requestURI == request.contextPath + "/favicon.ico")
    }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val prefix = request.contextPath + "/assets/"
        if (request.requestURI == prefix) {
            // v1.6: 자원 라우트에 걸리지 않으므로 끝 슬래시 제거 라우트가 `/assets`로 303 이동시킨다(기본 액션의 Cache-Control 포함)
            response.status = HttpServletResponse.SC_SEE_OTHER
            response.setHeader("Cache-Control", "no-cache")
            response.setHeader("Location", prefix.dropLast(1))
            response.setContentLength(0)
            response.flushBuffer()
            return
        }
        // v1.6 `GET /favicon.ico`는 `Assets.at("/public", "images/favicon.ico")`이다
        val raw = if (request.requestURI == request.contextPath + "/favicon.ico") "images/favicon.ico" else request.requestURI.removePrefix(prefix)
        val relative = try { UriUtils.decode(raw, Charsets.UTF_8) } catch (e: IllegalArgumentException) { "" }
        val entity = assets.serve(relative, request)
        response.status = entity.statusCode.value()
        for (name in entity.headers.headerNames()) for (value in entity.headers[name].orEmpty()) response.addHeader(name, value)
        val body = entity.body
        response.setContentLength(body?.size ?: 0)
        if (request.method == "GET" && body != null) response.outputStream.write(body)
        response.flushBuffer()
    }
}
