package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class UserIdentSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val userIdent = UserIdent()

            userIdent.id shouldBe null
            userIdent.loginId shouldBe null
            userIdent.name shouldBe null

            userIdent.id = 1L
            userIdent.loginId = "gildong"
            userIdent.name = "홍길동"

            userIdent.id shouldBe 1L
            userIdent.loginId shouldBe "gildong"
            userIdent.name shouldBe "홍길동"
        }
    }

    describe("보조 생성자(user)") {
        it("User의 id/loginId/name을 그대로 채워야 한다") {
            val user = User(id = 5L, loginId = "user5", name = "사용자5")

            val userIdent = UserIdent(user)

            userIdent.id shouldBe 5L
            userIdent.loginId shouldBe "user5"
            userIdent.name shouldBe "사용자5"
        }
    }
})
