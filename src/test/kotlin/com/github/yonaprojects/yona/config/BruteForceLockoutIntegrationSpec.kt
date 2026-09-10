package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.PasswordEncodingService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// 브루트포스 방어 — YonaAuthenticationProvider에 추가한 자동 잠금이 실제
// springSecurityFilterChain(폼 로그인 전체 경로) 위에서도 그대로 동작하는지 검증한다.
class BruteForceLockoutIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val passwordEncodingService: PasswordEncodingService
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()
        }

        describe("로그인 실패 5회 누적 시 자동 잠금") {
            it("6번째 시도는 올바른 비밀번호를 넣어도 로그인이 거부되어야 한다") {
                val loginId = "lockout-${System.nanoTime()}"
                userRepository.save(
                    User(
                        loginId = loginId, name = "잠금대상", email = "$loginId@example.com",
                        password = passwordEncodingService.encode("correct-password"), passwordSalt = null
                    )
                )

                repeat(5) {
                    mockMvc.perform(
                        post("/users/login")
                            .param("loginIdOrEmail", loginId)
                            .param("password", "wrong-password")
                            .session(MockHttpSession())
                            .with(csrf())
                    ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/users/loginform?error=true"))
                }

                mockMvc.perform(
                    post("/users/login")
                        .param("loginIdOrEmail", loginId)
                        .param("password", "correct-password")
                        .session(MockHttpSession())
                        .with(csrf())
                ).andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform?error=true"))

                val locked = userRepository.findByLoginId(loginId).orElseThrow()
                (locked.lockedUntil != null) shouldBe true
                locked.failedLoginAttempts shouldBe 5
            }

            it("실패가 임계값 미만이면 올바른 비밀번호로 정상 로그인되고 카운터가 리셋되어야 한다") {
                val loginId = "recover-${System.nanoTime()}"
                userRepository.save(
                    User(
                        loginId = loginId, name = "복구대상", email = "$loginId@example.com",
                        password = passwordEncodingService.encode("correct-password"), passwordSalt = null
                    )
                )

                repeat(3) {
                    mockMvc.perform(
                        post("/users/login")
                            .param("loginIdOrEmail", loginId)
                            .param("password", "wrong-password")
                            .session(MockHttpSession())
                            .with(csrf())
                    ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/users/loginform?error=true"))
                }

                mockMvc.perform(
                    post("/users/login")
                        .param("loginIdOrEmail", loginId)
                        .param("password", "correct-password")
                        .session(MockHttpSession())
                        .with(csrf())
                ).andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/"))

                val recovered = userRepository.findByLoginId(loginId).orElseThrow()
                recovered.failedLoginAttempts shouldBe 0
                (recovered.lockedUntil == null) shouldBe true
            }
        }
    }
}
