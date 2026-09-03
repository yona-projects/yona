package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
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
