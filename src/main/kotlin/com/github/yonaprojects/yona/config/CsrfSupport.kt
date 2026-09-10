package com.github.yonaprojects.yona.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.csrf.CsrfTokenRequestHandler
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.util.StringUtils
import java.util.function.Supplier

// PAT/레거시 전권 토큰 헤더(Authorization: token ... / Yona-Token)가 있는 요청만 CSRF 검증에서
// 제외하는 공용 매처 — 캐치올 체인(SecurityConfig)과 레거시 API 체인(LegacyApiSecurityConfig)
// 둘 다에서 재사용한다. 헤더가 있다는 사실만으로 판정하고 토큰 자체의 유효성은 검증하지 않는다
// (유효하지 않으면 CSRF 통과 여부와 무관하게 ApiTokenAuthenticationFilter/컨트롤러가 어차피
// 401/403으로 거절한다).
//
// LegacyApiSecurityConfig(api 이하)뿐 아니라 캐치올 체인에도 이 예외가 필요하다 — PAT 헤더로
// 인증하는 레거시 세션 기반 웹 MVC 경로(예: POST /projects/{owner}/{project}/webhooks,
// ApiTokenAuthenticationFilter의 parseLegacyWebProjectTarget 대상)는 api 접두어가 없어 캐치올
// 체인을 타기 때문이다.
val tokenAuthenticatedRequestMatcher = RequestMatcher { request ->
    ApiTokenAuthenticationFilter.extractToken(request) != null
}

/**
 * `CookieCsrfTokenRepository`(원문 토큰을 XSRF-TOKEN 쿠키에 저장, JS가 직접 읽어 헤더로 전송)와
 * Thymeleaf `th:action` 자동 주입(BREACH 방지를 위해 매 요청 다른 값으로 XOR된 토큰을 히든
 * 필드에 심음)을 같은 앱에서 함께 쓸 때 Spring Security 공식 문서("Integrating CSRF Protection
 * with a Single Page Application")가 권장하는 정확히 그 패턴이다.
 *
 * - 서버 렌더링 폼(`th:action`, `_csrf` 히든 필드): 항상 XOR된 값이 필요하므로 `handle()`은
 *   그대로 `XorCsrfTokenRequestAttributeHandler`에 위임한다(기존 기본 동작과 동일).
 * - AJAX/fetch: JS가 쿠키에서 원문 그대로 읽어 헤더(X-XSRF-TOKEN)에 실어 보낸다 —
 *   `CookieCsrfTokenRepository`는 쿠키에 항상 원문을 저장하므로, 헤더로 들어온 값은 XOR 복원 없이
 *   그대로 비교해야 한다. 헤더 값이 있으면(=AJAX 클라이언트) 순수
 *   `CsrfTokenRequestAttributeHandler`로, 없으면(=폼 파라미터 `_csrf`) 기존 Xor 위임 핸들러로
 *   해석한다.
 *
 * 캐치올 체인(SecurityConfig)과 레거시 API 체인(LegacyApiSecurityConfig) 양쪽에서 재사용한다 —
 * 둘 다 같은 이름/경로의 XSRF-TOKEN 쿠키를 쓰므로(더블 서브밋 쿠키 패턴, 서버 세션 상태 불필요)
 * 체인마다 독립적으로 인스턴스를 둬도 서로 어긋나지 않는다.
 */
class SpaCsrfTokenRequestHandler : CsrfTokenRequestAttributeHandler() {
    private val delegate: CsrfTokenRequestHandler = XorCsrfTokenRequestAttributeHandler()

    override fun handle(request: HttpServletRequest, response: HttpServletResponse, csrfToken: Supplier<CsrfToken>) {
        delegate.handle(request, response, csrfToken)
    }

    override fun resolveCsrfTokenValue(request: HttpServletRequest, csrfToken: CsrfToken): String? {
        val headerValue = request.getHeader(csrfToken.headerName)
        return if (StringUtils.hasText(headerValue)) {
            super.resolveCsrfTokenValue(request, csrfToken)
        } else {
            delegate.resolveCsrfTokenValue(request, csrfToken)
        }
    }
}
