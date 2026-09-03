package com.github.yonaprojects.yona.domain.role

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class RoleSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val role = Role()

            role.id shouldBe null
            role.name shouldBe ""
            role.active shouldBe true

            role.id = 1L
            role.name = "ADMIN"
            role.active = false

            role.id shouldBe 1L
            role.name shouldBe "ADMIN"
            role.active shouldBe false
        }

        it("생성자에 모든 인자를 전달해도 정상 생성돼야 한다") {
            val role = Role(id = 2L, name = "MEMBER", active = false)

            role.id shouldBe 2L
            role.name shouldBe "MEMBER"
            role.active shouldBe false
        }
    }
})
