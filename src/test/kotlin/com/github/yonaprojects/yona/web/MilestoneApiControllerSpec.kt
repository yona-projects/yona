package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona controllers/api/MilestoneApi.java newMilestone() 대응 (P2-58)
class MilestoneApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val organizationRepository = mockk<OrganizationRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val milestoneRepositoryForAccessControl = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepository, organizationRepository,
        issueRepository, postingRepository,
        reviewCommentRepository, commitCommentRepository,
        milestoneRepositoryForAccessControl
    )

    val controller = MilestoneApiController(projectRepository, milestoneRepository, userRepository, accessControl)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    val project = Project(id = 1L, name = "myproject", owner = "alice")
    val user = User(id = 10L, loginId = "alice", name = "Alice")
    user.projectUsers.add(ProjectUser(id = 1L, user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))
    val auth = UsernamePasswordAuthenticationToken(user.loginId, null, emptyList())

    beforeTest {
        every { projectRepository.findByOwnerAndNameOrPreviousPlace("alice", "myproject") } returns Optional.of(project)
        every { userRepository.findByLoginId("alice") } returns Optional.of(user)
    }

    describe("POST /-_-api/v1/owners/{owner}/projects/{projectName}/milestones") {
        it("마일스톤 배열을 벌크 생성한다") {
            every { milestoneRepository.findByProjectAndTitle(project, "v1.0") } returns null
            every { milestoneRepository.save(any()) } answers { firstArg<com.github.yonaprojects.yona.domain.milestone.Milestone>().apply { id = 99L } }

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/myproject/milestones")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("""{"milestones":[{"title":"v1.0","description":"desc","state":"open"}]}""")
                    .principal(auth)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$[0].id").value(99))
                .andExpect(jsonPath("$[0].title").value("v1.0"))
        }

        it("프로젝트가 없으면 404를 반환한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("alice", "nosuch") } returns Optional.empty()

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/nosuch/milestones")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("""{"milestones":[]}""")
                    .principal(auth)
            ).andExpect(status().isNotFound)
        }
    }
})
