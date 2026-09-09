package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.twofactor.TotpActivationResult
import com.github.yonaprojects.yona.domain.twofactor.TotpEnrollment
import com.github.yonaprojects.yona.domain.twofactor.TwoFactorService
import com.github.yonaprojects.yona.domain.twofactor.TwoFactorTotpCredential
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.security.MessageDigest
import java.util.Base64
import java.util.Optional

private fun legacyHash(password: String, salt: String): String {
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

class TwoFactorSettingsControllerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val twoFactorService = mockk<TwoFactorService>()
    val controller = TwoFactorSettingsController(userRepository, twoFactorService)
    val viewResolver = InternalResourceViewResolver().apply {
        setPrefix("/templates/")
        setSuffix(".html")
    }
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(viewResolver).build()

    val salt = "test-salt"
    val user = User(id = 1L, loginId = "gildong", name = "홍길동", password = legacyHash("correct-password", salt), passwordSalt = salt)
    val auth = UsernamePasswordAuthenticationToken("gildong", "password")

    beforeTest {
        clearMocks(userRepository, twoFactorService)
        every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
    }

    describe("GET /user/editform/security") {
        it("등록된 방식 목록과 백업 코드 여부를 모델에 담아 렌더링해야 한다") {
            every { twoFactorService.listEnabledTotpCredentials(user) } returns emptyList()
            every { twoFactorService.listWebauthnCredentials(user) } returns emptyList()
            every { twoFactorService.hasUnusedBackupCodes(user) } returns true

            mockMvc.perform(get("/user/editform/security").principal(auth))
                .andExpect(status().isOk)
                .andExpect(view().name("user/edit_security"))
                .andExpect(model().attribute("hasBackupCodes", true))
        }

        it("인증 정보가 없으면 403이어야 한다") {
            mockMvc.perform(get("/user/editform/security"))
                .andExpect(view().name("error/403"))
        }
    }

    describe("GET /user/editform/security/totp/new") {
        it("등록을 시작하고 secret/QR을 모델에 담아야 한다") {
            val credential = TwoFactorTotpCredential(id = 10L, user = user, enabled = false)
            val enrollment = TotpEnrollment(rawSecret = "SECRET123", qrCodeDataUri = "data:image/png;base64,xxx")
            every { twoFactorService.beginTotpEnrollment(user, "gildong") } returns (credential to enrollment)

            mockMvc.perform(get("/user/editform/security/totp/new").principal(auth))
                .andExpect(status().isOk)
                .andExpect(view().name("user/edit_security_totp_new"))
                .andExpect(model().attribute("secret", "SECRET123"))
                .andExpect(model().attribute("credentialId", 10L))
        }
    }

    describe("POST /user/editform/security/totp/{id}/verify") {
        it("성공하면 목록 화면으로 리다이렉트해야 한다(백업 코드가 이미 있는 경우)") {
            every { twoFactorService.verifyAndActivateTotp(user, 10L, "123456") } returns TotpActivationResult.Success(null)

            mockMvc.perform(post("/user/editform/security/totp/10/verify").param("code", "123456").principal(auth))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/user/editform/security"))
        }

        it("첫 2FA 등록이라 백업 코드가 새로 발급되면 백업 코드 화면으로 리다이렉트해야 한다") {
            every { twoFactorService.verifyAndActivateTotp(user, 10L, "123456") } returns
                TotpActivationResult.Success(listOf("AAAAA-BBBBB"))

            mockMvc.perform(post("/user/editform/security/totp/10/verify").param("code", "123456").principal(auth))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/user/editform/security/backup-codes/show"))
        }

        it("코드가 틀리면 등록 폼을 에러와 함께 다시 보여줘야 한다(같은 secret 재사용)") {
            every { twoFactorService.verifyAndActivateTotp(user, 10L, "000000") } returns TotpActivationResult.InvalidCode
            every { twoFactorService.reviewPendingTotpEnrollment(user, 10L) } returns
                TotpEnrollment(rawSecret = "SECRET123", qrCodeDataUri = "data:image/png;base64,xxx")

            mockMvc.perform(post("/user/editform/security/totp/10/verify").param("code", "000000").principal(auth))
                .andExpect(status().isOk)
                .andExpect(view().name("user/edit_security_totp_new"))
                .andExpect(model().attribute("totpError", true))
                .andExpect(model().attribute("secret", "SECRET123"))
        }
    }

    describe("POST /user/editform/security/disable") {
        it("비밀번호가 일치하면 전체 비활성화 후 리다이렉트해야 한다") {
            every { twoFactorService.disableAll(user) } returns Unit

            mockMvc.perform(post("/user/editform/security/disable").param("password", "correct-password").principal(auth))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/user/editform/security"))

            verify(exactly = 1) { twoFactorService.disableAll(user) }
        }

        it("비밀번호가 틀리면 비활성화하지 않고 에러와 함께 리다이렉트해야 한다") {
            mockMvc.perform(post("/user/editform/security/disable").param("password", "wrong-password").principal(auth))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/user/editform/security"))

            verify(exactly = 0) { twoFactorService.disableAll(any()) }
        }
    }

    describe("POST /user/editform/security/totp/{id}/delete") {
        it("비밀번호가 일치하면 해당 TOTP 자격증명을 삭제해야 한다") {
            every { twoFactorService.deleteTotpCredential(user, 10L) } returns Unit

            mockMvc.perform(post("/user/editform/security/totp/10/delete").param("password", "correct-password").principal(auth))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/user/editform/security"))

            verify(exactly = 1) { twoFactorService.deleteTotpCredential(user, 10L) }
        }

        it("비밀번호가 틀리면 삭제하지 않고 에러와 함께 리다이렉트해야 한다") {
            mockMvc.perform(post("/user/editform/security/totp/10/delete").param("password", "wrong-password").principal(auth))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/user/editform/security"))

            verify(exactly = 0) { twoFactorService.deleteTotpCredential(any(), any()) }
        }
    }

    describe("POST /user/editform/security/webauthn/{id}/delete") {
        it("비밀번호가 일치하면 해당 보안 키를 삭제해야 한다") {
            every { twoFactorService.deleteWebauthnCredential(user, 20L) } returns Unit

            mockMvc.perform(post("/user/editform/security/webauthn/20/delete").param("password", "correct-password").principal(auth))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/user/editform/security"))

            verify(exactly = 1) { twoFactorService.deleteWebauthnCredential(user, 20L) }
        }

        it("비밀번호가 틀리면 삭제하지 않고 에러와 함께 리다이렉트해야 한다") {
            mockMvc.perform(post("/user/editform/security/webauthn/20/delete").param("password", "wrong-password").principal(auth))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/user/editform/security"))

            verify(exactly = 0) { twoFactorService.deleteWebauthnCredential(any(), any()) }
        }
    }

    describe("POST /user/editform/security/backup-codes/regenerate") {
        it("재발급 후 백업 코드 표시 화면으로 리다이렉트해야 한다") {
            every { twoFactorService.regenerateBackupCodes(user) } returns listOf("CCCCC-DDDDD")

            mockMvc.perform(post("/user/editform/security/backup-codes/regenerate").principal(auth))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/user/editform/security/backup-codes/show"))
        }
    }
})
