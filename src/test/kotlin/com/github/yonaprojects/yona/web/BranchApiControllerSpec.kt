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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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

class BranchApiControllerSpec : DescribeSpec({
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

    val branchApiController = BranchApiController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(branchApiController).build()

    beforeTest {
        clearMocks(projectRepository, projectUserRepository, userRepository, repositoryService, playRepository)
    }

    describe("BranchApiController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner", vcs = "git", projectScope = ProjectScope.PUBLIC)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("POST /{owner}/{projectName}/code/{branch}/setAsDefault") {
            it("성공 시 302 리다이렉트와 setDefaultBranch 메소드가 정상 호출되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.setDefaultBranch("feature-a") } returns Unit

                mockMvc.perform(
                    post("/owner/TestProject/code/feature-a/setAsDefault").principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/owner/TestProject/branches"))

                verify { playRepository.setDefaultBranch("feature-a") }
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 성공해야 한다") {
                val org = Organization(id = 1L, name = "org")
                val groupProject = Project(id = 1L, name = "TestProject", owner = "owner", vcs = "git", projectScope = ProjectScope.PROTECTED, organization = org)
                org.organizationUsers.add(
                    OrganizationUser(
                        id = 1L, user = user, organization = org,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false
                every { repositoryService.getRepository(groupProject) } returns playRepository
                every { playRepository.setDefaultBranch("feature-a") } returns Unit

                mockMvc.perform(
                    post("/owner/TestProject/code/feature-a/setAsDefault").principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)

                verify { playRepository.setDefaultBranch("feature-a") }
            }

            // yona BranchApp.java:47 @IsOnlyGitAvailable 대응 (P2-29). SVN 프로젝트에 브랜치
            // 기본값 지정을 요청하면 아무 일도 하지 않은 채(no-op) 성공 신호를 돌려주지 않고
            // 400으로 명확히 거부해야 한다.
            it("Git이 아닌(SVN) 프로젝트면 400을 반환하고 setDefaultBranch를 호출하지 않아야 한다 (P2-29)") {
                val svnProject = Project(id = 2L, name = "SvnProject", owner = "owner", vcs = "SVN", projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "SvnProject") } returns Optional.of(svnProject)

                mockMvc.perform(
                    post("/owner/SvnProject/code/feature-a/setAsDefault").principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 0) { playRepository.setDefaultBranch(any()) }
            }

            it("존재하지 않는 프로젝트면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

                mockMvc.perform(
                    post("/owner/nosuch/code/feature-a/setAsDefault").principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 0) { playRepository.setDefaultBranch(any()) }
            }

            it("vcs가 null이면 Git이 아닌 것으로 취급해 400을 반환해야 한다") {
                val noVcsProject = Project(id = 4L, name = "NoVcsProject", owner = "owner", vcs = null, projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoVcsProject") } returns Optional.of(noVcsProject)

                mockMvc.perform(
                    post("/owner/NoVcsProject/code/feature-a/setAsDefault").principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/badrequest"))

                verify(exactly = 0) { playRepository.setDefaultBranch(any()) }
            }

            it("비로그인 사용자는 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)

                mockMvc.perform(
                    post("/owner/TestProject/code/feature-a/setAsDefault")
                )
                    .andExpect(status().isOk)

                verify(exactly = 0) { playRepository.setDefaultBranch(any()) }
            }

            it("로그인했지만 멤버도 그룹멤버도 아니면 error/forbidden 뷰를 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 99L) } returns false

                mockMvc.perform(
                    post("/owner/TestProject/code/feature-a/setAsDefault").principal(strangerAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 0) { playRepository.setDefaultBranch(any()) }
            }
        }

        describe("DELETE /{owner}/{projectName}/code/{branch}") {
            it("매니저가 삭제를 요청하면 302 리다이렉트와 deleteBranch 메소드가 정상 호출되어야 한다 (P1-97, legacy PROJECT DELETE는 매니저/조직관리자 전용)") {
                val managerUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                managerUser.projectUsers.add(
                    ProjectUser(
                        id = 200L, user = managerUser, project = project,
                        role = Role(id = RoleType.MANAGER.roleType)
                    )
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(managerUser)
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.deleteBranch("feature-a") } returns Unit

                mockMvc.perform(
                    delete("/owner/TestProject/code/feature-a").principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/owner/TestProject/branches"))

                verify { playRepository.deleteBranch("feature-a") }
            }

            it("매니저가 아닌 일반 멤버가 삭제를 요청하면 403 Forbidden 화면을 반환해야 한다 (P1-97)") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(
                    ProjectUser(
                        id = 201L, user = memberUser, project = project,
                        role = Role(id = RoleType.MEMBER.roleType)
                    )
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)

                mockMvc.perform(
                    delete("/owner/TestProject/code/feature-a").principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 0) { playRepository.deleteBranch(any()) }
            }

            // yona BranchApp.java:47 @IsOnlyGitAvailable 대응 (P2-29).
            it("Git이 아닌(SVN) 프로젝트면 400을 반환하고 deleteBranch를 호출하지 않아야 한다 (P2-29)") {
                val svnProject = Project(id = 3L, name = "SvnProject2", owner = "owner", vcs = "SUBVERSION", projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "SvnProject2") } returns Optional.of(svnProject)

                mockMvc.perform(
                    delete("/owner/SvnProject2/code/feature-a").principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 0) { playRepository.deleteBranch(any()) }
            }

            it("존재하지 않는 프로젝트면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "nosuch") } returns Optional.empty()

                mockMvc.perform(
                    delete("/owner/nosuch/code/feature-a").principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 0) { playRepository.deleteBranch(any()) }
            }

            it("vcs가 null이면 Git이 아닌 것으로 취급해 400을 반환해야 한다") {
                val noVcsProject = Project(id = 5L, name = "NoVcsProject2", owner = "owner", vcs = null, projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoVcsProject2") } returns Optional.of(noVcsProject)

                mockMvc.perform(
                    delete("/owner/NoVcsProject2/code/feature-a").principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/badrequest"))

                verify(exactly = 0) { playRepository.deleteBranch(any()) }
            }

            it("비로그인 사용자는 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)

                mockMvc.perform(
                    delete("/owner/TestProject/code/feature-a")
                )
                    .andExpect(status().isOk)

                verify(exactly = 0) { playRepository.deleteBranch(any()) }
            }
        }
    }
})
