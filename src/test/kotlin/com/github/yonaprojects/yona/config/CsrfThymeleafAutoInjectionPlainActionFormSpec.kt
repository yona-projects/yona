package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.string.shouldNotContain
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

// CsrfThymeleafAutoInjectionThActionFormSpec의 대조군 — 유일한 차이는 렌더링하는 템플릿이
// th:action이 아니라 순수 HTML action= 속성만 쓴다는 것뿐이다. site/layout.html의 익명 로그인
// 모달(action="/users/login", th:action 아님)이나 code/diff.html·code/compare.html이 JS
// 템플릿 문자열로 즉석에서 만드는 폼이 실제로 이 패턴이다 — CSRF를 켜도 이런 폼은 자동 주입
// 대상이 아니므로, 재활성화를 실제로 한다면 이런 폼들은 개별적으로 손봐야 한다는 근거다.
// 프로덕션 SecurityConfig.kt는 건드리지 않는다(이 스펙 전용의 좁은 securityMatcher 체인만 추가).
@Import(CsrfInvestigationPlainActionFormConfig::class)
class CsrfThymeleafAutoInjectionPlainActionFormSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    private val mockMvc by lazy {
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()
    }

    init {
        describe("CSRF 활성화 상태에서 순수 HTML action= 폼(th:action 아님)") {
            it("_csrf 히든 input이 자동으로 붙지 않는다") {
                // BootstrapSetupInterceptor는 DB에 유저가 0명이면 무조건 /bootstrap-setup으로
                // 리다이렉트하므로(인증 없이 GET하는 이 테스트 전용 경로도 예외 없음), 유저를
                // 최소 1명 만들어둔다.
                userRepository.save(User(loginId = "csrf-inv-plain-action", name = "조사용", email = "csrf-inv-plain-action@yona.io"))

                val body = mockMvc.perform(get("/csrf-investigation-plain-action/form"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldNotContain "_csrf"
            }
        }
    }
}

@Controller
private class CsrfInvestigationPlainActionFormController {
    @GetMapping("/csrf-investigation-plain-action/form")
    fun form(): String = "csrf-investigation-plain-form"
}

@TestConfiguration
class CsrfInvestigationPlainActionFormConfig {
    // CsrfInvestigationPlainActionFormController는 @Controller라 컴포넌트 스캔으로 자동
    // 등록된다(별도 @Bean 등록 없음 — 명시적으로 또 등록하면 BeanDefinitionOverrideException).

    @Bean
    @Order(0)
    fun csrfInvestigationPlainActionFormChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/csrf-investigation-plain-action/**")
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }
}
