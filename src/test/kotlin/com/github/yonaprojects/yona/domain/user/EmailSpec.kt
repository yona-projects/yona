package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class EmailSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("기본값 생성자로 생성하고 모든 프로퍼티를 읽고 쓸 수 있어야 한다") {
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            val email = Email(user = user)

            email.id shouldBe null
            email.user shouldBe user
            email.email shouldBe ""
            email.valid shouldBe false
            email.token shouldBe null
            email.confirmUrl shouldBe null

            val otherUser = User(id = 2L, loginId = "other", name = "타인")
            email.id = 10L
            email.user = otherUser
            email.email = "gildong@example.com"
            email.valid = true
            email.token = "tok-1"
            email.confirmUrl = "http://localhost/confirm/tok-1"

            email.id shouldBe 10L
            email.user shouldBe otherUser
            email.email shouldBe "gildong@example.com"
            email.valid shouldBe true
            email.token shouldBe "tok-1"
            email.confirmUrl shouldBe "http://localhost/confirm/tok-1"
        }

        it("모든 필드를 채운 생성자로 생성할 수 있어야 한다") {
            val user = User(id = 3L, loginId = "someone", name = "누군가")
            val email = Email(id = 20L, user = user, email = "someone@example.com", valid = true, token = "tok-2")

            email.id shouldBe 20L
            email.user shouldBe user
            email.email shouldBe "someone@example.com"
            email.valid shouldBe true
            email.token shouldBe "tok-2"
        }
    }

    describe("validate()") {
        it("입력한 토큰이 저장된 토큰과 같으면 valid를 true로 바꾸고 true를 반환해야 한다") {
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            val email = Email(user = user, email = "gildong@example.com", valid = false, token = "correct-token")

            val result = email.validate("correct-token")

            result shouldBe true
            email.valid shouldBe true
        }

        it("입력한 토큰이 저장된 토큰과 다르면 false를 반환하고 valid를 바꾸지 않아야 한다") {
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            val email = Email(user = user, email = "gildong@example.com", valid = false, token = "correct-token")

            val result = email.validate("wrong-token")

            result shouldBe false
            email.valid shouldBe false
        }

        it("저장된 토큰이 null이고 입력한 토큰이 빈 문자열이 아니면 false를 반환해야 한다") {
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            val email = Email(user = user, email = "gildong@example.com", valid = false, token = null)

            val result = email.validate("any-token")

            result shouldBe false
            email.valid shouldBe false
        }
    }
})
