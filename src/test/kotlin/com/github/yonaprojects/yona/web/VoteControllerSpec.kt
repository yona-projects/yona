package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.project.ProjectScope

class VoteControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val issueService = mockk<IssueService>()
    val issueRepository = mockk<IssueRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
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

    val voteController = VoteController(
        projectRepository,
        userRepository,
        projectUserRepository,
        issueService,
        issueRepository,
        issueCommentRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(voteController).build()

    beforeTest {
        clearMocks(projectRepository, userRepository, projectUserRepository, issueService, issueRepository, issueCommentRepository)
    }

    describe("VoteController 웹 API 및 리다이렉션 테스트") {
        val project = Project(id = 1L, name = "test-project", owner = "tester")
        val user = User(id = 10L, loginId = "tester", name = "테스터", email = "tester@yona.io")
        val issue = Issue(id = 50L, number = 1L, project = project, title = "이슈 제목")
        val comment = IssueComment(id = 100L, issue = issue, contents = "댓글 내용")

        val userAuth = UsernamePasswordAuthenticationToken("tester", "password")

        beforeTest {
            user.projectUsers.clear()
        }

        it("이슈 투표 요청 시 302 리다이렉트와 함께 해당 이슈 상세 뷰로 이동해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
            user.projectUsers.add(ProjectUser(id = 900L, user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
            every { issueService.voteIssue(50L, user) } returns Unit

            mockMvc.perform(
                post("/tester/test-project/issue/1/vote")
                    .principal(userAuth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/tester/test-project/issue/1"))

            verify(exactly = 1) { issueService.voteIssue(50L, user) }
        }

        it("이슈 투표 취소 요청 시 302 리다이렉트와 함께 해당 이슈 상세 뷰로 이동해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
            user.projectUsers.add(ProjectUser(id = 900L, user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
            every { issueService.unvoteIssue(50L, user) } returns Unit

            mockMvc.perform(
                post("/tester/test-project/issue/1/unvote")
                    .principal(userAuth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/tester/test-project/issue/1"))

            verify(exactly = 1) { issueService.unvoteIssue(50L, user) }
        }

        it("이슈 댓글 투표 요청 시 302 리다이렉트와 함께 해당 이슈 상세 뷰로 이동해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
            user.projectUsers.add(ProjectUser(id = 900L, user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            every { issueCommentRepository.findById(100L) } returns Optional.of(comment)
            every { issueService.voteComment(100L, user) } returns Unit

            mockMvc.perform(
                post("/tester/test-project/issue/1/comment/100/vote")
                    .principal(userAuth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/tester/test-project/issue/1"))

            verify(exactly = 1) { issueService.voteComment(100L, user) }
        }

        it("이슈 댓글 투표 취소 요청 시 302 리다이렉트와 함께 해당 이슈 상세 뷰로 이동해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
            user.projectUsers.add(ProjectUser(id = 900L, user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            every { issueCommentRepository.findById(100L) } returns Optional.of(comment)
            every { issueService.unvoteComment(100L, user) } returns Unit

            mockMvc.perform(
                post("/tester/test-project/issue/1/comment/100/unvote")
                    .principal(userAuth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/tester/test-project/issue/1"))

            verify(exactly = 1) { issueService.unvoteComment(100L, user) }
        }

        it("익명 사용자가 이슈 추천 시 401 Unauthorized 상태코드를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)
            
            mockMvc.perform(
                post("/tester/test-project/issue/1/vote")
            )
                .andExpect(status().isUnauthorized)
        }

        it("읽기 권한이 없는 사용자가 이슈 추천 시 403 Forbidden 상태코드를 반환해야 한다") {
            val privateProject = Project(id = 1L, name = "test-project", owner = "tester", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(privateProject)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

            mockMvc.perform(
                post("/tester/test-project/issue/1/vote")
                    .principal(userAuth)
            )
                .andExpect(status().isForbidden)
        }

        it("이슈 복수형 라우트(/issues)로 투표 요청 시에도 정상적으로 302 리다이렉트가 발생해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
            user.projectUsers.add(ProjectUser(id = 900L, user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
            every { issueService.voteIssue(50L, user) } returns Unit

            mockMvc.perform(
                post("/tester/test-project/issues/1/vote")
                    .principal(userAuth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/tester/test-project/issue/1"))
        }

        it("존재하지 않는 프로젝트에 이슈 투표 요청 시 404 Not Found를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.empty()

            mockMvc.perform(
                post("/tester/test-project/issue/1/vote")
                    .principal(userAuth)
            )
                .andExpect(status().isNotFound)
        }

        it("존재하지 않는 이슈에 투표 요청 시 404 Not Found를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
            user.projectUsers.add(ProjectUser(id = 900L, user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            every { issueRepository.findByProjectAndNumber(project, 1L) } returns null

            mockMvc.perform(
                post("/tester/test-project/issue/1/vote")
                    .principal(userAuth)
            )
                .andExpect(status().isNotFound)
        }

        it("존재하지 않는 프로젝트에 이슈 투표 취소 요청 시 404 Not Found를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.empty()

            mockMvc.perform(
                post("/tester/test-project/issue/1/unvote")
                    .principal(userAuth)
            )
                .andExpect(status().isNotFound)
        }

        it("익명 사용자가 이슈 투표 취소 요청 시 401 Unauthorized 상태코드를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)

            mockMvc.perform(
                post("/tester/test-project/issue/1/unvote")
            )
                .andExpect(status().isUnauthorized)
        }

        it("읽기 권한이 없는 사용자가 이슈 투표 취소 요청 시 403 Forbidden 상태코드를 반환해야 한다") {
            val privateProject = Project(id = 1L, name = "test-project", owner = "tester", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(privateProject)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

            mockMvc.perform(
                post("/tester/test-project/issue/1/unvote")
                    .principal(userAuth)
            )
                .andExpect(status().isForbidden)
        }

        it("존재하지 않는 이슈에 투표 취소 요청 시 404 Not Found를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
            user.projectUsers.add(ProjectUser(id = 900L, user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            every { issueRepository.findByProjectAndNumber(project, 1L) } returns null

            mockMvc.perform(
                post("/tester/test-project/issue/1/unvote")
                    .principal(userAuth)
            )
                .andExpect(status().isNotFound)
        }

        it("존재하지 않는 프로젝트에 이슈 댓글 투표 요청 시 404 Not Found를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.empty()

            mockMvc.perform(
                post("/tester/test-project/issue/1/comment/100/vote")
                    .principal(userAuth)
            )
                .andExpect(status().isNotFound)
        }

        it("익명 사용자가 이슈 댓글 투표 요청 시 401 Unauthorized 상태코드를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)

            mockMvc.perform(
                post("/tester/test-project/issue/1/comment/100/vote")
            )
                .andExpect(status().isUnauthorized)
        }

        it("읽기 권한이 없는 사용자가 이슈 댓글 투표 요청 시 403 Forbidden 상태코드를 반환해야 한다") {
            val privateProject = Project(id = 1L, name = "test-project", owner = "tester", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(privateProject)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

            mockMvc.perform(
                post("/tester/test-project/issue/1/comment/100/vote")
                    .principal(userAuth)
            )
                .andExpect(status().isForbidden)
        }

        it("존재하지 않는 댓글에 투표 요청 시 404 Not Found를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
            user.projectUsers.add(ProjectUser(id = 900L, user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            every { issueCommentRepository.findById(100L) } returns Optional.empty()

            mockMvc.perform(
                post("/tester/test-project/issue/1/comment/100/vote")
                    .principal(userAuth)
            )
                .andExpect(status().isNotFound)
        }

        it("존재하지 않는 프로젝트에 이슈 댓글 투표 취소 요청 시 404 Not Found를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.empty()

            mockMvc.perform(
                post("/tester/test-project/issue/1/comment/100/unvote")
                    .principal(userAuth)
            )
                .andExpect(status().isNotFound)
        }

        it("익명 사용자가 이슈 댓글 투표 취소 요청 시 401 Unauthorized 상태코드를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)

            mockMvc.perform(
                post("/tester/test-project/issue/1/comment/100/unvote")
            )
                .andExpect(status().isUnauthorized)
        }

        it("읽기 권한이 없는 사용자가 이슈 댓글 투표 취소 요청 시 403 Forbidden 상태코드를 반환해야 한다") {
            val privateProject = Project(id = 1L, name = "test-project", owner = "tester", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(privateProject)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

            mockMvc.perform(
                post("/tester/test-project/issue/1/comment/100/unvote")
                    .principal(userAuth)
            )
                .andExpect(status().isForbidden)
        }

        it("존재하지 않는 댓글에 투표 취소 요청 시 404 Not Found를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("tester", "test-project") } returns Optional.of(project)
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
            user.projectUsers.add(ProjectUser(id = 900L, user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            every { issueCommentRepository.findById(100L) } returns Optional.empty()

            mockMvc.perform(
                post("/tester/test-project/issue/1/comment/100/unvote")
                    .principal(userAuth)
            )
                .andExpect(status().isNotFound)
        }
    }
})
