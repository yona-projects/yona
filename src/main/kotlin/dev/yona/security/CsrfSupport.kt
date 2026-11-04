package dev.yona.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청 위조 방지 토큰을 요구할 요청을 가린다(R-02). 읽기 요청(GET, HEAD, TRACE, OPTIONS)과, 브라우저가 아닌 클라이언트가 쓰는 주소는 대상이 아니다.
 *
 * - 토큰 전용 외부 API(`/-_-api`), MCP(`/mcp`), Subversion(`/svn`, `/!svn-fake`), Git(`git-upload-pack`, `git-receive-pack`), Git LFS(`/info/lfs/`), 사내 SSH 인증(`/internal/ssh/`)
 * - `Authorization`이나 `Yona-Token` 헤더가 있는 요청: 다른 사이트의 페이지가 브라우저로 이 헤더를 붙여 보낼 수 없다
 */
object YonaCsrfMatcher : RequestMatcher {

    private val SAFE_METHODS = setOf("GET", "HEAD", "TRACE", "OPTIONS")
    private val EXEMPT_PATHS = listOf(
        Regex("^/-_-api(/.*)?$"), Regex("^/mcp(/.*)?$"), Regex("^/svn(/.*)?$"), Regex("^/!svn-fake(/.*)?$"), Regex("^/internal/ssh/.*$"),
        Regex("^/[^/]+/[^/]+/(git-upload-pack|git-receive-pack)$"), Regex("^.*/info/lfs/.*$")
    )

    override fun matches(request: HttpServletRequest): Boolean {
        if (request.method.uppercase() in SAFE_METHODS) return false
        if (request.getHeader("Authorization") != null || request.getHeader(ApiTokenHeaders.YONA_TOKEN_HEADER) != null) return false
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
        return EXEMPT_PATHS.none { it.matches(path) }
    }
}

/** 토큰을 쿠키(`XSRF-TOKEN`)에 항상 심는다. 화면 스크립트가 이 쿠키를 읽어 요청에 싣는다. 보안 기본 설정은 토큰을 쓸 때만 만들기 때문에 매 요청에서 읽어 만든다. */
class CsrfCookieFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        (request.getAttribute(CsrfToken::class.java.name) as? CsrfToken)?.token
        chain.doFilter(request, response)
    }
}
