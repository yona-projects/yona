package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType

class StatisticsViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val userRepositoryForAccessControl = mockk<UserRepository>()
    val organizationRepositoryForAccessControl = mockk<OrganizationRepository>()
    val issueRepositoryForAccessControl = mockk<IssueRepository>()
    val postingRepositoryForAccessControl = mockk<PostingRepository>()
    val reviewCommentRepositoryForAccessControl = mockk<ReviewCommentRepository>()
    val commitCommentRepositoryForAccessControl = mockk<CommitCommentRepository>()
    val milestoneRepositoryForAccessControl = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepositoryForAccessControl, organizationRepositoryForAccessControl,
        issueRepositoryForAccessControl, postingRepositoryForAccessControl,
        reviewCommentRepositoryForAccessControl, commitCommentRepositoryForAccessControl,
        milestoneRepositoryForAccessControl
    )

    val statisticsViewController = StatisticsViewController(
        projectRepository,
        userRepository,
        projectUserRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(statisticsViewController).build()

    beforeTest {
        clearMocks(projectRepository, userRepository, projectUserRepository)
    }

    describe("StatisticsViewController 템플릿 연동 테스트") {
        val privateProject = Project(id = 1L, name = "PrivateProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val publicProject = Project(id = 2L, name = "PublicProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /{owner}/{projectName}/statistics") {
            it("비공개 프로젝트일 때 로그인한 멤버라면 200 OK와 project/statistics 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PrivateProj") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/PrivateProj/statistics").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/statistics"))
                    .andExpect(model().attributeExists("project", "currentUser"))
            }

            it("공개 프로젝트일 때 로그인한 사용자라면 멤버가 아니더라도 200 OK와 project/statistics 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PublicProj") } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/owner/PublicProj/statistics").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/statistics"))
                    .andExpect(model().attributeExists("project", "currentUser"))
            }

            // yona StatisticsApp.java:30 @AnonymousCheck(기본값 requiresLogin=false) 대응 (P1-138) —
            // 프로젝트 스코프 확인 전에 익명 사용자를 무조건 막던 회귀를 수정. 공개 프로젝트는 익명도 볼 수 있다.
            it("공개 프로젝트는 로그인하지 않은 익명 사용자도 200 OK와 project/statistics 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PublicProj") } returns Optional.of(publicProject)

                mockMvc.perform(get("/owner/PublicProj/statistics"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/statistics"))
            }

            it("비공개 프로젝트는 로그인하지 않은 익명 사용자면 forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PrivateProj") } returns Optional.of(privateProject)

                mockMvc.perform(get("/owner/PrivateProj/statistics"))
                    .andExpect(view().name("error/forbidden"))
                    .andExpect(model().attributeExists("project"))
            }

            it("비공개 프로젝트이고 로그인한 사용자이지만 멤버가 아닐 때 forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PrivateProj") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/PrivateProj/statistics").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
                    .andExpect(model().attributeExists("project"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = Organization(id = 1L, name = "org")
                groupOrg.organizationUsers.add(
                    OrganizationUser(
                        id = 1L, user = user, organization = groupOrg,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 14L, name = "GroupProj", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GroupProj") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(14L, 10L) } returns false

                mockMvc.perform(get("/owner/GroupProj/statistics").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/statistics"))
            }

            it("프로젝트가 존재하지 않을 때 404 Not Found 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NonExistProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NonExistProj/statistics").principal(userAuth))
                    .andExpect(view().name("error/404"))
            }
        }
    }
})
