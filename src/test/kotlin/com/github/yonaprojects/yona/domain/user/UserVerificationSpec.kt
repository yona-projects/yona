package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class UserVerificationSpec : DescribeSpec({
    describe("UserVerification.isValidDate") {
        it("생성된 지 24시간 이내면 true를 반환해야 한다") {
            val user = User(id = 1L, loginId = "test", name = "Test", email = "test@example.com")
            val verification = UserVerification(
                user = user,
                loginId = "test",
                verificationCode = "code",
                timestamp = System.currentTimeMillis() - 1000 // 1초 전
            )
            verification.isValidDate() shouldBe true
        }

        it("생성된 지 24시간이 넘었으면 false를 반환해야 한다") {
            val user = User(id = 1L, loginId = "test", name = "Test", email = "test@example.com")
            val verification = UserVerification(
                user = user,
                loginId = "test",
                verificationCode = "code",
                timestamp = System.currentTimeMillis() - (24 * 60 * 60 * 1000) - 1000 // 24시간 1초 전
            )
            verification.isValidDate() shouldBe false
        }
    }

    describe("UserVerification 프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val user = User(id = 1L, loginId = "test", name = "Test", email = "test@example.com")
            val otherUser = User(id = 2L, loginId = "other", name = "Other", email = "other@example.com")
            val verification = UserVerification(
                id = 10L,
                user = user,
                loginId = "test",
                verificationCode = "code",
                timestamp = 123L
            )

            verification.id shouldBe 10L
            verification.user shouldBe user
            verification.loginId shouldBe "test"
            verification.verificationCode shouldBe "code"
            verification.timestamp shouldBe 123L

            verification.id = 20L
            verification.user = otherUser
            verification.loginId = "changed"
            verification.verificationCode = "new-code"
            verification.timestamp = 456L

            verification.id shouldBe 20L
            verification.user shouldBe otherUser
            verification.loginId shouldBe "changed"
            verification.verificationCode shouldBe "new-code"
            verification.timestamp shouldBe 456L
        }
    }
})
