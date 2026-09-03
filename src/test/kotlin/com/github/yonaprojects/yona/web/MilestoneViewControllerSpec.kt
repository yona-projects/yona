package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.support.MarkdownService
import org.springframework.http.HttpStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import org.springframework.ui.ExtendedModelMap
import java.time.Instant
import java.time.temporal.ChronoUnit

class MilestoneViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val milestoneService = mockk<MilestoneService>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val issueRepository = mockk<IssueRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val markdownService = mockk<MarkdownService>()
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

    val attachmentService = mockk<AttachmentService>()
    val milestoneViewController = MilestoneViewController(
        projectRepository,
        milestoneService,
        milestoneRepository,
        issueRepository,
        projectUserRepository,
        userRepository,
        attachmentRepository,
        markdownService,
        accessControl,
        attachmentService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(milestoneViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(
            projectRepository,
            milestoneService,
            milestoneRepository,
            issueRepository,
            projectUserRepository,
            userRepository,
            attachmentRepository,
            markdownService,
            attachmentService
        )
    }

    describe("MilestoneViewController 템플릿 연동 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        // isAllowed(user, project, Operation.READ)는 엔티티 관계(user.isMemberOf) 기반이라, 이 describe
        // 블록에서 공유되는 `user`를 직접 멤버로 바꾸면 아래 "비멤버 403" 테스트가 깨진다 — 필요한 개별
        // 테스트에서만 별도의 memberUser를 만들어 쓴다.
        val milestone = Milestone(id = 2L, title = "마일스톤 테스트", project = project)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /{owner}/{projectName}/milestones") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 milestone/list 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestones(1L, State.OPEN, "dueDate", "asc") } returns listOf(milestone)
                every { issueRepository.findByMilestone(milestone) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/milestones").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/list"))
                    .andExpect(model().attributeExists("project", "milestones", "state"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = Organization(id = 1L, name = "org")
                groupOrg.organizationUsers.add(
                    OrganizationUser(
                        id = 1L, user = user, organization = groupOrg,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )
                val groupProject = Project(id = 8L, name = "group-project", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(8L, 10L) } returns false
                every { milestoneService.getMilestones(8L, State.OPEN, "dueDate", "asc") } returns emptyList()

                mockMvc.perform(get("/owner/group-project/milestones").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/list"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/milestones").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            // yona MilestoneApp.java:50-73 MilestoneCondition(orderBy/orderDir 파라미터) 대응 (P1-128). [GL-controllers_MilestoneApp-002;GL-controllers_MilestoneApp-003]
            it("orderBy/orderDir 파라미터를 서비스 호출에 그대로 전달해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 902L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestones(1L, State.OPEN, "title", "desc") } returns listOf(milestone)
                every { issueRepository.findByMilestone(milestone) } returns emptyList()

                mockMvc.perform(
                    get("/owner/TestProj/milestones")
                        .param("orderBy", "title")
                        .param("orderDir", "desc")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("orderBy", "title"))
                    .andExpect(model().attribute("orderDir", "desc"))
            }

            // yona Milestone.java:214-227 findMilestones()의 completionRate Comparator 대응 (P1-128). [GL-models_Milestone-029]
            // completionRate는 DB 컬럼이 아니라 계산 필드라, 서비스에서 반환된 순서와 무관하게 컨트롤러가
            // 완료율 기준으로 다시 정렬해야 한다.
            it("orderBy=completionRate면 완료율 기준으로 재정렬해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 903L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                // 완료율: low=0%(0/2), high=100%(2/2) — 서비스가 반환하는 순서는 일부러 low, high로 둔다.
                val low = Milestone(id = 20L, title = "낮은 완료율", project = project)
                val high = Milestone(id = 21L, title = "높은 완료율", project = project)
                every { milestoneService.getMilestones(1L, State.OPEN, "completionRate", "desc") } returns listOf(low, high)
                every { issueRepository.findByMilestone(low) } returns listOf(
                    Issue(title = "이슈1", project = project, state = State.OPEN),
                    Issue(title = "이슈2", project = project, state = State.OPEN)
                )
                every { issueRepository.findByMilestone(high) } returns listOf(
                    Issue(title = "이슈1", project = project, state = State.CLOSED),
                    Issue(title = "이슈2", project = project, state = State.CLOSED)
                )

                val result = mockMvc.perform(
                    get("/owner/TestProj/milestones")
                        .param("orderBy", "completionRate")
                        .param("orderDir", "desc")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andReturn()

                @Suppress("UNCHECKED_CAST")
                val milestones = result.modelAndView!!.model["milestones"] as List<MilestoneViewController.MilestoneViewDto>
                milestones.map { it.milestone.id } shouldBe listOf(21L, 20L)
            }
        }

        describe("GET /{owner}/{projectName}/milestone/{id}") {
            it("멤버라면 200 OK와 milestone/view 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 901L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns milestone
                every { issueRepository.findByMilestone(milestone) } returns emptyList()
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, "2") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/milestone/2").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/view"))
                    .andExpect(model().attributeExists("project", "milestoneDto"))
            }

            // yona Milestone.java:99-108 sortedByNumberOfIssue()(이슈 번호 내림차순) 대응 (P2-22). [GL-models_Milestone-014;GL-models_Milestone-015]
            // 리포지토리 조회에는 정렬이 없으므로, 컨트롤러가 open/closed 이슈 목록을 번호 내림차순으로
            // 재정렬해야 한다.
            it("이슈 목록은 번호 내림차순으로 정렬돼 있어야 한다 (P2-22)") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 904L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns milestone
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, "2") } returns emptyList()

                every { issueRepository.findByMilestone(milestone) } returns listOf(
                    Issue(number = 3L, title = "이슈3", project = project, state = State.OPEN),
                    Issue(number = 7L, title = "이슈7", project = project, state = State.OPEN),
                    Issue(number = 5L, title = "이슈5", project = project, state = State.OPEN),
                    Issue(number = 1L, title = "이슈1", project = project, state = State.CLOSED),
                    Issue(number = 9L, title = "이슈9", project = project, state = State.CLOSED)
                )

                val result = mockMvc.perform(get("/owner/TestProj/milestone/2").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val milestoneDto = result.modelAndView!!.model["milestoneDto"] as MilestoneViewController.MilestoneViewDto
                milestoneDto.openIssues.map { it.number } shouldBe listOf(7L, 5L, 3L)
                milestoneDto.closedIssues.map { it.number } shouldBe listOf(9L, 1L)
            }
        }

        describe("GET /{owner}/{projectName}/milestone/new") {
            it("멤버라면 200 OK와 milestone/create 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/TestProj/milestone/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/create"))
                    .andExpect(model().attributeExists("project"))
            }
        }

        // yona Attachment.moveOnlySelected() 대응 (P0-22) — 요청받은 첨부파일 ID를 검증 없이 그대로
        // 재배선하지 않고, 실제로 이 로그인 사용자가 업로드한 임시 첨부만 옮기는지 검증한다.
        describe("POST /{owner}/{projectName}/milestones - 임시 업로드 첨부파일 연결") {
            it("temporaryUploadFiles로 넘어온 첨부파일 ID들이 moveOnlySelected를 통해 생성된 마일스톤으로 옮겨져야 한다") {
                val savedMilestone = Milestone(id = 100L, title = "새 마일스톤", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneRepository.findByProjectAndTitle(project, "새 마일스톤") } returns null
                every { milestoneService.createMilestone(1L, any()) } returns savedMilestone
                every {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.MILESTONE, "100",
                        listOf(900L), "testuser"
                    )
                } returns 1

                val result = milestoneViewController.createMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "새 마일스톤",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = "900",
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/milestone/100"
                verify(exactly = 1) {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.MILESTONE, "100",
                        listOf(900L), "testuser"
                    )
                }
            }
        }

        // yona MilestoneApp.java:100-125 validateDueDate()(Play 폼 바인딩 실패 시 hasErrors()로 전체
        // 제출을 막고 경고 플래시) 대응 (P2-23). dueDate 형식이 잘못되면 조용히 null로 저장하지 말고 [GL-controllers_MilestoneApp-006]
        // 저장 자체를 막아야 한다.
        describe("POST /{owner}/{projectName}/milestones - dueDate 형식 오류 (P2-23)") {
            it("dueDate 파싱에 실패하면 저장하지 않고 오류와 함께 생성 폼을 다시 보여줘야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneRepository.findByProjectAndTitle(project, "새 마일스톤") } returns null

                val model = ExtendedModelMap()
                val result = milestoneViewController.createMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "새 마일스톤",
                    contents = null,
                    dueDate = "이건-날짜가-아님",
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = model
                )

                result shouldBe "milestone/create"
                model.getAttribute("dueDateError") shouldBe "milestone.error.duedateFormat"
                verify(exactly = 0) { milestoneService.createMilestone(any(), any()) }
            }
        }

        describe("POST /{owner}/{projectName}/milestone/{id}/edit - dueDate 형식 오류 (P2-23)") {
            it("dueDate 파싱에 실패하면 저장하지 않고 오류와 함께 수정 폼을 다시 보여줘야 한다") {
                val original = Milestone(id = 2L, title = "기존 마일스톤", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns original
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, "2") } returns emptyList()

                val model = ExtendedModelMap()
                val result = milestoneViewController.editMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    id = 2L,
                    title = "기존 마일스톤",
                    contents = null,
                    dueDate = "이건-날짜가-아님",
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = model
                )

                result shouldBe "milestone/edit"
                model.getAttribute("dueDateError") shouldBe "milestone.error.duedateFormat"
                verify(exactly = 0) { milestoneService.updateMilestone(any(), any(), any(), any(), any()) }
            }
        }

        // 아래부터는 JaCoCo 커버리지 보강을 위해 추가된 테스트다. 완전 미실행 상태였던
        // openMilestone/closeMilestone/deleteMilestone/editMilestoneForm과, 일부만 커버되던
        // listMilestones/viewMilestone/createMilestoneForm/createMilestone/editMilestone의
        // 나머지 분기(404, 403, notfound, 첨부파일 JSON 직렬화 람다 등)를 다룬다.

        describe("GET /{owner}/{projectName}/milestones - 예외 분기") {
            it("프로젝트가 없으면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuchProj/milestones"))
                    .andExpect(view().name("error/404"))
            }

            it("비인증(익명) 사용자가 비공개 프로젝트에 접근하면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/milestones"))
                    .andExpect(view().name("error/forbidden"))
            }

            it("state=closed면 State.CLOSED로 조회해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 910L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestones(1L, State.CLOSED, "dueDate", "asc") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/milestones").param("state", "closed").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("state", "closed"))
            }

            it("state=all이면 State.ALL로 조회해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 911L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestones(1L, State.ALL, "dueDate", "asc") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/milestones").param("state", "all").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("state", "all"))
            }

            // completionRate 정렬의 desc가 아닌(=asc) 분기는 기존 테스트가 다루지 않는다.
            it("orderBy=completionRate이고 orderDir이 desc가 아니면 완료율 오름차순으로 정렬해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 912L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                val low = Milestone(id = 30L, title = "낮은 완료율", project = project)
                val high = Milestone(id = 31L, title = "높은 완료율", project = project)
                every { milestoneService.getMilestones(1L, State.OPEN, "completionRate", "asc") } returns listOf(high, low)
                every { issueRepository.findByMilestone(low) } returns listOf(
                    Issue(title = "이슈1", project = project, state = State.OPEN)
                )
                every { issueRepository.findByMilestone(high) } returns listOf(
                    Issue(title = "이슈1", project = project, state = State.CLOSED)
                )

                val result = mockMvc.perform(
                    get("/owner/TestProj/milestones")
                        .param("orderBy", "completionRate")
                        .param("orderDir", "asc")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andReturn()

                @Suppress("UNCHECKED_CAST")
                val milestones = result.modelAndView!!.model["milestones"] as List<MilestoneViewController.MilestoneViewDto>
                milestones.map { it.milestone.id } shouldBe listOf(30L, 31L)
            }
        }

        describe("GET /{owner}/{projectName}/milestone/{id} - 예외 분기 및 첨부파일 직렬화") {
            it("프로젝트가 없으면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuchProj/milestone/2"))
                    .andExpect(view().name("error/404"))
            }

            it("프로젝트 멤버가 아니면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/milestone/2").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            // authentication?.let{...}의 결과(loginUser)가 null이 되는 경우는 익명(인증 토큰 없음)뿐
            // 아니라 인증은 됐지만 대응하는 User 레코드가 없는 경우도 있다 — 별도 분기다.
            it("인증은 됐지만 대응하는 사용자 정보가 없으면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(get("/owner/TestProj/milestone/2").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            it("마일스톤이 없으면 error/notfound 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 920L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(999L) } returns null

                mockMvc.perform(get("/owner/TestProj/milestone/999").principal(userAuth))
                    .andExpect(view().name("error/notfound"))
                    .andExpect(model().attribute("targetType", "milestone"))
            }

            it("마일스톤이 다른 프로젝트 소속이면 error/notfound 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 921L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                val otherProject = Project(id = 99L, name = "OtherProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val otherMilestone = Milestone(id = 2L, title = "다른 프로젝트 마일스톤", project = otherProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns otherMilestone

                mockMvc.perform(get("/owner/TestProj/milestone/2").principal(userAuth))
                    .andExpect(view().name("error/notfound"))
            }

            // 첨부파일이 있을 때만 joinToString의 변환 람다(id/mimeType/size의 엘비스 분기, 이름의
            // 따옴표·줄바꿈 이스케이프)가 실행되고, contents가 있을 때만 markdownService.render가 호출된다.
            it("첨부파일과 본문이 있으면 attachmentsJson과 contentsHtml을 채워야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 922L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                // dueDate를 채워서 toViewDto()의 isOverdue/daysBetween 계산(milestone.dueDate?.let{...}) 중
                // non-null 분기도 함께 커버한다.
                val milestoneWithContents = Milestone(
                    id = 2L, title = "마일스톤 테스트", contents = "본문 \"내용\"\n둘째줄",
                    dueDate = Instant.now().plus(10, ChronoUnit.DAYS),
                    project = project
                )
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns milestoneWithContents
                every { issueRepository.findByMilestone(milestoneWithContents) } returns emptyList()
                every { markdownService.render("본문 \"내용\"\n둘째줄", true, project) } returns "<p>본문 내용</p>"
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, "2") } returns listOf(
                    Attachment(id = 5L, name = "파일\"1\"\n줄바꿈", containerType = ResourceType.MILESTONE, containerId = "2", mimeType = "image/png", size = 1024L),
                    Attachment(id = null, name = "파일2", containerType = ResourceType.MILESTONE, containerId = "2", mimeType = null, size = null)
                )

                val result = mockMvc.perform(get("/owner/TestProj/milestone/2").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()

                val attachmentsJson = result.modelAndView!!.model["attachmentsJson"] as String
                attachmentsJson shouldBe (
                    "{\"attachments\":[" +
                        "{\"id\":\"5\",\"mimeType\":\"image/png\",\"name\":\"파일\\\"1\\\"\\n줄바꿈\",\"url\":\"/files/5\",\"size\":1024}," +
                        "{\"id\":\"\",\"mimeType\":\"\",\"name\":\"파일2\",\"url\":\"/files/null\",\"size\":0}" +
                        "]}"
                )
                result.modelAndView!!.model["contentsHtml"] shouldBe "<p>본문 내용</p>"
            }
        }

        describe("GET /{owner}/{projectName}/milestone/new - 예외 분기") {
            it("프로젝트가 없으면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuchProj/milestone/new"))
                    .andExpect(view().name("error/404"))
            }

            it("비인증 사용자는 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/milestone/new"))
                    .andExpect(view().name("error/forbidden"))
            }

            it("프로젝트 멤버도 그룹 멤버도 아니면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/milestone/new").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 조직(그룹) 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = Organization(id = 2L, name = "org2")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 2L, user = user, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 9L, name = "group-project2", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project2") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(9L, 10L) } returns false

                mockMvc.perform(get("/owner/group-project2/milestone/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/create"))
            }
        }

        describe("GET /{owner}/{projectName}/milestone/{id}/editform") {
            it("프로젝트가 없으면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoSuchProj/milestone/2/editform"))
                    .andExpect(view().name("error/404"))
            }

            it("비인증 사용자는 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/milestone/2/editform"))
                    .andExpect(view().name("error/forbidden"))
            }

            it("프로젝트 멤버도 그룹 멤버도 아니면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/milestone/2/editform").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 조직(그룹) 멤버라면 200 OK를 반환해야 한다") {
                val groupOrg = Organization(id = 5L, name = "org5")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 5L, user = user, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 13L, name = "group-project5", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                val groupMilestone = Milestone(id = 70L, title = "그룹 마일스톤", project = groupProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project5") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(13L, 10L) } returns false
                every { milestoneService.getMilestone(70L) } returns groupMilestone
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, "70") } returns emptyList()

                mockMvc.perform(get("/owner/group-project5/milestone/70/editform").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/edit"))
            }

            it("마일스톤이 없으면 error/notfound 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 930L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(999L) } returns null

                mockMvc.perform(get("/owner/TestProj/milestone/999/editform").principal(userAuth))
                    .andExpect(view().name("error/notfound"))
            }

            it("마일스톤이 다른 프로젝트 소속이면 error/notfound 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 931L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                val otherProject = Project(id = 98L, name = "OtherProj2", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val otherMilestone = Milestone(id = 2L, title = "다른 프로젝트 마일스톤", project = otherProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns otherMilestone

                mockMvc.perform(get("/owner/TestProj/milestone/2/editform").principal(userAuth))
                    .andExpect(view().name("error/notfound"))
            }

            // 첨부파일 목록이 있을 때만 joinToString 변환 람다(editMilestoneForm$lambda$2)가 실행된다.
            it("멤버라면 200 OK와 milestone/edit 뷰, 첨부파일 JSON을 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 932L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns milestone
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, "2") } returns listOf(
                    Attachment(id = 6L, name = "첨부", containerType = ResourceType.MILESTONE, containerId = "2", mimeType = "text/plain", size = 10L),
                    Attachment(id = null, name = "첨부2", containerType = ResourceType.MILESTONE, containerId = "2", mimeType = null, size = null)
                )

                mockMvc.perform(get("/owner/TestProj/milestone/2/editform").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("milestone/edit"))
                    .andExpect(model().attributeExists("project", "milestone", "attachmentsJson"))
            }
        }

        describe("POST /{owner}/{projectName}/milestones - 예외 분기") {
            it("프로젝트가 없으면 error/404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                val result = milestoneViewController.createMilestone(
                    owner = "owner",
                    projectName = "NoSuchProj",
                    title = "새 마일스톤",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = null,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "error/404"
            }

            it("비인증 사용자는 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                val result = milestoneViewController.createMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "새 마일스톤",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = null,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            // authentication?.let{...} ?: run{...} 구조는 인증 토큰 자체가 없는 경우(익명)뿐 아니라,
            // 토큰은 있지만 대응하는 User 레코드가 없는 경우(예: 탈퇴한 사용자)도 별도 분기다.
            it("인증은 됐지만 대응하는 사용자 정보가 없으면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val result = milestoneViewController.createMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "새 마일스톤",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("프로젝트 멤버도 그룹 멤버도 아니면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                val result = milestoneViewController.createMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "새 마일스톤",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("제목이 이미 존재하면 저장하지 않고 titleError와 함께 생성 폼을 다시 보여줘야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneRepository.findByProjectAndTitle(project, "마일스톤 테스트") } returns milestone

                val model = ExtendedModelMap()
                val result = milestoneViewController.createMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "마일스톤 테스트",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = model
                )

                result shouldBe "milestone/create"
                model.getAttribute("titleError") shouldBe "milestone.title.duplicated"
                verify(exactly = 0) { milestoneService.createMilestone(any(), any()) }
            }

            it("dueDate가 유효한 날짜면 하루 끝 시각으로 보정하여 저장하고 상세 페이지로 리다이렉트해야 한다") {
                val savedMilestone = Milestone(id = 101L, title = "새 마일스톤2", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneRepository.findByProjectAndTitle(project, "새 마일스톤2") } returns null
                every { milestoneService.createMilestone(1L, any()) } returns savedMilestone

                val result = milestoneViewController.createMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "새 마일스톤2",
                    contents = "내용",
                    dueDate = "2026-12-31",
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/milestone/101"
                verify(exactly = 0) { attachmentService.moveOnlySelected(any(), any(), any(), any(), any(), any()) }
            }

            // dueDate/temporaryUploadFiles는 null이 아니라 공백 문자열("")로 들어올 수도 있다.
            // isNullOrBlank()는 null-체크와 blank-체크(빈 문자열 여부)가 별도 분기이므로, null이 아닌
            // 빈 문자열 케이스를 별도로 검증해야 두 분기 모두 커버된다.
            it("dueDate와 temporaryUploadFiles가 빈 문자열이면 둘 다 없는 것처럼 처리해야 한다") {
                val savedMilestone = Milestone(id = 103L, title = "새 마일스톤3", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneRepository.findByProjectAndTitle(project, "새 마일스톤3") } returns null
                every { milestoneService.createMilestone(1L, any()) } returns savedMilestone

                val result = milestoneViewController.createMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    title = "새 마일스톤3",
                    contents = null,
                    dueDate = "",
                    state = State.OPEN,
                    temporaryUploadFiles = "",
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/milestone/103"
                verify(exactly = 0) { attachmentService.moveOnlySelected(any(), any(), any(), any(), any(), any()) }
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 조직(그룹) 멤버라면 마일스톤을 생성할 수 있어야 한다") {
                val groupOrg = Organization(id = 3L, name = "org3")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 3L, user = user, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 11L, name = "group-project3", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                val savedMilestone = Milestone(id = 102L, title = "그룹 마일스톤", project = groupProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project3") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(11L, 10L) } returns false
                every { milestoneRepository.findByProjectAndTitle(groupProject, "그룹 마일스톤") } returns null
                every { milestoneService.createMilestone(11L, any()) } returns savedMilestone

                val result = milestoneViewController.createMilestone(
                    owner = "owner",
                    projectName = "group-project3",
                    title = "그룹 마일스톤",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/group-project3/milestone/102"
            }
        }

        describe("POST /{owner}/{projectName}/milestone/{id}/edit - 예외 분기") {
            it("프로젝트가 없으면 error/404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                val result = milestoneViewController.editMilestone(
                    owner = "owner",
                    projectName = "NoSuchProj",
                    id = 2L,
                    title = "제목",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = null,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "error/404"
            }

            it("비인증 사용자는 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                val result = milestoneViewController.editMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    id = 2L,
                    title = "제목",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = null,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            // authentication?.let{...} ?: run{...} 구조는 인증 토큰 자체가 없는 경우(익명)뿐 아니라,
            // 토큰은 있지만 대응하는 User 레코드가 없는 경우(예: 탈퇴한 사용자)도 별도 분기다.
            it("인증은 됐지만 대응하는 사용자 정보가 없으면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val result = milestoneViewController.editMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    id = 2L,
                    title = "제목",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("프로젝트 멤버도 그룹 멤버도 아니면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                val result = milestoneViewController.editMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    id = 2L,
                    title = "제목",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("마일스톤이 없으면 error/notfound 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(999L) } returns null

                val result = milestoneViewController.editMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    id = 999L,
                    title = "제목",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "error/notfound"
            }

            // 첨부파일 목록이 있을 때만 joinToString 변환 람다(editMilestone$lambda$3)가 실행된다.
            it("제목이 변경되고 다른 마일스톤과 중복되면 저장하지 않고 titleError와 첨부파일 JSON을 다시 보여줘야 한다") {
                val original = Milestone(id = 2L, title = "기존 마일스톤", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns original
                every { milestoneRepository.findByProjectAndTitle(project, "중복 제목") } returns Milestone(id = 55L, title = "중복 제목", project = project)
                // id/mimeType/size가 모두 null인 첨부파일을 함께 둬서 joinToString 변환 람다의 엘비스
                // 연산자(id?.toString() ?: "", mimeType ?: "", size?.toString() ?: "0") 양쪽 분기를 모두 커버한다.
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, "2") } returns listOf(
                    Attachment(id = 7L, name = "첨부", containerType = ResourceType.MILESTONE, containerId = "2", mimeType = "text/plain", size = 20L),
                    Attachment(id = null, name = "첨부2", containerType = ResourceType.MILESTONE, containerId = "2", mimeType = null, size = null)
                )

                val model = ExtendedModelMap()
                val result = milestoneViewController.editMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    id = 2L,
                    title = "중복 제목",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = model
                )

                result shouldBe "milestone/edit"
                model.getAttribute("titleError") shouldBe "milestone.title.duplicated"
                (model.getAttribute("milestone") as Milestone).title shouldBe "중복 제목"
                model.getAttribute("attachmentsJson") shouldBe (
                    "{\"attachments\":[" +
                        "{\"id\":\"7\",\"mimeType\":\"text/plain\",\"name\":\"첨부\",\"url\":\"/files/7\",\"size\":20}," +
                        "{\"id\":\"\",\"mimeType\":\"\",\"name\":\"첨부2\",\"url\":\"/files/null\",\"size\":0}" +
                        "]}"
                    )
                verify(exactly = 0) { milestoneService.updateMilestone(any(), any(), any(), any(), any()) }
            }

            // original.title == title이면 && 단락 평가로 findByProjectAndTitle이 호출되지 않아야 한다.
            // 스텁하지 않은 채로 성공한다는 사실 자체가(스텁 없이 호출됐다면 mockk가 예외를 던진다)
            // 호출되지 않았음을 증명한다.
            it("제목이 바뀌지 않으면 동일한 제목이 이미 존재하더라도 중복 검증을 건너뛰고 정상 수정해야 한다") {
                val original = Milestone(id = 2L, title = "기존 마일스톤", project = project)
                val updated = Milestone(id = 2L, title = "기존 마일스톤", contents = "내용", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns original
                every { milestoneService.updateMilestone(2L, "기존 마일스톤", "내용", null, State.OPEN) } returns updated

                val result = milestoneViewController.editMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    id = 2L,
                    title = "기존 마일스톤",
                    contents = "내용",
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/milestone/2"
            }

            // 제목을 원본과 다르게(새 제목) 바꾸되 중복이 없는 경우 — isDuplicateTitle 계산식
            // (original.title != title && findByProjectAndTitle(...) != null)의 "제목 변경 + 중복 아님"
            // 조합(true && false)을 커버한다. dueDate 유효 파싱도 함께 검증한다.
            it("dueDate가 유효한 날짜면 하루 끝 시각으로 보정하여 저장하고, 제목이 바뀌어도 중복이 없으면 상세 페이지로 리다이렉트해야 한다") {
                val original = Milestone(id = 2L, title = "기존 마일스톤", project = project)
                val updated = Milestone(id = 2L, title = "새 제목", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns original
                every { milestoneRepository.findByProjectAndTitle(project, "새 제목") } returns null
                every { milestoneService.updateMilestone(2L, "새 제목", "", any(), State.CLOSED) } returns updated

                val result = milestoneViewController.editMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    id = 2L,
                    title = "새 제목",
                    contents = null,
                    dueDate = "2026-12-31",
                    state = State.CLOSED,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/milestone/2"
            }

            // dueDate/temporaryUploadFiles는 null이 아니라 공백 문자열("")로 들어올 수도 있다.
            // isNullOrBlank()는 null-체크와 blank-체크(빈 문자열 여부)가 별도 분기이므로, null이 아닌
            // 빈 문자열 케이스를 별도로 검증해야 두 분기 모두 커버된다.
            it("dueDate와 temporaryUploadFiles가 빈 문자열이면 둘 다 없는 것처럼 처리해야 한다") {
                val original = Milestone(id = 2L, title = "기존 마일스톤", project = project)
                val updated = Milestone(id = 2L, title = "기존 마일스톤", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns original
                every { milestoneService.updateMilestone(2L, "기존 마일스톤", "", null, State.OPEN) } returns updated

                val result = milestoneViewController.editMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    id = 2L,
                    title = "기존 마일스톤",
                    contents = null,
                    dueDate = "",
                    state = State.OPEN,
                    temporaryUploadFiles = "",
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/milestone/2"
                verify(exactly = 0) { attachmentService.moveOnlySelected(any(), any(), any(), any(), any(), any()) }
            }

            it("temporaryUploadFiles가 있으면 moveOnlySelected로 첨부파일을 옮겨야 한다") {
                val original = Milestone(id = 2L, title = "기존 마일스톤", project = project)
                val updated = Milestone(id = 2L, title = "기존 마일스톤", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns original
                every { milestoneRepository.findByProjectAndTitle(project, "기존 마일스톤") } returns null
                every { milestoneService.updateMilestone(2L, "기존 마일스톤", "", null, State.OPEN) } returns updated
                every {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.MILESTONE, "2",
                        listOf(901L), "testuser"
                    )
                } returns 1

                val result = milestoneViewController.editMilestone(
                    owner = "owner",
                    projectName = "TestProj",
                    id = 2L,
                    title = "기존 마일스톤",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = "901",
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/milestone/2"
                verify(exactly = 1) {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.MILESTONE, "2",
                        listOf(901L), "testuser"
                    )
                }
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 조직(그룹) 멤버라면 마일스톤을 수정할 수 있어야 한다") {
                val groupOrg = Organization(id = 4L, name = "org4")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 4L, user = user, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 12L, name = "group-project4", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                val original = Milestone(id = 60L, title = "그룹 마일스톤", project = groupProject)
                val updated = Milestone(id = 60L, title = "그룹 마일스톤", project = groupProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project4") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(12L, 10L) } returns false
                every { milestoneService.getMilestone(60L) } returns original
                every { milestoneRepository.findByProjectAndTitle(groupProject, "그룹 마일스톤") } returns null
                every { milestoneService.updateMilestone(60L, "그룹 마일스톤", "", null, State.OPEN) } returns updated

                val result = milestoneViewController.editMilestone(
                    owner = "owner",
                    projectName = "group-project4",
                    id = 60L,
                    title = "그룹 마일스톤",
                    contents = null,
                    dueDate = null,
                    state = State.OPEN,
                    temporaryUploadFiles = null,
                    authentication = userAuth,
                    redirectAttributes = mockk(relaxed = true),
                    model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/group-project4/milestone/60"
            }
        }

        describe("POST /{owner}/{projectName}/milestone/{id}/open") {
            it("프로젝트가 없으면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                val result = milestoneViewController.openMilestone(
                    owner = "owner", projectName = "NoSuchProj", id = 2L,
                    authentication = null, model = ExtendedModelMap()
                )

                result shouldBe "error/404"
            }

            it("비인증 사용자는 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                val result = milestoneViewController.openMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L,
                    authentication = null, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            // authentication?.let{...} ?: run{...} 구조는 인증 토큰 자체가 없는 경우(익명)뿐 아니라,
            // 토큰은 있지만 대응하는 User 레코드가 없는 경우(예: 탈퇴한 사용자)도 별도 분기다.
            it("인증은 됐지만 대응하는 사용자 정보가 없으면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val result = milestoneViewController.openMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("프로젝트 멤버도 그룹 멤버도 아니면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                val result = milestoneViewController.openMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 조직(그룹) 멤버라면 State.OPEN으로 갱신할 수 있어야 한다") {
                val groupOrg = Organization(id = 6L, name = "org6")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 6L, user = user, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 14L, name = "group-project6", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                val groupMilestone = Milestone(id = 71L, title = "그룹 마일스톤", project = groupProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project6") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(14L, 10L) } returns false
                every { milestoneService.getMilestone(71L) } returns groupMilestone
                every { milestoneService.updateMilestone(71L, "그룹 마일스톤", null, null, State.OPEN) } returns groupMilestone

                val result = milestoneViewController.openMilestone(
                    owner = "owner", projectName = "group-project6", id = 71L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/group-project6/milestone/71"
            }

            it("마일스톤이 없으면 error/notfound 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(999L) } returns null

                val result = milestoneViewController.openMilestone(
                    owner = "owner", projectName = "TestProj", id = 999L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/notfound"
            }

            it("마일스톤이 다른 프로젝트 소속이면 error/notfound 뷰를 반환해야 한다") {
                val otherProject = Project(id = 97L, name = "OtherProj3", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val otherMilestone = Milestone(id = 2L, title = "다른 프로젝트 마일스톤", project = otherProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns otherMilestone

                val result = milestoneViewController.openMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/notfound"
            }

            it("정상 요청이면 State.OPEN으로 갱신하고 상세 페이지로 리다이렉트해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns milestone
                every { milestoneService.updateMilestone(2L, "마일스톤 테스트", null, null, State.OPEN) } returns milestone

                val result = milestoneViewController.openMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/milestone/2"
                verify(exactly = 1) { milestoneService.updateMilestone(2L, "마일스톤 테스트", null, null, State.OPEN) }
            }
        }

        describe("POST /{owner}/{projectName}/milestone/{id}/close") {
            it("프로젝트가 없으면 error/404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                val result = milestoneViewController.closeMilestone(
                    owner = "owner", projectName = "NoSuchProj", id = 2L,
                    authentication = null, model = ExtendedModelMap()
                )

                result shouldBe "error/404"
            }

            it("비인증 사용자는 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                val result = milestoneViewController.closeMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L,
                    authentication = null, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            // authentication?.let{...} ?: run{...} 구조는 인증 토큰 자체가 없는 경우(익명)뿐 아니라,
            // 토큰은 있지만 대응하는 User 레코드가 없는 경우(예: 탈퇴한 사용자)도 별도 분기다.
            it("인증은 됐지만 대응하는 사용자 정보가 없으면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val result = milestoneViewController.closeMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            it("프로젝트 멤버도 그룹 멤버도 아니면 error/forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                val result = milestoneViewController.closeMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/forbidden"
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57)
            it("직접 멤버가 아니어도 조직(그룹) 멤버라면 State.CLOSED로 갱신할 수 있어야 한다") {
                val groupOrg = Organization(id = 7L, name = "org7")
                groupOrg.organizationUsers.add(
                    OrganizationUser(id = 7L, user = user, organization = groupOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupProject = Project(id = 15L, name = "group-project7", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)
                val groupMilestone = Milestone(id = 72L, title = "그룹 마일스톤", project = groupProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project7") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(15L, 10L) } returns false
                every { milestoneService.getMilestone(72L) } returns groupMilestone
                every { milestoneService.updateMilestone(72L, "그룹 마일스톤", null, null, State.CLOSED) } returns groupMilestone

                val result = milestoneViewController.closeMilestone(
                    owner = "owner", projectName = "group-project7", id = 72L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/group-project7/milestone/72"
            }

            it("마일스톤이 없으면 error/notfound 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(999L) } returns null

                val result = milestoneViewController.closeMilestone(
                    owner = "owner", projectName = "TestProj", id = 999L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/notfound"
            }

            it("마일스톤이 다른 프로젝트 소속이면 error/notfound 뷰를 반환해야 한다") {
                val otherProject = Project(id = 95L, name = "OtherProj5", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val otherMilestone = Milestone(id = 2L, title = "다른 프로젝트 마일스톤", project = otherProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns otherMilestone

                val result = milestoneViewController.closeMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "error/notfound"
            }

            it("정상 요청이면 State.CLOSED로 갱신하고 상세 페이지로 리다이렉트해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns milestone
                every { milestoneService.updateMilestone(2L, "마일스톤 테스트", null, null, State.CLOSED) } returns milestone

                val result = milestoneViewController.closeMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L,
                    authentication = userAuth, model = ExtendedModelMap()
                )

                result shouldBe "redirect:/owner/TestProj/milestone/2"
                verify(exactly = 1) { milestoneService.updateMilestone(2L, "마일스톤 테스트", null, null, State.CLOSED) }
            }
        }

        // deleteMilestone은 openMilestone/closeMilestone과 달리 accessControl.isAllowedIfGroupMember를
        // 쓰지 않고 projectUserRepository 직접 멤버 여부만 확인한다(코드 근거: 그룹 멤버 우회 분기 자체가
        // 없다) — 그룹 멤버 성공 케이스를 만들지 않는 이유.
        describe("DELETE /{owner}/{projectName}/milestone/{id}") {
            it("프로젝트가 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                val result = milestoneViewController.deleteMilestone(
                    owner = "owner", projectName = "NoSuchProj", id = 2L, authentication = null
                )

                result.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("비인증 사용자는 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                val result = milestoneViewController.deleteMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L, authentication = null
                )

                result.statusCode shouldBe HttpStatus.FORBIDDEN
            }

            // authentication?.let{...} ?: return ... 구조는 인증 토큰 자체가 없는 경우(익명)뿐 아니라,
            // 토큰은 있지만 대응하는 User 레코드가 없는 경우(예: 탈퇴한 사용자)도 별도 분기다.
            it("인증은 됐지만 대응하는 사용자 정보가 없으면 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val result = milestoneViewController.deleteMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L, authentication = userAuth
                )

                result.statusCode shouldBe HttpStatus.FORBIDDEN
            }

            it("프로젝트 멤버가 아니면 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                val result = milestoneViewController.deleteMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L, authentication = userAuth
                )

                result.statusCode shouldBe HttpStatus.FORBIDDEN
            }

            it("마일스톤이 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(999L) } returns null

                val result = milestoneViewController.deleteMilestone(
                    owner = "owner", projectName = "TestProj", id = 999L, authentication = userAuth
                )

                result.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("마일스톤이 다른 프로젝트 소속이면 404를 반환해야 한다") {
                val otherProject = Project(id = 96L, name = "OtherProj4", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val otherMilestone = Milestone(id = 2L, title = "다른 프로젝트 마일스톤", project = otherProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns otherMilestone

                val result = milestoneViewController.deleteMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L, authentication = userAuth
                )

                result.statusCode shouldBe HttpStatus.NOT_FOUND
            }

            it("정상 요청이면 204와 Location 헤더를 반환하고 삭제를 수행해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestone(2L) } returns milestone
                every { milestoneService.deleteMilestone(2L) } returns Unit

                val result = milestoneViewController.deleteMilestone(
                    owner = "owner", projectName = "TestProj", id = 2L, authentication = userAuth
                )

                result.statusCode shouldBe HttpStatus.NO_CONTENT
                result.headers.getFirst("Location") shouldBe "/owner/TestProj/milestones"
                verify(exactly = 1) { milestoneService.deleteMilestone(2L) }
            }
        }
    }
})
