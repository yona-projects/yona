package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.project.TitleHeadService
import io.mockk.clearMocks
import io.mockk.slot
import com.github.yonaprojects.yona.domain.issue.IssueSharer
import com.github.yonaprojects.yona.domain.issue.IssueEvent
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.IssueComment
import java.security.MessageDigest
import java.time.Instant
import org.hamcrest.Matchers

class IssueControllerSpec : DescribeSpec({
    val issueService = mockk<IssueService>()
    val issueRepository = mockk<IssueRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val attachmentService = mockk<AttachmentService>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val issueEventRepository = mockk<IssueEventRepository>()
    val titleHeadService = mockk<TitleHeadService>()
    val watchService = mockk<com.github.yonaprojects.yona.domain.watch.WatchService>()
    val commentService = mockk<com.github.yonaprojects.yona.domain.comment.CommentService>()
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

    val issueController = IssueController(
        issueService,
        issueRepository,
        projectRepository,
        projectUserRepository,
        userRepository,
        attachmentService,
        issueCommentRepository,
        issueEventRepository,
        accessControl,
        titleHeadService,
        watchService,
        commentService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(issueController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(issueService, issueRepository, projectRepository, projectUserRepository, userRepository, attachmentService, issueCommentRepository, issueEventRepository, titleHeadService, watchService, commentService)
        every { titleHeadService.deleteTitleHeadKeyword(any(), any()) } returns Unit
    }

    describe("IssueController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", projectScope = ProjectScope.PRIVATE)
        val publicProject = Project(id = 2L, name = "PublicProject", projectScope = ProjectScope.PUBLIC)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerUser = User(id = 20L, loginId = "manageruser", name = "관리자유저")
        val otherUser = User(id = 30L, loginId = "otheruser", name = "외부유저")
        
        val memberRole = Role(id = RoleType.MEMBER.roleType)
        val managerRole = Role(id = RoleType.MANAGER.roleType)

        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)
        val projectManagerUser = ProjectUser(id = 101L, user = managerUser, project = project, role = managerRole)
        user.projectUsers.add(projectUser)
        managerUser.projectUsers.add(projectManagerUser)

        val issue = Issue(id = 5L, number = 5L, title = "이슈 제목", body = "이슈 내용", project = project, authorId = user.id, state = State.OPEN)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")
        val otherAuth = UsernamePasswordAuthenticationToken("otheruser", "password")
        val pageRequest = PageRequest.of(0, 25)

        describe("GET /api/projects/{projectId}/issues") {
            it("공개 프로젝트의 경우 비로그인 상태여도 이슈 목록을 반환해야 한다") {
                every { projectRepository.findById(2L) } returns Optional.of(publicProject)
                every { userRepository.findByLoginId(any()) } returns Optional.empty()
                every { issueRepository.findByProject(publicProject, any<Pageable>()) } returns PageImpl(listOf(issue), pageRequest, 1)

                mockMvc.perform(get("/api/projects/2/issues"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.content[0].title").value("이슈 제목"))
            }

            it("비공개 프로젝트의 경우 권한이 없는 유저가 조회하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 30L) } returns false

                mockMvc.perform(get("/api/projects/1/issues").principal(otherAuth))
                    .andExpect(status().isForbidden)
            }

            it("비공개 프로젝트의 경우 프로젝트 멤버인 유저가 조회하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueRepository.findByProject(project, any<Pageable>()) } returns PageImpl(listOf(issue), pageRequest, 1)

                mockMvc.perform(get("/api/projects/1/issues").principal(userAuth))
                    .andExpect(status().isOk)
            }

            // yona AbstractPostingApp.java:35 ITEMS_PER_PAGE(15)/IssueApp.java:46 ITEMS_PER_PAGE_MAX(45) 대응 (P1-105).
            it("size 파라미터를 지정하지 않으면 기본 페이지 크기는 15여야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val pageableSlot = slot<Pageable>()
                every { issueRepository.findByProject(project, capture(pageableSlot)) } returns PageImpl(listOf(issue), pageRequest, 1)

                mockMvc.perform(get("/api/projects/1/issues").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageSize shouldBe 15
            }

            it("size 파라미터가 45를 넘게 요청해도 45로 clamp되어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val pageableSlot = slot<Pageable>()
                every { issueRepository.findByProject(project, capture(pageableSlot)) } returns PageImpl(listOf(issue), pageRequest, 1)

                mockMvc.perform(get("/api/projects/1/issues").param("size", "999").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageSize shouldBe 45
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(get("/api/projects/999/issues"))
                    .andExpect(status().isNotFound)
            }

            it("state 파라미터를 지정하면 findByProjectAndState로 조회해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueRepository.findByProjectAndState(project, State.CLOSED, any<Pageable>()) } returns PageImpl(emptyList(), pageRequest, 0)

                mockMvc.perform(get("/api/projects/1/issues").param("state", "CLOSED").principal(userAuth))
                    .andExpect(status().isOk)

                verify(exactly = 1) { issueRepository.findByProjectAndState(project, State.CLOSED, any<Pageable>()) }
                verify(exactly = 0) { issueRepository.findByProject(any(), any()) }
            }
        }

        describe("GET /api/projects/{projectId}/issues/{issueId}") {
            it("권한이 있는 유저가 상세 조회하면 이슈 정보를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/api/projects/1/issues/5").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("이슈 제목"))
            }

            // yona AccessControl.java:274-279,368-383 isAllowedIfSharer() 대응 (P1-82) [GL-utils_AccessControl-013]
            it("프로젝트 멤버가 아니어도 이슈 공유자(IssueSharer)면 상세 조회를 허용해야 한다") {
                val sharedIssue = Issue(
                    id = 6L, number = 6L, title = "공유된 이슈", body = "본문", project = project,
                    authorId = user.id, state = State.OPEN
                )
                sharedIssue.sharers.add(
                    IssueSharer(
                        loginId = otherUser.loginId, user = otherUser, issue = sharedIssue
                    )
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 6L) } returns sharedIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 30L) } returns false

                mockMvc.perform(get("/api/projects/1/issues/6").principal(otherAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("공유된 이슈"))
            }

            it("이슈 공유자가 아니고 프로젝트 멤버도 아니면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 30L) } returns false

                mockMvc.perform(get("/api/projects/1/issues/5").principal(otherAuth))
                    .andExpect(status().isForbidden)
            }

            // yona IssueApp.java:267-269 issue()의 draft 전용 게이트 대응 (P1-84). AccessControl.isAllowed()
            // 호출보다 먼저 실행되는 별도 체크 — 프로젝트 멤버여도 작성자 본인이 아니면 초안은 못 본다.
            it("초안(draft) 이슈는 작성자 본인이 아닌 프로젝트 멤버가 조회하면 403 Forbidden을 반환해야 한다") {
                val draftIssue = Issue(
                    id = 8L, number = 8L, title = "초안 이슈", body = "본문", project = project,
                    authorId = user.id, authorLoginId = user.loginId, state = State.DRAFT, isDraft = true
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 8L) } returns draftIssue
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 20L) } returns true

                mockMvc.perform(get("/api/projects/1/issues/8").principal(managerAuth))
                    .andExpect(status().isForbidden)
            }

            it("초안(draft) 이슈는 작성자 본인이면 프로젝트 멤버가 아니어도 조회할 수 있어야 한다") {
                val draftIssue = Issue(
                    id = 9L, number = 9L, title = "초안 이슈2", body = "본문", project = project,
                    authorId = user.id, authorLoginId = user.loginId, state = State.DRAFT, isDraft = true
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 9L) } returns draftIssue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/api/projects/1/issues/9").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("초안 이슈2"))
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(get("/api/projects/999/issues/1"))
                    .andExpect(status().isNotFound)
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(get("/api/projects/1/issues/999").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            // yona IssueApp.java:267-269 issue()의 draft 전용 게이트 대응 (P1-84). 비로그인 사용자는
            // user == null이므로 isDraft && (user == null || ...) 분기에서 checkReadPermission보다
            // 먼저 403을 받는다.
            it("초안(draft) 이슈는 비로그인 사용자가 조회하면 403 Forbidden을 반환해야 한다") {
                val draftIssue = Issue(
                    id = 8L, number = 8L, title = "초안 이슈", body = "본문", project = project,
                    authorId = user.id, authorLoginId = user.loginId, state = State.DRAFT, isDraft = true
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 8L) } returns draftIssue

                mockMvc.perform(get("/api/projects/1/issues/8"))
                    .andExpect(status().isForbidden)
            }
        }

        describe("GET /api/projects/{projectId}/issues/{issueId}/timeline") {
            it("권한이 있는 유저가 조회하면 이슈의 변경 이력을 시간순으로 반환해야 한다") {
                val issueEvent = IssueEvent(
                    id = 1L, issue = issue, eventType = EventType.ISSUE_STATE_CHANGED,
                    oldValue = "OPEN", newValue = "CLOSED"
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueEventRepository.findByIssueOrderByCreatedAsc(issue) } returns listOf(issueEvent)

                mockMvc.perform(get("/api/projects/1/issues/5/timeline").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].eventType").value("ISSUE_STATE_CHANGED"))
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(get("/api/projects/1/issues/999/timeline").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            // yona IssueApp.java:299 timeline()의 @IsAllowed(resourceType = ISSUE_POST, READ) 대응 (P1-82)
            it("프로젝트 멤버가 아니어도 이슈 공유자면 변경 이력 조회를 허용해야 한다") {
                val sharedIssue = Issue(
                    id = 7L, number = 7L, title = "공유된 이슈2", body = "본문", project = project,
                    authorId = user.id, state = State.OPEN
                )
                sharedIssue.sharers.add(
                    IssueSharer(
                        loginId = otherUser.loginId, user = otherUser, issue = sharedIssue
                    )
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 7L) } returns sharedIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 30L) } returns false
                every { issueEventRepository.findByIssueOrderByCreatedAsc(sharedIssue) } returns emptyList()

                mockMvc.perform(get("/api/projects/1/issues/7/timeline").principal(otherAuth))
                    .andExpect(status().isOk)
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(get("/api/projects/999/issues/1/timeline"))
                    .andExpect(status().isNotFound)
            }

            it("공유자도 아니고 프로젝트 멤버도 아니면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 30L) } returns false

                mockMvc.perform(get("/api/projects/1/issues/5/timeline").principal(otherAuth))
                    .andExpect(status().isForbidden)
            }
        }

        describe("POST /api/projects/{projectId}/issues") {
            it("프로젝트 멤버인 유저가 새 이슈를 작성하면 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueService.createIssue(any(), user, null, null, null, false) } returns issue

                val jsonContent = """
                    {
                        "title": "이슈 제목",
                        "body": "이슈 내용",
                        "milestoneId": null,
                        "assigneeId": null,
                        "labelIds": null
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
            }

            // yona AbstractPosting.isPublish 대응 (P1-65).
            it("isDraft=true로 요청하면 초안 생성 요청이 서비스에 그대로 전달되어야 한다") {
                val draftIssue = Issue(id = 6L, number = null, title = "초안 제목", body = "초안 내용", project = project, authorId = user.id, state = State.DRAFT)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueService.createIssue(any(), user, null, null, null, true) } returns draftIssue

                val jsonContent = """
                    {
                        "title": "초안 제목",
                        "body": "초안 내용",
                        "milestoneId": null,
                        "assigneeId": null,
                        "labelIds": null,
                        "isDraft": true
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$.state").value("DRAFT"))

                verify(exactly = 1) { issueService.createIssue(any(), user, null, null, null, true) }
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                val jsonContent = """{ "title": "제목", "body": "내용", "milestoneId": null, "assigneeId": null, "labelIds": null }"""

                mockMvc.perform(
                    post("/api/projects/999/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                )
                    .andExpect(status().isNotFound)
            }

            it("익명 사용자면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                val jsonContent = """{ "title": "제목", "body": "내용", "milestoneId": null, "assigneeId": null, "labelIds": null }"""

                mockMvc.perform(
                    post("/api/projects/1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                )
                    .andExpect(status().isUnauthorized)
            }

            // checkWritePermission()의 existsByProjectIdAndUserId=false && isAllowedIfGroupMember=false 분기 대응.
            it("프로젝트 멤버가 아니고 그룹(조직) 멤버도 아니면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 30L) } returns false

                val jsonContent = """{ "title": "제목", "body": "내용", "milestoneId": null, "assigneeId": null, "labelIds": null }"""

                mockMvc.perform(
                    post("/api/projects/1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(otherAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueService.createIssue(any(), any(), any(), any(), any(), any()) }
            }

            // yona-wiki P3-02 14라운드(TASK-0436) — checkWritePermission()이 legacy
            // AccessControl.isProjectResourceCreatable()의 "PUBLIC 프로젝트는 조직 소속 여부와도
            // 무관하게 로그인한 사용자라면 누구나 이슈를 만들 수 있다" 분기를 놓쳐, 조직에 속하지
            // 않은 순수 PUBLIC 프로젝트의 비멤버가 항상 403을 받던 회귀 재현 테스트. 실서버(H2)
            // 재현: acmeorg 소속 없는 PUBLIC 프로젝트에 비멤버 bob이 `yona issue create` 호출 시
            // 403 Forbidden(수정 전) → 201 Created(수정 후).
            it("조직 소속이 아닌 공개 프로젝트도 비멤버 로그인 사용자가 이슈를 생성할 수 있어야 한다") {
                every { projectRepository.findById(2L) } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(2L, 30L) } returns false
                every { issueService.createIssue(any(), otherUser, null, null, null, false) } returns issue

                val jsonContent = """{ "title": "제목", "body": "내용", "milestoneId": null, "assigneeId": null, "labelIds": null }"""

                mockMvc.perform(
                    post("/api/projects/2/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(otherAuth)
                )
                    .andExpect(status().isCreated)

                verify(exactly = 1) { issueService.createIssue(any(), otherUser, null, null, null, false) }
            }

            // checkWritePermission()의 existsByProjectIdAndUserId=false && isAllowedIfGroupMember=true 분기 대응 (P1-57).
            // 프로젝트 직접 멤버가 아니어도 PUBLIC/PROTECTED 프로젝트의 조직 멤버면 쓰기 권한을 가진다.
            it("프로젝트 멤버가 아니어도 공개 프로젝트의 조직 멤버면 이슈를 생성할 수 있어야 한다") {
                val organization = Organization(id = 1L, name = "TestOrg")
                val orgProject = Project(id = 7L, name = "OrgProject", projectScope = ProjectScope.PUBLIC, organization = organization)
                val orgRole = Role(id = RoleType.ORG_MEMBER.roleType)
                organization.organizationUsers.add(OrganizationUser(user = otherUser, organization = organization, role = orgRole))

                every { projectRepository.findById(7L) } returns Optional.of(orgProject)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(7L, 30L) } returns false
                every { issueService.createIssue(any(), otherUser, null, null, null, false) } returns issue

                val jsonContent = """{ "title": "제목", "body": "내용", "milestoneId": null, "assigneeId": null, "labelIds": null }"""

                mockMvc.perform(
                    post("/api/projects/7/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(otherAuth)
                )
                    .andExpect(status().isCreated)
            }

            it("assigneeId를 지정하면 담당자를 조회해서 서비스에 전달해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { userRepository.findById(30L) } returns Optional.of(otherUser)
                every { issueService.createIssue(any(), user, otherUser, null, null, false) } returns issue

                val jsonContent = """{ "title": "제목", "body": "내용", "milestoneId": null, "assigneeId": 30, "labelIds": null }"""

                mockMvc.perform(
                    post("/api/projects/1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)

                verify(exactly = 1) { issueService.createIssue(any(), user, otherUser, null, null, false) }
            }

            it("body를 생략하면 빈 문자열로 이슈를 생성해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val issueSlot = slot<Issue>()
                every { issueService.createIssue(capture(issueSlot), user, null, null, null, false) } returns issue

                val jsonContent = """{ "title": "제목", "milestoneId": null, "assigneeId": null, "labelIds": null }"""

                mockMvc.perform(
                    post("/api/projects/1/issues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)

                issueSlot.captured.body shouldBe ""
            }
        }

        describe("PUT /api/projects/{projectId}/issues/{issueId}") {
            it("작성자가 이슈를 수정하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { issueService.updateIssue(5L, "수정된 제목", "수정된 내용", user, null, null, null) } returns issue

                val jsonContent = """
                    {
                        "title": "수정된 제목",
                        "body": "수정된 내용",
                        "milestoneId": null,
                        "assigneeId": null,
                        "labelIds": null
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/issues/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            // yona AccessControl.java:244-248 isAllowedIfAssignee() 대응 (P2-12). 담당자는
            // isManagerOrAuthor 여부와 무관하게 author와 동급 쓰기 권한을 가진다.
            it("작성자도 관리자도 아니지만 담당자면 이슈를 수정할 수 있어야 한다") {
                val assigneeIssue = Issue(
                    id = 6L, number = 6L, title = "담당 이슈", body = "본문", project = project,
                    authorId = user.id, state = State.OPEN, assignee = Assignee(user = otherUser, project = project)
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 6L) } returns assigneeIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()
                every { issueService.updateIssue(6L, "담당자가 수정", "수정 내용", otherUser, null, null, null) } returns assigneeIssue

                val jsonContent = """
                    {
                        "title": "담당자가 수정",
                        "body": "수정 내용",
                        "milestoneId": null,
                        "assigneeId": null,
                        "labelIds": null
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/issues/6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(otherAuth)
                )
                    .andExpect(status().isOk)
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                val jsonContent = """{ "title": "t", "body": "b", "milestoneId": null, "assigneeId": null, "labelIds": null }"""

                mockMvc.perform(
                    put("/api/projects/999/issues/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                val jsonContent = """{ "title": "t", "body": "b", "milestoneId": null, "assigneeId": null, "labelIds": null }"""

                mockMvc.perform(
                    put("/api/projects/1/issues/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("익명 사용자면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue

                val jsonContent = """{ "title": "t", "body": "b", "milestoneId": null, "assigneeId": null, "labelIds": null }"""

                mockMvc.perform(
                    put("/api/projects/1/issues/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                )
                    .andExpect(status().isUnauthorized)
            }

            it("작성자도 관리자도 담당자도 아니면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()

                val jsonContent = """{ "title": "t", "body": "b", "milestoneId": null, "assigneeId": null, "labelIds": null }"""

                mockMvc.perform(
                    put("/api/projects/1/issues/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(otherAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueService.updateIssue(any(), any(), any(), any(), any(), any(), any()) }
            }

            it("assigneeId를 지정하면 담당자를 조회해서 서비스에 전달해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { userRepository.findById(30L) } returns Optional.of(otherUser)
                every { issueService.updateIssue(5L, "제목", "내용", user, otherUser, null, null) } returns issue

                val jsonContent = """{ "title": "제목", "body": "내용", "milestoneId": null, "assigneeId": 30, "labelIds": null }"""

                mockMvc.perform(
                    put("/api/projects/1/issues/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { issueService.updateIssue(5L, "제목", "내용", user, otherUser, null, null) }
            }
        }

        // yona IssueApp.editIssue()의 hasTargetProject() 대응 (P1-48).
        describe("POST /api/projects/{projectId}/issues/{issueId}/move") {
            it("작성자가 대상 프로젝트로 이슈를 이동시키면 200 OK를 반환해야 한다") {
                val targetProject = Project(id = 3L, name = "TargetProject", projectScope = ProjectScope.PUBLIC)
                val movedIssue = Issue(id = 5L, number = 1L, title = "이슈 제목", body = "이슈 내용", project = targetProject, authorId = user.id, state = State.OPEN)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectRepository.findById(3L) } returns Optional.of(targetProject)
                every { issueService.moveIssue(5L, 3L, user) } returns movedIssue

                val jsonContent = """{ "targetProjectId": 3 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    // P3-30 — moveIssue()가 raw Issue 대신 IssueResponse를 반환하도록 바뀌면서
                    // 응답 형태가 nested "project.id"에서 flat "projectId"로 바뀌었다(IssueResponse의
                    // 기존 필드 선택 기준을 그대로 따름 — 다른 v1 API 소비자가 이미 이 필드명을 쓰고 있음).
                    .andExpect(jsonPath("$.projectId").value(3))

                verify(exactly = 1) { issueService.moveIssue(5L, 3L, user) }
            }

            it("원본 이슈의 관리자/작성자가 아니면 403 Forbidden을 반환하고 이동을 호출하지 않아야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()

                val jsonContent = """{ "targetProjectId": 3 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(otherAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueService.moveIssue(any(), any(), any()) }
            }

            it("대상 프로젝트에 이슈 생성 권한이 없으면(비공개+비멤버) 403 Forbidden을 반환하고 이동을 호출하지 않아야 한다") {
                val privateTargetProject = Project(id = 4L, name = "PrivateTarget", owner = "someoneelse", projectScope = ProjectScope.PRIVATE)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectRepository.findById(4L) } returns Optional.of(privateTargetProject)

                val jsonContent = """{ "targetProjectId": 4 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueService.moveIssue(any(), any(), any()) }
            }

            it("대상 프로젝트가 존재하지 않으면 400 Bad Request를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectRepository.findById(999L) } returns Optional.empty()

                val jsonContent = """{ "targetProjectId": 999 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)
            }

            // yona AccessControl.java:244-248 isAllowedIfAssignee() 대응 (P2-12)
            it("작성자도 관리자도 아니지만 담당자면 이슈를 이동시킬 수 있어야 한다") {
                val targetProject = Project(id = 3L, name = "TargetProject", projectScope = ProjectScope.PUBLIC)
                val assigneeIssue = Issue(
                    id = 6L, number = 6L, title = "담당 이슈", body = "본문", project = project,
                    authorId = user.id, state = State.OPEN, assignee = Assignee(user = otherUser, project = project)
                )
                val movedIssue = Issue(id = 6L, number = 1L, title = "담당 이슈", body = "본문", project = targetProject, authorId = user.id, state = State.OPEN)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 6L) } returns assigneeIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()
                every { projectRepository.findById(3L) } returns Optional.of(targetProject)
                every { issueService.moveIssue(6L, 3L, otherUser) } returns movedIssue

                val jsonContent = """{ "targetProjectId": 3 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/6/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(otherAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { issueService.moveIssue(6L, 3L, otherUser) }
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                val jsonContent = """{ "targetProjectId": 3 }"""

                mockMvc.perform(
                    post("/api/projects/999/issues/5/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                val jsonContent = """{ "targetProjectId": 3 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/999/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("익명 사용자면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue

                val jsonContent = """{ "targetProjectId": 3 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                )
                    .andExpect(status().isUnauthorized)
            }
        }

        // yona IssueApp.editIssue()의 "if (issue.isPublish) { ... }" 발행 전환 대응 (P1-65).
        describe("POST /api/projects/{projectId}/issues/{issueId}/publish") {
            it("작성자가 초안을 발행하면 200 OK와 함께 발행된 이슈를 반환해야 한다") {
                val draftIssue = Issue(id = 5L, number = 5L, title = "이슈 제목", body = "이슈 내용", project = project, authorId = user.id, state = State.DRAFT)
                val publishedIssue = Issue(id = 5L, number = 9L, title = "이슈 제목", body = "이슈 내용", project = project, authorId = user.id, state = State.OPEN)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns draftIssue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { issueService.publishIssue(5L, user) } returns publishedIssue

                mockMvc.perform(post("/api/projects/1/issues/5/publish").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.state").value("OPEN"))
                    .andExpect(jsonPath("$.number").value(9))

                verify(exactly = 1) { issueService.publishIssue(5L, user) }
            }

            it("관리자/작성자가 아니면 403 Forbidden을 반환하고 발행을 호출하지 않아야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()

                mockMvc.perform(post("/api/projects/1/issues/5/publish").principal(otherAuth))
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueService.publishIssue(any(), any()) }
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(post("/api/projects/1/issues/999/publish").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            // yona AccessControl.java:244-248 isAllowedIfAssignee() 대응 (P2-12)
            it("작성자도 관리자도 아니지만 담당자면 초안을 발행할 수 있어야 한다") {
                val draftIssue = Issue(
                    id = 6L, number = 6L, title = "담당 초안", body = "본문", project = project,
                    authorId = user.id, state = State.DRAFT, assignee = Assignee(user = otherUser, project = project)
                )
                val publishedIssue = Issue(id = 6L, number = 9L, title = "담당 초안", body = "본문", project = project, authorId = user.id, state = State.OPEN)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 6L) } returns draftIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()
                every { issueService.publishIssue(6L, otherUser) } returns publishedIssue

                mockMvc.perform(post("/api/projects/1/issues/6/publish").principal(otherAuth))
                    .andExpect(status().isOk)

                verify(exactly = 1) { issueService.publishIssue(6L, otherUser) }
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(post("/api/projects/999/issues/5/publish").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("익명 사용자면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue

                mockMvc.perform(post("/api/projects/1/issues/5/publish"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("DELETE /api/projects/{projectId}/issues/{issueId}") {
            it("관리자가 이슈를 삭제하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { issueService.deleteIssueCascade(issue) } returns Unit

                mockMvc.perform(delete("/api/projects/1/issues/5").principal(managerAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }

            // yona AccessControl.java:244-248 isAllowedIfAssignee() 대응 (P2-12)
            it("작성자도 관리자도 아니지만 담당자면 이슈를 삭제할 수 있어야 한다") {
                val assigneeIssue = Issue(
                    id = 6L, number = 6L, title = "담당 이슈", body = "본문", project = project,
                    authorId = user.id, state = State.OPEN, assignee = Assignee(user = otherUser, project = project)
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 6L) } returns assigneeIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()
                every { issueService.deleteIssueCascade(assigneeIssue) } returns Unit

                mockMvc.perform(delete("/api/projects/1/issues/6").principal(otherAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }

            // yona Project.delete() 이슈 삭제(댓글/이벤트/즐겨찾기/첨부파일/TitleHead 정리) 대응 (P0-19) —
            // 실제 연관 데이터 정리 자체는 IssueServiceImpl.deleteIssueCascade()에 위임되며
            // (검증은 IssueServiceSpec 참고), 컨트롤러는 위임 호출 자체만 검증한다.
            it("이슈를 삭제하면 issueService.deleteIssueCascade가 호출되어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { issueService.deleteIssueCascade(issue) } returns Unit

                mockMvc.perform(delete("/api/projects/1/issues/5").principal(managerAuth))
                    .andExpect(status().isOk)

                verify(exactly = 1) { issueService.deleteIssueCascade(issue) }
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(delete("/api/projects/999/issues/5").principal(managerAuth))
                    .andExpect(status().isNotFound)
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(delete("/api/projects/1/issues/999").principal(managerAuth))
                    .andExpect(status().isNotFound)
            }

            it("익명 사용자면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue

                mockMvc.perform(delete("/api/projects/1/issues/5"))
                    .andExpect(status().isUnauthorized)
            }

            it("작성자도 관리자도 담당자도 아니면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()

                mockMvc.perform(delete("/api/projects/1/issues/5").principal(otherAuth))
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueService.deleteIssueCascade(any()) }
            }
        }

        // yona IssueApp.java의 상태 전환(open/close) 대응. 담당자/작성자/매니저만 상태를 바꿀 수 있다.
        describe("POST /api/projects/{projectId}/issues/{issueId}/state") {
            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(post("/api/projects/999/issues/5/state").param("state", "CLOSED").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(post("/api/projects/1/issues/999/state").param("state", "CLOSED").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("익명 사용자면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue

                mockMvc.perform(post("/api/projects/1/issues/5/state").param("state", "CLOSED"))
                    .andExpect(status().isUnauthorized)
            }

            // isManagerOrAuthorOrAssignee()의 projectUserRepository.findByProjectIdAndUserId(...).map { role == MANAGER }
            // 람다가 false를 반환하는 경로 대응 — Optional이 비어있지 않고(MEMBER 존재) 매니저가 아닌 경우.
            it("작성자도 관리자도 담당자도 아닌 일반 멤버면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                val otherMemberProjectUser = ProjectUser(id = 102L, user = otherUser, project = project, role = memberRole)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.of(otherMemberProjectUser)

                mockMvc.perform(post("/api/projects/1/issues/5/state").param("state", "CLOSED").principal(otherAuth))
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueService.changeState(any(), any(), any()) }
            }

            it("작성자가 상태를 변경하면 200 OK와 함께 갱신된 이슈를 반환해야 한다") {
                val closedIssue = Issue(id = 5L, number = 5L, title = "이슈 제목", body = "이슈 내용", project = project, authorId = user.id, state = State.CLOSED)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueService.changeState(5L, State.CLOSED, "testuser") } returns closedIssue

                mockMvc.perform(post("/api/projects/1/issues/5/state").param("state", "CLOSED").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.state").value("CLOSED"))

                verify(exactly = 1) { issueService.changeState(5L, State.CLOSED, "testuser") }
            }
        }

        // yona IssueApi.java:1176-1210 upvoteWeight()/downvoteWeight() 대응 (P1-101). [GL-controllers_api_IssueApi-064;GL-controllers_api_IssueApi-065]
        describe("POST /api/projects/{projectId}/issues/{issueId}/upvoteWeight") {
            it("프로젝트 멤버가 이슈 가중치를 +1 하면 갱신된 weight를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueService.upvoteWeight(5L) } returns issue.also { it.weight = 1 }

                mockMvc.perform(post("/api/projects/1/issues/5/upvoteWeight").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.weight").value(1))

                verify(exactly = 1) { issueService.upvoteWeight(5L) }
            }

            it("프로젝트 멤버가 아니면 403 Forbidden을 반환하고 서비스를 호출하지 않아야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 30L) } returns Optional.empty()

                mockMvc.perform(post("/api/projects/1/issues/5/upvoteWeight").principal(otherAuth))
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueService.upvoteWeight(any()) }
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(post("/api/projects/999/issues/5/upvoteWeight").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(post("/api/projects/1/issues/999/upvoteWeight").principal(userAuth))
                    .andExpect(status().isNotFound)
            }
        }

        describe("POST /api/projects/{projectId}/issues/{issueId}/downvoteWeight") {
            it("프로젝트 멤버가 이슈 가중치를 -1 하면 갱신된 weight를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueService.downvoteWeight(5L) } returns issue.also { it.weight = -1 }

                mockMvc.perform(post("/api/projects/1/issues/5/downvoteWeight").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.weight").value(-1))

                verify(exactly = 1) { issueService.downvoteWeight(5L) }
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(post("/api/projects/999/issues/5/downvoteWeight").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(post("/api/projects/1/issues/999/downvoteWeight").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("프로젝트 멤버가 아니면 403 Forbidden을 반환하고 서비스를 호출하지 않아야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)

                mockMvc.perform(post("/api/projects/1/issues/5/downvoteWeight").principal(otherAuth))
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueService.downvoteWeight(any()) }
            }
        }

        // yona IssueApi.java:551-584 detectChange() 대응 (P1-102). 폴링으로 다른 사용자의 변경을 감지. [GL-controllers_api_IssueApi-030;GL-controllers_api_IssueApi-031]
        describe("POST /api/projects/{projectId}/issues/{issueId}/detectChange") {
            it("body와 댓글 수가 그대로면 issueBodyChanged=false를 반환해야 한다") {
                val checksum = MessageDigest.getInstance("SHA-1")
                    .digest("이슈 내용".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()

                val jsonContent = """{ "issueBodyChecksum": "$checksum", "numOfComments": 0 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/detectChange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.issueBodyChanged").value(false))
                    .andExpect(jsonPath("$.numOfComments").value(0))
                    .andExpect(jsonPath("$.result").value("ok"))
            }

            it("body가 변경됐으면 issueBodyChanged=true를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()

                val jsonContent = """{ "issueBodyChecksum": "stale-checksum", "numOfComments": 0 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/detectChange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.issueBodyChanged").value(true))
            }

            it("다른 사람이 댓글을 추가했으면 최신 댓글 작성자 이름을 포함해야 한다") {
                val newComment = IssueComment(
                    id = 50L, contents = "새 댓글", authorLoginId = "otheruser", issue = issue
                )
                val checksum = MessageDigest.getInstance("SHA-1")
                    .digest("이슈 내용".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns listOf(newComment)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)

                val jsonContent = """{ "issueBodyChecksum": "$checksum", "numOfComments": 0 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/detectChange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.commentAuthorName").value(otherUser.getDisplayName()))
            }

            it("익명 사용자는 401을 반환해야 한다") {
                val jsonContent = """{ "issueBodyChecksum": "x", "numOfComments": 0 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/detectChange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                )
                    .andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findById(999L) } returns Optional.empty()

                val jsonContent = """{ "issueBodyChecksum": "x", "numOfComments": 0 }"""

                mockMvc.perform(
                    post("/api/projects/999/issues/5/detectChange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                val jsonContent = """{ "issueBodyChecksum": "x", "numOfComments": 0 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/999/detectChange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            // lastComment.authorLoginId가 null인 경우(익명/탈퇴 등) commentAuthor 조회 자체를 건너뛰고
            // commentAuthorName도 null이어야 한다 — authorLoginId?.let{} 분기의 null 경로.
            it("최신 댓글의 작성자 loginId가 없으면 commentAuthorName은 null이어야 한다") {
                val anonymousComment = IssueComment(id = 51L, contents = "익명 댓글", authorLoginId = null, issue = issue)
                val checksum = MessageDigest.getInstance("SHA-1")
                    .digest("이슈 내용".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns listOf(anonymousComment)

                val jsonContent = """{ "issueBodyChecksum": "$checksum", "numOfComments": 0 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/detectChange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.commentAuthorName").value(Matchers.nullValue()))
            }

            // 댓글 작성자의 loginId는 남아있지만(탈퇴 등) userRepository에서 찾지 못하는 경우
            // commentAuthor?.getDisplayName() ?: lastComment.authorLoginId 의 우변(엘비스 false 대체) 경로.
            it("댓글 작성자를 찾을 수 없으면 loginId를 그대로 이름으로 사용해야 한다") {
                val ghostComment = IssueComment(id = 52L, contents = "탈퇴한 사람 댓글", authorLoginId = "ghostuser", issue = issue)
                val checksum = MessageDigest.getInstance("SHA-1")
                    .digest("이슈 내용".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns listOf(ghostComment)
                every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()

                val jsonContent = """{ "issueBodyChecksum": "$checksum", "numOfComments": 0 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/detectChange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.commentAuthorName").value("ghostuser"))
            }

            // (issue.updatedDate ?: issue.createdDate)?.toEpochMilli() 의 non-null 경로 대응.
            it("이슈에 updatedDate가 있으면 issueUpdateDate에 epoch milli 값을 채워야 한다") {
                val updatedInstant = Instant.parse("2026-01-01T00:00:00Z")
                val updatedIssue = Issue(
                    id = 20L, number = 20L, title = "이슈 제목", body = "이슈 내용", project = project,
                    authorId = user.id, state = State.OPEN, updatedDate = updatedInstant
                )
                val checksum = MessageDigest.getInstance("SHA-1")
                    .digest("이슈 내용".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 20L) } returns updatedIssue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(20L) } returns emptyList()

                val jsonContent = """{ "issueBodyChecksum": "$checksum", "numOfComments": 0 }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/20/detectChange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.issueUpdateDate").value(updatedInstant.toEpochMilli()))
            }
        }

        // P3-52 항목1 — yona IssueApi.commentNotiRecivers() 대응. 이슈 댓글 작성 중(디바운스)
        // "지금 이 내용으로 등록하면 누구에게 알림이 갈지" 미리보기. CommentServiceImpl.
        // createIssueComment()의 실제 알림 수신자 계산(baseWatchers=이슈 작성자 +
        // watchService.findActualWatchers(ISSUE_POST, NEW_COMMENT) + 멘션 - 본인)과 정확히
        // 동일한 로직을 재사용해, 미리보기와 실제 알림 발행 결과가 어긋나지 않게 한다.
        describe("POST /api/projects/{projectId}/issues/{issueId}/commentNotiReceivers") {
            it("이슈 작성자와 감시자를 알림 수신자로 반환해야 한다") {
                val authoredIssue = Issue(
                    id = 5L, number = 5L, title = "이슈 제목", body = "이슈 내용", project = project,
                    authorId = otherUser.id, state = State.OPEN
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns authoredIssue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { userRepository.findById(otherUser.id!!) } returns Optional.of(otherUser)
                every {
                    watchService.findActualWatchers(setOf(otherUser), ResourceType.ISSUE_POST, "5", 1L, eventType = EventType.NEW_COMMENT)
                } returns setOf(otherUser)
                every { commentService.extractMentionedUsers("새 댓글 내용") } returns emptySet()

                val jsonContent = """{ "comment": "새 댓글 내용", "parentCommentId": "" }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/commentNotiReceivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.receivers.length()").value(1))
                    .andExpect(jsonPath("$.receivers[0].loginId").value(otherUser.loginId))
                    .andExpect(jsonPath("$.receivers[0].pureNameOnly").value(otherUser.getPureNameOnly()))
            }

            it("댓글 내용에 멘션이 있으면 멘션된 사용자도 수신자에 포함해야 한다") {
                val mentionedUser = User(id = 40L, loginId = "mentioned", name = "멘션대상")
                val authoredIssue = Issue(
                    id = 5L, number = 5L, title = "이슈 제목", body = "이슈 내용", project = project,
                    authorId = otherUser.id, state = State.OPEN
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns authoredIssue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { userRepository.findById(otherUser.id!!) } returns Optional.of(otherUser)
                every {
                    watchService.findActualWatchers(setOf(otherUser), ResourceType.ISSUE_POST, "5", 1L, eventType = EventType.NEW_COMMENT)
                } returns emptySet()
                every { commentService.extractMentionedUsers("@mentioned 안녕하세요") } returns setOf(mentionedUser)

                val jsonContent = """{ "comment": "@mentioned 안녕하세요", "parentCommentId": "" }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/commentNotiReceivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.receivers.length()").value(1))
                    .andExpect(jsonPath("$.receivers[0].loginId").value("mentioned"))
            }

            it("요청 작성자 본인은 수신자 목록에서 제외돼야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { userRepository.findById(user.id!!) } returns Optional.of(user)
                every {
                    watchService.findActualWatchers(setOf(user), ResourceType.ISSUE_POST, "5", 1L, eventType = EventType.NEW_COMMENT)
                } returns setOf(user)
                every { commentService.extractMentionedUsers("내용") } returns emptySet()

                val jsonContent = """{ "comment": "내용", "parentCommentId": "" }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/commentNotiReceivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.receivers.length()").value(0))
            }

            it("익명 사용자는 401을 반환해야 한다") {
                val jsonContent = """{ "comment": "내용", "parentCommentId": "" }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/5/commentNotiReceivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                )
                    .andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                val jsonContent = """{ "comment": "내용", "parentCommentId": "" }"""

                mockMvc.perform(
                    post("/api/projects/1/issues/999/commentNotiReceivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }
        }

        // yona IssueApi.java:319-349 updateIssueContent()의 isModifiedByOthers() 충돌 감지 대응 (P1-102). [GL-controllers_api_IssueApi-020]
        describe("PATCH /api/projects/{projectId}/issues/{issueId}/content") {
            it("원본이 현재 body와 일치하면 정상적으로 갱신해야 한다") {
                val contentIssue = Issue(id = 15L, number = 15L, title = "이슈 제목", body = "이슈 내용", project = project, authorId = user.id, state = State.OPEN)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 15L) } returns contentIssue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueRepository.save(contentIssue) } returns contentIssue

                val jsonContent = """{ "content": "수정된 내용", "original": "이슈 내용" }"""

                mockMvc.perform(
                    patch("/api/projects/1/issues/15/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.body").value("수정된 내용"))
            }

            it("원본이 현재 body와 다르면(이미 다른 사람이 수정함) 409 Conflict를 반환하고 저장하지 않아야 한다") {
                val contentIssue = Issue(id = 16L, number = 16L, title = "이슈 제목", body = "이슈 내용", project = project, authorId = user.id, state = State.OPEN)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 16L) } returns contentIssue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                val jsonContent = """{ "content": "내 수정본", "original": "예전에 봤던 옛 내용" }"""

                mockMvc.perform(
                    patch("/api/projects/1/issues/16/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isConflict)
                    .andExpect(jsonPath("$.storedContent").value("이슈 내용"))

                verify(exactly = 0) { issueRepository.save(any()) }
            }

            it("익명 사용자면 401 Unauthorized를 반환해야 한다") {
                val jsonContent = """{ "content": "내용", "original": "이슈 내용" }"""

                mockMvc.perform(
                    patch("/api/projects/1/issues/5/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                )
                    .andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findById(999L) } returns Optional.empty()

                val jsonContent = """{ "content": "내용", "original": "이슈 내용" }"""

                mockMvc.perform(
                    patch("/api/projects/999/issues/5/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("존재하지 않는 이슈면 404를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                val jsonContent = """{ "content": "내용", "original": "이슈 내용" }"""

                mockMvc.perform(
                    patch("/api/projects/1/issues/999/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("원본은 일치하지만 수정 권한이 없으면 403 Forbidden을 반환하고 저장하지 않아야 한다") {
                val contentIssue = Issue(id = 17L, number = 17L, title = "이슈 제목", body = "이슈 내용", project = project, authorId = user.id, state = State.OPEN)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 17L) } returns contentIssue
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)

                val jsonContent = """{ "content": "내용", "original": "이슈 내용" }"""

                mockMvc.perform(
                    patch("/api/projects/1/issues/17/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(otherAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { issueRepository.save(any()) }
            }

            // issue.body ?: "" 엘비스의 null 경로 대응 — 본문이 아직 없는(null) 이슈도 정상 처리돼야 한다.
            it("이슈 본문이 null이어도 정상적으로 갱신해야 한다") {
                val emptyBodyIssue = Issue(id = 18L, number = 18L, title = "이슈 제목", body = null, project = project, authorId = user.id, state = State.OPEN)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 18L) } returns emptyBodyIssue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { issueRepository.save(emptyBodyIssue) } returns emptyBodyIssue

                val jsonContent = """{ "content": "새 내용", "original": "" }"""

                mockMvc.perform(
                    patch("/api/projects/1/issues/18/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.body").value("새 내용"))
            }
        }
    }
})
