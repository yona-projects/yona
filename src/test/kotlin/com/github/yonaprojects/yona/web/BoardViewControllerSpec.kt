package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.board.PostingService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.attachment.Attachment
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.ui.ExtendedModelMap
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import tools.jackson.databind.ObjectMapper
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import io.mockk.clearMocks
import io.mockk.slot
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.data.domain.Sort
import com.github.yonaprojects.yona.domain.vcs.PlayRepository
import com.github.yonaprojects.yona.domain.board.PostingComment

class BoardViewControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val postingService = mockk<PostingService>()
    val postingRepository = mockk<PostingRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val watchService = mockk<WatchService>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val repositoryService = mockk<RepositoryService>()
    val objectMapper = ObjectMapper()
    val recentIssueService = mockk<RecentIssueService>(relaxed = true)
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
    val boardViewController = BoardViewController(
        projectRepository,
        postingService,
        postingRepository,
        projectUserRepository,
        userRepository,
        postingCommentRepository,
        watchService,
        attachmentRepository,
        objectMapper,
        repositoryService,
        "/tmp/yona/git",
        recentIssueService,
        accessControl,
        attachmentService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(boardViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(projectRepository, postingService, postingRepository, projectUserRepository, userRepository,
            postingCommentRepository, watchService, attachmentRepository)
        every { projectUserRepository.findByProjectIdAndUserId(any(), any()) } returns Optional.empty()
        every { postingRepository.findByProjectAndNotice(any(), any(), any<Pageable>()) } returns PageImpl(emptyList())
    }

    describe("BoardViewController 템플릿 연동 테스트") {
        val project = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        // isAllowed(user, project, Operation.READ)는 엔티티 관계(user.isMemberOf) 기반이라, 이 describe
        // 블록에서 공유되는 `user`를 직접 멤버로 바꾸면 아래 "비멤버 403" 테스트가 깨진다 — 필요한 개별
        // 테스트에서만 별도의 memberUser를 만들어 쓴다.
        val posting = Posting(id = 5L, title = "게시물 제목", project = project, number = 1L)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val pageRequest = PageRequest.of(0, 20)

        describe("GET /{owner}/{projectName}/posts") {
            it("비공개 프로젝트일 때 멤버라면 200 OK와 board/list 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingRepository.findByProject(project, any<Pageable>()) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/posts").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/list"))
                    .andExpect(model().attributeExists("project", "postingPage", "notices"))
            }

            it("프로젝트 멤버가 아닐 경우 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                // yona BoardApp.posts() @IsAllowed(READ, PROJECT) -> IsAllowedAction forbidden 분기
                // ErrorViews.Forbidden.render("error.forbidden", project) 대응 (P-템플릿 #47).
                mockMvc.perform(get("/owner/TestProj/posts").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
                    .andExpect(model().attributeExists("project"))
            }

            // yona AbstractPostingApp.java:35 ITEMS_PER_PAGE 대응 (P1-105) — 게시글 목록은 항상 고정 15.
            it("페이지 크기는 항상 15로 고정되어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 900L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val pageableSlot = slot<Pageable>()
                every { postingRepository.findByProjectAndNotice(project, false, capture(pageableSlot)) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/posts").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageSize shouldBe 15
            }

            // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-57) — 직접 멤버가 아니어도
            // PROTECTED 프로젝트가 속한 조직의 멤버라면 읽을 수 있어야 한다.
            it("직접 멤버가 아니어도 프로젝트가 속한 조직의 멤버라면 200 OK를 반환해야 한다") {
                val org = Organization(id = 1L, name = "org")
                val groupProject = Project(id = 1L, name = "TestProj", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = org)
                org.organizationUsers.add(
                    OrganizationUser(
                        id = 1L, user = user, organization = org,
                        role = Role(id = RoleType.ORG_MEMBER.roleType)
                    )
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false
                every { postingRepository.findByProject(groupProject, any<Pageable>()) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/posts").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/list"))
            }

            it("labelIds 파라미터가 있으면 라벨 필터 쿼리를 사용해야 한다 (P1-19)") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 901L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every {
                    postingRepository.findByProjectAndLabelIdsIn(project, listOf(3L, 4L), null, any<Pageable>())
                } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(
                    get("/owner/TestProj/posts")
                        .param("labelIds", "3", "4")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/list"))
                    .andExpect(model().attribute("labelIds", listOf(3L, 4L)))

                verify(exactly = 0) { postingRepository.findByProject(any(), any<Pageable>()) }
            }
        }

        describe("GET /{owner}/{projectName}/post/{number}") {
            it("멤버라면 200 OK와 board/view 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 902L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.getPosting(1L, 1L) } returns posting
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "5") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/post/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/view"))
                    .andExpect(model().attributeExists("project", "post"))
            }
        }

        describe("GET /{owner}/{projectName}/post/new") {
            it("멤버라면 200 OK와 board/create 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 950L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/TestProj/post/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/create"))
                    .andExpect(model().attributeExists("project"))
            }

            // yona board/create.scala.html:100-106 대응(#145 재검토, TASK-0263) — readme 체크박스는
            // Git 프로젝트+커밋 생성 권한+?readme= 쿼리가 전부 갖춰졌을 때만 canReadmefy=true여야 한다.
            it("Git 프로젝트 멤버가 ?readme=true로 접근하면 canReadmefy가 true여야 한다(#145)") {
                val gitProject = Project(id = 2L, name = "GitProj", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 951L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GitProj") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(2L, 10L) } returns true

                mockMvc.perform(get("/owner/GitProj/post/new").param("readme", "true").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("canReadmefy", true))
            }

            it("readme 쿼리 없이 접근하면 canReadmefy가 false여야 한다(#145)") {
                val gitProject = Project(id = 3L, name = "GitProj2", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 952L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GitProj2") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(3L, 10L) } returns true

                mockMvc.perform(get("/owner/GitProj2/post/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("canReadmefy", false))
            }
        }

        describe("POST /{owner}/{projectName}/post/{number}/edit (P1-44)") {
            it("sendNotificationMail 옵션을 postingService.updatePosting에 그대로 전달해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.updatePosting(1L, 1L, "수정 제목", "수정 본문", false, false, 10L, true) } returns posting

                mockMvc.perform(
                    post("/owner/TestProj/post/1/edit")
                        .param("title", "수정 제목")
                        .param("body", "수정 본문")
                        .param("sendNotificationMail", "true")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)

                verify(exactly = 1) { postingService.updatePosting(1L, 1L, "수정 제목", "수정 본문", false, false, 10L, true) }
            }

            it("sendNotificationMail을 선택하지 않으면 false로 전달해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.updatePosting(1L, 1L, "수정 제목", "수정 본문", false, false, 10L, false) } returns posting

                mockMvc.perform(
                    post("/owner/TestProj/post/1/edit")
                        .param("title", "수정 제목")
                        .param("body", "수정 본문")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)

                verify(exactly = 1) { postingService.updatePosting(1L, 1L, "수정 제목", "수정 본문", false, false, 10L, false) }
            }

            // yona BoardApp.editPost()의 "if (post.readme)"는 제출된 값을 쓰는데, yona는 그동안
            // stale한 기존 posting.readme(항상 false인 이 테스트 fixture 기준)를 써서 체크박스로
            // readme를 새로 켜는 게 반영되지 않는 버그가 있었다(#146 재검토, TASK-0263에서 발견·수정).
            it("readme 체크박스를 선택해 제출하면 제출된 값(true)이 updatePosting에 전달되어야 한다(#146)") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.updatePosting(1L, 1L, "수정 제목", "수정 본문", false, true, 10L, false) } returns posting

                mockMvc.perform(
                    post("/owner/TestProj/post/1/edit")
                        .param("title", "수정 제목")
                        .param("body", "수정 본문")
                        .param("readme", "true")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/owner/TestProj"))

                verify(exactly = 1) { postingService.updatePosting(1L, 1L, "수정 제목", "수정 본문", false, true, 10L, false) }
            }
        }

        // yona Attachment.moveOnlySelected() 대응 (P0-22) — 요청받은 첨부파일 ID를 검증 없이 그대로
        // 재배선하지 않고, 실제로 이 로그인 사용자가 업로드한 임시 첨부만 옮기는지 검증한다.
        describe("POST /{owner}/{projectName}/posts - 임시 업로드 첨부파일 연결") {
            it("temporaryUploadFiles로 넘어온 첨부파일 ID들이 moveOnlySelected를 통해 생성된 게시글로 옮겨져야 한다") {
                val savedPosting = Posting(id = 100L, number = 5L, title = "제목", body = "본문", project = project)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 951L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.createPosting(1L, any(), 10L) } returns savedPosting
                every {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.BOARD_POST, "100",
                        listOf(900L), "testuser"
                    )
                } returns 1

                val request = PostingForm(title = "제목", body = "본문", temporaryUploadFiles = "900")

                val result = boardViewController.createPost("owner", "TestProj", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/TestProj/post/5"
                verify(exactly = 1) {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.BOARD_POST, "100",
                        listOf(900L), "testuser"
                    )
                }
            }
        }

        // yona BoardApp.newPost()의 README 게시글 중복 생성 방지 대응 (P1-109).
        describe("POST /{owner}/{projectName}/posts - README 게시글 중복 생성 방지") {
            it("이미 README 게시글이 있으면 새로 만들지 않고 기존 게시글을 수정해야 한다") {
                val existingReadme = Posting(id = 50L, number = 3L, title = "옛 README", body = "옛 본문", readme = true, project = project)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 952L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingRepository.findByProjectAndReadme(project, true) } returns listOf(existingReadme)
                every { postingService.getPosting(1L, 3L) } returns existingReadme
                every {
                    postingService.updatePosting(1L, 3L, "새 README", "새 본문", false, true, 10L, false)
                } returns existingReadme

                val request = PostingForm(title = "새 README", body = "새 본문", readme = true)

                val result = boardViewController.createPost("owner", "TestProj", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/TestProj"
                verify(exactly = 0) { postingService.createPosting(any(), any(), any()) }
                verify(exactly = 1) { postingService.updatePosting(1L, 3L, "새 README", "새 본문", false, true, 10L, false) }
            }

            it("README 게시글이 아직 없으면 정상적으로 새로 생성해야 한다") {
                val savedPosting = Posting(id = 60L, number = 4L, title = "첫 README", body = "본문", readme = true, project = project)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 953L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingRepository.findByProjectAndReadme(project, true) } returns emptyList()
                every { postingService.createPosting(1L, any(), 10L) } returns savedPosting

                val request = PostingForm(title = "첫 README", body = "본문", readme = true)

                val result = boardViewController.createPost("owner", "TestProj", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/TestProj"
                verify(exactly = 1) { postingService.createPosting(1L, any(), 10L) }
            }
        }

        // yona BoardApp.newPost()의 issueTemplate write-path 대응 (P1-110).
        describe("POST /{owner}/{projectName}/posts - issueTemplate 커밋 경로") {
            it("issueTemplate=true면 게시글을 생성하지 않고 프로젝트 홈으로 리다이렉트해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 954L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                val request = PostingForm(title = "이슈 템플릿", body = "템플릿 내용", issueTemplate = "true")

                val result = boardViewController.createPost("owner", "TestProj", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/TestProj"
                verify(exactly = 0) { postingService.createPosting(any(), any(), any()) }
            }
        }

        // yona BoardApp.newPost()의 path+isMemberOf(project) 온라인 커밋 분기 대응 (P1-111).
        // BareCommit의 branch+nested-path 지원 오버로드(P1-135)가 실제로 연결됐는지 검증하기 위해
        // "/tmp/yona/git"(테스트 전역 gitBaseDir) 아래 실제 bare 저장소를 만들어 커밋 결과를 직접 확인한다.
        describe("POST /{owner}/{projectName}/posts - 코드브라우저 편집 온라인 커밋 경로") {
            it("path가 채워지면 게시글 대신 지정 브랜치의 하위 경로에 커밋하고 코드브라우저로 리다이렉트해야 한다") {
                val codeEditProject = Project(id = 99L, name = "CodeEditProj", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저", email = "testuser@yona.io")
                memberUser.projectUsers.add(ProjectUser(id = 955L, user = memberUser, project = codeEditProject, role = Role(id = RoleType.MEMBER.roleType)))

                val gitBaseDir = File("/tmp/yona/git")
                val bareDir = File(gitBaseDir, "owner/CodeEditProj.git")
                bareDir.deleteRecursively()
                Git.init().setDirectory(bareDir).setBare(true).call().close()

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "CodeEditProj") } returns Optional.of(codeEditProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(99L, 10L) } returns true

                val request = PostingForm(
                    title = "커밋 메시지",
                    body = "package foo",
                    path = "src/main/Foo.kt",
                    branch = "develop"
                )

                val result = boardViewController.createPost("owner", "CodeEditProj", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/CodeEditProj/code/develop/src/main/Foo.kt"
                verify(exactly = 0) { postingService.createPosting(any(), any(), any()) }

                val repository = FileRepositoryBuilder().setGitDir(bareDir).build()
                try {
                    val developObjectId = repository.resolve("refs/heads/develop")
                    developObjectId shouldNotBe null
                    repository.findRef("refs/heads/master") shouldBe null

                    val revWalk = RevWalk(repository)
                    val commit = revWalk.parseCommit(developObjectId)
                    val treeWalk = TreeWalk.forPath(repository, "src/main/Foo.kt", commit.tree)
                    val committedContent = repository.open(treeWalk!!.getObjectId(0)).bytes.toString(Charsets.UTF_8)
                    committedContent shouldBe "package foo"
                    treeWalk.close()
                    revWalk.close()
                } finally {
                    repository.close()
                }
            }
        }

        // yona BoardApp.java:211 @IsCreatable(ResourceType.BOARD_POST) 대응 (P1-113) — 공개 프로젝트의 [GL-controllers_BoardApp-009]
        // 비멤버 로그인 사용자도 게시글을 쓸 수 있어야 한다(회귀 수정 검증).
        describe("POST /{owner}/{projectName}/posts - 공개 프로젝트 비멤버 작성 권한 (P1-113)") {
            it("공개 프로젝트의 비멤버 로그인 사용자도 게시글을 작성할 수 있어야 한다") {
                val publicProject = Project(id = 2L, name = "PublicProj", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val nonMember = User(id = 40L, loginId = "nonmember", name = "비멤버")
                val nonMemberAuth = UsernamePasswordAuthenticationToken("nonmember", "password")
                val savedPosting = Posting(id = 70L, number = 1L, title = "제목", body = "본문", project = publicProject)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PublicProj") } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("nonmember") } returns Optional.of(nonMember)
                every { projectUserRepository.existsByProjectIdAndUserId(2L, 40L) } returns false
                every { postingService.createPosting(2L, any(), 40L) } returns savedPosting

                val request = PostingForm(title = "제목", body = "본문")

                val result = boardViewController.createPost("owner", "PublicProj", request, nonMemberAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/PublicProj/post/1"
            }
        }

        // editPostForm은 이번 세션 이전에는 커버리지 0%였다 — 권한 게이트(비로그인/비멤버/비그룹멤버),
        // 게시글 부재, canReadmefy 계산, 첨부파일 매핑까지 전 분기를 검증한다.
        describe("GET /{owner}/{projectName}/post/{number}/editform") {
            it("존재하지 않는 프로젝트면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoProj/post/1/editform"))
                    .andExpect(view().name("error/404"))
            }

            it("비로그인 상태면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/post/1/editform"))
                    .andExpect(view().name("error/forbidden"))
                    .andExpect(model().attributeExists("project"))
            }

            it("로그인했지만 프로젝트 멤버도 그룹 멤버도 아니면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(get("/owner/TestProj/post/1/editform").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            it("멤버지만 게시글이 존재하지 않으면 notfound 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 960L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.getPosting(1L, 999L) } returns null

                mockMvc.perform(get("/owner/TestProj/post/999/editform").principal(userAuth))
                    .andExpect(view().name("error/notfound"))
                    .andExpect(model().attribute("targetType", "board_post"))
            }

            it("Git 프로젝트 멤버라면 canReadmefy=true, 첨부파일 정보와 함께 board/edit 뷰를 반환해야 한다") {
                val gitProject = Project(id = 5L, name = "EditGit", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 961L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                val editPosting = Posting(id = 20L, title = "편집 대상", project = gitProject, number = 7L)
                // 첨부 필드가 null인 경우(mimeType/size)까지 매핑 람다의 elvis 분기를 함께 검증한다.
                val attachWithValues = Attachment(id = 1L, name = "a.png", containerType = ResourceType.BOARD_POST, containerId = "20", mimeType = "image/png", size = 100L)
                val attachWithNulls = Attachment(id = null, name = "b.txt", containerType = ResourceType.BOARD_POST, containerId = "20", mimeType = null, size = null)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "EditGit") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(5L, 10L) } returns true
                every { postingService.getPosting(5L, 7L) } returns editPosting
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "20") } returns listOf(attachWithValues, attachWithNulls)

                mockMvc.perform(get("/owner/EditGit/post/7/editform").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/edit"))
                    .andExpect(model().attribute("canReadmefy", true))
                    .andExpect(model().attribute("isAllowedToNotice", true))
                    .andExpect(model().attributeExists("attachmentsJson"))
            }

            it("Git 프로젝트가 아니면 canReadmefy가 false여야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 962L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.getPosting(1L, 1L) } returns posting
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "5") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/post/1/editform").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("canReadmefy", false))
            }

            it("직접 멤버가 아니어도 그룹 멤버라면 board/edit 뷰에 접근할 수 있어야 한다") {
                val org = Organization(id = 2L, name = "org2")
                val groupProject = Project(id = 6L, name = "GroupEditProj", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = org)
                org.organizationUsers.add(
                    OrganizationUser(id = 2L, user = user, organization = org, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupPosting = Posting(id = 30L, title = "그룹 글", project = groupProject, number = 8L)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GroupEditProj") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(6L, 10L) } returns false
                every { postingService.getPosting(6L, 8L) } returns groupPosting
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "30") } returns emptyList()

                mockMvc.perform(get("/owner/GroupEditProj/post/8/editform").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("isAllowedToNotice", true))
            }
        }

        // viewPost 미커버 분기 보강: 404/403/notfound, 방문기록 예외 NOOP, 비로그인 조회,
        // isAllowedUpdate의 작성자/그룹멤버 분기, 첨부파일 null 필드 매핑, 대댓글 그룹핑, isProjectManager.
        describe("GET /{owner}/{projectName}/post/{number} 추가 분기") {
            it("존재하지 않는 프로젝트면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoProj/post/1"))
                    .andExpect(view().name("error/404"))
            }

            it("비공개 프로젝트를 비로그인 상태로 조회하면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/post/1"))
                    .andExpect(view().name("error/forbidden"))
            }

            it("게시글이 존재하지 않으면 notfound 뷰를 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 971L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.getPosting(1L, 999L) } returns null

                mockMvc.perform(get("/owner/TestProj/post/999").principal(userAuth))
                    .andExpect(view().name("error/notfound"))
                    .andExpect(model().attribute("targetType", "board_post"))
            }

            it("최근 방문 기록 저장 중 예외가 발생해도 게시글 조회는 정상적으로 이뤄져야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 972L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.getPosting(1L, 1L) } returns posting
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "5") } returns emptyList()
                every { recentIssueService.recordPostingVisit(memberUser, posting) } throws RuntimeException("boom")

                mockMvc.perform(get("/owner/TestProj/post/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("board/view"))
            }

            it("비로그인 사용자가 공개 프로젝트의 글을 조회하면 워칭/수정권한이 모두 false여야 한다") {
                val publicProject = Project(id = 8L, name = "PublicView", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val publicPosting = Posting(id = 80L, title = "공개 글", project = publicProject, number = 1L)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PublicView") } returns Optional.of(publicProject)
                every { postingService.getPosting(8L, 1L) } returns publicPosting
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(80L) } returns emptyList()
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "80") } returns emptyList()

                mockMvc.perform(get("/owner/PublicView/post/1"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("isWatching", false))
                    .andExpect(model().attribute("isAllowedUpdate", false))
                    .andExpect(model().attribute("currentUser", null as Any?))
            }

            it("게시글 작성자 본인이면(비멤버라도) isAllowedUpdate가 true여야 한다") {
                val publicProject = Project(id = 9L, name = "AuthorView", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val authorUser = User(id = 41L, loginId = "author1", name = "작성자")
                val authorAuth = UsernamePasswordAuthenticationToken("author1", "password")
                val authoredPosting = Posting(id = 81L, title = "내 글", project = publicProject, number = 2L, authorLoginId = "author1")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "AuthorView") } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("author1") } returns Optional.of(authorUser)
                every { postingService.getPosting(9L, 2L) } returns authoredPosting
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(81L) } returns emptyList()
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "81") } returns emptyList()
                every { projectUserRepository.existsByProjectIdAndUserId(9L, 41L) } returns false
                every { watchService.isWatching(any(), any(), any()) } returns false

                mockMvc.perform(get("/owner/AuthorView/post/2").principal(authorAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("isAllowedUpdate", true))
            }

            it("그룹 멤버라면(작성자·직접멤버 아니어도) isAllowedUpdate가 true여야 한다") {
                val org = Organization(id = 3L, name = "org3")
                val groupProject = Project(id = 10L, name = "GroupView", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = org)
                org.organizationUsers.add(
                    OrganizationUser(id = 3L, user = user, organization = org, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupPosting = Posting(id = 82L, title = "그룹 글", project = groupProject, number = 3L, authorLoginId = "다른사람")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GroupView") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingService.getPosting(10L, 3L) } returns groupPosting
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(82L) } returns emptyList()
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "82") } returns emptyList()
                every { projectUserRepository.existsByProjectIdAndUserId(10L, 10L) } returns false
                every { watchService.isWatching(any(), any(), any()) } returns false

                mockMvc.perform(get("/owner/GroupView/post/3").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("isAllowedUpdate", true))
            }

            it("첨부파일 필드가 null이어도 매핑되고, 대댓글은 부모별로 그룹핑되어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 973L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                val parentComment = PostingComment(id = 1L, contents = "부모", posting = posting)
                val childComment = PostingComment(id = 2L, contents = "대댓글", posting = posting, parentComment = parentComment)
                val attachNoId = Attachment(id = null, name = "no-id.txt", containerType = ResourceType.BOARD_POST, containerId = "5", mimeType = null, size = null)
                // 필드가 모두 채워진 첨부도 함께 넣어 elvis 분기(값 있음/null) 양쪽을 모두 검증한다.
                val attachWithId = Attachment(id = 77L, name = "with-id.png", containerType = ResourceType.BOARD_POST, containerId = "5", mimeType = "image/png", size = 200L)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.getPosting(1L, 1L) } returns posting
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(5L) } returns listOf(parentComment, childComment)
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "5") } returns listOf(attachNoId, attachWithId)
                // P3-50: 댓글 수정 폼의 기존 첨부파일 목록(commentAttachmentsByCommentId)이
                // 댓글마다 개별 조회한다.
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.NONISSUE_COMMENT, "1") } returns emptyList()
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.NONISSUE_COMMENT, "2") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/post/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("comments", listOf(parentComment)))
                    .andExpect(model().attribute("childCommentsByParentId", mapOf(1L to listOf(childComment))))
            }

            it("프로젝트 매니저가 조회하면 isProjectManager가 true여야 한다") {
                val managerUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                val managerProjectUser = ProjectUser(id = 974L, user = managerUser, project = project, role = Role(id = RoleType.MANAGER.roleType))
                managerUser.projectUsers.add(managerProjectUser)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(managerUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(managerProjectUser)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "5") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/post/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("isProjectManager", true))
            }

            it("작성자·멤버·그룹멤버 어디에도 해당하지 않으면 isAllowedUpdate가 false여야 한다") {
                val publicProject = Project(id = 12L, name = "NoneOfThem", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val otherLoginUser = User(id = 46L, loginId = "other1", name = "제3자")
                val otherAuth = UsernamePasswordAuthenticationToken("other1", "password")
                val othersPosting = Posting(id = 83L, title = "제3자 조회", project = publicProject, number = 4L, authorLoginId = "글쓴이")

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoneOfThem") } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("other1") } returns Optional.of(otherLoginUser)
                every { postingService.getPosting(12L, 4L) } returns othersPosting
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(83L) } returns emptyList()
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "83") } returns emptyList()
                every { projectUserRepository.existsByProjectIdAndUserId(12L, 46L) } returns false
                every { watchService.isWatching(any(), any(), any()) } returns false

                mockMvc.perform(get("/owner/NoneOfThem/post/4").principal(otherAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("isAllowedUpdate", false))
            }

            it("프로젝트 멤버지만 매니저가 아니면 isProjectManager가 false여야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                val memberProjectUser = ProjectUser(id = 975L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType))
                memberUser.projectUsers.add(memberProjectUser)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(memberProjectUser)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "5") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/post/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("isProjectManager", false))
            }

            // Role.id는 non-null 강제가 아닌 nullable Long?(도메인 데이터 이상 상황) — "it.role.id ==
            // MANAGER.roleType" 비교의 role.id가 null인 경로까지 커버해야 한다.
            it("역할(role)의 id가 null이면 isProjectManager가 false여야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                val roleIdNullProjectUser = ProjectUser(id = 976L, user = memberUser, project = project, role = Role(id = null))
                memberUser.projectUsers.add(roleIdNullProjectUser)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(roleIdNullProjectUser)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(5L) } returns emptyList()
                every { watchService.isWatching(any(), any(), any()) } returns false
                every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.BOARD_POST, "5") } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/post/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("isProjectManager", false))
            }
        }

        // createPostForm 미커버 분기 보강: 404/403, readme·issueTemplate·path의 저장소 조회 성공/예외,
        // getIssueTemplate 자체(그룹3 완전 미실행), 그룹멤버의 isAllowedToNotice.
        // 주의: PlayRepository.getRawFile()은 반환타입이 ByteArray(non-null)라 "bytes != null"의
        // false 분기는 코틀린 타입 시스템상 목(mock)으로도 만들 수 없다 — 실제 구현(GitRepository/
        // SvnRepository)도 파일이 없으면 FileNotFoundException을 던지지 null을 반환하지 않는다.
        // 최종 보고에 근거로 명시하고 이 분기는 테스트로 강제하지 않는다.
        describe("GET /{owner}/{projectName}/post/new 추가 분기") {
            it("존재하지 않는 프로젝트면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoProj/post/new"))
                    .andExpect(view().name("error/404"))
            }

            it("비공개 프로젝트의 비멤버는 글쓰기 폼에 접근할 수 없어(403) 한다") {
                val nonMember = User(id = 42L, loginId = "nonmember2", name = "비멤버2")
                val nonMemberAuth = UsernamePasswordAuthenticationToken("nonmember2", "password")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("nonmember2") } returns Optional.of(nonMember)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 42L) } returns false

                mockMvc.perform(get("/owner/TestProj/post/new").principal(nonMemberAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            it("비로그인 상태로 접근하면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/post/new"))
                    .andExpect(view().name("error/forbidden"))
            }

            it("path가 빈 문자열이면(공백) 파일 조회 없이 기본 폼을 반환해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 987L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/TestProj/post/new").param("path", "  ").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("preparedPostBody", ""))
                    .andExpect(model().attribute("preparedTitle", ""))

                verify(exactly = 0) { repositoryService.getRepository(project) }
            }

            // canReadmefy의 커밋 생성 권한 검사(COMMIT)는 BOARD_POST와 달리 "공개 프로젝트 로그인 사용자는
            // 항상 허용" 목록에 포함되지 않는다(AccessControl.isProjectResourceCreatable) — 공개 프로젝트의
            // 비멤버는 readme=true & GIT이어도 canReadmefy가 false여야 한다.
            it("공개 프로젝트의 비멤버는 readme=true여도 커밋 권한이 없어 canReadmefy가 false여야 한다") {
                val publicGitProject = Project(id = 28L, name = "PublicGitNonMember", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PUBLIC)
                val nonMember = User(id = 47L, loginId = "nonmember5", name = "비멤버5")
                val nonMemberAuth = UsernamePasswordAuthenticationToken("nonmember5", "password")
                val mockRepo = mockk<PlayRepository>()
                every { mockRepo.getRawFile("HEAD", "README.md") } returns "내용".toByteArray(Charsets.UTF_8)
                every { repositoryService.getRepository(publicGitProject) } returns mockRepo

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PublicGitNonMember") } returns Optional.of(publicGitProject)
                every { userRepository.findByLoginId("nonmember5") } returns Optional.of(nonMember)
                every { projectUserRepository.existsByProjectIdAndUserId(28L, 47L) } returns false

                mockMvc.perform(get("/owner/PublicGitNonMember/post/new").param("readme", "true").principal(nonMemberAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("canReadmefy", false))
            }

            // legacy board/create.scala.html의 titleMessage 대응 — "README 만들기/편집" 버튼은
            // 항상 이 create 폼으로 오고 기존 제목을 조회하지 않아, title을 미리 채워두지 않으면
            // 매번 필수 입력값을 새로 타이핑해야 한다(사용자가 직접 재현·신고한 실사용 버그).
            it("readme=true면 title이 'Update README.md'로 미리 채워져야 한다") {
                val gitProject = Project(id = 23L, name = "ReadmeTitleProj", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 981L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                val mockRepo = mockk<PlayRepository>()
                every { mockRepo.getRawFile("HEAD", "README.md") } returns "readme 내용".toByteArray(Charsets.UTF_8)
                every { repositoryService.getRepository(gitProject) } returns mockRepo
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "ReadmeTitleProj") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(23L, 10L) } returns true

                mockMvc.perform(get("/owner/ReadmeTitleProj/post/new").param("readme", "true").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("preparedTitle", "Update README.md"))
            }

            it("issueTemplate=true면 ISSUE_TEMPLATE.md 내용이 preparedPostBody에 채워져야 한다") {
                val gitProject = Project(id = 21L, name = "IssueTplProj", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 980L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                val mockRepo = mockk<PlayRepository>()
                every { mockRepo.getRawFile("HEAD", "ISSUE_TEMPLATE.md") } returns "템플릿 내용".toByteArray(Charsets.UTF_8)
                every { repositoryService.getRepository(gitProject) } returns mockRepo
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "IssueTplProj") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(21L, 10L) } returns true

                mockMvc.perform(get("/owner/IssueTplProj/post/new").param("issueTemplate", "true").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("preparedPostBody", "템플릿 내용"))
            }

            it("issueTemplate=true면 title이 'ISSUE_TEMPLATE.md: Project Issue Template'로 미리 채워져야 한다") {
                val gitProject = Project(id = 24L, name = "IssueTplTitleProj", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 982L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                val mockRepo = mockk<PlayRepository>()
                every { mockRepo.getRawFile("HEAD", "ISSUE_TEMPLATE.md") } returns "템플릿 내용".toByteArray(Charsets.UTF_8)
                every { repositoryService.getRepository(gitProject) } returns mockRepo
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "IssueTplTitleProj") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(24L, 10L) } returns true

                mockMvc.perform(get("/owner/IssueTplTitleProj/post/new").param("issueTemplate", "true").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("preparedTitle", "ISSUE_TEMPLATE.md: Project Issue Template"))
            }

            it("issueTemplate 조회 중 예외가 발생하면 preparedPostBody가 빈 문자열이어야 한다") {
                val gitProject = Project(id = 22L, name = "IssueTplBroken", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 981L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                val mockRepo = mockk<PlayRepository>()
                every { mockRepo.getRawFile("HEAD", "ISSUE_TEMPLATE.md") } throws RuntimeException("not found")
                every { repositoryService.getRepository(gitProject) } returns mockRepo
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "IssueTplBroken") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(22L, 10L) } returns true

                mockMvc.perform(get("/owner/IssueTplBroken/post/new").param("issueTemplate", "true").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("preparedPostBody", ""))
            }

            it("readme=true인데 README 후보 4개 모두 조회 중 예외가 발생해도 정상적으로 폼을 반환해야 한다") {
                val gitProject = Project(id = 23L, name = "ReadmeBroken", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 982L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                val mockRepo = mockk<PlayRepository>()
                every { mockRepo.getRawFile("HEAD", "README.md") } throws RuntimeException("io error")
                every { mockRepo.getRawFile("HEAD", "readme.md") } throws RuntimeException("io error")
                every { mockRepo.getRawFile("HEAD", "README.markdown") } throws RuntimeException("io error")
                every { mockRepo.getRawFile("HEAD", "readme.markdown") } throws RuntimeException("io error")
                every { repositoryService.getRepository(gitProject) } returns mockRepo
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "ReadmeBroken") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(23L, 10L) } returns true

                mockMvc.perform(get("/owner/ReadmeBroken/post/new").param("readme", "true").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("preparedPostBody", ""))
            }

            // yona READMEFileNameFilter() 대응 (P2-47) — README.md가 없으면 readme.md/README.markdown/
            // readme.markdown 순서로 다음 후보를 시도해야 한다.
            it("README.md가 없으면 다음 후보(readme.md)의 내용을 preparedPostBody로 채워야 한다") {
                val gitProject = Project(id = 29L, name = "ReadmeFallback", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 985L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                val mockRepo = mockk<PlayRepository>()
                every { mockRepo.getRawFile("HEAD", "README.md") } throws java.io.FileNotFoundException()
                every { mockRepo.getRawFile("HEAD", "readme.md") } returns "소문자 README 내용".toByteArray(Charsets.UTF_8)
                every { repositoryService.getRepository(gitProject) } returns mockRepo
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "ReadmeFallback") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(29L, 10L) } returns true

                mockMvc.perform(get("/owner/ReadmeFallback/post/new").param("readme", "true").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("preparedPostBody", "소문자 README 내용"))

                verify(exactly = 0) { mockRepo.getRawFile("HEAD", "README.markdown") }
            }

            it("README.md/readme.md가 없으면 README.markdown의 내용을 preparedPostBody로 채워야 한다") {
                val gitProject = Project(id = 30L, name = "ReadmeMarkdownFallback", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 986L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                val mockRepo = mockk<PlayRepository>()
                every { mockRepo.getRawFile("HEAD", "README.md") } throws java.io.FileNotFoundException()
                every { mockRepo.getRawFile("HEAD", "readme.md") } throws java.io.FileNotFoundException()
                every { mockRepo.getRawFile("HEAD", "README.markdown") } returns "마크다운 확장자 README".toByteArray(Charsets.UTF_8)
                every { repositoryService.getRepository(gitProject) } returns mockRepo
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "ReadmeMarkdownFallback") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(30L, 10L) } returns true

                mockMvc.perform(get("/owner/ReadmeMarkdownFallback/post/new").param("readme", "true").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("preparedPostBody", "마크다운 확장자 README"))
            }

            it("path 파라미터만 있으면 HEAD 브랜치의 파일 내용을 preparedPostBody로 채워야 한다") {
                val gitProject = Project(id = 24L, name = "PathHead", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 983L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                val mockRepo = mockk<PlayRepository>()
                every { mockRepo.getRawFile("HEAD", "docs/readme.txt") } returns "파일내용".toByteArray(Charsets.UTF_8)
                every { repositoryService.getRepository(gitProject) } returns mockRepo
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PathHead") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(24L, 10L) } returns true

                mockMvc.perform(get("/owner/PathHead/post/new").param("path", "docs/readme.txt").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("preparedPostBody", "파일내용"))
            }

            it("path와 branch가 함께 있으면 해당 브랜치의 파일 내용을 채워야 한다") {
                val gitProject = Project(id = 25L, name = "PathBranch", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 984L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                val mockRepo = mockk<PlayRepository>()
                every { mockRepo.getRawFile("develop", "docs/readme.txt") } returns "브랜치내용".toByteArray(Charsets.UTF_8)
                every { repositoryService.getRepository(gitProject) } returns mockRepo
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PathBranch") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(25L, 10L) } returns true

                mockMvc.perform(
                    get("/owner/PathBranch/post/new").param("path", "docs/readme.txt").param("branch", "develop").principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("preparedPostBody", "브랜치내용"))
            }

            it("path 조회 중 예외가 발생해도 정상적으로 폼을 반환해야 한다") {
                val gitProject = Project(id = 26L, name = "PathBroken", owner = "owner", vcs = "GIT", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 985L, user = memberUser, project = gitProject, role = Role(id = RoleType.MEMBER.roleType)))
                val mockRepo = mockk<PlayRepository>()
                every { mockRepo.getRawFile(any(), any()) } throws RuntimeException("boom")
                every { repositoryService.getRepository(gitProject) } returns mockRepo
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PathBroken") } returns Optional.of(gitProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(26L, 10L) } returns true

                mockMvc.perform(get("/owner/PathBroken/post/new").param("path", "bad.txt").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("preparedPostBody", ""))
            }

            it("readme=true여도 Git 프로젝트가 아니면 canReadmefy가 false여야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 986L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                val mockRepo = mockk<PlayRepository>()
                every { mockRepo.getRawFile("HEAD", "README.md") } returns "내용".toByteArray(Charsets.UTF_8)
                every { repositoryService.getRepository(project) } returns mockRepo
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/owner/TestProj/post/new").param("readme", "true").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("canReadmefy", false))
            }

            it("직접 멤버가 아니어도 그룹 멤버라면 isAllowedToNotice가 true여야 한다") {
                val org = Organization(id = 4L, name = "org4")
                val groupProject = Project(id = 27L, name = "GroupCreateProj", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = org)
                org.organizationUsers.add(
                    OrganizationUser(id = 4L, user = user, organization = org, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GroupCreateProj") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(27L, 10L) } returns false

                mockMvc.perform(get("/owner/GroupCreateProj/post/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("isAllowedToNotice", true))
            }
        }

        // createPost 미커버 분기 보강: 404/비로그인 403/비회원 403, branch 미지정 온라인 커밋(빈 문자열
        // 기본값), 온라인 커밋/issueTemplate 커밋/README 커밋 각각의 예외 catch 분기, temporaryUploadFiles의
        // 숫자 아닌 값 필터링.
        describe("POST /{owner}/{projectName}/posts 추가 분기") {
            it("존재하지 않는 프로젝트면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoProj") } returns Optional.empty()

                mockMvc.perform(post("/owner/NoProj/posts").param("title", "t").param("body", "b"))
                    .andExpect(view().name("error/404"))
            }

            it("비로그인 상태면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(post("/owner/TestProj/posts").param("title", "t").param("body", "b"))
                    .andExpect(view().name("error/forbidden"))
            }

            // authentication은 있지만 DB에 해당 loginId의 User가 없는 경우 — "authentication?.let{...}
            // ?: run{forbidden}"에서 `?:`가 트리거되는 건 authentication이 null일 때뿐 아니라 이 경우에도 해당한다.
            it("인증은 됐지만 DB에 사용자가 없으면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(post("/owner/TestProj/posts").param("title", "t").param("body", "b").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            it("비공개 프로젝트의 비멤버는 글을 작성할 수 없어(403) 한다") {
                val nonMember = User(id = 43L, loginId = "nonmember3", name = "비멤버3")
                val nonMemberAuth = UsernamePasswordAuthenticationToken("nonmember3", "password")
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("nonmember3") } returns Optional.of(nonMember)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 43L) } returns false

                mockMvc.perform(post("/owner/TestProj/posts").param("title", "t").param("body", "b").principal(nonMemberAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            it("branch 파라미터 없이 path만 있으면 빈 브랜치명으로 커밋을 시도하고 코드브라우저로 리다이렉트해야 한다") {
                val brokenProject = Project(id = 101L, name = "BrokenRepo", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 990L, user = memberUser, project = brokenProject, role = Role(id = RoleType.MEMBER.roleType)))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "BrokenRepo") } returns Optional.of(brokenProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(101L, 10L) } returns true

                val request = PostingForm(title = "커밋", body = "내용", path = "a.txt")
                val result = boardViewController.createPost("owner", "BrokenRepo", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/BrokenRepo/code//a.txt"
                verify(exactly = 0) { postingService.createPosting(any(), any(), any()) }
            }

            it("issueTemplate 커밋 중 예외가 발생해도 프로젝트 홈으로 리다이렉트해야 한다") {
                val brokenProject = Project(id = 102L, name = "BrokenIssueTpl", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 991L, user = memberUser, project = brokenProject, role = Role(id = RoleType.MEMBER.roleType)))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "BrokenIssueTpl") } returns Optional.of(brokenProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(102L, 10L) } returns true

                val request = PostingForm(title = "템플릿", body = "내용", issueTemplate = "true")
                val result = boardViewController.createPost("owner", "BrokenIssueTpl", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/BrokenIssueTpl"
                verify(exactly = 0) { postingService.createPosting(any(), any(), any()) }
            }

            it("README 게시글 생성 후 실제 커밋 중 예외가 발생해도 프로젝트 홈으로 리다이렉트해야 한다") {
                val brokenProject = Project(id = 103L, name = "BrokenReadmeCommit", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 992L, user = memberUser, project = brokenProject, role = Role(id = RoleType.MEMBER.roleType)))
                val savedPosting = Posting(id = 200L, number = 9L, title = "README", body = "내용", readme = true, project = brokenProject)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "BrokenReadmeCommit") } returns Optional.of(brokenProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(103L, 10L) } returns true
                every { postingRepository.findByProjectAndReadme(brokenProject, true) } returns emptyList()
                every { postingService.createPosting(103L, any(), 10L) } returns savedPosting

                val request = PostingForm(title = "README", body = "내용", readme = true)
                val result = boardViewController.createPost("owner", "BrokenReadmeCommit", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/BrokenReadmeCommit"
            }

            it("temporaryUploadFiles에 숫자가 아닌 값이 섞여 있으면 유효한 ID만 옮겨야 한다") {
                val savedPosting = Posting(id = 110L, number = 6L, title = "제목", body = "본문", project = project)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 993L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.createPosting(1L, any(), 10L) } returns savedPosting
                every {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.BOARD_POST, "110",
                        listOf(901L), "testuser"
                    )
                } returns 1

                val request = PostingForm(title = "제목", body = "본문", temporaryUploadFiles = "901, abc, ")
                val result = boardViewController.createPost("owner", "TestProj", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/TestProj/post/6"
                verify(exactly = 1) {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.BOARD_POST, "110",
                        listOf(901L), "testuser"
                    )
                }
            }

            // PostingForm의 body/notice/readme는 기본값이 이미 non-null(""/false)이라 실제 폼 바인딩으로는
            // null이 들어올 수 없다 — 하지만 필드 타입 자체는 nullable이므로 컨트롤러를 직접 호출해
            // "?: 기본값" 분기(존재 자체는 유효한 코드 경로)를 검증한다.
            // 참고: User.loginId는 도메인 모델상 non-null String(기본값 "")이라
            // "loginUser.loginId ?: \"\"" 분기의 null쪽은 타입 시스템상 도달 불가능이다 — 최종 보고에 근거 명시.
            it("PostingForm 필드가 null이면 기본값(빈 문자열/false)으로 게시글이 생성되어야 한다") {
                val memberUser = User(id = 44L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 9001L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                val savedPosting = Posting(id = 120L, number = 20L, title = "제목", body = "", project = project)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { postingService.createPosting(1L, any(), 44L) } returns savedPosting
                every {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.BOARD_POST, "120",
                        listOf(902L), "testuser"
                    )
                } returns 1

                val request = PostingForm(title = "제목", body = null, notice = null, readme = null, temporaryUploadFiles = "902")
                val result = boardViewController.createPost("owner", "TestProj", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/TestProj/post/20"
                verify(exactly = 1) {
                    attachmentService.moveOnlySelected(
                        ResourceType.NOT_A_RESOURCE, "",
                        ResourceType.BOARD_POST, "120",
                        listOf(902L), "testuser"
                    )
                }
            }

            it("issueTemplate 커밋 시 body가 null이면 빈 문자열로 처리되어야 한다") {
                val brokenProject = Project(id = 104L, name = "BrokenIssueTplNullBody", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 9002L, user = memberUser, project = brokenProject, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "BrokenIssueTplNullBody") } returns Optional.of(brokenProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(104L, 10L) } returns true

                val request = PostingForm(title = "템플릿", body = null, issueTemplate = "true")
                val result = boardViewController.createPost("owner", "BrokenIssueTplNullBody", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/BrokenIssueTplNullBody"
            }

            it("온라인 커밋 경로에서 path가 있어도 비멤버면 일반 게시글로 생성되어야 한다") {
                val nonMember = User(id = 45L, loginId = "nonmember4", name = "비멤버4")
                val nonMemberAuth = UsernamePasswordAuthenticationToken("nonmember4", "password")
                val publicProject = Project(id = 105L, name = "PathNonMember", owner = "owner", projectScope = ProjectScope.PUBLIC)
                val savedPosting = Posting(id = 130L, number = 21L, title = "제목", body = "본문", project = publicProject)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "PathNonMember") } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("nonmember4") } returns Optional.of(nonMember)
                every { projectUserRepository.existsByProjectIdAndUserId(105L, 45L) } returns false
                every { postingService.createPosting(105L, any(), 45L) } returns savedPosting

                val request = PostingForm(title = "제목", body = "본문", path = "a.txt")
                val result = boardViewController.createPost("owner", "PathNonMember", request, nonMemberAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/PathNonMember/post/21"
                verify(exactly = 1) { postingService.createPosting(105L, any(), 45L) }
            }

            it("path가 공백뿐이면 온라인 커밋 없이 일반 게시글로 생성되어야 한다") {
                val savedPosting = Posting(id = 150L, number = 24L, title = "제목", body = "본문", project = project)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 9006L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.createPosting(1L, any(), 10L) } returns savedPosting

                val request = PostingForm(title = "제목", body = "본문", path = "   ")
                val result = boardViewController.createPost("owner", "TestProj", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/TestProj/post/24"
            }

            it("온라인 커밋 중 body가 null이면 빈 문자열로 처리되어야 한다") {
                val brokenProject = Project(id = 106L, name = "BrokenPathNullBody", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 9003L, user = memberUser, project = brokenProject, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "BrokenPathNullBody") } returns Optional.of(brokenProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(106L, 10L) } returns true

                val request = PostingForm(title = "커밋", body = null, path = "a.txt")
                val result = boardViewController.createPost("owner", "BrokenPathNullBody", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/BrokenPathNullBody/code//a.txt"
            }

            it("README 게시글의 본문이 null이어도 커밋 시 빈 문자열로 처리되어야 한다") {
                val brokenProject = Project(id = 107L, name = "BrokenReadmeNullBody", owner = "owner", projectScope = ProjectScope.PRIVATE)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 9004L, user = memberUser, project = brokenProject, role = Role(id = RoleType.MEMBER.roleType)))
                val savedPosting = Posting(id = 210L, number = 22L, title = "README", body = null, readme = true, project = brokenProject)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "BrokenReadmeNullBody") } returns Optional.of(brokenProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(107L, 10L) } returns true
                every { postingRepository.findByProjectAndReadme(brokenProject, true) } returns emptyList()
                every { postingService.createPosting(107L, any(), 10L) } returns savedPosting

                val request = PostingForm(title = "README", body = "무시됨", readme = true)
                val result = boardViewController.createPost("owner", "BrokenReadmeNullBody", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/BrokenReadmeNullBody"
            }

            it("temporaryUploadFiles가 공백뿐이면 첨부파일 이동을 시도하지 않아야 한다") {
                val savedPosting = Posting(id = 140L, number = 23L, title = "제목", body = "본문", project = project)
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 9005L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.createPosting(1L, any(), 10L) } returns savedPosting

                val request = PostingForm(title = "제목", body = "본문", temporaryUploadFiles = "   ")
                val result = boardViewController.createPost("owner", "TestProj", request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/TestProj/post/23"
                verify(exactly = 0) { attachmentService.moveOnlySelected(any(), any(), ResourceType.BOARD_POST, "140", any(), any()) }
            }
        }

        // editPost 미커버 분기 보강: 404/비로그인 403/notfound, 작성자 본인의 멤버십 우회, 무권한 403,
        // 그룹멤버 허용.
        describe("POST /{owner}/{projectName}/post/{number}/edit 추가 분기") {
            it("존재하지 않는 프로젝트면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoProj") } returns Optional.empty()

                mockMvc.perform(post("/owner/NoProj/post/1/edit").param("title", "t").param("body", "b"))
                    .andExpect(view().name("error/404"))
            }

            it("비로그인 상태로 수정 요청하면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(post("/owner/TestProj/post/1/edit").param("title", "t").param("body", "b"))
                    .andExpect(view().name("error/forbidden"))
            }

            // authentication은 있지만(로그인은 했지만) DB에 해당 loginId의 User가 없는 경우 —
            // "authentication?.let{...} ?: run{forbidden}"에서 `?:`가 트리거되는 건 authentication이
            // null일 때뿐 아니라 이 경우에도 해당한다.
            it("인증은 됐지만 DB에 사용자가 없으면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(post("/owner/TestProj/post/1/edit").param("title", "t").param("body", "b").principal(userAuth))
                    .andExpect(view().name("error/forbidden"))
            }

            it("게시글이 존재하지 않으면 notfound 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingService.getPosting(1L, 999L) } returns null

                mockMvc.perform(
                    post("/owner/TestProj/post/999/edit").param("title", "t").param("body", "b").principal(userAuth)
                )
                    .andExpect(view().name("error/notfound"))
                    .andExpect(model().attribute("targetType", "board_post"))
            }

            it("작성자 본인이면 프로젝트 멤버가 아니어도 수정할 수 있어야 한다") {
                val authoredPosting = Posting(id = 51L, number = 11L, title = "내 글", authorLoginId = "testuser", project = project)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingService.getPosting(1L, 11L) } returns authoredPosting
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false
                every { postingService.updatePosting(1L, 11L, "수정 제목", "수정 본문", false, false, 10L, false) } returns authoredPosting

                mockMvc.perform(
                    post("/owner/TestProj/post/11/edit")
                        .param("title", "수정 제목").param("body", "수정 본문").principal(userAuth)
                ).andExpect(status().is3xxRedirection)

                verify(exactly = 1) { postingService.updatePosting(1L, 11L, "수정 제목", "수정 본문", false, false, 10L, false) }
            }

            it("작성자도 멤버도 그룹멤버도 아니면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns false

                mockMvc.perform(
                    post("/owner/TestProj/post/1/edit").param("title", "t").param("body", "b").principal(userAuth)
                ).andExpect(view().name("error/forbidden"))
            }

            it("직접 멤버가 아니어도 그룹 멤버라면 수정할 수 있어야 한다") {
                val org = Organization(id = 5L, name = "org5")
                val groupProject = Project(id = 11L, name = "GroupEditPost", owner = "owner", projectScope = ProjectScope.PROTECTED, organization = org)
                org.organizationUsers.add(
                    OrganizationUser(id = 5L, user = user, organization = org, role = Role(id = RoleType.ORG_MEMBER.roleType))
                )
                val groupPosting = Posting(id = 52L, number = 12L, title = "그룹 글", authorLoginId = "다른사람", project = groupProject)

                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "GroupEditPost") } returns Optional.of(groupProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingService.getPosting(11L, 12L) } returns groupPosting
                every { projectUserRepository.existsByProjectIdAndUserId(11L, 10L) } returns false
                every { postingService.updatePosting(11L, 12L, "수정", "본문", false, false, 10L, false) } returns groupPosting

                mockMvc.perform(
                    post("/owner/GroupEditPost/post/12/edit").param("title", "수정").param("body", "본문").principal(userAuth)
                ).andExpect(status().is3xxRedirection)
            }

            // PostingForm.body/notice/sendNotificationMail/readme는 기본값이 이미 non-null이라 실제 폼
            // 바인딩으로는 null이 될 수 없지만, 필드 타입 자체가 nullable이므로 컨트롤러를 직접 호출해
            // "?: 기본값" 분기를 검증한다.
            it("PostingForm 필드가 모두 null이면 기본값(빈 문자열/false)으로 처리되어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.updatePosting(1L, 1L, "제목", "", false, false, 10L, false) } returns posting

                val request = PostingForm(title = "제목", body = null, notice = null, readme = null, sendNotificationMail = null)
                val result = boardViewController.editPost("owner", "TestProj", 1L, request, userAuth, ExtendedModelMap())

                result shouldBe "redirect:/owner/TestProj/post/1"
                verify(exactly = 1) { postingService.updatePosting(1L, 1L, "제목", "", false, false, 10L, false) }
            }
        }

        // listPosts 미커버 분기 보강: 404, filter 검색, orderDir=asc, pageNum 변환(양수/0이하),
        // labelIds+filter 동시 사용, labelIds 빈 리스트.
        describe("GET /{owner}/{projectName}/posts 추가 분기") {
            it("존재하지 않는 프로젝트면 404 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "NoProj") } returns Optional.empty()

                mockMvc.perform(get("/owner/NoProj/posts"))
                    .andExpect(view().name("error/404"))
            }

            it("filter 파라미터가 있으면 검색 쿼리를 사용해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 994L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingRepository.searchPostingsInProject(project, "%검색어%", any<Pageable>()) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/posts").param("filter", "검색어").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("filter", "검색어"))

                verify(exactly = 1) { postingRepository.searchPostingsInProject(project, "%검색어%", any<Pageable>()) }
            }

            it("orderDir=asc면 오름차순 정렬을 사용해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 995L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val pageableSlot = slot<Pageable>()
                every { postingRepository.findByProjectAndNotice(project, false, capture(pageableSlot)) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/posts").param("orderDir", "asc").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.sort.getOrderFor("createdDate")!!.direction shouldBe Sort.Direction.ASC
            }

            it("pageNum이 1보다 크면 0-based 페이지로 변환되어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 996L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val pageableSlot = slot<Pageable>()
                every { postingRepository.findByProjectAndNotice(project, false, capture(pageableSlot)) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/posts").param("pageNum", "3").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageNumber shouldBe 2
            }

            it("pageNum이 0 이하이면 첫 페이지(0)로 처리되어야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 997L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val pageableSlot = slot<Pageable>()
                every { postingRepository.findByProjectAndNotice(project, false, capture(pageableSlot)) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/posts").param("pageNum", "0").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageNumber shouldBe 0
            }

            it("labelIds와 filter가 함께 있으면 라벨+검색 패턴을 함께 사용해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 998L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every {
                    postingRepository.findByProjectAndLabelIdsIn(project, listOf(7L), "%검색어%", any<Pageable>())
                } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(
                    get("/owner/TestProj/posts").param("labelIds", "7").param("filter", "검색어").principal(userAuth)
                ).andExpect(status().isOk)

                verify(exactly = 1) { postingRepository.findByProjectAndLabelIdsIn(project, listOf(7L), "%검색어%", any()) }
            }

            it("labelIds가 있고 filter가 공백뿐이면 라벨 필터만(검색 패턴 없이) 적용해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 9011L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every {
                    postingRepository.findByProjectAndLabelIdsIn(project, listOf(8L), null, any<Pageable>())
                } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(
                    get("/owner/TestProj/posts").param("labelIds", "8").param("filter", "  ").principal(userAuth)
                ).andExpect(status().isOk)

                verify(exactly = 1) { postingRepository.findByProjectAndLabelIdsIn(project, listOf(8L), null, any()) }
            }

            it("labelIds가 빈 리스트면 라벨 필터 없이 일반 목록을 조회해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 999L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingRepository.findByProjectAndNotice(project, false, any<Pageable>()) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                val result = boardViewController.listPosts(
                    "owner", "TestProj", 0, null, null, "createdDate", "desc", emptyList(), userAuth, ExtendedModelMap()
                )

                result shouldBe "board/list"
                verify(exactly = 0) { postingRepository.findByProjectAndLabelIdsIn(any(), any(), any(), any()) }
            }

            it("비로그인 상태로 목록을 조회하면 403 Forbidden 뷰를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)

                mockMvc.perform(get("/owner/TestProj/posts"))
                    .andExpect(view().name("error/forbidden"))
            }

            it("filter가 빈 문자열(공백)이면 필터 없이 전체 목록을 조회해야 한다") {
                val memberUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
                memberUser.projectUsers.add(ProjectUser(id = 9010L, user = memberUser, project = project, role = Role(id = RoleType.MEMBER.roleType)))
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProj") } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(memberUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingRepository.findByProjectAndNotice(project, false, any<Pageable>()) } returns PageImpl(listOf(posting), pageRequest, 1)
                every { postingService.getNotices(1L) } returns emptyList()

                mockMvc.perform(get("/owner/TestProj/posts").param("filter", "   ").principal(userAuth))
                    .andExpect(status().isOk)

                verify(exactly = 0) { postingRepository.searchPostingsInProject(any(), any(), any()) }
            }
        }
    }
})
