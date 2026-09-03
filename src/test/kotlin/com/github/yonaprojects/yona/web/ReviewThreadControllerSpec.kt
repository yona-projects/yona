package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.support.ReviewSearchCondition
import com.github.yonaprojects.yona.domain.support.ReviewThreadService
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.pullrequest.NonRangedCodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.user.UserIdent

class ReviewThreadControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val reviewThreadService = mockk<ReviewThreadService>()
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

    val reviewThreadController = ReviewThreadController(
        projectRepository,
        reviewThreadService,
        userRepository,
        projectUserRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(reviewThreadController).build()

    beforeTest {
        clearMocks(projectRepository, reviewThreadService, userRepository, projectUserRepository)
    }

    describe("ReviewThreadController TDD 검증") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner", projectScope = ProjectScope.PUBLIC)
        val memberOnlyProject = Project(id = 2L, name = "MemberOnlyProject", owner = "owner", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "")

        it("리뷰 스레드 목록 화면 요청 시 200 OK와 reviewthread/list 뷰를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
            every { reviewThreadService.getReviewThreads(eq(project), any(), any()) } returns PageImpl(emptyList())
            every { reviewThreadService.countReviewThreads(eq(project), any()) } returns 0L

            mockMvc.perform(
                get("/owner/TestProject/reviews")
            )
                .andExpect(status().isOk)
                .andExpect(view().name("reviewthread/list"))
                .andExpect(model().attributeExists("project"))
        }

        it("엑셀 다운로드 요청 시 200 OK와 엑셀 바이너리를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
            every { reviewThreadService.getReviewThreads(eq(project), any()) } returns emptyList()

            mockMvc.perform(
                get("/owner/TestProject/reviews")
                    .param("format", "xls")
            )
                .andExpect(status().isOk)
                .andExpect(header().exists("Content-Disposition"))
        }

        it("[Test-16-3-1] isCodeAccessibleMemberOnly가 true이고 비회원(비인증)인 경우 error/forbidden 뷰를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MemberOnlyProject") } returns Optional.of(memberOnlyProject)

            mockMvc.perform(
                get("/owner/MemberOnlyProject/reviews")
            )
                .andExpect(status().isOk)
                .andExpect(view().name("error/forbidden"))
        }

        it("[Test-16-3-2] isCodeAccessibleMemberOnly가 true이고 가입된 멤버인 경우 정상적으로 200 OK를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MemberOnlyProject") } returns Optional.of(memberOnlyProject)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(2L, 10L) } returns true
            every { reviewThreadService.getReviewThreads(eq(memberOnlyProject), any(), any()) } returns PageImpl(emptyList())
            every { reviewThreadService.countReviewThreads(eq(memberOnlyProject), any()) } returns 0L

            mockMvc.perform(
                get("/owner/MemberOnlyProject/reviews").principal(userAuth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("reviewthread/list"))
        }

        // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
        it("직접 멤버가 아니어도 PROTECTED 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
            val groupOrg = Organization(id = 1L, name = "org")
            groupOrg.organizationUsers.add(
                OrganizationUser(
                    id = 1L, user = user, organization = groupOrg,
                    role = Role(id = RoleType.ORG_MEMBER.roleType)
                )
            )
            val groupProject = Project(id = 13L, name = "GroupProject", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GroupProject") } returns Optional.of(groupProject)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(13L, 10L) } returns false
            every { reviewThreadService.getReviewThreads(eq(groupProject), any(), any()) } returns PageImpl(emptyList())
            every { reviewThreadService.countReviewThreads(eq(groupProject), any()) } returns 0L

            mockMvc.perform(
                get("/owner/GroupProject/reviews").principal(userAuth)
            )
                .andExpect(status().isOk)
                .andExpect(view().name("reviewthread/list"))
        }

        it("존재하지 않는 프로젝트면 error/404 뷰를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

            mockMvc.perform(get("/owner/NoSuch/reviews"))
                .andExpect(status().isOk)
                .andExpect(view().name("error/404"))
        }

        it("PRIVATE 프로젝트는 비인증 사용자에게 error/forbidden 뷰를 반환해야 한다") {
            val privateProject = Project(id = 20L, name = "PrivateProject", owner = "owner", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PrivateProject") } returns Optional.of(privateProject)

            mockMvc.perform(get("/owner/PrivateProject/reviews"))
                .andExpect(status().isOk)
                .andExpect(view().name("error/forbidden"))
        }

        it("PRIVATE 프로젝트는 멤버인 인증 사용자에게 정상적으로 목록을 반환해야 한다") {
            val privateProject = Project(id = 21L, name = "PrivateProject2", owner = "owner", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PrivateProject2") } returns Optional.of(privateProject)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(21L, 10L) } returns true
            every { reviewThreadService.getReviewThreads(eq(privateProject), any(), any()) } returns PageImpl(emptyList())
            every { reviewThreadService.countReviewThreads(eq(privateProject), any()) } returns 0L

            mockMvc.perform(get("/owner/PrivateProject2/reviews").principal(userAuth))
                .andExpect(status().isOk)
                .andExpect(view().name("reviewthread/list"))
        }

        it("PUBLIC이지만 memberOnly고 인증 사용자가 멤버도 조직멤버도 아니면 error/forbidden 뷰를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MemberOnlyProject") } returns Optional.of(memberOnlyProject)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(2L, 10L) } returns false

            mockMvc.perform(get("/owner/MemberOnlyProject/reviews").principal(userAuth))
                .andExpect(status().isOk)
                .andExpect(view().name("error/forbidden"))
        }

        it("엑셀 다운로드 시 리뷰 스레드/댓글 데이터를 실제로 셀에 채워야 한다(작성자 유무, 첫댓글 여부, 커밋ID 길이 분기 포함)") {
            val threadAuthorA = UserIdent(id = 100L, loginId = "threadauthora", name = "ThreadAuthorA")
            val commenterA2 = UserIdent(id = 101L, loginId = "commentera2", name = "CommenterA2")
            val commentA1 = ReviewComment(id = 1L, contents = "first msg", author = UserIdent(id = 102L, name = "CommenterA1"))
            val commentA2 = ReviewComment(id = 2L, contents = "reply msg", author = commenterA2)
            val threadA = NonRangedCodeCommentThread(
                id = 1L, author = threadAuthorA, commitId = "abcdefgh12345",
                reviewComments = mutableListOf(commentA1, commentA2)
            )

            val commentB1 = ReviewComment(id = 3L, contents = "only", author = null)
            val threadB = NonRangedCodeCommentThread(
                id = 2L, author = null, commitId = "ab",
                reviewComments = mutableListOf(commentB1)
            )

            // commitId가 아예 null인 경우(line136 ?: "" 분기) + 저자 UserIdent는 있으나 name 필드 자체가 null인 경우
            val commentC1 = ReviewComment(id = 4L, contents = "c-only", author = UserIdent(id = 103L, name = null))
            val threadC = NonRangedCodeCommentThread(
                id = 3L, author = UserIdent(id = 104L, name = null), commitId = null,
                reviewComments = mutableListOf(commentC1)
            )

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
            every { reviewThreadService.getReviewThreads(eq(project), any()) } returns listOf(threadA, threadB, threadC)

            mockMvc.perform(get("/owner/TestProject/reviews").param("format", "xls"))
                .andExpect(status().isOk)
                .andExpect(header().exists("Content-Disposition"))
        }

        it("PRIVATE 프로젝트에서 멤버도 아니고 조직 그룹멤버도 아니면 error/forbidden 뷰를 반환해야 한다") {
            val privateProject = Project(id = 22L, name = "PrivateProject3", owner = "owner", projectScope = ProjectScope.PRIVATE)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PrivateProject3") } returns Optional.of(privateProject)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(22L, 10L) } returns false

            mockMvc.perform(get("/owner/PrivateProject3/reviews").principal(userAuth))
                .andExpect(status().isOk)
                .andExpect(view().name("error/forbidden"))
        }

        it("PUBLIC이지만 memberOnly인 프로젝트에서 직접 멤버가 아니어도 조직 그룹멤버면 정상적으로 목록을 반환해야 한다") {
            val groupOrg2 = Organization(id = 2L, name = "org2")
            groupOrg2.organizationUsers.add(
                OrganizationUser(id = 2L, user = user, organization = groupOrg2, role = Role(id = RoleType.ORG_MEMBER.roleType))
            )
            val memberOnlyGroupProject = Project(
                id = 23L, name = "MemberOnlyGroupProject", owner = "owner",
                projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, organization = groupOrg2
            )
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MemberOnlyGroupProject") } returns Optional.of(memberOnlyGroupProject)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { projectUserRepository.existsByProjectIdAndUserId(23L, 10L) } returns false
            every { reviewThreadService.getReviewThreads(eq(memberOnlyGroupProject), any(), any()) } returns PageImpl(emptyList())
            every { reviewThreadService.countReviewThreads(eq(memberOnlyGroupProject), any()) } returns 0L

            mockMvc.perform(get("/owner/MemberOnlyGroupProject/reviews").principal(userAuth))
                .andExpect(status().isOk)
                .andExpect(view().name("reviewthread/list"))
        }
    }
})
