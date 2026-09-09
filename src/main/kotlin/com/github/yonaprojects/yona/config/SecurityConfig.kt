package com.github.yonaprojects.yona.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
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
import org.springframework.security.core.context.SecurityContextHolder
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
import com.github.yonaprojects.yona.domain.twofactor.TwoFactorService
import com.github.yonaprojects.yona.domain.user.Saml2UserProvisioningService
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,
    // google/github(소셜 로그인, link/merge 흐름)와 완전히 분리된 별도 OidcUserService.
    // userInfoEndpoint()에 둘 다 등록해도 Spring Security가 ClientRegistration의 scope에
    // "openid"가 있는지로 자동 분기한다(SecurityConfig 하단 참고).
    private val enterpriseOidcUserService: EnterpriseOidcUserService,
    // SAML2 JIT 프로비저닝.
    private val saml2UserProvisioningService: Saml2UserProvisioningService,
    private val gitAuthorizationFilter: GitAuthorizationFilter,
    // HTTPS Deploy Key 인증. `HttpSecurity.authenticationProvider()`를 한 번이라도 호출하면
    // AuthenticationManagerBuilder가 "이미 구성됨" 상태가 되어, Spring Boot가 컨텍스트의
    // AuthenticationProvider 빈들을 자동으로 긁어모으는 기본 동작
    // (InitializeAuthenticationProviderBeanManagerConfigurer)이 더 이상 동작하지 않는다 — 실제로
    // 이 provider 하나만 등록했다가 기존 YonaAuthenticationProvider(로컬/LDAP 로그인)가 통째로
    // 빠지면서 로그인 관련 통합테스트가 깨지는 회귀를 겪었다. 그래서 기존에 자동으로 등록되던
    // YonaAuthenticationProvider도 이 필드로 명시적으로 주입받아 아래 securityFilterChain()에서
    // 둘 다 등록한다.
    //
    // 다만 위 "InitializeAuthenticationProviderBeanManagerConfigurer 비활성화" 대응은 절반짜리다.
    // HttpSecurity.authenticationProvider()는 @Bean securityFilterChain() 메서드 "실행 시점"에
    // 전역(global) AuthenticationManagerBuilder에 provider를 더하는데, 이 실행 시점은 Spring이
    // 여러 @Bean SecurityFilterChain(AuthorizationServerConfig @Order1 등)을 만드는 순서에
    // 달려 있어 보장되지 않는다. 다른 체인이 먼저 만들어지면서 전역 빌더에 먼저 접근하면,
    // Spring Security의 InitializeUserDetailsBeanManagerConfigurer(우리보다 먼저 도는 별개의
    // 자동설정기)가 "UserDetailsService 빈 하나 + PasswordEncoder 빈 하나가 있으니"
    // DaoAuthenticationProvider를 몰래 끼워 넣어버린다(AuthorizationServerConfig가 OAuth2
    // confidential client secret 검증용으로 노출하는 PasswordEncoder 빈을 엉뚱하게 재사용).
    // 그 결과 ProviderManager 목록이 [DaoAuthenticationProvider, deployKey, yona] 순이 되고,
    // 로컬 로그인 시 비밀번호가 틀리면(YonaAuthenticationProvider가 정상적으로 BadCredentialsException을
    // 던짐) ProviderManager가 다음 provider인 DaoAuthenticationProvider로 넘어가는데, 이 provider는
    // yona가 저장하는 프리픽스 없는 SHA-256 해시를 DelegatingPasswordEncoder로 비교하려다
    // IllegalArgumentException(AuthenticationException이 아니라서 ProviderManager가 삼키지
    // 못함)을 던져 로그인 실패가 정상적인 "비밀번호 불일치" 메시지 대신 500으로 튀었다(올바른
    // 비밀번호로는 우연히 YonaAuthenticationProvider에서 먼저 성공해버려 드러나지 않고, 오직
    // "틀린 비밀번호 로그인 시도"에서만 재현됨).
    //
    // 수정: Spring Security가 공식적으로 보장하는 훅 — 아무 빈에나 있는
    // `@Autowired fun xxx(auth: AuthenticationManagerBuilder)` 메서드는 전역 빌더를 스캔하는
    // 자동설정기들(Initialize*BeanManagerConfigurer, 항상 낮은 우선순위로 맨 나중에 돎)보다
    // 먼저, 결정적으로(항상) 실행된다 — 아래 configureGlobalAuthentication()가 그 훅이다. 여기서
    // 전역 빌더를 "이미 구성됨" 상태로 만들어두면 이후 어느 체인이 먼저 만들어지든
    // InitializeUserDetailsBeanManagerConfigurer가 DaoAuthenticationProvider를 끼워 넣지 못한다
    // (기존 securityFilterChain() 안의 .authenticationProvider() 두 줄은 이제 중복이라 제거).
    private val yonaAuthenticationProvider: YonaAuthenticationProvider,
    private val deployKeyAuthenticationProvider: DeployKeyAuthenticationProvider,
    private val svnAuthorizationFilter: SvnAuthorizationFilter,
    // GitAuthorizationFilter/SvnAuthorizationFilter와 대칭인 Mercurial HTTP 전용 인가 필터.
    private val hgAuthorizationFilter: HgAuthorizationFilter,
    private val apiTokenAuthenticationFilter: ApiTokenAuthenticationFilter,
    private val accessLogFilter: AccessLogFilter,
    // 2FA: 1차 비밀번호 인증 성공 후 계정에 등록된 2FA가 있는지 판단해 완전한 로그인을
    // 보류할지 결정하는 데 쓴다(YonaAuthenticationSuccessHandler). OAuth2/SAML2/PAT 로그인
    // 경로는 이 폼 로그인 성공 핸들러를 타지 않으므로 이 게이트 대상이 아니다.
    private val twoFactorService: TwoFactorService,
    private val userRepository: UserRepository,
    private val pre2faGateFilter: Pre2faGateFilter,
    @Value("\${yona.sso.saml2.email-attribute:email}")
    private val saml2EmailAttribute: String,
    @Value("\${yona.sso.saml2.display-name-attribute:displayName}")
    private val saml2DisplayNameAttribute: String
) {

    // 위 생성자 코멘트 참고 — Initialize*BeanManagerConfigurer들보다 항상 먼저 도는
    // Spring Security 공식 훅. 여기서 전역 AuthenticationManagerBuilder를 "구성 완료" 상태로
    // 만들어 DaoAuthenticationProvider가 끼어들 여지를 원천 차단한다.
    @Autowired
    fun configureGlobalAuthentication(auth: AuthenticationManagerBuilder) {
        auth.authenticationProvider(deployKeyAuthenticationProvider)
        auth.authenticationProvider(yonaAuthenticationProvider)
    }

    // SVN(SvnController → SVNKit DAVServlet)이 실제로 쓰는 WebDAV/DeltaV 메서드(PROPFIND 등)가
    // Spring Security 기본 StrictHttpFirewall의 허용 목록(GET/HEAD/POST/PUT/PATCH/DELETE/OPTIONS)에
    // 없어, 요청이 SvnAuthorizationFilter/SvnController에 도달하기도 전에
    // RequestRejectedException → 400으로 거부되고 있었다. 실제 svn checkout/update/info/log는
    // 전부 PROPFIND를 쓰므로 이 방화벽 기본값 아래에서는 SVN-over-HTTP 자체가 완전히 동작
    // 불가능했다(실제 springSecurityFilterChain을 태우는 통합테스트를 작성하다가 발견 — 기존
    // SVN 테스트는 전부 standaloneSetup()이거나 보안 필터 체인을 안 태워서 이 문제를 잡아낸 적이
    // 없었음). HttpFirewall 빈을 노출하면 Spring Security의 WebSecurityConfiguration이 자동으로
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

    // 신규 AuthorizationServerConfig(@Order 1)/ResourceServerConfig(@Order 2: /mcp/**, @Order 3:
    // /api/v1/**)가 각각 좁은 경로만 담당하므로 이 캐치올 체인은 가장 낮은 우선순위(@Order 4)로
    // 명시한다 — 겹치는 URL이 없어 동작 변화는 없지만, 여러 SecurityFilterChain 빈이 공존할 때
    // 순서를 암묵적 추론에 맡기지 않기 위해 명시적으로 선언했다.
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
                    // 2FA 검증 화면/API — ROLE_PRE_2FA 상태에서만 의미가 있고, Pre2faGateFilter가
                    // 이 상태의 다른 모든 경로 접근을 여기로 되돌리므로 인가 규칙에서도 열어둬야 한다.
                    .requestMatchers("/users/login/2fa/**").permitAll()
                    .requestMatchers("/git/**").permitAll()
                    .requestMatchers("/svn/**").permitAll()
                    .requestMatchers("/hg/**").permitAll()
                    .requestMatchers("/site/**", "/sites/**").hasAnyRole("ADMIN", "SITE_ADMIN")
                    // springdoc이 자동 스캔하는 API 문서에는 관리자 전용 엔드포인트도 포함되므로
                    // /site/**와 동일하게 제한한다.
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs").hasAnyRole("ADMIN", "SITE_ADMIN")
                    // `/api/v1/**`는 이제 ResourceServerConfig의 전용 체인(@Order 3)이 이 경로를
                    // 전담한다(PAT + OAuth2 JWT 이중 인증, GET permitAll + 나머지 인증 요구는 그
                    // 체인에서 동일하게 유지됨) — 이 캐치올 체인까지 도달하는 /api/v1/** 요청은
                    // 없으므로 여기엔 별도 규칙을 두지 않는다.
                    .anyRequest().permitAll()
            }
            .formLogin { form ->
                form
                    .loginPage("/users/loginform")
                    .loginProcessingUrl("/users/login")
                    .usernameParameter("loginIdOrEmail")
                    .passwordParameter("password")
                    .successHandler(YonaAuthenticationSuccessHandler(twoFactorService, userRepository))
                    .failureHandler(YonaAuthenticationFailureHandler())
                    .permitAll()
            }
            .rememberMe { rememberMe ->
                rememberMe
                    .rememberMeParameter("rememberMe")
                    .key("yonaRememberMeKey")
            }
            .httpBasic { }
            // deployKeyAuthenticationProvider/yonaAuthenticationProvider는 이제
            // configureGlobalAuthentication()(위 생성자 코멘트 참고)에서 전역 빌더에 등록한다 —
            // 여기서 다시 .authenticationProvider()로 추가하면 같은 인스턴스가 중복 등록될
            // 뿐이라 제거.
            .oauth2Login { oauth2 ->
                oauth2
                    .loginPage("/users/loginform")
                    .userInfoEndpoint { userInfo ->
                        userInfo
                            .userService(customOAuth2UserService)
                            // "openid" 스코프를 포함한 등록(엔터프라이즈 OIDC)만 이 서비스로
                            // 라우팅된다. google/github는 scope=profile,email이라 영향 없음.
                            .oidcUserService(enterpriseOidcUserService)
                    }
                    .defaultSuccessUrl("/")
            }
            // SAML2 SP 로그인. RelyingPartyRegistrationRepository는
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
            .addFilterAfter(pre2faGateFilter, BasicAuthenticationFilter::class.java)
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

