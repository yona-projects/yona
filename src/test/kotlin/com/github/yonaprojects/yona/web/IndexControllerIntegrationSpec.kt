package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.hamcrest.Matchers

class IndexControllerIntegrationSpec @Autowired constructor(
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
            if (!userRepository.findByLoginId("system-setup-user").isPresent) {
                userRepository.save(User(loginId = "system-setup-user", name = "초기사용자", email = "setup@yona.io"))
            }
        }

        describe("IndexController 통합 테스트") {
            it("로그인하지 않은 익명 사용자가 메인 홈(/) 접근 시, 인트로 화면이 노출되어야 한다") {
                mockMvc.perform(get("/"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("index"))
                    .andExpect(content().string(Matchers.containsString("21st Century Software Development Platform")))
                    .andExpect(content().string(Matchers.containsString("로그인")))
                    .andExpect(content().string(Matchers.containsString("개발팀에게 문의하기")))
            }

            // site/layout.html의 익명 사용자용 로그인 모달이 순수 action= 속성이라 CSRF 자동
            // 주입 대상이 아니었다. th:action으로 바꾼 뒤(SecurityConfig의 캐치올 체인이 CSRF를
            // 활성화했으므로) 이 sitewide 모달에 _csrf 히든 필드가 실제로 붙는지 실제 보안 필터
            // 체인으로 검증한다.
            it("익명 사용자에게 렌더링되는 로그인 모달에 _csrf 히든 필드가 자동으로 붙어야 한다") {
                val body = mockMvc.perform(get("/"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "action=\"/users/login\""
                body shouldContain "name=\"_csrf\""
            }

            // site/layout.html::scripts에 추가한 전역 $.ajax/fetch 인터셉터가 익명 사용자를
            // 포함해 실제로 모든 페이지에 렌더링되는지 확인한다(로그인 여부와 무관하게 이
            // 인터셉터가 빠지면 $.ajax/fetch 호출 전부가 CSRF로 막힌다).
            it("모든 페이지에 전역 CSRF $.ajax/fetch 인터셉터 스크립트가 포함돼야 한다") {
                val body = mockMvc.perform(get("/"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // P3-48 화면별 재현 세션에서 jQuery.ajaxSetup(beforeSend)을 jQuery(document).ajaxSend로
                // 교체했다 — 개별 $.ajax() 호출이 자기만의 beforeSend를 넘기면(예:
                // yona.Tasklist.js) ajaxSetup의 beforeSend를 완전히 덮어써 CSRF 헤더가 빠지는
                // 문제를 실제로 재현해서 고쳤다(GlobalCsrfAjaxHeaderTemplateEquivalenceSpec 참고).
                body shouldContain "ajaxSend"
                body shouldContain "X-XSRF-TOKEN"
                body shouldContain "window.fetch = function"
            }

            it("로그인한 사용자가 메인 홈(/) 접근 시, 대시보드 화면과 사용자명이 노출되어야 한다") {
                // Given
                if (!userRepository.findByLoginId("gildong").isPresent) {
                    userRepository.save(User(loginId = "gildong", name = "길동", email = "gildong@yona.io"))
                }

                val userDetails = YonaUserDetails(
                    id = 1L,
                    loginId = "gildong",
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                // When & Then
                mockMvc.perform(
                    get("/").with(SecurityMockMvcRequestPostProcessors.user(userDetails))
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("index"))
                    .andExpect(content().string(Matchers.containsString("gildong")))
                    .andExpect(content().string(Matchers.containsString("새 프로젝트 만들기")))
            }
        }
    }
}
