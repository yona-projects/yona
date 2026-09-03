package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class LinkedAccountSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val user = User(id = 1L)
            val linkedAccount = LinkedAccount(user = user)

            val newUser = User(id = 2L)

            linkedAccount.id = 10L
            linkedAccount.user = newUser
            linkedAccount.providerKey = "github"
            linkedAccount.providerUserId = "12345"

            linkedAccount.id shouldBe 10L
            linkedAccount.user shouldBe newUser
            linkedAccount.providerKey shouldBe "github"
            linkedAccount.providerUserId shouldBe "12345"
        }

        it("기본값만으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val user = User(id = 1L)
            val linkedAccount = LinkedAccount(user = user)

            linkedAccount.id shouldBe null
            linkedAccount.user shouldBe user
            linkedAccount.providerKey shouldBe ""
            linkedAccount.providerUserId shouldBe ""
        }
    }
})
