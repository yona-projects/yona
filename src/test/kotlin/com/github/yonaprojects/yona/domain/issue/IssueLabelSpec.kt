package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class IssueLabelSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val project = Project(id = 1L, name = "p", owner = "owner")
            val category = IssueLabelCategory(name = "우선순위", project = project)
            val label = IssueLabel(category = category, project = project)

            val newProject = Project(id = 2L, name = "p2", owner = "owner2")
            val newCategory = IssueLabelCategory(name = "종류", project = newProject)

            label.id = 10L
            label.category = newCategory
            label.color = "#ff0000"
            label.name = "긴급"
            label.project = newProject

            label.id shouldBe 10L
            label.category shouldBe newCategory
            label.color shouldBe "#ff0000"
            label.name shouldBe "긴급"
            label.project shouldBe newProject
        }

        it("기본값만으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val project = Project(id = 1L, name = "p", owner = "owner")
            val category = IssueLabelCategory(name = "우선순위", project = project)
            val label = IssueLabel(category = category, project = project)

            label.id shouldBe null
            label.color shouldBe ""
            label.name shouldBe ""
        }
    }
})
