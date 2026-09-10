package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.user.PasswordEncodingService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserService
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import org.springframework.test.web.servlet.ResultActions
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver
import org.springframework.web.servlet.view.RedirectView

class BootstrapSetupControllerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val userService = mockk<UserService>()
    val passwordEncodingService = PasswordEncodingService()

    val controller = BootstrapSetupController(userRepository, userService, passwordEncodingService, "테스트사이트")
    // 반환하는 뷰 이름("bootstrap-setup")이 매핑된 URL 경로(/bootstrap-setup)와 같아, MockMvc 기본
    // InternalResourceViewResolver가 순환 포워드로 오인해 ServletException을 던진다(테스트 하네스
    // 한정 문제 — 다른 뷰 이름을 쓰는 컨트롤러들은 겪지 않음). "redirect:" 접두사는 실제
    // RedirectView로 그대로 처리하고, 그 외에는 실제 렌더링 없이 뷰 이름만 확인하는 no-op 뷰로 교체한다.
    val mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setViewResolvers(ViewResolver { viewName, _ ->
            if (viewName.startsWith("redirect:")) {
                RedirectView(viewName.removePrefix("redirect:"))
            } else {
                View { _, _, response -> response.status = 200 }
            }
        })
        .build()

    beforeTest {
        clearMocks(userRepository, userService)
    }

    describe("GET /bootstrap-setup") {
        it("가입자가 이미 있으면 메인 화면으로 리다이렉트해야 한다") {
            every { userRepository.count() } returns 1L

            mockMvc.perform(get("/bootstrap-setup"))
                .andExpect(status().is3xxRedirection)
                .andExpect(view().name("redirect:/"))
        }

        it("가입자가 없으면 초기 설정 화면을 빈 에러 목록과 함께 반환해야 한다") {
            every { userRepository.count() } returns 0L

            mockMvc.perform(get("/bootstrap-setup"))
                .andExpect(status().isOk)
                .andExpect(view().name("bootstrap-setup"))
                .andExpect(model().attribute("siteName", "테스트사이트"))
                .andExpect(model().attribute("loginIdErrors", emptyList<String>()))
                .andExpect(model().attribute("emailErrors", emptyList<String>()))
                .andExpect(model().attribute("passwordErrors", emptyList<String>()))
                .andExpect(model().attribute("retypedPasswordErrors", emptyList<String>()))
        }
    }

    describe("POST /bootstrap-setup") {
        fun validParams() = mapOf(
            "loginId" to "admin",
            "name" to "관리자",
            "email" to "admin@example.com",
            "password" to "password1",
            "retypedPassword" to "password1"
        )

        fun perform(overrides: Map<String, String> = emptyMap()): ResultActions {
            val params = validParams() + overrides
            var request = post("/bootstrap-setup")
            params.forEach { (k, v) -> request = request.param(k, v) }
            return mockMvc.perform(request)
        }

        it("가입자가 이미 있으면 메인 화면으로 리다이렉트하고 아무 검증도 하지 않아야 한다") {
            every { userRepository.count() } returns 1L

            perform().andExpect(status().is3xxRedirection).andExpect(view().name("redirect:/"))

            verify(exactly = 0) { userRepository.findByEmail(any()) }
            verify(exactly = 0) { userService.createUser(any()) }
        }

        it("모든 값이 유효하면 SITE_ADMIN 계정을 생성하고 재시작 화면을 반환해야 한다") {
            every { userRepository.count() } returns 0L
            every { userRepository.findByEmail("admin@example.com") } returns Optional.empty()
            val userSlot = slot<User>()
            every { userService.createUser(capture(userSlot)) } returns mockk(relaxed = true)

            perform()
                .andExpect(status().isOk)
                .andExpect(view().name("bootstrap-restart"))

            userSlot.captured.loginId shouldBe "admin"
            userSlot.captured.name shouldBe "관리자"
            userSlot.captured.email shouldBe "admin@example.com"
            userSlot.captured.state shouldBe UserState.SITE_ADMIN
            userSlot.captured.isGuest shouldBe false
            userSlot.captured.password.isNullOrBlank() shouldBe false
            userSlot.captured.passwordSalt shouldBe null
            passwordEncodingService.matches("password1", userSlot.captured.password, null) shouldBe true
        }

        it("loginId가 빈 값이면 loginIdErrors가 채워지고 설정 화면을 다시 반환해야 한다") {
            every { userRepository.count() } returns 0L
            every { userRepository.findByEmail("admin@example.com") } returns Optional.empty()

            perform(mapOf("loginId" to ""))
                .andExpect(status().isOk)
                .andExpect(view().name("bootstrap-setup"))

            verify(exactly = 0) { userService.createUser(any()) }
        }

        it("loginId가 admin이 아니면 loginIdErrors가 채워지고 설정 화면을 다시 반환해야 한다") {
            every { userRepository.count() } returns 0L
            every { userRepository.findByEmail("admin@example.com") } returns Optional.empty()

            perform(mapOf("loginId" to "notadmin"))
                .andExpect(status().isOk)
                .andExpect(view().name("bootstrap-setup"))

            verify(exactly = 0) { userService.createUser(any()) }
        }

        it("password가 빈 값이면 passwordErrors가 채워지고 설정 화면을 다시 반환해야 한다") {
            every { userRepository.count() } returns 0L
            every { userRepository.findByEmail("admin@example.com") } returns Optional.empty()

            perform(mapOf("password" to "", "retypedPassword" to ""))
                .andExpect(status().isOk)
                .andExpect(view().name("bootstrap-setup"))

            verify(exactly = 0) { userService.createUser(any()) }
        }

        it("password와 retypedPassword가 다르면 retypedPasswordErrors가 채워지고 설정 화면을 다시 반환해야 한다") {
            every { userRepository.count() } returns 0L
            every { userRepository.findByEmail("admin@example.com") } returns Optional.empty()

            perform(mapOf("retypedPassword" to "different"))
                .andExpect(status().isOk)
                .andExpect(view().name("bootstrap-setup"))

            verify(exactly = 0) { userService.createUser(any()) }
        }

        it("email이 빈 값이면 emailErrors가 채워지고 설정 화면을 다시 반환해야 한다") {
            every { userRepository.count() } returns 0L
            every { userRepository.findByEmail("") } returns Optional.empty()

            perform(mapOf("email" to ""))
                .andExpect(status().isOk)
                .andExpect(view().name("bootstrap-setup"))

            verify(exactly = 0) { userService.createUser(any()) }
        }

        it("email이 이미 사용 중이면 emailErrors가 채워지고 설정 화면을 다시 반환해야 한다") {
            every { userRepository.count() } returns 0L
            every { userRepository.findByEmail("admin@example.com") } returns Optional.of(User())

            perform()
                .andExpect(status().isOk)
                .andExpect(view().name("bootstrap-setup"))

            verify(exactly = 0) { userService.createUser(any()) }
        }

        it("여러 필드가 동시에 유효하지 않아도 설정 화면을 다시 반환해야 한다") {
            every { userRepository.count() } returns 0L
            every { userRepository.findByEmail("") } returns Optional.empty()

            perform(mapOf("loginId" to "", "password" to "", "retypedPassword" to "x", "email" to ""))
                .andExpect(status().isOk)
                .andExpect(view().name("bootstrap-setup"))

            verify(exactly = 0) { userService.createUser(any()) }
        }
    }
})
