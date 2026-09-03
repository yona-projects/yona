package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.milestone.Milestone
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
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona controllers/api/IssueApi.java 대응 (P2-55, P2-56)
class IssueApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val postingRepository = mockk<PostingRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val attachmentService = mockk<AttachmentService>()
    val userRepository = mockk<UserRepository>()
    val issueService = mockk<IssueService>()
    val issueLabelRepository = mockk<IssueLabelRepository>()
    val issueLabelCategoryRepository = mockk<IssueLabelCategoryRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()

    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val organizationRepository = mockk<OrganizationRepository>()
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

    val controller = IssueApiController(
        projectRepository,
        projectUserRepository,
        postingRepository,
        issueRepository,
        postingCommentRepository,
        issueCommentRepository,
        attachmentService,
        userRepository,
        issueService,
        accessControl,
        issueLabelRepository,
        issueLabelCategoryRepository,
        milestoneRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    val project = Project(id = 1L, name = "myproject", owner = "alice", lastIssueNumber = 3)
    val user = User(id = 10L, loginId = "alice", name = "Alice")
    user.projectUsers.add(ProjectUser(id = 1L, user = user, project = project, role = Role(id = RoleType.MANAGER.roleType)))
    val auth = UsernamePasswordAuthenticationToken(user.loginId, null, emptyList())

    beforeTest {
        every { projectRepository.findByOwnerAndNameOrPreviousPlace("alice", "myproject") } returns Optional.of(project)
        every { userRepository.findByLoginId("alice") } returns Optional.of(user)
        every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
        every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
        every { projectRepository.save(any()) } answers { firstArg() }
        every { attachmentService.moveAll(any(), any(), any(), any()) } returns 0
    }

    describe("POST /-_-api/v1/owners/{owner}/projects/{projectName}/issues/imports") {
        it("게시글을 이슈로 전환하고 원본 게시글을 삭제한다") {
            val posting = Posting(id = 100L, title = "제목", body = "내용", project = project, number = 5L)
            every { postingRepository.findByProjectAndNumber(project, 5L) } returns posting
            every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(100L) } returns emptyList()

            val savedIssueSlot = slot<Issue>()
            every { issueRepository.save(capture(savedIssueSlot)) } answers { savedIssueSlot.captured.apply { id = 200L } }
            every { postingCommentRepository.deleteAll(emptyList()) } returns Unit
            every { postingRepository.delete(posting) } returns Unit

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/myproject/issues/imports")
                    .param("postNumber", "5")
                    .principal(auth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.number").value(4))

            verify { attachmentService.moveAll(ResourceType.BOARD_POST, "100", ResourceType.ISSUE_POST, "200") }
            verify { postingRepository.delete(posting) }
        }

        it("댓글을 최상위→답글 순으로 이슈댓글로 옮긴다") {
            val posting = Posting(id = 100L, title = "제목", body = "내용", project = project, number = 5L)
            every { postingRepository.findByProjectAndNumber(project, 5L) } returns posting

            val topLevel = PostingComment(id = 1L, posting = posting, contents = "first")
            val reply = PostingComment(id = 2L, posting = posting, contents = "reply", parentComment = topLevel)
            every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(100L) } returns listOf(topLevel, reply)

            val savedIssueSlot = slot<Issue>()
            every { issueRepository.save(capture(savedIssueSlot)) } answers { savedIssueSlot.captured.apply { id = 200L } }

            val topLevelIssueComment = IssueComment(id = 300L, issue = Issue(id = 200L, project = project))
            val replyIssueComment = IssueComment(id = 301L, issue = Issue(id = 200L, project = project), parentComment = topLevelIssueComment)
            val issueCommentSlots = mutableListOf<IssueComment>()
            every { issueCommentRepository.save(capture(issueCommentSlots)) } answers {
                if (issueCommentSlots.size == 1) topLevelIssueComment else replyIssueComment
            }
            every { postingCommentRepository.deleteAll(listOf(topLevel, reply)) } returns Unit
            every { postingRepository.delete(posting) } returns Unit

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/myproject/issues/imports")
                    .param("postNumber", "5")
                    .principal(auth)
            )
                .andExpect(status().isOk)

            verify { attachmentService.moveAll(ResourceType.NONISSUE_COMMENT, "1", ResourceType.ISSUE_COMMENT, "300") }
            verify { attachmentService.moveAll(ResourceType.NONISSUE_COMMENT, "2", ResourceType.ISSUE_COMMENT, "301") }
        }

        it("프로젝트 쓰기 권한이 없으면 403을 반환한다") {
            every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/myproject/issues/imports")
                    .param("postNumber", "5")
                    .principal(auth)
            ).andExpect(status().isForbidden)
        }

        it("postNumber에 해당하는 게시글이 없으면 400을 반환한다") {
            every { postingRepository.findByProjectAndNumber(project, 5L) } returns null

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/myproject/issues/imports")
                    .param("postNumber", "5")
                    .principal(auth)
            ).andExpect(status().isBadRequest)
        }
    }

    describe("GET /-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}") {
        it("이슈를 조회한다") {
            val issue = Issue(id = 50L, title = "이슈", project = project, number = 7L, authorId = 10L)
            every { issueRepository.findByProjectAndNumber(project, 7L) } returns issue

            mockMvc.perform(get("/-_-api/v1/owners/alice/projects/myproject/issues/7").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.title").value("이슈"))
        }

        it("이슈가 없으면 400을 반환한다") {
            every { issueRepository.findByProjectAndNumber(project, 7L) } returns null

            mockMvc.perform(get("/-_-api/v1/owners/alice/projects/myproject/issues/7").principal(auth))
                .andExpect(status().isBadRequest)
        }
    }

    describe("POST /-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/upvoteWeight, downvoteWeight") {
        it("upvoteWeight는 가중치를 올린다") {
            val issue = Issue(id = 50L, title = "이슈", project = project, number = 7L, authorId = 10L)
            every { issueRepository.findByProjectAndNumber(project, 7L) } returns issue
            every { issueService.upvoteWeight(50L) } returns issue.apply { weight = 1 }

            mockMvc.perform(post("/-_-api/v1/owners/alice/projects/myproject/issues/7/upvoteWeight").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.weight").value(1))
        }

        it("downvoteWeight는 가중치를 내린다") {
            val issue = Issue(id = 50L, title = "이슈", project = project, number = 7L, authorId = 10L)
            every { issueRepository.findByProjectAndNumber(project, 7L) } returns issue
            every { issueService.downvoteWeight(50L) } returns issue.apply { weight = -1 }

            mockMvc.perform(post("/-_-api/v1/owners/alice/projects/myproject/issues/7/downvoteWeight").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.weight").value(-1))
        }
    }

    describe("PATCH /-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/content") {
        it("본문을 수정한다") {
            val issue = Issue(id = 50L, title = "이슈", body = "원문", project = project, number = 7L, authorId = 10L)
            every { issueRepository.findByProjectAndNumber(project, 7L) } returns issue
            every { issueRepository.save(any()) } answers { firstArg() }

            mockMvc.perform(
                patch("/-_-api/v1/owners/alice/projects/myproject/issues/7/content")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"수정됨","sha1":"${com.github.yonaprojects.yona.domain.support.sha1Hex("원문")}"}""")
                    .principal(auth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.body").value("수정됨"))
        }

        it("체크섬이 다르면(다른 사람이 이미 수정) 409를 반환한다") {
            val issue = Issue(id = 50L, title = "이슈", body = "원문", project = project, number = 7L, authorId = 10L)
            every { issueRepository.findByProjectAndNumber(project, 7L) } returns issue

            mockMvc.perform(
                patch("/-_-api/v1/owners/alice/projects/myproject/issues/7/content")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"수정됨","sha1":"다른체크섬"}""")
                    .principal(auth)
            ).andExpect(status().isConflict)
        }
    }

    describe("PATCH /-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number} (updateIssueState)") {
        it("state를 CLOSED로 바꾼다") {
            val issue = Issue(id = 50L, title = "이슈", project = project, number = 7L, authorId = 10L, state = State.OPEN)
            every { issueRepository.findByProjectAndNumber(project, 7L) } returns issue
            every { issueService.changeState(50L, State.CLOSED, "alice") } returns issue.apply { state = State.CLOSED }

            mockMvc.perform(
                patch("/-_-api/v1/owners/alice/projects/myproject/issues/7")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"state":"closed"}""")
                    .principal(auth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.state").value("CLOSED"))
        }
    }

    describe("PUT /-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number} (updateIssue)") {
        it("제목/본문을 수정한다") {
            val issue = Issue(id = 50L, title = "이전제목", body = "이전본문", project = project, number = 7L, authorId = 10L)
            every { issueRepository.findByProjectAndNumber(project, 7L) } returns issue
            every {
                issueService.updateIssue(50L, "새제목", "새본문", user, null, null, null)
            } returns issue.apply { title = "새제목"; body = "새본문" }

            mockMvc.perform(
                put("/-_-api/v1/owners/alice/projects/myproject/issues/7")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"새제목","body":"새본문"}""")
                    .principal(auth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.title").value("새제목"))
        }
    }

    describe("POST /-_-api/v1/owners/{owner}/projects/{projectName}/issuelabel/{number}") {
        it("라벨 집합을 통째로 교체한다") {
            val issue = Issue(id = 50L, title = "이슈", project = project, number = 7L, authorId = 10L)
            every { issueRepository.findByProjectAndNumber(project, 7L) } returns issue
            val label = IssueLabel(id = 9L, name = "bug", category = com.github.yonaprojects.yona.domain.issue.IssueLabelCategory(project = project), project = project)
            every { issueLabelRepository.findAllById(listOf(9L)) } returns listOf(label)
            every { issueRepository.save(any()) } answers { firstArg() }

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/myproject/issuelabel/7")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""["9"]""")
                    .principal(auth)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.labels").value(1))
        }
    }

    describe("POST /-_-api/v1/owners/{owner}/projects/{projectName}/issues (newIssues)") {
        it("이슈 배열을 벌크 생성한다") {
            val created = Issue(id = 60L, title = "새이슈", project = project, number = 8L)
            every {
                issueService.createIssue(any(), user, null, null, null, false, null, false)
            } returns created

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/myproject/issues")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"issues":[{"title":"새이슈","body":"내용"}]}""")
                    .principal(auth)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$[0].status").value(201))
        }

        it("number/sendNotification 필드를 issueService.createIssue()에 그대로 전달한다") {
            val created = Issue(id = 61L, title = "임포트이슈", project = project, number = 42L)
            every {
                issueService.createIssue(any(), user, null, null, null, false, 42L, true)
            } returns created

            mockMvc.perform(
                post("/-_-api/v1/owners/alice/projects/myproject/issues")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"issues":[{"title":"임포트이슈","body":"내용","number":42}],"sendNotification":true}""")
                    .principal(auth)
            ).andExpect(status().isCreated)

            verify { issueService.createIssue(any(), user, null, null, null, false, 42L, true) }
        }
    }
})
