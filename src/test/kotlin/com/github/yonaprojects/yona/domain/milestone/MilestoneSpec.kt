package com.github.yonaprojects.yona.domain.milestone

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class MilestoneSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val project = Project(id = 1L, name = "p", owner = "owner")
            val milestone = Milestone(project = project)
            val newProject = Project(id = 2L, name = "p2", owner = "owner2")
            val dueDate = Instant.parse("2026-08-25T00:00:00Z")

            milestone.id = 10L
            milestone.title = "마일스톤1"
            milestone.dueDate = dueDate
            milestone.contents = "본문"
            milestone.state = State.CLOSED
            milestone.project = newProject

            milestone.id shouldBe 10L
            milestone.title shouldBe "마일스톤1"
            milestone.dueDate shouldBe dueDate
            milestone.contents shouldBe "본문"
            milestone.state shouldBe State.CLOSED
            milestone.project shouldBe newProject
        }

        it("기본값만으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val project = Project(id = 1L, name = "p", owner = "owner")
            val milestone = Milestone(project = project)

            milestone.id shouldBe null
            milestone.title shouldBe ""
            milestone.dueDate shouldBe null
            milestone.contents shouldBe null
            milestone.state shouldBe State.OPEN
            milestone.project shouldBe project
        }
    }
})
