package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.device.DeviceRecognitionService
import com.github.yonaprojects.yona.domain.device.UserKnownDeviceRepository
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
import io.kotest.matchers.shouldNotBe
import jakarta.servlet.Filter
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
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

// 쿠키 기반 기기 인식(DeviceRecognitionService)이 실제 springSecurityFilterChain 위에서
// 두 완전 로그인 지점(2FA 없는 계정의 YonaAuthenticationSuccessHandler, 2FA 계정의
// TwoFactorLoginController.finalizeLogin) 모두에 실제로 걸리는지 검증한다.
class DeviceRecognitionLoginIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val totpCredentialRepository: TwoFactorTotpCredentialRepository,
    private val totpSecretEncryptor: TotpSecretEncryptor,
    private val twoFactorService: TwoFactorService,
    private val knownDeviceRepository: UserKnownDeviceRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private val totpCodeGenerator = DefaultCodeGenerator(HashingAlgorithm.SHA1, 6)
    private val timeProvider = SystemTimeProvider()

    private fun currentTotpCode(secret: String): String =
        totpCodeGenerator.generate(secret, timeProvider.time / 30)

    private fun registerUser(loginId: String, email: String): User {
        val salt = "salt-$loginId"
        return userRepository.save(
            User(loginId = loginId, name = loginId, email = email, password = legacyHash("password1234", salt), passwordSalt = salt)
        )
    }

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

        describe("2FA 없는 계정의 로그인") {
            it("쿠키 없이 처음 로그인하면 새 기기 쿠키가 발급되고, 같은 쿠키로 재로그인하면 다시 발급되지 않는다") {
                val loginId = "device-no2fa-${System.nanoTime()}"
                val user = registerUser(loginId, "$loginId@example.com")

                val firstResult = mockMvc.perform(
                    post("/users/login")
                        .param("loginIdOrEmail", loginId)
                        .param("password", "password1234")
                        .session(MockHttpSession())
                        .with(csrf())
                ).andExpect(redirectedUrl("/")).andReturn()

                val issuedCookie = firstResult.response.getCookie(DeviceRecognitionService.COOKIE_NAME)
                issuedCookie shouldNotBe null
                knownDeviceRepository.findByUserId(requireNotNull(user.id)).size shouldBe 1

                val secondResult = mockMvc.perform(
                    post("/users/login")
                        .param("loginIdOrEmail", loginId)
                        .param("password", "password1234")
                        .session(MockHttpSession())
                        .cookie(Cookie(DeviceRecognitionService.COOKIE_NAME, issuedCookie!!.value))
                        .with(csrf())
                ).andExpect(redirectedUrl("/")).andReturn()

                secondResult.response.getCookie(DeviceRecognitionService.COOKIE_NAME) shouldBe null
                knownDeviceRepository.findByUserId(requireNotNull(user.id)).size shouldBe 1
            }
        }

        describe("2FA 계정의 로그인") {
            it("TOTP 검증까지 통과해 완전히 로그인된 시점에 새 기기 쿠키가 발급된다") {
                val loginId = "device-2fa-${System.nanoTime()}"
                val user = registerUser(loginId, "$loginId@example.com")
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
                        .param("loginIdOrEmail", loginId)
                        .param("password", "password1234")
                        .session(session)
                        .with(csrf())
                ).andExpect(redirectedUrl("/users/login/2fa"))

                val result = mockMvc.perform(
                    post("/users/login/2fa/totp").param("code", currentTotpCode(secret)).session(session).with(csrf())
                ).andExpect(redirectedUrl("/")).andReturn()

                result.response.getCookie(DeviceRecognitionService.COOKIE_NAME) shouldNotBe null
                knownDeviceRepository.findByUserId(requireNotNull(user.id)).size shouldBe 1
            }
        }
    }
}
