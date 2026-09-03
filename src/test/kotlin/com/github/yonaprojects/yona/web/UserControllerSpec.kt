package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.YonaAuthenticationProvider
import com.github.yonaprojects.yona.domain.issue.RecentIssue
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.user.Email
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserService
import com.github.yonaprojects.yona.domain.user.UserSetting
import com.github.yonaprojects.yona.domain.user.UserSettingRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import io.mockk.clearMocks
import io.mockk.slot
import org.springframework.security.authentication.BadCredentialsException
import org.hamcrest.Matchers
import java.security.MessageDigest
import java.util.Base64

// UserController.hashPassword()와 동일한 알고리즘 재현 (SHA-256 salt + 1024회 스트레칭).
// 컨트롤러의 private 함수를 그대로 흉내내어 changePassword 테스트에서 기대 해시값을 계산하는 데 사용한다.
private fun testHashPassword(password: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.reset()
    digest.update(salt.toByteArray(Charsets.UTF_8))
    var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
    for (i in 1 until 1024) {
        digest.reset()
        hashed = digest.digest(hashed)
    }
    return Base64.getEncoder().encodeToString(hashed)
}

class UserControllerSpec : DescribeSpec({
    val userService = mockk<UserService>()
    val userRepository = mockk<UserRepository>()
    val recentIssueService = mockk<RecentIssueService>()
    val userSettingRepository = mockk<UserSettingRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val yonaAuthenticationProvider = mockk<YonaAuthenticationProvider>()
    val userController = UserController(
        userService, userRepository, recentIssueService, userSettingRepository,
        organizationRepository, yonaAuthenticationProvider, allowedEmailDomains = "", requireAdminConfirm = false
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(userController).build()

    beforeTest {
        clearMocks(userService, userRepository, recentIssueService, userSettingRepository, organizationRepository, yonaAuthenticationProvider)
    }

    describe("UserController 웹 API 테스트") {
        val testUser = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com")
        val auth = UsernamePasswordAuthenticationToken("gildong", "password")

        describe("GET /api/users") {
            it("사용자 검색어에 일치하는 활성 유저 목록을 반환해야 한다") {
                // Given
                every { userRepository.findAll() } returns listOf(testUser)

                // When & Then
                mockMvc.perform(
                    get("/api/users")
                        .param("query", "gildong")
                        .header("referer", "http://localhost/members")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].loginId").value("gildong"))
                    .andExpect(jsonPath("$[0].info").exists())
            }

            it("검색어가 빈 문자열이면 저장소를 조회하지 않고 빈 목록을 반환해야 한다") {
                mockMvc.perform(get("/api/users").param("query", ""))
                    .andExpect(status().isOk)
                    .andExpect(content().json("[]"))

                verify(exactly = 0) { userRepository.findAll() }
            }

            it("아이디는 불일치해도 이름이 일치하면 결과에 포함해야 한다") {
                every { userRepository.findAll() } returns listOf(testUser)

                mockMvc.perform(get("/api/users").param("query", "홍길동"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].loginId").value("gildong"))
            }

            it("탈퇴(DELETED) 상태 사용자는 아이디/이름이 일치해도 결과에서 제외해야 한다") {
                val deletedUser = User(id = 9L, loginId = "gildong2", name = "홍길동2", email = "gildong2@example.com", state = UserState.DELETED)
                every { userRepository.findAll() } returns listOf(deletedUser)

                mockMvc.perform(get("/api/users").param("query", "gildong2"))
                    .andExpect(status().isOk)
                    .andExpect(content().json("[]"))
            }

            it("아이디/이름 어느 쪽도 일치하지 않으면 결과에서 제외해야 한다") {
                every { userRepository.findAll() } returns listOf(testUser)

                mockMvc.perform(get("/api/users").param("query", "존재하지않는검색어"))
                    .andExpect(status().isOk)
                    .andExpect(content().json("[]"))

                verify(exactly = 1) { userRepository.findAll() }
            }
        }

        describe("POST /api/users/emails") {
            it("새로운 보조 이메일을 정상적으로 추가해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                val newEmail = Email(id = 10L, user = testUser, email = "gildong-sub@example.com")
                every { userService.addEmail(1L, "gildong-sub@example.com") } returns newEmail

                // When & Then
                mockMvc.perform(
                    post("/api/users/emails")
                        .param("email", "gildong-sub@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.emailId").value(10))
            }

            it("서비스에서 예외가 발생하면 400과 에러 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.addEmail(1L, "dup@example.com") } throws IllegalArgumentException("이미 등록되었거나 등록 대기 중인 이메일입니다.")

                mockMvc.perform(
                    post("/api/users/emails")
                        .param("email", "dup@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("이미 등록되었거나 등록 대기 중인 이메일입니다."))
            }

            it("인증 정보가 없으면 400을 반환해야 한다") {
                mockMvc.perform(post("/api/users/emails").param("email", "new@example.com"))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
            }

            it("인증은 되었으나 저장소에 사용자가 없으면 400을 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.empty()

                mockMvc.perform(
                    post("/api/users/emails")
                        .param("email", "new@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("User not found"))
            }

            it("저장된 이메일에 id가 없으면 emailId를 0으로 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                val newEmail = Email(id = null, user = testUser, email = "gildong-sub@example.com")
                every { userService.addEmail(1L, "gildong-sub@example.com") } returns newEmail

                mockMvc.perform(
                    post("/api/users/emails")
                        .param("email", "gildong-sub@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.emailId").value(0))
            }

            it("예외 메시지가 없으면 기본 에러 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.addEmail(1L, "dup@example.com") } throws RuntimeException()

                mockMvc.perform(
                    post("/api/users/emails")
                        .param("email", "dup@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Failed to add email"))
            }
        }

        describe("DELETE /api/users/emails/{emailId}") {
            it("보조 이메일을 정상적으로 삭제해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.deleteEmail(1L, 10L) } returns Unit

                // When & Then
                mockMvc.perform(
                    delete("/api/users/emails/10")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }

            it("서비스에서 예외가 발생하면 400과 에러 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.deleteEmail(1L, 10L) } throws IllegalArgumentException("삭제 권한이 없습니다.")

                mockMvc.perform(
                    delete("/api/users/emails/10")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("삭제 권한이 없습니다."))
            }

            it("예외 메시지가 없으면 기본 에러 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.deleteEmail(1L, 10L) } throws RuntimeException()

                mockMvc.perform(
                    delete("/api/users/emails/10")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Failed to delete email"))
            }
        }

        describe("POST /api/users/emails/{emailId}/send-verification") {
            it("기본 포트(80)면 포트 표기 없는 서버 URL로 인증 메일 발송을 요청해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.sendValidationEmail(1L, 10L, "http://localhost") } returns Unit

                mockMvc.perform(
                    post("/api/users/emails/10/send-verification")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))

                verify(exactly = 1) { userService.sendValidationEmail(1L, 10L, "http://localhost") }
            }

            it("표준 포트가 아니면 서버 URL에 포트 번호를 포함해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.sendValidationEmail(1L, 10L, "http://localhost:8080") } returns Unit

                mockMvc.perform(
                    post("/api/users/emails/10/send-verification")
                        .principal(auth)
                        .with { req -> req.serverPort = 8080; req }
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))

                verify(exactly = 1) { userService.sendValidationEmail(1L, 10L, "http://localhost:8080") }
            }

            it("표준 HTTPS 포트(443)면 역시 포트 표기 없는 서버 URL을 사용해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.sendValidationEmail(1L, 10L, "https://localhost") } returns Unit

                mockMvc.perform(
                    post("/api/users/emails/10/send-verification")
                        .principal(auth)
                        .with { req -> req.scheme = "https"; req.serverPort = 443; req }
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))

                verify(exactly = 1) { userService.sendValidationEmail(1L, 10L, "https://localhost") }
            }

            it("서비스에서 예외가 발생하면 400과 에러 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.sendValidationEmail(1L, 10L, "http://localhost") } throws IllegalArgumentException("메일을 보낼 권한이 없습니다.")

                mockMvc.perform(
                    post("/api/users/emails/10/send-verification")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("메일을 보낼 권한이 없습니다."))
            }

            it("예외 메시지가 없으면 기본 에러 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.sendValidationEmail(1L, 10L, "http://localhost") } throws RuntimeException()

                mockMvc.perform(
                    post("/api/users/emails/10/send-verification")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Failed to send verification email"))
            }
        }

        describe("GET /user/isUsed") {
            it("이미 존재하는 아이디면 isExist=true를 반환해야 한다") {
                every { userService.isLoginIdExist("gildong") } returns true
                every { organizationRepository.findByName("gildong") } returns Optional.empty()

                mockMvc.perform(get("/user/isUsed").param("name", "gildong"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.isExist").value(true))
                    .andExpect(jsonPath("$.isReserved").exists())
            }

            it("존재하지 않고 예약어도 아니면 isExist=false, isReserved=false를 반환해야 한다") {
                every { userService.isLoginIdExist("brandnewuser") } returns false
                every { organizationRepository.findByName("brandnewuser") } returns Optional.empty()

                mockMvc.perform(get("/user/isUsed").param("name", "brandnewuser"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.isExist").value(false))
                    .andExpect(jsonPath("$.isReserved").value(false))
            }

            it("사용자 아이디로는 없어도 동명의 조직이 있으면 isExist=true를 반환해야 한다") {
                every { userService.isLoginIdExist("someorg") } returns false
                every { organizationRepository.findByName("someorg") } returns Optional.of(mockk<Organization>(relaxed = true))

                mockMvc.perform(get("/user/isUsed").param("name", "someorg"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.isExist").value(true))
            }
        }

        describe("GET /user/isEmailExist") {
            it("이미 존재하는 이메일이면 isExist=true를 반환해야 한다") {
                every { userService.isEmailExist("gildong@example.com") } returns true

                mockMvc.perform(get("/user/isEmailExist").param("email", "gildong@example.com"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.isExist").value(true))
            }
        }

        describe("POST /api/users/emails/{emailId}/set-main") {
            it("서브 이메일을 기본 이메일로 격상하고 성공 상태를 리턴해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.setAsMainEmail(1L, 10L) } returns Unit

                // When & Then
                mockMvc.perform(
                    post("/api/users/emails/10/set-main")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }

            it("서비스에서 예외가 발생하면 400과 에러 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.setAsMainEmail(1L, 10L) } throws IllegalArgumentException("변경 권한이 없습니다.")

                mockMvc.perform(
                    post("/api/users/emails/10/set-main")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("변경 권한이 없습니다."))
            }

            it("예외 메시지가 없으면 기본 에러 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userService.setAsMainEmail(1L, 10L) } throws RuntimeException()

                mockMvc.perform(
                    post("/api/users/emails/10/set-main")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Failed to set main email"))
            }
        }

        describe("POST /api/users/profile/update") {
            it("프로필 정보를 정상 수정해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userService.isEmailExist("new-mail@example.com") } returns false
                every { userRepository.save(any()) } returns testUser

                // When & Then
                mockMvc.perform(
                    post("/api/users/profile/update")
                        .param("name", "신길동")
                        .param("email", "new-mail@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }

            it("[Test-18-1-2] 프로필 수정 시 이름에 HTML 스크립트가 유입되면 htmlEscape 처리되어 저장되어야 한다") {
                // Given
                val dirtyName = "<script>alert('XSS')</script>길동"
                val expectedCleanName = "&lt;script&gt;alert(&#39;XSS&#39;)&lt;/script&gt;길동"
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userService.isEmailExist("new-mail@example.com") } returns false

                val capturedUser = slot<User>()
                every { userRepository.save(capture(capturedUser)) } answers { capturedUser.captured }

                // When & Then
                mockMvc.perform(
                    post("/api/users/profile/update")
                        .param("name", dirtyName)
                        .param("email", "new-mail@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))

                capturedUser.captured.name shouldBe expectedCleanName
            }

            it("이름이 빈 문자열이면 400과 필수 항목 에러를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(testUser)

                mockMvc.perform(
                    post("/api/users/profile/update")
                        .param("name", "")
                        .param("email", "gildong@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("이름은 필수 항목입니다."))

                verify(exactly = 0) { userRepository.save(any()) }
            }

            it("다른 사용자가 이미 사용 중인 이메일로 변경하려 하면 400을 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userService.isEmailExist("taken@example.com") } returns true

                mockMvc.perform(
                    post("/api/users/profile/update")
                        .param("name", "신길동")
                        .param("email", "taken@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("이미 사용 중인 이메일 주소입니다."))

                verify(exactly = 0) { userRepository.save(any()) }
            }

            it("이메일을 바꾸지 않으면 중복 검사 없이 저장해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userRepository.save(any()) } returns testUser

                mockMvc.perform(
                    post("/api/users/profile/update")
                        .param("name", "신길동")
                        .param("email", testUser.email)
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))

                verify(exactly = 0) { userService.isEmailExist(any()) }
            }

            it("인증 정보가 없으면 400을 반환해야 한다") {
                mockMvc.perform(
                    post("/api/users/profile/update")
                        .param("name", "신길동")
                        .param("email", "new-mail@example.com")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
            }

            it("로그인은 되어 있으나 조회 시점에 사용자가 사라졌으면 400과 User not found를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.empty()

                mockMvc.perform(
                    post("/api/users/profile/update")
                        .param("name", "신길동")
                        .param("email", "new-mail@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("User not found"))
            }

            it("예외 메시지가 없으면 기본 에러 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userService.isEmailExist("new-mail@example.com") } returns false
                every { userRepository.save(any()) } throws RuntimeException()

                mockMvc.perform(
                    post("/api/users/profile/update")
                        .param("name", "신길동")
                        .param("email", "new-mail@example.com")
                        .principal(auth)
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Failed to update profile"))
            }
        }

        describe("POST /api/users/token/reset") {
            it("사용자의 API 접근 토큰을 재생성하여 반환해야 한다") {
                // Given
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userRepository.save(any()) } returns testUser

                // When & Then
                mockMvc.perform(
                    post("/api/users/token/reset")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.token").exists())
            }

            it("인증 정보가 없으면 400을 반환해야 한다") {
                mockMvc.perform(post("/api/users/token/reset"))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
            }

            it("로그인은 되어 있으나 조회 시점에 사용자가 사라졌으면 400과 User not found를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.empty()

                mockMvc.perform(post("/api/users/token/reset").principal(auth))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("User not found"))
            }

            it("예외 메시지가 없으면 기본 에러 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(testUser)
                every { userRepository.save(any()) } throws RuntimeException()

                mockMvc.perform(post("/api/users/token/reset").principal(auth))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Failed to reset token"))
            }
        }

        describe("POST /api/users/password/change") {
            it("현재 비밀번호가 일치하고 새 비밀번호 확인도 일치하면 비밀번호를 변경해야 한다") {
                val salt = "oldsalt1"
                val userWithPw = User(
                    id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com",
                    password = testHashPassword("oldpw", salt), passwordSalt = salt
                )
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(userWithPw)
                every { userRepository.save(any()) } answers { firstArg() }

                mockMvc.perform(
                    post("/api/users/password/change")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"oldPassword": "oldpw", "password": "newpw1", "retypedPassword": "newpw1"}""")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))

                verify(exactly = 1) { userRepository.save(match { it.passwordSalt != salt }) }
            }

            it("현재 비밀번호가 일치하지 않으면 400을 반환해야 한다") {
                val salt = "oldsalt2"
                val userWithPw = User(
                    id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com",
                    password = testHashPassword("realpw", salt), passwordSalt = salt
                )
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(userWithPw)

                mockMvc.perform(
                    post("/api/users/password/change")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"oldPassword": "wrongpw", "password": "newpw1", "retypedPassword": "newpw1"}""")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("현재 비밀번호가 일치하지 않습니다."))
            }

            it("새 비밀번호와 재입력이 일치하지 않으면 400을 반환해야 한다") {
                val salt = "oldsalt3"
                val userWithPw = User(
                    id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com",
                    password = testHashPassword("oldpw", salt), passwordSalt = salt
                )
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(userWithPw)

                mockMvc.perform(
                    post("/api/users/password/change")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"oldPassword": "oldpw", "password": "newpw1", "retypedPassword": "different"}""")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("입력한 새 비밀번호가 일치하지 않습니다."))
            }

            it("새 비밀번호가 4자 미만이면 400을 반환해야 한다") {
                val salt = "oldsalt4"
                val userWithPw = User(
                    id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com",
                    password = testHashPassword("oldpw", salt), passwordSalt = salt
                )
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(userWithPw)

                mockMvc.perform(
                    post("/api/users/password/change")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"oldPassword": "oldpw", "password": "abc", "retypedPassword": "abc"}""")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("비밀번호는 4자 이상이어야 합니다."))
            }

            it("비밀번호 솔트가 없는(레거시) 계정도 빈 문자열 솔트로 검증해야 한다") {
                val userWithoutSalt = User(
                    id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com",
                    password = testHashPassword("oldpw", ""), passwordSalt = null
                )
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(userWithoutSalt)
                every { userRepository.save(any()) } answers { firstArg() }

                mockMvc.perform(
                    post("/api/users/password/change")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"oldPassword": "oldpw", "password": "newpw1", "retypedPassword": "newpw1"}""")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }

            it("인증 정보가 없으면 400을 반환해야 한다") {
                mockMvc.perform(
                    post("/api/users/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"oldPassword": "oldpw", "password": "newpw1", "retypedPassword": "newpw1"}""")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
            }

            it("로그인은 되어 있으나 조회 시점에 사용자가 사라졌으면 400과 User not found를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.empty()

                mockMvc.perform(
                    post("/api/users/password/change")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"oldPassword": "oldpw", "password": "newpw1", "retypedPassword": "newpw1"}""")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("User not found"))
            }

            it("예외 메시지가 없으면 기본 에러 메시지를 반환해야 한다") {
                val salt = "oldsalt5"
                val userWithPw = User(
                    id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com",
                    password = testHashPassword("oldpw", salt), passwordSalt = salt
                )
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.findById(1L) } returns Optional.of(userWithPw)
                every { userRepository.save(any()) } throws RuntimeException()

                mockMvc.perform(
                    post("/api/users/password/change")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"oldPassword": "oldpw", "password": "newpw1", "retypedPassword": "newpw1"}""")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value("Failed to change password"))
            }
        }

        describe("GET /api/users/me/recent-issues (P1-41)") {
            it("로그인한 사용자의 최근 방문 이슈/게시글 목록을 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { recentIssueService.getRecentIssues(testUser) } returns listOf(
                    RecentIssue(id = 1L, userId = 1L, issueId = 5L, title = "최근 본 이슈", url = "/owner/proj/issue/1")
                )

                mockMvc.perform(
                    get("/api/users/me/recent-issues")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].title").value("최근 본 이슈"))
                    .andExpect(jsonPath("$[0].url").value("/owner/proj/issue/1"))
            }

            it("로그인하지 않았다면 401을 반환해야 한다") {
                mockMvc.perform(get("/api/users/me/recent-issues"))
                    .andExpect(status().isUnauthorized)
            }

            it("인증은 되었으나 저장소에 사용자가 없으면 401을 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.empty()

                mockMvc.perform(get("/api/users/me/recent-issues").principal(auth))
                    .andExpect(status().isUnauthorized)
            }
        }

        // yona UserApp.java:1372-1380 setDefaultLoginPage() 대응 (P2-11)
        describe("POST /user/setDefaultLoginPage") {
            it("설정이 없던 사용자면 새로 만들어 기본 페이지를 저장해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userSettingRepository.findByUserId(1L) } returns Optional.empty()
                every { userSettingRepository.save(any()) } answers { firstArg() }

                mockMvc.perform(
                    post("/user/setDefaultLoginPage")
                        .param("path", "notifications")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.defaultLoginPage").value("notifications"))

                verify(exactly = 1) { userSettingRepository.save(match { it.loginDefaultPage == "notifications" && it.user == testUser }) }
            }

            it("이미 설정이 있던 사용자면 기존 설정을 갱신해야 한다") {
                val existing = UserSetting(id = 100L, user = testUser, loginDefaultPage = "issues")
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userSettingRepository.findByUserId(1L) } returns Optional.of(existing)
                every { userSettingRepository.save(any()) } answers { firstArg() }

                mockMvc.perform(
                    post("/user/setDefaultLoginPage")
                        .param("path", "pull-requests")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.defaultLoginPage").value("pull-requests"))

                verify(exactly = 1) { userSettingRepository.save(match { it.id == 100L && it.loginDefaultPage == "pull-requests" }) }
            }

            it("인증 정보가 없으면 IllegalArgumentException을 던져야 한다") {
                // 이 엔드포인트는 try-catch가 없어 예외가 그대로 컨트롤러 밖으로 전파된다
                shouldThrow<IllegalArgumentException> {
                    userController.setDefaultLoginPage("notifications", null)
                }.message shouldBe "Unauthorized"
            }

            // legacy 경로 별칭 (P2-60) — `/-_-api/v1/user/defultLoginPage`(legacy 오타 포함)로도 동일하게 동작해야 한다
            it("legacy 경로 /-_-api/v1/user/defultLoginPage로도 동일하게 동작해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userSettingRepository.findByUserId(1L) } returns Optional.empty()
                every { userSettingRepository.save(any()) } answers { firstArg() }

                mockMvc.perform(
                    post("/-_-api/v1/user/defultLoginPage")
                        .param("path", "notifications")
                        .principal(auth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.defaultLoginPage").value("notifications"))
            }

            it("로그인은 되어 있으나 저장소에 사용자가 없으면 IllegalArgumentException을 던져야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.empty()

                shouldThrow<IllegalArgumentException> {
                    userController.setDefaultLoginPage("notifications", auth)
                }.message shouldBe "User not found"
            }
        }

        // yona UserApi.java:218-241 newUser() 대응 (P1-118). [GL-controllers_api_UserApi-014]
        describe("POST /api/users") {
            val siteManager = User(id = 2L, loginId = "admin", name = "관리자", email = "admin@example.com", state = UserState.SITE_ADMIN)
            val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")

            it("사이트관리자가 아니면 400 Bad Request를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)

                mockMvc.perform(
                    post("/api/users")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"users": [{"loginId": "newbie", "name": "새사람", "email": "newbie@example.com"}]}""")
                )
                    .andExpect(status().isBadRequest)
            }

            it("인증 정보가 없으면 400 Bad Request를 반환해야 한다") {
                mockMvc.perform(
                    post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"users": [{"loginId": "newbie", "name": "새사람", "email": "newbie@example.com"}]}""")
                )
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.message").value("User creation with api is allowed by Site admin only."))
            }

            it("허용된 이메일 도메인이 아니면 결과 배열의 해당 항목에 403을 담아야 한다") {
                val restrictedController = UserController(
                    userService, userRepository, recentIssueService, userSettingRepository,
                    organizationRepository, yonaAuthenticationProvider,
                    allowedEmailDomains = "example.com", requireAdminConfirm = false
                )
                val restrictedMockMvc = MockMvcBuilders.standaloneSetup(restrictedController).build()
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)

                restrictedMockMvc.perform(
                    post("/api/users")
                        .principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"users": [{"loginId": "outsider", "name": "외부인", "email": "outsider@other.com"}]}""")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$[0].status").value(403))
                    .andExpect(jsonPath("$[0].message").value("허용되지 않은 이메일 도메인입니다."))

                verify(exactly = 0) { userService.createUser(any()) }
            }

            it("관리자 승인이 필요한 설정이면 생성된 사용자를 LOCKED 상태로 만들어야 한다") {
                val confirmRequiredController = UserController(
                    userService, userRepository, recentIssueService, userSettingRepository,
                    organizationRepository, yonaAuthenticationProvider,
                    allowedEmailDomains = "", requireAdminConfirm = true
                )
                val confirmRequiredMockMvc = MockMvcBuilders.standaloneSetup(confirmRequiredController).build()
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByEmail("locked-new@example.com") } returns Optional.empty()
                every { userService.createUser(any()) } answers { (firstArg() as User).apply { id = 100L } }

                confirmRequiredMockMvc.perform(
                    post("/api/users")
                        .principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"users": [{"loginId": "lockeduser", "name": "잠김예정", "email": "locked-new@example.com"}]}""")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$[0].status").value(201))

                verify(exactly = 1) { userService.createUser(match { it.state == UserState.LOCKED }) }
            }

            it("사이트관리자면 신규 사용자를 생성하고 201과 결과 배열을 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByEmail("newbie@example.com") } returns Optional.empty()
                every { userService.createUser(any()) } answers {
                    (firstArg() as User).apply { id = 99L }
                }

                mockMvc.perform(
                    post("/api/users")
                        .principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"users": [{"loginId": "newbie", "name": "새사람", "email": "newbie@example.com"}]}""")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$[0].status").value(201))
                    .andExpect(jsonPath("$[0].user.loginId").value("newbie"))

                verify(exactly = 1) { userService.createUser(match { it.loginId == "newbie" && it.email == "newbie@example.com" }) }
            }

            it("이미 존재하는 이메일이면 결과 배열의 해당 항목에 409를 담아야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByEmail("dup@example.com") } returns Optional.of(testUser)

                mockMvc.perform(
                    post("/api/users")
                        .principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"users": [{"loginId": "dup", "name": "중복", "email": "dup@example.com"}]}""")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$[0].status").value(409))

                verify(exactly = 0) { userService.createUser(any()) }
            }
        }

        // yona UserApi.java:244-265 newToken() 대응 (P1-118). [GL-controllers_api_UserApi-015]
        describe("POST /api/users/token") {
            it("존재하지 않는 아이디/이메일이면 401과 No valid user by id를 반환해야 한다") {
                every { userRepository.findByLoginId("nobody") } returns Optional.empty()
                every { userRepository.findByEmail("nobody") } returns Optional.empty()

                mockMvc.perform(
                    post("/api/users/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"id": "nobody", "password": "pw"}""")
                )
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.message").value("No valid user by id"))
            }

            it("잠긴 계정이면 401과 No valid user by id를 반환해야 한다") {
                val locked = User(id = 3L, loginId = "locked", name = "잠김", email = "locked@example.com", state = UserState.LOCKED)
                every { userRepository.findByLoginId("locked") } returns Optional.of(locked)

                mockMvc.perform(
                    post("/api/users/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"id": "locked", "password": "pw"}""")
                )
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.message").value("No valid user by id"))
            }

            it("탈퇴(DELETED) 상태 계정이면 401과 No valid user by id를 반환해야 한다") {
                val deleted = User(id = 4L, loginId = "gone", name = "탈퇴자", email = "gone@example.com", state = UserState.DELETED)
                every { userRepository.findByLoginId("gone") } returns Optional.of(deleted)

                mockMvc.perform(
                    post("/api/users/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"id": "gone", "password": "pw"}""")
                )
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.message").value("No valid user by id"))
            }

            it("비밀번호가 틀리면 401과 No user by id and password를 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every {
                    yonaAuthenticationProvider.authenticate(match<UsernamePasswordAuthenticationToken> { it.name == "gildong" })
                } throws BadCredentialsException("비밀번호가 일치하지 않습니다.")

                mockMvc.perform(
                    post("/api/users/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"id": "gildong", "password": "wrong"}""")
                )
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.message").value("No user by id and password"))
            }

            it("아이디/비밀번호가 맞으면 새 토큰을 발급하고 저장해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every {
                    yonaAuthenticationProvider.authenticate(match<UsernamePasswordAuthenticationToken> { it.name == "gildong" })
                } returns UsernamePasswordAuthenticationToken("gildong", "correct")
                every { userRepository.save(any()) } answers { firstArg() }

                mockMvc.perform(
                    post("/api/users/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"id": "gildong", "password": "correct"}""")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.access_token").isNotEmpty)

                verify(exactly = 1) { userRepository.save(match { it.token != null }) }
            }
        }

        // yona UserApi.java:320-339 users() 대응 (P1-118). [GL-controllers_api_UserApi-018;GL-controllers_api_UserApi-019]
        describe("GET /api/admin/users") {
            val siteManager = User(id = 2L, loginId = "admin", name = "관리자", email = "admin@example.com", state = UserState.SITE_ADMIN)
            val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")

            it("사이트관리자가 아니면 403을 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)

                mockMvc.perform(get("/api/admin/users").principal(auth))
                    .andExpect(status().isForbidden)
            }

            it("인증 정보가 없으면 403을 반환해야 한다") {
                mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isForbidden)
            }

            it("사이트관리자면 ACTIVE 사용자 목록을 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByState(UserState.ACTIVE) } returns listOf(testUser)

                mockMvc.perform(get("/api/admin/users").principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].login_id").value("gildong"))
            }
        }

        // yona UserApi.java:341-379 updateUserState() 대응 (P1-118). [GL-controllers_api_UserApi-020;GL-controllers_api_UserApi-021]
        describe("PATCH /api/admin/users/{loginId}") {
            val siteManager = User(id = 2L, loginId = "admin", name = "관리자", email = "admin@example.com", state = UserState.SITE_ADMIN)
            val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")

            it("사이트관리자가 아니면 403을 반환해야 한다") {
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)

                mockMvc.perform(
                    patch("/api/admin/users/gildong").principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"state": "LOCKED"}""")
                )
                    .andExpect(status().isForbidden)
            }

            it("인증 정보가 없으면 403을 반환해야 한다") {
                mockMvc.perform(
                    patch("/api/admin/users/gildong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"state": "LOCKED"}""")
                )
                    .andExpect(status().isForbidden)
            }

            it("대상 사용자를 찾을 수 없으면 401을 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByLoginId("nobody") } returns Optional.empty()

                mockMvc.perform(
                    patch("/api/admin/users/nobody").principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"state": "LOCKED"}""")
                )
                    .andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 상태값이면 400을 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)

                mockMvc.perform(
                    patch("/api/admin/users/gildong").principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"state": "NOT_A_REAL_STATE"}""")
                )
                    .andExpect(status().isBadRequest)
            }

            it("SITE_ADMIN 상태로 변경 요청하면 403을 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)

                mockMvc.perform(
                    patch("/api/admin/users/gildong").principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"state": "SITE_ADMIN"}""")
                )
                    .andExpect(status().isForbidden)
            }

            it("정상 상태값이면 사용자 상태를 변경하고 200을 반환해야 한다") {
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { userRepository.findByLoginId("gildong") } returns Optional.of(testUser)
                every { userRepository.save(any()) } answers { firstArg() }

                mockMvc.perform(
                    patch("/api/admin/users/gildong").principal(adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"state": "LOCKED"}""")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.state").value("LOCKED"))

                verify(exactly = 1) { userRepository.save(match { it.state == UserState.LOCKED }) }
            }
        }
    }
})