class YonaAuthenticationSuccessHandler(
    private val twoFactorService: TwoFactorService,
    private val userRepository: UserRepository
) : AuthenticationSuccessHandler {
    private val requestCache = HttpSessionRequestCache()
    private val securityContextRepository = org.springframework.security.web.context.HttpSessionSecurityContextRepository()

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        // 2FA 게이트는 폼 로그인(로컬 비밀번호) 경로만 대상이다 — 이 핸들러는 formLogin()에만
        // 등록돼 있으므로 OAuth2/SAML2/PAT 로그인은 애초에 이 코드를 타지 않는다. isTwoFactorEnabled()는
        // User.isTwoFactorEnabled 캐시가 아니라 실제 등록 테이블을 조회하는 단일 진실 공급원이라
        // 캐시 드리프트가 로그인 우회로 이어지지 않는다.
        val principal = authentication.principal
        val loginId = if (principal is YonaUserDetails) principal.loginId else authentication.name
        val user = userRepository.findByLoginId(loginId).orElse(null)

        if (user != null && twoFactorService.isTwoFactorEnabled(user)) {
            val pre2fa = Pre2faAuthenticationToken(authentication)
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = pre2fa
            SecurityContextHolder.setContext(context)
            securityContextRepository.saveContext(context, request, response)
            // 원래 목적지는 기존 requestCache에 그대로 남아있다(HttpSessionRequestCache.getRequest()는
            // 세션에서 제거하지 않음) — 2FA 검증 성공 후 TwoFactorLoginController가 동일 캐시로 읽는다.
            response.sendRedirect("${request.contextPath}/users/login/2fa")
            return
        }

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

