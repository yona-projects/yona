package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.board.PostingService
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona controllers/api/BoardApi.java 대응 (P2-57)
class BoardApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val postingRepository = mockk<PostingRepository>()
    val postingService = mockk<PostingService>()
    val userRepository = mockk<UserRepository>()
    val issueLabelRepository = mockk<IssueLabelRepository>()

    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val organizationRepository = mockk<OrganizationRepository>()
    val issueRepository = mockk<IssueRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepository, organizationRepository,
        issueRepository, postingRepository,
        reviewCommentRepository, commitCommentRepository,
        milestoneRepository
    )

    val controller = BoardApiController(
        projectRepository, postingRepository, postingService, userRepository, issueLabelRepository, accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    val project = Project(id = 1L, name = "myproject", owner = "alice")
    val user = User(id = 10L, loginId = "alice", name = "Alice")
    user.projectUsers.add(ProjectUser(id = 1L, user = user, project = project, role = Role(id = RoleType.MANAGER.roleType)))
    val auth = UsernamePasswordAuthenticationToken(user.loginId, null, emptyList())

    beforeTest {
        every { projectRepository.findByOwnerAndNameOrPreviousPlace("alice", "myproject") } returns Optional.of(project)
        every { userRepository.findByLoginId("alice") } returns Optional.of(user)
    }

    describe("POST /-_-api/v1/owners/{owner}/projects/{projectName}/postlabel/{number}") {
        it("게시글 라벨 집합을 통째로 교체한다") {
            val posting = Posting(id = 60L, title = "글", project = project, number = 6L, authorId = 10L)
            every { postingRepository.findByProjectAndNumber(project, 6L) } returns posting
            val category = IssueLabelCategory(project = project)
            val label = IssueLabel(id = 9L, name = "bug", category = category, project = project)
            every { issueLabelRepository.findAllById(listOf(9L)) } returns listOf(label)
            every { postingRepository.save(any()) } answers { firstArg() }

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/myproject/postlabel/6")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""["9"]""")
                    .principal(auth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.labels").value(1))
        }
    }

    describe("PATCH /-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number}/content") {
        it("본문을 수정한다") {
            val posting = Posting(id = 60L, title = "글", body = "원문", project = project, number = 6L, authorId = 10L)
            every { postingRepository.findByProjectAndNumber(project, 6L) } returns posting
            every { postingRepository.save(any()) } answers { firstArg() }

            mockMvc.perform(
                patch("/-_-api/v1/owners/alice/projects/myproject/posts/6/content")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"수정됨","original":"원문"}""")
                    .principal(auth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.body").value("수정됨"))
        }

        it("original(원문)이 현재 값과 다르면 409를 반환한다") {
            val posting = Posting(id = 60L, title = "글", body = "원문", project = project, number = 6L, authorId = 10L)
            every { postingRepository.findByProjectAndNumber(project, 6L) } returns posting

            mockMvc.perform(
                patch("/-_-api/v1/owners/alice/projects/myproject/posts/6/content")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"수정됨","original":"다른내용"}""")
                    .principal(auth)
            ).andExpect(status().isConflict)
        }
    }

    describe("POST /-_-api/v1/owners/{owner}/projects/{projectName}/posts (newPostings)") {
        it("게시글 배열을 벌크 생성한다") {
            val created = Posting(id = 70L, title = "새글", project = project, number = 9L)
            every { postingService.createPosting(1L, any(), 10L) } returns created

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/myproject/posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"posts":[{"title":"새글","body":"내용"}]}""")
                    .principal(auth)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$[0].status").value(201))
        }

        it("게시글 생성 권한이 없으면 403을 반환한다") {
            val nonMember = User(id = 99L, loginId = "outsider", name = "외부인")
            every { userRepository.findByLoginId("outsider") } returns Optional.of(nonMember)
            val outsiderAuth = UsernamePasswordAuthenticationToken("outsider", null, emptyList())

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/myproject/posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"posts":[{"title":"새글","body":"내용"}]}""")
                    .principal(outsiderAuth)
            ).andExpect(status().isForbidden)
        }

        it("number 필드를 postingService.createPosting()에 그대로 전달한다") {
            val created = Posting(id = 71L, title = "임포트글", project = project, number = 42L)
            every { postingService.createPosting(1L, any(), 10L, 42L) } returns created

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/myproject/posts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"posts":[{"title":"임포트글","body":"내용","number":42}]}""")
                    .principal(auth)
            ).andExpect(status().isCreated)

            verify { postingService.createPosting(1L, any(), 10L, 42L) }
        }
    }
})
