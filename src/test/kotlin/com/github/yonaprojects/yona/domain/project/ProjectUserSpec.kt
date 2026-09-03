package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ProjectUserSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val user = User(id = 1L)
            val project = Project(id = 2L, name = "p", owner = "owner")
            val role = Role(id = 3L)
            val projectUser = ProjectUser(user = user, project = project, role = role)

            val newUser = User(id = 4L)
            val newProject = Project(id = 5L, name = "p2", owner = "owner2")
            val newRole = Role(id = 6L)

            projectUser.id = 10L
            projectUser.user = newUser
            projectUser.project = newProject
            projectUser.role = newRole

            projectUser.id shouldBe 10L
            projectUser.user shouldBe newUser
            projectUser.project shouldBe newProject
            projectUser.role shouldBe newRole
        }

        it("기본값만으로 생성하면 id가 null이어야 한다") {
            val user = User(id = 1L)
            val project = Project(id = 2L, name = "p", owner = "owner")
            val role = Role(id = 3L)
            val projectUser = ProjectUser(user = user, project = project, role = role)

            projectUser.id shouldBe null
            projectUser.user shouldBe user
            projectUser.project shouldBe project
            projectUser.role shouldBe role
        }
    }
})
