package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CodeReviewService
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.ui.Model
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
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

class ReviewApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val codeReviewService = mockk<CodeReviewService>()
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

    val reviewApiController = ReviewApiController(
        projectRepository,
        pullRequestRepository,
        userRepository,
        projectUserRepository,
        codeReviewService,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(reviewApiController).build()
    val auth = UsernamePasswordAuthenticationToken("gildong", "pass")

    beforeTest {
        clearMocks(projectRepository, pullRequestRepository, userRepository, projectUserRepository, codeReviewService)
    }

    describe("ReviewApiController API 매핑 및 로직 검증") {
        val user = User(id = 1L, loginId = "gildong", name = "길동")
        val project = Project(id = 10L, name = "yona-project", owner = "gildong")
        val pullRequest = PullRequest(
            id = 100L,
            number = 5L,
            title = "PR 제목",
            toProject = project,
            fromProject = project,
            contributor = user
        )

        it("리뷰어 등록 API 호출 시 302 리다이렉트와 서비스 메소드가 정상 호출되어야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
            every { projectUserRepository.existsByProjectIdAndUserId(10L, 1L) } returns true
            every { pullRequestRepository.findById(100L) } returns Optional.of(pullRequest)
            every { codeReviewService.addReviewer(100L, 1L) } returns Unit

            mockMvc.perform(
                post("/api/gildong/yona-project/pullRequest/100/review")
                    .principal(auth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/gildong/yona-project/pullRequest/5"))

            verify { codeReviewService.addReviewer(100L, 1L) }
        }

        it("리뷰어 해제 API 호출 시 302 리다이렉트와 서비스 메소드가 정상 호출되어야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
            every { projectUserRepository.existsByProjectIdAndUserId(10L, 1L) } returns true
            every { pullRequestRepository.findById(100L) } returns Optional.of(pullRequest)
            every { codeReviewService.removeReviewer(100L, 1L) } returns Unit

            mockMvc.perform(
                post("/api/gildong/yona-project/pullRequest/100/unreview")
                    .principal(auth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/gildong/yona-project/pullRequest/5"))

            verify { codeReviewService.removeReviewer(100L, 1L) }
        }

        // yona AccessControl.isProjectResourceAllowed()의 PULL_REQUEST Operation.ACCEPT 분기 대응 (P1-78).
        it("PUBLIC 프로젝트여도 멤버가 아니면 리뷰어로 등록할 수 없어야 한다(인가 우회 방지)") {
            val publicProject = Project(id = 11L, name = "public-project", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val stranger = User(id = 2L, loginId = "stranger", name = "제3자")
            val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "pass")

            every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "public-project") } returns Optional.of(publicProject)
            every { projectUserRepository.existsByProjectIdAndUserId(11L, 2L) } returns false

            mockMvc.perform(
                post("/api/owner/public-project/pullRequest/100/review")
                    .principal(strangerAuth)
            )
                .andExpect(view().name("error/forbidden"))

            verify(exactly = 0) { codeReviewService.addReviewer(any(), any()) }
        }

        it("PUBLIC 프로젝트여도 멤버가 아니면 리뷰어를 해제할 수 없어야 한다(인가 우회 방지)") {
            val publicProject = Project(id = 11L, name = "public-project", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val stranger = User(id = 2L, loginId = "stranger", name = "제3자")
            val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "pass")

            every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "public-project") } returns Optional.of(publicProject)
            every { projectUserRepository.existsByProjectIdAndUserId(11L, 2L) } returns false

            mockMvc.perform(
                post("/api/owner/public-project/pullRequest/100/unreview")
                    .principal(strangerAuth)
            )
                .andExpect(view().name("error/forbidden"))

            verify(exactly = 0) { codeReviewService.removeReviewer(any(), any()) }
        }

        it("REVIEW_COMMENT 삭제 API 호출 시 status ok와 deleteReviewComment 서비스 메소드가 정상 호출되어야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { codeReviewService.deleteReviewComment(200L, user) } returns Unit

            mockMvc.perform(
                delete("/comments/REVIEW_COMMENT/200")
                    .principal(auth)
            )
                .andExpect(status().isOk)

            verify { codeReviewService.deleteReviewComment(200L, user) }
        }

        it("[Test-13-1-1] 타인의 REVIEW_COMMENT 삭제 API 호출 시 403 Forbidden을 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { codeReviewService.deleteReviewComment(200L, user) } throws IllegalArgumentException("Permission denied")

            mockMvc.perform(
                delete("/comments/REVIEW_COMMENT/200")
                    .principal(auth)
            )
                .andExpect(status().isForbidden)
        }

        it("COMMIT_COMMENT 삭제 API 호출 시 status ok와 deleteCommitComment 서비스 메소드가 정상 호출되어야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { codeReviewService.deleteCommitComment(200L, user) } returns Unit

            mockMvc.perform(
                delete("/comments/COMMIT_COMMENT/200")
                    .principal(auth)
            )
                .andExpect(status().isOk)

            verify { codeReviewService.deleteCommitComment(200L, user) }
        }

        it("[Test-13-1-2] 타인의 COMMIT_COMMENT 삭제 API 호출 시 403 Forbidden을 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { codeReviewService.deleteCommitComment(200L, user) } throws IllegalArgumentException("Permission denied")

            mockMvc.perform(
                delete("/comments/COMMIT_COMMENT/200")
                    .principal(auth)
            )
                .andExpect(status().isForbidden)
        }

        it("인증되지 않은 댓글 삭제 요청은 401을 반환해야 한다") {
            mockMvc.perform(delete("/comments/REVIEW_COMMENT/200"))
                .andExpect(status().isUnauthorized)

            verify(exactly = 0) { codeReviewService.deleteReviewComment(any(), any()) }
        }

        it("인증은 되었지만 DB에 없는 사용자의 댓글 삭제 요청은 401을 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.empty()

            mockMvc.perform(
                delete("/comments/REVIEW_COMMENT/200")
                    .principal(auth)
            )
                .andExpect(status().isUnauthorized)

            verify(exactly = 0) { codeReviewService.deleteReviewComment(any(), any()) }
        }

        it("지원하지 않는 type이면 어떤 서비스도 호출하지 않고 200을 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)

            mockMvc.perform(
                delete("/comments/UNKNOWN_TYPE/200")
                    .principal(auth)
            )
                .andExpect(status().isOk)

            verify(exactly = 0) { codeReviewService.deleteReviewComment(any(), any()) }
            verify(exactly = 0) { codeReviewService.deleteCommitComment(any(), any()) }
        }

        it("Permission denied가 아닌 다른 메시지의 예외는 그대로 전파되어야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { codeReviewService.deleteReviewComment(200L, user) } throws IllegalArgumentException("Other reason")

            shouldThrow<IllegalArgumentException> {
                reviewApiController.deleteComment("REVIEW_COMMENT", 200L, auth)
            }
        }

        it("인증되지 않은 review 요청은 IllegalStateException을 던져야 한다") {
            val model = mockk<Model>(relaxed = true)

            shouldThrow<IllegalStateException> {
                reviewApiController.review("gildong", "yona-project", 100L, null, model)
            }
        }

        it("인증은 되었지만 DB에 없는 사용자의 review 요청은 IllegalStateException을 던져야 한다") {
            val model = mockk<Model>(relaxed = true)
            every { userRepository.findByLoginId("gildong") } returns Optional.empty()

            shouldThrow<IllegalStateException> {
                reviewApiController.review("gildong", "yona-project", 100L, auth, model)
            }
        }

        it("존재하지 않는 프로젝트에 대한 review 요청은 error/404 뷰를 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "nosuch") } returns Optional.empty()

            mockMvc.perform(
                post("/api/gildong/nosuch/pullRequest/100/review")
                    .principal(auth)
            ).andExpect(view().name("error/404"))
        }

        it("존재하지 않는 PullRequest에 대한 review 요청은 error/notfound 뷰를 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
            every { projectUserRepository.existsByProjectIdAndUserId(10L, 1L) } returns true
            every { pullRequestRepository.findById(999L) } returns Optional.empty()

            mockMvc.perform(
                post("/api/gildong/yona-project/pullRequest/999/review")
                    .principal(auth)
            ).andExpect(view().name("error/notfound"))
        }

        // yona AccessControl.isAllowedIfGroupMember() 대응 — 프로젝트 직접 멤버가 아니어도 PUBLIC/PROTECTED
        // 프로젝트의 조직(그룹) 멤버라면 review 권한을 가져야 한다.
        it("프로젝트 멤버는 아니지만 PUBLIC 프로젝트의 조직 멤버라면 리뷰어로 등록할 수 있어야 한다") {
            val org = Organization(id = 500L, name = "org")
            val groupMemberUser = User(id = 3L, loginId = "groupmember", name = "그룹멤버")
            val groupMemberAuth = UsernamePasswordAuthenticationToken("groupmember", "pass")
            val orgRole = Role(id = RoleType.ORG_MEMBER.roleType)
            org.organizationUsers.add(OrganizationUser(id = 900L, user = groupMemberUser, organization = org, role = orgRole))
            val publicOrgProject = Project(id = 12L, name = "public-org-project", owner = "owner", projectScope = ProjectScope.PUBLIC, organization = org)
            val orgPullRequest = PullRequest(
                id = 101L, number = 6L, title = "PR", toProject = publicOrgProject, fromProject = publicOrgProject, contributor = groupMemberUser
            )

            every { userRepository.findByLoginId("groupmember") } returns Optional.of(groupMemberUser)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "public-org-project") } returns Optional.of(publicOrgProject)
            every { projectUserRepository.existsByProjectIdAndUserId(12L, 3L) } returns false
            every { pullRequestRepository.findById(101L) } returns Optional.of(orgPullRequest)
            every { codeReviewService.addReviewer(101L, 3L) } returns Unit

            mockMvc.perform(
                post("/api/owner/public-org-project/pullRequest/101/review")
                    .principal(groupMemberAuth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/owner/public-org-project/pullRequest/6"))

            verify { codeReviewService.addReviewer(101L, 3L) }
        }

        it("인증되지 않은 unreview 요청은 IllegalStateException을 던져야 한다") {
            val model = mockk<Model>(relaxed = true)

            shouldThrow<IllegalStateException> {
                reviewApiController.unreview("gildong", "yona-project", 100L, null, model)
            }
        }

        it("인증은 되었지만 DB에 없는 사용자의 unreview 요청은 IllegalStateException을 던져야 한다") {
            val model = mockk<Model>(relaxed = true)
            every { userRepository.findByLoginId("gildong") } returns Optional.empty()

            shouldThrow<IllegalStateException> {
                reviewApiController.unreview("gildong", "yona-project", 100L, auth, model)
            }
        }

        it("존재하지 않는 프로젝트에 대한 unreview 요청은 error/404 뷰를 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "nosuch") } returns Optional.empty()

            mockMvc.perform(
                post("/api/gildong/nosuch/pullRequest/100/unreview")
                    .principal(auth)
            ).andExpect(view().name("error/404"))
        }

        it("존재하지 않는 PullRequest에 대한 unreview 요청은 error/notfound 뷰를 반환해야 한다") {
            every { userRepository.findByLoginId("gildong") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("gildong", "yona-project") } returns Optional.of(project)
            every { projectUserRepository.existsByProjectIdAndUserId(10L, 1L) } returns true
            every { pullRequestRepository.findById(999L) } returns Optional.empty()

            mockMvc.perform(
                post("/api/gildong/yona-project/pullRequest/999/unreview")
                    .principal(auth)
            ).andExpect(view().name("error/notfound"))
        }

        it("프로젝트 멤버는 아니지만 PUBLIC 프로젝트의 조직 멤버라면 리뷰어를 해제할 수 있어야 한다") {
            val org = Organization(id = 501L, name = "org2")
            val groupMemberUser = User(id = 4L, loginId = "groupmember2", name = "그룹멤버2")
            val groupMemberAuth = UsernamePasswordAuthenticationToken("groupmember2", "pass")
            val orgRole = Role(id = RoleType.ORG_ADMIN.roleType)
            org.organizationUsers.add(OrganizationUser(id = 901L, user = groupMemberUser, organization = org, role = orgRole))
            val publicOrgProject = Project(id = 13L, name = "public-org-project2", owner = "owner", projectScope = ProjectScope.PUBLIC, organization = org)
            val orgPullRequest = PullRequest(
                id = 102L, number = 7L, title = "PR", toProject = publicOrgProject, fromProject = publicOrgProject, contributor = groupMemberUser
            )

            every { userRepository.findByLoginId("groupmember2") } returns Optional.of(groupMemberUser)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "public-org-project2") } returns Optional.of(publicOrgProject)
            every { projectUserRepository.existsByProjectIdAndUserId(13L, 4L) } returns false
            every { pullRequestRepository.findById(102L) } returns Optional.of(orgPullRequest)
            every { codeReviewService.removeReviewer(102L, 4L) } returns Unit

            mockMvc.perform(
                post("/api/owner/public-org-project2/pullRequest/102/unreview")
                    .principal(groupMemberAuth)
            )
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/owner/public-org-project2/pullRequest/7"))

            verify { codeReviewService.removeReviewer(102L, 4L) }
        }
    }
})
