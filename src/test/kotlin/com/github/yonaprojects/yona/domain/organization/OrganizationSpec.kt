package com.github.yonaprojects.yona.domain.organization

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class OrganizationSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val organization = Organization()
            val project = Project(id = 1L, name = "p", owner = "owner")
            val organizationUser = OrganizationUser(organization = organization, user = User(id = 2L), role = Role())
            val enrolledUser = User(id = 3L)
            val now = Instant.now()

            organization.id = 10L
            organization.name = "조직명"
            organization.created = now
            organization.descr = "설명"
            organization.projects = mutableListOf(project)
            organization.organizationUsers = mutableListOf(organizationUser)
            organization.enrolledUsers = mutableListOf(enrolledUser)

            organization.id shouldBe 10L
            organization.name shouldBe "조직명"
            organization.created shouldBe now
            organization.descr shouldBe "설명"
            organization.projects shouldBe mutableListOf(project)
            organization.organizationUsers shouldBe mutableListOf(organizationUser)
            organization.enrolledUsers shouldBe mutableListOf(enrolledUser)
        }

        it("기본값만으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val organization = Organization()

            organization.id shouldBe null
            organization.name shouldBe ""
            organization.created shouldBe null
            organization.descr shouldBe null
            organization.projects shouldBe mutableListOf()
            organization.organizationUsers shouldBe mutableListOf()
            organization.enrolledUsers shouldBe mutableListOf()
        }
    }
})
