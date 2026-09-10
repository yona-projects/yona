package com.github.yonaprojects.yona.domain.twofactor

import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.time.SystemTimeProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional

class TwoFactorServiceImplSpec : DescribeSpec({
    val totpCredentialRepository = mockk<TwoFactorTotpCredentialRepository>()
    val webauthnCredentialRepository = mockk<WebauthnCredentialRepository>()
    val backupCodeRepository = mockk<BackupCodeRepository>()
    val userRepository = mockk<UserRepository>()
    val totpSecretEncryptor = TotpSecretEncryptor("test-password", "596f6e6132303236")
    val totpCodeVerifier = TotpCodeVerifier("Yona")
    val backupCodeGenerator = BackupCodeGenerator()
    val mailService = mockk<MailService>(relaxed = true)

    val service = TwoFactorServiceImpl(
        totpCredentialRepository, webauthnCredentialRepository, backupCodeRepository,
        userRepository, totpSecretEncryptor, totpCodeVerifier, backupCodeGenerator,
        mailService, "테스트사이트"
    )

    fun currentTotpCode(secret: String): String =
        DefaultCodeGenerator(HashingAlgorithm.SHA1, 6).generate(secret, SystemTimeProvider().time / 30)

    val user = User(id = 1L, loginId = "gildong", name = "홍길동")

    beforeTest {
        // mailService는 verify(exactly = 0){...}로도 검증하므로, 이전 테스트의 호출 기록이
        // 새는 것을 막기 위해 매번 초기화한다.
        io.mockk.clearMocks(mailService, answers = false)
        every { userRepository.save(any()) } answers { firstArg() }
    }

    describe("TOTP 등록") {
        it("등록 시작 시 raw secret이 QR과 함께 발급되고, 이전 미확정 등록은 정리된다") {
            val staleUnconfirmed = TwoFactorTotpCredential(id = 5L, user = user, enabled = false)
            every { totpCredentialRepository.findByUserId(1L) } returns listOf(staleUnconfirmed)
            every { totpCredentialRepository.delete(staleUnconfirmed) } returns Unit
            val savedSlot = slot<TwoFactorTotpCredential>()
            every { totpCredentialRepository.save(capture(savedSlot)) } answers { savedSlot.captured.also { it.id = 10L } }

            val (credential, enrollment) = service.beginTotpEnrollment(user, "gildong")

            credential.enabled shouldBe false
            enrollment.rawSecret.isNotBlank() shouldBe true
            enrollment.qrCodeDataUri.startsWith("data:image/png;base64,") shouldBe true
            // 저장된 secret은 원문이 아니라 암호문이어야 한다.
            (credential.encryptedSecret == enrollment.rawSecret) shouldBe false
            totpSecretEncryptor.decrypt(credential.encryptedSecret) shouldBe enrollment.rawSecret
        }

        it("올바른 6자리 코드로 검증하면 활성화되고 첫 등록이라 백업 코드 8개가 함께 발급된다") {
            val secret = "JBSWY3DPEHPK3PXP"
            val pending = TwoFactorTotpCredential(id = 10L, user = user, encryptedSecret = totpSecretEncryptor.encrypt(secret), enabled = false)
            every { totpCredentialRepository.findById(10L) } returns Optional.of(pending)
            every { totpCredentialRepository.save(pending) } returns pending
            every { totpCredentialRepository.findByUserIdAndEnabledTrue(1L) } returns listOf(pending)
            every { webauthnCredentialRepository.findByUserId(1L) } returns emptyList()
            every { backupCodeRepository.findByUserId(1L) } returns emptyList()
            every { backupCodeRepository.deleteByUserId(1L) } returns Unit
            every { backupCodeRepository.save(any()) } answers { firstArg() }

            val result = service.verifyAndActivateTotp(user, 10L, currentTotpCode(secret))

            result.shouldBeInstanceOf<TotpActivationResult.Success>()
            val success = result as TotpActivationResult.Success
            success.freshBackupCodes?.size shouldBe 8
            pending.enabled shouldBe true
            user.isTwoFactorEnabled shouldBe true
        }

        it("틀린 코드면 활성화되지 않는다") {
            val secret = "JBSWY3DPEHPK3PXP"
            val pending = TwoFactorTotpCredential(id = 11L, user = user, encryptedSecret = totpSecretEncryptor.encrypt(secret), enabled = false)
            every { totpCredentialRepository.findById(11L) } returns Optional.of(pending)

            val result = service.verifyAndActivateTotp(user, 11L, "000000")

            result.shouldBeInstanceOf<TotpActivationResult.InvalidCode>()
            pending.enabled shouldBe false
        }

        it("다른 사용자 소유의 credentialId면 NotFound를 반환한다") {
            val otherUser = User(id = 2L, loginId = "other", name = "다른사람")
            val pending = TwoFactorTotpCredential(id = 12L, user = otherUser, encryptedSecret = "x", enabled = false)
            every { totpCredentialRepository.findById(12L) } returns Optional.of(pending)

            val result = service.verifyAndActivateTotp(user, 12L, "123456")

            result.shouldBeInstanceOf<TotpActivationResult.NotFound>()
        }

        it("계정에 2FA를 처음 활성화하면 소유자에게 알림 메일을 보낸다") {
            val notifyUser = User(id = 50L, loginId = "first2fa", name = "첫등록", email = "first2fa@example.com")
            val secret = "JBSWY3DPEHPK3PXP"
            val pending = TwoFactorTotpCredential(id = 50L, user = notifyUser, encryptedSecret = totpSecretEncryptor.encrypt(secret), enabled = false)
            every { totpCredentialRepository.findById(50L) } returns Optional.of(pending)
            every { totpCredentialRepository.save(pending) } returns pending
            every { totpCredentialRepository.findByUserIdAndEnabledTrue(50L) } returns listOf(pending)
            every { webauthnCredentialRepository.findByUserId(50L) } returns emptyList()
            every { backupCodeRepository.findByUserId(50L) } returns emptyList()
            every { backupCodeRepository.deleteByUserId(50L) } returns Unit
            every { backupCodeRepository.save(any()) } answers { firstArg() }

            service.verifyAndActivateTotp(notifyUser, 50L, currentTotpCode(secret))

            verify(exactly = 1) {
                mailService.sendHtmlMail("first2fa@example.com", "첫등록", any(), any())
            }
        }

        it("이미 다른 2FA 방식이 등록된 계정에 두 번째 방식을 추가할 때는 중복 알림을 보내지 않는다") {
            val notifyUser = User(id = 51L, loginId = "second2fa", name = "추가등록", email = "second2fa@example.com")
            val secret = "JBSWY3DPEHPK3PXP"
            val pending = TwoFactorTotpCredential(id = 51L, user = notifyUser, encryptedSecret = totpSecretEncryptor.encrypt(secret), enabled = false)
            every { totpCredentialRepository.findById(51L) } returns Optional.of(pending)
            every { totpCredentialRepository.save(pending) } returns pending
            every { totpCredentialRepository.findByUserIdAndEnabledTrue(51L) } returns listOf(pending)
            every { webauthnCredentialRepository.findByUserId(51L) } returns emptyList()
            // 이미 미사용 백업 코드가 있다 = 이 계정은 이전에 이미 2FA를 등록해 첫 알림을 받았다.
            every { backupCodeRepository.findByUserId(51L) } returns listOf(BackupCode(id = 1L, user = notifyUser, codeHash = "x"))

            service.verifyAndActivateTotp(notifyUser, 51L, currentTotpCode(secret))

            verify(exactly = 0) { mailService.sendHtmlMail(any(), any(), any(), any()) }
        }
    }

    describe("WebAuthn 등록 완료") {
        it("계정에 2FA를 처음 활성화하는 WebAuthn 등록이면 알림 메일을 보낸다") {
            val notifyUser = User(id = 60L, loginId = "firstwebauthn", name = "웹인증첫등록", email = "firstwebauthn@example.com")
            val credential = WebauthnCredential(id = 60L, user = notifyUser)
            every { totpCredentialRepository.findByUserIdAndEnabledTrue(60L) } returns emptyList()
            every { webauthnCredentialRepository.findByUserId(60L) } returns listOf(credential)
            every { backupCodeRepository.findByUserId(60L) } returns emptyList()
            every { backupCodeRepository.deleteByUserId(60L) } returns Unit
            every { backupCodeRepository.save(any()) } answers { firstArg() }

            service.completeWebauthnRegistration(notifyUser, credential)

            verify(exactly = 1) {
                mailService.sendHtmlMail("firstwebauthn@example.com", "웹인증첫등록", any(), any())
            }
        }

        it("이미 2FA가 등록된 계정에 WebAuthn을 추가 등록할 때는 중복 알림을 보내지 않는다") {
            val notifyUser = User(id = 61L, loginId = "secondwebauthn", name = "웹인증추가등록", email = "secondwebauthn@example.com")
            val credential = WebauthnCredential(id = 61L, user = notifyUser)
            every { totpCredentialRepository.findByUserIdAndEnabledTrue(61L) } returns emptyList()
            every { webauthnCredentialRepository.findByUserId(61L) } returns listOf(credential)
            every { backupCodeRepository.findByUserId(61L) } returns listOf(BackupCode(id = 2L, user = notifyUser, codeHash = "x"))

            service.completeWebauthnRegistration(notifyUser, credential)

            verify(exactly = 0) { mailService.sendHtmlMail(any(), any(), any(), any()) }
        }
    }

    describe("로그인 시 TOTP 검증") {
        it("활성화된 TOTP secret 중 하나라도 코드가 일치하면 통과한다") {
            val secret = "JBSWY3DPEHPK3PXP"
            val credential = TwoFactorTotpCredential(id = 20L, user = user, encryptedSecret = totpSecretEncryptor.encrypt(secret), enabled = true)
            every { totpCredentialRepository.findByUserIdAndEnabledTrue(1L) } returns listOf(credential)

            service.verifyTotpForLogin(user, currentTotpCode(secret)) shouldBe true
            service.verifyTotpForLogin(user, "111111") shouldBe false
        }
    }

    describe("백업 코드") {
        it("재발급 시 기존 코드를 전량 무효화하고 8개를 새로 발급한다") {
            every { backupCodeRepository.deleteByUserId(1L) } returns Unit
            every { backupCodeRepository.save(any()) } answers { firstArg() }

            val codes = service.regenerateBackupCodes(user)

            codes.size shouldBe 8
            codes.toSet().size shouldBe 8
        }

        it("사용한 백업 코드는 재사용할 수 없다(consume 시점에 usedAt이 채워진 코드는 매칭 대상에서 제외)") {
            val code = "ABCDE-FGHJK"
            val entity = BackupCode(id = 1L, user = user, codeHash = backupCodeGenerator.hash(code))
            every { backupCodeRepository.findByUserIdAndUsedAtIsNull(1L) } returnsMany listOf(listOf(entity), emptyList())
            every { backupCodeRepository.save(entity) } returns entity

            service.consumeBackupCode(user, code) shouldBe true
            service.consumeBackupCode(user, code) shouldBe false
        }
    }

    describe("전체 비활성화") {
        it("모든 TOTP/WebAuthn/백업코드를 삭제하고 요약 플래그를 끈다") {
            val totp = TwoFactorTotpCredential(id = 30L, user = user, enabled = true)
            val webauthn = WebauthnCredential(id = 31L, user = user)
            every { totpCredentialRepository.findByUserId(1L) } returns listOf(totp)
            every { totpCredentialRepository.delete(totp) } returns Unit
            every { webauthnCredentialRepository.findByUserId(1L) } returns listOf(webauthn)
            every { webauthnCredentialRepository.delete(webauthn) } returns Unit
            every { backupCodeRepository.deleteByUserId(1L) } returns Unit

            service.disableAll(user)

            user.isTwoFactorEnabled shouldBe false
        }

        // 법적 컴플라이언스 감사 #10 대응 — 계정 탈취 시나리오에서 공격자가 방어 수단을 끄는
        // 것이 가장 민감한 이벤트라 본인/관리자 강제 여부와 무관하게 항상 알린다.
        it("실제로 등록돼 있던 2FA를 비활성화하면 계정 소유자에게 알림 메일을 보내야 한다") {
            val notifyUser = User(id = 40L, loginId = "notifyme", name = "알림대상", email = "notifyme@example.com")
            val totp = TwoFactorTotpCredential(id = 32L, user = notifyUser, enabled = true)
            every { totpCredentialRepository.findByUserId(40L) } returns listOf(totp)
            every { totpCredentialRepository.delete(totp) } returns Unit
            every { webauthnCredentialRepository.findByUserId(40L) } returns emptyList()
            every { backupCodeRepository.deleteByUserId(40L) } returns Unit

            service.disableAll(notifyUser)

            verify(exactly = 1) {
                mailService.sendHtmlMail("notifyme@example.com", "알림대상", any(), any())
            }
        }

        it("애초에 등록된 2FA가 없었다면 알림 메일을 보내지 않아야 한다") {
            val noopUser = User(id = 41L, loginId = "noop", name = "무동작", email = "noop@example.com")
            every { totpCredentialRepository.findByUserId(41L) } returns emptyList()
            every { webauthnCredentialRepository.findByUserId(41L) } returns emptyList()
            every { backupCodeRepository.deleteByUserId(41L) } returns Unit

            service.disableAll(noopUser)

            verify(exactly = 0) { mailService.sendHtmlMail(any(), any(), any(), any()) }
        }
    }

    describe("isTwoFactorEnabled — 로그인 게이트가 실제로 참조하는 단일 진실 공급원") {
        it("활성화된 TOTP나 WebAuthn credential이 하나라도 있으면 true") {
            every { totpCredentialRepository.findByUserIdAndEnabledTrue(1L) } returns emptyList()
            every { webauthnCredentialRepository.findByUserId(1L) } returns listOf(WebauthnCredential(id = 1L, user = user))

            service.isTwoFactorEnabled(user) shouldBe true
        }

        it("둘 다 없으면 false") {
            every { totpCredentialRepository.findByUserIdAndEnabledTrue(1L) } returns emptyList()
            every { webauthnCredentialRepository.findByUserId(1L) } returns emptyList()

            service.isTwoFactorEnabled(user) shouldBe false
        }
    }
})
