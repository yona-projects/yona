package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.comment.CommentService
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
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
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import io.mockk.clearMocks

class CommentControllerSpec : DescribeSpec({
    val commentService = mockk<CommentService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
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

    val commentController = CommentController(
        commentService,
        projectRepository,
        projectUserRepository,
        userRepository,
        issueRepository,
        postingRepository,
        issueCommentRepository,
        postingCommentRepository,
        accessControl
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(commentController).build()

    beforeTest {
        clearMocks(
            commentService, projectRepository, projectUserRepository, userRepository,
            issueRepository, postingRepository, issueCommentRepository, postingCommentRepository
        )
    }

    describe("CommentController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val otherUser = User(id = 20L, loginId = "otheruser", name = "다른유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val otherAuth = UsernamePasswordAuthenticationToken("otheruser", "password")

        val issue = Issue(id = 50L, number = 5L, title = "이슈", body = "내용", project = project, authorId = user.id)
        val posting = Posting(id = 60L, number = 6L, title = "포스트", body = "내용", project = project)

        val issueComment = IssueComment(id = 100L, contents = "이슈댓글", issue = issue, authorId = user.id, authorLoginId = user.loginId, authorName = user.name)
        val postingComment = PostingComment(id = 200L, contents = "게시판댓글", posting = posting, authorId = user.id, authorLoginId = user.loginId, authorName = user.name)

        user.projectUsers.add(ProjectUser(id = 900L, user = user, project = project, role = Role(id = RoleType.MEMBER.roleType)))

        // isManager 판단 람다(projectUserRepository.findByProjectIdAndUserId(...).map { it.role.id == MANAGER })가
        // 실제로 실행되는 분기(0/4 커버리지)를 위한 추가 사용자 — 프로젝트 멤버라서 checkReadPermission은
        // 통과하지만, 댓글 작성자는 아니라서 isManager 값에 따라 결과가 갈린다.
        val managerUser = User(id = 40L, loginId = "manageruser", name = "매니저유저")
        val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")
        managerUser.projectUsers.add(ProjectUser(id = 901L, user = managerUser, project = project, role = Role(id = RoleType.MANAGER.roleType)))

        val memberUser = User(id = 41L, loginId = "memberuser", name = "일반멤버")
        val memberAuth = UsernamePasswordAuthenticationToken("memberuser", "password")
        memberUser.projectUsers.add(ProjectUser(id = 902L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))

        describe("POST /api/projects/{projectId}/issues/{number}/comments (이슈 댓글 작성)") {
            it("권한이 있는 멤버가 호출 시 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { commentService.createIssueComment(50L, "이슈댓글", user) } returns issueComment

                mockMvc.perform(
                    post("/api/projects/1/issues/5/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"이슈댓글\"}")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.contents").value("이슈댓글"))
            }

            // yona models/Comment.java:45 parentCommentId 대응 (P1-112).
            it("parentCommentId가 전달되면 commentService에 그대로 전달되어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { commentService.createIssueComment(50L, "답글", user, 100L) } returns issueComment

                mockMvc.perform(
                    post("/api/projects/1/issues/5/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"답글\", \"parentCommentId\": 100}")
                )
                    .andExpect(status().isCreated)

                verify(exactly = 1) { commentService.createIssueComment(50L, "답글", user, 100L) }
            }

            // P2-34: AccessControl.isResourceCreatable() 이식 후, 프로젝트 READ 권한이 아니라
            // 이슈 작성자/담당자/공유대상 우회 + 프로젝트 생성권한으로 판단한다 — 이 이슈의 작성자도
            // 담당자도 공유대상도 아니고 프로젝트 멤버도 아닌 사용자는 403.
            it("이슈 작성자/담당자/공유대상도 아니고 프로젝트 멤버도 아닌 사용자가 호출 시 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue

                mockMvc.perform(
                    post("/api/projects/1/issues/5/comments")
                        .principal(otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"이슈댓글\"}")
                )
                    .andExpect(status().isForbidden)
            }

            // P2-34: yona AccessControl.java isAllowedIfAuthor() 우회 대응 — 프로젝트 비멤버라도
            // 이슈 작성자라면 댓글을 달 수 있다.
            it("프로젝트 비멤버라도 이슈 작성자라면 201 Created를 반환해야 한다") {
                val nonMemberAuthor = User(id = 30L, loginId = "issueauthor", name = "이슈작성자")
                val authoredIssue = Issue(id = 51L, number = 7L, title = "비멤버작성이슈", body = "내용", project = project, authorId = nonMemberAuthor.id)
                val authorAuth = UsernamePasswordAuthenticationToken("issueauthor", "password")

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("issueauthor") } returns Optional.of(nonMemberAuthor)
                every { issueRepository.findByProjectAndNumber(project, 7L) } returns authoredIssue
                every { commentService.createIssueComment(51L, "이슈댓글", nonMemberAuthor) } returns issueComment

                mockMvc.perform(
                    post("/api/projects/1/issues/7/comments")
                        .principal(authorAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"이슈댓글\"}")
                )
                    .andExpect(status().isCreated)
            }

            it("존재하지 않는 프로젝트로 요청하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    post("/api/projects/999/issues/5/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"이슈댓글\"}")
                )
                    .andExpect(status().isNotFound)
            }

            // getLoginUser()의 authentication == null 분기 커버 (비로그인 요청).
            it("비로그인 사용자가 요청하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    post("/api/projects/1/issues/5/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"이슈댓글\"}")
                )
                    .andExpect(status().isUnauthorized)
            }

            // CommentRequest.contents의 기본값("") 분기 커버 — 요청 JSON에 contents 필드 자체가
            // 없으면 Kotlin data class 기본값이 적용된다.
            it("contents 필드가 없으면 빈 문자열 기본값으로 댓글을 생성해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { commentService.createIssueComment(50L, "", user) } returns issueComment

                mockMvc.perform(
                    post("/api/projects/1/issues/5/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                )
                    .andExpect(status().isCreated)

                verify(exactly = 1) { commentService.createIssueComment(50L, "", user) }
            }

            it("존재하지 않는 이슈 번호로 요청하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(
                    post("/api/projects/1/issues/999/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"이슈댓글\"}")
                )
                    .andExpect(status().isNotFound)
            }
        }

        describe("PUT /api/projects/{projectId}/issues/{number}/comments/{commentId} (이슈 댓글 수정)") {
            it("작성자 본인이 수정 시 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)
                
                val updatedComment = IssueComment(id = 100L, contents = "수정된이슈댓글", issue = issue, authorId = user.id, authorLoginId = user.loginId, authorName = user.name)
                every { commentService.updateIssueComment(100L, "수정된이슈댓글", user) } returns updatedComment

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/100")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정된이슈댓글\"}")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.contents").value("수정된이슈댓글"))
            }

            it("original이 현재 댓글 내용과 일치하면 정상적으로 수정해야 한다 (P1-102)") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)

                val updatedComment = IssueComment(id = 100L, contents = "수정된이슈댓글", issue = issue, authorId = user.id, authorLoginId = user.loginId, authorName = user.name)
                every { commentService.updateIssueComment(100L, "수정된이슈댓글", user) } returns updatedComment

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/100")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정된이슈댓글\", \"original\": \"이슈댓글\"}")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.contents").value("수정된이슈댓글"))
            }

            it("original이 현재 댓글 내용과 다르면 409 Conflict와 storedContent를 반환하고 저장하지 않아야 한다 (P1-102)") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/100")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정된이슈댓글\", \"original\": \"다른사람이 이미 바꾼 내용\"}")
                )
                    .andExpect(status().isConflict)
                    .andExpect(jsonPath("$.message").value("Already modified by someone."))
                    .andExpect(jsonPath("$.storedContent").value("이슈댓글"))

                verify(exactly = 0) { commentService.updateIssueComment(any(), any(), any()) }
            }

            it("타인이 수정 시 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 20L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.empty()
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/100")
                        .principal(otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정된이슈댓글\"}")
                )
                    .andExpect(status().isForbidden)
            }

            it("존재하지 않는 프로젝트로 요청하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/999/issues/5/comments/100")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isNotFound)
            }

            it("가입되지 않은 사용자가 요청하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/100")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isUnauthorized)
            }

            it("프로젝트 읽기 권한이 없는 사용자가 요청하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/100")
                        .principal(otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isForbidden)
            }

            it("존재하지 않는 댓글이면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueCommentRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/999")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isNotFound)
            }

            // isManager 람다가 false를 반환하는 분기(프로젝트 멤버지만 매니저는 아님) 커버.
            it("작성자도 매니저도 아닌 프로젝트 멤버가 수정 시 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("memberuser") } returns Optional.of(memberUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 41L) } returns
                    Optional.of(ProjectUser(id = 903L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/100")
                        .principal(memberAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { commentService.updateIssueComment(any(), any(), any()) }
            }

            // Role.id는 @Id(PK)라 실제 DB에서 조회되면 항상 non-null이지만, isManager 람다
            // (it.role.id == RoleType.MANAGER.roleType)의 role.id가 null인 방어분기도 mockk로
            // 직접 구성 가능해 함께 커버한다.
            it("역할 id가 null인 프로젝트 멤버가 수정 시도하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("memberuser") } returns Optional.of(memberUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 41L) } returns
                    Optional.of(ProjectUser(id = 904L, user = memberUser, project = project, role = Role(id = null)))
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/100")
                        .principal(memberAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { commentService.updateIssueComment(any(), any(), any()) }
            }

            // isManager 람다가 true를 반환하는 분기 커버 — 매니저는 작성자가 아니어도 수정할 수 있다.
            it("매니저는 타인이 작성한 댓글도 수정할 수 있어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 40L) } returns
                    Optional.of(ProjectUser(id = 901L, user = managerUser, project = project, role = Role(id = RoleType.MANAGER.roleType)))
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)

                val updatedComment = IssueComment(id = 100L, contents = "매니저수정", issue = issue, authorId = user.id, authorLoginId = user.loginId, authorName = user.name)
                every { commentService.updateIssueComment(100L, "매니저수정", managerUser) } returns updatedComment

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/100")
                        .principal(managerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"매니저수정\"}")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.contents").value("매니저수정"))
            }

            it("비로그인 사용자가 요청하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    put("/api/projects/1/issues/5/comments/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("DELETE /api/projects/{projectId}/issues/{number}/comments/{commentId} (이슈 댓글 삭제)") {
            it("작성자가 삭제 시 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)
                every { commentService.deleteIssueComment(100L, user) } returns Unit

                mockMvc.perform(
                    delete("/api/projects/1/issues/5/comments/100")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("존재하지 않는 프로젝트로 요청하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    delete("/api/projects/999/issues/5/comments/100")
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("가입되지 않은 사용자가 요청하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(
                    delete("/api/projects/1/issues/5/comments/100")
                        .principal(userAuth)
                )
                    .andExpect(status().isUnauthorized)
            }

            it("프로젝트 읽기 권한이 없는 사용자가 요청하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)

                mockMvc.perform(
                    delete("/api/projects/1/issues/5/comments/100")
                        .principal(otherAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("존재하지 않는 댓글이면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueCommentRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    delete("/api/projects/1/issues/5/comments/999")
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            // isManager 람다가 false를 반환하는 분기 커버.
            it("작성자도 매니저도 아닌 프로젝트 멤버가 삭제 시 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("memberuser") } returns Optional.of(memberUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 41L) } returns
                    Optional.of(ProjectUser(id = 903L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)

                mockMvc.perform(
                    delete("/api/projects/1/issues/5/comments/100")
                        .principal(memberAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { commentService.deleteIssueComment(any(), any()) }
            }

            // Role.id는 @Id(PK)라 실제 DB에서 조회되면 항상 non-null이지만, isManager 람다
            // (it.role.id == RoleType.MANAGER.roleType)의 role.id가 null인 방어분기도 mockk로
            // 직접 구성 가능해 함께 커버한다.
            it("역할 id가 null인 프로젝트 멤버가 삭제 시도하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("memberuser") } returns Optional.of(memberUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 41L) } returns
                    Optional.of(ProjectUser(id = 904L, user = memberUser, project = project, role = Role(id = null)))
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)

                mockMvc.perform(
                    delete("/api/projects/1/issues/5/comments/100")
                        .principal(memberAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { commentService.deleteIssueComment(any(), any()) }
            }

            // isManager 람다가 true를 반환하는 분기 커버 — 매니저는 작성자가 아니어도 삭제할 수 있다.
            it("매니저는 타인이 작성한 댓글도 삭제할 수 있어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 40L) } returns
                    Optional.of(ProjectUser(id = 901L, user = managerUser, project = project, role = Role(id = RoleType.MANAGER.roleType)))
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)
                every { commentService.deleteIssueComment(100L, managerUser) } returns Unit

                mockMvc.perform(
                    delete("/api/projects/1/issues/5/comments/100")
                        .principal(managerAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { commentService.deleteIssueComment(100L, managerUser) }
            }

            it("비로그인 사용자가 요청하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    delete("/api/projects/1/issues/5/comments/100")
                )
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("POST /api/projects/{projectId}/posts/{number}/comments (게시글 댓글 작성)") {
            it("권한이 있는 멤버가 호출 시 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingRepository.findByProjectAndNumber(project, 6L) } returns posting
                every { commentService.createPostingComment(60L, "게시판댓글", user) } returns postingComment

                mockMvc.perform(
                    post("/api/projects/1/posts/6/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"게시판댓글\"}")
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.contents").value("게시판댓글"))
            }

            // yona models/Comment.java:45 parentCommentId 대응 (P1-112).
            it("parentCommentId가 전달되면 commentService에 그대로 전달되어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingRepository.findByProjectAndNumber(project, 6L) } returns posting
                every { commentService.createPostingComment(60L, "답글", user, 200L) } returns postingComment

                mockMvc.perform(
                    post("/api/projects/1/posts/6/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"답글\", \"parentCommentId\": 200}")
                )
                    .andExpect(status().isCreated)

                verify(exactly = 1) { commentService.createPostingComment(60L, "답글", user, 200L) }
            }

            // P2-34: AccessControl.isResourceCreatable() 이식 후, 프로젝트 READ 권한이 아니라
            // 게시글 작성자 우회 + 프로젝트 생성권한으로 판단한다 — 작성자도 아니고 프로젝트
            // 멤버도 아닌 사용자는 403.
            it("게시글 작성자도 아니고 프로젝트 멤버도 아닌 사용자가 호출 시 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { postingRepository.findByProjectAndNumber(project, 6L) } returns posting

                mockMvc.perform(
                    post("/api/projects/1/posts/6/comments")
                        .principal(otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"게시판댓글\"}")
                )
                    .andExpect(status().isForbidden)
            }

            // P2-34: yona AccessControl.java isAllowedIfAuthor() 우회 대응 — 프로젝트 비멤버라도
            // 게시글 작성자라면 댓글을 달 수 있다.
            it("프로젝트 비멤버라도 게시글 작성자라면 201 Created를 반환해야 한다") {
                val nonMemberAuthor = User(id = 31L, loginId = "postauthor", name = "게시글작성자")
                val authoredPosting = Posting(id = 61L, number = 8L, title = "비멤버작성글", body = "내용", project = project, authorId = nonMemberAuthor.id)
                val authorAuth = UsernamePasswordAuthenticationToken("postauthor", "password")

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("postauthor") } returns Optional.of(nonMemberAuthor)
                every { postingRepository.findByProjectAndNumber(project, 8L) } returns authoredPosting
                every { commentService.createPostingComment(61L, "게시판댓글", nonMemberAuthor) } returns postingComment

                mockMvc.perform(
                    post("/api/projects/1/posts/8/comments")
                        .principal(authorAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"게시판댓글\"}")
                )
                    .andExpect(status().isCreated)
            }

            it("존재하지 않는 프로젝트로 요청하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    post("/api/projects/999/posts/6/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"게시판댓글\"}")
                )
                    .andExpect(status().isNotFound)
            }

            it("가입되지 않은 사용자가 요청하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(
                    post("/api/projects/1/posts/6/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"게시판댓글\"}")
                )
                    .andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 게시글 번호로 요청하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(
                    post("/api/projects/1/posts/999/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"게시판댓글\"}")
                )
                    .andExpect(status().isNotFound)
            }

            it("비로그인 사용자가 요청하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    post("/api/projects/1/posts/6/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"게시판댓글\"}")
                )
                    .andExpect(status().isUnauthorized)
            }

            it("contents 필드가 없으면 빈 문자열 기본값으로 댓글을 생성해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingRepository.findByProjectAndNumber(project, 6L) } returns posting
                every { commentService.createPostingComment(60L, "", user) } returns postingComment

                mockMvc.perform(
                    post("/api/projects/1/posts/6/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                )
                    .andExpect(status().isCreated)

                verify(exactly = 1) { commentService.createPostingComment(60L, "", user) }
            }
        }

        describe("PUT /api/projects/{projectId}/posts/{number}/comments/{commentId} (게시글 댓글 수정)") {
            it("작성자 본인이 수정 시 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)
                
                val updatedComment = PostingComment(id = 200L, contents = "수정된게시판댓글", posting = posting, authorId = user.id, authorLoginId = user.loginId, authorName = user.name)
                every { commentService.updatePostingComment(200L, "수정된게시판댓글", user) } returns updatedComment

                mockMvc.perform(
                    put("/api/projects/1/posts/6/comments/200")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정된게시판댓글\"}")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.contents").value("수정된게시판댓글"))
            }

            it("original이 현재 댓글 내용과 일치하면 정상적으로 수정해야 한다 (P1-107)") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)

                val updatedComment = PostingComment(id = 200L, contents = "수정된게시판댓글", posting = posting, authorId = user.id, authorLoginId = user.loginId, authorName = user.name)
                every { commentService.updatePostingComment(200L, "수정된게시판댓글", user) } returns updatedComment

                mockMvc.perform(
                    put("/api/projects/1/posts/6/comments/200")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정된게시판댓글\", \"original\": \"게시판댓글\"}")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.contents").value("수정된게시판댓글"))
            }

            it("original이 현재 댓글 내용과 다르면 409 Conflict와 storedContent를 반환하고 저장하지 않아야 한다 (P1-107)") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)

                mockMvc.perform(
                    put("/api/projects/1/posts/6/comments/200")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정된게시판댓글\", \"original\": \"다른사람이 이미 바꾼 내용\"}")
                )
                    .andExpect(status().isConflict)
                    .andExpect(jsonPath("$.message").value("Already modified by someone."))
                    .andExpect(jsonPath("$.storedContent").value("게시판댓글"))

                verify(exactly = 0) { commentService.updatePostingComment(any(), any(), any()) }
            }

            it("존재하지 않는 프로젝트로 요청하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/999/posts/6/comments/200")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isNotFound)
            }

            it("가입되지 않은 사용자가 요청하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/1/posts/6/comments/200")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isUnauthorized)
            }

            it("프로젝트 읽기 권한이 없는 사용자가 요청하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)

                mockMvc.perform(
                    put("/api/projects/1/posts/6/comments/200")
                        .principal(otherAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isForbidden)
            }

            it("존재하지 않는 댓글이면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingCommentRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/1/posts/6/comments/999")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isNotFound)
            }

            // isManager 람다가 false를 반환하는 분기 커버.
            it("작성자도 매니저도 아닌 프로젝트 멤버가 수정 시 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("memberuser") } returns Optional.of(memberUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 41L) } returns
                    Optional.of(ProjectUser(id = 903L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)

                mockMvc.perform(
                    put("/api/projects/1/posts/6/comments/200")
                        .principal(memberAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { commentService.updatePostingComment(any(), any(), any()) }
            }

            // Role.id는 @Id(PK)라 실제 DB에서 조회되면 항상 non-null이지만, isManager 람다
            // (it.role.id == RoleType.MANAGER.roleType)의 role.id가 null인 방어분기도 mockk로
            // 직접 구성 가능해 함께 커버한다.
            it("역할 id가 null인 프로젝트 멤버가 수정 시도하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("memberuser") } returns Optional.of(memberUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 41L) } returns
                    Optional.of(ProjectUser(id = 904L, user = memberUser, project = project, role = Role(id = null)))
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)

                mockMvc.perform(
                    put("/api/projects/1/posts/6/comments/200")
                        .principal(memberAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { commentService.updatePostingComment(any(), any(), any()) }
            }

            // isManager 람다가 true를 반환하는 분기 커버 — 매니저는 작성자가 아니어도 수정할 수 있다.
            it("매니저는 타인이 작성한 게시글 댓글도 수정할 수 있어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 40L) } returns
                    Optional.of(ProjectUser(id = 901L, user = managerUser, project = project, role = Role(id = RoleType.MANAGER.roleType)))
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)

                val updatedComment = PostingComment(id = 200L, contents = "매니저수정", posting = posting, authorId = user.id, authorLoginId = user.loginId, authorName = user.name)
                every { commentService.updatePostingComment(200L, "매니저수정", managerUser) } returns updatedComment

                mockMvc.perform(
                    put("/api/projects/1/posts/6/comments/200")
                        .principal(managerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"매니저수정\"}")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.contents").value("매니저수정"))
            }

            it("비로그인 사용자가 요청하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    put("/api/projects/1/posts/6/comments/200")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\": \"수정\"}")
                )
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("DELETE /api/projects/{projectId}/posts/{number}/comments/{commentId} (게시글 댓글 삭제)") {
            it("작성자가 삭제 시 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)
                every { commentService.deletePostingComment(200L, user) } returns Unit

                mockMvc.perform(
                    delete("/api/projects/1/posts/6/comments/200")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("존재하지 않는 프로젝트로 요청하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    delete("/api/projects/999/posts/6/comments/200")
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("가입되지 않은 사용자가 요청하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(
                    delete("/api/projects/1/posts/6/comments/200")
                        .principal(userAuth)
                )
                    .andExpect(status().isUnauthorized)
            }

            it("프로젝트 읽기 권한이 없는 사용자가 요청하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)

                mockMvc.perform(
                    delete("/api/projects/1/posts/6/comments/200")
                        .principal(otherAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("존재하지 않는 댓글이면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingCommentRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    delete("/api/projects/1/posts/6/comments/999")
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            // isManager 람다가 false를 반환하는 분기 커버.
            it("작성자도 매니저도 아닌 프로젝트 멤버가 삭제 시 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("memberuser") } returns Optional.of(memberUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 41L) } returns
                    Optional.of(ProjectUser(id = 903L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)

                mockMvc.perform(
                    delete("/api/projects/1/posts/6/comments/200")
                        .principal(memberAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { commentService.deletePostingComment(any(), any()) }
            }

            // Role.id는 @Id(PK)라 실제 DB에서 조회되면 항상 non-null이지만, isManager 람다
            // (it.role.id == RoleType.MANAGER.roleType)의 role.id가 null인 방어분기도 mockk로
            // 직접 구성 가능해 함께 커버한다.
            it("역할 id가 null인 프로젝트 멤버가 삭제 시도하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("memberuser") } returns Optional.of(memberUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 41L) } returns
                    Optional.of(ProjectUser(id = 904L, user = memberUser, project = project, role = Role(id = null)))
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)

                mockMvc.perform(
                    delete("/api/projects/1/posts/6/comments/200")
                        .principal(memberAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { commentService.deletePostingComment(any(), any()) }
            }

            // isManager 람다가 true를 반환하는 분기 커버 — 매니저는 작성자가 아니어도 삭제할 수 있다.
            it("매니저는 타인이 작성한 게시글 댓글도 삭제할 수 있어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 40L) } returns
                    Optional.of(ProjectUser(id = 901L, user = managerUser, project = project, role = Role(id = RoleType.MANAGER.roleType)))
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)
                every { commentService.deletePostingComment(200L, managerUser) } returns Unit

                mockMvc.perform(
                    delete("/api/projects/1/posts/6/comments/200")
                        .principal(managerAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { commentService.deletePostingComment(200L, managerUser) }
            }

            it("비로그인 사용자가 요청하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    delete("/api/projects/1/posts/6/comments/200")
                )
                    .andExpect(status().isUnauthorized)
            }
        }

        // yona controllers/api/IssueApi.java newIssueComment()/updateIssueComment(),
        // controllers/api/BoardApi.java newPostingComment()/updatePostingComment() 대응 (P2-56/57)
        describe("legacy Open API 경로 별칭 (-_-api/v1)") {
            it("POST /-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/comments — comment 필드로 이슈 댓글을 생성한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { commentService.createIssueComment(50L, "레거시댓글", user, null) } returns issueComment

                mockMvc.perform(
                    post("/-_-api/v1/owners/owner/projects/TestProject/issues/5/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"comment": "레거시댓글"}""")
                )
                    .andExpect(status().isCreated)
            }

            it("PUT /-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/comments/{commentId} — content/sha1 필드로 이슈 댓글을 수정한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { commentService.updateIssueComment(100L, "수정됨", user) } returns issueComment

                mockMvc.perform(
                    put("/-_-api/v1/owners/owner/projects/TestProject/issues/5/comments/100")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content": "수정됨", "sha1": "${com.github.yonaprojects.yona.domain.support.sha1Hex("이슈댓글")}"}""")
                )
                    .andExpect(status().isOk)
            }

            it("POST /-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number}/comments — body 필드로 게시글 댓글을 생성한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingRepository.findByProjectAndNumber(project, 6L) } returns posting
                every { commentService.createPostingComment(60L, "레거시게시글댓글", user, null) } returns postingComment

                mockMvc.perform(
                    post("/-_-api/v1/owners/owner/projects/TestProject/posts/6/comments")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"body": "레거시게시글댓글"}""")
                )
                    .andExpect(status().isCreated)
            }

            it("PUT /-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number}/comments/{commentId} — content/sha1 필드로 게시글 댓글을 수정한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingCommentRepository.findById(200L) } returns Optional.of(postingComment)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                every { commentService.updatePostingComment(200L, "수정됨", user) } returns postingComment

                mockMvc.perform(
                    put("/-_-api/v1/owners/owner/projects/TestProject/posts/6/comments/200")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content": "수정됨", "sha1": "${com.github.yonaprojects.yona.domain.support.sha1Hex("게시판댓글")}"}""")
                )
                    .andExpect(status().isOk)
            }

            it("체크섬이 legacy sha1과 다르면(다른 사람이 이미 수정) 409를 반환한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueCommentRepository.findById(100L) } returns Optional.of(issueComment)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()

                mockMvc.perform(
                    put("/-_-api/v1/owners/owner/projects/TestProject/issues/5/comments/100")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content": "수정됨", "sha1": "다른체크섬"}""")
                )
                    .andExpect(status().isConflict)
            }
        }

        describe("CommentRequest data class test") {
            it("기본값 및 data class 메서드들이 정상 동작해야 한다") {
                val req1 = CommentController.CommentRequest()
                val req2 = CommentController.CommentRequest(contents = "test", original = "orig", parentCommentId = 1L)
                val req3 = req2.copy()
                
                assert(req1.contents == "")
                assert(req1.original == null)
                assert(req1.parentCommentId == null)
                
                assert(req2.contents == "test")
                assert(req2.original == "orig")
                assert(req2.parentCommentId == 1L)
                
                assert(req2.hashCode() == req3.hashCode())
                assert(req2.toString() == req3.toString())
                assert(req2 == req3)
                assert(req1 != req2)
            }
        }
    }
})
