package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// TemplateEquivalenceSpec의 "sendYonaUsage 설정 기본값(true)이면 렌더링되어야 한다" 테스트와
// 짝을 이룬다 — 배포자가 yona.analytics.send-usage=false로 명시적으로 꺼도 구글 애널리틱스
// 스크립트가 렌더링되지 않아야 한다는 옵트아웃 경로를 별도 프로퍼티 오버라이드로 검증한다.
@TestPropertySource(properties = ["yona.analytics.send-usage=false"])
class SendYonaUsageOptOutTemplateSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

            // BootstrapSetupInterceptor가 DB에 사용자가 0명이면 모든 요청을 /bootstrap-setup으로
            // 리다이렉트한다 — 이 스펙은 별도 fixture 없이 독립 실행되므로 최소 1명은 있어야 한다.
            if (userRepository.count() == 0L) {
                userRepository.save(User(loginId = "optout-tester", name = "옵트아웃테스터", email = "optout-tester@yona.io"))
            }
        }

        describe("sendYonaUsage 옵트아웃") {
            it("yona.analytics.send-usage=false로 명시하면 구글 애널리틱스 스크립트가 렌더링되지 않아야 한다") {
                val result = mockMvc.perform(get("/users/loginform"))
                    .andExpect(status().isOk)
                    .andReturn()

                result.response.contentAsString.contains("google-analytics.com/analytics.js") shouldBe false
            }
        }
    }
}
