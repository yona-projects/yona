package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.twofactor.BackupCodeGenerator
import com.github.yonaprojects.yona.domain.twofactor.TotpCodeVerifier
import com.github.yonaprojects.yona.domain.twofactor.TotpSecretEncryptor
import com.github.yonaprojects.yona.domain.twofactor.TwoFactorService
import com.github.yonaprojects.yona.domain.twofactor.TwoFactorTotpCredential
import com.github.yonaprojects.yona.domain.twofactor.TwoFactorTotpCredentialRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.time.SystemTimeProvider
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

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

// 로그인 필터 체인에 실제로 끼워 넣은 2FA 게이트(Pre2faAuthenticationToken + Pre2faGateFilter +
// YonaAuthenticationSuccessHandler)를 실제 springSecurityFilterChain 위에서 검증한다. 특히
// "2FA 미등록 계정은 기존과 동일하게 동작해야 한다"는 회귀 방지 케이스를 반드시 포함한다.
class TwoFactorLoginFlowIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val totpCredentialRepository: TwoFactorTotpCredentialRepository,
    private val totpSecretEncryptor: TotpSecretEncryptor,
    private val twoFactorService: TwoFactorService
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private val totpCodeGenerator = DefaultCodeGenerator(HashingAlgorithm.SHA1, 6)
    private val timeProvider = SystemTimeProvider()

    private fun currentTotpCode(secret: String): String =
        totpCodeGenerator.generate(secret, timeProvider.time / 30)

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()
        }

        afterTest {
            SecurityContextHolder.clearContext()
        }

        describe("2FA 미등록 계정 로그인(회귀 방지)") {
            it("비밀번호 로그인만으로 완전히 로그인되어 2FA 화면을 거치지 않아야 한다") {
                val salt = "salt-no2fa"
                userRepository.save(
                    User(
                        loginId = "no2fa-${System.nanoTime()}", name = "일반유저", email = "no2fa@example.com",
                        password = legacyHash("password1234", salt), passwordSalt = salt
                    )
                )
                val loginId = userRepository.findByEmail("no2fa@example.com").get().loginId
                val session = MockHttpSession()

                mockMvc.perform(
                    post("/users/login")
                        .param("loginIdOrEmail", loginId)
                        .param("password", "password1234")
                        .session(session)
                        .with(csrf())
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/"))

                // 2FA 게이트를 거치지 않았으므로 세션의 Authentication은 이미 완전한 권한을 갖는다.
                mockMvc.perform(get("/").session(session))
                    .andExpect(status().isOk)
            }
        }

        describe("TOTP가 등록된 계정 로그인") {
            it("비밀번호 인증 직후 완전한 로그인 대신 2FA 검증 화면으로 리다이렉트되고, 다른 페이지 접근이 차단되며, 올바른 TOTP 코드로만 완전히 로그인된다") {
                val salt = "salt-totp"
                val user = userRepository.save(
                    User(
                        loginId = "totp2fa-${System.nanoTime()}", name = "TOTP유저", email = "totp2fa@example.com",
                        password = legacyHash("password1234", salt), passwordSalt = salt
                    )
                )
                val secret = "JBSWY3DPEHPK3PXP"
                totpCredentialRepository.save(
                    TwoFactorTotpCredential(
                        user = user, label = user.loginId,
                        encryptedSecret = totpSecretEncryptor.encrypt(secret),
                        enabled = true, activatedAt = Instant.now()
                    )
                )
                twoFactorService.refreshSummaryFlag(user)
                val session = MockHttpSession()

                mockMvc.perform(
                    post("/users/login")
                        .param("loginIdOrEmail", user.loginId)
                        .param("password", "password1234")
                        .session(session)
                        .with(csrf())
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/login/2fa"))

                // 2FA 검증 전이라 다른 페이지 접근은 전부 2FA 화면으로 강제 리다이렉트된다.
                mockMvc.perform(get("/").session(session))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/login/2fa"))

                // 틀린 코드로는 완전한 로그인이 되지 않는다.
                mockMvc.perform(post("/users/login/2fa/totp").param("code", "000000").session(session).with(csrf()))
                    .andExpect(status().is3xxRedirection)
                mockMvc.perform(get("/").session(session))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/login/2fa"))

                // 올바른 코드로 완전히 로그인된다.
                mockMvc.perform(post("/users/login/2fa/totp").param("code", currentTotpCode(secret)).session(session).with(csrf()))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/"))
                mockMvc.perform(get("/").session(session))
                    .andExpect(status().isOk)
            }
        }

        describe("백업 코드 로그인") {
            it("백업 코드로 2단계 인증을 통과할 수 있고, 사용한 코드는 재사용할 수 없다") {
                val salt = "salt-backup"
                val user = userRepository.save(
                    User(
                        loginId = "backup2fa-${System.nanoTime()}", name = "백업유저", email = "backup2fa@example.com",
                        password = legacyHash("password1234", salt), passwordSalt = salt
                    )
                )
                totpCredentialRepository.save(
                    TwoFactorTotpCredential(user = user, label = user.loginId, encryptedSecret = totpSecretEncryptor.encrypt("JBSWY3DPEHPK3PXP"), enabled = true)
                )
                val codes = twoFactorService.regenerateBackupCodes(user)
                val code = codes.first()
                val session = MockHttpSession()

                mockMvc.perform(
                    post("/users/login")
                        .param("loginIdOrEmail", user.loginId)
                        .param("password", "password1234")
                        .session(session)
                        .with(csrf())
                ).andExpect(redirectedUrl("/users/login/2fa"))

                mockMvc.perform(post("/users/login/2fa/backup").param("code", code).session(session).with(csrf()))
                    .andExpect(redirectedUrl("/"))
                mockMvc.perform(get("/").session(session)).andExpect(status().isOk)

                // 재사용 불가 확인 — 새 세션으로 다시 1차 인증부터.
                val secondSession = MockHttpSession()
                mockMvc.perform(
                    post("/users/login")
                        .param("loginIdOrEmail", user.loginId)
                        .param("password", "password1234")
                        .session(secondSession)
                        .with(csrf())
                ).andExpect(redirectedUrl("/users/login/2fa"))

                mockMvc.perform(post("/users/login/2fa/backup").param("code", code).session(secondSession).with(csrf()))
                    .andExpect(status().is3xxRedirection)
                mockMvc.perform(get("/").session(secondSession))
                    .andExpect(redirectedUrl("/users/login/2fa"))
            }
        }

        describe("관리자 강제 2FA 비활성화") {
            it("비활성화 후에는 해당 계정이 2FA 없이 완전히 로그인되어야 한다") {
                val salt = "salt-adm"
                val user = userRepository.save(
                    User(
                        loginId = "admdisable-${System.nanoTime()}", name = "관리자비활성대상", email = "admdisable@example.com",
                        password = legacyHash("password1234", salt), passwordSalt = salt
                    )
                )
                totpCredentialRepository.save(
                    TwoFactorTotpCredential(user = user, label = user.loginId, encryptedSecret = totpSecretEncryptor.encrypt("JBSWY3DPEHPK3PXP"), enabled = true)
                )
                twoFactorService.refreshSummaryFlag(user)

                twoFactorService.disableAll(user)

                val session = MockHttpSession()
                mockMvc.perform(
                    post("/users/login")
                        .param("loginIdOrEmail", user.loginId)
                        .param("password", "password1234")
                        .session(session)
                        .with(csrf())
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/"))
            }
        }
    }
}
