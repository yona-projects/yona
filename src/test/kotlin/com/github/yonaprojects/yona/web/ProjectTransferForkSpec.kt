package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.*
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import io.mockk.clearMocks

class ProjectTransferForkSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val pushedBranchRepository = mockk<PushedBranchRepository>()
    val titleHeadService = mockk<TitleHeadService>()
    val issueLabelRepository = mockk<IssueLabelRepository>()
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

    val projectController = ProjectController(
        projectService,
        projectRepository,
        projectUserRepository,
        userRepository,
        pushedBranchRepository,
        accessControl,
        titleHeadService,
        issueLabelRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(projectController).build()
    val auth = UsernamePasswordAuthenticationToken("gildong", "pass")

    beforeTest {
        clearMocks(projectService, projectRepository, projectUserRepository, userRepository)
    }

    describe("프로젝트 이관 및 포크 TDD 검증") {
        val user = User(id = 1L, loginId = "gildong", name = "길동")
        val project = Project(id = 10L, name = "yona-project", owner = "gildong")
        val managerRole = Role(id = RoleType.MANAGER.roleType)
        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = managerRole)

        it("프로젝트 이관 요청 API 호출 시 정상 응답을 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
            every { projectUserRepository.findByProjectIdAndUserId(10L, 1L) } returns Optional.of(projectUser)
            
            val pt = ProjectTransfer(
                id = 50L,
                project = project,
                sender = user,
                destination = "another-owner",
                confirmKey = "testKey",
                newProjectName = "yona-project",
                requested = Instant.now()
            )
            every { projectService.requestNewTransfer(10L, 1L, "another-owner") } returns pt

            mockMvc.perform(
                post("/api/gildong/yona-project/transfer")
                    .param("destination", "another-owner")
                    .principal(auth)
            )
                .andExpect(status().isOk)

            verify { projectService.requestNewTransfer(10L, 1L, "another-owner") }
        }

        it("프로젝트 이관 수락 API 호출 시 정상 응답을 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectService.acceptTransfer(50L, "testKey", 1L) } returns Unit

            mockMvc.perform(
                post("/api/projects/transfer/50/accept")
                    .param("confirmKey", "testKey")
                    .principal(auth)
            )
                .andExpect(status().isOk)

            verify { projectService.acceptTransfer(50L, "testKey", 1L) }
        }

        it("프로젝트 포크 API 호출 시 자식 프로젝트를 생성하고 정상 응답을 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
            
            val forkedProject = Project(id = 20L, name = "yona-project", owner = "gildong", originalProject = project)
            every { projectService.forkProject(10L, 1L) } returns forkedProject

            mockMvc.perform(
                post("/api/gildong/yona-project/fork")
                    .principal(auth)
            )
                .andExpect(status().isOk)

            verify { projectService.forkProject(10L, 1L) }
        }
    }
})
