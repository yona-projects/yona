package com.github.yonaprojects.yona.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository

// -_-api/v1 이하(레거시 Open API, GlobalApiController/IssueApiController/BoardApiController 등)와
// 레거시 api 이하(구 Play 경로, api/v1 이하는 제외 — 그건 ResourceServerConfig의 Order(3) 체인이
// 이미 전담) 전용 체인.
//
// (경로 표기에 Kotlin 블록 주석 중첩 규칙과 충돌하는 글롭 패턴 문자열을 그대로 쓰지 않는다 —
// "/api/**"처럼 "/*"를 포함한 문자열이 KDoc(/** */) 안에 있으면 중첩 블록 주석으로 해석돼
// "Unclosed comment" 컴파일 에러가 난다. 전부 line comment(//)로 쓴 이유다 — line comment는
// 중첩 규칙의 영향을 받지 않는다.)
//
// 이 경로는 PAT 헤더(yona-cli 등 헤드리스 클라이언트)뿐 아니라 웹 UI 자신도 로그인 세션
// 쿠키로 $.ajax/data-request-method를 통해 직접 호출한다 — site/layout.html(즐겨찾기 토글,
// -_-api/v1/favorite*), issue/view.html(-_-api/v1/owners/.../assignableUsers),
// project/members.html/pullrequest/view.html/board/edit.html(레거시 api/projects/{id}/...) 등.
// 즉 PAT 헤더와 세션 쿠키 양쪽으로 호출되므로, api/v1 이하처럼 CSRF를 통째로 꺼버리면 세션
// 쿠키 호출 경로에 CSRF 공백이 남는다.
//
// 그래서 인증 방식 기반으로 분기한다: PAT 헤더(Authorization: token .../ Yona-Token)가 있는
// 요청만 CSRF 검증에서 제외하고, 그 외(세션 쿠키) 요청은 캐치올 체인과 동일하게 CSRF를
// 요구한다. ApiTokenAuthenticationFilter는 원래 캐치올 체인(SecurityConfig, Order(5))에만
// 배선돼 있었다 — 이 체인이 그 경로들을 먼저 가로채므로(더 낮은 Order 값) PAT 인증이 계속
// 동작하려면 같은 필터를 여기에도 다시 추가해야 한다.
@Configuration
class LegacyApiSecurityConfig(
    private val apiTokenAuthenticationFilter: ApiTokenAuthenticationFilter
) {

    @Bean
    @Order(4)
    fun legacyApiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/-_-api/v1/**", "/api/**")
            .csrf { csrf ->
                csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(SpaCsrfTokenRequestHandler())
                    .ignoringRequestMatchers(tokenAuthenticatedRequestMatcher)
            }
            .headers { headers -> headers.frameOptions { it.sameOrigin() } }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            // 이 경로들의 실제 인가는 SecurityConfig의 캐치올 체인과 마찬가지로 각 컨트롤러/
            // ApiTokenAuthenticationFilter가 담당한다 — 이 체인은 순수하게 CSRF 정책만 다르게
            // 가져가려고 분리한 것뿐이라, 인가 규칙 자체는 기존과 동일하게 permitAll이다.
            .addFilterAfter(apiTokenAuthenticationFilter, BasicAuthenticationFilter::class.java)

        return http.build()
    }
}
