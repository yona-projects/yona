package dev.yona.web.svn

import dev.yona.web.error.ErrorViews
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 사이트 비밀 키를 정하기 전의 `/svn/` 아래 요청을 받는 자리이다.
 *
 * v1.6 `Global.onRouteRequest`는 비밀 키가 기본값이면 `SvnApp` 액션을 포함한 모든 액션을 가로채 기본 관리자 계정 입력 화면을 보인다.
 * [SvnDavFilter]는 비밀 키가 정해지기 전에는 요청을 가로채지 않으므로, 요청이 컨트롤러까지 와야 `SiteSecretInterceptor`가 같은 화면을 낸다.
 * 비밀 키가 정해진 뒤에는 필터가 먼저 처리해 이 메서드에 닿지 않는다.
 */
@Controller
class SvnSecretGateController {

    @RequestMapping("/svn/**")
    fun gate(): Any = ErrorViews.notFound()
}
