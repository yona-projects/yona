package com.github.yonaprojects.yona.config

import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.savedrequest.SavedRequest
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import com.github.yonaprojects.yona.config.git.GitAuthorizationFilter
import com.github.yonaprojects.yona.config.oauth2.CustomOAuth2UserService
import com.github.yonaprojects.yona.config.svn.SvnAuthorizationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val gitAuthorizationFilter: GitAuthorizationFilter,
    private val svnAuthorizationFilter: SvnAuthorizationFilter,
    private val apiTokenAuthenticationFilter: ApiTokenAuthenticationFilter,
    private val accessLogFilter: AccessLogFilter
) {

    @Bean
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
                    .requestMatchers("/site/**", "/sites/**").hasAnyRole("ADMIN", "SITE_ADMIN")
                    // yona-wiki P3-09(Swagger/OpenAPI UI) 대응 — springdoc이 자동 스캔하는 API
                    // 문서에는 관리자 전용 엔드포인트도 포함되므로 /site/**와 동일하게 제한한다.
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs").hasAnyRole("ADMIN", "SITE_ADMIN")
                    // yona-wiki P3-02 Step4~6 — 신규 `/api/v1/projects/**` 네임스페이스는 ApiTokenAuthenticationFilter가
                    // 스코프 토큰이 없거나 알 수 없는 토큰이면 그냥 통과시킨다(컨트롤러의 401/403 처리에 위임하는
                    // 설계, 필터 주석 참고). 이 앱의 다른 모든 API 경로는 anyRequest().permitAll() + 컨트롤러
                    // 자체 인증/인가 체크(getLoginUser 401, AccessControl 403)에 의존하는 동일한 컨벤션을 쓰고
                    // 있어 이 자체로는 구멍이 아니지만(공개 프로젝트 익명 읽기 허용도 그 컨벤션의 일부), 신규
                    // 쓰기 경로(POST/PUT/PATCH/DELETE)만큼은 프레임워크 레벨에서도 방어선을 하나 더 두어
                    // 토큰/세션이 전혀 없는 요청이 컨트롤러까지 도달하기 전에 걸러지도록 한다. GET은 기존
                    // 컨벤션(공개 프로젝트 익명 조회 허용)을 그대로 유지하기 위해 제외한다.
                    .requestMatchers(HttpMethod.GET, "/api/v1/projects/**").permitAll()
                    .requestMatchers("/api/v1/projects/**").authenticated()
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
            .oauth2Login { oauth2 ->
                oauth2
                    .loginPage("/users/loginform")
                    .userInfoEndpoint { userInfo ->
                        userInfo.userService(customOAuth2UserService)
                    }
                    .defaultSuccessUrl("/")
            }
            .logout { logout ->
                logout
                    .logoutUrl("/users/logout")
                    .logoutSuccessUrl("/users/loginform?logout")
                    .permitAll()
            }
            .addFilterAfter(gitAuthorizationFilter, BasicAuthenticationFilter::class.java)
            .addFilterAfter(svnAuthorizationFilter, BasicAuthenticationFilter::class.java)
            .addFilterAfter(apiTokenAuthenticationFilter, BasicAuthenticationFilter::class.java)
            .addFilterAfter(accessLogFilter, BasicAuthenticationFilter::class.java)
        return http.build()
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

