package dev.yona.config

import dev.yona.domain.user.UserRepository
import dev.yona.security.YonaAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.firewall.HttpFirewall
import org.springframework.security.web.firewall.StrictHttpFirewall
import org.springframework.security.web.context.SecurityContextHolderFilter

/**
 * Spring Security 설정이다.
 *
 * v1.6은 URL이 아니라 컨트롤러에서 접근 권한을 검사하고(`AccessControl`, `@AnonymousCheck`) 익명 사용자도
 * 하나의 사용자(`User.anonymous`)로 다룬다. 같은 구조를 유지하기 위해 이 설정은 모든 URL을 허용하고,
 * 세션, 쿠키, 토큰으로 사용자를 식별하는 일([YonaAuthenticationFilter])과 응답 보안 헤더만 맡는다.
 *
 * v1.6과 달라지는 점:
 * - CSRF 보호를 끈다. v1.6의 폼과 JS는 CSRF 토큰을 보내지 않으므로 켜면 요청 계약이 달라진다
 *   ([refactoring-backlog.md] R-02).
 * - `X-Frame-Options`는 `SAMEORIGIN`이다. 레이아웃의 사이드바(`sidebar.scala.html`)가 같은 출처의 iframe을 쓴다.
 * - 캐시 방지 헤더는 켜지 않는다. 켜면 `/assets/` 정적 자원까지 캐시되지 않는다. 캐시를 막아야 하는
 *   응답(로그인 등)은 v1.6처럼 컨트롤러가 직접 헤더를 붙인다.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

    /**
     * 주소에 `%25`(인코딩된 퍼센트)와 `%2F`를 허용한다. v1.6은 `/`가 든 브랜치 이름 같은 값을 두 번 인코딩해 링크를 만들어(`feature%252Fx`)
     * 주소에 `%2F`가 직접 나오지 않게 한다. 기본 방화벽은 `%25`를 거부하므로 이 링크가 열리지 않는다.
     */
    @Bean
    fun httpFirewall(): HttpFirewall = StrictHttpFirewall().apply {
        setAllowUrlEncodedPercent(true)
        // 두 번 인코딩해 디코딩하면 `%2F`가 남는다. 이 값을 검사에서 거부하지 않는다.
        setAllowUrlEncodedSlash(true)
        // svn 클라이언트는 파일 이름의 `;`를 `%3B`로 보내 커밋한다. 기본 방화벽은 세미콜론이 든 주소를 400으로 막아 이런 이름의 파일을 올릴 수 없다
        setAllowSemicolon(true)
        // Subversion 클라이언트가 보내는 WebDAV 메서드(`PROPFIND`, `MKACTIVITY` 등)를 허용한다. 기본 방화벽은 표준 메서드만 받는다.
        setAllowedHttpMethods(listOf("DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT") + dev.yona.web.svn.SvnDavService.WEBDAV_METHODS)
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity, users: UserRepository, sessionUsers: dev.yona.security.SessionUserCache,
        apiTokens: dev.yona.domain.apitoken.ApiTokenService, tokenFailures: dev.yona.security.TokenFailureLimiter,
        audit: dev.yona.domain.audit.AuditBoundary, properties: YonaProperties, gate: dev.yona.security.TwoFactorGate,
        oauthTokens: dev.yona.domain.oauth2server.OAuthAccessTokenService, siteSettings: dev.yona.domain.site.SiteSettingService
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .headers { headers ->
                // 승인(동의) 화면(X-1)은 다른 곳에 끼워 넣어 클릭을 가로채지 못하도록 DENY이고, 그 밖은 SAMEORIGIN이다
                headers.frameOptions { it.disable() }
                headers.addHeaderWriter { request, response ->
                    response.setHeader("X-Frame-Options", if (request.requestURI.removePrefix(request.contextPath.orEmpty()) == "/oauth2/consent") "DENY" else "SAMEORIGIN")
                }
                headers.cacheControl { it.disable() }
            }
            .addFilterAfter(YonaAuthenticationFilter(users, sessionUsers, apiTokens, tokenFailures, audit, gate, oauthTokens), SecurityContextHolderFilter::class.java)
            .addFilterAfter(dev.yona.mcp.McpAccessFilter(siteSettings, oauthTokens, tokenFailures, audit), YonaAuthenticationFilter::class.java)
            .addFilterAfter(dev.yona.oauth2server.ProtectedResourceChallengeFilter(), YonaAuthenticationFilter::class.java)
            .addFilterAfter(dev.yona.security.AccessDeniedAuditFilter(audit), YonaAuthenticationFilter::class.java)
            .addFilterAfter(dev.yona.observability.UserLogContextFilter(properties.observabilityIdentityInLogs), YonaAuthenticationFilter::class.java)
            .build()
}
