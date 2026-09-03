package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AssigneeIdFormSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("기본값은 null이어야 한다") {
            val form = AssigneeIdForm()

            form.id.shouldBeNull()
        }

        it("id를 설정하면 그대로 반환해야 한다") {
            val form = AssigneeIdForm()

            form.id = 42L

            form.id shouldBe 42L
        }
    }
})
