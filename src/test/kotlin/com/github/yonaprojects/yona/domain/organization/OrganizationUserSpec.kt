package com.github.yonaprojects.yona.domain.organization

import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class OrganizationUserSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val user = User(id = 1L)
            val organization = Organization(id = 2L)
            val role = Role(id = 3L)
            val organizationUser = OrganizationUser(user = user, organization = organization, role = role)

            val newUser = User(id = 4L)
            val newOrganization = Organization(id = 5L)
            val newRole = Role(id = 6L)

            organizationUser.id = 10L
            organizationUser.user = newUser
            organizationUser.organization = newOrganization
            organizationUser.role = newRole

            organizationUser.id shouldBe 10L
            organizationUser.user shouldBe newUser
            organizationUser.organization shouldBe newOrganization
            organizationUser.role shouldBe newRole
        }

        it("기본값만으로 생성하면 id가 null이어야 한다") {
            val user = User(id = 1L)
            val organization = Organization(id = 2L)
            val role = Role(id = 3L)
            val organizationUser = OrganizationUser(user = user, organization = organization, role = role)

            organizationUser.id shouldBe null
            organizationUser.user shouldBe user
            organizationUser.organization shouldBe organization
            organizationUser.role shouldBe role
        }
    }
})
