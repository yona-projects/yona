package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional

import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import org.springframework.context.MessageSource
import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.support.MarkdownService
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.project.ProjectTransferRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelService
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.project.RecentProjectRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.ui.ExtendedModelMap
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import com.github.yonaprojects.yona.domain.vcs.Commit
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.ProjectTransfer
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.UserState
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import jakarta.servlet.http.HttpServletRequest
import java.util.Date
import java.util.Locale
import java.io.File
import java.time.Instant
import io.mockk.spyk
import com.github.yonaprojects.yona.domain.vcs.SvnRepository
import com.github.yonaprojects.yona.domain.issue.DuplicateLabelCategoryNameException
import org.springframework.data.domain.Sort

class ProjectViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val repositoryService = mockk<RepositoryService>()
    val projectService = mockk<ProjectService>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val attachmentService = mockk<AttachmentService>()
    val organizationRepository = mockk<OrganizationRepository>()
    val messageSource = mockk<MessageSource>()
    val mailService = mockk<MailService>()
    val markdownService = mockk<MarkdownService>()
    val roleRepository = mockk<RoleRepository>()
    val projectTransferRepository = mockk<ProjectTransferRepository>()
    val issueLabelService = mockk<IssueLabelService>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val watchService = mockk<WatchService>()
    val recentProjectRepository = mockk<RecentProjectRepository>()
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

    val projectViewController = ProjectViewController(
        projectRepository,
        projectUserRepository,
        userRepository,
        repositoryService,
        projectService,
        organizationUserRepository,
        attachmentRepository,
        attachmentService,
        organizationRepository,
        messageSource,
        mailService,
        markdownService,
        roleRepository,
        projectTransferRepository,
        issueLabelService,
        issueRepository,
        postingRepository,
        pullRequestRepository,
        milestoneRepository,
        watchService,
        recentProjectRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(projectViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(
            projectRepository, projectUserRepository, userRepository, repositoryService, projectService,
            organizationUserRepository, attachmentRepository, attachmentService, organizationRepository,
            messageSource, mailService, markdownService, roleRepository, projectTransferRepository,
            issueLabelService, issueRepository, postingRepository, pullRequestRepository, milestoneRepository,
            watchService, recentProjectRepository
        )
        every { recentProjectRepository.recordVisit(any(), any()) } just Runs
        every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
        every {
            milestoneRepository.findByProjectAndState(any(), any(), any<Sort>())
        } returns emptyList()
    }

    describe("ProjectViewController 템플릿 연동 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerRole = Role(id = RoleType.MANAGER.roleType)
        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = managerRole)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /{owner}/{projectName}") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 project/home 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = managerRole))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectId(1L) } returns listOf(projectUser)
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()

                mockMvc.perform(get("/owner/TestProj").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/home"))
                    .andExpect(model().attributeExists("project", "projectUsers"))
            }

            // yona partial_readme.scala.html:41 Markdown.renderFileInReadme() 대응 (P1-139) —
            // README 렌더링에 상대경로 링크 치환이 포함된 renderFileInReadme()를 써야 한다(일반 render() 아님).
            it("readme 탭이면 README.md를 renderFileInReadme로 렌더링해 markdownHtml에 담아야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 901L, user = memberUser, project = project, role = managerRole))
                val playRepo = mockk<PlayRepository>()
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectId(1L) } returns listOf(projectUser)
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()
                every { repositoryService.getRepository(project) } returns playRepo
                every { playRepo.isFile("README.md") } returns true
                every { playRepo.getRawFile("HEAD", "README.md") } returns "# 안내".toByteArray(Charsets.UTF_8)
                every { markdownService.renderFileInReadme("# 안내", project) } returns "<h1>안내</h1>"

                mockMvc.perform(get("/owner/TestProj").param("tabId", "readme").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/home"))
                    .andExpect(model().attribute("readmeHtml", "<h1>안내</h1>"))
                verify(exactly = 0) { markdownService.render("# 안내", true, project) }
            }

            // yona partial_readme.scala.html:38-42 대응 (P2-42) — 코드브라우저 메뉴가 꺼진
            // 프로젝트는 게시판 README 글(readme=true) 본문을 git 파일 대신 우선 사용한다.
            it("코드브라우저가 꺼진 프로젝트는 게시판 README 글의 본문을 렌더링해야 한다") {
                val noCodeProject = Project(id = 5L, name = "NoCodeProj", owner = "owner", projectScope = ProjectScope.PRIVATE, isCodeEnabled = false)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 902L, user = memberUser, project = noCodeProject, role = managerRole))
                val readmePosting = Posting(
                    id = 700L, title = "README", body = "게시판 README 본문", project = noCodeProject, number = 1L, readme = true
                )
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoCodeProj") } returns Optional.of(noCodeProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(5L, 10L) } returns true
                every { projectUserRepository.findByProjectId(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(noCodeProject) } returns playRepo
                every { playRepo.isFile("README.md") } returns true
                every { postingRepository.findByProjectAndReadme(noCodeProject, true) } returns listOf(readmePosting)
                every { markdownService.render("게시판 README 본문", true, noCodeProject) } returns "<p>게시판 README 본문</p>"

                mockMvc.perform(get("/owner/NoCodeProj").param("tabId", "readme").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("readmeHtml", "<p>게시판 README 본문</p>"))
                verify(exactly = 0) { markdownService.renderFileInReadme(any(), any()) }
            }

            it("코드브라우저가 꺼져도 게시판 README 글이 없으면 기존처럼 git 파일을 렌더링해야 한다") {
                val noCodeProject = Project(id = 6L, name = "NoCodeProj2", owner = "owner", projectScope = ProjectScope.PRIVATE, isCodeEnabled = false)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 903L, user = memberUser, project = noCodeProject, role = managerRole))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoCodeProj2") } returns Optional.of(noCodeProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(6L, 10L) } returns true
                every { projectUserRepository.findByProjectId(6L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()
                val playRepo = mockk<PlayRepository>()
                every { repositoryService.getRepository(noCodeProject) } returns playRepo
                every { playRepo.isFile("README.md") } returns true
                every { playRepo.getRawFile("HEAD", "README.md") } returns "# git readme".toByteArray(Charsets.UTF_8)
                every { postingRepository.findByProjectAndReadme(noCodeProject, true) } returns emptyList()
                every { markdownService.renderFileInReadme("# git readme", noCodeProject) } returns "<h1>git readme</h1>"

                mockMvc.perform(get("/owner/NoCodeProj2").param("tabId", "readme").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("readmeHtml", "<h1>git readme</h1>"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
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
                val groupProject = Project(id = 11L, name = "group-project", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = groupOrg)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-project") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(11L, 10L) } returns false
                every { projectUserRepository.findByProjectId(11L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()

                mockMvc.perform(get("/owner/group-project").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/home"))
            }

            // yona GitApp.java:95-104 findByPreviousPlaceOf() 폴백 대응, 웹 라우트 배선 (P1-100).
            it("프로젝트가 이전(rename/소유자 변경)된 뒤에도 예전 owner/name URL로 접근하면 새 위치의 프로젝트를 찾아야 한다") {
                val movedProject = Project(id = 20L, name = "new-name", owner = "new-owner", projectScope = ProjectScope.PUBLIC)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("old-owner", "old-name") } returns Optional.of(movedProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(20L, 10L) } returns false
                every { projectUserRepository.findByProjectId(20L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { watchService.findWatchers(any(), any()) } returns emptySet()

                mockMvc.perform(get("/old-owner/old-name").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/home"))
                    .andExpect(model().attribute("project", movedProject))
            }
        }

        describe("GET /{owner}/{projectName}/members") {
            it("멤버라면 200 OK와 project/members 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 901L, user = memberUser, project = project, role = managerRole))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectId(1L) } returns listOf(projectUser)

                mockMvc.perform(get("/owner/TestProj/members").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/members"))
                    .andExpect(model().attributeExists("project", "projectUsers"))
            }
        }

        describe("GET /{owner}/{projectName}/setting") {
            it("MANAGER 권한을 지닌 멤버라면 200 OK와 project/setting 뷰를 반환해야 한다") {
                val playRepository = mockk<PlayRepository>()
                every { repositoryService.getRepository(project) } returns playRepository
                every { playRepository.getRefNames() } returns listOf("refs/heads/master")
                every { playRepository.getDefaultBranch() } returns "refs/heads/master"

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)

                mockMvc.perform(get("/owner/TestProj/setting").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/setting"))
                    .andExpect(model().attributeExists("project", "branches", "defaultBranch"))
            }

            it("MANAGER 권한이 없는 일반 멤버라면 403 Forbidden 뷰를 반환해야 한다") {
                val memberRole = Role(id = RoleType.MEMBER.roleType)
                val memberProjectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(memberProjectUser)

                mockMvc.perform(get("/owner/TestProj/setting").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }
        }

        describe("GET /{owner}/{projectName}/changeVCS") {
            it("MANAGER 권한이 있는 멤버는 change_vcs 뷰를 반환받아야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)

                mockMvc.perform(get("/owner/TestProj/changeVCS").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("project/change_vcs"))
            }
        }

        describe("POST /{owner}/{projectName}/changeVCS") {
            it("MANAGER 권한을 지닌 멤버는 VCS 변경을 성공적으로 요청할 수 있어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectService.changeVCS(1L) } returns project

                mockMvc.perform(MockMvcRequestBuilders.post("/owner/TestProj/changeVCS").principal(userAuth))
                    .andExpect(status().isNoContent)
            }
        }

        // yona ProjectApp.java:168-186 newProject()의 "owner가 기존 조직명이면 그 조직 admin만
        // 생성 가능" 가드 + "그 조직에 project.organization 연동" 대응 (P2-34). [GL-controllers_ProjectApp-016;GL-controllers_ProjectApp-017;GL-controllers_ProjectApp-018]
        describe("POST /projectform (프로젝트 생성)") {
            fun newProjectRequest(owner: String) =
                MockMvcRequestBuilders.post("/projectform")
                    .principal(userAuth)
                    .param("owner", owner)
                    .param("name", "newproj")
                    .param("overview", "설명")
                    .param("projectScope", "PUBLIC")
                    .param("vcs", "GIT")

            it("owner가 조직명이 아니면(개인 프로젝트) 조직 가드 없이 생성된다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationRepository.findByName("testuser") } returns Optional.empty()
                val created = Project(id = 900L, owner = "testuser", name = "newproj")
                every { projectService.createProject(any(), user) } returns created
                every { watchService.watch(any(), any(), any()) } just Runs

                mockMvc.perform(newProjectRequest("testuser"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/testuser/newproj"))

                verify(exactly = 1) { projectService.createProject(any(), user) }
            }

            it("owner가 기존 조직명이고 사용자가 그 조직의 admin이면 조직이 연동된 채 생성된다") {
                val org = Organization(id = 50L, name = "myorg")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationRepository.findByName("myorg") } returns Optional.of(org)
                every { organizationUserRepository.findByOrganizationIdAndUserId(50L, 10L) } returns
                    Optional.of(OrganizationUser(user = user, organization = org, role = Role(id = RoleType.ORG_ADMIN.roleType)))
                val projectSlot = slot<Project>()
                val created = Project(id = 901L, owner = "myorg", name = "newproj", organization = org)
                every { projectService.createProject(capture(projectSlot), user) } returns created
                every { watchService.watch(any(), any(), any()) } just Runs

                mockMvc.perform(newProjectRequest("myorg"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/myorg/newproj"))

                projectSlot.captured.organization shouldBe org
            }

            it("owner가 기존 조직명인데 사용자가 그 조직의 admin이 아니면 403 Forbidden을 반환하고 생성하지 않는다") {
                val org = Organization(id = 51L, name = "otherorg")
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationRepository.findByName("otherorg") } returns Optional.of(org)
                every { organizationUserRepository.findByOrganizationIdAndUserId(51L, 10L) } returns Optional.empty()

                mockMvc.perform(newProjectRequest("otherorg"))
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { projectService.createProject(any(), any()) }
            }
        }

        describe("GET /{owner}/{projectName}/code/{branch}/download") {
            val memberOnlyProject = Project(id = 4L, owner = "owner", name = "memberonly-project", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")

            it("[Test-12-5-1] 공개 프로젝트이지만 isCodeAccessibleMemberOnly가 true이고 비멤버인 경우 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "memberonly-project") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(4L, 10L) } returns false

                mockMvc.perform(get("/owner/memberonly-project/code/main/download").principal(userAuth))
                    .andExpect(status().isForbidden)
            }

            it("[Test-12-5-2] 공개 프로젝트이며 isCodeAccessibleMemberOnly가 true이고 멤버인 경우 200 OK와 올바른 zip 출력을 해야 한다") {
                val playRepo = mockk<PlayRepository>()
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "memberonly-project") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(4L, 10L) } returns true
                every { repositoryService.getRepository(memberOnlyProject) } returns playRepo
                every {
                    repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "")
                } returns listOf(mockk())
                every { playRepo.getArchive(any(), "main") } returns Unit

                mockMvc.perform(get("/owner/memberonly-project/code/main/download").principal(userAuth))
                    .andExpect(status().isOk)
            }

            // yona CodeApp.java:135-164 download()의 getMetaDataFromAncestorDirectories() 존재
            // 검증 대응 (P2-30) — 존재하지 않는 브랜치를 요청하면 아카이브 스트리밍을 시도하기 전에 [GL-controllers_CodeApp-006]
            // 404로 명확히 거부해야 한다(응답 헤더를 이미 써버린 뒤 스트리밍 도중 예외가 나는 것을
            // 방지).
            it("존재하지 않는 브랜치면 아카이브를 생성하지 않고 404를 반환해야 한다 (P2-30)") {
                val playRepo = mockk<PlayRepository>()
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "memberonly-project") } returns Optional.of(memberOnlyProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(4L, 10L) } returns true
                every { repositoryService.getRepository(memberOnlyProject) } returns playRepo
                every {
                    repositoryService.getMetaDataFromAncestorDirectories(playRepo, "no-such-branch", "")
                } returns null

                mockMvc.perform(get("/owner/memberonly-project/code/no-such-branch/download").principal(userAuth))
                    .andExpect(status().isNotFound)

                verify(exactly = 0) { playRepo.getArchive(any(), any()) }
            }
        }

        // yona IssueLabelApp.newLabel/delete/update/updateCategory/copyLabels() 대응 (P-템플릿 #108
        // 재검토, TASK-0262) — project/issuelabels.html이 REST JSON 커스텀 구현 대신 legacy와 동일한
        // 폼 제출 라우트를 쓰도록 교체하며 신설.
        describe("이슈 라벨/카테고리 CRUD 폼 라우트") {
            val labelProject = Project(id = 5L, name = "LabelProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val labelManager = User(id = 50L, loginId = "labelmanager", name = "라벨매니저")
            labelManager.projectUsers.add(ProjectUser(id = 500L, user = labelManager, project = labelProject, role = managerRole))
            val labelManagerAuth = UsernamePasswordAuthenticationToken("labelmanager", "password")

            val outsider = User(id = 60L, loginId = "labeloutsider", name = "외부인")
            val outsiderAuth = UsernamePasswordAuthenticationToken("labeloutsider", "password")

            val labelCategory = IssueLabelCategory(id = 1L, name = "카테고리", project = labelProject)
            val existingLabel = IssueLabel(id = 10L, name = "기존라벨", color = "#ff0000", category = labelCategory, project = labelProject)

            beforeTest {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "LabelProj") } returns Optional.of(labelProject)
                every { userRepository.findByLoginId("labelmanager") } returns Optional.of(labelManager)
                every { userRepository.findByLoginId("labeloutsider") } returns Optional.of(outsider)
                // yona-wiki P3-02 12라운드 — update/deleteLabelForm()이 id(및 category.id)가 project
                // 소속인지 확인하는 가드를 추가했으므로, 기본값으로 "10번 라벨/1번 카테고리는 이
                // 프로젝트 소속"이라고 스텁해둔다(교차 프로젝트 가드 테스트는 이 기본값을 덮어쓴다).
                every { issueLabelService.getLabels(5L) } returns listOf(existingLabel)
                every { issueLabelService.getCategories(5L) } returns listOf(labelCategory)
            }

            describe("POST /{owner}/{projectName}/issue/labels") {
                it("생성 권한이 있으면 201 Created와 새 라벨 JSON을 반환해야 한다") {
                    val category = IssueLabelCategory(id = 1L, name = "새카테고리", project = labelProject)
                    val newLabel = IssueLabel(id = 10L, name = "새라벨", color = "#2196f3", category = category, project = labelProject)
                    every {
                        issueLabelService.newLabelByCategoryName(5L, "새카테고리", false, "새라벨", "#2196f3")
                    } returns newLabel

                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/labels")
                            .principal(labelManagerAuth)
                            .param("labelName", "새라벨")
                            .param("labelColor", "#2196f3")
                            .param("categoryName", "새카테고리")
                    ).andExpect(status().isCreated)
                        .andExpect(jsonPath("$.name").value("새라벨"))
                        .andExpect(jsonPath("$.category").value("새카테고리"))
                }

                it("이미 같은 카테고리+이름의 라벨이 있으면 204 No Content를 반환해야 한다") {
                    every {
                        issueLabelService.newLabelByCategoryName(5L, "기존카테고리", false, "중복라벨", "#000000")
                    } returns null

                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/labels")
                            .principal(labelManagerAuth)
                            .param("labelName", "중복라벨")
                            .param("labelColor", "#000000")
                            .param("categoryName", "기존카테고리")
                    ).andExpect(status().isNoContent)
                }

                it("프로젝트 멤버가 아니면 403 Forbidden을 반환해야 한다") {
                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/labels")
                            .principal(outsiderAuth)
                            .param("labelName", "x")
                            .param("labelColor", "#000000")
                            .param("categoryName", "y")
                    ).andExpect(status().isForbidden)

                    verify(exactly = 0) { issueLabelService.newLabelByCategoryName(any(), any(), any(), any(), any()) }
                }
            }

            describe("POST /{owner}/{projectName}/issue/label/{id}/delete") {
                it("_method=delete와 함께 요청하면 라벨을 삭제하고 200 OK를 반환해야 한다") {
                    every { issueLabelService.deleteLabel(10L) } just Runs

                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/label/10/delete")
                            .principal(labelManagerAuth)
                            .param("_method", "delete")
                    ).andExpect(status().isOk)

                    verify(exactly = 1) { issueLabelService.deleteLabel(10L) }
                }

                it("_method가 delete가 아니면 400 Bad Request를 반환해야 한다") {
                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/label/10/delete")
                            .principal(labelManagerAuth)
                            .param("_method", "put")
                    ).andExpect(status().isBadRequest)

                    verify(exactly = 0) { issueLabelService.deleteLabel(any()) }
                }

                it("프로젝트 멤버가 아니면 403 Forbidden을 반환해야 한다") {
                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/label/10/delete")
                            .principal(outsiderAuth)
                            .param("_method", "delete")
                    ).andExpect(status().isForbidden)
                }

                // yona-wiki P3-02 12라운드(실서버+실 CLI로 재현한 실제 버그) — bob이 자기 프로젝트
                // (bob/repo-b)에 대한 라벨 삭제 권한만으로, URL의 project는 자기 것을 그대로 두고
                // id만 alice 프로젝트의 라벨 번호로 바꿔 호출해 alice의 라벨을 지울 수 있었다.
                it("id가 이 프로젝트 소속 라벨이 아니면 404 Not Found를 반환하고 삭제하지 않아야 한다") {
                    every { issueLabelService.getLabels(5L) } returns emptyList()

                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/issue/label/10/delete")
                            .principal(labelManagerAuth)
                            .param("_method", "delete")
                    ).andExpect(status().isNotFound)

                    verify(exactly = 0) { issueLabelService.deleteLabel(any()) }
                }
            }

            describe("PUT /{owner}/{projectName}/issue/label/{id}") {
                it("수정 권한이 있으면 라벨을 수정하고 200 OK를 반환해야 한다") {
                    val category = IssueLabelCategory(id = 1L, name = "카테고리", project = labelProject)
                    every {
                        issueLabelService.updateLabel(10L, "수정된이름", "#ff0000", 1L)
                    } returns IssueLabel(id = 10L, name = "수정된이름", color = "#ff0000", category = category, project = labelProject)

                    mockMvc.perform(
                        MockMvcRequestBuilders.put("/owner/LabelProj/issue/label/10")
                            .principal(labelManagerAuth)
                            .param("name", "수정된이름")
                            .param("color", "#ff0000")
                            .param("category.id", "1")
                    ).andExpect(status().isOk)

                    verify(exactly = 1) { issueLabelService.updateLabel(10L, "수정된이름", "#ff0000", 1L) }
                }

                // yona-wiki P3-02 12라운드 — delete와 동일한 근본원인의 update 버전. id가 이 프로젝트
                // 소속이 아니면 수정 자체를 거부해야 한다(그렇지 않으면 남의 라벨을 자기 프로젝트
                // 권한만으로 덮어쓸 수 있다 — 실서버+실 CLI로 재현 확인).
                it("id가 이 프로젝트 소속 라벨이 아니면 404 Not Found를 반환하고 수정하지 않아야 한다") {
                    every { issueLabelService.getLabels(5L) } returns emptyList()

                    mockMvc.perform(
                        MockMvcRequestBuilders.put("/owner/LabelProj/issue/label/10")
                            .principal(labelManagerAuth)
                            .param("name", "해킹된이름")
                            .param("color", "#000000")
                            .param("category.id", "1")
                    ).andExpect(status().isNotFound)

                    verify(exactly = 0) { issueLabelService.updateLabel(any(), any(), any(), any()) }
                }

                // category.id 쪽 교차 프로젝트 가드 — id는 이 프로젝트 소속이지만 category.id를 다른
                // 프로젝트의 카테고리 번호로 바꿔치기하면 라벨이 남의 프로젝트 카테고리로 재배정될 수
                // 있었다.
                it("category.id가 이 프로젝트 소속 카테고리가 아니면 400 Bad Request를 반환하고 수정하지 않아야 한다") {
                    every { issueLabelService.getCategories(5L) } returns emptyList()

                    mockMvc.perform(
                        MockMvcRequestBuilders.put("/owner/LabelProj/issue/label/10")
                            .principal(labelManagerAuth)
                            .param("name", "수정된이름")
                            .param("color", "#ff0000")
                            .param("category.id", "999")
                    ).andExpect(status().isBadRequest)

                    verify(exactly = 0) { issueLabelService.updateLabel(any(), any(), any(), any()) }
                }
            }

            describe("PUT /{owner}/{projectName}/issue/label/category/{id}") {
                it("수정 권한이 있으면 카테고리를 수정하고 200 OK를 반환해야 한다") {
                    val category = IssueLabelCategory(id = 1L, name = "수정된카테고리", project = labelProject)
                    every { issueLabelService.updateCategory(1L, "수정된카테고리", true) } returns category

                    mockMvc.perform(
                        MockMvcRequestBuilders.put("/owner/LabelProj/issue/label/category/1")
                            .principal(labelManagerAuth)
                            .param("name", "수정된카테고리")
                            .param("isExclusive", "true")
                    ).andExpect(status().isOk)
                }

                it("같은 프로젝트 내 다른 카테고리와 이름이 중복되면 400 Bad Request를 반환해야 한다") {
                    every {
                        issueLabelService.updateCategory(1L, "중복이름", false)
                    } throws DuplicateLabelCategoryNameException("dup")

                    mockMvc.perform(
                        MockMvcRequestBuilders.put("/owner/LabelProj/issue/label/category/1")
                            .principal(labelManagerAuth)
                            .param("name", "중복이름")
                    ).andExpect(status().isBadRequest)
                }
            }

            describe("POST /{owner}/{projectName}/copyLabels") {
                it("원본 프로젝트를 읽을 수 있으면 라벨을 복사하고 labelsform으로 리다이렉트해야 한다") {
                    val fromProject = Project(id = 6L, name = "FromProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
                    every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "FromProj") } returns Optional.of(fromProject)
                    every { issueLabelService.copyLabels(6L, 5L) } returns emptyList()

                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/copyLabels")
                            .principal(labelManagerAuth)
                            .param("owner", "owner")
                            .param("projectName", "FromProj")
                    ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/owner/LabelProj/issue/labelsform"))

                    verify(exactly = 1) { issueLabelService.copyLabels(6L, 5L) }
                }

                it("원본 프로젝트가 존재하지 않으면 조용히 무시하고 labelsform으로 리다이렉트해야 한다") {
                    every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj") } returns Optional.empty()

                    mockMvc.perform(
                        MockMvcRequestBuilders.post("/owner/LabelProj/copyLabels")
                            .principal(labelManagerAuth)
                            .param("owner", "owner")
                            .param("projectName", "NoSuchProj")
                    ).andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/owner/LabelProj/issue/labelsform"))

                    verify(exactly = 0) { issueLabelService.copyLabels(any(), any()) }
                }
            }
        }
    }

    // yona ProjectApp.java:1055-1058 대응 (P0-23) — HIDE_PROJECT_LISTING이 켜져 있으면 사이트매니저를
    // 포함해 누구도 전체 프로젝트 목록(HTML/JSON 둘 다)을 볼 수 없다.
    describe("HIDE_PROJECT_LISTING=true일 때 GET /projects") {
        val hiddenController = ProjectViewController(
            projectRepository, projectUserRepository, userRepository, repositoryService, projectService,
            organizationUserRepository, attachmentRepository, attachmentService, organizationRepository,
            messageSource, mailService, markdownService, roleRepository, projectTransferRepository,
            issueLabelService, issueRepository, postingRepository, pullRequestRepository, milestoneRepository,
            watchService, recentProjectRepository, accessControl, hideProjectListing = true
        )

        it("HTML 목록은 error/403 뷰를 반환해야 한다") {
            val result = hiddenController.projects(filter = "", pageNum = 1, authentication = null, model = ExtendedModelMap())
            result shouldBe "error/403"
        }

        it("JSON API는 403 Forbidden을 반환해야 한다") {
            val response = hiddenController.projectsJson(query = "", filter = "", authentication = null)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // ============================================================================================
    // TASK-커버리지 — getProjectHistory / getProjectDashboardData / transferProject 등 JaCoCo 미실행
    // 상위 메서드 보강. private 메서드는 projectHome(tabId=...)을 통해서만 도달 가능하므로 그 경로로
    // 검증하고, ResponseEntity를 반환하는 REST/직접 호출 메서드는 mockMvc 없이 컨트롤러를 직접
    // 호출해 분기별 응답을 검증한다(기존 HIDE_PROJECT_LISTING 테스트와 동일한 패턴).
    // ============================================================================================

    // yona ProjectApp.java history() 대응 — getProjectHistory는 private이라 projectHome(tabId가
    // readme/dashboard가 아닌 경우)를 통해서만 도달 가능하다.
    describe("getProjectHistory (GET /{owner}/{projectName}?tabId=history)") {
        val historyProject = Project(id = 30L, name = "HistoryProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
        val historyUser = User(id = 30L, loginId = "historyuser", name = "히스토리유저")
        val historyAuth = UsernamePasswordAuthenticationToken("historyuser", "password")

        beforeTest {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "HistoryProj") } returns Optional.of(historyProject)
            every { userRepository.findByLoginId("historyuser") } returns Optional.of(historyUser)
            every { projectUserRepository.findByProjectId(30L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()
        }

        it("코드/이슈/게시글/PR이 모두 활성화된 프로젝트는 4가지 이력을 모두 조회해 최신순으로 정렬해야 한다") {
            val playRepo = mockk<PlayRepository>()
            every { repositoryService.getRepository(historyProject) } returns playRepo

            val commitAuthor = User(id = 31L, loginId = "commitauthor", name = "커밋작성자")
            val commit = mockk<Commit>()
            every { commit.getAuthorEmail() } returns "author@yona.io"
            every { commit.getAuthorName() } returns "커밋작성자원본"
            every { commit.getCommitterDate() } returns Date.from(Instant.parse("2026-01-01T00:00:00Z"))
            every { commit.getShortId() } returns "abc1234"
            every { commit.getShortMessage() } returns "커밋 메시지"
            every { commit.getId() } returns "abc1234567890"
            every { playRepo.getHistory(0, 10, null, null) } returns listOf(commit)
            every { userRepository.findByEmail("author@yona.io") } returns Optional.of(commitAuthor)

            val issue = Issue(
                id = 40L, title = "이슈제목", project = historyProject, number = 1L,
                createdDate = Instant.parse("2026-01-02T00:00:00Z"), authorLoginId = "issueauthor", authorName = "이슈작성자원본"
            )
            every { issueRepository.findByProject(historyProject, any()) } returns PageImpl(listOf(issue))
            every { userRepository.findByLoginId("issueauthor") } returns Optional.of(User(id = 32L, loginId = "issueauthor", name = "이슈작성자"))

            val posting = Posting(
                id = 700L, title = "게시글제목", project = historyProject, number = 2L,
                createdDate = Instant.parse("2026-01-03T00:00:00Z"), authorLoginId = null, authorName = "게시글작성자원본"
            )
            every { postingRepository.findByProject(historyProject, any()) } returns PageImpl(listOf(posting))

            val contributor = User(id = 33L, loginId = "prcontributor", name = "PR작성자")
            val pull = PullRequest(
                id = 800L, title = "PR제목", toProject = historyProject, fromProject = historyProject,
                contributor = contributor, number = 3L, created = Instant.parse("2026-01-04T00:00:00Z")
            )
            every { pullRequestRepository.findByToProject(historyProject, any()) } returns PageImpl(listOf(pull))

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "HistoryProj", "history", historyAuth, model)

            @Suppress("UNCHECKED_CAST")
            val histories = model.getAttribute("histories") as List<HistoryDto>
            histories.size shouldBe 4
            // 최신순 정렬(PR 1/4 -> 게시글 1/3 -> 이슈 1/2 -> 커밋 1/1) 검증
            histories.map { it.what } shouldBe listOf("pullrequest", "post", "issue", "commit")
            histories[0].who shouldBe "PR작성자"
            histories[0].url shouldBe "/owner/HistoryProj/pullRequest/3"
            histories[1].who shouldBe "게시글작성자원본"
            histories[1].userPageUrl shouldBe "#"
            histories[2].who shouldBe "이슈작성자원본"
            histories[2].userPageUrl shouldBe "/user/issueauthor"
            histories[3].who shouldBe "커밋작성자"
            histories[3].shortTitle shouldBe "abc1234"
        }

        it("커밋 이력 조회 중 예외가 발생해도 무시하고 빈 이력을 반환해야 한다") {
            val onlyCodeProject = Project(
                id = 34L, name = "OnlyCodeProj", owner = "owner", projectScope = ProjectScope.PUBLIC,
                isIssueEnabled = false, isBoardEnabled = false, isPullRequestEnabled = false
            )
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "OnlyCodeProj") } returns Optional.of(onlyCodeProject)
            every { projectUserRepository.findByProjectId(34L) } returns emptyList()
            every { repositoryService.getRepository(onlyCodeProject) } throws RuntimeException("repo error")

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "OnlyCodeProj", "history", historyAuth, model)

            @Suppress("UNCHECKED_CAST")
            val histories = model.getAttribute("histories") as List<HistoryDto>
            histories.size shouldBe 0
        }

        it("코드/이슈/게시글/PR이 모두 비활성화된 프로젝트는 활동 이력이 비어 있어야 한다") {
            val disabledProject = Project(
                id = 35L, name = "DisabledProj", owner = "owner", projectScope = ProjectScope.PUBLIC,
                isCodeEnabled = false, isIssueEnabled = false, isBoardEnabled = false, isPullRequestEnabled = false
            )
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "DisabledProj") } returns Optional.of(disabledProject)
            every { projectUserRepository.findByProjectId(35L) } returns emptyList()

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "DisabledProj", "history", historyAuth, model)

            @Suppress("UNCHECKED_CAST")
            val histories = model.getAttribute("histories") as List<HistoryDto>
            histories.size shouldBe 0
        }

        it("커밋 작성자를 이메일로 찾지 못하면 authorName 또는 Unknown으로 대체해야 한다") {
            val commitOnlyProject = Project(
                id = 36L, name = "CommitFallbackProj", owner = "owner", projectScope = ProjectScope.PUBLIC,
                isIssueEnabled = false, isBoardEnabled = false, isPullRequestEnabled = false
            )
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "CommitFallbackProj") } returns Optional.of(commitOnlyProject)
            every { projectUserRepository.findByProjectId(36L) } returns emptyList()

            val playRepo = mockk<PlayRepository>()
            every { repositoryService.getRepository(commitOnlyProject) } returns playRepo

            val commitNoEmail = mockk<Commit>()
            every { commitNoEmail.getAuthorEmail() } returns null
            every { commitNoEmail.getAuthorName() } returns "이메일없음작성자"
            every { commitNoEmail.getCommitterDate() } returns null
            every { commitNoEmail.getShortId() } returns "noemail1"
            every { commitNoEmail.getShortMessage() } returns "이메일 없는 커밋"
            every { commitNoEmail.getId() } returns "noemail1234"

            val commitUnknown = mockk<Commit>()
            every { commitUnknown.getAuthorEmail() } returns "ghost@yona.io"
            every { commitUnknown.getAuthorName() } returns null
            every { commitUnknown.getCommitterDate() } returns Date()
            every { commitUnknown.getShortId() } returns "ghost123"
            every { commitUnknown.getShortMessage() } returns "유령 커밋"
            every { commitUnknown.getId() } returns "ghost1234567"

            every { playRepo.getHistory(0, 10, null, null) } returns listOf(commitNoEmail, commitUnknown)
            every { userRepository.findByEmail("ghost@yona.io") } returns Optional.empty()

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "CommitFallbackProj", "history", historyAuth, model)

            @Suppress("UNCHECKED_CAST")
            val histories = model.getAttribute("histories") as List<HistoryDto>
            histories.size shouldBe 2
            histories.any { it.who == "이메일없음작성자" } shouldBe true
            histories.any { it.who == "Unknown" } shouldBe true
        }

        it("이슈/게시글 작성자를 찾지 못하면 authorName 또는 Unknown, userPageUrl은 #으로 대체해야 한다") {
            val noAuthorProject = Project(
                id = 37L, name = "NoAuthorProj", owner = "owner", projectScope = ProjectScope.PUBLIC,
                isCodeEnabled = false, isPullRequestEnabled = false
            )
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoAuthorProj") } returns Optional.of(noAuthorProject)
            every { projectUserRepository.findByProjectId(37L) } returns emptyList()

            val issueNoLoginId = Issue(
                id = 41L, title = "로그인아이디없음", project = noAuthorProject, number = 4L,
                createdDate = Instant.now(), authorLoginId = null, authorName = null
            )
            every { issueRepository.findByProject(noAuthorProject, any()) } returns PageImpl(listOf(issueNoLoginId))

            val postingNotFound = Posting(
                id = 701L, title = "찾을수없는작성자", project = noAuthorProject, number = 5L,
                createdDate = Instant.now(), authorLoginId = "ghostwriter", authorName = null
            )
            every { postingRepository.findByProject(noAuthorProject, any()) } returns PageImpl(listOf(postingNotFound))
            every { userRepository.findByLoginId("ghostwriter") } returns Optional.empty()

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "NoAuthorProj", "history", historyAuth, model)

            @Suppress("UNCHECKED_CAST")
            val histories = model.getAttribute("histories") as List<HistoryDto>
            histories.size shouldBe 2
            histories.forEach {
                it.who shouldBe "Unknown"
                it.userPageUrl shouldBe "#"
            }
        }
    }

    // yona project/home.scala.html 대시보드 탭 대응 — getProjectDashboardData는 private이라
    // projectHome(tabId="dashboard")를 통해서만 도달 가능하다.
    describe("getProjectDashboardData (GET /{owner}/{projectName}?tabId=dashboard)") {
        val dashboardUser = User(id = 40L, loginId = "dashboarduser", name = "대시보드유저")
        val dashboardAuth = UsernamePasswordAuthenticationToken("dashboarduser", "password")

        beforeTest {
            every { userRepository.findByLoginId("dashboarduser") } returns Optional.of(dashboardUser)
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()
        }

        it("이슈/마일스톤/PR/라벨이 있는 프로젝트에서 대시보드 데이터가 올바르게 계산돼야 한다") {
            val project = Project(id = 40L, name = "DashboardProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val memberA = User(id = 41L, loginId = "membera", name = "회원A")
            val userB = User(id = 42L, loginId = "userb", name = "회원B")

            val milestone1 = Milestone(id = 50L, title = "마일스톤1", project = project, state = State.OPEN)
            val milestone2 = Milestone(id = 51L, title = "마일스톤2(이슈없음)", project = project, state = State.OPEN)
            val milestone3 = Milestone(id = 52L, title = "마일스톤3(전체집계누락)", project = project, state = State.OPEN)

            val category1 = IssueLabelCategory(id = 1L, name = "카테고리1", project = project)
            val category2 = IssueLabelCategory(id = 2L, name = "카테고리2", project = project)
            val label1 = IssueLabel(id = 10L, category = category1, color = "#111111", name = "라벨1", project = project)
            val label2 = IssueLabel(id = 11L, category = category1, color = "#222222", name = "라벨2", project = project)
            val label3 = IssueLabel(id = 12L, category = category2, color = "#333333", name = "라벨3(미사용)", project = project)

            val issue1 = Issue(
                id = 1L, title = "이슈1", project = project, number = 1L, state = State.OPEN,
                assignee = Assignee(user = userB, project = project), milestone = milestone1, labels = mutableSetOf(label1)
            )
            val issue2 = Issue(
                id = 2L, title = "이슈2(미배정)", project = project, number = 2L, state = State.OPEN
            )
            val issue3 = Issue(
                id = 3L, title = "이슈3", project = project, number = 3L, state = State.OPEN,
                assignee = Assignee(user = userB, project = project), milestone = milestone1, labels = mutableSetOf(label1, label2)
            )
            // milestone3에 배정된 이슈지만 findByProject(전체 이슈) 조회 결과에는 포함되지 않아
            // totalInMilestone==0 방어분기(0으로 나누기 가드)를 검증하기 위한 데이터.
            val issue5 = Issue(
                id = 5L, title = "이슈5(전체집계누락)", project = project, number = 5L, state = State.OPEN, milestone = milestone3
            )
            val closedIssue4 = Issue(
                id = 4L, title = "이슈4(닫힘)", project = project, number = 4L, state = State.CLOSED, milestone = milestone1
            )

            val openIssues = listOf(issue1, issue2, issue3, issue5)
            val allIssues = listOf(issue1, issue2, issue3, closedIssue4)

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "DashboardProj") } returns Optional.of(project)
            every { projectUserRepository.findByProjectId(40L) } returns
                listOf(ProjectUser(id = 400L, user = memberA, project = project, role = Role(id = RoleType.MEMBER.roleType)))
            every { issueRepository.findByProjectAndState(project, State.OPEN) } returns openIssues
            every { issueRepository.findByProject(project) } returns allIssues
            every { milestoneRepository.findByProjectAndState(project, State.OPEN) } returns listOf(milestone1, milestone2, milestone3)

            val pr1 = PullRequest(id = 900L, title = "PR1", toProject = project, fromProject = project, contributor = userB, number = 1L, state = State.OPEN)
            val pr2 = PullRequest(id = 901L, title = "PR2", toProject = project, fromProject = project, contributor = memberA, number = 2L, state = State.OPEN)
            val pr3 = PullRequest(id = 902L, title = "PR3", toProject = project, fromProject = project, contributor = memberA, number = 3L, state = State.OPEN)
            every { pullRequestRepository.findByToProjectAndState(project, State.OPEN, any()) } returns PageImpl(listOf(pr1, pr2))
            every { pullRequestRepository.findByToProjectAndState(project, State.OPEN) } returns listOf(pr1, pr2, pr3)

            every { issueLabelService.getLabels(40L) } returns listOf(label1, label2, label3)

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "DashboardProj", "dashboard", dashboardAuth, model)

            model.getAttribute("openIssuesCount") shouldBe 4
            model.getAttribute("notAssignedIssuesCount") shouldBe 2
            model.getAttribute("notAssignedIssuesPercent") shouldBe 50
            model.getAttribute("noMilestoneIssuesCount") shouldBe 1
            model.getAttribute("totalOpenPullRequestsCount") shouldBe 3

            @Suppress("UNCHECKED_CAST")
            val assigneeList = model.getAttribute("assigneeList") as List<ProjectViewController.AssigneeDashboardDto>
            assigneeList.size shouldBe 1
            assigneeList[0].user shouldBe userB
            assigneeList[0].count shouldBe 2
            assigneeList[0].percent shouldBe 50

            @Suppress("UNCHECKED_CAST")
            val milestoneList = model.getAttribute("milestoneList") as List<ProjectViewController.MilestoneDashboardDto>
            milestoneList.map { it.id } shouldBe listOf(50L, 52L) // openCount 내림차순: milestone1(2) -> milestone3(1), milestone2(0)는 제외
            milestoneList[0].completionRate shouldBe 33 // 1/3*100
            milestoneList[1].completionRate shouldBe 0 // totalInMilestone==0 방어분기

            @Suppress("UNCHECKED_CAST")
            val openPullRequests = model.getAttribute("openPullRequests") as List<PullRequest>
            openPullRequests.size shouldBe 2

            @Suppress("UNCHECKED_CAST")
            val labelCategories = model.getAttribute("labelCategories") as List<ProjectViewController.LabelCategoryDashboardDto>
            labelCategories.size shouldBe 2
            val cat1Dto = labelCategories.first { it.name == "카테고리1" }
            cat1Dto.labels.first { it.id == 10L }.count shouldBe 2
            cat1Dto.labels.first { it.id == 11L }.count shouldBe 1
            val cat2Dto = labelCategories.first { it.name == "카테고리2" }
            cat2Dto.labels.first { it.id == 12L }.count shouldBe 0
        }

        it("이슈/마일스톤/PR/라벨이 없는 빈 프로젝트에서는 0으로 나누기 없이 안전하게 계산돼야 한다") {
            val emptyProject = Project(id = 45L, name = "EmptyDashboardProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "EmptyDashboardProj") } returns Optional.of(emptyProject)
            every { projectUserRepository.findByProjectId(45L) } returns
                listOf(ProjectUser(id = 450L, user = dashboardUser, project = emptyProject, role = Role(id = RoleType.MEMBER.roleType)))
            every { issueRepository.findByProjectAndState(emptyProject, State.OPEN) } returns emptyList()
            every { issueRepository.findByProject(emptyProject) } returns emptyList()
            every { milestoneRepository.findByProjectAndState(emptyProject, State.OPEN) } returns emptyList()
            every { pullRequestRepository.findByToProjectAndState(emptyProject, State.OPEN, any()) } returns PageImpl(emptyList())
            every { pullRequestRepository.findByToProjectAndState(emptyProject, State.OPEN) } returns emptyList()
            every { issueLabelService.getLabels(45L) } returns emptyList()

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "EmptyDashboardProj", "dashboard", dashboardAuth, model)

            model.getAttribute("openIssuesCount") shouldBe 0
            model.getAttribute("notAssignedIssuesCount") shouldBe 0
            model.getAttribute("notAssignedIssuesPercent") shouldBe 0
            model.getAttribute("noMilestoneIssuesCount") shouldBe 0
            model.getAttribute("totalOpenPullRequestsCount") shouldBe 0

            @Suppress("UNCHECKED_CAST")
            val assigneeList = model.getAttribute("assigneeList") as List<ProjectViewController.AssigneeDashboardDto>
            assigneeList.size shouldBe 0

            @Suppress("UNCHECKED_CAST")
            val milestoneList = model.getAttribute("milestoneList") as List<ProjectViewController.MilestoneDashboardDto>
            milestoneList.size shouldBe 0

            @Suppress("UNCHECKED_CAST")
            val labelCategories = model.getAttribute("labelCategories") as List<ProjectViewController.LabelCategoryDashboardDto>
            labelCategories.size shouldBe 0
        }
    }

    // yona ProjectApp.java transferForm() 대응.
    describe("GET /{owner}/{projectName}/transfer") {
        val transferProject1 = Project(id = 500L, name = "TransferProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
        val transferManager = User(id = 500L, loginId = "transfermanager", name = "이관매니저")
        val transferManagerAuth = UsernamePasswordAuthenticationToken("transfermanager", "password")
        val transferOutsider = User(id = 501L, loginId = "transferoutsider", name = "이관외부인")
        val transferOutsiderAuth = UsernamePasswordAuthenticationToken("transferoutsider", "password")

        it("프로젝트가 없으면 error/404 뷰를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchTransferProj") } returns Optional.empty()
            mockMvc.perform(get("/owner/NoSuchTransferProj/transfer").principal(transferManagerAuth))
                .andExpect(view().name("error/404"))
        }

        it("로그인하지 않았으면 로그인 폼으로 리다이렉트해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TransferProj") } returns Optional.of(transferProject1)
            mockMvc.perform(get("/owner/TransferProj/transfer"))
                .andExpect(redirectedUrl("/users/loginform"))
        }

        it("MANAGER 권한이 없으면 error/forbidden 뷰를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TransferProj") } returns Optional.of(transferProject1)
            every { userRepository.findByLoginId("transferoutsider") } returns Optional.of(transferOutsider)
            every { projectUserRepository.findByProjectIdAndUserId(500L, 501L) } returns Optional.empty()

            mockMvc.perform(get("/owner/TransferProj/transfer").principal(transferOutsiderAuth))
                .andExpect(view().name("error/forbidden"))
        }

        it("MANAGER 권한이 있으면 project/transfer 뷰를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TransferProj") } returns Optional.of(transferProject1)
            every { userRepository.findByLoginId("transfermanager") } returns Optional.of(transferManager)
            every { projectUserRepository.findByProjectIdAndUserId(500L, 500L) } returns
                Optional.of(ProjectUser(id = 5000L, user = transferManager, project = transferProject1, role = Role(id = RoleType.MANAGER.roleType)))

            mockMvc.perform(get("/owner/TransferProj/transfer").principal(transferManagerAuth))
                .andExpect(status().isOk)
                .andExpect(view().name("project/transfer"))
        }
    }

    // yona ProjectApp.java transfer()/newTransferForm() 대응 — transferProject()가 성공 시 호출하는
    // private sendTransferRequestMail()/getServerUrl()도 이 경로로만 도달 가능하므로 함께 검증한다.
    describe("PUT /{owner}/{projectName}/transfer (transferProject + 이관 메일 발송)") {
        val transferProject2 = Project(id = 55L, name = "TransferProj2", owner = "owner", projectScope = ProjectScope.PUBLIC)
        val manager = User(id = 55L, loginId = "manager55", name = "매니저55")
        val managerAuth = UsernamePasswordAuthenticationToken("manager55", "password")
        val managerProjectUser = ProjectUser(id = 550L, user = manager, project = transferProject2, role = Role(id = RoleType.MANAGER.roleType))

        fun mockRequest(scheme: String = "https", serverName: String = "yona.io", port: Int = 443): HttpServletRequest {
            val request = mockk<HttpServletRequest>()
            every { request.scheme } returns scheme
            every { request.serverName } returns serverName
            every { request.serverPort } returns port
            return request
        }

        it("로그인하지 않았으면 401을 반환해야 한다") {
            val response = projectViewController.transferProject("owner", "TransferProj2", "dest", mockRequest(), null)
            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        it("프로젝트가 없으면 404를 반환해야 한다") {
            every { userRepository.findByLoginId("manager55") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchProj2") } returns Optional.empty()

            val response = projectViewController.transferProject("owner", "NoSuchProj2", "dest", mockRequest(), managerAuth)
            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        it("MANAGER 권한이 없으면 403을 반환해야 한다") {
            every { userRepository.findByLoginId("manager55") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TransferProj2") } returns Optional.of(transferProject2)
            every { projectUserRepository.findByProjectIdAndUserId(55L, 55L) } returns Optional.empty()

            val response = projectViewController.transferProject("owner", "TransferProj2", "dest", mockRequest(), managerAuth)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        it("이관 대상이 사용자도 조직도 아니면 400을 반환해야 한다") {
            every { userRepository.findByLoginId("manager55") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TransferProj2") } returns Optional.of(transferProject2)
            every { projectUserRepository.findByProjectIdAndUserId(55L, 55L) } returns Optional.of(managerProjectUser)
            every { userRepository.findByLoginId("nowhere") } returns Optional.empty()
            every { organizationRepository.findByName("nowhere") } returns Optional.empty()

            val response = projectViewController.transferProject("owner", "TransferProj2", "nowhere", mockRequest(), managerAuth)
            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        it("자기 자신(현재 owner와 동일한 사용자)에게 이관 요청하면 400을 반환해야 한다") {
            every { userRepository.findByLoginId("manager55") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TransferProj2") } returns Optional.of(transferProject2)
            every { projectUserRepository.findByProjectIdAndUserId(55L, 55L) } returns Optional.of(managerProjectUser)
            val selfUser = User(id = 999L, loginId = "owner", name = "본인")
            every { userRepository.findByLoginId("owner") } returns Optional.of(selfUser)
            every { organizationRepository.findByName("owner") } returns Optional.empty()

            val response = projectViewController.transferProject("owner", "TransferProj2", "owner", mockRequest(), managerAuth)
            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        it("자기 자신(현재 owner와 동일한 조직)에게 이관 요청하면 400을 반환해야 한다") {
            every { userRepository.findByLoginId("manager55") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TransferProj2") } returns Optional.of(transferProject2)
            every { projectUserRepository.findByProjectIdAndUserId(55L, 55L) } returns Optional.of(managerProjectUser)
            every { userRepository.findByLoginId("owner") } returns Optional.empty()
            val selfOrg = Organization(id = 5000L, name = "owner")
            every { organizationRepository.findByName("owner") } returns Optional.of(selfOrg)

            val response = projectViewController.transferProject("owner", "TransferProj2", "owner", mockRequest(), managerAuth)
            response.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        it("정상 이관 요청이면 204와 Location 헤더를 반환하고 대상 사용자에게 이관 메일을 발송해야 한다 (포트 443)") {
            every { userRepository.findByLoginId("manager55") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TransferProj2") } returns Optional.of(transferProject2)
            every { projectUserRepository.findByProjectIdAndUserId(55L, 55L) } returns Optional.of(managerProjectUser)
            val destUser = User(id = 60L, loginId = "destuser", name = "대상유저", email = "dest@yona.io")
            every { userRepository.findByLoginId("destuser") } returns Optional.of(destUser)
            every { organizationRepository.findByName("destuser") } returns Optional.empty()
            val pt = ProjectTransfer(id = 900L, sender = manager, destination = "destuser", project = transferProject2, confirmKey = "confirmkey1", newProjectName = "TransferProj2")
            every { projectService.requestNewTransfer(55L, 55L, "destuser") } returns pt

            every { messageSource.getMessage(any(), any(), any()) } returns "메시지"
            every { markdownService.render(any(), true, transferProject2) } returns "<p>html</p>"
            every { mailService.sendHtmlMail("dest@yona.io", "Yona", any(), "<p>html</p>") } just Runs

            val response = projectViewController.transferProject("owner", "TransferProj2", "destuser", mockRequest(port = 443), managerAuth)
            response.statusCode shouldBe HttpStatus.NO_CONTENT
            response.headers.getFirst("Location") shouldBe "/owner/TransferProj2"
            verify(exactly = 1) { mailService.sendHtmlMail("dest@yona.io", "Yona", any(), "<p>html</p>") }
        }

        it("이관 대상이 조직이면 조직 관리자 전원에게만 메일을 발송해야 한다 (포트 80, 일반 멤버는 제외)") {
            every { userRepository.findByLoginId("manager55") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TransferProj2") } returns Optional.of(transferProject2)
            every { projectUserRepository.findByProjectIdAndUserId(55L, 55L) } returns Optional.of(managerProjectUser)
            every { userRepository.findByLoginId("destorg") } returns Optional.empty()
            val destOrg = Organization(id = 600L, name = "destorg")
            every { organizationRepository.findByName("destorg") } returns Optional.of(destOrg)
            val orgAdmin = User(id = 61L, loginId = "orgadmin", name = "조직관리자", email = "admin@yona.io")
            val orgMember = User(id = 62L, loginId = "orgmember", name = "조직멤버", email = "member@yona.io")
            every { organizationUserRepository.findByOrganizationId(600L) } returns listOf(
                OrganizationUser(id = 1L, user = orgAdmin, organization = destOrg, role = Role(id = RoleType.ORG_ADMIN.roleType)),
                OrganizationUser(id = 2L, user = orgMember, organization = destOrg, role = Role(id = RoleType.ORG_MEMBER.roleType))
            )
            val pt = ProjectTransfer(id = 901L, sender = manager, destination = "destorg", project = transferProject2, confirmKey = "confirmkey2", newProjectName = "TransferProj2")
            every { projectService.requestNewTransfer(55L, 55L, "destorg") } returns pt

            every { messageSource.getMessage(any(), any(), any()) } returns "메시지"
            every { markdownService.render(any(), true, transferProject2) } returns "<p>html</p>"
            every { mailService.sendHtmlMail("admin@yona.io", "Yona", any(), "<p>html</p>") } just Runs

            val response = projectViewController.transferProject("owner", "TransferProj2", "destorg", mockRequest(scheme = "http", port = 80), managerAuth)
            response.statusCode shouldBe HttpStatus.NO_CONTENT
            verify(exactly = 1) { mailService.sendHtmlMail("admin@yona.io", "Yona", any(), "<p>html</p>") }
            verify(exactly = 0) { mailService.sendHtmlMail("member@yona.io", "Yona", any(), "<p>html</p>") }
        }

        it("메일 발송 중 예외가 발생해도 이관 요청 자체는 204로 성공해야 한다") {
            every { userRepository.findByLoginId("manager55") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TransferProj2") } returns Optional.of(transferProject2)
            every { projectUserRepository.findByProjectIdAndUserId(55L, 55L) } returns Optional.of(managerProjectUser)
            val destUser = User(id = 63L, loginId = "brokenmail", name = "메일깨짐", email = "broken@yona.io")
            every { userRepository.findByLoginId("brokenmail") } returns Optional.of(destUser)
            every { organizationRepository.findByName("brokenmail") } returns Optional.empty()
            val pt = ProjectTransfer(id = 902L, sender = manager, destination = "brokenmail", project = transferProject2, confirmKey = "confirmkey3", newProjectName = "TransferProj2")
            every { projectService.requestNewTransfer(55L, 55L, "brokenmail") } returns pt
            every { messageSource.getMessage(any(), any(), any()) } throws RuntimeException("메시지 로드 실패")

            val response = projectViewController.transferProject("owner", "TransferProj2", "brokenmail", mockRequest(), managerAuth)
            response.statusCode shouldBe HttpStatus.NO_CONTENT
        }

        it("이관 대상 이메일이 비어 있으면 메일 발송 없이도 정상 처리돼야 한다") {
            every { userRepository.findByLoginId("manager55") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TransferProj2") } returns Optional.of(transferProject2)
            every { projectUserRepository.findByProjectIdAndUserId(55L, 55L) } returns Optional.of(managerProjectUser)
            val destUserNoEmail = User(id = 64L, loginId = "noemaildest", name = "이메일없음", email = "")
            every { userRepository.findByLoginId("noemaildest") } returns Optional.of(destUserNoEmail)
            every { organizationRepository.findByName("noemaildest") } returns Optional.empty()
            val pt = ProjectTransfer(id = 903L, sender = manager, destination = "noemaildest", project = transferProject2, confirmKey = "confirmkey4", newProjectName = "TransferProj2")
            every { projectService.requestNewTransfer(55L, 55L, "noemaildest") } returns pt
            every { messageSource.getMessage(any(), any(), any()) } returns "메시지"
            every { markdownService.render(any(), true, transferProject2) } returns "<p>html</p>"

            val response = projectViewController.transferProject("owner", "TransferProj2", "noemaildest", mockRequest(), managerAuth)
            response.statusCode shouldBe HttpStatus.NO_CONTENT
            verify(exactly = 0) { mailService.sendHtmlMail(any(), any(), any(), any()) }
        }
    }

    // yona ProjectApp.java acceptTransfer() 대응.
    describe("GET /project/transfer/{transferId}/{confirmKey} (acceptTransfer)") {
        val acceptUser = User(id = 70L, loginId = "acceptuser", name = "수락자")
        val acceptAuth = UsernamePasswordAuthenticationToken("acceptuser", "password")

        it("로그인하지 않았으면 로그인 폼으로 리다이렉트해야 한다") {
            val result = projectViewController.acceptTransfer(1L, "key", null, ExtendedModelMap())
            result shouldBe "redirect:/users/loginform"
        }

        it("존재하지 않는 이관 요청이면 error/404를 반환해야 한다") {
            every { userRepository.findByLoginId("acceptuser") } returns Optional.of(acceptUser)
            every { projectTransferRepository.findById(999L) } returns Optional.empty()

            val model = ExtendedModelMap()
            val result = projectViewController.acceptTransfer(999L, "key", acceptAuth, model)
            result shouldBe "error/404"
            model.getAttribute("errorMessage") shouldBe "존재하지 않는 이관 요청입니다."
        }

        it("이관 승인에 성공하면 새 프로젝트 위치로 리다이렉트해야 한다") {
            val proj = Project(id = 80L, name = "OldName", owner = "olddest")
            val sender = User(id = 71L, loginId = "sender71", name = "발신자")
            val pt = ProjectTransfer(id = 10L, sender = sender, destination = "newdest", project = proj, confirmKey = "validkey", newProjectName = "newname")
            every { userRepository.findByLoginId("acceptuser") } returns Optional.of(acceptUser)
            every { projectTransferRepository.findById(10L) } returns Optional.of(pt)
            every { projectService.acceptTransfer(10L, "validkey", 70L) } just Runs

            val result = projectViewController.acceptTransfer(10L, "validkey", acceptAuth, ExtendedModelMap())
            result shouldBe "redirect:/newdest/newname"
        }

        it("이관 승인 중 예외가 발생하면 error/500과 에러 메시지를 반환해야 한다") {
            val proj = Project(id = 81L, name = "OldName2", owner = "olddest2")
            val sender = User(id = 72L, loginId = "sender72", name = "발신자2")
            val pt = ProjectTransfer(id = 11L, sender = sender, destination = "newdest2", project = proj, confirmKey = "badkey", newProjectName = "newname2")
            every { userRepository.findByLoginId("acceptuser") } returns Optional.of(acceptUser)
            every { projectTransferRepository.findById(11L) } returns Optional.of(pt)
            every { projectService.acceptTransfer(11L, "badkey", 70L) } throws IllegalStateException("만료된 요청")

            val model = ExtendedModelMap()
            val result = projectViewController.acceptTransfer(11L, "badkey", acceptAuth, model)
            result shouldBe "error/500"
            (model.getAttribute("errorMessage") as String).contains("만료된 요청") shouldBe true
        }
    }

    // yona ProjectApp.java delete() 대응.
    describe("DELETE /{owner}/{projectName}/delete (deleteProject)") {
        val deleteProj = Project(id = 90L, name = "DeleteProj", owner = "owner")
        val deleteManager = User(id = 90L, loginId = "deletemanager", name = "삭제매니저")
        val deleteManagerAuth = UsernamePasswordAuthenticationToken("deletemanager", "password")

        it("로그인하지 않았으면 401을 반환해야 한다") {
            val response = projectViewController.deleteProject("owner", "DeleteProj", null)
            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        it("프로젝트가 없으면 404를 반환해야 한다") {
            every { userRepository.findByLoginId("deletemanager") } returns Optional.of(deleteManager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchDeleteProj") } returns Optional.empty()

            val response = projectViewController.deleteProject("owner", "NoSuchDeleteProj", deleteManagerAuth)
            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        it("MANAGER 권한이 없으면 403을 반환해야 한다") {
            every { userRepository.findByLoginId("deletemanager") } returns Optional.of(deleteManager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "DeleteProj") } returns Optional.of(deleteProj)
            every { projectUserRepository.findByProjectIdAndUserId(90L, 90L) } returns Optional.empty()

            val response = projectViewController.deleteProject("owner", "DeleteProj", deleteManagerAuth)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        it("MANAGER 권한이 있으면 204와 Location:/ 헤더를 반환하며 삭제를 수행해야 한다") {
            every { userRepository.findByLoginId("deletemanager") } returns Optional.of(deleteManager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "DeleteProj") } returns Optional.of(deleteProj)
            every { projectUserRepository.findByProjectIdAndUserId(90L, 90L) } returns
                Optional.of(ProjectUser(id = 900L, user = deleteManager, project = deleteProj, role = Role(id = RoleType.MANAGER.roleType)))
            every { projectService.deleteProject(90L) } just Runs

            val response = projectViewController.deleteProject("owner", "DeleteProj", deleteManagerAuth)
            response.statusCode shouldBe HttpStatus.NO_CONTENT
            response.headers.getFirst("Location") shouldBe "/"
            verify(exactly = 1) { projectService.deleteProject(90L) }
        }
    }

    // yona ProjectApp.java deleteForm() 대응.
    describe("GET /{owner}/{projectName}/deleteform") {
        it("프로젝트가 없으면 error/404 뷰를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchDF") } returns Optional.empty()
            val result = projectViewController.deleteForm("owner", "NoSuchDF", null, ExtendedModelMap())
            result shouldBe "error/404"
        }

        it("로그인하지 않았으면 로그인 폼으로 리다이렉트해야 한다") {
            val proj = Project(id = 91L, name = "DFProj", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "DFProj") } returns Optional.of(proj)
            val result = projectViewController.deleteForm("owner", "DFProj", null, ExtendedModelMap())
            result shouldBe "redirect:/users/loginform"
        }

        it("MANAGER 권한이 없으면 error/forbidden 뷰를 반환해야 한다") {
            val proj = Project(id = 92L, name = "DFProj2", owner = "owner")
            val outsider = User(id = 92L, loginId = "dfoutsider", name = "외부인")
            val outsiderAuth = UsernamePasswordAuthenticationToken("dfoutsider", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "DFProj2") } returns Optional.of(proj)
            every { userRepository.findByLoginId("dfoutsider") } returns Optional.of(outsider)
            every { projectUserRepository.findByProjectIdAndUserId(92L, 92L) } returns Optional.empty()

            val result = projectViewController.deleteForm("owner", "DFProj2", outsiderAuth, ExtendedModelMap())
            result shouldBe "error/forbidden"
        }

        it("MANAGER 권한이 있으면 project/delete 뷰를 반환해야 한다") {
            val proj = Project(id = 93L, name = "DFProj3", owner = "owner")
            val manager = User(id = 93L, loginId = "dfmanager", name = "삭매니저")
            val managerAuth = UsernamePasswordAuthenticationToken("dfmanager", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "DFProj3") } returns Optional.of(proj)
            every { userRepository.findByLoginId("dfmanager") } returns Optional.of(manager)
            every { projectUserRepository.findByProjectIdAndUserId(93L, 93L) } returns
                Optional.of(ProjectUser(id = 930L, user = manager, project = proj, role = Role(id = RoleType.MANAGER.roleType)))

            val result = projectViewController.deleteForm("owner", "DFProj3", managerAuth, ExtendedModelMap())
            result shouldBe "project/delete"
        }
    }

    // yona PullRequestApp.doClone() 대응.
    describe("POST /api/{ownerName}/{projectName}/doClone") {
        it("원본 프로젝트가 없으면 status=failed, url=/ 를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchClone") } returns Optional.empty()
            val response = projectViewController.doClone("owner", "NoSuchClone", "dest", "name", null)
            response.body?.get("status") shouldBe "failed"
            response.body?.get("url") shouldBe "/"
        }

        it("로그인하지 않았으면 status=failed, url=/users/loginform 을 반환해야 한다") {
            val original = Project(id = 100L, name = "CloneOrigin", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "CloneOrigin") } returns Optional.of(original)
            val response = projectViewController.doClone("owner", "CloneOrigin", "dest", "name", null)
            response.body?.get("status") shouldBe "failed"
            response.body?.get("url") shouldBe "/users/loginform"
        }

        it("정상적으로 fork되면 status=success와 새 프로젝트 URL을 반환해야 한다") {
            val original = Project(id = 101L, name = "CloneOrigin2", owner = "owner")
            val cloner = User(id = 100L, loginId = "cloner", name = "클로너")
            val clonerAuth = UsernamePasswordAuthenticationToken("cloner", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "CloneOrigin2") } returns Optional.of(original)
            every { userRepository.findByLoginId("cloner") } returns Optional.of(cloner)
            every { projectService.forkProject(101L, 100L, "clonedest", "clonedname") } returns
                Project(id = 102L, name = "clonedname", owner = "clonedest")

            val response = projectViewController.doClone("owner", "CloneOrigin2", "clonedest", "clonedname", clonerAuth)
            response.body?.get("status") shouldBe "success"
            response.body?.get("url") shouldBe "/clonedest/clonedname"
        }

        it("fork 도중 예외가 발생하면 status=failed와 pulls 화면 URL을 반환해야 한다") {
            val original = Project(id = 103L, name = "CloneOrigin3", owner = "owner")
            val cloner = User(id = 101L, loginId = "cloner2", name = "클로너2")
            val clonerAuth = UsernamePasswordAuthenticationToken("cloner2", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "CloneOrigin3") } returns Optional.of(original)
            every { userRepository.findByLoginId("cloner2") } returns Optional.of(cloner)
            every { projectService.forkProject(103L, 101L, "faildest", "failname") } throws RuntimeException("clone 실패")

            val response = projectViewController.doClone("owner", "CloneOrigin3", "faildest", "failname", clonerAuth)
            response.body?.get("status") shouldBe "failed"
            response.body?.get("url") shouldBe "/owner/CloneOrigin3/pulls"
        }
    }

    // yona PullRequestApp.fork() 대응.
    describe("POST /{ownerName}/{projectName}/fork") {
        it("원본 프로젝트가 없으면 error/404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchFork") } returns Optional.empty()
            val result = projectViewController.fork("owner", "NoSuchFork", "dest", "name", "PUBLIC", null, ExtendedModelMap())
            result shouldBe "error/404"
        }

        it("로그인하지 않았으면 로그인 폼으로 리다이렉트해야 한다") {
            val original = Project(id = 110L, name = "ForkOrigin", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "ForkOrigin") } returns Optional.of(original)
            val result = projectViewController.fork("owner", "ForkOrigin", "dest", "name", "PUBLIC", null, ExtendedModelMap())
            result shouldBe "redirect:/users/loginform"
        }

        it("동일 소유자 아래 같은 이름의 프로젝트가 이미 있으면 project/fork 뷰에 에러를 담아 반환해야 한다") {
            val original = Project(id = 111L, name = "ForkOrigin2", owner = "owner")
            val forker = User(id = 110L, loginId = "forker", name = "포커")
            val forkerAuth = UsernamePasswordAuthenticationToken("forker", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "ForkOrigin2") } returns Optional.of(original)
            every { userRepository.findByLoginId("forker") } returns Optional.of(forker)
            every { projectRepository.existsByOwnerAndName("forker", "dupname") } returns true
            every { organizationUserRepository.findByUserIdAndRoleId(110L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
            every { projectRepository.findByOwnerAndOriginalProject("forker", original) } returns emptyList()

            val model = ExtendedModelMap()
            val result = projectViewController.fork("owner", "ForkOrigin2", "forker", "dupname", "PUBLIC", forkerAuth, model)
            result shouldBe "project/fork"
            model.getAttribute("error") shouldBe "이미 동일한 소유자 밑에 같은 이름의 프로젝트가 존재합니다."
        }

        it("이름 충돌이 없으면 pullrequest/clone 뷰로 이동해야 한다") {
            val original = Project(id = 112L, name = "ForkOrigin3", owner = "owner")
            val forker = User(id = 111L, loginId = "forker2", name = "포커2")
            val forkerAuth = UsernamePasswordAuthenticationToken("forker2", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "ForkOrigin3") } returns Optional.of(original)
            every { userRepository.findByLoginId("forker2") } returns Optional.of(forker)
            every { projectRepository.existsByOwnerAndName("forker2", "newname") } returns false

            val model = ExtendedModelMap()
            val result = projectViewController.fork("owner", "ForkOrigin3", "forker2", "newname", "PRIVATE", forkerAuth, model)
            result shouldBe "pullrequest/clone"
            model.getAttribute("forkOwner") shouldBe "forker2"
            model.getAttribute("forkName") shouldBe "newname"
            model.getAttribute("forkProjectScope") shouldBe "PRIVATE"
        }
    }

    // yona PullRequestApp.newFork() 대응.
    describe("GET /{ownerName}/{projectName}/newFork") {
        it("원본 프로젝트가 없으면 error/404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchNewFork") } returns Optional.empty()
            val result = projectViewController.newFork("owner", "NoSuchNewFork", null, null, ExtendedModelMap())
            result shouldBe "error/404"
        }

        it("로그인하지 않았으면 로그인 폼으로 리다이렉트해야 한다") {
            val original = Project(id = 120L, name = "NewForkOrigin", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NewForkOrigin") } returns Optional.of(original)
            val result = projectViewController.newFork("owner", "NewForkOrigin", null, null, ExtendedModelMap())
            result shouldBe "redirect:/users/loginform"
        }

        it("forkOwner가 사용자가 관리하는 조직명과 일치하면 그 조직을 fork 대상으로 삼아야 한다") {
            val original = Project(id = 121L, name = "NewForkOrigin2", owner = "owner")
            val forker = User(id = 120L, loginId = "forker3", name = "포커3")
            val forkerAuth = UsernamePasswordAuthenticationToken("forker3", "password")
            val org = Organization(id = 700L, name = "myorg2")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NewForkOrigin2") } returns Optional.of(original)
            every { userRepository.findByLoginId("forker3") } returns Optional.of(forker)
            every { organizationUserRepository.findByUserIdAndRoleId(120L, RoleType.ORG_ADMIN.roleType) } returns
                listOf(OrganizationUser(id = 1L, user = forker, organization = org, role = Role(id = RoleType.ORG_ADMIN.roleType)))
            every { projectRepository.findByOwnerAndOriginalProject("myorg2", original) } returns
                listOf(Project(id = 122L, name = "already-forked", owner = "myorg2"))

            val model = ExtendedModelMap()
            val result = projectViewController.newFork("owner", "NewForkOrigin2", "myorg2", forkerAuth, model)
            result shouldBe "project/fork"
            @Suppress("UNCHECKED_CAST")
            (model.getAttribute("forkedProjects") as List<Project>).size shouldBe 1
        }

        it("forkOwner가 관리 조직이 아니면 로그인 사용자 본인을 fork 대상으로 삼아야 한다") {
            val original = Project(id = 123L, name = "NewForkOrigin3", owner = "owner")
            val forker = User(id = 121L, loginId = "forker4", name = "포커4")
            val forkerAuth = UsernamePasswordAuthenticationToken("forker4", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NewForkOrigin3") } returns Optional.of(original)
            every { userRepository.findByLoginId("forker4") } returns Optional.of(forker)
            every { organizationUserRepository.findByUserIdAndRoleId(121L, RoleType.ORG_ADMIN.roleType) } returns emptyList()
            every { projectRepository.findByOwnerAndOriginalProject("forker4", original) } returns emptyList()

            val result = projectViewController.newFork("owner", "NewForkOrigin3", "notmyorg", forkerAuth, ExtendedModelMap())
            result shouldBe "project/fork"
        }
    }

    // yona ProjectApp.java projects() JSON API 대응 — hideProjectListing=true 케이스는
    // HIDE_PROJECT_LISTING 스펙에서 이미 검증했으므로 그 외 분기를 보강한다.
    describe("GET /projects (JSON, projectsJson)") {
        it("로그인하지 않았으면 401을 반환해야 한다") {
            val response = projectViewController.projectsJson("", "", null)
            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        it("사이트매니저면 findProjectsForAdmin 결과를 반환해야 한다") {
            val siteManager = User(id = 200L, loginId = "sitemanager", name = "사이트매니저", state = UserState.SITE_ADMIN)
            val siteManagerAuth = UsernamePasswordAuthenticationToken("sitemanager", "password")
            every { userRepository.findByLoginId("sitemanager") } returns Optional.of(siteManager)
            every { projectRepository.findProjectsForAdmin("admin-query", any()) } returns
                PageImpl(listOf(Project(id = 201L, name = "adminproj", owner = "adminowner")))

            val response = projectViewController.projectsJson("admin-query", "", siteManagerAuth)
            response.statusCode shouldBe HttpStatus.OK
            response.body shouldBe listOf("adminowner/adminproj")
        }

        it("query가 비어 있으면 filter 값을 검색어로 사용해야 한다") {
            val siteManager = User(id = 202L, loginId = "sitemanager2", name = "사이트매니저2", state = UserState.SITE_ADMIN)
            val siteManagerAuth = UsernamePasswordAuthenticationToken("sitemanager2", "password")
            every { userRepository.findByLoginId("sitemanager2") } returns Optional.of(siteManager)
            every { projectRepository.findProjectsForAdmin("filter-value", any()) } returns PageImpl(emptyList())

            val response = projectViewController.projectsJson("", "filter-value", siteManagerAuth)
            response.statusCode shouldBe HttpStatus.OK
        }

        it("일반 사용자이고 허용된 프로젝트가 없고 공개 프로젝트도 없으면 빈 목록을 반환해야 한다") {
            val normalUser = User(id = 203L, loginId = "normaluser", name = "일반유저")
            val normalAuth = UsernamePasswordAuthenticationToken("normaluser", "password")
            every { userRepository.findByLoginId("normaluser") } returns Optional.of(normalUser)
            every { projectRepository.findAllowedProjectIdsForUser(203L) } returns emptyList()
            every { projectRepository.findPublicProjectIds() } returns emptyList()

            val response = projectViewController.projectsJson("", "", normalAuth)
            response.statusCode shouldBe HttpStatus.OK
            response.body shouldBe emptyList()
        }

        it("일반 사용자이고 허용된 프로젝트가 없지만 공개 프로젝트는 있으면 공개 프로젝트 안에서 검색해야 한다") {
            val normalUser = User(id = 204L, loginId = "normaluser2", name = "일반유저2")
            val normalAuth = UsernamePasswordAuthenticationToken("normaluser2", "password")
            every { userRepository.findByLoginId("normaluser2") } returns Optional.of(normalUser)
            every { projectRepository.findAllowedProjectIdsForUser(204L) } returns emptyList()
            every { projectRepository.findPublicProjectIds() } returns listOf(300L, 301L)
            every { projectRepository.searchProjects(listOf(300L, 301L), "", any()) } returns
                PageImpl(listOf(Project(id = 300L, name = "publicproj", owner = "publicowner")))

            val response = projectViewController.projectsJson("", "", normalAuth)
            response.statusCode shouldBe HttpStatus.OK
            response.body shouldBe listOf("publicowner/publicproj")
        }

        it("일반 사용자이고 허용된 프로젝트가 있으면 그 안에서 검색하고 Content-Range 헤더를 채워야 한다") {
            val normalUser = User(id = 205L, loginId = "normaluser3", name = "일반유저3")
            val normalAuth = UsernamePasswordAuthenticationToken("normaluser3", "password")
            every { userRepository.findByLoginId("normaluser3") } returns Optional.of(normalUser)
            every { projectRepository.findAllowedProjectIdsForUser(205L) } returns listOf(400L)
            every { projectRepository.searchProjects(listOf(400L), "query1", any()) } returns
                PageImpl(listOf(Project(id = 400L, name = "allowedproj", owner = "allowedowner")))

            val response = projectViewController.projectsJson("query1", "", normalAuth)
            response.statusCode shouldBe HttpStatus.OK
            response.body shouldBe listOf("allowedowner/allowedproj")
            response.headers.getFirst("Content-Range") shouldBe "items 1/1"
        }
    }

    // yona ProjectApp.java projects() HTML 목록 대응.
    describe("GET /projects (HTML, projects)") {
        it("비로그인 사용자이고 공개 프로젝트가 없으면 빈 페이지를 반환해야 한다") {
            every { projectRepository.findPublicProjectIds() } returns emptyList()
            val model = ExtendedModelMap()
            val result = projectViewController.projects("", 1, null, model)
            result shouldBe "project/list"
            @Suppress("UNCHECKED_CAST")
            (model.getAttribute("projects") as Page<Project>).isEmpty shouldBe true
        }

        it("비로그인 사용자이고 공개 프로젝트가 있으면 목록을 검색해 반환해야 한다") {
            every { projectRepository.findPublicProjectIds() } returns listOf(500L)
            every { projectRepository.searchProjects(listOf(500L), "%%", any()) } returns
                PageImpl(listOf(Project(id = 500L, name = "listedproj", owner = "listedowner")))

            val model = ExtendedModelMap()
            val result = projectViewController.projects("", 1, null, model)
            result shouldBe "project/list"
            @Suppress("UNCHECKED_CAST")
            (model.getAttribute("projects") as Page<Project>).content.size shouldBe 1
        }

        it("로그인 사용자이고 허용된 프로젝트가 없으면 빈 페이지를 반환해야 한다") {
            val user = User(id = 210L, loginId = "listuser", name = "목록유저")
            val auth = UsernamePasswordAuthenticationToken("listuser", "password")
            every { userRepository.findByLoginId("listuser") } returns Optional.of(user)
            every { projectRepository.findAllowedProjectIdsForUser(210L) } returns emptyList()

            val model = ExtendedModelMap()
            val result = projectViewController.projects("", 1, auth, model)
            result shouldBe "project/list"
            @Suppress("UNCHECKED_CAST")
            (model.getAttribute("projects") as Page<Project>).isEmpty shouldBe true
        }

        it("로그인 사용자이고 허용된 프로젝트가 있으면 필터 키워드로 검색해야 한다") {
            val user = User(id = 211L, loginId = "listuser2", name = "목록유저2")
            val auth = UsernamePasswordAuthenticationToken("listuser2", "password")
            every { userRepository.findByLoginId("listuser2") } returns Optional.of(user)
            every { projectRepository.findAllowedProjectIdsForUser(211L) } returns listOf(501L)
            every { projectRepository.searchProjects(listOf(501L), "%키워드%", any()) } returns
                PageImpl(listOf(Project(id = 501L, name = "필터매치", owner = "필터소유자")))

            val model = ExtendedModelMap()
            val result = projectViewController.projects("키워드", 1, auth, model)
            result shouldBe "project/list"
        }
    }

    // yona ProjectApp.java logo() 대응. 첨부파일이 없을 때의 기본 로고 폴백 경로가
    // "/Users/mzc01-search5/123/yona/..." 로 다른 개발자 로컬 macOS 절대경로에 하드코딩돼 있던 실버그를
    // 커버리지 감사 중 발견해 ClassPathResource로 수정(TASK-0270) — 이제 배포 환경에서도 정상 동작한다.
    describe("GET /projects/{projectId}/logo") {
        it("첨부파일이 없으면 classpath의 기본 로고 이미지를 200 OK로 반환해야 한다") {
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.PROJECT, "99") } returns emptyList()

            val response = projectViewController.projectLogo(99L)
            response.statusCode shouldBe HttpStatus.OK
            response.headers.contentType shouldBe MediaType.IMAGE_PNG
        }

        it("첨부파일이 있고 실제 파일이 존재하면 200 OK와 파일 내용을 반환해야 한다") {
            val tempFile = File.createTempFile("logo", ".png")
            tempFile.deleteOnExit()
            val attachment = Attachment(id = 1L, name = "logo.png", hash = "hash1", containerType = ResourceType.PROJECT, containerId = "100", mimeType = "image/png")
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.PROJECT, "100") } returns listOf(attachment)
            every { attachmentService.getFile(attachment) } returns tempFile

            val response = projectViewController.projectLogo(100L)
            response.statusCode shouldBe HttpStatus.OK
        }

        it("첨부파일은 있지만 실제 파일이 존재하지 않으면 404를 반환해야 한다") {
            val missingFile = File("/no/such/path/logo.png")
            val attachment = Attachment(id = 2L, name = "logo.png", hash = "hash2", containerType = ResourceType.PROJECT, containerId = "101")
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.PROJECT, "101") } returns listOf(attachment)
            every { attachmentService.getFile(attachment) } returns missingFile

            val response = projectViewController.projectLogo(101L)
            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }
    }

    // yona CodeApp.java 다운로드 접근 제어 대응 — 조직(그룹) 멤버 우회 허용 분기 보강.
    describe("GET /{owner}/{projectName}/code/{branch}/download 추가 분기") {
        it("isCodeAccessibleMemberOnly가 true이고 직접 멤버는 아니지만 소속 조직 멤버라면 다운로드를 허용해야 한다") {
            val org = Organization(id = 800L, name = "downloadorg")
            val groupUser = User(id = 130L, loginId = "groupdownloader", name = "그룹다운로더")
            org.organizationUsers.add(OrganizationUser(id = 1L, user = groupUser, organization = org, role = Role(id = RoleType.ORG_MEMBER.roleType)))
            val groupProject = Project(id = 131L, name = "group-download-proj", owner = "owner", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT", organization = org)
            val groupAuth = UsernamePasswordAuthenticationToken("groupdownloader", "password")
            val playRepo = mockk<PlayRepository>()

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "group-download-proj") } returns Optional.of(groupProject)
            every { userRepository.findByLoginId("groupdownloader") } returns Optional.of(groupUser)
            every { projectUserRepository.existsByProjectIdAndUserId(131L, 130L) } returns false
            every { repositoryService.getRepository(groupProject) } returns playRepo
            every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "") } returns listOf(mockk())
            every { playRepo.getArchive(any(), "main") } returns Unit

            mockMvc.perform(get("/owner/group-download-proj/code/main/download").principal(groupAuth))
                .andExpect(status().isOk)
        }

        it("isCodeAccessibleMemberOnly가 false인 비공개 프로젝트에서 접근 권한이 없으면 403을 반환해야 한다") {
            val privateProject = Project(id = 132L, name = "private-download-proj", owner = "owner", projectScope = ProjectScope.PRIVATE, isCodeAccessibleMemberOnly = false, vcs = "GIT")
            val outsider = User(id = 133L, loginId = "downloadoutsider", name = "다운로드외부인")
            val outsiderAuth = UsernamePasswordAuthenticationToken("downloadoutsider", "password")

            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "private-download-proj") } returns Optional.of(privateProject)
            every { userRepository.findByLoginId("downloadoutsider") } returns Optional.of(outsider)

            mockMvc.perform(get("/owner/private-download-proj/code/main/download").principal(outsiderAuth))
                .andExpect(status().isForbidden)
        }
    }

    // yona IssueLabelApp.update()/updateCategory() 대응 — 프로젝트 미존재/미인증 분기 보강
    // (accessControl.isAllowed는 user.projectUsers 인메모리 컬렉션을 사용하므로 이미 성공 케이스는
    // 기존 스펙에서 검증됨. 여기서는 그 앞단 가드만 보강한다).
    describe("PUT /{owner}/{projectName}/issue/label/{id} 추가 분기") {
        it("프로젝트가 없으면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchLabelProj") } returns Optional.empty()
            val response = projectViewController.updateLabelForm("owner", "NoSuchLabelProj", 1L, "n", "c", 1L, null)
            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        it("로그인하지 않았으면 403을 반환해야 한다") {
            val proj = Project(id = 140L, name = "UpdLabelProj", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "UpdLabelProj") } returns Optional.of(proj)
            val response = projectViewController.updateLabelForm("owner", "UpdLabelProj", 1L, "n", "c", 1L, null)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    describe("PUT /{owner}/{projectName}/issue/label/category/{id} 추가 분기") {
        it("프로젝트가 없으면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchCatProj") } returns Optional.empty()
            val response = projectViewController.updateCategoryForm("owner", "NoSuchCatProj", 1L, "n", false, null)
            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        it("로그인하지 않았으면 403을 반환해야 한다") {
            val proj = Project(id = 141L, name = "UpdCatProj", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "UpdCatProj") } returns Optional.of(proj)
            val response = projectViewController.updateCategoryForm("owner", "UpdCatProj", 1L, "n", false, null)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // yona IssueLabelApp.copyLabels() 대응 — 원본 프로젝트를 읽을 권한이 없거나 대상 프로젝트에
    // 라벨 생성 권한이 없는 분기 보강(둘 다 accessControl의 인메모리 판정을 사용하므로
    // user.projectUsers/project.projectScope 조합으로 자연스럽게 재현한다).
    describe("POST /{owner}/{projectName}/copyLabels 추가 분기") {
        it("원본 프로젝트를 읽을 권한이 없으면 라벨을 복사하지 않고 조용히 리다이렉트해야 한다") {
            val toProject = Project(id = 150L, name = "CopyToProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val fromProject = Project(id = 151L, name = "CopyFromProj", owner = "otherowner", projectScope = ProjectScope.PRIVATE)
            val copier = User(id = 150L, loginId = "copier", name = "복사자")
            val copierAuth = UsernamePasswordAuthenticationToken("copier", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "CopyToProj") } returns Optional.of(toProject)
            every { userRepository.findByLoginId("copier") } returns Optional.of(copier)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("otherowner", "CopyFromProj") } returns Optional.of(fromProject)

            mockMvc.perform(
                MockMvcRequestBuilders.post("/owner/CopyToProj/copyLabels")
                    .principal(copierAuth)
                    .param("owner", "otherowner")
                    .param("projectName", "CopyFromProj")
            ).andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/owner/CopyToProj/issue/labelsform"))

            verify(exactly = 0) { issueLabelService.copyLabels(any(), any()) }
        }

        it("원본은 읽을 수 있지만 대상 프로젝트에 라벨 생성 권한이 없으면 복사하지 않아야 한다") {
            val toProject = Project(id = 152L, name = "CopyToProj2", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val fromProject = Project(id = 153L, name = "CopyFromProj2", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val copier = User(id = 151L, loginId = "copier2", name = "복사자2")
            val copierAuth = UsernamePasswordAuthenticationToken("copier2", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "CopyToProj2") } returns Optional.of(toProject)
            every { userRepository.findByLoginId("copier2") } returns Optional.of(copier)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "CopyFromProj2") } returns Optional.of(fromProject)

            mockMvc.perform(
                MockMvcRequestBuilders.post("/owner/CopyToProj2/copyLabels")
                    .principal(copierAuth)
                    .param("owner", "owner")
                    .param("projectName", "CopyFromProj2")
            ).andExpect(status().is3xxRedirection)

            verify(exactly = 0) { issueLabelService.copyLabels(any(), any()) }
        }
    }

    // yona IssueLabelApp.labelsForm() 대응 — 사이트매니저 우회 분기 및 앞단 가드 보강.
    describe("GET /{owner}/{projectName}/issue/labelsform 추가 분기") {
        it("MANAGER가 아니어도 사이트매니저면 라벨 설정 화면에 접근할 수 있어야 한다") {
            val proj = Project(id = 160L, name = "SiteMgrLabelProj", owner = "owner")
            val siteManager = User(id = 160L, loginId = "sitemgrlabel", name = "사이트매니저라벨", state = UserState.SITE_ADMIN)
            val siteManagerAuth = UsernamePasswordAuthenticationToken("sitemgrlabel", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "SiteMgrLabelProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("sitemgrlabel") } returns Optional.of(siteManager)
            every { projectUserRepository.findByProjectIdAndUserId(160L, 160L) } returns Optional.empty()
            every { issueLabelService.getLabels(160L) } returns emptyList()

            mockMvc.perform(get("/owner/SiteMgrLabelProj/issue/labelsform").principal(siteManagerAuth))
                .andExpect(status().isOk)
                .andExpect(view().name("project/issuelabels"))
        }

        it("MANAGER도 아니고 사이트매니저도 아니면 error/forbidden 뷰를 반환해야 한다") {
            val proj = Project(id = 161L, name = "NoPermLabelProj", owner = "owner")
            val outsider = User(id = 161L, loginId = "labeloutsider2", name = "라벨외부인2")
            val outsiderAuth = UsernamePasswordAuthenticationToken("labeloutsider2", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoPermLabelProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("labeloutsider2") } returns Optional.of(outsider)
            every { projectUserRepository.findByProjectIdAndUserId(161L, 161L) } returns Optional.empty()

            mockMvc.perform(get("/owner/NoPermLabelProj/issue/labelsform").principal(outsiderAuth))
                .andExpect(view().name("error/forbidden"))
        }

        it("프로젝트가 없으면 error/404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchLabelsForm") } returns Optional.empty()
            mockMvc.perform(get("/owner/NoSuchLabelsForm/issue/labelsform"))
                .andExpect(view().name("error/404"))
        }

        it("로그인하지 않았으면 로그인 폼으로 리다이렉트해야 한다") {
            val proj = Project(id = 162L, name = "NoLoginLabelsForm", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoLoginLabelsForm") } returns Optional.of(proj)
            mockMvc.perform(get("/owner/NoLoginLabelsForm/issue/labelsform"))
                .andExpect(redirectedUrl("/users/loginform"))
        }
    }

    // yona ProjectApp.java changeVCSForm() 대응 — 앞단 가드 및 SUBVERSION -> GIT 제안 분기 보강.
    describe("GET /{owner}/{projectName}/changeVCS 추가 분기") {
        it("프로젝트가 없으면 error/404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchVCSProj") } returns Optional.empty()
            mockMvc.perform(get("/owner/NoSuchVCSProj/changeVCS"))
                .andExpect(view().name("error/404"))
        }

        it("멤버가 아니면 error/forbidden을 반환해야 한다") {
            val proj = Project(id = 170L, name = "VCSProj", owner = "owner")
            val outsider = User(id = 170L, loginId = "vcsoutsider", name = "VCS외부인")
            val outsiderAuth = UsernamePasswordAuthenticationToken("vcsoutsider", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "VCSProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("vcsoutsider") } returns Optional.of(outsider)
            every { projectUserRepository.existsByProjectIdAndUserId(170L, 170L) } returns false

            mockMvc.perform(get("/owner/VCSProj/changeVCS").principal(outsiderAuth))
                .andExpect(view().name("error/forbidden"))
        }

        it("MANAGER 권한이 없는 일반 멤버라면 error/forbidden을 반환해야 한다") {
            val proj = Project(id = 171L, name = "VCSProj2", owner = "owner")
            val member = User(id = 171L, loginId = "vcsmember", name = "VCS멤버")
            val memberAuth = UsernamePasswordAuthenticationToken("vcsmember", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "VCSProj2") } returns Optional.of(proj)
            every { userRepository.findByLoginId("vcsmember") } returns Optional.of(member)
            every { projectUserRepository.existsByProjectIdAndUserId(171L, 171L) } returns true
            every { projectUserRepository.findByProjectIdAndUserId(171L, 171L) } returns
                Optional.of(ProjectUser(id = 1710L, user = member, project = proj, role = Role(id = RoleType.MEMBER.roleType)))

            mockMvc.perform(get("/owner/VCSProj2/changeVCS").principal(memberAuth))
                .andExpect(view().name("error/forbidden"))
        }

        it("현재 VCS가 SUBVERSION이면 다음 VCS로 MERCURIAL을 제안해야 한다(yona-wiki P3-12 — GIT->SUBVERSION->MERCURIAL->GIT 3종 순환)") {
            val proj = Project(id = 172L, name = "VCSProj3", owner = "owner", vcs = "SUBVERSION")
            val manager = User(id = 172L, loginId = "vcsmanager", name = "VCS매니저")
            val managerAuth = UsernamePasswordAuthenticationToken("vcsmanager", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "VCSProj3") } returns Optional.of(proj)
            every { userRepository.findByLoginId("vcsmanager") } returns Optional.of(manager)
            every { projectUserRepository.existsByProjectIdAndUserId(172L, 172L) } returns true
            every { projectUserRepository.findByProjectIdAndUserId(172L, 172L) } returns
                Optional.of(ProjectUser(id = 1720L, user = manager, project = proj, role = Role(id = RoleType.MANAGER.roleType)))

            mockMvc.perform(get("/owner/VCSProj3/changeVCS").principal(managerAuth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("nextVcs", "MERCURIAL"))
        }

        it("현재 VCS가 MERCURIAL이면 다음 VCS로 GIT을 제안해야 한다(순환 완결)") {
            val proj = Project(id = 173L, name = "VCSProj4", owner = "owner", vcs = "MERCURIAL")
            val manager = User(id = 173L, loginId = "vcsmanager2", name = "VCS매니저2")
            val managerAuth = UsernamePasswordAuthenticationToken("vcsmanager2", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "VCSProj4") } returns Optional.of(proj)
            every { userRepository.findByLoginId("vcsmanager2") } returns Optional.of(manager)
            every { projectUserRepository.existsByProjectIdAndUserId(173L, 173L) } returns true
            every { projectUserRepository.findByProjectIdAndUserId(173L, 173L) } returns
                Optional.of(ProjectUser(id = 1730L, user = manager, project = proj, role = Role(id = RoleType.MANAGER.roleType)))

            mockMvc.perform(get("/owner/VCSProj4/changeVCS").principal(managerAuth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("nextVcs", "GIT"))
        }
    }

    // yona ProjectApp.java changeVCS() POST 대응 — 앞단 가드 보강(성공 케이스는 기존 스펙에 있음).
    describe("POST /{owner}/{projectName}/changeVCS 추가 분기") {
        it("로그인하지 않았으면 401을 반환해야 한다") {
            val proj = Project(id = 182L, name = "AnyProj", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "AnyProj") } returns Optional.of(proj)

            val response = projectViewController.changeVCS("owner", "AnyProj", null)
            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        it("프로젝트가 없으면 404를 반환해야 한다") {
            val user = User(id = 180L, loginId = "vcspostuser", name = "VCS포스트유저")
            val auth = UsernamePasswordAuthenticationToken("vcspostuser", "password")
            every { userRepository.findByLoginId("vcspostuser") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchVCSPost") } returns Optional.empty()

            val response = projectViewController.changeVCS("owner", "NoSuchVCSPost", auth)
            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        it("MANAGER 권한이 없으면 403을 반환해야 한다") {
            val proj = Project(id = 181L, name = "VCSPostProj", owner = "owner")
            val user = User(id = 181L, loginId = "vcspostuser2", name = "VCS포스트유저2")
            val auth = UsernamePasswordAuthenticationToken("vcspostuser2", "password")
            every { userRepository.findByLoginId("vcspostuser2") } returns Optional.of(user)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "VCSPostProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectIdAndUserId(181L, 181L) } returns Optional.empty()

            val response = projectViewController.changeVCS("owner", "VCSPostProj", auth)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // yona ProjectApp.java setting() 대응 — 저장소 브랜치/기본 브랜치 조회 예외 처리 분기 보강.
    describe("GET /{owner}/{projectName}/setting 저장소 조회 예외 처리") {
        it("브랜치 목록/기본 브랜치 조회 중 예외가 발생하면 빈 목록과 master로 대체해야 한다") {
            val proj = Project(id = 190L, name = "SettingExProj", owner = "owner")
            val manager = User(id = 190L, loginId = "settingexmanager", name = "설정예외매니저")
            val managerAuth = UsernamePasswordAuthenticationToken("settingexmanager", "password")
            val playRepo = mockk<PlayRepository>()
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "SettingExProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("settingexmanager") } returns Optional.of(manager)
            every { projectUserRepository.existsByProjectIdAndUserId(190L, 190L) } returns true
            every { projectUserRepository.findByProjectIdAndUserId(190L, 190L) } returns
                Optional.of(ProjectUser(id = 1900L, user = manager, project = proj, role = Role(id = RoleType.MANAGER.roleType)))
            every { repositoryService.getRepository(proj) } returns playRepo
            every { playRepo.getRefNames() } throws RuntimeException("branch 조회 실패")
            every { playRepo.getDefaultBranch() } throws RuntimeException("default branch 조회 실패")

            mockMvc.perform(get("/owner/SettingExProj/setting").principal(managerAuth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("branches", emptyList<String>()))
                .andExpect(model().attribute("defaultBranch", "master"))
        }
    }

    // yona GitApp/ProjectApp getReadmeFileName() 대응 — README.md 대문자 파일이 없을 때의
    // 소문자/SVN 경로 폴백 분기 보강(기본 GIT + README.md 존재 케이스는 기존 스펙에 있음).
    describe("getReadmeFileName 분기 (GET /{owner}/{projectName}?tabId=readme)") {
        it("README.md는 없지만 소문자 readme.md는 있으면 소문자 파일명을 사용해야 한다") {
            val proj = Project(id = 200L, name = "LowerReadmeProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val readmeUser = User(id = 200L, loginId = "lowerreadmeuser", name = "소문자README유저")
            val auth = UsernamePasswordAuthenticationToken("lowerreadmeuser", "password")
            val playRepo = mockk<PlayRepository>()
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "LowerReadmeProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("lowerreadmeuser") } returns Optional.of(readmeUser)
            every { projectUserRepository.findByProjectId(200L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()
            every { repositoryService.getRepository(proj) } returns playRepo
            every { playRepo.isFile("README.md") } returns false
            every { playRepo.isFile("readme.md") } returns true
            every { playRepo.getRawFile("HEAD", "readme.md") } returns "소문자 리드미".toByteArray(Charsets.UTF_8)
            every { markdownService.renderFileInReadme("소문자 리드미", proj) } returns "<p>소문자 리드미</p>"

            mockMvc.perform(get("/owner/LowerReadmeProj").param("tabId", "readme").principal(auth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("readmeFileName", "readme.md"))
        }

        it("SVN 저장소는 /trunk/README.md 경로를 사용해야 한다") {
            val proj = Project(id = 201L, name = "SvnReadmeProj", owner = "owner", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
            val svnUser = User(id = 201L, loginId = "svnreadmeuser", name = "SVN README유저")
            val auth = UsernamePasswordAuthenticationToken("svnreadmeuser", "password")
            // getReadmeFileName()은 repo.javaClass.simpleName에 "Svn"이 포함되는지로 SVN 여부를
            // 판별하므로, mockk<PlayRepository>() 대신 실제 SvnRepository 인스턴스를 spyk로 감싸
            // isFile/getRawFile만 오버라이드한다(생성자 부수효과는 없어 안전).
            val svnRepo = spyk(SvnRepository(ownerName = "owner", projectName = "SvnReadmeProj", baseDir = "/tmp/yona-test-svn-base", userResolver = { null }))
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "SvnReadmeProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("svnreadmeuser") } returns Optional.of(svnUser)
            every { projectUserRepository.findByProjectId(201L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()
            every { repositoryService.getRepository(proj) } returns svnRepo
            every { svnRepo.isFile("README.md") } returns false
            every { svnRepo.isFile("readme.md") } returns false
            every { svnRepo.isFile("/trunk/README.md") } returns true
            every { svnRepo.getRawFile("HEAD", "/trunk/README.md") } returns "SVN 리드미".toByteArray(Charsets.UTF_8)
            every { markdownService.renderFileInReadme("SVN 리드미", proj) } returns "<p>SVN 리드미</p>"

            mockMvc.perform(get("/owner/SvnReadmeProj").param("tabId", "readme").principal(auth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("readmeFileName", "/trunk/README.md"))
        }
    }

    // ============================================================================================
    // TASK-분기커버리지 95% 보강 — 위 배치들에서 남은 미실행 분기. 실제 코드(if/elvis/try-catch/&&)를
    // 근거로, 아직 어떤 테스트도 거치지 않은 조합만 추가한다(중복 작성 금지).
    // ============================================================================================

    // getProjectHistory 잔여 분기 — 게시글 작성자 조회 성공(positive), 각 소스의 createdDate/created가
    // 없을 때의 Instant.now() 대체값, 활성화됐지만 조회 결과가 전부 비어있는 경우(반복문 0회) 보강.
    describe("getProjectHistory 잔여 분기 보강") {
        it("코드/이슈/게시글/PR이 모두 활성화됐지만 조회 결과가 모두 비어있으면 예외 없이 빈 이력을 반환해야 한다") {
            val emptyResultProject = Project(id = 900L, name = "EmptyResultProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val historyUser2 = User(id = 900L, loginId = "historyuser2", name = "히스토리유저2")
            val auth2 = UsernamePasswordAuthenticationToken("historyuser2", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "EmptyResultProj") } returns Optional.of(emptyResultProject)
            every { userRepository.findByLoginId("historyuser2") } returns Optional.of(historyUser2)
            every { projectUserRepository.findByProjectId(900L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()
            val playRepo = mockk<PlayRepository>()
            every { repositoryService.getRepository(emptyResultProject) } returns playRepo
            every { playRepo.getHistory(0, 10, null, null) } returns emptyList()
            every { issueRepository.findByProject(emptyResultProject, any()) } returns PageImpl(emptyList())
            every { postingRepository.findByProject(emptyResultProject, any()) } returns PageImpl(emptyList())
            every { pullRequestRepository.findByToProject(emptyResultProject, any()) } returns PageImpl(emptyList())

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "EmptyResultProj", "history", auth2, model)

            @Suppress("UNCHECKED_CAST")
            val histories = model.getAttribute("histories") as List<HistoryDto>
            histories.size shouldBe 0
        }

        it("게시글 작성자가 authorLoginId로 조회되어 발견되면 who/userPageUrl을 작성자 정보로 채워야 한다") {
            val proj = Project(
                id = 901L, name = "PostAuthorFoundProj", owner = "owner", projectScope = ProjectScope.PUBLIC,
                isCodeEnabled = false, isIssueEnabled = false, isPullRequestEnabled = false
            )
            val user2 = User(id = 901L, loginId = "postauthoruser", name = "게시글작성자조회유저")
            val auth2 = UsernamePasswordAuthenticationToken("postauthoruser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PostAuthorFoundProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("postauthoruser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectId(901L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()

            val postAuthor = User(id = 902L, loginId = "foundpostauthor", name = "발견된게시글작성자")
            val posting = Posting(
                id = 800L, title = "게시글", project = proj, number = 1L,
                createdDate = Instant.now(), authorLoginId = "foundpostauthor", authorName = "원본이름"
            )
            every { postingRepository.findByProject(proj, any()) } returns PageImpl(listOf(posting))
            every { userRepository.findByLoginId("foundpostauthor") } returns Optional.of(postAuthor)

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "PostAuthorFoundProj", "history", auth2, model)

            @Suppress("UNCHECKED_CAST")
            val histories = model.getAttribute("histories") as List<HistoryDto>
            histories.size shouldBe 1
            histories[0].userPageUrl shouldBe "/user/foundpostauthor"
        }

        it("이슈 생성일이 없으면 현재 시각으로 대체해야 한다") {
            val proj = Project(
                id = 903L, name = "IssueNoDateProj", owner = "owner", projectScope = ProjectScope.PUBLIC,
                isCodeEnabled = false, isBoardEnabled = false, isPullRequestEnabled = false
            )
            val user2 = User(id = 903L, loginId = "issuenodateuser", name = "이슈날짜없는유저")
            val auth2 = UsernamePasswordAuthenticationToken("issuenodateuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "IssueNoDateProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("issuenodateuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectId(903L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()

            val issueNoDate = Issue(
                id = 42L, title = "날짜없는이슈", project = proj, number = 1L,
                createdDate = null, authorLoginId = null, authorName = "작성자"
            )
            every { issueRepository.findByProject(proj, any()) } returns PageImpl(listOf(issueNoDate))

            val before = Instant.now()
            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "IssueNoDateProj", "history", auth2, model)
            val after = Instant.now()

            @Suppress("UNCHECKED_CAST")
            val histories = model.getAttribute("histories") as List<HistoryDto>
            histories.size shouldBe 1
            (!histories[0].whenInstant.isBefore(before) && !histories[0].whenInstant.isAfter(after)) shouldBe true
        }

        it("게시글 생성일이 없으면 현재 시각으로 대체해야 한다") {
            val proj = Project(
                id = 904L, name = "PostNoDateProj", owner = "owner", projectScope = ProjectScope.PUBLIC,
                isCodeEnabled = false, isIssueEnabled = false, isPullRequestEnabled = false
            )
            val user2 = User(id = 904L, loginId = "postnodateuser", name = "게시글날짜없는유저")
            val auth2 = UsernamePasswordAuthenticationToken("postnodateuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PostNoDateProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("postnodateuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectId(904L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()

            val postingNoDate = Posting(
                id = 801L, title = "날짜없는게시글", project = proj, number = 1L,
                createdDate = null, authorLoginId = null, authorName = "작성자"
            )
            every { postingRepository.findByProject(proj, any()) } returns PageImpl(listOf(postingNoDate))

            val before = Instant.now()
            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "PostNoDateProj", "history", auth2, model)
            val after = Instant.now()

            @Suppress("UNCHECKED_CAST")
            val histories = model.getAttribute("histories") as List<HistoryDto>
            histories.size shouldBe 1
            (!histories[0].whenInstant.isBefore(before) && !histories[0].whenInstant.isAfter(after)) shouldBe true
        }

        it("PR 생성일이 없으면 현재 시각으로 대체해야 한다") {
            val proj = Project(
                id = 905L, name = "PrNoDateProj", owner = "owner", projectScope = ProjectScope.PUBLIC,
                isCodeEnabled = false, isIssueEnabled = false, isBoardEnabled = false
            )
            val user2 = User(id = 905L, loginId = "prnodateuser", name = "PR날짜없는유저")
            val auth2 = UsernamePasswordAuthenticationToken("prnodateuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PrNoDateProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("prnodateuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectId(905L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()

            val contributor = User(id = 906L, loginId = "prcontrib2", name = "PR기여자2")
            val pullNoDate = PullRequest(
                id = 802L, title = "날짜없는PR", toProject = proj, fromProject = proj,
                contributor = contributor, number = 1L, created = null
            )
            every { pullRequestRepository.findByToProject(proj, any()) } returns PageImpl(listOf(pullNoDate))

            val before = Instant.now()
            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "PrNoDateProj", "history", auth2, model)
            val after = Instant.now()

            @Suppress("UNCHECKED_CAST")
            val histories = model.getAttribute("histories") as List<HistoryDto>
            histories.size shouldBe 1
            (!histories[0].whenInstant.isBefore(before) && !histories[0].whenInstant.isAfter(after)) shouldBe true
        }
    }

    // sendTransferRequestMail / getServerUrl 잔여 분기 — 조직 관리자 0명, 관리자 이메일 공백,
    // 표준 포트(80/443)가 아닌 경우의 URL 조합 보강.
    describe("sendTransferRequestMail / getServerUrl 잔여 분기 보강") {
        val proj = Project(id = 910L, name = "MailBranchProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
        val manager = User(id = 910L, loginId = "mailbranchmanager", name = "메일분기매니저")
        val managerAuth = UsernamePasswordAuthenticationToken("mailbranchmanager", "password")
        val managerProjectUser = ProjectUser(id = 9100L, user = manager, project = proj, role = Role(id = RoleType.MANAGER.roleType))

        fun mailBranchRequest(scheme: String = "https", serverName: String = "yona.io", port: Int = 443): HttpServletRequest {
            val request = mockk<HttpServletRequest>()
            every { request.scheme } returns scheme
            every { request.serverName } returns serverName
            every { request.serverPort } returns port
            return request
        }

        it("이관 대상 조직에 ORG_ADMIN이 한 명도 없으면 메일을 보내지 않아야 한다") {
            every { userRepository.findByLoginId("mailbranchmanager") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MailBranchProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectIdAndUserId(910L, 910L) } returns Optional.of(managerProjectUser)
            every { userRepository.findByLoginId("noadminorg") } returns Optional.empty()
            val destOrg = Organization(id = 911L, name = "noadminorg")
            every { organizationRepository.findByName("noadminorg") } returns Optional.of(destOrg)
            every { organizationUserRepository.findByOrganizationId(911L) } returns emptyList()
            val pt = ProjectTransfer(id = 950L, sender = manager, destination = "noadminorg", project = proj, confirmKey = "key950", newProjectName = "MailBranchProj")
            every { projectService.requestNewTransfer(910L, 910L, "noadminorg") } returns pt
            every { messageSource.getMessage(any(), any(), any()) } returns "메시지"
            every { markdownService.render(any(), true, proj) } returns "<p>html</p>"

            val response = projectViewController.transferProject("owner", "MailBranchProj", "noadminorg", mailBranchRequest(), managerAuth)
            response.statusCode shouldBe HttpStatus.NO_CONTENT
            verify(exactly = 0) { mailService.sendHtmlMail(any(), any(), any(), any()) }
        }

        it("이관 대상 조직 관리자의 이메일이 비어 있으면 발송을 건너뛰어야 한다") {
            every { userRepository.findByLoginId("mailbranchmanager") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MailBranchProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectIdAndUserId(910L, 910L) } returns Optional.of(managerProjectUser)
            every { userRepository.findByLoginId("blankadminorg") } returns Optional.empty()
            val destOrg = Organization(id = 912L, name = "blankadminorg")
            every { organizationRepository.findByName("blankadminorg") } returns Optional.of(destOrg)
            val blankAdmin = User(id = 913L, loginId = "blankemailadmin", name = "이메일공백관리자", email = "")
            every { organizationUserRepository.findByOrganizationId(912L) } returns listOf(
                OrganizationUser(id = 1L, user = blankAdmin, organization = destOrg, role = Role(id = RoleType.ORG_ADMIN.roleType))
            )
            val pt = ProjectTransfer(id = 951L, sender = manager, destination = "blankadminorg", project = proj, confirmKey = "key951", newProjectName = "MailBranchProj")
            every { projectService.requestNewTransfer(910L, 910L, "blankadminorg") } returns pt
            every { messageSource.getMessage(any(), any(), any()) } returns "메시지"
            every { markdownService.render(any(), true, proj) } returns "<p>html</p>"

            val response = projectViewController.transferProject("owner", "MailBranchProj", "blankadminorg", mailBranchRequest(), managerAuth)
            response.statusCode shouldBe HttpStatus.NO_CONTENT
            verify(exactly = 0) { mailService.sendHtmlMail(any(), any(), any(), any()) }
        }

        it("서버 포트가 80/443이 아니면 URL에 포트 번호를 포함해야 한다") {
            every { userRepository.findByLoginId("mailbranchmanager") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MailBranchProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectIdAndUserId(910L, 910L) } returns Optional.of(managerProjectUser)
            val destUser = User(id = 914L, loginId = "portdest", name = "포트대상", email = "portdest@yona.io")
            every { userRepository.findByLoginId("portdest") } returns Optional.of(destUser)
            every { organizationRepository.findByName("portdest") } returns Optional.empty()
            val pt = ProjectTransfer(id = 952L, sender = manager, destination = "portdest", project = proj, confirmKey = "key952", newProjectName = "MailBranchProj")
            every { projectService.requestNewTransfer(910L, 910L, "portdest") } returns pt
            every { messageSource.getMessage(any(), any(), any()) } returns "메시지"
            val markdownSlot = slot<String>()
            every { markdownService.render(capture(markdownSlot), true, proj) } returns "<p>html</p>"
            every { mailService.sendHtmlMail("portdest@yona.io", "Yona", any(), "<p>html</p>") } just Runs

            val response = projectViewController.transferProject(
                "owner", "MailBranchProj", "portdest",
                mailBranchRequest(scheme = "http", serverName = "dev.yona.io", port = 8080), managerAuth
            )
            response.statusCode shouldBe HttpStatus.NO_CONTENT
            markdownSlot.captured.contains("http://dev.yona.io:8080") shouldBe true
        }
    }

    // downloadCode 잔여 분기 — 완전 비로그인(Authentication 자체가 없는) 상태의 멤버 전용 프로젝트
    // 접근과 isCodeAccessibleMemberOnly=false 프로젝트의 정상 다운로드 성공 분기 보강.
    describe("downloadCode 잔여 분기 보강") {
        it("isCodeAccessibleMemberOnly가 true인 프로젝트에 완전히 비로그인 상태로 접근하면 403을 반환해야 한다") {
            val proj = Project(id = 920L, name = "AnonMemberOnlyProj", owner = "owner", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = true, vcs = "GIT")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "AnonMemberOnlyProj") } returns Optional.of(proj)

            mockMvc.perform(get("/owner/AnonMemberOnlyProj/code/main/download"))
                .andExpect(status().isForbidden)
        }

        it("isCodeAccessibleMemberOnly가 false인 공개 프로젝트는 비로그인 상태에서도 다운로드가 성공해야 한다") {
            val proj = Project(id = 921L, name = "AnonPublicDownloadProj", owner = "owner", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = false, vcs = "GIT")
            val playRepo = mockk<PlayRepository>()
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "AnonPublicDownloadProj") } returns Optional.of(proj)
            every { repositoryService.getRepository(proj) } returns playRepo
            every { repositoryService.getMetaDataFromAncestorDirectories(playRepo, "main", "") } returns listOf(mockk())
            every { playRepo.getArchive(any(), "main") } returns Unit

            mockMvc.perform(get("/owner/AnonPublicDownloadProj/code/main/download"))
                .andExpect(status().isOk)
        }
    }

    // getProjectDashboardData 잔여 분기 — 이슈는 있지만 마일스톤/라벨/PR은 전혀 없는 부분-비어있음
    // 조합 보강(전체가 비어있는 케이스, 전체가 채워진 케이스는 기존 스펙에 있음).
    describe("getProjectDashboardData 잔여 분기 보강") {
        it("이슈는 있지만 마일스톤/라벨/PR이 없는 프로젝트에서도 안전하게 계산돼야 한다") {
            val proj = Project(id = 930L, name = "PartialDashProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val user2 = User(id = 930L, loginId = "partialdashuser", name = "부분대시보드유저")
            val auth2 = UsernamePasswordAuthenticationToken("partialdashuser", "password")
            every { userRepository.findByLoginId("partialdashuser") } returns Optional.of(user2)
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()

            val issueOnly = Issue(id = 60L, title = "이슈만있음", project = proj, number = 1L, state = State.OPEN)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PartialDashProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectId(930L) } returns emptyList()
            every { issueRepository.findByProjectAndState(proj, State.OPEN) } returns listOf(issueOnly)
            every { issueRepository.findByProject(proj) } returns listOf(issueOnly)
            every { milestoneRepository.findByProjectAndState(proj, State.OPEN) } returns emptyList()
            every { pullRequestRepository.findByToProjectAndState(proj, State.OPEN, any()) } returns PageImpl(emptyList())
            every { pullRequestRepository.findByToProjectAndState(proj, State.OPEN) } returns emptyList()
            every { issueLabelService.getLabels(930L) } returns emptyList()

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "PartialDashProj", "dashboard", auth2, model)

            model.getAttribute("openIssuesCount") shouldBe 1
            model.getAttribute("notAssignedIssuesCount") shouldBe 1
            model.getAttribute("notAssignedIssuesPercent") shouldBe 100

            @Suppress("UNCHECKED_CAST")
            val milestoneList = model.getAttribute("milestoneList") as List<ProjectViewController.MilestoneDashboardDto>
            milestoneList.size shouldBe 0

            @Suppress("UNCHECKED_CAST")
            val labelCategories = model.getAttribute("labelCategories") as List<ProjectViewController.LabelCategoryDashboardDto>
            labelCategories.size shouldBe 0
        }
    }

    // newProject 잔여 분기 — 비로그인 리다이렉트와 createProject() 예외 처리(catch) 블록은 기존 스펙에서
    // 전혀 거치지 않았다(조직 admin 가드 성공/실패 케이스만 있었음).
    describe("newProject(POST /projectform) 잔여 분기 보강") {
        it("로그인하지 않았으면 로그인 폼으로 리다이렉트해야 한다") {
            mockMvc.perform(
                MockMvcRequestBuilders.post("/projectform")
                    .param("owner", "anon")
                    .param("name", "anonproj")
                    .param("overview", "설명")
                    .param("projectScope", "PUBLIC")
                    .param("vcs", "GIT")
            ).andExpect(redirectedUrl("/users/loginform"))
        }

        it("프로젝트 생성 중 예외가 발생하면 에러 메시지와 함께 폼을 다시 보여줘야 한다") {
            val user2 = User(id = 940L, loginId = "newprojexuser", name = "생성예외유저")
            val auth2 = UsernamePasswordAuthenticationToken("newprojexuser", "password")
            every { userRepository.findByLoginId("newprojexuser") } returns Optional.of(user2)
            every { organizationRepository.findByName("newprojexuser") } returns Optional.empty()
            every { projectService.createProject(any(), user2) } throws RuntimeException("이미 존재하는 프로젝트입니다.")
            every { organizationUserRepository.findByUserIdAndRoleId(940L, RoleType.ORG_ADMIN.roleType) } returns emptyList()

            mockMvc.perform(
                MockMvcRequestBuilders.post("/projectform")
                    .principal(auth2)
                    .param("owner", "newprojexuser")
                    .param("name", "dupname")
                    .param("overview", "설명")
                    .param("projectScope", "PUBLIC")
                    .param("vcs", "GIT")
            ).andExpect(status().isOk)
                .andExpect(view().name("project/create"))
                .andExpect(model().attribute("error", "이미 존재하는 프로젝트입니다."))
        }

        it("createProject 실패 시 예외 메시지가 없으면 기본 에러 메시지를 사용해야 한다") {
            val user2 = User(id = 941L, loginId = "newprojexuser2", name = "생성예외유저2")
            val auth2 = UsernamePasswordAuthenticationToken("newprojexuser2", "password")
            every { userRepository.findByLoginId("newprojexuser2") } returns Optional.of(user2)
            every { organizationRepository.findByName("newprojexuser2") } returns Optional.empty()
            every { projectService.createProject(any(), user2) } throws RuntimeException()
            every { organizationUserRepository.findByUserIdAndRoleId(941L, RoleType.ORG_ADMIN.roleType) } returns emptyList()

            val model = ExtendedModelMap()
            val result = projectViewController.newProject(
                "newprojexuser2", "noname", "설명", "PUBLIC", "GIT",
                false, false, false, false, false, false, auth2, model
            )
            result shouldBe "project/create"
            model.getAttribute("error") shouldBe "프로젝트 생성 도중 오류가 발생했습니다."
        }

        it("요청의 projectScope 값이 올바르지 않으면 재구성된 폼의 projectScope는 PUBLIC으로 대체돼야 한다") {
            val user2 = User(id = 942L, loginId = "newprojbadscope", name = "잘못된스코프유저")
            val auth2 = UsernamePasswordAuthenticationToken("newprojbadscope", "password")
            every { userRepository.findByLoginId("newprojbadscope") } returns Optional.of(user2)
            every { organizationRepository.findByName("newprojbadscope") } returns Optional.empty()
            every { organizationUserRepository.findByUserIdAndRoleId(942L, RoleType.ORG_ADMIN.roleType) } returns emptyList()

            val model = ExtendedModelMap()
            val result = projectViewController.newProject(
                "newprojbadscope", "noname", "설명", "NOT_A_SCOPE", "GIT",
                false, false, false, false, false, false, auth2, model
            )
            result shouldBe "project/create"
            val form = model.getAttribute("form") as NewProjectForm
            form.projectScope shouldBe ProjectScope.PUBLIC
        }
    }

    // projectChangeVCSForm 잔여 분기 — 비로그인(Authentication 자체가 없는) 상태로 존재하는 프로젝트에
    // 접근하는 경우는 "멤버가 아니면" 테스트(인증된 outsider)와 서로 다른 분기라 별도로 보강한다.
    describe("projectChangeVCSForm 잔여 분기 보강") {
        it("비로그인 상태로 존재하는 프로젝트의 변경 폼에 접근하면 error/forbidden을 반환해야 한다") {
            val proj = Project(id = 950L, name = "AnonVCSFormProj", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "AnonVCSFormProj") } returns Optional.of(proj)

            mockMvc.perform(get("/owner/AnonVCSFormProj/changeVCS"))
                .andExpect(view().name("error/forbidden"))
        }
    }

    // newFork 잔여 분기 — Authentication은 있지만 그 사용자 레코드를 DB에서 찾을 수 없는 경우
    // (세션은 유효하나 계정이 삭제된 상황 등)는 완전 비로그인과 별개의 분기다.
    describe("newFork 잔여 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 로그인 폼으로 리다이렉트해야 한다") {
            val original = Project(id = 960L, name = "GhostForkOrigin", owner = "owner")
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostforkuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GhostForkOrigin") } returns Optional.of(original)
            every { userRepository.findByLoginId("ghostforkuser") } returns Optional.empty()

            val result = projectViewController.newFork("owner", "GhostForkOrigin", null, ghostAuth, ExtendedModelMap())
            result shouldBe "redirect:/users/loginform"
        }
    }

    // copyLabelsForm 잔여 분기 — 대상(toProject) 미존재(404), 완전 비로그인, 인증은 있으나 사용자
    // 레코드 없음(둘 다 로그인 폼 리다이렉트이지만 서로 다른 분기 지점) 보강.
    describe("copyLabelsForm 잔여 분기 보강") {
        it("대상 프로젝트가 없으면 error/404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchCopyToProj") } returns Optional.empty()

            mockMvc.perform(
                MockMvcRequestBuilders.post("/owner/NoSuchCopyToProj/copyLabels")
                    .param("owner", "owner")
                    .param("projectName", "whatever")
            ).andExpect(view().name("error/404"))
        }

        it("비로그인 상태면 로그인 폼으로 리다이렉트해야 한다") {
            val toProject = Project(id = 970L, name = "AnonCopyToProj", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "AnonCopyToProj") } returns Optional.of(toProject)

            mockMvc.perform(
                MockMvcRequestBuilders.post("/owner/AnonCopyToProj/copyLabels")
                    .param("owner", "owner")
                    .param("projectName", "whatever")
            ).andExpect(redirectedUrl("/users/loginform"))
        }

        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 로그인 폼으로 리다이렉트해야 한다") {
            val toProject = Project(id = 971L, name = "GhostCopyToProj", owner = "owner")
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostcopyuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GhostCopyToProj") } returns Optional.of(toProject)
            every { userRepository.findByLoginId("ghostcopyuser") } returns Optional.empty()

            mockMvc.perform(
                MockMvcRequestBuilders.post("/owner/GhostCopyToProj/copyLabels")
                    .principal(ghostAuth)
                    .param("owner", "owner")
                    .param("projectName", "whatever")
            ).andExpect(redirectedUrl("/users/loginform"))
        }
    }

    // projectHome 잔여 분기 — 완전 비로그인 공개 프로젝트 접근(방문 이력 미기록/watch 미조회),
    // README 본문 조회 실패(getReadmeContent가 null), isMilestoneEnabled=false 보강.
    describe("projectHome 잔여 분기 보강") {
        it("비로그인 사용자가 공개 프로젝트에 접근하면 200 OK를 반환하고 방문 이력을 남기지 않아야 한다") {
            val proj = Project(id = 980L, name = "AnonPublicHomeProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "AnonPublicHomeProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectId(980L) } returns emptyList()
            every { watchService.findWatchers(any(), any()) } returns emptySet()

            mockMvc.perform(get("/owner/AnonPublicHomeProj"))
                .andExpect(status().isOk)
                .andExpect(view().name("project/home"))

            verify(exactly = 0) { recentProjectRepository.recordVisit(any(), any()) }
            verify(exactly = 0) { watchService.isWatching(any(), any(), any()) }
        }

        it("README 파일은 있지만 내용을 읽는 데 실패하면 readmeHtml은 null이어야 한다") {
            val proj = Project(id = 981L, name = "ReadmeContentFailProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val user2 = User(id = 981L, loginId = "readmefailuser", name = "리드미실패유저")
            val auth2 = UsernamePasswordAuthenticationToken("readmefailuser", "password")
            val playRepo = mockk<PlayRepository>()
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "ReadmeContentFailProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("readmefailuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectId(981L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()
            every { repositoryService.getRepository(proj) } returns playRepo
            every { playRepo.isFile("README.md") } returns true
            every { playRepo.getRawFile("HEAD", "README.md") } throws RuntimeException("파일 조회 실패")

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "ReadmeContentFailProj", "readme", auth2, model)
            model.getAttribute("readmeHtml") shouldBe null
            verify(exactly = 0) { markdownService.renderFileInReadme(any(), any()) }
        }

        it("isMilestoneEnabled가 꺼진 프로젝트는 마일스톤 조회 없이 sidebarMilestone이 null이어야 한다") {
            val proj = Project(id = 982L, name = "NoMilestoneHomeProj", owner = "owner", projectScope = ProjectScope.PUBLIC, isMilestoneEnabled = false)
            val user2 = User(id = 982L, loginId = "nomilestoneuser", name = "마일스톤없는유저")
            val auth2 = UsernamePasswordAuthenticationToken("nomilestoneuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoMilestoneHomeProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("nomilestoneuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectId(982L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "NoMilestoneHomeProj", "readme", auth2, model)
            model.getAttribute("sidebarMilestone") shouldBe null
            verify(exactly = 0) { milestoneRepository.findByProjectAndState(proj, State.OPEN, any()) }
        }
    }

    // projectSetting 잔여 분기 — 완전 비로그인과, 로그인은 했으나 해당 프로젝트 멤버가 아닌 경우(둘 다
    // error/forbidden이지만 "loginUser == null"과 "!exists(...)"는 서로 다른 분기)를 나눠서 보강한다.
    describe("projectSetting 잔여 분기 보강") {
        it("비로그인 상태면 error/forbidden 뷰를 반환해야 한다") {
            val proj = Project(id = 990L, name = "AnonSettingProj", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "AnonSettingProj") } returns Optional.of(proj)

            mockMvc.perform(get("/owner/AnonSettingProj/setting"))
                .andExpect(view().name("error/forbidden"))
        }

        it("로그인 사용자를 찾았지만 프로젝트 멤버가 아니면 error/forbidden 뷰를 반환해야 한다") {
            val proj = Project(id = 991L, name = "NonMemberSettingProj", owner = "owner")
            val outsider = User(id = 991L, loginId = "settingoutsider", name = "설정외부인")
            val outsiderAuth = UsernamePasswordAuthenticationToken("settingoutsider", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NonMemberSettingProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("settingoutsider") } returns Optional.of(outsider)
            every { projectUserRepository.existsByProjectIdAndUserId(991L, 991L) } returns false

            mockMvc.perform(get("/owner/NonMemberSettingProj/setting").principal(outsiderAuth))
                .andExpect(view().name("error/forbidden"))
        }
    }

    // newLabel 잔여 분기 — 프로젝트 미존재(404), 완전 비로그인, 인증은 있으나 사용자 레코드 없음
    // (둘 다 403이지만 authentication?.let 안전호출의 서로 다른 분기) 보강.
    describe("newLabel 잔여 분기 보강") {
        it("프로젝트가 없으면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchNewLabelProj") } returns Optional.empty()
            val response = projectViewController.newLabel("owner", "NoSuchNewLabelProj", "n", "#fff", "c", false, null)
            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        it("완전히 비로그인 상태면 403을 반환해야 한다") {
            val proj = Project(id = 1000L, name = "AnonNewLabelProj", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "AnonNewLabelProj") } returns Optional.of(proj)
            val response = projectViewController.newLabel("owner", "AnonNewLabelProj", "n", "#fff", "c", false, null)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 403을 반환해야 한다") {
            val proj = Project(id = 1001L, name = "GhostNewLabelProj", owner = "owner")
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostnewlabeluser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GhostNewLabelProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("ghostnewlabeluser") } returns Optional.empty()
            val response = projectViewController.newLabel("owner", "GhostNewLabelProj", "n", "#fff", "c", false, ghostAuth)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // deleteLabelForm 잔여 분기 — 프로젝트 미존재(404)와 완전 비로그인(403) 보강.
    describe("deleteLabelForm 잔여 분기 보강") {
        it("프로젝트가 없으면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchDelLabelProj") } returns Optional.empty()
            val response = projectViewController.deleteLabelForm("owner", "NoSuchDelLabelProj", 1L, "delete", null)
            response.statusCode shouldBe HttpStatus.NOT_FOUND
        }

        it("로그인하지 않았으면 403을 반환해야 한다") {
            val proj = Project(id = 1010L, name = "AnonDelLabelProj", owner = "owner")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "AnonDelLabelProj") } returns Optional.of(proj)
            val response = projectViewController.deleteLabelForm("owner", "AnonDelLabelProj", 1L, "delete", null)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // getReadmeFileName 잔여 분기 — 대문자/소문자 README가 모두 없는 비-SVN 저장소, SVN 저장소에서
    // 대문자 /trunk/README.md는 없지만 소문자는 있는 경우 보강.
    describe("getReadmeFileName 잔여 분기 보강") {
        it("대문자/소문자 README가 모두 없고 SVN 저장소도 아니면 readmeFileName은 null이어야 한다") {
            val proj = Project(id = 1020L, name = "NoReadmeProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val user2 = User(id = 1020L, loginId = "noreadmeuser", name = "README없는유저")
            val auth2 = UsernamePasswordAuthenticationToken("noreadmeuser", "password")
            val playRepo = mockk<PlayRepository>()
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoReadmeProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("noreadmeuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectId(1020L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()
            every { repositoryService.getRepository(proj) } returns playRepo
            every { playRepo.isFile("README.md") } returns false
            every { playRepo.isFile("readme.md") } returns false

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "NoReadmeProj", "readme", auth2, model)
            model.getAttribute("readmeFileName") shouldBe null
        }

        it("SVN 저장소에서 대문자 /trunk/README.md는 없지만 소문자는 있으면 소문자 경로를 사용해야 한다") {
            val proj = Project(id = 1021L, name = "SvnLowerReadmeProj", owner = "owner", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
            val user2 = User(id = 1021L, loginId = "svnlowerreadmeuser", name = "SVN소문자README유저")
            val auth2 = UsernamePasswordAuthenticationToken("svnlowerreadmeuser", "password")
            val svnRepo = spyk(SvnRepository(ownerName = "owner", projectName = "SvnLowerReadmeProj", baseDir = "/tmp/yona-test-svn-base2", userResolver = { null }))
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "SvnLowerReadmeProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("svnlowerreadmeuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectId(1021L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()
            every { repositoryService.getRepository(proj) } returns svnRepo
            every { svnRepo.isFile("README.md") } returns false
            every { svnRepo.isFile("readme.md") } returns false
            every { svnRepo.isFile("/trunk/README.md") } returns false
            every { svnRepo.isFile("/trunk/readme.md") } returns true
            every { svnRepo.getRawFile("HEAD", "/trunk/readme.md") } returns "SVN 소문자 리드미".toByteArray(Charsets.UTF_8)
            every { markdownService.renderFileInReadme("SVN 소문자 리드미", proj) } returns "<p>SVN 소문자 리드미</p>"

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "SvnLowerReadmeProj", "readme", auth2, model)
            model.getAttribute("readmeFileName") shouldBe "/trunk/readme.md"
        }
    }

    // projectMembers 잔여 분기 — 프로젝트 미존재(404), 읽기 권한 없음(403), 비로그인 공개 프로젝트
    // 접근 허용(200) 보강(기존 스펙에는 멤버 성공 케이스 1건만 있었다).
    describe("projectMembers 잔여 분기 보강") {
        it("프로젝트가 없으면 error/404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchMembersProj") } returns Optional.empty()
            mockMvc.perform(get("/owner/NoSuchMembersProj/members"))
                .andExpect(view().name("error/404"))
        }

        it("읽기 권한이 없으면 error/forbidden을 반환해야 한다") {
            val proj = Project(id = 1030L, name = "PrivateMembersProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val outsider = User(id = 1030L, loginId = "membersoutsider", name = "멤버외부인")
            val outsiderAuth = UsernamePasswordAuthenticationToken("membersoutsider", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PrivateMembersProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("membersoutsider") } returns Optional.of(outsider)

            mockMvc.perform(get("/owner/PrivateMembersProj/members").principal(outsiderAuth))
                .andExpect(view().name("error/forbidden"))
        }

        it("비로그인 사용자도 공개 프로젝트의 멤버 목록은 볼 수 있어야 한다") {
            val proj = Project(id = 1031L, name = "PublicMembersProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PublicMembersProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectId(1031L) } returns emptyList()

            mockMvc.perform(get("/owner/PublicMembersProj/members"))
                .andExpect(status().isOk)
                .andExpect(view().name("project/members"))
        }
    }

    // updateLabelForm / updateCategoryForm 잔여 분기 — 인증은 있으나 프로젝트 멤버가 아니라
    // accessControl.isAllowed(...)가 false를 반환하는 경우(기존 스펙엔 이 조합이 없었다).
    describe("updateLabelForm / updateCategoryForm 잔여 분기 보강") {
        it("updateLabelForm: 프로젝트 멤버가 아니면 403을 반환해야 한다") {
            val proj = Project(id = 1040L, name = "UpdLabelPermProj", owner = "owner")
            val outsider = User(id = 1040L, loginId = "updlabeloutsider", name = "라벨수정외부인")
            val outsiderAuth = UsernamePasswordAuthenticationToken("updlabeloutsider", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "UpdLabelPermProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("updlabeloutsider") } returns Optional.of(outsider)

            val response = projectViewController.updateLabelForm("owner", "UpdLabelPermProj", 1L, "n", "c", 1L, outsiderAuth)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        it("updateCategoryForm: 프로젝트 멤버가 아니면 403을 반환해야 한다") {
            val proj = Project(id = 1041L, name = "UpdCatPermProj", owner = "owner")
            val outsider = User(id = 1041L, loginId = "updcatoutsider", name = "카테고리수정외부인")
            val outsiderAuth = UsernamePasswordAuthenticationToken("updcatoutsider", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "UpdCatPermProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("updcatoutsider") } returns Optional.of(outsider)

            val response = projectViewController.updateCategoryForm("owner", "UpdCatPermProj", 1L, "n", false, outsiderAuth)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // newProjectForm 잔여 분기 — 기존 스펙엔 GET /projectform 테스트가 전혀 없었다. 비로그인 리다이렉트와
    // isOwnerOrganization(조직명==loginId) 참/거짓 두 방향을 보강한다.
    describe("newProjectForm 잔여 분기 보강") {
        it("비로그인 상태면 로그인 폼으로 리다이렉트해야 한다") {
            mockMvc.perform(get("/projectform"))
                .andExpect(redirectedUrl("/users/loginform"))
        }

        it("사용자가 관리하는 조직 중 자신의 loginId와 같은 이름이 있으면 isOwnerOrganization이 true여야 한다") {
            val user2 = User(id = 1050L, loginId = "sameasorg", name = "동일이름유저")
            val auth2 = UsernamePasswordAuthenticationToken("sameasorg", "password")
            val org = Organization(id = 1051L, name = "sameasorg")
            every { userRepository.findByLoginId("sameasorg") } returns Optional.of(user2)
            every { organizationUserRepository.findByUserIdAndRoleId(1050L, RoleType.ORG_ADMIN.roleType) } returns
                listOf(OrganizationUser(id = 1L, user = user2, organization = org, role = Role(id = RoleType.ORG_ADMIN.roleType)))

            val model = ExtendedModelMap()
            val result = projectViewController.newProjectForm(auth2, model)
            result shouldBe "project/create"
            model.getAttribute("isOwnerOrganization") shouldBe true
        }

        it("사용자가 관리하는 조직 이름이 자신의 loginId와 다르면 isOwnerOrganization이 false여야 한다") {
            val user2 = User(id = 1052L, loginId = "diffowner", name = "다른이름유저")
            val auth2 = UsernamePasswordAuthenticationToken("diffowner", "password")
            every { userRepository.findByLoginId("diffowner") } returns Optional.of(user2)
            every { organizationUserRepository.findByUserIdAndRoleId(1052L, RoleType.ORG_ADMIN.roleType) } returns emptyList()

            val model = ExtendedModelMap()
            val result = projectViewController.newProjectForm(auth2, model)
            result shouldBe "project/create"
            model.getAttribute("isOwnerOrganization") shouldBe false
        }
    }

    // changeVCS(POST) 잔여 분기 — projectUserRepository.findByProjectIdAndUserId()가 Optional.empty()가
    // 아니라 실제 MEMBER 역할의 ProjectUser를 반환할 때 .map{ role==MANAGER }가 false로 평가되는 경로
    // (기존 "MANAGER 권한이 없으면" 테스트는 Optional.empty()라 이 map 람다 자체가 실행되지 않았다).
    describe("changeVCS(POST) 잔여 분기 보강") {
        it("MANAGER가 아닌 일반 멤버 권한으로 요청하면 403을 반환해야 한다") {
            val proj = Project(id = 1060L, name = "MemberVCSPostProj", owner = "owner")
            val member = User(id = 1060L, loginId = "vcspostmember", name = "VCS포스트멤버")
            val memberAuth = UsernamePasswordAuthenticationToken("vcspostmember", "password")
            every { userRepository.findByLoginId("vcspostmember") } returns Optional.of(member)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MemberVCSPostProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectIdAndUserId(1060L, 1060L) } returns
                Optional.of(ProjectUser(id = 10600L, user = member, project = proj, role = Role(id = RoleType.MEMBER.roleType)))

            val response = projectViewController.changeVCS("owner", "MemberVCSPostProj", memberAuth)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // ============================================================================================
    // TASK-분기커버리지 95% 최종 보강 — JaCoCo HTML 리포트(build/reports/jacoco/test/html)를 줄 단위로
    // 대조해 아직 어떤 테스트도 거치지 않은 조합만 추가한다. "인증은 있으나 로그인 사용자 레코드를
    // DB에서 찾을 수 없는" 경우(authentication?.let{...}의 두 번째 null-체크)와 "역할(Role) id가
    // null인" 경우(.map{ it.role.id == MANAGER }의 첫 번째 null-체크)가 대부분의 컨트롤러 메서드에서
    // 공통으로 남아있던 미실행 분기다.
    // ============================================================================================

    // projectHome readmeHtml 잔여 분기 — 게시판 README 글(readme=true)의 본문이 null인 경우.
    describe("projectHome readmeHtml 최종 분기 보강") {
        it("게시판 README 글의 body가 null이면 빈 문자열로 렌더링해야 한다") {
            val proj = Project(id = 2000L, name = "NullBodyReadmeProj", owner = "owner", projectScope = ProjectScope.PUBLIC, isCodeEnabled = false)
            val user2 = User(id = 2000L, loginId = "nullbodyuser", name = "본문없는유저")
            val auth2 = UsernamePasswordAuthenticationToken("nullbodyuser", "password")
            val readmePostingNullBody = Posting(id = 2001L, title = "README", body = null, project = proj, number = 1L, readme = true)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NullBodyReadmeProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("nullbodyuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectId(2000L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()
            val playRepo = mockk<PlayRepository>()
            every { repositoryService.getRepository(proj) } returns playRepo
            every { playRepo.isFile("README.md") } returns true
            every { postingRepository.findByProjectAndReadme(proj, true) } returns listOf(readmePostingNullBody)
            every { markdownService.render("", true, proj) } returns "<p></p>"

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "NullBodyReadmeProj", "readme", auth2, model)
            model.getAttribute("readmeHtml") shouldBe "<p></p>"
            verify(exactly = 1) { markdownService.render("", true, proj) }
        }
    }

    // projectSetting 최종 분기 — 프로젝트 미존재(404)와 findByProjectIdAndUserId가 role.id==null인
    // ProjectUser를 반환하는 경우(.map{ it.role.id == MANAGER } 람다의 null-체크 분기).
    describe("projectSetting 최종 분기 보강") {
        it("프로젝트가 없으면 error/404 뷰를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchSettingProj") } returns Optional.empty()
            mockMvc.perform(get("/owner/NoSuchSettingProj/setting"))
                .andExpect(view().name("error/404"))
        }

        it("멤버의 role.id가 null이면 MANAGER로 취급하지 않고 error/forbidden을 반환해야 한다") {
            val proj = Project(id = 2010L, name = "NullRoleSettingProj", owner = "owner")
            val user2 = User(id = 2010L, loginId = "nullrolesettinguser", name = "역할없는유저")
            val auth2 = UsernamePasswordAuthenticationToken("nullrolesettinguser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NullRoleSettingProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("nullrolesettinguser") } returns Optional.of(user2)
            every { projectUserRepository.existsByProjectIdAndUserId(2010L, 2010L) } returns true
            every { projectUserRepository.findByProjectIdAndUserId(2010L, 2010L) } returns
                Optional.of(ProjectUser(id = 20100L, user = user2, project = proj, role = Role(id = null)))

            mockMvc.perform(get("/owner/NullRoleSettingProj/setting").principal(auth2))
                .andExpect(view().name("error/forbidden"))
        }
    }

    // projectChangeVCSForm(GET) 최종 분기 — role.id==null인 경우.
    describe("projectChangeVCSForm(GET) 최종 분기 보강") {
        it("멤버의 role.id가 null이면 MANAGER로 취급하지 않고 error/forbidden을 반환해야 한다") {
            val proj = Project(id = 2020L, name = "NullRoleVCSFormProj", owner = "owner")
            val user2 = User(id = 2020L, loginId = "nullrolevcsformuser", name = "역할없는유저2")
            val auth2 = UsernamePasswordAuthenticationToken("nullrolevcsformuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NullRoleVCSFormProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("nullrolevcsformuser") } returns Optional.of(user2)
            every { projectUserRepository.existsByProjectIdAndUserId(2020L, 2020L) } returns true
            every { projectUserRepository.findByProjectIdAndUserId(2020L, 2020L) } returns
                Optional.of(ProjectUser(id = 20200L, user = user2, project = proj, role = Role(id = null)))

            mockMvc.perform(get("/owner/NullRoleVCSFormProj/changeVCS").principal(auth2))
                .andExpect(view().name("error/forbidden"))
        }
    }

    // changeVCS(POST) 최종 분기 — 인증은 있으나 사용자 레코드가 없는 경우(401)와 role.id==null인 경우(403).
    describe("changeVCS(POST) 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 401을 반환해야 한다") {
            val proj = Project(id = 2030L, name = "GhostVCSPostProj", owner = "owner")
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostvcspostuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GhostVCSPostProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("ghostvcspostuser") } returns Optional.empty()

            val response = projectViewController.changeVCS("owner", "GhostVCSPostProj", ghostAuth)
            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        it("멤버의 role.id가 null이면 MANAGER로 취급하지 않고 403을 반환해야 한다") {
            val proj = Project(id = 2031L, name = "NullRoleVCSPostProj", owner = "owner")
            val user2 = User(id = 2031L, loginId = "nullrolevcspostuser", name = "역할없는유저3")
            val auth2 = UsernamePasswordAuthenticationToken("nullrolevcspostuser", "password")
            every { userRepository.findByLoginId("nullrolevcspostuser") } returns Optional.of(user2)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NullRoleVCSPostProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectIdAndUserId(2031L, 2031L) } returns
                Optional.of(ProjectUser(id = 20310L, user = user2, project = proj, role = Role(id = null)))

            val response = projectViewController.changeVCS("owner", "NullRoleVCSPostProj", auth2)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // downloadCode 최종 분기 — 프로젝트 미존재(404).
    describe("downloadCode 최종 분기 보강") {
        it("프로젝트가 없으면 404를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoSuchDownloadProj") } returns Optional.empty()
            mockMvc.perform(get("/owner/NoSuchDownloadProj/code/main/download"))
                .andExpect(status().isNotFound)
        }
    }

    // newProjectForm 최종 분기 — 인증은 있으나 사용자 레코드가 없는 경우.
    describe("newProjectForm 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 로그인 폼으로 리다이렉트해야 한다") {
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostnewprojectformuser", "password")
            every { userRepository.findByLoginId("ghostnewprojectformuser") } returns Optional.empty()

            mockMvc.perform(get("/projectform").principal(ghostAuth))
                .andExpect(redirectedUrl("/users/loginform"))
        }
    }

    // newProject 최종 분기 — 인증은 있으나 사용자 레코드가 없는 경우.
    describe("newProject 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 로그인 폼으로 리다이렉트해야 한다") {
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostnewprojectuser", "password")
            every { userRepository.findByLoginId("ghostnewprojectuser") } returns Optional.empty()

            mockMvc.perform(
                MockMvcRequestBuilders.post("/projectform")
                    .principal(ghostAuth)
                    .param("owner", "ghostnewprojectuser")
                    .param("name", "ghostproj")
                    .param("overview", "설명")
                    .param("projectScope", "PUBLIC")
                    .param("vcs", "GIT")
            ).andExpect(redirectedUrl("/users/loginform"))
        }
    }

    // projectsJson 최종 분기 — 인증은 있으나 사용자 레코드가 없는 경우.
    describe("projectsJson 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 401을 반환해야 한다") {
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostprojectsjsonuser", "password")
            every { userRepository.findByLoginId("ghostprojectsjsonuser") } returns Optional.empty()

            val response = projectViewController.projectsJson("", "", ghostAuth)
            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }
    }

    // projectLogo 최종 분기 — 첨부파일의 mimeType이 null인 경우 image/png로 대체.
    // (defaultImage.exists() 하드코딩 절대경로 분기는 TASK-0270에서 ClassPathResource로 이미 수정되고
    // "GET /projects/{projectId}/logo" 스펙에서 커버됐다 — 여기서는 중복 작성하지 않는다.)
    describe("projectLogo 최종 분기 보강") {
        it("첨부파일의 mimeType이 null이면 image/png로 대체해야 한다") {
            val tempFile = File.createTempFile("nullmimelogo", ".png")
            tempFile.deleteOnExit()
            val attachment = Attachment(id = 2040L, name = "logo.png", hash = "hash2040", containerType = ResourceType.PROJECT, containerId = "2040", mimeType = null)
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.PROJECT, "2040") } returns listOf(attachment)
            every { attachmentService.getFile(attachment) } returns tempFile

            val response = projectViewController.projectLogo(2040L)
            response.statusCode shouldBe HttpStatus.OK
            response.headers.contentType shouldBe MediaType.IMAGE_PNG
        }
    }

    // transferForm 최종 분기 — 인증은 있으나 사용자 레코드가 없는 경우, role.id==null인 경우,
    // 실제로 조회된(Optional.empty()가 아닌) MEMBER 역할이 MANAGER가 아니라고 판정되는 경우.
    // (기존 "MANAGER 권한이 없으면" 테스트는 Optional.empty()라 .map{} 람다 자체가 실행되지 않았다.)
    describe("transferForm 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 로그인 폼으로 리다이렉트해야 한다") {
            val proj = Project(id = 2050L, name = "GhostTransferFormProj", owner = "owner")
            val ghostAuth = UsernamePasswordAuthenticationToken("ghosttransferformuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GhostTransferFormProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("ghosttransferformuser") } returns Optional.empty()

            mockMvc.perform(get("/owner/GhostTransferFormProj/transfer").principal(ghostAuth))
                .andExpect(redirectedUrl("/users/loginform"))
        }

        it("멤버의 role.id가 null이면 MANAGER로 취급하지 않고 error/forbidden을 반환해야 한다") {
            val proj = Project(id = 2051L, name = "NullRoleTransferFormProj", owner = "owner")
            val user2 = User(id = 2051L, loginId = "nullroletransferformuser", name = "역할없는유저4")
            val auth2 = UsernamePasswordAuthenticationToken("nullroletransferformuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NullRoleTransferFormProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("nullroletransferformuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectIdAndUserId(2051L, 2051L) } returns
                Optional.of(ProjectUser(id = 20510L, user = user2, project = proj, role = Role(id = null)))

            mockMvc.perform(get("/owner/NullRoleTransferFormProj/transfer").principal(auth2))
                .andExpect(view().name("error/forbidden"))
        }

        it("실제로 조회된 MEMBER 역할이면(Optional.empty()가 아님) MANAGER가 아니므로 error/forbidden을 반환해야 한다") {
            val proj = Project(id = 2052L, name = "MemberTransferFormProj", owner = "owner")
            val user2 = User(id = 2052L, loginId = "membertransferformuser", name = "멤버유저")
            val auth2 = UsernamePasswordAuthenticationToken("membertransferformuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MemberTransferFormProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("membertransferformuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectIdAndUserId(2052L, 2052L) } returns
                Optional.of(ProjectUser(id = 20520L, user = user2, project = proj, role = Role(id = RoleType.MEMBER.roleType)))

            mockMvc.perform(get("/owner/MemberTransferFormProj/transfer").principal(auth2))
                .andExpect(view().name("error/forbidden"))
        }
    }

    // transferProject 최종 분기 — transferForm과 동일한 3가지 조합.
    describe("transferProject 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 401을 반환해야 한다") {
            val ghostAuth = UsernamePasswordAuthenticationToken("ghosttransferprojectuser", "password")
            every { userRepository.findByLoginId("ghosttransferprojectuser") } returns Optional.empty()

            val response = projectViewController.transferProject("owner", "AnyProj", "dest", mockk(relaxed = true), ghostAuth)
            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        it("멤버의 role.id가 null이면 MANAGER로 취급하지 않고 403을 반환해야 한다") {
            val proj = Project(id = 2060L, name = "NullRoleTransferProjectProj", owner = "owner")
            val user2 = User(id = 2060L, loginId = "nullroletransferprojectuser", name = "역할없는유저5")
            val auth2 = UsernamePasswordAuthenticationToken("nullroletransferprojectuser", "password")
            every { userRepository.findByLoginId("nullroletransferprojectuser") } returns Optional.of(user2)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NullRoleTransferProjectProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectIdAndUserId(2060L, 2060L) } returns
                Optional.of(ProjectUser(id = 20600L, user = user2, project = proj, role = Role(id = null)))

            val response = projectViewController.transferProject("owner", "NullRoleTransferProjectProj", "dest", mockk(relaxed = true), auth2)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        it("실제로 조회된 MEMBER 역할이면(Optional.empty()가 아님) MANAGER가 아니므로 403을 반환해야 한다") {
            val proj = Project(id = 2061L, name = "MemberTransferProjectProj", owner = "owner")
            val user2 = User(id = 2061L, loginId = "membertransferprojectuser", name = "멤버유저2")
            val auth2 = UsernamePasswordAuthenticationToken("membertransferprojectuser", "password")
            every { userRepository.findByLoginId("membertransferprojectuser") } returns Optional.of(user2)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MemberTransferProjectProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectIdAndUserId(2061L, 2061L) } returns
                Optional.of(ProjectUser(id = 20610L, user = user2, project = proj, role = Role(id = RoleType.MEMBER.roleType)))

            val response = projectViewController.transferProject("owner", "MemberTransferProjectProj", "dest", mockk(relaxed = true), auth2)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // sendTransferRequestMail 최종 분기 — project.owner가 null인 경우(메시지 조립 시 "" 대체),
    // 조직 관리자 중 role.id==null인 사람은 관리자로 취급되지 않는 경우.
    // (toUser/조직관리자의 email이 null인 분기는 User.email이 Kotlin에서 비-nullable String이라
    // 생성자로 null을 넣을 수 없어 테스트 불가 — 최종 보고에 근거를 남긴다.)
    describe("sendTransferRequestMail 최종 분기 보강") {
        val proj = Project(id = 2070L, name = "OwnerAbsentMailProj", owner = null, projectScope = ProjectScope.PUBLIC)
        val manager = User(id = 2070L, loginId = "absentownermanager", name = "오너없는매니저")
        val managerAuth = UsernamePasswordAuthenticationToken("absentownermanager", "password")
        val managerProjectUser = ProjectUser(id = 20700L, user = manager, project = proj, role = Role(id = RoleType.MANAGER.roleType))

        fun finalMockRequest(): HttpServletRequest {
            val request = mockk<HttpServletRequest>()
            every { request.scheme } returns "https"
            every { request.serverName } returns "yona.io"
            every { request.serverPort } returns 443
            return request
        }

        it("project.owner가 null이면 메시지 조립 시 빈 문자열로 대체하고 정상 처리돼야 한다") {
            every { userRepository.findByLoginId("absentownermanager") } returns Optional.of(manager)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "OwnerAbsentMailProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectIdAndUserId(2070L, 2070L) } returns Optional.of(managerProjectUser)
            val destUser = User(id = 2071L, loginId = "absentownerdest", name = "대상", email = "absentownerdest@yona.io")
            every { userRepository.findByLoginId("absentownerdest") } returns Optional.of(destUser)
            every { organizationRepository.findByName("absentownerdest") } returns Optional.empty()
            val pt = ProjectTransfer(id = 2072L, sender = manager, destination = "absentownerdest", project = proj, confirmKey = "keyabsentowner", newProjectName = "OwnerAbsentMailProj")
            every { projectService.requestNewTransfer(2070L, 2070L, "absentownerdest") } returns pt
            every { messageSource.getMessage(any(), any(), any()) } returns "메시지"
            val markdownSlot = slot<String>()
            every { markdownService.render(capture(markdownSlot), true, proj) } returns "<p>html</p>"
            every { mailService.sendHtmlMail("absentownerdest@yona.io", "Yona", any(), "<p>html</p>") } just Runs

            val response = projectViewController.transferProject("owner", "OwnerAbsentMailProj", "absentownerdest", finalMockRequest(), managerAuth)
            response.statusCode shouldBe HttpStatus.NO_CONTENT
            markdownSlot.captured.contains("null") shouldBe false
        }

        it("조직 관리자 중 role.id가 null인 사람은 관리자로 취급하지 않고 메일을 보내지 않아야 한다") {
            val proj2 = Project(id = 2073L, name = "NullAdminRoleProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val manager2 = User(id = 2073L, loginId = "nulladminrolemanager", name = "널관리자매니저")
            val managerAuth2 = UsernamePasswordAuthenticationToken("nulladminrolemanager", "password")
            val managerProjectUser2 = ProjectUser(id = 20730L, user = manager2, project = proj2, role = Role(id = RoleType.MANAGER.roleType))
            every { userRepository.findByLoginId("nulladminrolemanager") } returns Optional.of(manager2)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NullAdminRoleProj") } returns Optional.of(proj2)
            every { projectUserRepository.findByProjectIdAndUserId(2073L, 2073L) } returns Optional.of(managerProjectUser2)
            every { userRepository.findByLoginId("nullroleadminorg") } returns Optional.empty()
            val destOrg = Organization(id = 2074L, name = "nullroleadminorg")
            every { organizationRepository.findByName("nullroleadminorg") } returns Optional.of(destOrg)
            val nullRoleUser = User(id = 2075L, loginId = "nullroleorguser", name = "역할없는조직원", email = "nullroleorguser@yona.io")
            val realAdmin = User(id = 2076L, loginId = "realorgadmin", name = "진짜관리자", email = "realorgadmin@yona.io")
            every { organizationUserRepository.findByOrganizationId(2074L) } returns listOf(
                OrganizationUser(id = 1L, user = nullRoleUser, organization = destOrg, role = Role(id = null)),
                OrganizationUser(id = 2L, user = realAdmin, organization = destOrg, role = Role(id = RoleType.ORG_ADMIN.roleType))
            )
            val pt = ProjectTransfer(id = 2077L, sender = manager2, destination = "nullroleadminorg", project = proj2, confirmKey = "keynullrole", newProjectName = "NullAdminRoleProj")
            every { projectService.requestNewTransfer(2073L, 2073L, "nullroleadminorg") } returns pt
            every { messageSource.getMessage(any(), any(), any()) } returns "메시지"
            every { markdownService.render(any(), true, proj2) } returns "<p>html</p>"
            every { mailService.sendHtmlMail("realorgadmin@yona.io", "Yona", any(), "<p>html</p>") } just Runs

            val response = projectViewController.transferProject("owner", "NullAdminRoleProj", "nullroleadminorg", finalMockRequest(), managerAuth2)
            response.statusCode shouldBe HttpStatus.NO_CONTENT
            verify(exactly = 1) { mailService.sendHtmlMail("realorgadmin@yona.io", "Yona", any(), "<p>html</p>") }
            verify(exactly = 0) { mailService.sendHtmlMail("nullroleorguser@yona.io", "Yona", any(), "<p>html</p>") }
        }
    }

    // acceptTransfer 최종 분기 — 인증은 있으나 사용자 레코드가 없는 경우.
    describe("acceptTransfer 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 로그인 폼으로 리다이렉트해야 한다") {
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostaccepttransferuser", "password")
            every { userRepository.findByLoginId("ghostaccepttransferuser") } returns Optional.empty()

            val result = projectViewController.acceptTransfer(1L, "key", ghostAuth, ExtendedModelMap())
            result shouldBe "redirect:/users/loginform"
        }
    }

    // deleteForm 최종 분기 — transferForm과 동일한 3가지 조합.
    describe("deleteForm 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 로그인 폼으로 리다이렉트해야 한다") {
            val proj = Project(id = 2080L, name = "GhostDeleteFormProj", owner = "owner")
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostdeleteformuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GhostDeleteFormProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("ghostdeleteformuser") } returns Optional.empty()

            val result = projectViewController.deleteForm("owner", "GhostDeleteFormProj", ghostAuth, ExtendedModelMap())
            result shouldBe "redirect:/users/loginform"
        }

        it("멤버의 role.id가 null이면 MANAGER로 취급하지 않고 error/forbidden을 반환해야 한다") {
            val proj = Project(id = 2081L, name = "NullRoleDeleteFormProj", owner = "owner")
            val user2 = User(id = 2081L, loginId = "nullroledeleteformuser", name = "역할없는유저6")
            val auth2 = UsernamePasswordAuthenticationToken("nullroledeleteformuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NullRoleDeleteFormProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("nullroledeleteformuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectIdAndUserId(2081L, 2081L) } returns
                Optional.of(ProjectUser(id = 20810L, user = user2, project = proj, role = Role(id = null)))

            val result = projectViewController.deleteForm("owner", "NullRoleDeleteFormProj", auth2, ExtendedModelMap())
            result shouldBe "error/forbidden"
        }

        it("실제로 조회된 MEMBER 역할이면(Optional.empty()가 아님) MANAGER가 아니므로 error/forbidden을 반환해야 한다") {
            val proj = Project(id = 2082L, name = "MemberDeleteFormProj", owner = "owner")
            val user2 = User(id = 2082L, loginId = "memberdeleteformuser", name = "멤버유저3")
            val auth2 = UsernamePasswordAuthenticationToken("memberdeleteformuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MemberDeleteFormProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("memberdeleteformuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectIdAndUserId(2082L, 2082L) } returns
                Optional.of(ProjectUser(id = 20820L, user = user2, project = proj, role = Role(id = RoleType.MEMBER.roleType)))

            val result = projectViewController.deleteForm("owner", "MemberDeleteFormProj", auth2, ExtendedModelMap())
            result shouldBe "error/forbidden"
        }
    }

    // deleteProject 최종 분기 — transferProject와 동일한 3가지 조합.
    describe("deleteProject 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 401을 반환해야 한다") {
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostdeleteprojectuser", "password")
            every { userRepository.findByLoginId("ghostdeleteprojectuser") } returns Optional.empty()

            val response = projectViewController.deleteProject("owner", "AnyProj", ghostAuth)
            response.statusCode shouldBe HttpStatus.UNAUTHORIZED
        }

        it("멤버의 role.id가 null이면 MANAGER로 취급하지 않고 403을 반환해야 한다") {
            val proj = Project(id = 2090L, name = "NullRoleDeleteProjectProj", owner = "owner")
            val user2 = User(id = 2090L, loginId = "nullroledeleteprojectuser", name = "역할없는유저7")
            val auth2 = UsernamePasswordAuthenticationToken("nullroledeleteprojectuser", "password")
            every { userRepository.findByLoginId("nullroledeleteprojectuser") } returns Optional.of(user2)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NullRoleDeleteProjectProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectIdAndUserId(2090L, 2090L) } returns
                Optional.of(ProjectUser(id = 20900L, user = user2, project = proj, role = Role(id = null)))

            val response = projectViewController.deleteProject("owner", "NullRoleDeleteProjectProj", auth2)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        it("실제로 조회된 MEMBER 역할이면(Optional.empty()가 아님) MANAGER가 아니므로 403을 반환해야 한다") {
            val proj = Project(id = 2091L, name = "MemberDeleteProjectProj", owner = "owner")
            val user2 = User(id = 2091L, loginId = "memberdeleteprojectuser", name = "멤버유저4")
            val auth2 = UsernamePasswordAuthenticationToken("memberdeleteprojectuser", "password")
            every { userRepository.findByLoginId("memberdeleteprojectuser") } returns Optional.of(user2)
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MemberDeleteProjectProj") } returns Optional.of(proj)
            every { projectUserRepository.findByProjectIdAndUserId(2091L, 2091L) } returns
                Optional.of(ProjectUser(id = 20910L, user = user2, project = proj, role = Role(id = RoleType.MEMBER.roleType)))

            val response = projectViewController.deleteProject("owner", "MemberDeleteProjectProj", auth2)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // labelsForm 최종 분기 — transferForm과 동일한 3가지 조합(사이트매니저 우회 분기는 기존 스펙에 있음).
    describe("labelsForm 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 로그인 폼으로 리다이렉트해야 한다") {
            val proj = Project(id = 2100L, name = "GhostLabelsFormProj", owner = "owner")
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostlabelsformuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GhostLabelsFormProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("ghostlabelsformuser") } returns Optional.empty()

            mockMvc.perform(get("/owner/GhostLabelsFormProj/issue/labelsform").principal(ghostAuth))
                .andExpect(redirectedUrl("/users/loginform"))
        }

        it("멤버의 role.id가 null이면 MANAGER로 취급하지 않고 error/forbidden을 반환해야 한다") {
            val proj = Project(id = 2101L, name = "NullRoleLabelsFormProj", owner = "owner")
            val user2 = User(id = 2101L, loginId = "nullrolelabelsformuser", name = "역할없는유저8")
            val auth2 = UsernamePasswordAuthenticationToken("nullrolelabelsformuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NullRoleLabelsFormProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("nullrolelabelsformuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectIdAndUserId(2101L, 2101L) } returns
                Optional.of(ProjectUser(id = 21010L, user = user2, project = proj, role = Role(id = null)))

            mockMvc.perform(get("/owner/NullRoleLabelsFormProj/issue/labelsform").principal(auth2))
                .andExpect(view().name("error/forbidden"))
        }

        it("실제로 조회된 MEMBER 역할이면(Optional.empty()가 아님) MANAGER가 아니므로 error/forbidden을 반환해야 한다") {
            val proj = Project(id = 2102L, name = "MemberLabelsFormProj", owner = "owner")
            val user2 = User(id = 2102L, loginId = "memberlabelsformuser", name = "멤버유저5")
            val auth2 = UsernamePasswordAuthenticationToken("memberlabelsformuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "MemberLabelsFormProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("memberlabelsformuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectIdAndUserId(2102L, 2102L) } returns
                Optional.of(ProjectUser(id = 21020L, user = user2, project = proj, role = Role(id = RoleType.MEMBER.roleType)))

            mockMvc.perform(get("/owner/MemberLabelsFormProj/issue/labelsform").principal(auth2))
                .andExpect(view().name("error/forbidden"))
        }
    }

    // deleteLabelForm 최종 분기 — 인증은 있으나 사용자 레코드가 없는 경우.
    describe("deleteLabelForm 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 403을 반환해야 한다") {
            val proj = Project(id = 2110L, name = "GhostDelLabelProj2", owner = "owner")
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostdellabeluser2", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GhostDelLabelProj2") } returns Optional.of(proj)
            every { userRepository.findByLoginId("ghostdellabeluser2") } returns Optional.empty()

            val response = projectViewController.deleteLabelForm("owner", "GhostDelLabelProj2", 1L, "delete", ghostAuth)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // updateLabelForm 최종 분기 — 인증은 있으나 사용자 레코드가 없는 경우.
    describe("updateLabelForm 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 403을 반환해야 한다") {
            val proj = Project(id = 2120L, name = "GhostUpdLabelProj", owner = "owner")
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostupdlabeluser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GhostUpdLabelProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("ghostupdlabeluser") } returns Optional.empty()

            val response = projectViewController.updateLabelForm("owner", "GhostUpdLabelProj", 1L, "n", "c", 1L, ghostAuth)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // updateCategoryForm 최종 분기 — 인증은 있으나 사용자 레코드가 없는 경우.
    describe("updateCategoryForm 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 403을 반환해야 한다") {
            val proj = Project(id = 2130L, name = "GhostUpdCatProj", owner = "owner")
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostupdcatuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GhostUpdCatProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("ghostupdcatuser") } returns Optional.empty()

            val response = projectViewController.updateCategoryForm("owner", "GhostUpdCatProj", 1L, "n", false, ghostAuth)
            response.statusCode shouldBe HttpStatus.FORBIDDEN
        }
    }

    // fork(POST) 최종 분기 — 인증은 있으나 사용자 레코드가 없는 경우.
    describe("fork(POST) 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 로그인 폼으로 리다이렉트해야 한다") {
            val original = Project(id = 2140L, name = "GhostForkPostOrigin", owner = "owner")
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostforkpostuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GhostForkPostOrigin") } returns Optional.of(original)
            every { userRepository.findByLoginId("ghostforkpostuser") } returns Optional.empty()

            val result = projectViewController.fork("owner", "GhostForkPostOrigin", "dest", "name", "PUBLIC", ghostAuth, ExtendedModelMap())
            result shouldBe "redirect:/users/loginform"
        }
    }

    // doClone 최종 분기 — 인증은 있으나 사용자 레코드가 없는 경우.
    describe("doClone 최종 분기 보강") {
        it("인증 정보는 있지만 사용자 레코드를 찾을 수 없으면 status=failed, url=/users/loginform 을 반환해야 한다") {
            val original = Project(id = 2150L, name = "GhostCloneOrigin", owner = "owner")
            val ghostAuth = UsernamePasswordAuthenticationToken("ghostcloneuser", "password")
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GhostCloneOrigin") } returns Optional.of(original)
            every { userRepository.findByLoginId("ghostcloneuser") } returns Optional.empty()

            val response = projectViewController.doClone("owner", "GhostCloneOrigin", "dest", "name", ghostAuth)
            response.body?.get("status") shouldBe "failed"
            response.body?.get("url") shouldBe "/users/loginform"
        }
    }

    // getReadmeFileName 최종 분기 — SVN 저장소에서 대문자/소문자 /trunk/README.md가 모두 없는 경우
    // (isFile(svnPath.lowercase())의 false 분기).
    describe("getReadmeFileName 최종 분기 보강") {
        it("SVN 저장소에서 대문자/소문자 /trunk/README.md가 모두 없으면 readmeFileName은 null이어야 한다") {
            val proj = Project(id = 2160L, name = "SvnNoReadmeProj", owner = "owner", projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
            val user2 = User(id = 2160L, loginId = "svnnoreadmeuser", name = "SVN README없는유저")
            val auth2 = UsernamePasswordAuthenticationToken("svnnoreadmeuser", "password")
            val svnRepo = spyk(SvnRepository(ownerName = "owner", projectName = "SvnNoReadmeProj", baseDir = "/tmp/yona-test-svn-base3", userResolver = { null }))
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "SvnNoReadmeProj") } returns Optional.of(proj)
            every { userRepository.findByLoginId("svnnoreadmeuser") } returns Optional.of(user2)
            every { projectUserRepository.findByProjectId(2160L) } returns emptyList()
            every { watchService.isWatching(any(), any(), any()) } returns false
            every { watchService.findWatchers(any(), any()) } returns emptySet()
            every { repositoryService.getRepository(proj) } returns svnRepo
            every { svnRepo.isFile("README.md") } returns false
            every { svnRepo.isFile("readme.md") } returns false
            every { svnRepo.isFile("/trunk/README.md") } returns false
            every { svnRepo.isFile("/trunk/readme.md") } returns false

            val model = ExtendedModelMap()
            projectViewController.projectHome("owner", "SvnNoReadmeProj", "readme", auth2, model)
            model.getAttribute("readmeFileName") shouldBe null
        }
    }
})
