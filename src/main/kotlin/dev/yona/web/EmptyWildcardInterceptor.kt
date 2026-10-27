package dev.yona.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 코드 보기 계열의 와일드카드 라우트(`code`, `rawcode`, `image`, `files` 아래의 `:branch` 다음 `*path`)에 끝 슬래시만 있는 주소가 오면 라우트가 없는 것으로 다룬다.
 *
 * Play의 `*path`는 한 글자 이상이라 `code/master/`는 이 라우트가 아니라 맨 아래의 끝 슬래시 제거 라우트(`Application.removeTrailer`)로 가서
 * `code/master`로 303 이동한다. 스프링의 `{*path}`는 빈 값도 받으므로 접근 검사보다 먼저 같은 결과를 내도록 이 인터셉터가 막는다
 * (이동은 [dev.yona.web.error.GlobalErrorHandler]가 한다). `code/!/`, `code/:branch/!/`, `commits/:branch/`는 v1.6에 따로 라우트가 있어 대상이 아니다.
 */
@Component
class EmptyWildcardInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method == "GET" && PATTERN.matches(request.requestURI.removePrefix(request.contextPath))) {
            // v1.6의 끝 슬래시 제거 라우트는 기본 액션 안이라 이동 응답에도 Cache-Control: no-cache가 붙는다
            response.setHeader("Cache-Control", "no-cache")
            throw NoResourceFoundException(HttpMethod.GET, request.requestURI, request.requestURI)
        }
        return true
    }

    private companion object {
        /** `/소유자/프로젝트/code|rawcode|image|files/브랜치/` (브랜치가 `!`인 `code/!/`는 따로 라우트가 있다) */
        val PATTERN = Regex("^/[^/]+/[^/]+/(code/(?!!/)|rawcode/|image/|files/)[^/]+/$")
    }
}
