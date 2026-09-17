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

            // site/layout.html의 로그인 모달은 Vue 3 SFC(<yona-login-dialog>)다. 이 컴포넌트는
            // <form action=...> 제출이 아니라 fetch(actionUrl, {...})로 직접 POST하므로
            // (YonaLoginDialog.vue의 onSubmit), CsrfRequestDataValueProcessor의 자동 히든 필드
            // 주입 대상이 아니다 - 대신 아래 "전역 CSRF fetch 인터셉터" 테스트가 검증하는 전역
            // fetch 패치가 CSRF 헤더를 붙여준다.
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

                // CSRF 주입은 전역 window.fetch 패치가 단독으로 담당한다(jQuery 기반 ajaxSetup/
                // ajaxSend 메커니즘의 함정은 GlobalCsrfAjaxHeaderTemplateEquivalenceSpec 참고).
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
