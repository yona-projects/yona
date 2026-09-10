package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith

class PasswordEncodingServiceSpec : DescribeSpec({
    val service = PasswordEncodingService()

    describe("encode") {
        it("항상 Argon2 포맷(\$로 시작)으로 인코딩해야 한다") {
            val encoded = service.encode("password1234")
            encoded shouldStartWith "$"
        }

        it("동일한 원문이라도 매번 다른 인코딩 결과를 내야 한다(salt가 매번 랜덤)") {
            val first = service.encode("password1234")
            val second = service.encode("password1234")
            first shouldNotBe second
        }
    }

    describe("matches - Argon2 포맷") {
        it("올바른 비밀번호는 통과해야 한다") {
            val encoded = service.encode("password1234")
            service.matches("password1234", encoded, null) shouldBe true
        }

        it("틀린 비밀번호는 거부해야 한다") {
            val encoded = service.encode("password1234")
            service.matches("wrong-password", encoded, null) shouldBe false
        }
    }

    describe("matches - 레거시(SHA-256x1024) 포맷") {
        val salt = "legacy-salt"
        val legacyStored = PasswordEncodingService.legacyHash("password1234", salt)

        it("올바른 비밀번호+salt는 통과해야 한다") {
            service.matches("password1234", legacyStored, salt) shouldBe true
        }

        it("틀린 비밀번호는 거부해야 한다") {
            service.matches("wrong-password", legacyStored, salt) shouldBe false
        }

        it("salt가 null이면 빈 문자열 salt로 취급해 검증해야 한다(레거시 호출부의 `?: \"\"` 관례와 동일)") {
            val hashedWithEmptySalt = PasswordEncodingService.legacyHash("password1234", "")
            service.matches("password1234", hashedWithEmptySalt, null) shouldBe true
        }
    }

    describe("matches - 저장된 해시 자체가 없을 때") {
        it("null이면 거부해야 한다") {
            service.matches("password1234", null, "salt") shouldBe false
        }

        it("빈 문자열이면 거부해야 한다") {
            service.matches("password1234", "", "salt") shouldBe false
        }
    }

    describe("needsUpgrade") {
        it("레거시 포맷이면 true를 반환해야 한다") {
            service.needsUpgrade(PasswordEncodingService.legacyHash("password1234", "salt")) shouldBe true
        }

        it("Argon2 포맷이면 false를 반환해야 한다") {
            service.needsUpgrade(service.encode("password1234")) shouldBe false
        }

        it("저장된 해시가 없으면 false를 반환해야 한다") {
            service.needsUpgrade(null) shouldBe false
            service.needsUpgrade("") shouldBe false
        }
    }
})
