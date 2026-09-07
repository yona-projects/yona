package com.github.yonaprojects.yona.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.savedrequest.SavedRequest
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.firewall.HttpFirewall
import org.springframework.security.web.firewall.StrictHttpFirewall
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import com.github.yonaprojects.yona.config.git.DeployKeyAuthenticationProvider
import com.github.yonaprojects.yona.config.git.GitAuthorizationFilter
import com.github.yonaprojects.yona.config.hg.HgAuthorizationFilter
import com.github.yonaprojects.yona.config.oauth2.CustomOAuth2UserService
import com.github.yonaprojects.yona.config.sso.EnterpriseOidcUserService
import com.github.yonaprojects.yona.config.sso.EnterpriseSaml2ResponseAuthenticationConverter
import com.github.yonaprojects.yona.config.svn.SvnAuthorizationFilter
import com.github.yonaprojects.yona.domain.user.Saml2UserProvisioningService

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,
    // yona-wiki P3-06(엔터프라이즈 SSO) Step2 — google/github(소셜 로그인, link/merge 흐름)와
    // 완전히 분리된 별도 OidcUserService. userInfoEndpoint()에 둘 다 등록해도 Spring Security가
    // ClientRegistration의 scope에 "openid"가 있는지로 자동 분기한다(SecurityConfig 하단 참고).
    private val enterpriseOidcUserService: EnterpriseOidcUserService,
    // yona-wiki P3-06 Step3 — SAML2 JIT 프로비저닝.
    private val saml2UserProvisioningService: Saml2UserProvisioningService,
    private val gitAuthorizationFilter: GitAuthorizationFilter,
    // yona-wiki P3-03 Step2 — HTTPS Deploy Key 인증. `HttpSecurity.authenticationProvider()`를
    // 한 번이라도 호출하면 AuthenticationManagerBuilder가 "이미 구성됨" 상태가 되어, Spring Boot가
    // 컨텍스트의 AuthenticationProvider 빈들을 자동으로 긁어모으는 기본 동작
    // (InitializeAuthenticationProviderBeanManagerConfigurer)이 더 이상 동작하지 않는다 — 실제로
    // 이 provider 하나만 등록했다가 기존 YonaAuthenticationProvider(로컬/LDAP 로그인)가 통째로
    // 빠지면서 로그인 관련 통합테스트가 깨지는 회귀를 겪었다. 그래서 기존에 자동으로 등록되던
    // YonaAuthenticationProvider도 이 필드로 명시적으로 주입받아 아래 securityFilterChain()에서
    // 둘 다 등록한다.
    private val yonaAuthenticationProvider: YonaAuthenticationProvider,
    private val deployKeyAuthenticationProvider: DeployKeyAuthenticationProvider,
    private val svnAuthorizationFilter: SvnAuthorizationFilter,
    // yona-wiki P3-12(Mercurial 지원) 2라운드 — GitAuthorizationFilter/SvnAuthorizationFilter와
    // 대칭인 Mercurial HTTP 전용 인가 필터.
    private val hgAuthorizationFilter: HgAuthorizationFilter,
    private val apiTokenAuthenticationFilter: ApiTokenAuthenticationFilter,
    private val accessLogFilter: AccessLogFilter,
    @Value("\${yona.sso.saml2.email-attribute:email}")
    private val saml2EmailAttribute: String,
    @Value("\${yona.sso.saml2.display-name-attribute:displayName}")
    private val saml2DisplayNameAttribute: String
) {

    // 2026-09-07 발견 — SVN(SvnController → SVNKit DAVServlet)이 실제로 쓰는 WebDAV/DeltaV
    // 메서드(PROPFIND 등)가 Spring Security 기본 StrictHttpFirewall의 허용 목록
    // (GET/HEAD/POST/PUT/PATCH/DELETE/OPTIONS)에 없어, 요청이 SvnAuthorizationFilter/
    // SvnController에 도달하기도 전에 RequestRejectedException → 400으로 거부되고 있었다.
    // 실제 svn checkout/update/info/log는 전부 PROPFIND를 쓰므로 이 방화벽 기본값 아래에서는
    // SVN-over-HTTP 자체가 완전히 동작 불가능했다(DeployKeySvnAuthorizationIntegrationSpec을
    // 실제 springSecurityFilterChain을 태워서 작성하다가 발견 — 기존 SVN 테스트는 전부
    // standaloneSetup()이거나 보안 필터 체인을 안 태워서 이 문제를 잡아낸 적이 없었음).
    // HttpFirewall 빈을 노출하면 Spring Security의 WebSecurityConfiguration이 자동으로
    // 감지해 적용한다(별도 배선 불필요).
    @Bean
    fun httpFirewall(): HttpFirewall {
        val firewall = StrictHttpFirewall()
        // StrictHttpFirewall 기본값(DELETE/GET/HEAD/OPTIONS/PATCH/POST/PUT, 이 버전엔 상수로
        // 노출돼 있지 않아 그대로 나열)에 SVN DAVServlet이 쓰는 WebDAV/DeltaV 메서드를 추가한다.
        firewall.setAllowedHttpMethods(
            listOf(
                "DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT",
                "PROPFIND", "PROPPATCH", "MKCOL", "COPY", "MOVE", "LOCK", "UNLOCK",
                "REPORT", "MKACTIVITY", "CHECKOUT", "MERGE", "VERSION-CONTROL"
            )
        )
        return firewall
    }

    // yona-wiki P3-07(MCP 서버) Step2 — 신규 AuthorizationServerConfig(@Order 1)/ResourceServerConfig
    // (@Order 2: /mcp/**, @Order 3: /api/v1/**, yona-wiki P3-14에서 추가)가 각각 좁은 경로만
    // 담당하므로 이 캐치올 체인은 가장 낮은 우선순위(@Order 4)로 명시한다 — 겹치는 URL이 없어
    // 동작 변화는 없지만, 여러 SecurityFilterChain 빈이 공존할 때 순서를 암묵적 추론에 맡기지
    // 않기 위해 명시적으로 선언했다.
    @Bean
    @Order(4)
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { csrf -> csrf.disable() }
            .headers { headers ->
                headers.frameOptions { frameOptions ->
                    frameOptions.sameOrigin()
                }
            }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/css/**", "/js/**", "/images/**", "/stylesheets/**", "/javascripts/**", "/bootstrap/**", "/assets/**").permitAll()
                    .requestMatchers("/login", "/signup", "/lostPassword", "/user/reset-password", "/bootstrap-setup", "/users/loginform", "/users/signupform", "/users/signup").permitAll()
                    .requestMatchers("/git/**").permitAll()
                    .requestMatchers("/svn/**").permitAll()
                    .requestMatchers("/hg/**").permitAll()
                    .requestMatchers("/site/**", "/sites/**").hasAnyRole("ADMIN", "SITE_ADMIN")
                    // yona-wiki P3-09(Swagger/OpenAPI UI) 대응 — springdoc이 자동 스캔하는 API
                    // 문서에는 관리자 전용 엔드포인트도 포함되므로 /site/**와 동일하게 제한한다.
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs").hasAnyRole("ADMIN", "SITE_ADMIN")
                    // yona-wiki P3-02 Step4~6 / P3-14 — `/api/v1/**`는 이제 ResourceServerConfig의
                    // 전용 체인(@Order 3)이 이 경로를 전담한다(PAT + OAuth2 JWT 이중 인증, GET
                    // permitAll + 나머지 인증 요구는 그 체인에서 동일하게 유지됨) — 이 캐치올
                    // 체인까지 도달하는 /api/v1/** 요청은 없으므로 여기엔 별도 규칙을 두지 않는다.
                    .anyRequest().permitAll()
            }
            .formLogin { form ->
                form
                    .loginPage("/users/loginform")
                    .loginProcessingUrl("/users/login")
                    .usernameParameter("loginIdOrEmail")
                    .passwordParameter("password")
                    .successHandler(YonaAuthenticationSuccessHandler())
                    .failureHandler(YonaAuthenticationFailureHandler())
                    .permitAll()
            }
            .rememberMe { rememberMe ->
                rememberMe
                    .rememberMeParameter("rememberMe")
                    .key("yonaRememberMeKey")
            }
            .httpBasic { }
            .authenticationProvider(deployKeyAuthenticationProvider)
            .authenticationProvider(yonaAuthenticationProvider)
            .oauth2Login { oauth2 ->
                oauth2
                    .loginPage("/users/loginform")
                    .userInfoEndpoint { userInfo ->
                        userInfo
                            .userService(customOAuth2UserService)
                            // yona-wiki P3-06 Step2 — "openid" 스코프를 포함한 등록(엔터프라이즈 OIDC)만
                            // 이 서비스로 라우팅된다. google/github는 scope=profile,email이라 영향 없음.
                            .oidcUserService(enterpriseOidcUserService)
                    }
                    .defaultSuccessUrl("/")
            }
            // yona-wiki P3-06 Step3 — SAML2 SP 로그인. RelyingPartyRegistrationRepository는
            // config/sso/YonaRelyingPartyRegistrationRepository(관리자 UI/설정 파일 기반, 비활성화면
            // findByRegistrationId가 null을 반환)가 유일한 빈이라 자동으로 주입된다. 기본
            // OpenSaml5AuthenticationProvider 대신 JIT 프로비저닝을 끼워 넣은 커스텀
            // responseAuthenticationConverter를 쓰는 AuthenticationManager를 명시적으로 지정한다.
            .saml2Login { saml2 ->
                saml2.authenticationManager(saml2AuthenticationManager())
            }
            .logout { logout ->
                logout
                    .logoutUrl("/users/logout")
                    .logoutSuccessUrl("/users/loginform?logout")
                    .permitAll()
            }
            .addFilterAfter(gitAuthorizationFilter, BasicAuthenticationFilter::class.java)
            .addFilterAfter(svnAuthorizationFilter, BasicAuthenticationFilter::class.java)
            .addFilterAfter(hgAuthorizationFilter, BasicAuthenticationFilter::class.java)
            .addFilterAfter(apiTokenAuthenticationFilter, BasicAuthenticationFilter::class.java)
            .addFilterAfter(accessLogFilter, BasicAuthenticationFilter::class.java)
        return http.build()
    }

    private fun saml2AuthenticationManager(): AuthenticationManager {
        val provider = OpenSaml5AuthenticationProvider()
        provider.setResponseAuthenticationConverter(
            EnterpriseSaml2ResponseAuthenticationConverter(
                saml2UserProvisioningService, saml2EmailAttribute, saml2DisplayNameAttribute
            )
        )
        return ProviderManager(provider)
    }
}

class YonaAuthenticationSuccessHandler : AuthenticationSuccessHandler {
    private val requestCache = HttpSessionRequestCache()

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val requestedWith = request.getHeader("X-Requested-With")
        val accept = request.getHeader("Accept")
        val isAjax = "XMLHttpRequest" == requestedWith || (accept != null && accept.contains("application/json"))

        if (isAjax) {
            response.contentType = "application/json;charset=UTF-8"
            response.status = HttpServletResponse.SC_OK
            response.writer.write("{}")
            response.writer.flush()
        } else {
            val savedRequest: SavedRequest? = requestCache.getRequest(request, response)
            val targetUrl = savedRequest?.redirectUrl ?: "/"
            response.sendRedirect(targetUrl)
        }
    }
}

class YonaAuthenticationFailureHandler : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException
    ) {
        val requestedWith = request.getHeader("X-Requested-With")
        val accept = request.getHeader("Accept")
        val isAjax = "XMLHttpRequest" == requestedWith || (accept != null && accept.contains("application/json"))

        if (isAjax) {
            response.contentType = "application/json;charset=UTF-8"
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.writer.write("{\"message\":\"user.login.invalid\"}")
            response.writer.flush()
        } else {
            response.sendRedirect("/users/loginform?error=true")
        }
    }
}

