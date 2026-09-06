package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
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
import com.github.yonaprojects.yona.domain.project.ProjectUser

// yona-wiki P3-10 — BranchApiControllerSpec의 DELETE 케이스와 정확히 같은 패턴의 태그 삭제 테스트.
class TagApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val playRepository = mockk<PlayRepository>()
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

    val tagApiController = TagApiController(
        projectRepository,
        userRepository,
        repositoryService,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(tagApiController).build()

    beforeTest {
        clearMocks(projectRepository, projectUserRepository, userRepository, repositoryService, playRepository)
    }

    describe("TagApiController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner", vcs = "git", projectScope = ProjectScope.PUBLIC)
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("DELETE /{owner}/{projectName}/tags/{tag}") {
            it("매니저가 삭제를 요청하면 302 리다이렉트와 deleteTag 메소드가 정상 호출되어야 한다") {
                val managerUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                managerUser.projectUsers.add(
                    ProjectUser(id = 200L, user = managerUser, project = project, role = Role(id = RoleType.MANAGER.roleType))
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(managerUser)
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.deleteTag("v1.0") } returns Unit

                mockMvc.perform(delete("/owner/TestProject/tags/v1.0").principal(userAuth))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/owner/TestProject/tags"))

                verify { playRepository.deleteTag("v1.0") }
            }

            // yona-wiki P3-10 "직접 멤버가 아니어도 조직 멤버라면" 케이스(BranchApiControllerSpec과 동일한 근거)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 관리자라면 성공해야 한다") {
                val org = Organization(id = 1L, name = "org")
                val orgAdminUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                val groupProject = Project(id = 1L, name = "TestProject", owner = "owner", vcs = "git", projectScope = ProjectScope.PROTECTED, organization = org)
                val orgAdminMembership = OrganizationUser(id = 1L, user = orgAdminUser, organization = org, role = Role(id = RoleType.ORG_ADMIN.roleType))
                org.organizationUsers.add(orgAdminMembership)
                // AccessControl.isOrganizationAdmin(Organization, User)는(Operation.DELETE 판정 경로)
                // organization.organizationUsers 인메모리 컬렉션이 아니라
                // organizationUserRepository.findByOrganizationIdAndUserId()를 실제로 조회한다 —
                // isAllowedIfGroupMember()(인메모리 컬렉션 기반)와 혼동하지 않도록 주의.
                every { organizationUserRepository.findByOrganizationIdAndUserId(1L, 10L) } returns Optional.of(orgAdminMembership)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(orgAdminUser)
                every { repositoryService.getRepository(groupProject) } returns playRepository
                every { playRepository.deleteTag("v1.0") } returns Unit

                mockMvc.perform(delete("/owner/TestProject/tags/v1.0").principal(userAuth))
                    .andExpect(status().is3xxRedirection)

                verify { playRepository.deleteTag("v1.0") }
            }

            it("매니저가 아닌 일반 멤버가 삭제를 요청하면 error/forbidden 화면을 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(
                    ProjectUser(id = 201L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType))
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)

                mockMvc.perform(delete("/owner/TestProject/tags/v1.0").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden"))

                verify(exactly = 0) { playRepository.deleteTag(any()) }
            }

            it("Git이 아닌(SVN) 프로젝트면 400을 반환하고 deleteTag를 호출하지 않아야 한다") {
                val svnProject = Project(id = 3L, name = "SvnProject", owner = "owner", vcs = "SUBVERSION", projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "SvnProject") } returns Optional.of(svnProject)

                mockMvc.perform(delete("/owner/SvnProject/tags/v1.0").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/badrequest"))

                verify(exactly = 0) { playRepository.deleteTag(any()) }
            }

            it("존재하지 않는 프로젝트면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

                mockMvc.perform(delete("/owner/nosuch/tags/v1.0").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))

                verify(exactly = 0) { playRepository.deleteTag(any()) }
            }

            it("비로그인 사용자는 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)

                mockMvc.perform(delete("/owner/TestProject/tags/v1.0"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden"))

                verify(exactly = 0) { playRepository.deleteTag(any()) }
            }

            it("URL 인코딩된 태그 이름을 디코딩해 deleteTag에 전달해야 한다") {
                val managerUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                managerUser.projectUsers.add(
                    ProjectUser(id = 202L, user = managerUser, project = project, role = Role(id = RoleType.MANAGER.roleType))
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(managerUser)
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.deleteTag("release v1") } returns Unit

                mockMvc.perform(delete("/owner/TestProject/tags/release%20v1").principal(userAuth))
                    .andExpect(status().is3xxRedirection)

                verify { playRepository.deleteTag("release v1") }
            }
        }
    }
})
