package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import org.springframework.ui.ExtendedModelMap
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationService
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.mention.MentionService
import com.github.yonaprojects.yona.domain.role.RoleRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.attachment.LogoValidator
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import jakarta.servlet.http.HttpServletRequest
import java.io.File

class OrganizationViewControllerSpec : DescribeSpec({
    val organizationRepository = mockk<OrganizationRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val userRepository = mockk<UserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val organizationService = mockk<OrganizationService>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val attachmentService = mockk<AttachmentService>()
    val accessControl = AccessControl(
        mockk<ProjectUserRepository>(),
        organizationUserRepository,
        userRepository,
        organizationRepository,
        issueRepository,
        postingRepository,
        mockk<ReviewCommentRepository>(),
        mockk<CommitCommentRepository>(),
        mockk<MilestoneRepository>()
    )

    val mentionService = mockk<MentionService>()
    val roleRepository = mockk<RoleRepository>()

    val organizationViewController = OrganizationViewController(
        organizationRepository,
        organizationUserRepository,
        userRepository,
        issueRepository,
        postingRepository,
        pullRequestRepository,
        organizationService,
        attachmentRepository,
        attachmentService,
        accessControl,
        mentionService,
        roleRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(organizationViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(
            organizationRepository, organizationUserRepository, userRepository,
            issueRepository, postingRepository, pullRequestRepository,
            organizationService, attachmentRepository, attachmentService
        )
        every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    }

    describe("OrganizationViewController 템플릿 연동 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저", state = UserState.ACTIVE)
        val siteManager = User(id = 20L, loginId = "admin", name = "관리자", state = UserState.SITE_ADMIN)
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")

        val org = Organization(id = 1L, name = "testorg")
        val roleMember = Role(id = RoleType.ORG_MEMBER.roleType, name = "ORG_MEMBER")
        val roleAdmin = Role(id = RoleType.ORG_ADMIN.roleType, name = "ORG_ADMIN")
        every {
            roleRepository.findAllById(listOf(RoleType.ORG_ADMIN.roleType, RoleType.ORG_MEMBER.roleType))
        } returns listOf(roleAdmin, roleMember)

        // yona OrganizationApp.java:90-91 @GuestProhibit 대응 (P1-121). orgList와 동일하게 [GL-controllers_OrganizationApp-005]
        // isGuest 계정만 차단하고 비로그인 사용자는 (별도 @AnonymousCheck가 담당하는) error/403으로
        // 처리된다.
        describe("POST /organizations/new") {
            it("게스트 계정이면 조직을 생성하지 않고 인덱스로 리다이렉트해야 한다") {
                val guestAuth = UsernamePasswordAuthenticationToken("guestuser", "password")
                val guestUser = User(id = 31L, loginId = "guestuser", name = "게스트", state = UserState.ACTIVE, isGuest = true)
                every { userRepository.findByLoginId("guestuser") } returns Optional.of(guestUser)

                mockMvc.perform(
                    post("/organizations/new").principal(guestAuth)
                        .param("name", "neworg")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/"))

                verify(exactly = 0) { organizationService.createOrganization(any(), any(), any()) }
            }

            it("일반 사용자면 조직을 생성해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.createOrganization("neworg", null, 10L) } returns Organization(id = 5L, name = "neworg")

                mockMvc.perform(
                    post("/organizations/new").principal(userAuth)
                        .param("name", "neworg")
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/organizations/neworg"))
            }
        }

        // yona OrganizationApp.java:409-420 validateForUpdate()의 LogoUtil.isImageFile()/ [GL-controllers_OrganizationApp-022]
        // LOGO_FILE_LIMIT_SIZE 검증 대응 (P1-124). 검증 실패 시 이름/설명 변경을 포함해 전체
        // 갱신 자체가 거부되어야 한다(legacy가 badRequest(setting.render(...))로 아무 것도
        // 반영하지 않는 것과 동일).
        describe("POST /organizations/{orgName}/setting 로고 업로드 검증") {
            it("이미지가 아닌 파일이면 갱신 자체를 거부해야 한다") {
                org.organizationUsers = mutableListOf(OrganizationUser(id = 3L, user = user, organization = org, role = roleAdmin))
                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                val badFile = MockMultipartFile("logoPath", "malware.exe", "application/octet-stream", ByteArray(10))

                mockMvc.perform(
                    multipart("/organizations/testorg/setting").file(badFile).principal(userAuth)
                        .param("name", "testorg")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/setting"))

                verify(exactly = 0) { organizationService.updateOrganizationSettings(any(), any(), any(), any()) }
            }

            it("5MB를 초과하는 이미지면 갱신 자체를 거부해야 한다") {
                org.organizationUsers = mutableListOf(OrganizationUser(id = 3L, user = user, organization = org, role = roleAdmin))
                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                val oversizedFile = MockMultipartFile(
                    "logoPath", "logo.png", "image/png",
                    ByteArray((LogoValidator.LOGO_FILE_LIMIT_SIZE + 1).toInt())
                )

                mockMvc.perform(
                    multipart("/organizations/testorg/setting").file(oversizedFile).principal(userAuth)
                        .param("name", "testorg")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/setting"))

                verify(exactly = 0) { organizationService.updateOrganizationSettings(any(), any(), any(), any()) }
            }

            it("정상 크기의 이미지면 설정을 갱신해야 한다") {
                org.organizationUsers = mutableListOf(OrganizationUser(id = 3L, user = user, organization = org, role = roleAdmin))
                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.updateOrganizationSettings(org.id!!, "testorg", "", user.id!!) } returns Unit
                every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
                every { attachmentService.store(any(), any(), any(), any(), any()) } returns
                    (mockk<Attachment>(relaxed = true) to true)

                val goodFile = MockMultipartFile("logoPath", "logo.png", "image/png", ByteArray(10))

                mockMvc.perform(
                    multipart("/organizations/testorg/setting").file(goodFile).principal(userAuth)
                        .param("name", "testorg")
                )
                    .andExpect(status().is3xxRedirection)

                verify(exactly = 1) { organizationService.updateOrganizationSettings(org.id!!, "testorg", "", user.id!!) }
            }
        }

        describe("GET /org/{orgName}") {
            it("조직이 존재하지 않으면 404 에러 뷰를 반환해야 한다") {
                every { organizationRepository.findByName("nonexistent") } returns Optional.empty()

                mockMvc.perform(get("/org/nonexistent").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("조직이 존재하면 200 OK와 organization/view 뷰를 반환해야 한다") {
                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/org/testorg").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/view"))
                    .andExpect(model().attributeExists("org", "projects", "orgUsers", "currentUser"))
            }
        }

        describe("GET /org/{orgName}/members") {
            it("조직이 존재하지 않으면 404 에러 뷰를 반환해야 한다") {
                every { organizationRepository.findByName("nonexistent") } returns Optional.empty()

                mockMvc.perform(get("/org/nonexistent/members").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("일반 유저(조직 Admin 아님)가 접근하면 컨텍스트 인지형 403(error/forbidden_organization) 뷰를 반환해야 한다") {
                val orgUser = OrganizationUser(id = 1L, user = user, organization = org, role = roleMember)
                org.organizationUsers = mutableListOf(orgUser)

                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                // yona error/forbidden_organization.scala.html 대응 (P-템플릿 #49) — 조직은 이미
                // 찾았으므로 조직 헤더/메뉴가 붙는 컨텍스트 인지형 403(제네릭 error/403이 아니다).
                mockMvc.perform(get("/org/testorg/members").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden_organization"))
                    .andExpect(model().attributeExists("org"))
            }

            it("조직 Admin인 유저가 접근하면 200 OK와 organization/members 뷰를 반환해야 한다") {
                val orgUser = OrganizationUser(id = 1L, user = user, organization = org, role = roleAdmin)
                org.organizationUsers = mutableListOf(orgUser)

                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/org/testorg/members").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/members"))
                    .andExpect(model().attributeExists("org", "orgUsers", "currentUser"))
            }

            it("SiteManager인 유저가 접근하면 권한 검사를 패스하고 200 OK와 organization/members 뷰를 반환해야 한다") {
                val orgUser = OrganizationUser(id = 1L, user = user, organization = org, role = roleMember)
                org.organizationUsers = mutableListOf(orgUser)

                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)

                mockMvc.perform(get("/org/testorg/members").principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/members"))
                    .andExpect(model().attributeExists("org", "orgUsers", "currentUser"))
            }
        }

        // yona BoardApp.organizationBoards()가 Organization.getVisibleProjects(User)로 비공개 프로젝트를
        // 걸러내던 것을 대응(P0-17). 조직 게시판 목록에 비공개 프로젝트 게시글이 노출되지 않아야 한다.
        describe("GET /org/{orgName}/boards") {
            val publicProject = Project(id = 100L, name = "pub", projectScope = ProjectScope.PUBLIC, organization = org)
            val privateProject = Project(id = 101L, name = "priv", projectScope = ProjectScope.PRIVATE, organization = org)

            it("조직 비회원에게는 비공개 프로젝트를 제외한 게시글 목록만 노출해야 한다") {
                org.projects = mutableListOf(publicProject, privateProject)
                org.organizationUsers = mutableListOf()
                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                val projectsSlot = slot<List<Project>>()
                every {
                    postingRepository.findByProjectInAndKeyword(capture(projectsSlot), any(), any())
                } returns PageImpl(emptyList<Posting>())
                every {
                    postingRepository.findByProjectInAndNotice(any(), any())
                } returns emptyList()

                mockMvc.perform(get("/org/testorg/boards").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/boardList"))

                projectsSlot.captured.map { it.id } shouldBe listOf(publicProject.id)
            }

            it("조직 관리자에게는 비공개 프로젝트를 포함한 게시글 목록을 노출해야 한다") {
                org.projects = mutableListOf(publicProject, privateProject)
                every { organizationRepository.findByName("testorg") } returns Optional.of(org)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationUserRepository.findByOrganizationIdAndUserId(org.id!!, user.id!!) } returns
                    Optional.of(OrganizationUser(id = 3L, user = user, organization = org, role = roleAdmin))
                val projectsSlot = slot<List<Project>>()
                every {
                    postingRepository.findByProjectInAndKeyword(capture(projectsSlot), any(), any())
                } returns PageImpl(emptyList<Posting>())
                every {
                    postingRepository.findByProjectInAndNotice(any(), any())
                } returns emptyList()

                mockMvc.perform(get("/org/testorg/boards").principal(userAuth))
                    .andExpect(status().isOk)

                projectsSlot.captured.map { it.id }.toSet() shouldBe setOf(publicProject.id, privateProject.id)
            }
        }

        // yona OrganizationApp.java:485-488 대응 (P0-23) — HIDE_PROJECT_LISTING이 켜져 있으면
        // 누구도 전체 조직 목록을 볼 수 없다.
        describe("HIDE_PROJECT_LISTING=true일 때 GET /orgs") {
            val hiddenController = OrganizationViewController(
                organizationRepository, organizationUserRepository, userRepository, issueRepository,
                postingRepository, pullRequestRepository, organizationService, attachmentRepository,
                attachmentService, accessControl, mentionService, roleRepository, hideProjectListing = true
            )

            it("error/403 뷰를 반환해야 한다") {
                val result = hiddenController.orgList(filter = "", pageNum = 1, authentication = null, model = ExtendedModelMap())
                result shouldBe "error/403"
            }
        }

        // yona OrganizationApp.java:485-486 @GuestProhibit 대응 (P1-120). 게스트 계정은 조직 목록으로
        // 들어오지 못하고 인덱스로 리다이렉트된다(로그인하지 않은 익명 사용자는 그대로 통과 — isGuest는
        // 로그인 상태의 특수 계정 플래그이지 익명 여부가 아니다).
        describe("GET /orgs 게스트 계정 차단") {
            it("게스트 계정으로 조회하면 인덱스로 리다이렉트해야 한다") {
                val guestAuth = UsernamePasswordAuthenticationToken("guestuser", "password")
                val guestUser = User(id = 30L, loginId = "guestuser", name = "게스트", state = UserState.ACTIVE, isGuest = true)
                every { userRepository.findByLoginId("guestuser") } returns Optional.of(guestUser)

                val result = organizationViewController.orgList(
                    filter = "", pageNum = 1, authentication = guestAuth, model = ExtendedModelMap()
                )
                result shouldBe "redirect:/"
            }

            it("비로그인(익명) 사용자는 차단되지 않고 목록을 그대로 볼 수 있어야 한다") {
                every { organizationRepository.findByNameContainingIgnoreCaseOrDescrContainingIgnoreCase(any(), any(), any()) } returns
                    PageImpl(emptyList<Organization>())

                val result = organizationViewController.orgList(
                    filter = "", pageNum = 1, authentication = null, model = ExtendedModelMap()
                )
                result shouldBe "organization/list"
            }
        }

        describe("GET /orgs 추가 분기") {
            it("pageNum이 1 미만이면 error/404를 반환해야 한다") {
                val result = organizationViewController.orgList(
                    filter = "", pageNum = 0, authentication = null, model = ExtendedModelMap()
                )
                result shouldBe "error/404"
            }
        }

        describe("GET /organizations/new (createForm)") {
            it("인증 정보가 없으면 error/403을 반환해야 한다") {
                mockMvc.perform(get("/organizations/new"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            it("인증은 있으나 유저를 찾지 못하면 error/403을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(get("/organizations/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            it("정상 유저면 organization/create 뷰와 currentUser 모델을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/organizations/new").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/create"))
                    .andExpect(model().attributeExists("currentUser"))
            }
        }

        describe("POST /organizations/new 추가 분기") {
            it("인증 정보가 없으면 error/403을 반환해야 한다") {
                mockMvc.perform(post("/organizations/new").param("name", "neworg2"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))

                verify(exactly = 0) { organizationService.createOrganization(any(), any(), any()) }
            }

            it("인증은 있으나 유저를 찾지 못하면 error/403을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(post("/organizations/new").principal(userAuth).param("name", "neworg2"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/403"))
            }

            it("생성 중 예외가 발생하면 입력값을 보존한 채 organization/create를 다시 보여줘야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.createOrganization("dup", "설명", 10L) } throws
                    IllegalArgumentException("organization.name.duplicate")

                mockMvc.perform(
                    post("/organizations/new").principal(userAuth)
                        .param("name", "dup").param("descr", "설명")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/create"))
                    .andExpect(model().attribute("error", "organization.name.duplicate"))
                    .andExpect(model().attribute("name", "dup"))
                    .andExpect(model().attribute("descr", "설명"))
            }
        }

        describe("GET /organizations/{orgId}/logo (organizationLogo)") {
            it("첨부파일이 없으면 classpath의 기본 그룹 이미지를 200 OK로 반환해야 한다") {
                // 컨트롤러의 디폴트 이미지 경로가 특정 개발자 로컬 절대경로(/Users/mzc01-search5/...)로
                // 하드코딩돼 있던 실버그를 커버리지 감사 중 발견해 ClassPathResource로 수정(TASK-0270).
                every {
                    attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ORGANIZATION, "700")
                } returns emptyList()

                val result = organizationViewController.organizationLogo(700L)

                result.statusCode shouldBe HttpStatus.OK
                result.headers.contentType shouldBe MediaType.IMAGE_PNG
            }

            it("첨부파일이 있고 실제 파일이 존재하면 mimeType으로 200 OK를 반환해야 한다") {
                val tempFile = File.createTempFile("logo", ".png").apply { deleteOnExit() }
                val attachment = Attachment(
                    id = 1L, name = "logo.png", mimeType = "image/jpeg",
                    containerType = ResourceType.ORGANIZATION, containerId = "701"
                )
                every {
                    attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ORGANIZATION, "701")
                } returns listOf(attachment)
                every { attachmentService.getFile(attachment) } returns tempFile

                val result = organizationViewController.organizationLogo(701L)

                result.statusCode shouldBe HttpStatus.OK
                result.headers.contentType shouldBe MediaType.parseMediaType("image/jpeg")
            }

            it("mimeType이 없으면 기본값 image/png로 200 OK를 반환해야 한다") {
                val tempFile = File.createTempFile("logo", ".png").apply { deleteOnExit() }
                val attachment = Attachment(
                    id = 2L, name = "logo.png", mimeType = null,
                    containerType = ResourceType.ORGANIZATION, containerId = "702"
                )
                every {
                    attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ORGANIZATION, "702")
                } returns listOf(attachment)
                every { attachmentService.getFile(attachment) } returns tempFile

                val result = organizationViewController.organizationLogo(702L)

                result.statusCode shouldBe HttpStatus.OK
                result.headers.contentType shouldBe MediaType.IMAGE_PNG
            }

            it("첨부파일은 있지만 실제 파일이 존재하지 않으면 404를 반환해야 한다") {
                val missingFile = File("/nonexistent/path/${System.nanoTime()}.png")
                val attachment = Attachment(
                    id = 3L, name = "logo.png", mimeType = "image/png",
                    containerType = ResourceType.ORGANIZATION, containerId = "703"
                )
                every {
                    attachmentRepository.findByContainerTypeAndContainerId(ResourceType.ORGANIZATION, "703")
                } returns listOf(attachment)
                every { attachmentService.getFile(attachment) } returns missingFile

                val result = organizationViewController.organizationLogo(703L)

                result.statusCode shouldBe HttpStatus.NOT_FOUND
            }
        }

        describe("POST /org/{orgName}/enroll (enroll)") {
            it("인증 정보가 없으면 401 Unauthorized를 반환해야 한다") {
                val request = mockk<HttpServletRequest>(relaxed = true)

                val result = organizationViewController.enroll("someorg", null, request)

                result.statusCode shouldBe HttpStatus.UNAUTHORIZED
                result.body shouldBe mapOf("error" to "Unauthorized")
            }

            it("인증은 있으나 유저를 찾지 못하면 401 Unauthorized를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()
                val request = mockk<HttpServletRequest>(relaxed = true)

                val result = organizationViewController.enroll("someorg", userAuth, request)

                result.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }

            it("Host 헤더가 있으면 해당 헤더로 statusMonitorUrl을 만들고 202 Accepted를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.enroll("someorg", 10L) } returns Unit
                val request = mockk<HttpServletRequest>()
                every { request.getHeader("Host") } returns "example.com"

                val result = organizationViewController.enroll("someorg", userAuth, request)

                result.statusCode shouldBe HttpStatus.ACCEPTED
                result.body?.get("statusMonitorUrl") shouldBe "http://example.com/organizations/someorg"
            }

            it("Host 헤더가 없으면 기본값 localhost:8080으로 statusMonitorUrl을 만들어야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.enroll("someorg", 10L) } returns Unit
                val request = mockk<HttpServletRequest>()
                every { request.getHeader("Host") } returns null

                val result = organizationViewController.enroll("someorg", userAuth, request)

                result.statusCode shouldBe HttpStatus.ACCEPTED
                result.body?.get("statusMonitorUrl") shouldBe "http://localhost:8080/organizations/someorg"
            }

            it("가입 신청 중 예외(메시지 있음)가 발생하면 400과 예외 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.enroll("someorg", 10L) } throws IllegalStateException("이미 가입된 조직입니다")
                val request = mockk<HttpServletRequest>(relaxed = true)

                val result = organizationViewController.enroll("someorg", userAuth, request)

                result.statusCode shouldBe HttpStatus.BAD_REQUEST
                result.body shouldBe mapOf("error" to "이미 가입된 조직입니다")
            }

            it("가입 신청 중 예외(메시지 없음)가 발생하면 400과 기본 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.enroll("someorg", 10L) } throws RuntimeException()
                val request = mockk<HttpServletRequest>(relaxed = true)

                val result = organizationViewController.enroll("someorg", userAuth, request)

                result.statusCode shouldBe HttpStatus.BAD_REQUEST
                result.body shouldBe mapOf("error" to "Failed to enroll")
            }
        }

        describe("POST /org/{orgName}/cancel/enroll (cancelEnroll)") {
            it("인증 정보가 없으면 401 Unauthorized를 반환해야 한다") {
                val request = mockk<HttpServletRequest>(relaxed = true)

                val result = organizationViewController.cancelEnroll("someorg", null, request)

                result.statusCode shouldBe HttpStatus.UNAUTHORIZED
                result.body shouldBe mapOf("error" to "Unauthorized")
            }

            it("인증은 있으나 유저를 찾지 못하면 401 Unauthorized를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()
                val request = mockk<HttpServletRequest>(relaxed = true)

                val result = organizationViewController.cancelEnroll("someorg", userAuth, request)

                result.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }

            it("Host 헤더가 있으면 해당 헤더로 statusMonitorUrl을 만들고 202 Accepted를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.cancelEnroll("someorg", 10L) } returns Unit
                val request = mockk<HttpServletRequest>()
                every { request.getHeader("Host") } returns "example.com"

                val result = organizationViewController.cancelEnroll("someorg", userAuth, request)

                result.statusCode shouldBe HttpStatus.ACCEPTED
                result.body?.get("statusMonitorUrl") shouldBe "http://example.com/organizations/someorg"
            }

            it("Host 헤더가 없으면 기본값 localhost:8080으로 statusMonitorUrl을 만들어야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.cancelEnroll("someorg", 10L) } returns Unit
                val request = mockk<HttpServletRequest>()
                every { request.getHeader("Host") } returns null

                val result = organizationViewController.cancelEnroll("someorg", userAuth, request)

                result.statusCode shouldBe HttpStatus.ACCEPTED
                result.body?.get("statusMonitorUrl") shouldBe "http://localhost:8080/organizations/someorg"
            }

            it("가입 취소 중 예외(메시지 있음)가 발생하면 400과 예외 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.cancelEnroll("someorg", 10L) } throws IllegalStateException("취소할 수 없습니다")
                val request = mockk<HttpServletRequest>(relaxed = true)

                val result = organizationViewController.cancelEnroll("someorg", userAuth, request)

                result.statusCode shouldBe HttpStatus.BAD_REQUEST
                result.body shouldBe mapOf("error" to "취소할 수 없습니다")
            }

            it("가입 취소 중 예외(메시지 없음)가 발생하면 400과 기본 메시지를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.cancelEnroll("someorg", 10L) } throws RuntimeException()
                val request = mockk<HttpServletRequest>(relaxed = true)

                val result = organizationViewController.cancelEnroll("someorg", userAuth, request)

                result.statusCode shouldBe HttpStatus.BAD_REQUEST
                result.body shouldBe mapOf("error" to "Failed to cancel enroll")
            }
        }

        describe("DELETE /org/{orgName} (deleteOrganization)") {
            it("조직이 존재하지 않으면 404를 반환해야 한다") {
                every { organizationRepository.findByName("nodelorg") } returns Optional.empty()

                val result = organizationViewController.deleteOrganization("nodelorg", userAuth)

                result.statusCode shouldBe HttpStatus.NOT_FOUND
                result.body shouldBe mapOf("errorMsg" to "Organization not found")
            }

            it("인증 정보가 없으면 401을 반환해야 한다") {
                val orgDel = Organization(id = 503L, name = "delorgctrl")
                every { organizationRepository.findByName("delorgctrl") } returns Optional.of(orgDel)

                val result = organizationViewController.deleteOrganization("delorgctrl", null)

                result.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }

            it("유저를 찾지 못하면 401을 반환해야 한다") {
                val orgDel = Organization(id = 503L, name = "delorgctrl")
                every { organizationRepository.findByName("delorgctrl") } returns Optional.of(orgDel)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val result = organizationViewController.deleteOrganization("delorgctrl", userAuth)

                result.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }

            it("관리자도 사이트매니저도 아니면 403을 반환해야 한다") {
                val orgDel = Organization(id = 503L, name = "delorgctrl")
                orgDel.organizationUsers = mutableListOf(OrganizationUser(id = 90L, user = user, organization = orgDel, role = roleMember))
                every { organizationRepository.findByName("delorgctrl") } returns Optional.of(orgDel)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                val result = organizationViewController.deleteOrganization("delorgctrl", userAuth)

                result.statusCode shouldBe HttpStatus.FORBIDDEN
                verify(exactly = 0) { organizationService.deleteOrganization(any(), any()) }
            }

            it("조직 관리자면 삭제에 성공해야 한다") {
                val orgDel = Organization(id = 503L, name = "delorgctrl")
                orgDel.organizationUsers = mutableListOf(OrganizationUser(id = 91L, user = user, organization = orgDel, role = roleAdmin))
                every { organizationRepository.findByName("delorgctrl") } returns Optional.of(orgDel)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.deleteOrganization(503L, 10L) } returns Unit

                val result = organizationViewController.deleteOrganization("delorgctrl", userAuth)

                result.statusCode shouldBe HttpStatus.OK
                result.body shouldBe mapOf("status" to "success")
                verify(exactly = 1) { organizationService.deleteOrganization(503L, 10L) }
            }

            it("조직 관리자가 아니어도 사이트매니저면 삭제에 성공해야 한다") {
                val orgDel = Organization(id = 503L, name = "delorgctrl")
                orgDel.organizationUsers = mutableListOf()
                every { organizationRepository.findByName("delorgctrl") } returns Optional.of(orgDel)
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { organizationService.deleteOrganization(503L, 20L) } returns Unit

                val result = organizationViewController.deleteOrganization("delorgctrl", adminAuth)

                result.statusCode shouldBe HttpStatus.OK
            }

            it("삭제 중 예외(메시지 있음)가 발생하면 400과 예외 메시지를 반환해야 한다") {
                val orgDel = Organization(id = 503L, name = "delorgctrl")
                orgDel.organizationUsers = mutableListOf(OrganizationUser(id = 92L, user = user, organization = orgDel, role = roleAdmin))
                every { organizationRepository.findByName("delorgctrl") } returns Optional.of(orgDel)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.deleteOrganization(503L, 10L) } throws IllegalStateException("삭제할 수 없습니다")

                val result = organizationViewController.deleteOrganization("delorgctrl", userAuth)

                result.statusCode shouldBe HttpStatus.BAD_REQUEST
                result.body shouldBe mapOf("errorMsg" to "삭제할 수 없습니다")
            }

            it("삭제 중 예외(메시지 없음)가 발생하면 400과 기본 메시지를 반환해야 한다") {
                val orgDel = Organization(id = 503L, name = "delorgctrl")
                orgDel.organizationUsers = mutableListOf(OrganizationUser(id = 93L, user = user, organization = orgDel, role = roleAdmin))
                every { organizationRepository.findByName("delorgctrl") } returns Optional.of(orgDel)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.deleteOrganization(503L, 10L) } throws RuntimeException()

                val result = organizationViewController.deleteOrganization("delorgctrl", userAuth)

                result.statusCode shouldBe HttpStatus.BAD_REQUEST
                result.body shouldBe mapOf("errorMsg" to "Failed to delete organization")
            }
        }

        describe("DELETE /organizations/{orgName}/leave (leave) 추가 분기") {
            it("조직이 존재하지 않으면 404를 반환해야 한다") {
                every { organizationRepository.findByName("noleaveorg") } returns Optional.empty()

                val result = organizationViewController.leave("noleaveorg", userAuth)

                result.statusCode shouldBe HttpStatus.NOT_FOUND
                result.body shouldBe mapOf("errorMsg" to "organization.member.unknownOrganization")
            }

            it("인증 정보가 없으면 401을 반환해야 한다") {
                val orgLeave = Organization(id = 504L, name = "leaveorg")
                every { organizationRepository.findByName("leaveorg") } returns Optional.of(orgLeave)

                val result = organizationViewController.leave("leaveorg", null)

                result.statusCode shouldBe HttpStatus.UNAUTHORIZED
                result.body shouldBe mapOf("errorMsg" to "unauthorized")
            }

            it("유저를 찾지 못하면 401을 반환해야 한다") {
                val orgLeave = Organization(id = 504L, name = "leaveorg")
                every { organizationRepository.findByName("leaveorg") } returns Optional.of(orgLeave)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                val result = organizationViewController.leave("leaveorg", userAuth)

                result.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }

            it("정상적으로 탈퇴하면 200 OK와 리다이렉트 위치를 반환해야 한다") {
                val orgLeave = Organization(id = 504L, name = "leaveorg")
                every { organizationRepository.findByName("leaveorg") } returns Optional.of(orgLeave)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.leaveOrganization(504L, 10L) } returns Unit

                val result = organizationViewController.leave("leaveorg", userAuth)

                result.statusCode shouldBe HttpStatus.OK
                result.body shouldBe mapOf("location" to "/organizations/leaveorg")
            }

            it("탈퇴 불가 상태(메시지 있음)면 403과 예외 메시지를 반환해야 한다") {
                val orgLeave = Organization(id = 504L, name = "leaveorg")
                every { organizationRepository.findByName("leaveorg") } returns Optional.of(orgLeave)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.leaveOrganization(504L, 10L) } throws
                    IllegalStateException("organization.member.atLeastOneAdmin")

                val result = organizationViewController.leave("leaveorg", userAuth)

                result.statusCode shouldBe HttpStatus.FORBIDDEN
                result.body shouldBe mapOf("errorMsg" to "organization.member.atLeastOneAdmin")
            }

            it("탈퇴 불가 상태(메시지 없음)면 403과 기본 메시지를 반환해야 한다") {
                val orgLeave = Organization(id = 504L, name = "leaveorg")
                every { organizationRepository.findByName("leaveorg") } returns Optional.of(orgLeave)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.leaveOrganization(504L, 10L) } throws IllegalStateException()

                val result = organizationViewController.leave("leaveorg", userAuth)

                result.statusCode shouldBe HttpStatus.FORBIDDEN
                result.body shouldBe mapOf("errorMsg" to "organization.member.leave.unknownerror")
            }

            it("일반 예외(메시지 있음)면 400과 예외 메시지를 반환해야 한다") {
                val orgLeave = Organization(id = 504L, name = "leaveorg")
                every { organizationRepository.findByName("leaveorg") } returns Optional.of(orgLeave)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.leaveOrganization(504L, 10L) } throws RuntimeException("DB 오류")

                val result = organizationViewController.leave("leaveorg", userAuth)

                result.statusCode shouldBe HttpStatus.BAD_REQUEST
                result.body shouldBe mapOf("errorMsg" to "DB 오류")
            }

            it("일반 예외(메시지 없음)면 400과 기본 메시지를 반환해야 한다") {
                val orgLeave = Organization(id = 504L, name = "leaveorg")
                every { organizationRepository.findByName("leaveorg") } returns Optional.of(orgLeave)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.leaveOrganization(504L, 10L) } throws RuntimeException()

                val result = organizationViewController.leave("leaveorg", userAuth)

                result.statusCode shouldBe HttpStatus.BAD_REQUEST
                result.body shouldBe mapOf("errorMsg" to "organization.member.leave.unknownerror")
            }
        }

        describe("POST /org/{orgName}/setting (updateOrganization) 추가 분기") {
            it("조직이 없으면 error/404를 반환해야 한다") {
                every { organizationRepository.findByName("noupdorg") } returns Optional.empty()

                mockMvc.perform(
                    multipart("/organizations/noupdorg/setting").principal(userAuth).param("name", "noupdorg")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("인증 정보가 없으면 로그인 화면으로 리다이렉트해야 한다") {
                val orgUpd = Organization(id = 505L, name = "updorg")
                every { organizationRepository.findByName("updorg") } returns Optional.of(orgUpd)

                mockMvc.perform(multipart("/organizations/updorg/setting").param("name", "updorg"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("유저를 찾지 못하면 로그인 화면으로 리다이렉트해야 한다") {
                val orgUpd = Organization(id = 505L, name = "updorg")
                every { organizationRepository.findByName("updorg") } returns Optional.of(orgUpd)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(multipart("/organizations/updorg/setting").principal(userAuth).param("name", "updorg"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("관리자도 사이트매니저도 아니면 error/forbidden_organization을 반환해야 한다") {
                val orgUpd = Organization(id = 505L, name = "updorg")
                orgUpd.organizationUsers = mutableListOf(OrganizationUser(id = 94L, user = user, organization = orgUpd, role = roleMember))
                every { organizationRepository.findByName("updorg") } returns Optional.of(orgUpd)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(multipart("/organizations/updorg/setting").principal(userAuth).param("name", "updorg"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden_organization"))

                verify(exactly = 0) { organizationService.updateOrganizationSettings(any(), any(), any(), any()) }
            }

            it("조직 관리자가 아니어도 사이트매니저면 설정을 변경해야 한다") {
                val orgUpd = Organization(id = 505L, name = "updorg")
                orgUpd.organizationUsers = mutableListOf()
                every { organizationRepository.findByName("updorg") } returns Optional.of(orgUpd)
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)
                every { organizationService.updateOrganizationSettings(505L, "updorg", "", 20L) } returns Unit

                mockMvc.perform(multipart("/organizations/updorg/setting").principal(adminAuth).param("name", "updorg"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/organizations/updorg"))
            }

            it("설정 변경 중 예외가 발생하면 organization/setting을 에러와 함께 다시 보여줘야 한다") {
                val orgUpd = Organization(id = 505L, name = "updorg")
                orgUpd.organizationUsers = mutableListOf(OrganizationUser(id = 95L, user = user, organization = orgUpd, role = roleAdmin))
                every { organizationRepository.findByName("updorg") } returns Optional.of(orgUpd)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { organizationService.updateOrganizationSettings(505L, "dupname", "", 10L) } throws
                    IllegalArgumentException("organization.name.duplicate")

                mockMvc.perform(
                    multipart("/organizations/updorg/setting").principal(userAuth).param("name", "dupname")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/setting"))
                    .andExpect(model().attribute("error", "organization.name.duplicate"))
            }
        }

        describe("GET /org/{orgName}/deleteForm (deleteForm) 추가 분기") {
            it("조직이 없으면 error/404를 반환해야 한다") {
                every { organizationRepository.findByName("nodelformorg") } returns Optional.empty()

                mockMvc.perform(get("/org/nodelformorg/deleteForm").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("인증 정보가 없으면 로그인 화면으로 리다이렉트해야 한다") {
                val orgDelForm = Organization(id = 506L, name = "delformorg")
                every { organizationRepository.findByName("delformorg") } returns Optional.of(orgDelForm)

                mockMvc.perform(get("/org/delformorg/deleteForm"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("유저를 찾지 못하면 로그인 화면으로 리다이렉트해야 한다") {
                val orgDelForm = Organization(id = 506L, name = "delformorg")
                every { organizationRepository.findByName("delformorg") } returns Optional.of(orgDelForm)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(get("/org/delformorg/deleteForm").principal(userAuth))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("관리자도 사이트매니저도 아니면 error/forbidden_organization을 반환해야 한다") {
                val orgDelForm = Organization(id = 506L, name = "delformorg")
                orgDelForm.organizationUsers = mutableListOf(OrganizationUser(id = 96L, user = user, organization = orgDelForm, role = roleMember))
                every { organizationRepository.findByName("delformorg") } returns Optional.of(orgDelForm)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/org/delformorg/deleteForm").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden_organization"))
            }

            it("조직 관리자면 organization/delete 뷰를 반환해야 한다") {
                val orgDelForm = Organization(id = 506L, name = "delformorg")
                orgDelForm.organizationUsers = mutableListOf(OrganizationUser(id = 97L, user = user, organization = orgDelForm, role = roleAdmin))
                every { organizationRepository.findByName("delformorg") } returns Optional.of(orgDelForm)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/org/delformorg/deleteForm").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/delete"))
            }

            it("조직 관리자가 아니어도 사이트매니저면 organization/delete 뷰를 반환해야 한다") {
                val orgDelForm = Organization(id = 506L, name = "delformorg")
                orgDelForm.organizationUsers = mutableListOf()
                every { organizationRepository.findByName("delformorg") } returns Optional.of(orgDelForm)
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)

                mockMvc.perform(get("/org/delformorg/deleteForm").principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/delete"))
            }
        }

        describe("GET /org/{orgName}/settingform (settingForm) 추가 분기") {
            it("조직이 없으면 error/404를 반환해야 한다") {
                every { organizationRepository.findByName("nosettingorg") } returns Optional.empty()

                mockMvc.perform(get("/org/nosettingorg/settingform").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("인증 정보가 없으면 로그인 화면으로 리다이렉트해야 한다") {
                val orgSetting = Organization(id = 507L, name = "settingorg2")
                every { organizationRepository.findByName("settingorg2") } returns Optional.of(orgSetting)

                mockMvc.perform(get("/org/settingorg2/settingform"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("유저를 찾지 못하면 로그인 화면으로 리다이렉트해야 한다") {
                val orgSetting = Organization(id = 507L, name = "settingorg2")
                every { organizationRepository.findByName("settingorg2") } returns Optional.of(orgSetting)
                every { userRepository.findByLoginId("testuser") } returns Optional.empty()

                mockMvc.perform(get("/org/settingorg2/settingform").principal(userAuth))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("관리자도 사이트매니저도 아니면 error/forbidden_organization을 반환해야 한다") {
                val orgSetting = Organization(id = 507L, name = "settingorg2")
                orgSetting.organizationUsers = mutableListOf(OrganizationUser(id = 98L, user = user, organization = orgSetting, role = roleMember))
                every { organizationRepository.findByName("settingorg2") } returns Optional.of(orgSetting)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/org/settingorg2/settingform").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden_organization"))
            }

            it("조직 관리자가 아니어도 사이트매니저면 organization/setting 뷰를 반환해야 한다") {
                val orgSetting = Organization(id = 507L, name = "settingorg2")
                orgSetting.organizationUsers = mutableListOf()
                every { organizationRepository.findByName("settingorg2") } returns Optional.of(orgSetting)
                every { userRepository.findByLoginId("admin") } returns Optional.of(siteManager)

                mockMvc.perform(get("/org/settingorg2/settingform").principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/setting"))
            }
        }

        describe("GET /org/{orgName} 추가 분기") {
            it("비로그인 사용자가 조회하면 isGuest/isEnrolled가 모두 false여야 한다") {
                val orgHome = Organization(id = 508L, name = "homeorg2")
                every { organizationRepository.findByName("homeorg2") } returns Optional.of(orgHome)

                mockMvc.perform(get("/org/homeorg2"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/view"))
                    .andExpect(model().attribute("isGuest", false))
                    .andExpect(model().attribute("isEnrolled", false))
            }

            it("가입 신청 중(enrolledUsers)인 유저면 isEnrolled가 true여야 한다") {
                val orgHome = Organization(id = 508L, name = "homeorg2")
                orgHome.organizationUsers = mutableListOf()
                orgHome.enrolledUsers = mutableListOf(user)
                every { organizationRepository.findByName("homeorg2") } returns Optional.of(orgHome)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/org/homeorg2").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("isGuest", true))
                    .andExpect(model().attribute("isEnrolled", true))
            }
        }

        describe("GET /org/{orgName}/members 추가 분기") {
            it("비로그인 사용자가 접근하면 컨텍스트 인지형 403을 반환해야 한다") {
                val orgMembers = Organization(id = 509L, name = "membersorg2")
                orgMembers.organizationUsers = mutableListOf()
                every { organizationRepository.findByName("membersorg2") } returns Optional.of(orgMembers)

                mockMvc.perform(get("/org/membersorg2/members"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/forbidden_organization"))
            }
        }

        describe("GET /org/{orgName}/boards 추가 분기") {
            it("조직이 없으면 error/404를 반환해야 한다") {
                every { organizationRepository.findByName("noboardsorg") } returns Optional.empty()

                mockMvc.perform(get("/org/noboardsorg/boards"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("보이는 프로젝트가 하나도 없으면 빈 페이지를 반환하고 게시글 조회를 호출하지 않아야 한다") {
                val orgBoards = Organization(id = 510L, name = "boardsorg2")
                orgBoards.projects = mutableListOf()
                orgBoards.organizationUsers = mutableListOf()
                every { organizationRepository.findByName("boardsorg2") } returns Optional.of(orgBoards)

                mockMvc.perform(get("/org/boardsorg2/boards"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/boardList"))

                verify(exactly = 0) { postingRepository.findByProjectInAndKeyword(any(), any(), any()) }
                verify(exactly = 0) { postingRepository.findByProjectInAndNotice(any(), any()) }
            }
        }

        describe("GET /org/{orgName}/issues 추가 분기") {
            it("조직이 없으면 error/404를 반환해야 한다") {
                every { organizationRepository.findByName("noissuesorg") } returns Optional.empty()

                mockMvc.perform(get("/org/noissuesorg/issues"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("보이는 프로젝트가 없으면 빈 페이지를 반환하고 이슈 검색을 호출하지 않아야 한다") {
                val orgIssues = Organization(id = 511L, name = "issuesorg")
                orgIssues.projects = mutableListOf()
                orgIssues.organizationUsers = mutableListOf()
                every { organizationRepository.findByName("issuesorg") } returns Optional.of(orgIssues)
                every { issueRepository.countByProjectInAndState(any(), any()) } returns 0L

                mockMvc.perform(get("/org/issuesorg/issues"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/issueList"))

                verify(exactly = 0) { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) }
            }

            it("state=closed로 조회하면 닫힌 이슈 상태로 검색해야 한다") {
                val orgIssues = Organization(id = 511L, name = "issuesorg")
                val proj = Project(id = 900L, name = "p1", projectScope = ProjectScope.PUBLIC, organization = orgIssues)
                orgIssues.projects = mutableListOf(proj)
                every { organizationRepository.findByName("issuesorg") } returns Optional.of(orgIssues)
                every { issueRepository.countByProjectInAndState(any(), any()) } returns 0L
                every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList<Issue>())

                mockMvc.perform(get("/org/issuesorg/issues").param("state", "closed"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("currentState", "closed"))
            }

            it("orderDir=asc로 조회하면 오름차순 정렬로 검색해야 한다") {
                val orgIssues = Organization(id = 511L, name = "issuesorg")
                val proj = Project(id = 901L, name = "p2", projectScope = ProjectScope.PUBLIC, organization = orgIssues)
                orgIssues.projects = mutableListOf(proj)
                every { organizationRepository.findByName("issuesorg") } returns Optional.of(orgIssues)
                every { issueRepository.countByProjectInAndState(any(), any()) } returns 0L
                every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList<Issue>())

                mockMvc.perform(get("/org/issuesorg/issues").param("orderDir", "asc"))
                    .andExpect(status().isOk)
            }

            it("mentionId가 지정되면 멘션된 이슈 ID를 조회해야 한다") {
                val orgIssues = Organization(id = 511L, name = "issuesorg")
                val proj = Project(id = 902L, name = "p3", projectScope = ProjectScope.PUBLIC, organization = orgIssues)
                orgIssues.projects = mutableListOf(proj)
                every { organizationRepository.findByName("issuesorg") } returns Optional.of(orgIssues)
                every { issueRepository.countByProjectInAndState(any(), any()) } returns 0L
                every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList<Issue>())
                every { mentionService.getMentioningIssueIds(10L) } returns emptyList()

                mockMvc.perform(get("/org/issuesorg/issues").param("mentionId", "10"))
                    .andExpect(status().isOk)

                verify(exactly = 1) { mentionService.getMentioningIssueIds(10L) }
            }

            it("projectNames[] 필터를 지정하면 해당 프로젝트로만 좁혀서 검색해야 한다") {
                val orgIssues = Organization(id = 511L, name = "issuesorg")
                val proj1 = Project(id = 903L, name = "pa", projectScope = ProjectScope.PUBLIC, organization = orgIssues)
                val proj2 = Project(id = 904L, name = "pb", projectScope = ProjectScope.PUBLIC, organization = orgIssues)
                orgIssues.projects = mutableListOf(proj1, proj2)
                every { organizationRepository.findByName("issuesorg") } returns Optional.of(orgIssues)
                every { issueRepository.countByProjectInAndState(any(), any()) } returns 0L
                every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList<Issue>())

                mockMvc.perform(get("/org/issuesorg/issues").param("projectNames[]", "pa"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("selectedProjectNames", listOf("pa")))
            }
        }

        describe("GET /org/{orgName}/pullrequests (organizationPullRequests) 추가 분기") {
            it("조직이 없으면 error/404를 반환해야 한다") {
                every { organizationRepository.findByName("noprorg") } returns Optional.empty()

                mockMvc.perform(get("/org/noprorg/pullrequests"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("error/404"))
            }

            it("보이는 프로젝트가 없으면 빈 페이지를 반환해야 한다") {
                val orgPr = Organization(id = 512L, name = "prorg")
                orgPr.projects = mutableListOf()
                orgPr.organizationUsers = mutableListOf()
                every { organizationRepository.findByName("prorg") } returns Optional.of(orgPr)
                every { pullRequestRepository.countByToProjectInAndState(any(), any()) } returns 0L

                mockMvc.perform(get("/org/prorg/pullrequests"))
                    .andExpect(status().isOk)
                    .andExpect(view().name("organization/pullRequestList"))
                    .andExpect(model().attribute("category", "open"))

                verify(exactly = 0) { pullRequestRepository.searchByToProjectInAndState(any(), any(), any(), any()) }
            }

            it("category=closed로 조회하면 닫힌 PR 상태로 검색해야 한다") {
                val orgPr = Organization(id = 512L, name = "prorg")
                val proj = Project(id = 910L, name = "prp", projectScope = ProjectScope.PUBLIC, organization = orgPr)
                orgPr.projects = mutableListOf(proj)
                every { organizationRepository.findByName("prorg") } returns Optional.of(orgPr)
                every { pullRequestRepository.countByToProjectInAndState(any(), any()) } returns 0L
                every {
                    pullRequestRepository.searchByToProjectInAndState(any(), any(), any(), any())
                } returns PageImpl(emptyList<PullRequest>())

                mockMvc.perform(get("/org/prorg/pullrequests").param("category", "closed"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("category", "closed"))
            }

            it("/closedPullrequests 경로로 조회하면 요청 URI만으로도 닫힌 PR로 인식해야 한다") {
                val orgPr = Organization(id = 512L, name = "prorg")
                val proj = Project(id = 911L, name = "prp2", projectScope = ProjectScope.PUBLIC, organization = orgPr)
                orgPr.projects = mutableListOf(proj)
                every { organizationRepository.findByName("prorg") } returns Optional.of(orgPr)
                every { pullRequestRepository.countByToProjectInAndState(any(), any()) } returns 0L
                every {
                    pullRequestRepository.searchByToProjectInAndState(any(), any(), any(), any())
                } returns PageImpl(emptyList<PullRequest>())

                mockMvc.perform(get("/org/prorg/closedPullrequests"))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("category", "closed"))
            }
        }
    }
})
