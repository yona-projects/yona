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

// th:action 폼에 CSRF 히든 필드가 자동으로 붙는 것은 thymeleaf-extras-springsecurity6와 무관하다
// (그 라이브러리는 sec:authorize류 표시 로직만 제공, CSRF 관련 클래스가 없음). 실제 메커니즘은
// spring-security-config가 @EnableWebSecurity + DispatcherServlet 조합에서 자동 등록하는
// "requestDataValueProcessor"(CsrfRequestDataValueProcessor) 빈을 thymeleaf-spring6의
// SpringActionTagProcessor가 이름으로 조회해 getExtraHiddenFields()를 호출하는 것이다.
// 프로덕션 SecurityConfig.kt는 건드리지 않는다(이 스펙 전용의 좁은 securityMatcher 체인만 추가).
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

    // 기존 체인들과 겹치지 않는 좁은 securityMatcher. csrf {}를 기본값(활성화)으로 둔 것이 핵심
    // — 프로덕션 SecurityConfig.kt의 .csrf { it.disable() }는 건드리지 않는다.
    @Bean
    @Order(0)
    fun csrfInvestigationThActionFormChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/csrf-investigation-th-action/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}
