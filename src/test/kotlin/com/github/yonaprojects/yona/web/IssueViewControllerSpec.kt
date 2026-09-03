package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import org.springframework.data.web.PageableHandlerMethodArgumentResolver

import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.milestone.MilestoneService
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.user.FavoriteIssueRepository
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService

import com.github.yonaprojects.yona.domain.project.RecentProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import org.springframework.context.MessageSource
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.config.TemplateHelper
import com.github.yonaprojects.yona.domain.issue.IssueExcelService
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import com.github.yonaprojects.yona.domain.project.TitleHeadService
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.project.ProjectUser
import org.springframework.data.jpa.domain.Specification
import io.mockk.slot
import com.github.yonaprojects.yona.domain.issue.IssueComment
import java.time.Instant
import com.github.yonaprojects.yona.domain.issue.IssueEvent
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.issue.IssueTimelineItem
import com.github.yonaprojects.yona.domain.project.RecentProject
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.role.RoleType
import org.springframework.ui.ExtendedModelMap
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpHeaders
import org.springframework.data.domain.Sort
import org.springframework.security.core.context.SecurityContextHolder
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldNotBe
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory

class IssueViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectService = mockk<ProjectService>()
    val issueRepository = mockk<IssueRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val watchService = mockk<WatchService>()
    val milestoneService = mockk<MilestoneService>()
    val issueLabelRepository = mockk<IssueLabelRepository>()
    val favoriteIssueRepository = mockk<FavoriteIssueRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val messageSource = mockk<MessageSource>()
    val recentProjectRepository = mockk<RecentProjectRepository>()
    val issueService = mockk<IssueService>()
    val templateHelper = mockk<TemplateHelper>()
    val issueExcelService = mockk<IssueExcelService>()
    val repositoryService = mockk<RepositoryService>()
    val recentIssueService = mockk<RecentIssueService>(relaxed = true)
    val titleHeadService = mockk<TitleHeadService>()
    val issueEventRepository = mockk<IssueEventRepository>()
    val attachmentService = mockk<AttachmentService>()
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

    val issueViewController = IssueViewController(
        projectRepository,
        projectService,
        issueRepository,
        projectUserRepository,
        userRepository,
        issueCommentRepository,
        watchService,
        milestoneService,
        issueLabelRepository,
        favoriteIssueRepository,
        attachmentRepository,
        messageSource,
        recentProjectRepository,
        issueService,
        templateHelper,
        issueExcelService,
        repositoryService,
        recentIssueService,
        accessControl,
        titleHeadService,
        issueEventRepository,
        attachmentService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(issueViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(
            projectRepository, projectService, issueRepository, projectUserRepository, userRepository, issueCommentRepository,
            watchService, milestoneService, issueLabelRepository, favoriteIssueRepository, attachmentRepository,
            messageSource, recentProjectRepository, issueService, templateHelper, issueExcelService, repositoryService,
            recentIssueService, titleHeadService, issueEventRepository, attachmentService
        )
        every { titleHeadService.deleteTitleHeadKeyword(any(), any()) } returns Unit
        every { issueEventRepository.findByIssueOrderByCreatedAsc(any()) } returns emptyList()
        every { projectUserRepository.findByProjectIdAndUserId(any(), any()) } returns Optional.empty()
    }

    describe("IssueViewController 템플릿 연동 테스트") {
        val memberRole = Role(id = 2L, name = "MEMBER")
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        
        val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val projectUser = ProjectUser(id = 1L, user = memberUser, project = project, role = memberRole)
        memberUser.projectUsers.add(projectUser)

        val nonMemberUser = User(id = 11L, loginId = "testuser", name = "테스트유저") // projectUsers가 비어있는 비멤버 유저

        val issue = Issue(id = 5L, title = "이슈 제목", project = project)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val pageRequest = PageRequest.of(0, 20)

        describe("GET /{owner}/{projectName}/issues") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 issue/list 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndState(project, State.OPEN, any<Pageable>()) } returns PageImpl(listOf(issue), pageRequest, 1)
                every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(listOf(issue), pageRequest, 1)
                every { issueRepository.count(any<Specification<Issue>>()) } returns 1L
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 1L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                // 그룹7 #119: 이슈 목록 첫 페이지에서 로그인 본인의 초안 이슈를 조회한다.
                every { issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(any(), any()) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/issues").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("issue/list"))
                    .andExpect(model().attributeExists("project", "issuePage", "state"))
            }

            it("프로젝트 멤버가 아닐 경우 컨텍스트 인지형 403(error/forbidden) 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(nonMemberUser)

                // yona error/forbidden.scala.html 대응 (P-템플릿 #47) — 프로젝트는 이미 찾았으므로
                // 프로젝트 헤더/메뉴가 붙는 컨텍스트 인지형 403(제네릭 error/403이 아니다).
                mockMvc.perform(get("/owner/TestProj/issues").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
                    .andExpect(model().attributeExists("project"))
            }

            // yona IssueApp.java:46,166-177 getItemsPerPage() 대응 (P1-105). [GL-controllers_IssueApp-010]
            it("itemsPerPage를 지정하지 않으면 기본 페이지 크기는 15여야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 1L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                // 그룹7 #119: 이슈 목록 첫 페이지에서 로그인 본인의 초안 이슈를 조회한다.
                every { issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(any(), any()) } returns emptyList()
                val pageableSlot = slot<Pageable>()
                every { issueRepository.findAll(any<Specification<Issue>>(), capture(pageableSlot)) } returns PageImpl(listOf(issue), pageRequest, 1)
                every { issueRepository.count(any<Specification<Issue>>()) } returns 1L

                mockMvc.perform(get("/owner/TestProj/issues").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageSize shouldBe 15
            }

            it("itemsPerPage가 45를 넘게 요청해도 45로 clamp되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 1L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                // 그룹7 #119: 이슈 목록 첫 페이지에서 로그인 본인의 초안 이슈를 조회한다.
                every { issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(any(), any()) } returns emptyList()
                val pageableSlot = slot<Pageable>()
                every { issueRepository.findAll(any<Specification<Issue>>(), capture(pageableSlot)) } returns PageImpl(listOf(issue), pageRequest, 1)
                every { issueRepository.count(any<Specification<Issue>>()) } returns 1L

                mockMvc.perform(get("/owner/TestProj/issues").param("itemsPerPage", "999").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageSize shouldBe 45
            }
        }

        describe("GET /{owner}/{projectName}/issue/{number}") {
            it("프로젝트 멤버가 이슈 조회를 요청하면 200 OK와 issue/view 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { favoriteIssueRepository.findByUserIdAndIssueId(10L, 5L) } returns Optional.empty()
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
                // 그룹7 #127: 인라인 마일스톤 수정 위젯용 open/closed 마일스톤 목록.
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("issue/view"))
                    .andExpect(model().attributeExists("project", "issue", "comments", "currentUser", "isWatching", "isWatchingProject"))
            }

            // yona Issue.getTimeline() 대응 (P1-106) — 댓글+IssueEvent를 시간순으로 병합하고
            // ISSUE_BODY_CHANGED는 화면에서 제외한다.
            it("timeline 모델 속성에 댓글과 이벤트가 시간순으로 병합되고 ISSUE_BODY_CHANGED는 제외되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                val comment = IssueComment(
                    id = 300L, contents = "댓글", issue = issue,
                    createdDate = Instant.parse("2026-01-01T00:00:00Z")
                )
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns listOf(comment)
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { favoriteIssueRepository.findByUserIdAndIssueId(10L, 5L) } returns Optional.empty()
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()

                val stateEvent = IssueEvent(
                    id = 1L, issue = issue, senderLoginId = "testuser",
                    eventType = EventType.ISSUE_STATE_CHANGED,
                    oldValue = "OPEN", newValue = "CLOSED",
                    created = Instant.parse("2026-01-02T00:00:00Z")
                )
                val bodyChangedEvent = IssueEvent(
                    id = 2L, issue = issue, senderLoginId = "testuser",
                    eventType = EventType.ISSUE_BODY_CHANGED,
                    oldValue = "이전 본문", newValue = "새 본문",
                    created = Instant.parse("2026-01-03T00:00:00Z")
                )
                every { issueEventRepository.findByIssueOrderByCreatedAsc(issue) } returns listOf(stateEvent, bodyChangedEvent)
                // 그룹7 #127: 인라인 마일스톤 수정 위젯용 open/closed 마일스톤 목록.
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val timeline = result.modelAndView!!.model["timeline"] as List<*>
                timeline.size shouldBe 2
                val kinds = timeline.map { (it as IssueTimelineItem).kind }
                kinds shouldBe listOf("COMMENT", "EVENT")
            }
        }
        describe("GET /user/issues/new") {
            it("commentId가 주어지면 해당 댓글을 조회하고 레퍼런스 본문 및 ISSUE_TEMPLATE을 포함하여 200 OK를 반환해야 한다") {
                val recentProject = RecentProject(id = 1L, userId = 10L, projectId = 1L)
                every { recentProjectRepository.findByUserIdOrderByVisitedDateDesc(10L) } returns listOf(recentProject)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                val mockComment = mockk<IssueComment>()
                every { mockComment.id } returns 200L
                every { mockComment.contents } returns "댓글 원본 내용"
                every { mockComment.authorLoginId } returns "commenter"
                every { mockComment.issue } returns issue
                every { issueCommentRepository.findById(200L) } returns Optional.of(mockComment)

                val mockPlayRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns mockPlayRepo
                every { mockPlayRepo.getRawFile("HEAD", "ISSUE_TEMPLATE.md") } returns "템플릿 내용".toByteArray()

                every { milestoneService.getMilestones(1L, State.OPEN) } returns emptyList()
                every { projectUserRepository.findByProjectId(1L) } returns emptyList()
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueLabelRepository.findByProject(project) } returns emptyList()
                // 그룹7 #125: 부모 이슈 후보군 — 상태 무관, 최신순 최대 300건.
                every { issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(any(), any()) } returns emptyList()

                mockMvc.perform(get("/user/issues/new").param("commentId", "200").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("issue/create"))
                    .andExpect(model().attributeExists("project", "issueTemplate"))
            }
        }

        // yona Attachment.moveOnlySelected() 대응 (P0-22) — 요청받은 첨부파일 ID를 검증 없이 그대로
        // 재배선하지 않고, 실제로 이 로그인 사용자가 업로드한 임시 첨부만 옮기는지 검증한다.
        describe("POST /{owner}/{projectName}/issues - 임시 업로드 첨부파일 연결") {
            it("temporaryUploadFiles로 넘어온 첨부파일 ID들이 moveOnlySelected를 통해 생성된 이슈로 옮겨져야 한다") {
                val user = User(id = 10L, loginId = "testuser", name = "테스터")
                val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val savedIssue = Issue(id = 100L, number = 5L, title = "제목", body = "본문", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every {
                    issueService.createIssue(any(), any(), any(), any(), any())
                } returns savedIssue
                every {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.ISSUE_POST, "100",
                        listOf(900L, 901L), "testuser"
                    )
                } returns 2

                val auth = UsernamePasswordAuthenticationToken("testuser", "pass")

                val result = issueViewController.createIssue(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "제목",
                    body = "본문",
                    parentIssueId = null,
                    targetProjectId = null,
                    assigneeLoginId = null,
                    milestoneId = null,
                    dueDate = null,
                    labelIds = null,
                    isDraft = false,
                    temporaryUploadFiles = "900,901",
                    authentication = auth,
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/issue/5"
                verify(exactly = 1) {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.ISSUE_POST, "100",
                        listOf(900L, 901L), "testuser"
                    )
                }
            }

            // "moverLoginId = loginUser.loginId ?: \"\"" 엘비스 분기 중 null 쪽은 진짜 도달 불가능한
            // 코드다 — User.loginId는 Kotlin에서 "var loginId: String = \"\""로 non-null 선언되어
            // 있어(User.kt:25), User(loginId = null, ...)을 생성하는 것 자체가 "Null cannot be a
            // value of a non-null type 'String'" 컴파일 에러가 난다. getIssueTemplate()의 bytes
            // null 분기와 동일한 종류의 방어적 죽은 코드다.

            it("temporaryUploadFiles가 없으면 첨부파일 연결 로직 없이도 정상 생성되어야 한다") {
                val user = User(id = 10L, loginId = "testuser", name = "테스터")
                val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val savedIssue = Issue(id = 100L, number = 5L, title = "제목", body = "본문", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every {
                    issueService.createIssue(any(), any(), any(), any(), any())
                } returns savedIssue

                val auth = UsernamePasswordAuthenticationToken("testuser", "pass")

                val result = issueViewController.createIssue(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "제목",
                    body = "본문",
                    parentIssueId = null,
                    targetProjectId = null,
                    assigneeLoginId = null,
                    milestoneId = null,
                    dueDate = null,
                    labelIds = null,
                    isDraft = false,
                    temporaryUploadFiles = null,
                    authentication = auth,
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/issue/5"
            }
        }

        describe("POST /{owner}/{projectName}/issue/{number}/edit") {
            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57) — 작성자 본인도 아니고
            // 직접 프로젝트 멤버도 아니지만, 프로젝트가 속한 조직의 멤버라면 이슈를 수정할 수 있어야 한다.
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 수정에 성공해야 한다") {
                val org = Organization(id = 1L, name = "org")
                val groupUser = User(id = 20L, loginId = "groupuser", name = "그룹멤버")
                org.organizationUsers.add(
                    OrganizationUser(
                        id = 1L, user = groupUser, organization = org,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 7L, name = "GroupProj", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = org)
                val groupIssue = Issue(id = 50L, number = 3L, title = "원제목", authorLoginId = "otherauthor", project = groupProject)
                val auth = UsernamePasswordAuthenticationToken("groupuser", "pass")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GroupProj") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("groupuser") } returns Optional.of(groupUser)
                every { issueRepository.findByProjectAndNumber(groupProject, 3L) } returns groupIssue
                every { projectUserRepository.existsByProjectIdAndUserId(7L, 20L) } returns false
                every {
                    issueService.updateIssue(any(), any(), any(), any(), any(), any(), any())
                } returns groupIssue

                val result = issueViewController.editIssue(
                    owner = "owner",
                    projectName = "GroupProj",
                    number = 3L,
                    request = IssueForm(title = "새 제목", body = "새 본문"),
                    authentication = auth,
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/GroupProj/issue/3"
            }
        }

        // yona IssueApp.massUpdate()의 delete 분기(AbstractPosting.delete()) 대응 (P1-103).
        // 실제 연관 데이터(댓글/이벤트/즐겨찾기/첨부파일/TitleHead) 정리는 P0-19로 IssueServiceImpl
        // .deleteIssueCascade()에 위임되며(검증은 IssueServiceSpec 참고), 여기서는 위임 호출만 검증한다.
        describe("POST /{owner}/{projectName}/issues/massupdate (delete=true)") {
            it("일괄삭제 대상 이슈마다 issueService.deleteIssueCascade가 호출되어야 한다") {
                val toDelete = Issue(id = 7L, number = 7L, title = "[Bug] 지울 이슈", body = "본문", project = project, authorId = memberUser.id, state = State.OPEN)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(7L)) } returns listOf(toDelete)
                every { issueService.deleteIssueCascade(toDelete) } returns Unit

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 7L })

                issueViewController.massUpdate(
                    owner = "owner",
                    projectName = "TestProj",
                    form = form,
                    authentication = userAuth,
                    delete = true,
                    isDueDateChanged = false,
                    dueDate = null,
                    model = ExtendedModelMap()
                )

                verify(exactly = 1) { issueService.deleteIssueCascade(toDelete) }
            }
        }

        describe("GET /{owner}/{projectName}/issues - 추가 분기") {
            it("프로젝트를 찾을 수 없으면 컨텍스트 없는 404(error/404) 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuch/issues"))
                    .andExpect(view().name("error/404"))
            }

            it("익명 사용자가 공개 프로젝트를 조회하면 퀵링크 카운트와 초안 목록 없이 200을 반환해야 한다") {
                val publicProject = Project(id = 2L, name = "PubProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PubProj") } returns Optional.of(publicProject)
                every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList(), pageRequest, 0)
                every { issueRepository.countByProjectAndState(publicProject, State.OPEN) } returns 0L
                every { issueRepository.countByProjectAndState(publicProject, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()

                val result = mockMvc.perform(get("/owner/PubProj/issues"))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["currentUser"] shouldBe null
                result.modelAndView!!.model["assignedToMeCount"] shouldBe 0L
                (result.modelAndView!!.model["draftIssues"] as List<*>).size shouldBe 0
                verify(exactly = 0) { issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(any(), any()) }
                verify(exactly = 0) { issueRepository.count(any<Specification<Issue>>()) }
            }

            it("pageNum이 1보다 크면 실제 조회 페이지는 pageNum-1이고 초안 목록은 조회되지 않아야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 0L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                every { issueRepository.count(any<Specification<Issue>>()) } returns 0L
                val pageableSlot = slot<Pageable>()
                every { issueRepository.findAll(any<Specification<Issue>>(), capture(pageableSlot)) } returns PageImpl(emptyList(), pageRequest, 0)

                mockMvc.perform(get("/owner/TestProj/issues").param("pageNum", "3").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageNumber shouldBe 2
                verify(exactly = 0) { issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(any(), any()) }
            }

            it("pageNum이 0 이하이면 실제 조회 페이지는 0이어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 0L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                every { issueRepository.count(any<Specification<Issue>>()) } returns 0L
                every { issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(any(), any()) } returns emptyList()
                val pageableSlot = slot<Pageable>()
                every { issueRepository.findAll(any<Specification<Issue>>(), capture(pageableSlot)) } returns PageImpl(emptyList(), pageRequest, 0)

                mockMvc.perform(get("/owner/TestProj/issues").param("pageNum", "0").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageNumber shouldBe 0
            }

            it("orderDir=asc이면 오름차순 정렬로 조회해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 0L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                every { issueRepository.count(any<Specification<Issue>>()) } returns 0L
                every { issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(any(), any()) } returns emptyList()
                val pageableSlot = slot<Pageable>()
                every { issueRepository.findAll(any<Specification<Issue>>(), capture(pageableSlot)) } returns PageImpl(emptyList(), pageRequest, 0)

                mockMvc.perform(get("/owner/TestProj/issues").param("orderDir", "asc").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.sort.getOrderFor("createdDate")!!.direction shouldBe Sort.Direction.ASC
            }

            it("format=xls로 요청하면 엑셀 파일을 다운로드 응답으로 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAll(any<Specification<Issue>>()) } returns listOf(issue)
                every { issueExcelService.excelFrom(listOf(issue)) } returns "excel-bytes".toByteArray()

                mockMvc.perform(get("/owner/TestProj/issues").param("format", "xls").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.ms-excel"))
            }

            it("authorId 등 검색조건이 있으면 초안 목록을 조회하지 않아야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 0L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                every { issueRepository.count(any<Specification<Issue>>()) } returns 0L
                every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList(), pageRequest, 0)

                mockMvc.perform(get("/owner/TestProj/issues").param("authorId", "10").principal(userAuth))
                    .andExpect(status().isOk)

                verify(exactly = 0) { issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(any(), any()) }
            }

            // hasCondition = authorId != null || assigneeId != null || commenterId != null 의
            // 나머지 두 조건(assigneeId, commenterId)만 참인 경우도 초안 목록이 생략되어야 한다.
            it("assigneeId만 있어도 초안 목록을 조회하지 않아야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 0L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                every { issueRepository.count(any<Specification<Issue>>()) } returns 0L
                every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList(), pageRequest, 0)

                mockMvc.perform(get("/owner/TestProj/issues").param("assigneeId", "10").principal(userAuth))
                    .andExpect(status().isOk)

                verify(exactly = 0) { issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(any(), any()) }
            }

            it("commenterId만 있어도 초안 목록을 조회하지 않아야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 0L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                every { issueRepository.count(any<Specification<Issue>>()) } returns 0L
                every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList(), pageRequest, 0)

                mockMvc.perform(get("/owner/TestProj/issues").param("commenterId", "10").principal(userAuth))
                    .andExpect(status().isOk)

                verify(exactly = 0) { issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(any(), any()) }
            }

            it("state=CLOSED이면 초안 목록을 조회하지 않아야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 0L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(any<Long>()) } returns emptyList()
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                every { issueRepository.count(any<Specification<Issue>>()) } returns 0L
                every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList(), pageRequest, 0)

                mockMvc.perform(get("/owner/TestProj/issues").param("state", "CLOSED").principal(userAuth))
                    .andExpect(status().isOk)

                verify(exactly = 0) { issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(any(), any()) }
            }

            // listIssues()의 "val members = projectUsers.map { it.user }" 람다 본문 커버 — 항상
            // 빈 목록만 넘겨서는 map 람다 자체가 실행되지 않는다.
            it("프로젝트 멤버가 있으면 members 모델 속성에 사용자 목록이 채워져야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.countByProjectAndState(project, State.OPEN) } returns 0L
                every { issueRepository.countByProjectAndState(project, State.CLOSED) } returns 0L
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectId(1L) } returns listOf(
                    ProjectUser(id = 900L, user = memberUser, project = project, role = memberRole)
                )
                every { issueLabelRepository.findByProject(any<Project>()) } returns emptyList()
                every { issueRepository.count(any<Specification<Issue>>()) } returns 0L
                every { issueRepository.findByProjectAndAuthorLoginIdAndIsDraftTrueOrderByNumberDesc(any(), any()) } returns emptyList()
                every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList(), pageRequest, 0)

                val result = mockMvc.perform(get("/owner/TestProj/issues").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                @Suppress("UNCHECKED_CAST")
                val members = result.modelAndView!!.model["members"] as List<*>
                members shouldBe listOf(memberUser)
            }
        }

        describe("GET /{owner}/{projectName}/issue/{number} - 추가 분기") {
            it("프로젝트를 찾을 수 없으면 404여야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuch/issue/1"))
                    .andExpect(view().name("error/404"))
            }

            it("비멤버가 조회하면 컨텍스트 인지형 403(error/forbidden)을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(nonMemberUser)

                mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            it("이슈를 찾을 수 없으면 컨텍스트 인지형 404(error/notfound)를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                mockMvc.perform(get("/owner/TestProj/issue/999").principal(userAuth))
                    .andExpect(view().name("error/notfound"))
                    .andExpect(model().attribute("targetType", "issue_post"))
            }

            it("익명 사용자가 공개 프로젝트 이슈를 조회하면 방문이력 기록 없이 200을 반환해야 한다") {
                val publicProject = Project(id = 3L, name = "PubProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val publicIssue = Issue(id = 30L, title = "공개 이슈", project = publicProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PubProj") } returns Optional.of(publicProject)
                every { issueRepository.findByProjectAndNumber(publicProject, 1L) } returns publicIssue
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(30L) } returns emptyList()
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()

                val result = mockMvc.perform(get("/owner/PubProj/issue/1"))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["currentUser"] shouldBe null
                result.modelAndView!!.model["isWatching"] shouldBe false
                result.modelAndView!!.model["isFavoriteIssue"] shouldBe false
                verify(exactly = 0) { recentIssueService.recordIssueVisit(any(), any()) }
            }

            it("인증은 있지만 사용자를 찾을 수 없으면 익명 취급되어 컨텍스트 인지형 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            it("방문이력 기록 중 예외가 발생해도 이슈 조회는 정상적으로 진행되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { favoriteIssueRepository.findByUserIdAndIssueId(10L, 5L) } returns Optional.empty()
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { recentIssueService.recordIssueVisit(memberUser, issue) } throws RuntimeException("방문이력 저장 실패")

                mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("issue/view"))
            }

            it("즐겨찾기한 이슈라면 isFavoriteIssue가 true여야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { favoriteIssueRepository.findByUserIdAndIssueId(10L, 5L) } returns Optional.of(mockk(relaxed = true))
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["isFavoriteIssue"] shouldBe true
            }

            // buildTimelineModel()의 groupBy { it.parentComment!!.id!! } 분기 커버 — 대댓글이 실제로
            // 존재해야 groupBy 람다 본문이 실행된다(그룹11 #25/#29/#30/#31 재작업 대응).
            it("대댓글이 있으면 childCommentsByParentId에 부모 댓글 ID 기준으로 그룹핑되어야 한다") {
                val parentComment = IssueComment(id = 400L, contents = "부모 댓글", issue = issue)
                val childComment = IssueComment(id = 401L, contents = "대댓글", issue = issue, parentComment = parentComment)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns listOf(parentComment, childComment)
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { favoriteIssueRepository.findByUserIdAndIssueId(10L, 5L) } returns Optional.empty()
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                @Suppress("UNCHECKED_CAST")
                val grouped = result.modelAndView!!.model["childCommentsByParentId"] as Map<Long, List<*>>
                grouped[400L]?.size shouldBe 1
                // 대댓글은 최상위 타임라인에는 별도 항목으로 나타나지 않는다.
                val timeline = result.modelAndView!!.model["timeline"] as List<*>
                timeline.size shouldBe 1
            }

            // buildTimelineModel()의 isProjectManager 계산에서 Optional.map { ... } 람다 본문
            // (buildTimelineModel$lambda$7) 커버 — findByProjectIdAndUserId가 항상 Optional.empty()면
            // map 람다 자체가 실행되지 않는다. 매니저 역할이면 true가 되어야 한다.
            it("프로젝트 매니저가 조회하면 isProjectManager가 true여야 한다") {
                val managerRole = Role(id = RoleType.MANAGER.roleType, name = "MANAGER")
                val managerProjectUser = ProjectUser(id = 950L, user = memberUser, project = project, role = managerRole)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { favoriteIssueRepository.findByUserIdAndIssueId(10L, 5L) } returns Optional.empty()
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(managerProjectUser)

                val result = mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["isProjectManager"] shouldBe true
            }

            it("일반 멤버(매니저가 아님)가 조회하면 isProjectManager가 false여야 한다") {
                val generalProjectUser = ProjectUser(id = 951L, user = memberUser, project = project, role = memberRole)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { favoriteIssueRepository.findByUserIdAndIssueId(10L, 5L) } returns Optional.empty()
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(generalProjectUser)

                val result = mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["isProjectManager"] shouldBe false
            }

            // Role.id가 Long?(nullable)이라 "it.role.id == RoleType.MANAGER.roleType" 비교에는
            // role.id 자체가 null인 경우의 널 안전 분기도 별도로 존재한다.
            it("역할의 id가 null이면 isProjectManager가 false여야 한다") {
                val roleWithNullId = Role(id = null, name = "미지정역할")
                val projectUserWithNullRoleId = ProjectUser(id = 952L, user = memberUser, project = project, role = roleWithNullId)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { favoriteIssueRepository.findByUserIdAndIssueId(10L, 5L) } returns Optional.empty()
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUserWithNullRoleId)

                val result = mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                result.modelAndView!!.model["isProjectManager"] shouldBe false
            }

            // viewIssue()의 attachmentsJson 조립 람다(viewIssue$lambda$6) 커버 — id/mimeType/size가
            // 채워진 첨부와 전부 null인 첨부를 함께 넣어 엘비스 연산자 양쪽 분기를 모두 실행시킨다.
            it("첨부파일이 있으면 attachmentsJson에 이름/타입/크기 정보가 조립되어야 한다") {
                val fullAttachment = Attachment(
                    id = 900L, name = "파일\"이름\n.txt", hash = "h", containerType = ResourceType.ISSUE_POST,
                    containerId = "5", mimeType = "text/plain", size = 123L
                )
                val emptyAttachment = Attachment(
                    id = null, name = "이름없음", hash = "h2", containerType = ResourceType.ISSUE_POST,
                    containerId = "5", mimeType = null, size = null
                )
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { favoriteIssueRepository.findByUserIdAndIssueId(10L, 5L) } returns Optional.empty()
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, "5") } returns listOf(fullAttachment, emptyAttachment)
                every { milestoneService.getMilestones(any<Long>(), any<State>()) } returns emptyList()

                val result = mockMvc.perform(get("/owner/TestProj/issue/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val attachmentsJson = result.modelAndView!!.model["attachmentsJson"] as String
                attachmentsJson shouldContain "\"id\":\"900\""
                attachmentsJson shouldContain "\"mimeType\":\"text/plain\""
                attachmentsJson shouldContain "\"id\":\"\""
                attachmentsJson shouldContain "\"size\":0"
            }
        }

        describe("GET /{owner}/{projectName}/issue/{number}/timeline") {
            it("프로젝트를 찾을 수 없으면 error/404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

                val result = issueViewController.timeline(
                    owner = "owner", projectName = "NoSuch", number = 1L,
                    authentication = null, model = ExtendedModelMap()
                )

                result shouldBe "error/404"
            }

            it("이슈를 찾을 수 없으면 error/404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                val result = issueViewController.timeline(
                    owner = "owner", projectName = "TestProj", number = 999L,
                    authentication = null, model = ExtendedModelMap()
                )

                result shouldBe "error/404"
            }

            it("읽기 권한이 없으면 error/403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(nonMemberUser)

                val result = issueViewController.timeline(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/403"
            }

            it("인증은 있지만 사용자를 찾을 수 없으면 익명 취급되어 error/403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val result = issueViewController.timeline(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/403"
            }

            // timeline()의 "authentication?.let { ... }" 분기 — 위의 project/issue 미발견 테스트는
            // 모두 이 줄에 도달하기 전에 return되므로, project/issue가 모두 존재하는 상태에서
            // authentication 자체가 null인 케이스를 별도로 커버해야 한다.
            it("익명 사용자(authentication=null)가 비공개 프로젝트 타임라인을 조회하면 error/403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue

                val result = issueViewController.timeline(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    authentication = null, model = ExtendedModelMap()
                )

                result shouldBe "error/403"
            }

            it("정상 조회 시 issue/view :: timelineItems 프래그먼트를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(5L) } returns emptyList()
                val model = ExtendedModelMap()

                val result = issueViewController.timeline(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    authentication = userAuth, model = model
                )

                result shouldBe "issue/view :: timelineItems"
                model.containsAttribute("timeline") shouldBe true
            }
        }

        describe("GET /user/issues/new - 추가 분기") {
            it("로그인하지 않았다면 로그인 폼으로 리다이렉트해야 한다") {
                val result = issueViewController.newDirectIssueForm(
                    commentId = -1L, authentication = null, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/users/loginform"
            }

            it("인증은 있지만 사용자를 찾을 수 없으면 로그인 폼으로 리다이렉트해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val result = issueViewController.newDirectIssueForm(
                    commentId = -1L, authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/users/loginform"
            }

            it("최근 방문 프로젝트도 소속 프로젝트도 없다면 경고 메시지와 함께 홈으로 리다이렉트해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { recentProjectRepository.findByUserIdOrderByVisitedDateDesc(10L) } returns emptyList()
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { messageSource.getMessage("project.is.empty", null, "프로젝트가 존재하지 않습니다.", any()) } returns "프로젝트가 존재하지 않습니다."
                val model = ExtendedModelMap()

                val result = issueViewController.newDirectIssueForm(
                    commentId = -1L, authentication = userAuth, model = model
                )

                result shouldBe "redirect:/"
                model.containsAttribute("warning") shouldBe true
            }

            it("commentId가 -1이면 댓글 조회 없이 진행해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                val recentProject = RecentProject(id = 1L, userId = 10L, projectId = 1L)
                every { recentProjectRepository.findByUserIdOrderByVisitedDateDesc(10L) } returns listOf(recentProject)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { milestoneService.getMilestones(1L, State.OPEN) } returns emptyList()
                every { projectUserRepository.findByProjectId(1L) } returns emptyList()
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueLabelRepository.findByProject(project) } returns emptyList()
                every { issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(any(), any()) } returns emptyList()
                every { repositoryService.getRepository(project) } throws RuntimeException("템플릿 없음")

                val result = issueViewController.newDirectIssueForm(
                    commentId = -1L, authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "issue/create"
                verify(exactly = 0) { issueCommentRepository.findById(any()) }
            }

            it("commentId를 지정했지만 해당 댓글이 없으면 참조 본문 없이 진행해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                val recentProject = RecentProject(id = 1L, userId = 10L, projectId = 1L)
                every { recentProjectRepository.findByUserIdOrderByVisitedDateDesc(10L) } returns listOf(recentProject)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueCommentRepository.findById(777L) } returns Optional.empty()
                every { milestoneService.getMilestones(1L, State.OPEN) } returns emptyList()
                every { projectUserRepository.findByProjectId(1L) } returns emptyList()
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueLabelRepository.findByProject(project) } returns emptyList()
                every { issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(any(), any()) } returns emptyList()
                every { repositoryService.getRepository(project) } throws RuntimeException("템플릿 없음")

                val result = issueViewController.newDirectIssueForm(
                    commentId = 777L, authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "issue/create"
            }

            it("최근 방문 프로젝트가 없어도 소속 프로젝트가 있으면 가장 최근 생성된 프로젝트를 사용해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                val olderProject = Project(id = 20L, name = "Older", owner = "owner", projectScope = ProjectScope.PUBLIC, createdDate = Instant.parse("2025-01-01T00:00:00Z"))
                val newerProject = Project(id = 21L, name = "Newer", owner = "owner", projectScope = ProjectScope.PUBLIC, createdDate = Instant.parse("2026-01-01T00:00:00Z"))
                every { recentProjectRepository.findByUserIdOrderByVisitedDateDesc(10L) } returns emptyList()
                every { projectUserRepository.findByUserId(10L) } returns listOf(
                    ProjectUser(id = 30L, user = memberUser, project = olderProject, role = memberRole),
                    ProjectUser(id = 31L, user = memberUser, project = newerProject, role = memberRole)
                )
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "Newer") } returns Optional.of(newerProject)
                every { milestoneService.getMilestones(21L, State.OPEN) } returns emptyList()
                every { projectUserRepository.findByProjectId(21L) } returns emptyList()
                every { issueLabelRepository.findByProject(newerProject) } returns emptyList()
                every { issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(any(), any()) } returns emptyList()
                every { repositoryService.getRepository(newerProject) } throws RuntimeException("템플릿 없음")

                val result = issueViewController.newDirectIssueForm(
                    commentId = -1L, authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "issue/create"
            }
        }

        describe("GET /user/issues/new/mine") {
            it("로그인하지 않았다면 로그인 폼으로 리다이렉트해야 한다") {
                val result = issueViewController.newDirectMyIssueForm(authentication = null, model = ExtendedModelMap())
                result shouldBe "redirect:/users/loginform"
            }

            it("인증은 있지만 사용자를 찾을 수 없으면 로그인 폼으로 리다이렉트해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val result = issueViewController.newDirectMyIssueForm(authentication = userAuth, model = ExtendedModelMap())

                result shouldBe "redirect:/users/loginform"
            }

            it("inbox 프로젝트가 있으면 그 프로젝트로 이슈 생성 폼을 열어야 한다") {
                val inboxProject = Project(id = 40L, name = "inbox", owner = "testuser", projectScope = ProjectScope.PUBLIC)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectRepository.findByOwnerAndName("testuser", "inbox") } returns Optional.of(inboxProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testuser", "inbox") } returns Optional.of(inboxProject)
                every { milestoneService.getMilestones(40L, State.OPEN) } returns emptyList()
                every { projectUserRepository.findByProjectId(40L) } returns emptyList()
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueLabelRepository.findByProject(inboxProject) } returns emptyList()
                every { issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(any(), any()) } returns emptyList()
                every { repositoryService.getRepository(inboxProject) } throws RuntimeException("템플릿 없음")

                val result = issueViewController.newDirectMyIssueForm(authentication = userAuth, model = ExtendedModelMap())

                result shouldBe "issue/create"
            }

            it("inbox가 없고 _private 프로젝트가 있으면 그 프로젝트를 사용해야 한다") {
                val privateNamedProject = Project(id = 41L, name = "_private", owner = "testuser", projectScope = ProjectScope.PUBLIC)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectRepository.findByOwnerAndName("testuser", "inbox") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "_private") } returns Optional.of(privateNamedProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testuser", "_private") } returns Optional.of(privateNamedProject)
                every { milestoneService.getMilestones(41L, State.OPEN) } returns emptyList()
                every { projectUserRepository.findByProjectId(41L) } returns emptyList()
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueLabelRepository.findByProject(privateNamedProject) } returns emptyList()
                every { issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(any(), any()) } returns emptyList()
                every { repositoryService.getRepository(privateNamedProject) } throws RuntimeException("템플릿 없음")

                val result = issueViewController.newDirectMyIssueForm(authentication = userAuth, model = ExtendedModelMap())

                result shouldBe "issue/create"
            }

            it("inbox/_private가 없으면 PRIVATE 프로젝트 중 최신 생성 프로젝트를 후보로 삼아야 한다") {
                val oldPrivate = Project(id = 42L, name = "old-priv", owner = "testuser", projectScope = ProjectScope.PRIVATE, createdDate = Instant.parse("2025-01-01T00:00:00Z"))
                val newPrivate = Project(id = 43L, name = "new-priv", owner = "testuser", projectScope = ProjectScope.PRIVATE, createdDate = Instant.parse("2026-01-01T00:00:00Z"))
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectRepository.findByOwnerAndName("testuser", "inbox") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "_private") } returns Optional.empty()
                every { projectRepository.findByOwner("testuser") } returns listOf(oldPrivate, newPrivate)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testuser", "new-priv") } returns Optional.of(newPrivate)

                val result = issueViewController.newDirectMyIssueForm(authentication = userAuth, model = ExtendedModelMap())

                // 소유한 PRIVATE 프로젝트 중 최신순으로 선택하는 로직 자체가 검증 대상이다. 다만 실제
                // 이슈 생성 권한(프로젝트 멤버 아님)은 없으므로 createIssueForm은 컨텍스트 인지형 403을 반환한다.
                result shouldBe "error/forbidden"
            }

            it("PRIVATE 프로젝트가 없으면 PUBLIC 프로젝트 중 최신 생성 프로젝트를 사용해야 한다") {
                val oldPublic = Project(id = 44L, name = "old-pub", owner = "testuser", projectScope = ProjectScope.PUBLIC, createdDate = Instant.parse("2025-01-01T00:00:00Z"))
                val newPublic = Project(id = 45L, name = "new-pub", owner = "testuser", projectScope = ProjectScope.PUBLIC, createdDate = Instant.parse("2026-01-01T00:00:00Z"))
                // PUBLIC도 PRIVATE도 아닌(PROTECTED) 프로젝트를 섞어 넣어 publicProjects 필터의
                // "제외" 분기(it.projectScope == PUBLIC이 false인 경우)도 실행되도록 한다.
                val protectedProject = Project(id = 46L, name = "protected-proj", owner = "testuser", projectScope = ProjectScope.PROTECTED, createdDate = Instant.parse("2026-06-01T00:00:00Z"))
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectRepository.findByOwnerAndName("testuser", "inbox") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "_private") } returns Optional.empty()
                every { projectRepository.findByOwner("testuser") } returns listOf(oldPublic, newPublic, protectedProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("testuser", "new-pub") } returns Optional.of(newPublic)
                every { milestoneService.getMilestones(45L, State.OPEN) } returns emptyList()
                every { projectUserRepository.findByProjectId(45L) } returns emptyList()
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueLabelRepository.findByProject(newPublic) } returns emptyList()
                every { issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(any(), any()) } returns emptyList()
                every { repositoryService.getRepository(newPublic) } throws RuntimeException("템플릿 없음")

                val result = issueViewController.newDirectMyIssueForm(authentication = userAuth, model = ExtendedModelMap())

                result shouldBe "issue/create"
            }

            it("소유한 프로젝트가 전혀 없으면 홈으로 리다이렉트해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectRepository.findByOwnerAndName("testuser", "inbox") } returns Optional.empty()
                every { projectRepository.findByOwnerAndName("testuser", "_private") } returns Optional.empty()
                every { projectRepository.findByOwner("testuser") } returns emptyList()

                val result = issueViewController.newDirectMyIssueForm(authentication = userAuth, model = ExtendedModelMap())

                result shouldBe "redirect:/"
            }
        }

        describe("GET /{owner}/{projectName}/issueform - 추가 분기") {
            it("프로젝트를 찾을 수 없으면 404여야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

                val result = issueViewController.createIssueForm(
                    owner = "owner", projectName = "NoSuch", parentIssueId = null,
                    isFromGlobalMenuNew = false, bodyText = null,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/404"
            }

            it("이슈 생성 권한이 없으면 컨텍스트 인지형 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(nonMemberUser)

                val result = issueViewController.createIssueForm(
                    owner = "owner", projectName = "TestProj", parentIssueId = null,
                    isFromGlobalMenuNew = false, bodyText = null,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("authentication이 null이면 SecurityContextHolder의 인증 정보를 사용해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { milestoneService.getMilestones(1L, State.OPEN) } returns emptyList()
                every { projectUserRepository.findByProjectId(1L) } returns emptyList()
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueLabelRepository.findByProject(project) } returns emptyList()
                every { issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(any(), any()) } returns emptyList()
                every { repositoryService.getRepository(project) } throws RuntimeException("템플릿 없음")

                SecurityContextHolder.getContext().authentication = userAuth
                try {
                    val result = issueViewController.createIssueForm(
                        owner = "owner", projectName = "TestProj", parentIssueId = null,
                        isFromGlobalMenuNew = false, bodyText = null,
                        authentication = null, model = ExtendedModelMap()
                    )

                    result shouldBe "issue/create"
                } finally {
                    SecurityContextHolder.clearContext()
                }
            }

            it("authentication과 SecurityContextHolder 모두 없으면 로그인하지 않은 사용자로 취급되어 컨텍스트 인지형 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                SecurityContextHolder.clearContext()
                val result = issueViewController.createIssueForm(
                    owner = "owner", projectName = "TestProj", parentIssueId = null,
                    isFromGlobalMenuNew = false, bodyText = null,
                    authentication = null, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("parentIssueId가 주어지면 부모 이슈 정보를 모델에 담아야 한다") {
                val parent = Issue(id = 6L, number = 2L, title = "부모 이슈", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { milestoneService.getMilestones(1L, State.OPEN) } returns emptyList()
                every { projectUserRepository.findByProjectId(1L) } returns emptyList()
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueLabelRepository.findByProject(project) } returns emptyList()
                every { issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(any(), any()) } returns emptyList()
                every { issueRepository.findById(6L) } returns Optional.of(parent)
                every { repositoryService.getRepository(project) } throws RuntimeException("템플릿 없음")
                val model = ExtendedModelMap()

                val result = issueViewController.createIssueForm(
                    owner = "owner", projectName = "TestProj", parentIssueId = 6L,
                    isFromGlobalMenuNew = false, bodyText = null,
                    authentication = userAuth, model = model
                )

                result shouldBe "issue/create"
                model["parentIssue"] shouldBe parent
            }

            // createIssueForm()의 "val members = projectUsers.map { it.user }"와
            // "val labelMap = labels.groupBy { it.category }" 람다 본문 커버 — 항상 빈 목록만
            // 넘겨서는 두 람다 모두 실행되지 않는다.
            it("프로젝트 멤버와 라벨이 있으면 members/labelMap 모델 속성이 채워져야 한다") {
                val category = mockk<IssueLabelCategory>(relaxed = true)
                val label = IssueLabel(id = 210L, category = category, name = "버그", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { milestoneService.getMilestones(1L, State.OPEN) } returns emptyList()
                every { projectUserRepository.findByProjectId(1L) } returns listOf(
                    ProjectUser(id = 901L, user = memberUser, project = project, role = memberRole)
                )
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueLabelRepository.findByProject(project) } returns listOf(label)
                every { issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(any(), any()) } returns emptyList()
                every { repositoryService.getRepository(project) } throws RuntimeException("템플릿 없음")
                val model = ExtendedModelMap()

                val result = issueViewController.createIssueForm(
                    owner = "owner", projectName = "TestProj", parentIssueId = null,
                    isFromGlobalMenuNew = false, bodyText = null,
                    authentication = userAuth, model = model
                )

                result shouldBe "issue/create"
                (model["members"] as List<*>) shouldBe listOf(memberUser)
                @Suppress("UNCHECKED_CAST")
                val labelMap = model["labelMap"] as Map<*, List<*>>
                labelMap[category] shouldBe listOf(label)
            }

            // getIssueTemplate()의 "if (bytes != null) ... else \"\"" 중 null 분기는 진짜 도달
            // 불가능한 코드다 — PlayRepository.getRawFile()이 Kotlin에서 non-null ByteArray를
            // 반환하도록 선언되어 있어(getRawFile(revision, path): ByteArray), mockk로 null을
            // 리턴하도록 스텁하는 것조차 "Null cannot be a value of a non-null type 'ByteArray'"
            // 컴파일 에러가 난다. 실제 구현체(GitRepository/SvnRepository)도 항상 ByteArray를
            // 반환하므로 프로덕션 경로에서 이 else 분기에 도달할 방법이 없다. 대신 예외 발생 시의
            // catch 분기(빈 문자열 폴백)는 다른 여러 테스트에서 이미 커버하고 있다.
        }

        describe("GET /{owner}/{projectName}/issue/{number}/editform - 추가 분기") {
            it("프로젝트를 찾을 수 없으면 404여야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

                val result = issueViewController.editIssueForm(
                    owner = "owner", projectName = "NoSuch", number = 1L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/404"
            }

            it("이슈를 찾을 수 없으면 컨텍스트 인지형 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                val result = issueViewController.editIssueForm(
                    owner = "owner", projectName = "TestProj", number = 999L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/notfound"
            }

            it("수정 권한이 없으면 컨텍스트 인지형 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(nonMemberUser)

                val result = issueViewController.editIssueForm(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("로그인하지 않았다면(authentication=null) 컨텍스트 인지형 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue

                val result = issueViewController.editIssueForm(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    authentication = null, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            // editIssueForm()의 attachmentsJson 조립 람다(editIssueForm$lambda$7)와 hasChildIssue=true
            // 분기, parentCandidates에서 자기 자신을 제외하는 필터를 함께 커버한다.
            it("이미 하위이슈를 가진 이슈를 열람하면 hasChildIssue=true와 첨부파일 정보가 모델에 담겨야 한다") {
                val fullAttachment = Attachment(
                    id = 901L, name = "파일.txt", hash = "h", containerType = ResourceType.ISSUE_POST,
                    containerId = "5", mimeType = "text/plain", size = 10L
                )
                val emptyAttachment = Attachment(
                    id = null, name = "이름없음", hash = "h2", containerType = ResourceType.ISSUE_POST,
                    containerId = "5", mimeType = null, size = null
                )
                val category = mockk<IssueLabelCategory>(relaxed = true)
                val label = IssueLabel(id = 211L, category = category, name = "버그", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { milestoneService.getMilestones(1L, State.OPEN) } returns emptyList()
                // editIssueForm()의 "val members = projectUsers.map { it.user }",
                // "val labelMap = labels.groupBy { it.category }",
                // "val movableProjects = projectUserRepository.findByUserId(...).map { it.project }"
                // 람다 본문 커버 — 빈 목록만 넘겨서는 세 람다 모두 실행되지 않는다.
                every { projectUserRepository.findByProjectId(1L) } returns listOf(
                    ProjectUser(id = 902L, user = memberUser, project = project, role = memberRole)
                )
                every { projectUserRepository.findByUserId(10L) } returns listOf(
                    ProjectUser(id = 903L, user = memberUser, project = project, role = memberRole)
                )
                every { issueLabelRepository.findByProject(project) } returns listOf(label)
                every { issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(any(), any()) } returns listOf(issue)
                every { issueRepository.countByParentId(5L) } returns 2L
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, "5") } returns listOf(fullAttachment, emptyAttachment)
                val model = ExtendedModelMap()

                val result = issueViewController.editIssueForm(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    authentication = userAuth, model = model
                )

                result shouldBe "issue/edit"
                model["hasChildIssue"] shouldBe true
                // 부모 이슈 후보군에서 자기 자신(issue.id=5)은 제외되어야 한다.
                (model["parentCandidates"] as List<*>).isEmpty() shouldBe true
                (model["attachmentsJson"] as String) shouldContain "\"id\":\"901\""
                (model["members"] as List<*>) shouldBe listOf(memberUser)
                @Suppress("UNCHECKED_CAST")
                val labelMap = model["labelMap"] as Map<*, List<*>>
                labelMap[category] shouldBe listOf(label)
                (model["movableProjects"] as List<*>) shouldBe listOf(project)
            }

            // parentCandidates 필터(.filter { it.id != issue.id })의 "다른 이슈는 유지" 분기와
            // hasChildIssue=false 분기를 함께 커버한다(위 테스트는 반대 분기만 커버했다).
            it("하위이슈가 없는 이슈를 열람하면 hasChildIssue=false이고 다른 이슈는 부모 후보로 유지되어야 한다") {
                val otherCandidate = Issue(id = 77L, number = 30L, title = "다른 후보 이슈", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { milestoneService.getMilestones(1L, State.OPEN) } returns emptyList()
                every { projectUserRepository.findByProjectId(1L) } returns emptyList()
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueLabelRepository.findByProject(project) } returns emptyList()
                every { issueRepository.findByProjectAndParentIsNullOrderByCreatedDateDesc(any(), any()) } returns listOf(otherCandidate, issue)
                every { issueRepository.countByParentId(5L) } returns 0L
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
                val model = ExtendedModelMap()

                val result = issueViewController.editIssueForm(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    authentication = userAuth, model = model
                )

                result shouldBe "issue/edit"
                model["hasChildIssue"] shouldBe false
                (model["parentCandidates"] as List<*>) shouldBe listOf(otherCandidate)
            }
        }

        describe("POST /{owner}/{projectName}/issues (createIssue) - 추가 분기") {
            it("프로젝트를 찾을 수 없으면 404여야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

                val result = issueViewController.createIssue(
                    owner = "owner", projectName = "NoSuch", title = "제목", body = "본문",
                    parentIssueId = null, targetProjectId = null, assigneeLoginId = null,
                    milestoneId = null, dueDate = null, labelIds = null, isDraft = false,
                    temporaryUploadFiles = null, authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/404"
            }

            it("로그인하지 않았다면 로그인 폼으로 리다이렉트해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                val result = issueViewController.createIssue(
                    owner = "owner", projectName = "TestProj", title = "제목", body = "본문",
                    parentIssueId = null, targetProjectId = null, assigneeLoginId = null,
                    milestoneId = null, dueDate = null, labelIds = null, isDraft = false,
                    temporaryUploadFiles = null, authentication = null, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/users/loginform"
            }

            it("인증은 있지만 사용자를 찾을 수 없으면 로그인 폼으로 리다이렉트해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val result = issueViewController.createIssue(
                    owner = "owner", projectName = "TestProj", title = "제목", body = "본문",
                    parentIssueId = null, targetProjectId = null, assigneeLoginId = null,
                    milestoneId = null, dueDate = null, labelIds = null, isDraft = false,
                    temporaryUploadFiles = null, authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/users/loginform"
            }

            it("이슈 생성 권한이 없으면 컨텍스트 인지형 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(nonMemberUser)

                val result = issueViewController.createIssue(
                    owner = "owner", projectName = "TestProj", title = "제목", body = "본문",
                    parentIssueId = null, targetProjectId = null, assigneeLoginId = null,
                    milestoneId = null, dueDate = null, labelIds = null, isDraft = false,
                    temporaryUploadFiles = null, authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("dueDate가 빈 문자열이면 null과 마찬가지로 dueDate 없이 진행되어야 한다") {
                val savedIssue = Issue(id = 100L, number = 5L, title = "제목", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                val issueSlot = slot<Issue>()
                every { issueService.createIssue(capture(issueSlot), any(), any(), any(), any()) } returns savedIssue

                issueViewController.createIssue(
                    owner = "owner", projectName = "TestProj", title = "제목", body = "본문",
                    parentIssueId = null, targetProjectId = null, assigneeLoginId = null,
                    milestoneId = null, dueDate = "", labelIds = null, isDraft = false,
                    temporaryUploadFiles = null, authentication = userAuth, model = ExtendedModelMap()
                )

                issueSlot.captured.dueDate shouldBe null
            }

            it("temporaryUploadFiles가 공백 문자열이면 첨부파일 연결 로직 없이 진행되어야 한다") {
                val savedIssue = Issue(id = 100L, number = 5L, title = "제목", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueService.createIssue(any(), any(), any(), any(), any()) } returns savedIssue

                issueViewController.createIssue(
                    owner = "owner", projectName = "TestProj", title = "제목", body = "본문",
                    parentIssueId = null, targetProjectId = null, assigneeLoginId = null,
                    milestoneId = null, dueDate = null, labelIds = null, isDraft = false,
                    temporaryUploadFiles = "   ", authentication = userAuth, model = ExtendedModelMap()
                )

                verify(exactly = 0) { attachmentService.moveOnlySelected(any(), any(), any(), any(), any(), any()) }
            }

            it("parentIssueId가 주어지면 생성되는 이슈의 부모로 연결되어야 한다") {
                val parent = Issue(id = 6L, number = 2L, title = "부모", project = project)
                val savedIssue = Issue(id = 100L, number = 5L, title = "제목", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findById(6L) } returns Optional.of(parent)
                val issueSlot = slot<Issue>()
                every { issueService.createIssue(capture(issueSlot), any(), any(), any(), any()) } returns savedIssue

                issueViewController.createIssue(
                    owner = "owner", projectName = "TestProj", title = "제목", body = "본문",
                    parentIssueId = 6L, targetProjectId = null, assigneeLoginId = null,
                    milestoneId = null, dueDate = null, labelIds = null, isDraft = false,
                    temporaryUploadFiles = null, authentication = userAuth, model = ExtendedModelMap()
                )

                issueSlot.captured.parent shouldBe parent
            }

            it("dueDate가 유효한 형식이면 자정 직전 시각으로 파싱되어야 한다") {
                val savedIssue = Issue(id = 100L, number = 5L, title = "제목", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                val issueSlot = slot<Issue>()
                every { issueService.createIssue(capture(issueSlot), any(), any(), any(), any()) } returns savedIssue

                issueViewController.createIssue(
                    owner = "owner", projectName = "TestProj", title = "제목", body = "본문",
                    parentIssueId = null, targetProjectId = null, assigneeLoginId = null,
                    milestoneId = null, dueDate = "2026-12-31", labelIds = null, isDraft = false,
                    temporaryUploadFiles = null, authentication = userAuth, model = ExtendedModelMap()
                )

                issueSlot.captured.dueDate shouldNotBe null
            }

            it("dueDate 형식이 잘못되면 예외를 삼키고 dueDate 없이 진행되어야 한다") {
                val savedIssue = Issue(id = 100L, number = 5L, title = "제목", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                val issueSlot = slot<Issue>()
                every { issueService.createIssue(capture(issueSlot), any(), any(), any(), any()) } returns savedIssue

                issueViewController.createIssue(
                    owner = "owner", projectName = "TestProj", title = "제목", body = "본문",
                    parentIssueId = null, targetProjectId = null, assigneeLoginId = null,
                    milestoneId = null, dueDate = "잘못된-날짜", labelIds = null, isDraft = false,
                    temporaryUploadFiles = null, authentication = userAuth, model = ExtendedModelMap()
                )

                issueSlot.captured.dueDate shouldBe null
            }

            it("assigneeLoginId로 담당자를 찾으면 createIssue에 담당자로 전달되어야 한다") {
                val savedIssue = Issue(id = 100L, number = 5L, title = "제목", project = project)
                val assignee = User(id = 12L, loginId = "assignee1", name = "담당자")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { userRepository.findByLoginId("assignee1") } returns Optional.of(assignee)
                every { issueService.createIssue(any(), any(), assignee, any(), any()) } returns savedIssue

                val result = issueViewController.createIssue(
                    owner = "owner", projectName = "TestProj", title = "제목", body = "본문",
                    parentIssueId = null, targetProjectId = null, assigneeLoginId = "assignee1",
                    milestoneId = null, dueDate = null, labelIds = null, isDraft = false,
                    temporaryUploadFiles = null, authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/issue/5"
                verify(exactly = 1) { issueService.createIssue(any(), any(), assignee, any(), any()) }
            }

            it("assigneeLoginId에 해당하는 사용자가 없으면 담당자 없이 생성되어야 한다") {
                val savedIssue = Issue(id = 100L, number = 5L, title = "제목", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { userRepository.findByLoginId("nobody") } returns Optional.empty()
                every { issueService.createIssue(any(), any(), null, any(), any()) } returns savedIssue

                issueViewController.createIssue(
                    owner = "owner", projectName = "TestProj", title = "제목", body = "본문",
                    parentIssueId = null, targetProjectId = null, assigneeLoginId = "nobody",
                    milestoneId = null, dueDate = null, labelIds = null, isDraft = false,
                    temporaryUploadFiles = null, authentication = userAuth, model = ExtendedModelMap()
                )

                verify(exactly = 1) { issueService.createIssue(any(), any(), null, any(), any()) }
            }

            it("isDraft=true이면 State.DRAFT로 생성되어야 한다") {
                val savedIssue = Issue(id = 100L, number = 5L, title = "제목", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                val issueSlot = slot<Issue>()
                every { issueService.createIssue(capture(issueSlot), any(), any(), any(), any()) } returns savedIssue

                issueViewController.createIssue(
                    owner = "owner", projectName = "TestProj", title = "제목", body = "본문",
                    parentIssueId = null, targetProjectId = null, assigneeLoginId = null,
                    milestoneId = null, dueDate = null, labelIds = null, isDraft = true,
                    temporaryUploadFiles = null, authentication = userAuth, model = ExtendedModelMap()
                )

                issueSlot.captured.state shouldBe State.DRAFT
                issueSlot.captured.isDraft shouldBe true
            }
        }

        describe("POST /{owner}/{projectName}/issue/{number}/edit - 추가 분기") {
            it("프로젝트를 찾을 수 없으면 404여야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

                val result = issueViewController.editIssue(
                    owner = "owner", projectName = "NoSuch", number = 1L,
                    request = IssueForm(title = "제목", body = "본문"),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/404"
            }

            it("로그인하지 않았다면 컨텍스트 인지형 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                val result = issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    request = IssueForm(title = "제목", body = "본문"),
                    authentication = null, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("인증은 있지만 사용자를 찾을 수 없으면 컨텍스트 인지형 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val result = issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    request = IssueForm(title = "제목", body = "본문"),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("이슈를 찾을 수 없으면 컨텍스트 인지형 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 999L) } returns null

                val result = issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 999L,
                    request = IssueForm(title = "제목", body = "본문"),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/notfound"
            }

            it("작성자 본인이면 직접 멤버가 아니어도 수정에 성공해야 한다") {
                val authoredIssue = Issue(id = 8L, number = 4L, title = "원제목", authorLoginId = "testuser", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(nonMemberUser)
                every { issueRepository.findByProjectAndNumber(project, 4L) } returns authoredIssue
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 11L) } returns false
                every { issueService.updateIssue(any(), any(), any(), any(), any(), any(), any()) } returns authoredIssue

                val result = issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 4L,
                    request = IssueForm(title = "새 제목", body = "새 본문"),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/issue/4"
            }

            it("직접 프로젝트 멤버라면 작성자가 아니어도 수정에 성공해야 한다") {
                val otherIssue = Issue(id = 9L, number = 9L, title = "원제목", authorLoginId = "someoneelse", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 9L) } returns otherIssue
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueService.updateIssue(any(), any(), any(), any(), any(), any(), any()) } returns otherIssue

                val result = issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 9L,
                    request = IssueForm(title = "새 제목", body = "새 본문"),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/issue/9"
            }

            it("작성자도 멤버도 그룹멤버도 아니면 컨텍스트 인지형 403을 반환해야 한다") {
                val otherIssue = Issue(id = 9L, number = 9L, title = "원제목", authorLoginId = "someoneelse", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(nonMemberUser)
                every { issueRepository.findByProjectAndNumber(project, 9L) } returns otherIssue
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 11L) } returns false

                val result = issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 9L,
                    request = IssueForm(title = "새 제목", body = "새 본문"),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("assigneeLoginId가 공백이면 담당자를 조회하지 않아야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueService.updateIssue(any(), any(), any(), any(), any(), any(), any()) } returns issue

                issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    request = IssueForm(title = "새 제목", body = "새 본문", assigneeLoginId = "   "),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                verify(exactly = 0) { userRepository.findByLoginId("   ") }
            }

            it("assigneeLoginId가 유효하면 담당자를 조회해야 한다") {
                val assignee = User(id = 13L, loginId = "assignee2", name = "담당자2")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue
                every { userRepository.findByLoginId("assignee2") } returns Optional.of(assignee)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueService.updateIssue(any(), any(), any(), any(), any(), any(), any()) } returns issue

                issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    request = IssueForm(title = "새 제목", body = "새 본문", assigneeLoginId = "assignee2"),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                verify(exactly = 1) { userRepository.findByLoginId("assignee2") }
            }

            it("dueDate가 유효한 형식이면 파싱되어 저장되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                val localIssue = Issue(id = 5L, number = 1L, title = "이슈 제목", authorLoginId = "testuser", project = project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns localIssue
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueService.updateIssue(any(), any(), any(), any(), any(), any(), any()) } returns localIssue

                issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    request = IssueForm(title = "새 제목", body = "새 본문", dueDate = "2026-12-31"),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                localIssue.dueDate shouldNotBe null
            }

            it("dueDate 형식이 잘못되면 예외를 삼키고 넘어가야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                val localIssue = Issue(id = 5L, number = 1L, title = "이슈 제목", authorLoginId = "testuser", project = project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns localIssue
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueService.updateIssue(any(), any(), any(), any(), any(), any(), any()) } returns localIssue

                val result = issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    request = IssueForm(title = "새 제목", body = "새 본문", dueDate = "잘못된-날짜"),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/issue/1"
            }

            // request.dueDate가 빈 문자열("")이면(null이 아니지만 비어있음) isNullOrBlank()가 true가
            // 되어 dueDate가 null로 초기화되는 분기(dueDate=null과는 별도 분기)를 커버한다.
            it("dueDate가 빈 문자열이면 마감일이 null로 초기화되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                val localIssue = Issue(id = 5L, number = 1L, title = "이슈 제목", authorLoginId = "testuser", project = project, dueDate = Instant.parse("2026-01-01T00:00:00Z"))
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns localIssue
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueService.updateIssue(any(), any(), any(), any(), any(), any(), any()) } returns localIssue

                issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    request = IssueForm(title = "새 제목", body = "새 본문", dueDate = ""),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                localIssue.dueDate shouldBe null
            }

            // "issue.body = request.body ?: \"\"" 엘비스 분기 — body가 null로 전달되는 경우도
            // 커버한다(다른 모든 테스트는 항상 non-null body를 사용했다).
            it("body가 null이면 빈 문자열로 저장되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                val localIssue = Issue(id = 5L, number = 1L, title = "이슈 제목", authorLoginId = "testuser", project = project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns localIssue
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueService.updateIssue(any(), any(), "", any(), any(), any(), any()) } returns localIssue

                issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    request = IssueForm(title = "새 제목", body = null),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                localIssue.body shouldBe ""
            }

            it("parentIssueId가 주어지면 부모 이슈로 연결되어야 한다") {
                val parent = Issue(id = 6L, number = 2L, title = "부모", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                val localIssue = Issue(id = 5L, number = 1L, title = "이슈 제목", authorLoginId = "testuser", project = project)
                every { issueRepository.findByProjectAndNumber(project, 1L) } returns localIssue
                every { issueRepository.findById(6L) } returns Optional.of(parent)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { issueService.updateIssue(any(), any(), any(), any(), any(), any(), any()) } returns localIssue

                issueViewController.editIssue(
                    owner = "owner", projectName = "TestProj", number = 1L,
                    request = IssueForm(title = "새 제목", body = "새 본문", parentIssueId = 6L),
                    authentication = userAuth, model = ExtendedModelMap()
                )

                localIssue.parent shouldBe parent
            }
        }

        describe("POST /{owner}/{projectName}/issues/massupdate - 추가 분기") {
            it("프로젝트를 찾을 수 없으면 비JSON 요청은 error/404 뷰를, JSON 요청은 404 상태코드를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuch") } returns Optional.empty()

                val htmlResult = issueViewController.massUpdate(
                    owner = "owner", projectName = "NoSuch", form = IssueMassUpdateForm(),
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )
                htmlResult shouldBe "error/404"

                val jsonResult = issueViewController.massUpdate(
                    owner = "owner", projectName = "NoSuch", form = IssueMassUpdateForm(),
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = "application/json", model = ExtendedModelMap()
                ) as ResponseEntity<*>
                jsonResult.statusCode.value() shouldBe 404
            }

            it("로그인하지 않았다면 비JSON 요청은 컨텍스트 인지형 403 뷰를, JSON 요청은 403 상태코드를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                val htmlResult = issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = IssueMassUpdateForm(),
                    authentication = null, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )
                htmlResult shouldBe "error/forbidden"

                val jsonResult = issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = IssueMassUpdateForm(),
                    authentication = null, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = "application/json", model = ExtendedModelMap()
                ) as ResponseEntity<*>
                jsonResult.statusCode.value() shouldBe 403
            }

            it("대상 이슈 ID가 없으면 아무 것도 갱신하지 않고 성공 리다이렉트해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)

                val result = issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = IssueMassUpdateForm(),
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/issues"
                verify(exactly = 0) { issueRepository.findAllById(any<List<Long>>()) }
            }

            // wantsJson = accept?.contains("application/json") == true 분기 커버 — accept 헤더가
            // 아예 없는 경우(null)와는 다르게, JSON을 포함하지 않는 다른 Accept 값(text/html)이 와도
            // wantsJson은 false여야 한다.
            it("Accept 헤더가 application/json을 포함하지 않으면 비JSON 응답(리다이렉트)이어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)

                val result = issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = IssueMassUpdateForm(),
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = "text/html", model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/issues"
            }

            it("다른 프로젝트 소속 이슈와 초안 이슈는 건너뛰고, 나머지만 정상 갱신되어야 한다") {
                val otherProject = Project(id = 2L, name = "Other", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val foreignIssue = Issue(id = 70L, number = 1L, title = "다른 프로젝트 이슈", project = otherProject, authorLoginId = "testuser")
                val draftIssue = Issue(id = 71L, number = 2L, title = "초안", project = project, authorLoginId = "testuser").apply { isDraft = true }
                val normalIssue = Issue(id = 72L, number = 3L, title = "정상 이슈", project = project, authorLoginId = "testuser")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(70L, 71L, 72L)) } returns listOf(foreignIssue, draftIssue, normalIssue)

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 70L }, IssueIdForm().apply { id = 71L }, IssueIdForm().apply { id = 72L })

                val result = issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/issues"
                verify(exactly = 0) { issueRepository.save(foreignIssue) }
                verify(exactly = 0) { issueRepository.save(draftIssue) }
            }

            it("delete=false에서 수정 권한이 없는 이슈는 rejectedByPermission으로만 집계되고, 전부 거부되면 403을 반환해야 한다") {
                val forbiddenIssue = Issue(id = 73L, number = 4L, title = "권한없음", project = project, authorLoginId = "otherauthor")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(nonMemberUser)
                every { issueRepository.findAllById(listOf(73L)) } returns listOf(forbiddenIssue)

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 73L })

                val htmlResult = issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )
                htmlResult shouldBe "redirect:/error/403"

                val jsonResult = issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = "application/json", model = ExtendedModelMap()
                ) as ResponseEntity<*>
                jsonResult.statusCode.value() shouldBe 403
            }

            it("delete=true에서 수정 권한이 없으면 삭제되지 않고 rejectedByPermission으로 집계되어야 한다") {
                val forbiddenIssue = Issue(id = 74L, number = 5L, title = "권한없음", project = project, authorLoginId = "otherauthor")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(nonMemberUser)
                every { issueRepository.findAllById(listOf(74L)) } returns listOf(forbiddenIssue)

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 74L })

                val result = issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = true, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/error/403"
                verify(exactly = 0) { issueService.deleteIssueCascade(forbiddenIssue) }
            }

            it("state 값이 유효하면 changeState가 호출되어야 한다") {
                val target1 = Issue(id = 75L, number = 6L, title = "이슈1", project = project, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(75L)) } returns listOf(target1)
                every { issueService.changeState(75L, State.CLOSED, "testuser") } returns target1

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 75L })
                form.state = "closed"

                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )

                verify(exactly = 1) { issueService.changeState(75L, State.CLOSED, "testuser") }
            }

            it("state 값이 잘못된 문자열이면 changeState를 호출하지 않고 예외를 삼켜야 한다") {
                val target1 = Issue(id = 76L, number = 7L, title = "이슈1", project = project, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(76L)) } returns listOf(target1)

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 76L })
                form.state = "존재하지않는상태"

                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )

                verify(exactly = 0) { issueService.changeState(any(), any(), any()) }
            }

            it("담당자 id가 -1이면 담당자 해제, 유효하면 해당 사용자로 배정되어야 한다") {
                val target1 = Issue(id = 77L, number = 8L, title = "이슈1", project = project, authorLoginId = "testuser")
                val target2 = Issue(id = 78L, number = 9L, title = "이슈2", project = project, authorLoginId = "testuser")
                val assignee = User(id = 99L, loginId = "assignee3", name = "담당자3")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(77L)) } returns listOf(target1)
                every { issueRepository.findAllById(listOf(78L)) } returns listOf(target2)
                every { issueService.changeAssignee(77L, null, "testuser") } returns target1
                every { issueService.changeAssignee(78L, assignee, "testuser") } returns target2
                every { userRepository.findById(99L) } returns Optional.of(assignee)

                val unassignForm = IssueMassUpdateForm()
                unassignForm.issues = listOf(IssueIdForm().apply { id = 77L })
                unassignForm.assignee = AssigneeIdForm().apply { id = -1L }
                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = unassignForm,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )
                verify(exactly = 1) { issueService.changeAssignee(77L, null, "testuser") }

                val assignForm = IssueMassUpdateForm()
                assignForm.issues = listOf(IssueIdForm().apply { id = 78L })
                assignForm.assignee = AssigneeIdForm().apply { id = 99L }
                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = assignForm,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )
                verify(exactly = 1) { issueService.changeAssignee(78L, assignee, "testuser") }
            }

            it("담당자 id가 유효하지만 사용자를 찾을 수 없으면 changeAssignee를 호출하지 않아야 한다") {
                val target1 = Issue(id = 79L, number = 10L, title = "이슈1", project = project, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(79L)) } returns listOf(target1)
                every { userRepository.findById(1234L) } returns Optional.empty()

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 79L })
                form.assignee = AssigneeIdForm().apply { id = 1234L }

                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )

                verify(exactly = 0) { issueService.changeAssignee(any(), any(), any()) }
            }

            // "assigneeUserId == null || assigneeUserId == -1L" 중 id 필드 자체가 비어 있는(null)
            // 경우(-1L 산탄이 아닌) 경로도 담당자 해제로 처리되어야 한다.
            it("담당자 id 필드가 비어 있으면(null) 담당자 해제로 처리되어야 한다") {
                val target = Issue(id = 90L, number = 21L, title = "이슈", project = project, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(90L)) } returns listOf(target)
                every { issueService.changeAssignee(90L, null, "testuser") } returns target

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 90L })
                form.assignee = AssigneeIdForm()

                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )

                verify(exactly = 1) { issueService.changeAssignee(90L, null, "testuser") }
            }

            // "milestoneId == null || milestoneId == -1L" 중 id 필드 자체가 비어 있는(null) 경로도
            // 마일스톤 해제로 처리되어야 한다.
            it("마일스톤 id 필드가 비어 있으면(null) 마일스톤 해제로 처리되어야 한다") {
                val target = Issue(id = 91L, number = 22L, title = "이슈", project = project, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(91L)) } returns listOf(target)
                every { issueService.changeMilestone(91L, null, "testuser") } returns target

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 91L })
                form.milestone = MilestoneIdForm()

                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )

                verify(exactly = 1) { issueService.changeMilestone(91L, null, "testuser") }
            }

            it("마일스톤 id가 -1이면 마일스톤 해제, 유효하면 해당 마일스톤으로 변경되어야 한다") {
                val target1 = Issue(id = 80L, number = 11L, title = "이슈1", project = project, authorLoginId = "testuser")
                val target2 = Issue(id = 81L, number = 12L, title = "이슈2", project = project, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(80L)) } returns listOf(target1)
                every { issueRepository.findAllById(listOf(81L)) } returns listOf(target2)
                every { issueService.changeMilestone(80L, null, "testuser") } returns target1
                every { issueService.changeMilestone(81L, 500L, "testuser") } returns target2

                val clearForm = IssueMassUpdateForm()
                clearForm.issues = listOf(IssueIdForm().apply { id = 80L })
                clearForm.milestone = MilestoneIdForm().apply { id = -1L }
                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = clearForm,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )
                verify(exactly = 1) { issueService.changeMilestone(80L, null, "testuser") }

                val setForm = IssueMassUpdateForm()
                setForm.issues = listOf(IssueIdForm().apply { id = 81L })
                setForm.milestone = MilestoneIdForm().apply { id = 500L }
                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = setForm,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )
                verify(exactly = 1) { issueService.changeMilestone(81L, 500L, "testuser") }
            }

            it("라벨 추가/삭제 ID가 주어지면 issue.labels에 반영되고 저장되어야 한다") {
                val category = mockk<IssueLabelCategory>(relaxed = true)
                val labelA = IssueLabel(id = 200L, category = category, name = "A", project = project)
                val labelB = IssueLabel(id = 201L, category = category, name = "B", project = project)
                val target = Issue(id = 82L, number = 13L, title = "이슈", project = project, authorLoginId = "testuser")
                target.labels.add(labelB)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(82L)) } returns listOf(target)
                every { issueLabelRepository.findAllById(listOf(200L)) } returns listOf(labelA)
                every { issueLabelRepository.findAllById(listOf(201L)) } returns listOf(labelB)
                every { issueRepository.save(target) } returns target

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 82L })
                form.attachingLabelIds = listOf(200L)
                form.detachingLabelIds = listOf(201L)

                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )

                target.labels shouldBe mutableSetOf(labelA)
                verify(exactly = 1) { issueRepository.save(target) }
            }

            it("마감일 변경 시 값이 없으면 null로, 유효하면 파싱된 시각으로, 잘못된 형식이면 변경 없이 저장되어야 한다") {
                val clearTarget = Issue(id = 83L, number = 14L, title = "이슈", project = project, authorLoginId = "testuser", dueDate = Instant.parse("2026-01-01T00:00:00Z"))
                val validTarget = Issue(id = 84L, number = 15L, title = "이슈", project = project, authorLoginId = "testuser")
                val invalidTarget = Issue(id = 85L, number = 16L, title = "이슈", project = project, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.save(any<Issue>()) } answers { firstArg() }

                every { issueRepository.findAllById(listOf(83L)) } returns listOf(clearTarget)
                val clearForm = IssueMassUpdateForm()
                clearForm.issues = listOf(IssueIdForm().apply { id = 83L })
                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = clearForm,
                    authentication = userAuth, delete = false, isDueDateChanged = true, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )
                clearTarget.dueDate shouldBe null

                every { issueRepository.findAllById(listOf(84L)) } returns listOf(validTarget)
                val validForm = IssueMassUpdateForm()
                validForm.issues = listOf(IssueIdForm().apply { id = 84L })
                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = validForm,
                    authentication = userAuth, delete = false, isDueDateChanged = true, dueDate = "2026-12-31",
                    accept = null, model = ExtendedModelMap()
                )
                validTarget.dueDate shouldNotBe null

                every { issueRepository.findAllById(listOf(85L)) } returns listOf(invalidTarget)
                val invalidForm = IssueMassUpdateForm()
                invalidForm.issues = listOf(IssueIdForm().apply { id = 85L })
                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = invalidForm,
                    authentication = userAuth, delete = false, isDueDateChanged = true, dueDate = "잘못된-날짜",
                    accept = null, model = ExtendedModelMap()
                )
                invalidTarget.dueDate shouldBe null
                verify(exactly = 3) { issueRepository.save(any<Issue>()) }
            }

            it("isDueDateChanged=false이면 마감일이 변경되지 않고 저장도 호출되지 않아야 한다") {
                val target = Issue(id = 86L, number = 17L, title = "이슈", project = project, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(86L)) } returns listOf(target)

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 86L })

                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = "2026-12-31",
                    accept = null, model = ExtendedModelMap()
                )

                verify(exactly = 0) { issueRepository.save(any<Issue>()) }
            }

            it("JSON 응답 요청이고 마감일이 변경되었으면 연체 여부와 메시지를 함께 반환해야 한다(연체)") {
                val overdueTarget = Issue(id = 87L, number = 18L, title = "이슈", project = project, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(87L)) } returns listOf(overdueTarget)
                every { issueRepository.save(any<Issue>()) } answers { firstArg() }
                every { issueRepository.findById(87L) } returns Optional.of(overdueTarget)
                every { templateHelper.isOverDueDate(overdueTarget) } returns true
                every { messageSource.getMessage("issue.dueDate.overdue", null, any()) } returns "기한이 지났습니다"

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 87L })

                val result = issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = true, dueDate = "2026-12-31",
                    accept = "application/json", model = ExtendedModelMap()
                ) as ResponseEntity<*>

                @Suppress("UNCHECKED_CAST")
                val body = result.body as Map<String, Any>
                body["isOverDue"] shouldBe true
                body["dueDateMsg"] shouldBe "기한이 지났습니다"
            }

            it("JSON 응답 요청이고 연체가 아니면 남은 기간 문자열을 반환해야 한다") {
                val notOverdueTarget = Issue(id = 88L, number = 19L, title = "이슈", project = project, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(88L)) } returns listOf(notOverdueTarget)
                every { issueRepository.save(any<Issue>()) } answers { firstArg() }
                every { issueRepository.findById(88L) } returns Optional.of(notOverdueTarget)
                every { templateHelper.isOverDueDate(notOverdueTarget) } returns false
                every { templateHelper.until(notOverdueTarget) } returns "3일 남음"

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 88L })

                val result = issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = true, dueDate = "2026-12-31",
                    accept = "application/json", model = ExtendedModelMap()
                ) as ResponseEntity<*>

                @Suppress("UNCHECKED_CAST")
                val body = result.body as Map<String, Any>
                body["isOverDue"] shouldBe false
                body["dueDateMsg"] shouldBe "3일 남음"
            }

            it("JSON 응답 요청이지만 마감일 변경이 없으면 빈 맵을 반환해야 한다") {
                val target = Issue(id = 89L, number = 20L, title = "이슈", project = project, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(89L)) } returns listOf(target)

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 89L })

                val result = issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = "application/json", model = ExtendedModelMap()
                ) as ResponseEntity<*>

                @Suppress("UNCHECKED_CAST")
                val body = result.body as Map<String, Any>
                body.isEmpty() shouldBe true
            }

            // "if (isDueDateChanged && firstUpdatedIssue != null)" 중 isDueDateChanged=true이지만
            // firstUpdatedIssue가 null인 경로(대상 이슈가 전부 다른 프로젝트 소속이라 continue되어
            // firstOrNull 결과가 없는 경우)도 빈 맵으로 응답해야 한다.
            it("JSON 응답 요청이고 isDueDateChanged=true여도 대상 프로젝트에 갱신된 이슈가 없으면 빈 맵을 반환해야 한다") {
                val otherProject = Project(id = 2L, name = "Other", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val foreignIssue = Issue(id = 92L, number = 23L, title = "다른 프로젝트 이슈", project = otherProject, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(92L)) } returns listOf(foreignIssue)

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 92L })

                val result = issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = true, dueDate = "2026-12-31",
                    accept = "application/json", model = ExtendedModelMap()
                ) as ResponseEntity<*>

                @Suppress("UNCHECKED_CAST")
                val body = result.body as Map<String, Any>
                body.isEmpty() shouldBe true
            }

            // form.state가 빈 문자열("")이면(null이 아니지만 비어 있음) isNullOrEmpty()가 true가 되어
            // changeState가 호출되지 않는 분기(state=null과는 별도 분기)를 커버한다.
            it("state 값이 빈 문자열이면 changeState를 호출하지 않아야 한다") {
                val target = Issue(id = 93L, number = 24L, title = "이슈", project = project, authorLoginId = "testuser")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(93L)) } returns listOf(target)

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 93L })
                form.state = ""

                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = false, dueDate = null,
                    accept = null, model = ExtendedModelMap()
                )

                verify(exactly = 0) { issueService.changeState(any(), any(), any()) }
            }

            // dueDate가 빈 문자열("")이면(null이 아니지만 비어 있음) isNullOrEmpty()가 true가 되어
            // 마감일이 null로 초기화되는 분기(dueDate=null과는 별도 분기)를 커버한다.
            it("isDueDateChanged=true이고 dueDate가 빈 문자열이면 마감일이 null로 초기화되어야 한다") {
                val target = Issue(id = 94L, number = 25L, title = "이슈", project = project, authorLoginId = "testuser", dueDate = Instant.parse("2026-01-01T00:00:00Z"))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { issueRepository.findAllById(listOf(94L)) } returns listOf(target)
                every { issueRepository.save(any<Issue>()) } answers { firstArg() }

                val form = IssueMassUpdateForm()
                form.issues = listOf(IssueIdForm().apply { id = 94L })

                issueViewController.massUpdate(
                    owner = "owner", projectName = "TestProj", form = form,
                    authentication = userAuth, delete = false, isDueDateChanged = true, dueDate = "",
                    accept = null, model = ExtendedModelMap()
                )

                target.dueDate shouldBe null
            }
        }
    }
})
