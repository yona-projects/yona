package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.ProjectTransfer
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserService
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import com.github.yonaprojects.yona.domain.project.TitleHeadService
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.mockk
import org.springframework.context.MessageSource
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// v1.6 ProjectApp의 설정/삭제/이전/멤버 관리는 @IsAllowed(Operation.UPDATE|DELETE, ResourceType.PROJECT)라
// 사이트 관리자와 (조직 소속 프로젝트의) 조직 관리자도 통과했다. next는 "직접 MANAGER 역할"만 허용해 이들을 차단했다.
class ProjectAdminOverrideSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val projectUserService = mockk<ProjectUserService>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        mockk<UserRepository>(), mockk<OrganizationRepository>(), mockk<IssueRepository>(),
        mockk<PostingRepository>(), mockk<ReviewCommentRepository>(), mockk<CommitCommentRepository>(),
        mockk<MilestoneRepository>()
    )

    val projectController = ProjectController(
        projectService, projectRepository, projectUserRepository, userRepository, mockk<PushedBranchRepository>(),
        accessControl, mockk<TitleHeadService>(), mockk<IssueLabelRepository>(), mockk<AttachmentRepository>(),
        mockk<AttachmentService>()
    )
    val memberController = ProjectMemberController(
        projectUserService, projectRepository, projectUserRepository, userRepository, mockk<MessageSource>(),
        accessControl, organizationUserRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(projectController, memberController).build()

    val org = Organization(id = 5L, name = "corp")
    val project = Project(id = 1L, name = "TestProject", owner = "owner", projectScope = ProjectScope.PRIVATE)
    val orgProject = Project(id = 2L, name = "OrgProject", owner = "corp", projectScope = ProjectScope.PUBLIC).also { it.organization = org }

    val siteAdmin = User(id = 11L, loginId = "siteadmin", name = "사이트관리자", isSiteAdmin = true)
    val siteAdminAuth = UsernamePasswordAuthenticationToken("siteadmin", "password")
    val orgAdmin = User(id = 12L, loginId = "orgadmin", name = "조직관리자")
    val orgAdminAuth = UsernamePasswordAuthenticationToken("orgadmin", "password")

    beforeTest {
        every { userRepository.findByLoginId("siteadmin") } returns Optional.of(siteAdmin)
        every { userRepository.findById(11L) } returns Optional.of(siteAdmin)
        every { userRepository.findByLoginId("orgadmin") } returns Optional.of(orgAdmin)
        every { userRepository.findById(12L) } returns Optional.of(orgAdmin)
        every { projectRepository.findById(1L) } returns Optional.of(project)
        every { projectRepository.findById(2L) } returns Optional.of(orgProject)
        every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
        every { projectUserRepository.findByProjectIdAndUserId(any(), any()) } returns Optional.empty()
        every { projectUserRepository.existsByProjectIdAndUserId(any(), any()) } returns false
        every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
        every { organizationUserRepository.findByOrganizationIdAndUserId(5L, 12L) } returns
            Optional.of(OrganizationUser(user = orgAdmin, organization = org, role = Role(id = RoleType.ORG_ADMIN.roleType)))
        every { projectUserService.addMember(any(), any(), any()) } just Runs
    }

    describe("사이트 관리자 (프로젝트 비멤버)") {
        it("프로젝트를 삭제할 수 있다") {
            every { projectService.deleteProject(1L) } just Runs
            mockMvc.perform(delete("/api/projects/1").principal(siteAdminAuth)).andExpect(status().isOk)
        }

        it("프로젝트 이전을 요청할 수 있다") {
            every { projectService.requestNewTransfer(1L, 11L, "dest") } returns
                ProjectTransfer(id = 1L, sender = siteAdmin, destination = "dest", project = project)
            mockMvc.perform(post("/api/owner/TestProject/transfer").param("destination", "dest").principal(siteAdminAuth))
                .andExpect(status().isOk)
        }

        it("멤버를 추가할 수 있다") {
            mockMvc.perform(post("/api/projects/1/members").param("loginId", "newbie").principal(siteAdminAuth))
                .andExpect(status().isOk)
        }
    }

    describe("포크 권한 (v1.6 IsCreatable FORK)") {
        val stranger = User(id = 13L, loginId = "stranger", name = "이방인")
        val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")

        it("비멤버는 비공개 프로젝트를 REST로 포크할 수 없다") {
            every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
            every { userRepository.findById(13L) } returns Optional.of(stranger)

            mockMvc.perform(post("/api/owner/TestProject/fork").principal(strangerAuth))
                .andExpect(status().isForbidden)

            io.mockk.verify(exactly = 0) { projectService.forkProject(any(), any(), any(), any()) }
        }
    }

    describe("조직 관리자 (조직 소속 프로젝트의 비멤버)") {
        it("멤버를 추가할 수 있다") {
            mockMvc.perform(post("/api/projects/2/members").param("loginId", "newbie").principal(orgAdminAuth))
                .andExpect(status().isOk)
        }

        it("프로젝트를 삭제할 수 있다") {
            every { projectService.deleteProject(2L) } just Runs
            mockMvc.perform(delete("/api/projects/2").principal(orgAdminAuth)).andExpect(status().isOk)
        }
    }
})
