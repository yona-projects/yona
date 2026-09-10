package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.security.web.SecurityFilterChain
import org.springframework.stereotype.Controller
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.context.WebApplicationContext

// CSRF 재활성화 조사(docs/CSRF_INVESTIGATION_2026-09-10.md)의 핵심 전제 검증용 실험 테스트 —
// 프로덕션 SecurityConfig.kt는 전혀 건드리지 않는다(이 스펙 전용의 좁은 securityMatcher 체인만
// @TestConfiguration으로 추가).
//
// 착수 전 가정: "thymeleaf-extras-springsecurity6가 있으면 CSRF만 켜도 th:action 폼에 _csrf가
// 자동으로 붙는다." 실제로 검증해보니 결론은 맞지만 근거는 달랐다 — thymeleaf-extras-springsecurity6
// 자체는 sec:authorize류 표시 로직만 제공할 뿐 CSRF와 무관하다(jar 안에 CSRF 관련 클래스가
// 아예 없음). 실제 메커니즘은 spring-security-config의 WebMvcSecurityConfiguration이
// @EnableWebSecurity + DispatcherServlet 존재 시 자동으로 등록하는
// "requestDataValueProcessor"(CsrfRequestDataValueProcessor) 빈이고, 이 앱은 이미
// @EnableWebSecurity를 쓰고 있어 이 빈이 별도 설정 없이도 이미 존재한다(직접 등록하려다
// BeanDefinitionOverrideException으로 확인함 — 프레임워크가 같은 이름으로 이미 등록해둔
// 상태였음). thymeleaf-spring6의 SpringActionTagProcessor(th:action 전용 태그 프로세서)가 이
// 빈을 이름으로 조회해(spring-webmvc RequestContext, 타입이 아니라 정확히
// "requestDataValueProcessor"라는 이름으로 조회 — 바이트코드로 확인) getExtraHiddenFields()를
// 호출하는 게 실제 주입 지점이다.
@Import(CsrfInvestigationThActionFormConfig::class)
class CsrfThymeleafAutoInjectionThActionFormSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    private val mockMvc by lazy {
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
    }

    init {
        describe("CSRF 활성화 상태에서 th:action 폼") {
            it("_csrf 히든 input이 자동으로 붙는다(추가 빈 등록 불필요 — 프레임워크가 이미 제공)") {
                // BootstrapSetupInterceptor는 DB에 유저가 0명이면 무조건 /bootstrap-setup으로
                // 리다이렉트하므로(인증 없이 GET하는 이 테스트 전용 경로도 예외 없음), 유저를
                // 최소 1명 만들어둔다.
                userRepository.save(User(loginId = "csrf-inv-th-action", name = "조사용", email = "csrf-inv-th-action@yona.io"))

                val body = mockMvc.perform(get("/csrf-investigation-th-action/form"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "_csrf"
                body shouldContain "type=\"hidden\""
            }
        }
    }
}

@Controller
private class CsrfInvestigationThActionFormController {
    @GetMapping("/csrf-investigation-th-action/form")
    fun form(): String = "csrf-investigation-form"
}

@TestConfiguration
class CsrfInvestigationThActionFormConfig {
    // CsrfInvestigationThActionFormController는 @Controller라 컴포넌트 스캔으로 자동 등록된다
    // (별도 @Bean 등록 없음 — 명시적으로 또 등록하면 BeanDefinitionOverrideException).

    // 이 테스트 경로에만 적용되는 별도 체인 — 기존 4개 체인(@Order 1~4)과 겹치지 않는 좁은
    // securityMatcher라 프로덕션 SecurityConfig/ResourceServerConfig/AuthorizationServerConfig
    // 동작에는 영향이 없다. csrf {}를 기본값(활성화) 그대로 둔 것이 이 스펙의 핵심 — 프로덕션
    // SecurityConfig.kt의 .csrf { it.disable() }는 전혀 건드리지 않는다.
    @Bean
    @Order(0)
    fun csrfInvestigationThActionFormChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/csrf-investigation-th-action/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}
