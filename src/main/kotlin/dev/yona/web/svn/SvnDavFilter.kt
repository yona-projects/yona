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
class SvnDavFilter(private val dav: SvnDavService, private val secret: SiteSecret, private val metrics: dev.yona.observability.VcsMetrics) : OncePerRequestFilter(), Ordered {

    override fun getOrder(): Int = 0

    /**
     * `/svn/…` 주소이거나 WebDAV 메서드이면 가로챈다. v1.6 `Global.onRouteRequest`는 메서드가 WebDAV이면 주소와 상관없이 `SvnApp.service`로 보냈다
     * (주소 구간이 3개 미만이면 403, 아니면 두 번째 구간을 소유자로 읽어 프로젝트를 찾아 없으면 404).
     */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        secret.invalid || !(request.requestURI.startsWith(request.contextPath + "/svn/") || SvnDavService.WEBDAV_METHODS.any { it.equals(request.method, ignoreCase = true) })

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        // 서비스 이름은 알려진 WebDAV 메서드로 제한하고(카디널리티), 결과는 응답 상태에서 정한다. 요청 본문 크기는 알 때만(`Content-Length`) 센다(X-10 C5c)
        metrics.request("svn", dev.yona.observability.VcsMetrics.svnServiceOf(request.method)) { req ->
            metrics.transfer("svn", "code", "in", request.contentLengthLong)
            dav.service(request, response)
            req.result = dev.yona.observability.VcsMetrics.resultOf(response.status)
        }
    }
}
