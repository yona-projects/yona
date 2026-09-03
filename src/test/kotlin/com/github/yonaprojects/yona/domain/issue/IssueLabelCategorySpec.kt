package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class IssueLabelCategorySpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val project = Project(id = 1L, name = "p", owner = "owner")
            val category = IssueLabelCategory(project = project)

            val newProject = Project(id = 2L, name = "p2", owner = "owner2")

            category.id = 10L
            category.name = "우선순위"
            category.isExclusive = true
            category.project = newProject

            category.id shouldBe 10L
            category.name shouldBe "우선순위"
            category.isExclusive shouldBe true
            category.project shouldBe newProject
        }

        it("기본값만으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val project = Project(id = 1L, name = "p", owner = "owner")
            val category = IssueLabelCategory(project = project)

            category.id shouldBe null
            category.name shouldBe ""
            category.isExclusive shouldBe false
        }
    }
})
