package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.view.InternalResourceViewResolver
import io.mockk.clearMocks
import io.mockk.slot
import com.github.yonaprojects.yona.domain.user.UserState

class AuthControllerSpec : DescribeSpec({
    val userService = mockk<UserService>()
    val authController = AuthController(userService, "", false)
    val viewResolver = InternalResourceViewResolver().apply {
        setPrefix("/templates/")
        setSuffix(".html")
    }
    val mockMvc = MockMvcBuilders.standaloneSetup(authController)
        .setViewResolvers(viewResolver)
        .build()

    beforeTest {
        clearMocks(userService)
    }

    describe("AuthController") {
        describe("GET /login") {
            it("로그인 폼 페이지로 리다이렉트되어야 한다") {
                mockMvc.perform(get("/login"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("에러가 있을 경우 에러 파라미터를 담아 리다이렉트되어야 한다") {
                mockMvc.perform(get("/login").param("error", "true"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform?error=true"))
            }

            it("로그아웃 파라미터가 있을 경우 로그아웃 파라미터를 담아 리다이렉트되어야 한다") {
                mockMvc.perform(get("/login").param("logout", "true"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform?logout=true"))
            }
        }

        describe("GET /users/loginform") {
            it("로그인 페이지가 정상 반환되어야 한다") {
                mockMvc.perform(get("/users/loginform"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("login"))
            }

            it("에러가 있을 경우 에러 메시지가 모델에 적재되어야 한다") {
                mockMvc.perform(get("/users/loginform").param("error", "true"))
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("loginError"))
                    .andExpect(view().name("login"))
            }

            it("로그아웃 파라미터가 있을 경우 로그아웃 메시지가 모델에 적재되어야 한다") {
                mockMvc.perform(get("/users/loginform").param("logout", "true"))
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("logoutMessage"))
                    .andExpect(view().name("login"))
            }
        }

        describe("GET /users/logout") {
            it("/logout으로 리다이렉트되어야 한다") {
                mockMvc.perform(get("/users/logout"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/logout"))
            }
        }

        describe("GET /signup") {
            it("회원가입 페이지가 정상 반환되어야 한다") {
                mockMvc.perform(get("/signup"))
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("user"))
                    .andExpect(view().name("signup"))
            }

            it("대체 경로(/users/signupform)도 동일하게 회원가입 페이지를 반환해야 한다") {
                mockMvc.perform(get("/users/signupform"))
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("user"))
                    .andExpect(view().name("signup"))
            }
        }

        describe("POST /signup") {
            it("회원가입 요청 시 정상 가입 후 로그인 페이지로 리다이렉트되어야 한다") {
                // Given
                every { userService.isLoginIdExist("gildong") } returns false
                val user = User(id = 1L, loginId = "gildong", name = "홍길동", email = "gildong@example.com")
                every { userService.createUser(any()) } returns user

                // When & Then
                mockMvc.perform(
                    post("/signup")
                        .param("loginId", "gildong")
                        .param("name", "홍길동")
                        .param("email", "gildong@example.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "pass123")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform?signupSuccess"))

                verify(exactly = 1) { userService.createUser(any()) }
            }

            // yona UserApp.java:1218-1224 isUsingSignUpConfirm()/:1260-1275 createNewUser() 대응 (P1-77).
            it("관리자 승인 대기 설정이 켜져 있으면 신규 유저가 LOCKED 상태로 생성되고 승인 대기 안내로 리다이렉트되어야 한다") {
                val confirmController = AuthController(userService, "", true)
                val confirmViewResolver = InternalResourceViewResolver().apply {
                    setPrefix("/templates/")
                    setSuffix(".html")
                }
                val confirmMockMvc = MockMvcBuilders.standaloneSetup(confirmController)
                    .setViewResolvers(confirmViewResolver)
                    .build()

                every { userService.isLoginIdExist("gildong") } returns false
                val savedUserSlot = slot<User>()
                every { userService.createUser(capture(savedUserSlot)) } answers { savedUserSlot.captured }

                confirmMockMvc.perform(
                    post("/signup")
                        .param("loginId", "gildong")
                        .param("name", "홍길동")
                        .param("email", "gildong@example.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "pass123")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform?signupRequested"))

                savedUserSlot.captured.state shouldBe UserState.LOCKED
            }

            it("관리자 승인 대기 설정이 꺼져 있으면(기본값) 기존과 동일하게 즉시 활성 상태로 생성되어야 한다") {
                every { userService.isLoginIdExist("gildong") } returns false
                val savedUserSlot = slot<User>()
                every { userService.createUser(capture(savedUserSlot)) } answers { savedUserSlot.captured }

                mockMvc.perform(
                    post("/signup")
                        .param("loginId", "gildong")
                        .param("name", "홍길동")
                        .param("email", "gildong@example.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "pass123")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform?signupSuccess"))

                savedUserSlot.captured.state shouldBe UserState.ACTIVE
            }

            it("비밀번호 재입력이 일치하지 않으면 회원가입 폼이 유지되어야 한다") {
                // Given
                every { userService.isLoginIdExist("gildong") } returns false

                // When & Then
                mockMvc.perform(
                    post("/signup")
                        .param("loginId", "gildong")
                        .param("name", "홍길동")
                        .param("email", "gildong@example.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "differentPass")
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("passwordError"))
                    .andExpect(view().name("signup"))

                verify(exactly = 0) { userService.createUser(any()) }
            }

            it("허용된 이메일 도메인 설정이 있고 그 목록에 없는 도메인이면 가입이 거부되어야 한다") {
                val restrictedController = AuthController(userService, "allowed.com", false)
                val restrictedViewResolver = InternalResourceViewResolver().apply {
                    setPrefix("/templates/")
                    setSuffix(".html")
                }
                val restrictedMockMvc = MockMvcBuilders.standaloneSetup(restrictedController)
                    .setViewResolvers(restrictedViewResolver)
                    .build()
                every { userService.isLoginIdExist("gildong") } returns false

                restrictedMockMvc.perform(
                    post("/signup")
                        .param("loginId", "gildong")
                        .param("name", "홍길동")
                        .param("email", "gildong@notallowed.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "pass123")
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attributeExists("emailDomainError"))
                    .andExpect(view().name("signup"))

                verify(exactly = 0) { userService.createUser(any()) }
            }

            it("아이디가 예약어면 회원가입이 거부되어야 한다(P2-01)") {
                every { userService.isLoginIdExist("projects") } returns false

                mockMvc.perform(
                    post("/signup")
                        .param("loginId", "projects")
                        .param("name", "홍길동")
                        .param("email", "gildong@example.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "pass123")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("signup"))

                verify(exactly = 0) { userService.createUser(any()) }
            }

            // yona models/User.java:65-66,80 LOGIN_ID_PATTERN(@Pattern) 대응 (P1-104).
            it("아이디에 공백이 포함되면 회원가입이 거부되어야 한다") {
                every { userService.isLoginIdExist("gil dong") } returns false

                mockMvc.perform(
                    post("/signup")
                        .param("loginId", "gil dong")
                        .param("name", "홍길동")
                        .param("email", "gildong@example.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "pass123")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("signup"))

                verify(exactly = 0) { userService.createUser(any()) }
            }

            it("이미 존재하는 아이디면 회원가입이 거부되어야 한다") {
                every { userService.isLoginIdExist("gildong") } returns true

                mockMvc.perform(
                    post("/signup")
                        .param("loginId", "gildong")
                        .param("name", "홍길동")
                        .param("email", "gildong@example.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "pass123")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("signup"))

                verify(exactly = 0) { userService.createUser(any()) }
            }

            it("대체 경로(/users/signup)로도 정상 가입되어야 한다") {
                every { userService.isLoginIdExist("gildong") } returns false
                every { userService.createUser(any()) } returns User(loginId = "gildong", name = "홍길동")

                mockMvc.perform(
                    post("/users/signup")
                        .param("loginId", "gildong")
                        .param("name", "홍길동")
                        .param("email", "gildong@example.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "pass123")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform?signupSuccess"))

                verify(exactly = 1) { userService.createUser(any()) }
            }

            it("아이디 형식이 올바르면(영문/숫자/한글/하이픈) 회원가입이 정상 진행되어야 한다") {
                every { userService.isLoginIdExist("gil-dong123") } returns false
                every { userService.createUser(any()) } returns User(loginId = "gil-dong123", name = "홍길동")

                mockMvc.perform(
                    post("/signup")
                        .param("loginId", "gil-dong123")
                        .param("name", "홍길동")
                        .param("email", "gildong@example.com")
                        .param("password", "pass123")
                        .param("retypedPassword", "pass123")
                )
                    .andExpect(status().is3xxRedirection)

                verify(exactly = 1) { userService.createUser(any()) }
            }
        }
    }
})
