package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class AssigneeSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val user = User(id = 1L)
            val project = Project(id = 2L, name = "p", owner = "owner")
            val assignee = Assignee(user = user, project = project)

            val newUser = User(id = 3L)
            val newProject = Project(id = 4L, name = "p2", owner = "owner2")

            assignee.id = 10L
            assignee.user = newUser
            assignee.project = newProject

            assignee.id shouldBe 10L
            assignee.user shouldBe newUser
            assignee.project shouldBe newProject
        }

        it("기본값만으로 생성하면 id가 null이어야 한다") {
            val user = User(id = 1L)
            val project = Project(id = 2L, name = "p", owner = "owner")
            val assignee = Assignee(user = user, project = project)

            assignee.id shouldBe null
        }
    }
})
