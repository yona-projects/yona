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
                    .andExpect(content().string(Matchers.containsString("Log in")))
                    .andExpect(content().string(Matchers.containsString("Feedback")))
            }

            // 2026-09-17 갱신 - site/layout.html의 익명 사용자용 로그인 모달이 Vue 3
            // SFC(<yona-login-dialog>)로 교체됐다. 이 컴포넌트는 순수 HTML <form action=...>
            // 제출이 아니라 fetch(actionUrl, {...})로 직접 POST하므로(components/vue-widgets/
            // src/login-dialog/YonaLoginDialog.vue의 onSubmit), 더 이상 CsrfRequestDataValueProcessor의
            // 자동 히든 필드 주입 대상이 아니다 - 대신 사이트 전역 fetch 패치(아래
            // "전역 CSRF fetch 인터셉터" 테스트가 검증)가 모든 fetch 호출에 CSRF 헤더를
            // 자동으로 붙여준다. 여기서는 그 컴포넌트가 익명 사용자에게 실제로 렌더링되는지만
            // 확인한다.
            it("익명 사용자에게는 <yona-login-dialog> 로그인 모달이 렌더링되어야 한다") {
                val body = mockMvc.perform(get("/"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "<yona-login-dialog"
                body shouldContain "id=\"loginDialog\""
            }

            // site/layout.html::scripts에 추가한 전역 fetch 인터셉터가 익명 사용자를 포함해
            // 실제로 모든 페이지에 렌더링되는지 확인한다(로그인 여부와 무관하게 이 인터셉터가
            // 빠지면 fetch 호출 전부가 CSRF로 막힌다).
            it("모든 페이지에 전역 CSRF fetch 인터셉터 스크립트가 포함돼야 한다") {
                val body = mockMvc.perform(get("/"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // P3-48 화면별 재현 세션에서 jQuery.ajaxSetup(beforeSend)을 jQuery(document).ajaxSend로
                // 교체했다 — 개별 $.ajax() 호출이 자기만의 beforeSend를 넘기면(예:
                // yona.Tasklist.js) ajaxSetup의 beforeSend를 완전히 덮어써 CSRF 헤더가 빠지는
                // 문제를 실제로 재현해서 고쳤다(GlobalCsrfAjaxHeaderTemplateEquivalenceSpec 참고).
                // P3-70 라운드12 갱신: jQuery 코어 자체를 제거하면서 이 jQuery(document).ajaxSend
                // 블록(도달 가능한 $.ajax 호출이 0건인 죽은 코드였다 - 라운드10~11에서 이미 확인)도
                // 함께 제거했다 - $.ajax 호출 자체가 이제 저장소 전체에 없으므로(모두 fetch로
                // 전환됨) "ajaxSend" 문자열 존재 여부는 더 이상 의미 있는 계약이 아니다. 실제
                // CSRF 주입은 아래 window.fetch 패치 단독으로 담당한다.
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
                    .andExpect(content().string(Matchers.containsString("Create new project")))
            }
        }
    }
}
