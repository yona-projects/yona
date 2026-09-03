package com.github.yonaprojects.yona.domain.project

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class LabelSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val label = Label()
            val project = Project(id = 1L, name = "p", owner = "owner")

            label.id = 10L
            label.category = "os"
            label.name = "linux"
            label.projects = mutableSetOf(project)

            label.id shouldBe 10L
            label.category shouldBe "os"
            label.name shouldBe "linux"
            label.projects shouldBe mutableSetOf(project)
        }

        it("기본값만으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val label = Label()

            label.id shouldBe null
            label.category shouldBe ""
            label.name shouldBe ""
            label.projects shouldBe mutableSetOf()
        }
    }
})
