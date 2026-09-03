package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class MilestoneIdFormSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("기본값은 null이고, id를 설정하면 그대로 읽을 수 있어야 한다") {
            val form = MilestoneIdForm()

            form.id shouldBe null

            form.id = 42L

            form.id shouldBe 42L
        }
    }
})
