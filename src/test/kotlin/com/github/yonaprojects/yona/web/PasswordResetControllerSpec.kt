package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.user.PasswordResetService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import io.mockk.clearMocks

class PasswordResetControllerSpec : DescribeSpec({
    val passwordResetService = mockk<PasswordResetService>()
    val userRepository = mockk<UserRepository>()
    val mailService = mockk<MailService>()
    val passwordResetController = PasswordResetController(passwordResetService, userRepository, mailService, "Yona")
    val mockMvc = MockMvcBuilders.standaloneSetup(passwordResetController).build()

    beforeTest {
        clearMocks(passwordResetService, userRepository, mailService)
    }

    describe("PasswordResetController 웹 API 테스트") {
        val testUser = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com")

        describe("GET /lostPassword") {
            it("비밀번호 찾기 메일 신청 화면을 반환해야 한다") {
                mockMvc.perform(get("/lostPassword"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/lostPassword"))
                    .andExpect(model().attribute("siteName", "Yona"))
            }
        }

        describe("POST /lostPassword") {
            it("존재하지 않는 로그인ID면 뷰와 에러메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("nosuch") } returns Optional.empty()

                mockMvc.perform(
                    post("/lostPassword")
                        .param("loginId", "nosuch")
                        .param("emailAddress", "nosuch@example.com")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/lostPassword"))
                    .andExpect(model().attributeExists("errorMessage"))

                verify(exactly = 0) { mailService.sendHtmlMail(any(), any(), any(), any()) }
            }

            // legacy sendPasswordResetMail()도 메일 발송 실패(EmailException)를 로그만 남기고 화면에는
            // 아무것도 노출하지 않는다 — catch 블록 자체를 태우는 테스트.
            it("메일 발송이 예외로 실패해도 화면에는 에러를 노출하지 않고 정상 응답해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { passwordResetService.generateResetHash("gildong") } returns "reset-hash-token"
                every { passwordResetService.addHashToResetTable("gildong", "reset-hash-token") } returns Unit
                every { mailService.sendHtmlMail(any(), any(), any(), any()) } throws RuntimeException("smtp down")

                mockMvc.perform(
                    post("/lostPassword")
                        .param("loginId", "gildong")
                        .param("emailAddress", "gildong@example.com")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/lostPassword"))
                    .andExpect(model().attributeDoesNotExist("isSent"))
                    .andExpect(model().attributeDoesNotExist("errorMessage"))
            }
            it("아이디와 이메일이 일치하면 비밀번호 재설정 링크 메일을 전송해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { passwordResetService.generateResetHash("gildong") } returns "reset-hash-token"
                every { passwordResetService.addHashToResetTable("gildong", "reset-hash-token") } returns Unit
                every { mailService.sendHtmlMail(any(), any(), any(), any()) } returns Unit

                // When & Then
                mockMvc.perform(
                    post("/lostPassword")
                        .param("loginId", "gildong")
                        .param("emailAddress", "gildong@example.com")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/lostPassword"))
                    .andExpect(model().attributeExists("isSent"))

                verify(exactly = 1) { mailService.sendHtmlMail("gildong@example.com", "홍길동", any(), any()) }
            }

            // getServerUrl()의 `serverPort == 80 || serverPort == 443` 분기 — 기본 테스트 포트(80)가
            // 아닌 값을 명시적으로 설정해 else 경로(":포트" 포함)를 태운다.
            it("표준 포트(80/443)가 아니면 재설정 링크에 포트를 포함해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { passwordResetService.generateResetHash("gildong") } returns "reset-hash-token"
                every { passwordResetService.addHashToResetTable("gildong", "reset-hash-token") } returns Unit
                val bodySlot = io.mockk.slot<String>()
                every { mailService.sendHtmlMail(any(), any(), any(), capture(bodySlot)) } returns Unit

                mockMvc.perform(
                    post("/lostPassword")
                        .param("loginId", "gildong")
                        .param("emailAddress", "gildong@example.com")
                        .with { request -> request.serverPort = 8443; request }
                )
                    .andExpect(status().isOk)

                bodySlot.captured shouldContain ":8443"
            }

            it("아이디와 이메일 정보가 다르면 뷰와 에러메시지를 반환해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)

                // When & Then
                mockMvc.perform(
                    post("/lostPassword")
                        .param("loginId", "gildong")
                        .param("emailAddress", "wrong@example.com")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/lostPassword"))
                    .andExpect(model().attributeExists("errorMessage"))
            }
        }

        describe("GET /user/reset-password") {
            it("유효한 토큰이면 비밀번호 변경을 요청할 수 있는 입력 폼을 제공해야 한다") {
                // Given
                every { passwordResetService.isValidResetHash("reset-hash-token") } returns true

                // When & Then
                mockMvc.perform(
                    get("/user/reset-password")
                        .param("hash", "reset-hash-token")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/resetPassword"))
                    .andExpect(model().attribute("hash", "reset-hash-token"))
            }

            it("만료되거나 잘못된 토큰이면 뷰와 에러메시지를 반환해야 한다") {
                // Given
                every { passwordResetService.isValidResetHash("invalid-hash") } returns false

                // When & Then
                mockMvc.perform(
                    get("/user/reset-password")
                        .param("hash", "invalid-hash")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/resetPassword"))
                    .andExpect(model().attributeExists("errorMessage"))
            }
        }

        describe("POST /user/reset-password") {
            it("토큰 검증 성공 후 새로운 비밀번호로 정상 재설정하고 로그인 화면으로 리다이렉트해야 한다") {
                // Given
                every { passwordResetService.resetPassword("reset-hash-token", "newPass123") } returns true

                // When & Then
                mockMvc.perform(
                    post("/user/reset-password")
                        .param("hashString", "reset-hash-token")
                        .param("password", "newPass123")
                        .param("retypedPassword", "newPass123")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("입력한 두 비밀번호가 일치하지 않으면 서비스 호출 없이 뷰와 에러메시지를 반환해야 한다") {
                mockMvc.perform(
                    post("/user/reset-password")
                        .param("hashString", "reset-hash-token")
                        .param("password", "newPass123")
                        .param("retypedPassword", "differentPass")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/resetPassword"))
                    .andExpect(model().attribute("hash", "reset-hash-token"))
                    .andExpect(model().attributeExists("errorMessage"))

                verify(exactly = 0) { passwordResetService.resetPassword(any(), any()) }
            }

            it("비밀번호는 일치하지만 서비스가 실패(만료 등)를 반환하면 뷰와 에러메시지를 반환해야 한다") {
                every { passwordResetService.resetPassword("expired-hash", "newPass123") } returns false

                mockMvc.perform(
                    post("/user/reset-password")
                        .param("hashString", "expired-hash")
                        .param("password", "newPass123")
                        .param("retypedPassword", "newPass123")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/resetPassword"))
                    .andExpect(model().attribute("hash", "expired-hash"))
                    .andExpect(model().attributeExists("errorMessage"))
            }
        }
    }
})
