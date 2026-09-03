package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.service.MigrationService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona conf/routes의 마이그레이션 화면(외부 서비스에서 데이터를 가져오는 진입점) 대응.
class MigrationViewControllerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val migrationService = mockk<MigrationService>()

    val controller = MigrationViewController(userRepository, migrationService)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(userRepository, migrationService)
    }

    describe("GET /migration") {
        it("마이그레이션이 허용되지 않으면 error/403 뷰를 반환해야 한다") {
            every { migrationService.isAllowMigration() } returns false

            mockMvc.perform(get("/migration"))
                .andExpect(status().isOk)
                .andExpect(view().name("error/403"))
        }

        it("로그인하지 않았으면 로그인 폼으로 리다이렉트해야 한다") {
            every { migrationService.isAllowMigration() } returns true

            mockMvc.perform(get("/migration"))
                .andExpect(status().is3xxRedirection)
                .andExpect(view().name("redirect:/users/loginform"))
        }

        it("인증정보는 있지만 DB에 사용자가 없으면 로그인 폼으로 리다이렉트해야 한다") {
            every { migrationService.isAllowMigration() } returns true
            every { userRepository.findByLoginId("ghost") } returns Optional.empty()
            val auth = UsernamePasswordAuthenticationToken("ghost", "password")

            mockMvc.perform(get("/migration").principal(auth))
                .andExpect(status().is3xxRedirection)
                .andExpect(view().name("redirect:/users/loginform"))
        }

        it("code 파라미터가 없으면 토큰/코드를 빈 문자열로 채워 migration/home 뷰를 반환해야 한다") {
            every { migrationService.isAllowMigration() } returns true
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            val auth = UsernamePasswordAuthenticationToken("gildong", "password")

            mockMvc.perform(get("/migration").principal(auth))
                .andExpect(status().isOk)
                .andExpect(view().name("migration/home"))
                .andExpect(model().attribute("token", ""))
                .andExpect(model().attribute("code", ""))
        }

        it("code 파라미터가 있으면 OAuth 토큰을 조회해 모델에 채워야 한다") {
            every { migrationService.isAllowMigration() } returns true
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { migrationService.getOAuthToken("auth-code") } returns "access-token"
            val auth = UsernamePasswordAuthenticationToken("gildong", "password")

            mockMvc.perform(get("/migration").param("code", "auth-code").principal(auth))
                .andExpect(status().isOk)
                .andExpect(view().name("migration/home"))
                .andExpect(model().attribute("token", "access-token"))
                .andExpect(model().attribute("code", "auth-code"))
        }

        it("code 파라미터가 공백뿐이면 없는 것으로 취급해야 한다") {
            every { migrationService.isAllowMigration() } returns true
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            val auth = UsernamePasswordAuthenticationToken("gildong", "password")

            mockMvc.perform(get("/migration").param("code", "   ").principal(auth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("token", ""))
                .andExpect(model().attribute("code", ""))
        }
    }
})
