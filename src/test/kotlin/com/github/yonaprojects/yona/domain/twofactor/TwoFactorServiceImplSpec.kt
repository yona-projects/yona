package com.github.yonaprojects.yona.domain.twofactor

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
import java.util.Optional

class TwoFactorServiceImplSpec : DescribeSpec({
    val totpCredentialRepository = mockk<TwoFactorTotpCredentialRepository>()
    val webauthnCredentialRepository = mockk<WebauthnCredentialRepository>()
    val backupCodeRepository = mockk<BackupCodeRepository>()
    val userRepository = mockk<UserRepository>()
    val totpSecretEncryptor = TotpSecretEncryptor("test-password", "596f6e6132303236")
    val totpCodeVerifier = TotpCodeVerifier("Yona")
    val backupCodeGenerator = BackupCodeGenerator()

    val service = TwoFactorServiceImpl(
        totpCredentialRepository, webauthnCredentialRepository, backupCodeRepository,
        userRepository, totpSecretEncryptor, totpCodeVerifier, backupCodeGenerator
    )

    fun currentTotpCode(secret: String): String =
        DefaultCodeGenerator(HashingAlgorithm.SHA1, 6).generate(secret, SystemTimeProvider().time / 30)

    val user = User(id = 1L, loginId = "gildong", name = "홍길동")

    beforeTest {
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
