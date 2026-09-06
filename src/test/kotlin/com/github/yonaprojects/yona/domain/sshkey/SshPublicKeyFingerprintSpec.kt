package com.github.yonaprojects.yona.domain.sshkey

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

private const val TEST_PUBLIC_KEY_1 =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHFAhHu7wL23JgqJtpP8u/JUqCaLm1vcYoohMQFAdpXS tester@example.com"

class SshPublicKeyFingerprintSpec : DescribeSpec({

    describe("SshPublicKeyFingerprint.parse") {
        it("실제 OpenSSH ed25519 공개키를 파싱해 SHA256: 접두어의 지문을 계산해야 한다") {
            val parsed = SshPublicKeyFingerprint.parse(TEST_PUBLIC_KEY_1)

            parsed.keyType shouldBe "ssh-ed25519"
            parsed.comment shouldBe "tester@example.com"
            parsed.fingerprint.startsWith("SHA256:") shouldBe true
        }

        it("동일한 공개키는 항상 동일한 지문을 계산해야 한다(결정적)") {
            val first = SshPublicKeyFingerprint.parse(TEST_PUBLIC_KEY_1)
            val second = SshPublicKeyFingerprint.parse(TEST_PUBLIC_KEY_1)

            first.fingerprint shouldBe second.fingerprint
        }

        it("comment가 없어도 파싱에 성공해야 한다") {
            val withoutComment = TEST_PUBLIC_KEY_1.substringBeforeLast(" ")
            val parsed = SshPublicKeyFingerprint.parse(withoutComment)

            parsed.comment shouldBe null
            parsed.fingerprint.startsWith("SHA256:") shouldBe true
        }

        it("빈 문자열은 InvalidPublicKeyException을 던져야 한다") {
            shouldThrow<SshPublicKeyFingerprint.InvalidPublicKeyException> {
                SshPublicKeyFingerprint.parse("   ")
            }
        }

        it("지원하지 않는 키 타입은 InvalidPublicKeyException을 던져야 한다") {
            shouldThrow<SshPublicKeyFingerprint.InvalidPublicKeyException> {
                SshPublicKeyFingerprint.parse("ssh-dss AAAAB3NzaC1kc3MAAACB")
            }
        }

        it("base64로 디코딩할 수 없는 블롭은 InvalidPublicKeyException을 던져야 한다") {
            shouldThrow<SshPublicKeyFingerprint.InvalidPublicKeyException> {
                SshPublicKeyFingerprint.parse("ssh-ed25519 not-base64!!! comment")
            }
        }

        it("단어가 하나뿐이면 InvalidPublicKeyException을 던져야 한다") {
            shouldThrow<SshPublicKeyFingerprint.InvalidPublicKeyException> {
                SshPublicKeyFingerprint.parse("ssh-ed25519")
            }
        }
    }
})
