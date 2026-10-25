package dev.yona.web.svn

import dev.yona.security.SiteSecret
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * `/svn/…` 주소의 모든 요청을 [SvnDavService]로 보낸다. 스프링 MVC의 디스패처 서블릿은 `PROPFIND`, `MKACTIVITY` 같은 WebDAV 메서드를
 * 처리하지 못하므로(`501`) 컨트롤러가 아니라 필터로 가로챈다. v1.6은 `Global.onRouteRequest`에서 WebDAV 메서드를 가로챘고 `/svn` 아래의 경로는
 * 라우트로 보냈다. 보안 필터 뒤에서 실행하므로 세션과 토큰으로 식별한 사용자를 쓸 수 있다. 비밀 키를 정하기 전에는 가로채지 않는다.
 */
@Component
class SvnDavFilter(private val dav: SvnDavService, private val secret: SiteSecret) : OncePerRequestFilter(), Ordered {

    override fun getOrder(): Int = 0

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        secret.invalid || !request.requestURI.startsWith(request.contextPath + "/svn/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        dav.service(request, response)
    }
}
