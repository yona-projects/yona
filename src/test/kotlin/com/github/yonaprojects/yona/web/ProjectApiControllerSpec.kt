package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.AssigneeRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.notification.NotificationUrlResolver
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.project.ProjectScope
import java.text.SimpleDateFormat
import java.util.Locale
import java.time.Instant
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest

// yona ProjectApi.java newProject() 대응 (P2-45).
class ProjectApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val roleRepository = mockk<RoleRepository>()
    val repositoryService = mockk<RepositoryService>()

    // AccessControl은 실제 로직(isGlobalResourceCreatable/isOrganizationAdmin)을 검증하기 위해
    // 이 세션에서 확립된 관례대로 실제 인스턴스를 쓰고, 그 내부 리포지토리만 mock한다.
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
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

    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val issueLabelRepository = mockk<IssueLabelRepository>()
    val assigneeRepository = mockk<AssigneeRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val notificationUrlResolver = mockk<NotificationUrlResolver>()

    val controller = ProjectApiController(
        projectRepository,
        projectUserRepository,
        userRepository,
        organizationRepository,
        roleRepository,
        repositoryService,
        accessControl,
        issueRepository,
        postingRepository,
        issueCommentRepository,
        postingCommentRepository,
        milestoneRepository,
        issueLabelRepository,
        assigneeRepository,
        attachmentRepository,
        pullRequestRepository,
        notificationUrlResolver
    )

    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(
            projectRepository, projectUserRepository, userRepository, organizationRepository,
            roleRepository, repositoryService, organizationUserRepository,
            issueRepository, postingRepository, reviewCommentRepository, commitCommentRepository,
            milestoneRepository, issueCommentRepository, postingCommentRepository, issueLabelRepository,
            assigneeRepository, attachmentRepository, pullRequestRepository, notificationUrlResolver,
            answers = false
        )
        every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    }

    describe("POST /api/projects/{owner} (P2-45)") {
        val siteManager = User(
            id = 1L, loginId = "admin", name = "관리자",
            state = UserState.SITE_ADMIN
        )
        val normalUser = User(id = 2L, loginId = "normal", name = "일반유저")
        val siteManagerAuth = UsernamePasswordAuthenticationToken("admin", "password")
        val normalAuth = UsernamePasswordAuthenticationToken("normal", "password")
        val sitemanagerRole = Role(id = RoleType.SITEMANAGER.roleType)

        it("인증되지 않은 요청은 401을 반환해야 한다") {
            mockMvc.perform(
                post("/api/projects/someowner")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj"}""")
            ).andExpect(status().isUnauthorized)
        }

        // legacy Open API 경로 별칭 (P2-59)
        it("legacy 경로 /-_-api/v1/owners/{owner}/projects로도 동일하게 동작해야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("someowner", "newproj") } returns Optional.empty()
            every { organizationRepository.findByName("someowner") } returns Optional.empty()
            val saved = Project(id = 60L, owner = "someowner", name = "newproj")
            every { projectRepository.save(any()) } returns saved
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            every { repositoryService.getRepository(saved) } returns mockk(relaxed = true)

            mockMvc.perform(
                post("/-_-api/v1/owners/someowner/projects")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj"}""")
            ).andExpect(status().isCreated)
        }

        it("사이트매니저가 아니면 400과 안내 메시지를 반환해야 한다") {
            every { userRepository.findByLoginId("normal") } returns Optional.of(normalUser)

            mockMvc.perform(
                post("/api/projects/someowner")
                    .principal(normalAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("User creation with api is allowed by Site admin only."))
        }

        // yona models/Project.java:62 @ExConstraints.Restricted({".", "..", ".git"}) 대응 (P1-145).
        it("프로젝트명이 예약 패턴(.,..,.git)과 일치하면 400을 반환해야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": ".git"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Project name is restricted: .git"))

            verify(exactly = 0) { projectRepository.save(any()) }
        }

        it("이미 같은 owner/projectName 프로젝트가 있으면 HTTP 400에 status:409 바디를 담아 반환해야 한다(legacy 원문 그대로)") {
            val existed = Project(id = 50L, owner = "someowner", name = "newproj", overview = "기존", vcs = "GIT")
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("someowner", "newproj") } returns Optional.of(existed)

            mockMvc.perform(
                post("/api/projects/someowner")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.reason").value("Conflict"))
                .andExpect(jsonPath("$.project.id").value(50))
        }

        it("owner가 기존 조직명이고 호출자가 그 조직 admin이 아니면 403을 반환해야 한다") {
            val org = Organization(id = 10L, name = "myorg")
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("myorg", "newproj") } returns Optional.empty()
            every { organizationRepository.findByName("myorg") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(10L, 1L) } returns Optional.empty()

            mockMvc.perform(
                post("/api/projects/myorg")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj"}""")
            )
                .andExpect(status().isForbidden)
                .andExpect(jsonPath("$.message").value("'관리자' has no permission"))
        }

        it("개인 소유(owner가 조직명이 아님) 프로젝트를 성공적으로 생성해야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()

            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers {
                projectSlot.captured.apply { id = 100L }
            }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }

            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            val result = mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "projectName": "newproj",
                            "projectDescription": "설명",
                            "projectVcs": "GIT",
                            "projectScope": "PUBLIC"
                        }
                        """.trimIndent()
                    )
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.owner").value("admin"))
                .andExpect(jsonPath("$.name").value("newproj"))
                .andExpect(jsonPath("$.overview").value("설명"))
                .andExpect(jsonPath("$.vcs").value("GIT"))
                .andReturn()

            projectSlot.captured.projectScope shouldBe ProjectScope.PUBLIC
            projectSlot.captured.organization shouldBe null
            verify(exactly = 1) { playRepo.create() }
            val savedProjectUserSlot = slot<ProjectUser>()
            verify(exactly = 1) { projectUserRepository.save(capture(savedProjectUserSlot)) }
            savedProjectUserSlot.captured.user shouldBe siteManager
            savedProjectUserSlot.captured.role shouldBe sitemanagerRole
        }

        it("owner가 기존 조직명이고 호출자가 그 조직 admin이면 조직이 연동된 채 생성돼야 한다") {
            val org = Organization(id = 11L, name = "myorg2")
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("myorg2", "newproj") } returns Optional.empty()
            every { organizationRepository.findByName("myorg2") } returns Optional.of(org)
            every { organizationUserRepository.findByOrganizationIdAndUserId(11L, 1L) } returns
                Optional.of(OrganizationUser(user = siteManager, organization = org, role = Role(id = RoleType.ORG_ADMIN.roleType)))

            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers {
                projectSlot.captured.apply { id = 101L }
            }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/myorg2")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj"}""")
            ).andExpect(status().isCreated)

            projectSlot.captured.organization shouldBe org
        }

        it("projectScope가 없거나 알 수 없는 값이면 PRIVATE로 기본 처리돼야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj2") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 102L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj2", "projectScope": "UNKNOWN_SCOPE"}""")
            ).andExpect(status().isCreated)

            projectSlot.captured.projectScope shouldBe ProjectScope.PRIVATE
        }

        it("projectScope가 명시적으로 PRIVATE/PROTECTED이면 그 값 그대로 처리돼야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            every { projectRepository.findByOwnerAndName("admin", "scopeprivate") } returns Optional.empty()
            val privateSlot = slot<Project>()
            every { projectRepository.save(capture(privateSlot)) } answers { privateSlot.captured.apply { id = 110L } }
            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "scopeprivate", "projectScope": "PRIVATE"}""")
            ).andExpect(status().isCreated)
            privateSlot.captured.projectScope shouldBe ProjectScope.PRIVATE

            every { projectRepository.findByOwnerAndName("admin", "scopeprotected") } returns Optional.empty()
            val protectedSlot = slot<Project>()
            every { projectRepository.save(capture(protectedSlot)) } answers { protectedSlot.captured.apply { id = 111L } }
            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "scopeprotected", "projectScope": "PROTECTED"}""")
            ).andExpect(status().isCreated)
            protectedSlot.captured.projectScope shouldBe ProjectScope.PROTECTED
        }

        it("projectVcs가 없으면 GIT을 기본값으로 사용해야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj3") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 103L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj3"}""")
            ).andExpect(status().isCreated)

            projectSlot.captured.vcs shouldBe "GIT"
        }

        it("projectCreatedDate가 legacy 포맷이면 그 시각으로 파싱돼야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj4") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 104L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"projectName": "newproj4", "projectCreatedDate": "2020-01-15 PM 03:30:00 +0900"}"""
                    )
            ).andExpect(status().isCreated)

            val expectedInstant = SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH)
                .parse("2020-01-15 PM 03:30:00 +0900").toInstant()
            projectSlot.captured.createdDate shouldBe expectedInstant
        }

        it("projectCreatedDate가 파싱 불가능한 형식이면 조용히 null로 남겨야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj5") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 105L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectName": "newproj5", "projectCreatedDate": "이상한날짜"}""")
            ).andExpect(status().isCreated)

            projectSlot.captured.createdDate shouldBe null
        }

        it("members 배열의 member/manager 역할을 각각 부여해야 한다") {
            val memberUser = User(id = 20L, loginId = "member1", name = "멤버1", email = "member1@example.com")
            val managerUser = User(id = 21L, loginId = "manager1", name = "매니저1", email = "manager1@example.com")
            val memberRole = Role(id = RoleType.MEMBER.roleType)
            val managerRole = Role(id = RoleType.MANAGER.roleType)

            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj6") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 106L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { roleRepository.findById(RoleType.MEMBER.roleType) } returns Optional.of(memberRole)
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            every { userRepository.findByEmail("member1@example.com") } returns Optional.of(memberUser)
            every { userRepository.findByEmail("manager1@example.com") } returns Optional.of(managerUser)
            every { projectUserRepository.findByProjectIdAndUserId(106L, 20L) } returns Optional.empty()
            every { projectUserRepository.findByProjectIdAndUserId(106L, 21L) } returns Optional.empty()
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "projectName": "newproj6",
                            "members": [
                                {"email": "member1@example.com", "role": "member"},
                                {"email": "manager1@example.com", "role": "manager"}
                            ]
                        }
                        """.trimIndent()
                    )
            ).andExpect(status().isCreated)

            val savedSlot = mutableListOf<ProjectUser>()
            verify { projectUserRepository.save(capture(savedSlot)) }
            savedSlot.any { it.user == memberUser && it.role == memberRole } shouldBe true
            savedSlot.any { it.user == managerUser && it.role == managerRole } shouldBe true
        }

        it("존재하지 않는 이메일의 멤버는 조용히 건너뛰어야 한다") {
            every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
            every { projectRepository.findByOwnerAndName("admin", "newproj7") } returns Optional.empty()
            every { organizationRepository.findByName("admin") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 107L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            every { userRepository.findByEmail("nobody@example.com") } returns Optional.empty()
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin")
                    .principal(siteManagerAuth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"projectName": "newproj7", "members": [{"email": "nobody@example.com", "role": "member"}]}"""
                    )
            ).andExpect(status().isCreated)

            // SITEMANAGER 역할 배정(1회) 외에 멤버 배정 저장이 없어야 한다.
            verify(exactly = 1) { projectUserRepository.save(any()) }
        }
    }

    // yona ProjectApi.java:46-72 exports() 대응 (P2-46). [GL-controllers_api_ProjectApi-002;GL-controllers_api_ProjectApi-003]
    describe("GET /api/projects/{owner}/{projectName}/exports (P2-46)") {
        val manager = User(id = 30L, loginId = "manager", name = "매니저", email = "manager@example.com")
        val managerAuth = UsernamePasswordAuthenticationToken("manager", "password")
        val nonMember = User(id = 31L, loginId = "outsider", name = "외부인")
        val nonMemberAuth = UsernamePasswordAuthenticationToken("outsider", "password")
        val managerRole = Role(id = RoleType.MANAGER.roleType)

        val project = Project(
            id = 200L, owner = "acme", name = "widget", overview = "위젯 프로젝트",
            vcs = "GIT", projectScope = ProjectScope.PRIVATE
        )
        val managerProjectUser = ProjectUser(id = 900L, user = manager, project = project, role = managerRole)
        project.projectUsers.add(managerProjectUser)
        manager.projectUsers.add(managerProjectUser)

        val project2 = Project(
            id = 201L, owner = "acme", name = "widget2", overview = "위젯2 프로젝트",
            vcs = "GIT", projectScope = ProjectScope.PRIVATE
        )
        val managerProjectUser2 = ProjectUser(id = 901L, user = manager, project = project2, role = managerRole)
        project2.projectUsers.add(managerProjectUser2)
        manager.projectUsers.add(managerProjectUser2)

        it("존재하지 않는 프로젝트는 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("acme", "nope") } returns Optional.empty()

            mockMvc.perform(get("/api/projects/acme/nope/exports").principal(managerAuth))
                .andExpect(status().isNotFound)
        }

        // legacy Open API 경로 별칭 (P2-59)
        it("legacy 경로 /-_-api/v1/owners/{owner}/projects/{projectName}/exports로도 동일하게 동작해야 한다") {
            every { projectRepository.findByOwnerAndName("acme", "nope") } returns Optional.empty()

            mockMvc.perform(get("/-_-api/v1/owners/acme/projects/nope/exports").principal(managerAuth))
                .andExpect(status().isNotFound)
        }

        it("매니저/조직관리자가 아니면 403을 반환해야 한다") {
            every { projectRepository.findByOwnerAndName("acme", "widget") } returns Optional.of(project)
            every { userRepository.findByLoginId("outsider") } returns Optional.of(nonMember)

            mockMvc.perform(get("/api/projects/acme/widget/exports").principal(nonMemberAuth))
                .andExpect(status().isForbidden)
        }

        it("매니저는 프로젝트 전체(이슈/게시글/멤버/라벨/마일스톤/작성자/담당가능자)를 JSON으로 내보낼 수 있어야 한다") {
            every { projectRepository.findByOwnerAndName("acme", "widget") } returns Optional.of(project)
            every { userRepository.findByLoginId("manager") } returns Optional.of(manager)

            val assigneeUser = User(id = 40L, loginId = "assignee1", name = "담당자1", email = "assignee1@example.com")
            val issueAuthor = User(id = 41L, loginId = "issueauthor", name = "이슈작성자", email = "issueauthor@example.com")
            val postAuthor = User(id = 42L, loginId = "postauthor", name = "글작성자", email = "postauthor@example.com")
            val prContributor = User(id = 43L, loginId = "contributor1", name = "기여자1", email = "contributor1@example.com")

            val milestone = Milestone(id = 500L, title = "1.0", contents = "첫 릴리즈", project = project, state = State.OPEN)
            val category = IssueLabelCategory(id = 600L, name = "우선순위", isExclusive = true, project = project)
            val label = IssueLabel(id = 601L, name = "긴급", color = "#ff0000", category = category, project = project)

            val issueCreated = Instant.parse("2026-01-01T00:00:00Z")
            val issue = Issue(
                id = 700L, title = "버그 발생", body = "이슈 본문", project = project, number = 1L,
                authorId = issueAuthor.id, createdDate = issueCreated, updatedDate = issueCreated,
                state = State.OPEN, milestone = milestone,
                assignee = Assignee(id = 701L, user = assigneeUser, project = project)
            )
            issue.labels.add(label)

            val posting = Posting(
                id = 800L, title = "공지사항", body = "게시글 본문", project = project, number = 1L,
                authorId = postAuthor.id, createdDate = issueCreated, updatedDate = issueCreated
            )

            val topComment = IssueComment(
                id = 900L, contents = "댓글입니다", issue = issue, authorId = issueAuthor.id, createdDate = issueCreated
            )
            val replyComment = IssueComment(
                id = 901L, contents = "답글입니다", issue = issue, authorId = manager.id,
                parentComment = topComment, createdDate = issueCreated
            )
            // legacy는 부모가 top-level 목록에 없는(2단계 이상 중첩) 답글이 섞이면 NPE로 exports() 전체가
            // 죽는데, yona는 이를 재현하지 않고 조용히 무시한다 — 그 케이스도 여기서 함께 검증.
            val orphanReply = IssueComment(
                id = 902L, contents = "고아 답글", issue = issue, authorId = manager.id,
                parentComment = replyComment, createdDate = issueCreated
            )

            val attachment = Attachment(
                id = 1000L, name = "screenshot.png", hash = "abc123",
                containerType = ResourceType.ISSUE_POST, containerId = "700",
                mimeType = "image/png", size = 1024L, createdDate = issueCreated, ownerLoginId = "issueauthor"
            )

            every { issueRepository.findByProject(project) } returns listOf(issue)
            every { postingRepository.findByProject(project) } returns listOf(posting)
            every { milestoneRepository.findByProject(project) } returns listOf(milestone)
            every { issueLabelRepository.findByProject(project) } returns listOf(label)
            every { projectUserRepository.findByProjectId(200L) } returns project.projectUsers
            every { assigneeRepository.findByProjectId(200L) } returns listOf(Assignee(id = 702L, user = assigneeUser, project = project))
            every { pullRequestRepository.findByToProject(project) } returns listOf(
                PullRequest(
                    id = 1100L, number = 1L, toProject = project, fromProject = project, contributor = prContributor
                )
            )
            every { userRepository.findById(issueAuthor.id!!) } returns Optional.of(issueAuthor)
            every { userRepository.findById(postAuthor.id!!) } returns Optional.of(postAuthor)
            every { userRepository.findById(prContributor.id!!) } returns Optional.of(prContributor)
            every { userRepository.findById(manager.id!!) } returns Optional.of(manager)
            every { notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, "700") } returns "http://localhost/acme/widget/issue/1"
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, "700") } returns listOf(attachment)
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "800") } returns emptyList()
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_COMMENT, any()) } returns emptyList()
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(700L) } returns listOf(topComment, replyComment, orphanReply)
            every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(800L) } returns emptyList()

            mockMvc.perform(get("/api/projects/acme/widget/exports").principal(managerAuth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.owner").value("acme"))
                .andExpect(jsonPath("$.projectName").value("widget"))
                .andExpect(jsonPath("$.projectDescription").value("위젯 프로젝트"))
                .andExpect(jsonPath("$.projectVcs").value("GIT"))
                .andExpect(jsonPath("$.projectScope").value("PRIVATE"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.members[0].loginId").value("manager"))
                .andExpect(jsonPath("$.members[0].role").value(managerRole.name))
                .andExpect(jsonPath("$.assignees[0].loginId").value("assignee1"))
                // authors: 이슈작성자, 게시글작성자, PR기여자 순서로 중복없이 모두 포함
                .andExpect(jsonPath("$.authors[0].loginId").value("issueauthor"))
                .andExpect(jsonPath("$.authors[1].loginId").value("postauthor"))
                .andExpect(jsonPath("$.authors[2].loginId").value("contributor1"))
                .andExpect(jsonPath("$.issueCount").value(1))
                .andExpect(jsonPath("$.postCount").value(1))
                .andExpect(jsonPath("$.milestoneCount").value(1))
                // project 최상위 labels는 isExclusive 포함
                .andExpect(jsonPath("$.labels[0].labelName").value("긴급"))
                .andExpect(jsonPath("$.labels[0].isExclusive").value(true))
                .andExpect(jsonPath("$.milestones[0].id").value(500))
                .andExpect(jsonPath("$.milestones[0].state").value("open"))
                // 이슈 본문
                .andExpect(jsonPath("$.issues[0].id").value(700))
                .andExpect(jsonPath("$.issues[0].type").value("ISSUE_POST"))
                .andExpect(jsonPath("$.issues[0].author.loginId").value("issueauthor"))
                .andExpect(jsonPath("$.issues[0].assignees[0].loginId").value("assignee1"))
                .andExpect(jsonPath("$.issues[0].state").value("OPEN"))
                // 이슈 안의 labels는 isExclusive가 없어야 한다(project 최상위 labels와 다른 형태)
                .andExpect(jsonPath("$.issues[0].labels[0].labelName").value("긴급"))
                .andExpect(jsonPath("$.issues[0].labels[0].isExclusive").doesNotExist())
                .andExpect(jsonPath("$.issues[0].milestoneId").value(500))
                .andExpect(jsonPath("$.issues[0].milestoneTitle").value("1.0"))
                .andExpect(jsonPath("$.issues[0].refUrl").value("http://localhost/acme/widget/issue/1"))
                .andExpect(jsonPath("$.issues[0].attachments[0].name").value("screenshot.png"))
                // 댓글: top-level 1개(답글 포함), 고아 답글은 NPE 없이 조용히 누락
                .andExpect(jsonPath("$.issues[0].comments.length()").value(1))
                .andExpect(jsonPath("$.issues[0].comments[0].id").value(900))
                .andExpect(jsonPath("$.issues[0].comments[0].childComments[0].id").value(901))
                .andExpect(jsonPath("$.issues[0].comments[0].childComments[0].childComments").doesNotExist())
                // 게시글 본문 — 이슈 전용 필드(assignees/state/labels/milestoneId/dueDate/refUrl) 없음
                .andExpect(jsonPath("$.posts[0].id").value(800))
                .andExpect(jsonPath("$.posts[0].type").value("BOARD_POST"))
                .andExpect(jsonPath("$.posts[0].author.loginId").value("postauthor"))
                .andExpect(jsonPath("$.posts[0].state").doesNotExist())
                .andExpect(jsonPath("$.posts[0].assignees").doesNotExist())
                .andExpect(jsonPath("$.posts[0].refUrl").doesNotExist())
        }

        it("담당자/마일스톤/라벨이 없는 이슈, 마감일이 있는 마일스톤/이슈, 저자가 겹치는 경우, 첨부있는 게시글/댓글, 중첩된 게시글 댓글, 기여자 없는 PR까지 잔여 분기를 모두 커버한다") {
            every { projectRepository.findByOwnerAndName("acme", "widget2") } returns Optional.of(project2)
            every { userRepository.findByLoginId("manager") } returns Optional.of(manager)

            val sharedAuthor = User(id = 44L, loginId = "shared", name = "공동작성자", email = "shared@example.com")
            val dueInstant = Instant.parse("2026-06-01T00:00:00Z")
            val milestoneWithDue = Milestone(
                id = 501L, title = "1.1", contents = "패치", project = project2, state = State.OPEN, dueDate = dueInstant
            )

            // 담당자/라벨/마일스톤 전부 없고, 마감일만 있는 이슈 — 작성자는 posting과 동일인(dedup 분기)
            val bareIssue = Issue(
                id = 710L, title = "담당자 없는 이슈", project = project2, number = 2L,
                authorId = sharedAuthor.id, createdDate = dueInstant, updatedDate = dueInstant,
                state = State.OPEN, dueDate = dueInstant
            )

            val posting2 = Posting(
                id = 810L, title = "첨부있는 공지", body = "본문", project = project2, number = 2L,
                authorId = sharedAuthor.id, createdDate = dueInstant, updatedDate = dueInstant
            )
            val postingAttachment = Attachment(
                id = 1001L, name = "file.pdf", hash = "def456",
                containerType = ResourceType.BOARD_POST, containerId = "810",
                mimeType = "application/pdf", size = 2048L, createdDate = null, ownerLoginId = "shared"
            )

            val commentWithAttachment = IssueComment(
                id = 910L, contents = "첨부있는 댓글", issue = bareIssue, authorId = null, createdDate = dueInstant
            )
            val commentAttachment = Attachment(
                id = 1002L, name = "log.txt", hash = "ghi789",
                containerType = ResourceType.ISSUE_COMMENT, containerId = "910",
                mimeType = "text/plain", size = 10L, createdDate = dueInstant, ownerLoginId = "shared"
            )

            val postTopComment = PostingComment(
                id = 920L, contents = "게시글댓글", posting = posting2, authorId = sharedAuthor.id, createdDate = dueInstant
            )
            val postReplyComment = PostingComment(
                id = 921L, contents = "게시글답글", posting = posting2, authorId = sharedAuthor.id,
                parentComment = postTopComment, createdDate = dueInstant
            )
            // 3단계 중첩(대댓글의 대댓글) — legacy는 NPE지만 yona는 조용히 무시해야 한다(게시글 댓글 쪽)
            val postOrphanReply = PostingComment(
                id = 922L, contents = "게시글 고아 답글", posting = posting2, authorId = sharedAuthor.id,
                parentComment = postReplyComment, createdDate = dueInstant
            )

            every { issueRepository.findByProject(project2) } returns listOf(bareIssue)
            every { postingRepository.findByProject(project2) } returns listOf(posting2)
            every { milestoneRepository.findByProject(project2) } returns listOf(milestoneWithDue)
            every { issueLabelRepository.findByProject(project2) } returns emptyList()
            every { projectUserRepository.findByProjectId(201L) } returns project2.projectUsers
            every { assigneeRepository.findByProjectId(201L) } returns emptyList()
            // PR 기여자도 이슈/게시글 작성자와 동일인 — findAuthors의 dedup(containsKey) 분기 커버
            every { pullRequestRepository.findByToProject(project2) } returns listOf(
                PullRequest(
                    id = 1101L, number = 2L, toProject = project2, fromProject = project2, contributor = sharedAuthor
                )
            )
            every { userRepository.findById(sharedAuthor.id!!) } returns Optional.of(sharedAuthor)
            every { userRepository.findById(manager.id!!) } returns Optional.of(manager)
            every { notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, "710") } returns "http://localhost/acme/widget2/issue/2"
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, "710") } returns emptyList()
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "810") } returns listOf(postingAttachment)
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_COMMENT, "910") } returns listOf(commentAttachment)
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.NONISSUE_COMMENT, any()) } returns emptyList()
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(710L) } returns listOf(commentWithAttachment)
            every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(810L) } returns
                listOf(postTopComment, postReplyComment, postOrphanReply)

            mockMvc.perform(get("/api/projects/acme/widget2/exports").principal(managerAuth))
                .andExpect(status().isOk)
                // 담당자 없는 이슈: assignees 필드 자체가 없어야 한다
                .andExpect(jsonPath("$.issues[0].assignees").doesNotExist())
                .andExpect(jsonPath("$.issues[0].labels").doesNotExist())
                .andExpect(jsonPath("$.issues[0].milestoneId").doesNotExist())
                .andExpect(jsonPath("$.issues[0].dueDate").exists())
                .andExpect(jsonPath("$.milestones[0].dueDate").exists())
                // 저자 없는 댓글: author의 loginId/name/email이 전부 null
                .andExpect(jsonPath("$.issues[0].comments[0].author.loginId").doesNotExist())
                .andExpect(jsonPath("$.issues[0].comments[0].attachments[0].name").value("log.txt"))
                .andExpect(jsonPath("$.posts[0].attachments[0].name").value("file.pdf"))
                .andExpect(jsonPath("$.posts[0].attachments[0].createdDate").doesNotExist())
                .andExpect(jsonPath("$.posts[0].comments[0].id").value(920))
                .andExpect(jsonPath("$.posts[0].comments[0].childComments[0].id").value(921))
                // authors: 이슈작성자==게시글작성자==PR기여자가 전부 동일인이라 dedup으로 1번만 나와야 한다
                .andExpect(jsonPath("$.authors.length()").value(1))
                .andExpect(jsonPath("$.authors[0].loginId").value("shared"))
        }

        it("작성자가 없는(authorId=null) 이슈, updatedDate가 없는 경우, id가 없는(미영속) 최상위 댓글은 목록에서 빠져야 한다") {
            val project3 = Project(
                id = 202L, owner = "acme", name = "widget3", vcs = "GIT", projectScope = ProjectScope.PRIVATE
            )
            val managerProjectUser3 = ProjectUser(id = 902L, user = manager, project = project3, role = managerRole)
            project3.projectUsers.add(managerProjectUser3)
            manager.projectUsers.add(managerProjectUser3)

            every { projectRepository.findByOwnerAndName("acme", "widget3") } returns Optional.of(project3)
            every { userRepository.findByLoginId("manager") } returns Optional.of(manager)

            val when0 = Instant.parse("2026-03-01T00:00:00Z")
            val issue3 = Issue(
                id = 720L, title = "작성자 없는 이슈", project = project3, number = 3L,
                authorId = null, createdDate = when0, updatedDate = null, state = State.OPEN
            )
            val posting3 = Posting(
                id = 820L, title = "게시글3", body = "본문", project = project3, number = 3L,
                authorId = null, createdDate = when0, updatedDate = when0
            )
            // id가 없는(아직 저장 전) 최상위 댓글 — composeIssueCommentsJson/composePostingCommentsJson이
            // topLevel에 추가하지 않고 조용히 걸러내야 한다(line 420/439 분기).
            val unsavedIssueComment = IssueComment(
                id = null, contents = "미저장 댓글", issue = issue3, authorId = null, createdDate = when0
            )
            val unsavedPostingComment = PostingComment(
                id = null, contents = "미저장 게시글댓글", posting = posting3, authorId = null, createdDate = when0
            )

            every { issueRepository.findByProject(project3) } returns listOf(issue3)
            every { postingRepository.findByProject(project3) } returns listOf(posting3)
            every { milestoneRepository.findByProject(project3) } returns emptyList()
            every { issueLabelRepository.findByProject(project3) } returns emptyList()
            every { projectUserRepository.findByProjectId(202L) } returns project3.projectUsers
            every { assigneeRepository.findByProjectId(202L) } returns emptyList()
            every { pullRequestRepository.findByToProject(project3) } returns emptyList()
            every { notificationUrlResolver.getUrl(ResourceType.ISSUE_POST, "720") } returns "http://localhost/acme/widget3/issue/3"
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ISSUE_POST, "720") } returns emptyList()
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "820") } returns emptyList()
            every { issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(720L) } returns listOf(unsavedIssueComment)
            every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(820L) } returns listOf(unsavedPostingComment)

            mockMvc.perform(get("/api/projects/acme/widget3/exports").principal(managerAuth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.authors").isEmpty)
                .andExpect(jsonPath("$.issues[0].author.loginId").doesNotExist())
                .andExpect(jsonPath("$.issues[0].updatedAt").value(""))
                .andExpect(jsonPath("$.issues[0].comments").doesNotExist())
                .andExpect(jsonPath("$.posts[0].comments").doesNotExist())
        }
    }

    describe("addProjectMembers() 잔여 분기 (P2-45)") {
        val siteManager2 = User(id = 60L, loginId = "admin2", name = "관리자2", state = UserState.SITE_ADMIN)
        val siteManagerAuth2 = UsernamePasswordAuthenticationToken("admin2", "password")
        val sitemanagerRole2 = Role(id = RoleType.SITEMANAGER.roleType)

        it("알 수 없는 role 값이 오면 경고만 남기고 조용히 건너뛰어야 한다") {
            val someUser = User(id = 61L, loginId = "someone", name = "누군가", email = "someone@example.com")
            every { userRepository.findByLoginId("admin2") } returns Optional.of(siteManager2)
            every { projectRepository.findByOwnerAndName("admin2", "projA") } returns Optional.empty()
            every { organizationRepository.findByName("admin2") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 200L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole2)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            every { userRepository.findByEmail("someone@example.com") } returns Optional.of(someUser)
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin2")
                    .principal(siteManagerAuth2)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"projectName": "projA", "members": [{"email": "someone@example.com", "role": "owner"}]}"""
                    )
            ).andExpect(status().isCreated)

            // SITEMANAGER 역할 배정(1회) 외에 멤버 배정 저장이 없어야 한다.
            verify(exactly = 1) { projectUserRepository.save(any()) }
        }

        it("역할 자체가 DB에 없으면(roleRepository 조회 실패) 조용히 건너뛰어야 한다") {
            val someUser2 = User(id = 62L, loginId = "someone2", name = "누군가2", email = "someone2@example.com")
            every { userRepository.findByLoginId("admin2") } returns Optional.of(siteManager2)
            every { projectRepository.findByOwnerAndName("admin2", "projB") } returns Optional.empty()
            every { organizationRepository.findByName("admin2") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 201L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole2)
            every { roleRepository.findById(RoleType.MEMBER.roleType) } returns Optional.empty()
            every { projectUserRepository.save(any()) } answers { firstArg() }
            every { userRepository.findByEmail("someone2@example.com") } returns Optional.of(someUser2)
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin2")
                    .principal(siteManagerAuth2)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"projectName": "projB", "members": [{"email": "someone2@example.com", "role": "member"}]}"""
                    )
            ).andExpect(status().isCreated)

            verify(exactly = 1) { projectUserRepository.save(any()) }
        }

        it("이미 해당 프로젝트에 속한 멤버면 기존 행의 역할을 갱신해야 한다") {
            val existingMember = User(id = 63L, loginId = "existing", name = "기존멤버", email = "existing@example.com")
            val memberRole2 = Role(id = RoleType.MEMBER.roleType)
            val managerRole2 = Role(id = RoleType.MANAGER.roleType)
            every { userRepository.findByLoginId("admin2") } returns Optional.of(siteManager2)
            every { projectRepository.findByOwnerAndName("admin2", "projC") } returns Optional.empty()
            every { organizationRepository.findByName("admin2") } returns Optional.empty()
            val projectSlot = slot<Project>()
            every { projectRepository.save(capture(projectSlot)) } answers { projectSlot.captured.apply { id = 202L } }
            every { roleRepository.findById(RoleType.SITEMANAGER.roleType) } returns Optional.of(sitemanagerRole2)
            every { roleRepository.findById(RoleType.MANAGER.roleType) } returns Optional.of(managerRole2)
            every { projectUserRepository.save(any()) } answers { firstArg() }
            every { userRepository.findByEmail("existing@example.com") } returns Optional.of(existingMember)
            val existingProjectUser = ProjectUser(id = 950L, project = Project(id = 202L), user = existingMember, role = memberRole2)
            every { projectUserRepository.findByProjectIdAndUserId(202L, 63L) } returns Optional.of(existingProjectUser)
            val playRepo = mockk<PlayRepository>(relaxed = true)
            every { repositoryService.getRepository(any()) } returns playRepo

            mockMvc.perform(
                post("/api/projects/admin2")
                    .principal(siteManagerAuth2)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"projectName": "projC", "members": [{"email": "existing@example.com", "role": "manager"}]}"""
                    )
            ).andExpect(status().isCreated)

            existingProjectUser.role shouldBe managerRole2
            verify(exactly = 1) { projectUserRepository.save(existingProjectUser) }
        }
    }
})
