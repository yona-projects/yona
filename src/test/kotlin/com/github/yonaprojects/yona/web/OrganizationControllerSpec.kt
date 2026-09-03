package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationService
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import io.mockk.clearMocks

// yona AccessControl.java:119-203 isGlobalResourceAllowed()의 ORGANIZATION 케이스
// "user.isSiteManager() || isOrganizationAdmin" 대응 (P0-21). 조직 REST API가 조직 관리자 [GL-utils_AccessControl-008]
// 여부만 검사하고 사이트매니저 전역 우회가 빠져 있던 회귀를 검증한다.
class OrganizationControllerSpec : DescribeSpec({
    val organizationService = mockk<OrganizationService>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val userRepository = mockk<UserRepository>()

    val organizationController = OrganizationController(
        organizationService,
        organizationUserRepository,
        userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(organizationController).build()

    beforeTest {
        clearMocks(organizationService, organizationUserRepository, userRepository)
    }

    val siteManager = User(id = 1L, loginId = "sitemanager", name = "사이트매니저", state = UserState.SITE_ADMIN)
    val regularUser = User(id = 2L, loginId = "regular", name = "일반유저", state = UserState.ACTIVE)
    val siteManagerAuth = UsernamePasswordAuthenticationToken("sitemanager", "password")
    val regularAuth = UsernamePasswordAuthenticationToken("regular", "password")

    describe("PUT /api/organizations/{orgId}/settings") {
        it("조직 관리자가 아니어도 사이트매니저면 설정을 변경할 수 있어야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { userRepository.findById(1L) } returns Optional.of(siteManager)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
            every { organizationService.updateOrganizationSettings(10L, "new-name", null, 1L) } returns Unit

            mockMvc.perform(
                put("/api/organizations/10/settings")
                    .param("name", "new-name")
                    .principal(siteManagerAuth)
            ).andExpect(status().isOk)
        }

        it("조직 관리자도 사이트매니저도 아니면 403을 반환해야 한다") {
            every { userRepository.findByLoginId("regular") } returns Optional.of(regularUser)
            every { userRepository.findById(2L) } returns Optional.of(regularUser)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 2L) } returns Optional.empty()

            mockMvc.perform(
                put("/api/organizations/10/settings")
                    .param("name", "new-name")
                    .principal(regularAuth)
            ).andExpect(status().isForbidden)
        }

        it("조직 관리자면 사이트매니저가 아니어도 설정을 변경할 수 있어야 한다") {
            val orgAdmin = User(id = 3L, loginId = "orgadmin", name = "조직관리자", state = UserState.ACTIVE)
            val orgAdminAuth = UsernamePasswordAuthenticationToken("orgadmin", "password")
            every { userRepository.findByLoginId("orgadmin") } returns Optional.of(orgAdmin)
            every { userRepository.findById(3L) } returns Optional.of(orgAdmin)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 3L) } returns
                Optional.of(
                    OrganizationUser(
                        user = orgAdmin,
                        organization = Organization(id = 10L, name = "testorg"),
                        role = Role(id = RoleType.ORG_ADMIN.roleType)
                    )
                )
            every { organizationService.updateOrganizationSettings(10L, "new-name", null, 3L) } returns Unit

            mockMvc.perform(
                put("/api/organizations/10/settings")
                    .param("name", "new-name")
                    .principal(orgAdminAuth)
            ).andExpect(status().isOk)
        }

        it("조직 관리자면 사이트매니저가 아니어도 설정 변경 시, 서비스 예외 발생 시 400과 에러메시지를 반환해야 한다") {
            val orgAdmin = User(id = 3L, loginId = "orgadmin", name = "조직관리자", state = UserState.ACTIVE)
            val orgAdminAuth = UsernamePasswordAuthenticationToken("orgadmin", "password")
            every { userRepository.findByLoginId("orgadmin") } returns Optional.of(orgAdmin)
            every { userRepository.findById(3L) } returns Optional.of(orgAdmin)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 3L) } returns
                Optional.of(OrganizationUser(user = orgAdmin, organization = Organization(id = 10L, name = "testorg"), role = Role(id = RoleType.ORG_ADMIN.roleType)))
            every { organizationService.updateOrganizationSettings(10L, "new-name", null, 3L) } throws IllegalStateException("이름 중복")

            mockMvc.perform(
                put("/api/organizations/10/settings")
                    .param("name", "new-name")
                    .principal(orgAdminAuth)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("이름 중복"))
        }

        it("조직 멤버지만 관리자 역할이 아니면 403을 반환해야 한다") {
            val member = User(id = 4L, loginId = "member", name = "멤버", state = UserState.ACTIVE)
            val memberAuth = UsernamePasswordAuthenticationToken("member", "password")
            every { userRepository.findByLoginId("member") } returns Optional.of(member)
            every { userRepository.findById(4L) } returns Optional.of(member)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 4L) } returns
                Optional.of(OrganizationUser(user = member, organization = Organization(id = 10L, name = "testorg"), role = Role(id = RoleType.MEMBER.roleType)))

            mockMvc.perform(
                put("/api/organizations/10/settings")
                    .param("name", "new-name")
                    .principal(memberAuth)
            ).andExpect(status().isForbidden)
        }
    }

    describe("POST /api/organizations (createOrganization)") {
        it("인증되지 않은 요청은 400으로 처리되어야 한다") {
            mockMvc.perform(post("/api/organizations").param("name", "neworg"))
                .andExpect(status().isBadRequest)
        }

        it("로그인 사용자를 찾을 수 없으면 400으로 처리되어야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.empty()

            mockMvc.perform(post("/api/organizations").param("name", "neworg").principal(siteManagerAuth))
                .andExpect(status().isBadRequest)
        }

        it("정상적으로 조직을 생성하면 status success와 orgId를 반환해야 한다") {
            val createdOrg = Organization(id = 30L, name = "neworg")
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { organizationService.createOrganization("neworg", "설명", 1L) } returns createdOrg

            mockMvc.perform(
                post("/api/organizations").param("name", "neworg").param("descr", "설명").principal(siteManagerAuth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.orgId").value(30))
        }

        it("조직 생성 중 예외가 발생하면 400과 에러메시지를 반환해야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { organizationService.createOrganization("dup", null, 1L) } throws IllegalStateException("이미 존재하는 조직명")

            mockMvc.perform(post("/api/organizations").param("name", "dup").principal(siteManagerAuth))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("이미 존재하는 조직명"))
        }
    }

    describe("POST /api/organizations/{orgId}/members (addOrganizationMember)") {
        it("조직 관리자가 아니면 403을 반환해야 한다") {
            every { userRepository.findByLoginId("regular") } returns Optional.of(regularUser)
            every { userRepository.findById(2L) } returns Optional.of(regularUser)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 2L) } returns Optional.empty()

            mockMvc.perform(
                post("/api/organizations/10/members")
                    .param("userLoginId", "newmember")
                    .param("roleId", RoleType.MEMBER.roleType.toString())
                    .principal(regularAuth)
            ).andExpect(status().isForbidden)
        }

        it("사이트매니저면 조직 관리자가 아니어도 멤버를 추가할 수 있어야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { userRepository.findById(1L) } returns Optional.of(siteManager)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
            every { organizationService.addOrganizationMember(10L, "newmember", RoleType.MEMBER.roleType, 1L) } returns Unit

            mockMvc.perform(
                post("/api/organizations/10/members")
                    .param("userLoginId", "newmember")
                    .param("roleId", RoleType.MEMBER.roleType.toString())
                    .principal(siteManagerAuth)
            ).andExpect(status().isOk)
        }

        it("멤버 추가 중 예외가 발생하면 400과 에러메시지를 반환해야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { userRepository.findById(1L) } returns Optional.of(siteManager)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
            every { organizationService.addOrganizationMember(10L, "unknown", RoleType.MEMBER.roleType, 1L) } throws IllegalStateException("존재하지 않는 사용자")

            mockMvc.perform(
                post("/api/organizations/10/members")
                    .param("userLoginId", "unknown")
                    .param("roleId", RoleType.MEMBER.roleType.toString())
                    .principal(siteManagerAuth)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("존재하지 않는 사용자"))
        }
    }

    describe("PUT /api/organizations/{orgId}/members/{userId}/role (updateOrganizationMemberRole)") {
        it("조직 관리자가 아니면 403을 반환해야 한다") {
            every { userRepository.findByLoginId("regular") } returns Optional.of(regularUser)
            every { userRepository.findById(2L) } returns Optional.of(regularUser)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 2L) } returns Optional.empty()

            mockMvc.perform(
                put("/api/organizations/10/members/5/role")
                    .param("roleId", RoleType.MANAGER.roleType.toString())
                    .principal(regularAuth)
            ).andExpect(status().isForbidden)
        }

        it("조직 관리자면 멤버의 역할을 변경할 수 있어야 한다") {
            val orgAdmin = User(id = 3L, loginId = "orgadmin", name = "조직관리자", state = UserState.ACTIVE)
            val orgAdminAuth = UsernamePasswordAuthenticationToken("orgadmin", "password")
            every { userRepository.findByLoginId("orgadmin") } returns Optional.of(orgAdmin)
            every { userRepository.findById(3L) } returns Optional.of(orgAdmin)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 3L) } returns
                Optional.of(OrganizationUser(user = orgAdmin, organization = Organization(id = 10L, name = "testorg"), role = Role(id = RoleType.ORG_ADMIN.roleType)))
            every { organizationService.updateOrganizationMemberRole(10L, 5L, RoleType.MANAGER.roleType, 3L) } returns Unit

            mockMvc.perform(
                put("/api/organizations/10/members/5/role")
                    .param("roleId", RoleType.MANAGER.roleType.toString())
                    .principal(orgAdminAuth)
            ).andExpect(status().isOk)
        }

        it("역할 변경 중 예외가 발생하면 400과 에러메시지를 반환해야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { userRepository.findById(1L) } returns Optional.of(siteManager)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
            every { organizationService.updateOrganizationMemberRole(10L, 5L, RoleType.MANAGER.roleType, 1L) } throws IllegalStateException("대상 멤버 없음")

            mockMvc.perform(
                put("/api/organizations/10/members/5/role")
                    .param("roleId", RoleType.MANAGER.roleType.toString())
                    .principal(siteManagerAuth)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("대상 멤버 없음"))
        }
    }

    describe("DELETE /api/organizations/{orgId}/members/{userId} (removeOrganizationMember)") {
        it("본인 탈퇴는 조직 관리자가 아니어도 허용되어야 한다") {
            every { userRepository.findByLoginId("regular") } returns Optional.of(regularUser)
            every { organizationService.removeOrganizationMember(10L, 2L, 2L) } returns Unit

            mockMvc.perform(delete("/api/organizations/10/members/2").principal(regularAuth))
                .andExpect(status().isOk)

            io.mockk.verify(exactly = 0) { userRepository.findById(any()) }
        }

        it("본인이 아닌 다른 멤버를 조직 관리자가 제거할 수 있어야 한다") {
            val orgAdmin = User(id = 3L, loginId = "orgadmin", name = "조직관리자", state = UserState.ACTIVE)
            val orgAdminAuth = UsernamePasswordAuthenticationToken("orgadmin", "password")
            every { userRepository.findByLoginId("orgadmin") } returns Optional.of(orgAdmin)
            every { userRepository.findById(3L) } returns Optional.of(orgAdmin)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 3L) } returns
                Optional.of(OrganizationUser(user = orgAdmin, organization = Organization(id = 10L, name = "testorg"), role = Role(id = RoleType.ORG_ADMIN.roleType)))
            every { organizationService.removeOrganizationMember(10L, 5L, 3L) } returns Unit

            mockMvc.perform(delete("/api/organizations/10/members/5").principal(orgAdminAuth))
                .andExpect(status().isOk)
        }

        it("본인도 아니고 조직 관리자도 아니면 403을 반환해야 한다") {
            every { userRepository.findByLoginId("regular") } returns Optional.of(regularUser)
            every { userRepository.findById(2L) } returns Optional.of(regularUser)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 2L) } returns Optional.empty()

            mockMvc.perform(delete("/api/organizations/10/members/5").principal(regularAuth))
                .andExpect(status().isForbidden)
        }

        it("멤버 제거 중 예외가 발생하면 400과 에러메시지를 반환해야 한다") {
            every { userRepository.findByLoginId("regular") } returns Optional.of(regularUser)
            every { organizationService.removeOrganizationMember(10L, 2L, 2L) } throws IllegalStateException("이미 탈퇴한 멤버")

            mockMvc.perform(delete("/api/organizations/10/members/2").principal(regularAuth))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("이미 탈퇴한 멤버"))
        }
    }

    describe("DELETE /api/organizations/{orgId}") {
        it("조직 관리자가 아니어도 사이트매니저면 조직을 삭제할 수 있어야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { userRepository.findById(1L) } returns Optional.of(siteManager)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
            every { organizationService.deleteOrganization(10L, 1L) } returns Unit

            mockMvc.perform(delete("/api/organizations/10").principal(siteManagerAuth))
                .andExpect(status().isOk)
        }

        it("조직 관리자도 사이트매니저도 아니면 403을 반환해야 한다") {
            every { userRepository.findByLoginId("regular") } returns Optional.of(regularUser)
            every { userRepository.findById(2L) } returns Optional.of(regularUser)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 2L) } returns Optional.empty()

            mockMvc.perform(delete("/api/organizations/10").principal(regularAuth))
                .andExpect(status().isForbidden)
        }

        it("조직 삭제 중 예외가 발생하면 400과 에러메시지를 반환해야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { userRepository.findById(1L) } returns Optional.of(siteManager)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
            every { organizationService.deleteOrganization(10L, 1L) } throws IllegalStateException("하위 프로젝트 존재")

            mockMvc.perform(delete("/api/organizations/10").principal(siteManagerAuth))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("하위 프로젝트 존재"))
        }
    }

    describe("예외 메시지가 null인 경우 각 엔드포인트의 기본 에러메시지 사용") {
        it("createOrganization: 예외 메시지가 없으면 기본 메시지를 사용해야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { organizationService.createOrganization("neworg2", null, 1L) } throws IllegalStateException()

            mockMvc.perform(post("/api/organizations").param("name", "neworg2").principal(siteManagerAuth))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Failed to create organization"))
        }

        it("updateOrganizationSettings: 예외 메시지가 없으면 기본 메시지를 사용해야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { userRepository.findById(1L) } returns Optional.of(siteManager)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
            every { organizationService.updateOrganizationSettings(10L, "new-name", null, 1L) } throws IllegalStateException()

            mockMvc.perform(put("/api/organizations/10/settings").param("name", "new-name").principal(siteManagerAuth))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Failed to update organization"))
        }

        it("addOrganizationMember: 예외 메시지가 없으면 기본 메시지를 사용해야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { userRepository.findById(1L) } returns Optional.of(siteManager)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
            every { organizationService.addOrganizationMember(10L, "unknown2", RoleType.MEMBER.roleType, 1L) } throws IllegalStateException()

            mockMvc.perform(
                post("/api/organizations/10/members")
                    .param("userLoginId", "unknown2")
                    .param("roleId", RoleType.MEMBER.roleType.toString())
                    .principal(siteManagerAuth)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Failed to add organization member"))
        }

        it("updateOrganizationMemberRole: 예외 메시지가 없으면 기본 메시지를 사용해야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { userRepository.findById(1L) } returns Optional.of(siteManager)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
            every { organizationService.updateOrganizationMemberRole(10L, 6L, RoleType.MANAGER.roleType, 1L) } throws IllegalStateException()

            mockMvc.perform(
                put("/api/organizations/10/members/6/role")
                    .param("roleId", RoleType.MANAGER.roleType.toString())
                    .principal(siteManagerAuth)
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Failed to update member role"))
        }

        it("removeOrganizationMember: 예외 메시지가 없으면 기본 메시지를 사용해야 한다") {
            every { userRepository.findByLoginId("regular") } returns Optional.of(regularUser)
            every { organizationService.removeOrganizationMember(10L, 2L, 2L) } throws IllegalStateException()

            mockMvc.perform(delete("/api/organizations/10/members/2").principal(regularAuth))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Failed to remove member"))
        }

        it("deleteOrganization: 예외 메시지가 없으면 기본 메시지를 사용해야 한다") {
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { userRepository.findById(1L) } returns Optional.of(siteManager)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()
            every { organizationService.deleteOrganization(10L, 1L) } throws IllegalStateException()

            mockMvc.perform(delete("/api/organizations/10").principal(siteManagerAuth))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value("Failed to delete organization"))
        }
    }
})
