package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.mention.MentionService
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserService
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import com.github.yonaprojects.yona.domain.notification.UserProjectNotificationRepository
import com.github.yonaprojects.yona.domain.user.FavoriteProjectRepository
import com.github.yonaprojects.yona.domain.user.FavoriteOrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.Runs
import io.mockk.just
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional
import io.mockk.clearMocks
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.ui.ExtendedModelMap
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.watch.Watch
import com.github.yonaprojects.yona.domain.notification.UserProjectNotification
import com.github.yonaprojects.yona.domain.user.UserState
import io.mockk.slot
import jakarta.servlet.http.HttpServletRequest
import java.security.MessageDigest
import java.util.Base64
import java.time.Instant
import java.time.temporal.ChronoUnit

class UserViewControllerSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val watchRepository = mockk<WatchRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val userProjectNotificationRepository = mockk<UserProjectNotificationRepository>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val postingRepository = mockk<PostingRepository>()
    val favoriteProjectRepository = mockk<FavoriteProjectRepository>()
    val favoriteOrganizationRepository = mockk<FavoriteOrganizationRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val userService = mockk<UserService>()
    val accessControl = mockk<AccessControl>()
    val mentionService = mockk<MentionService>(relaxed = true)
    val recentIssueService = mockk<RecentIssueService>(relaxed = true)
    val apiTokenService = mockk<com.github.yonaprojects.yona.domain.apitoken.ApiTokenService>()
    val oAuthAuthorizedAppsService = mockk<com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizedAppsService>()
    val oAuthAppRegistrationService = mockk<com.github.yonaprojects.yona.domain.oauth2server.OAuthAppRegistrationService>()
    val sshKeyService = mockk<com.github.yonaprojects.yona.domain.sshkey.SshKeyService>()
    val gpgKeyService = mockk<com.github.yonaprojects.yona.domain.gpgkey.GpgKeyService>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val passwordEncodingService = com.github.yonaprojects.yona.domain.user.PasswordEncodingService()

    val userViewController = UserViewController(
        userRepository,
        projectUserRepository,
        issueRepository,
        pullRequestRepository,
        watchRepository,
        projectRepository,
        userProjectNotificationRepository,
        attachmentRepository,
        postingRepository,
        favoriteProjectRepository,
        favoriteOrganizationRepository,
        organizationUserRepository,
        organizationRepository,
        userService,
        passwordEncodingService,
        accessControl,
        mentionService,
        recentIssueService,
        apiTokenService,
        oAuthAuthorizedAppsService,
        oAuthAppRegistrationService,
        sshKeyService,
        gpgKeyService,
        milestoneRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(userViewController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    // UserViewController.hashPassword()(private)와 동일한 알고리즘 복제. resetUserPassword()의
    // "기존 비밀번호 일치" 성공 분기를 테스트하려면 미리 올바른 해시값을 만들어 둬야 한다.
    val hashPasswordLike: (String, String) -> String = { password, salt ->
        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt.toByteArray(Charsets.UTF_8))
        var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
        for (i in 1 until 1024) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        Base64.getEncoder().encodeToString(hashed)
    }

    beforeTest {
        clearMocks(
            userRepository,
            projectUserRepository,
            issueRepository,
            userService,
            pullRequestRepository,
            watchRepository,
            projectRepository,
            userProjectNotificationRepository,
            attachmentRepository,
            postingRepository,
            favoriteProjectRepository,
            favoriteOrganizationRepository,
            organizationUserRepository,
            organizationRepository,
            accessControl,
            mentionService,
            apiTokenService,
            oAuthAuthorizedAppsService,
            oAuthAppRegistrationService,
            sshKeyService,
            gpgKeyService
        )
        every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
        every { accessControl.isAllowedToReadProject(any(), any()) } returns true
    }

    describe("UserViewController 템플릿 연동 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("GET /user/{loginId}") {
            it("200 OK와 user/view 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByUserId(10L) } returns emptyList()
                every { issueRepository.findRecentlyByUser(10L, any()) } returns emptyList()
                every { pullRequestRepository.findByContributorAndUpdatedGreaterThanEqualOrderByUpdatedDescStateAsc(user, any()) } returns emptyList()

                mockMvc.perform(get("/user/testuser").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/view"))
                    .andExpect(model().attributeExists("user", "projects", "issues", "pullRequests"))
            }
        }

        // yona Mention.getMentioningIssueIds() 대응 (P2-41) — LIKE 텍스트 검색 대신 멘션 인덱스
        // 테이블 조회 결과(이슈 id 목록)를 그대로 이슈 조회에 사용해야 한다.
        describe("GET /user/issues?mentionId=... (P2-41)") {
            it("멘션 인덱스 조회 결과 이슈 id 목록을 findMentionedByState에 그대로 전달해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { mentionService.getMentioningIssueIds(10L) } returns listOf(100L, 200L)
                every {
                    issueRepository.findMentionedByState(listOf(100L, 200L), State.OPEN, null, any())
                } returns PageImpl(emptyList())
                every { issueRepository.countMentionedByState(listOf(100L, 200L), State.OPEN) } returns 2L
                every { issueRepository.countMentionedByState(listOf(100L, 200L), State.CLOSED) } returns 0L
                every { issueRepository.countSharedByState(10L, State.OPEN) } returns 0L
                every { issueRepository.countFavoriteByState(10L, State.OPEN) } returns 0L

                mockMvc.perform(get("/user/issues").param("mentionId", "1").principal(userAuth))
                    .andExpect(status().isOk)

                verify(exactly = 1) { issueRepository.findMentionedByState(listOf(100L, 200L), State.OPEN, null, any()) }
            }

            it("멘션된 이슈가 하나도 없으면 repository를 호출하지 않고 빈 결과를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { mentionService.getMentioningIssueIds(10L) } returns emptyList()
                every { issueRepository.countSharedByState(10L, State.OPEN) } returns 0L
                every { issueRepository.countFavoriteByState(10L, State.OPEN) } returns 0L

                mockMvc.perform(get("/user/issues").param("mentionId", "1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(model().attribute("mentionCount", 0L))

                verify(exactly = 0) { issueRepository.findMentionedByState(any(), any(), any(), any()) }
                verify(exactly = 0) { issueRepository.countMentionedByState(any(), any()) }
            }
        }

        describe("GET /user/verify") {
            it("인증 코드가 유효하면 200 OK와 user/verified 뷰, loginId 모델 속성을 반환해야 한다") {
                every { userService.verifyUser("gildong", "verification-code") } returns true

                mockMvc.perform(
                    get("/user/verify")
                        .param("loginId", "gildong")
                        .param("code", "verification-code")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/verified"))
                    .andExpect(model().attribute("loginId", "gildong"))
            }

            it("인증 코드가 유효하지 않으면 404와 error/404 뷰를 반환해야 한다") {
                every { userService.verifyUser("gildong", "wrong-code") } returns false

                mockMvc.perform(
                    get("/user/verify")
                        .param("loginId", "gildong")
                        .param("code", "wrong-code")
                )
                    .andExpect(status().isNotFound)
                    .andExpect(view().name("error/404"))
            }
        }

        describe("GET /user/emails/{emailId}/confirm") {
            it("토큰이 유효하면 /user/editform으로 리다이렉트해야 한다") {
                every { userService.confirmEmail(10L, "test-token-50") } returns true

                mockMvc.perform(get("/user/emails/10/confirm").param("token", "test-token-50"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/user/editform"))
            }

            it("토큰이 유효하지 않으면 404와 error/404 뷰를 반환해야 한다") {
                every { userService.confirmEmail(10L, "bad-token") } returns false

                mockMvc.perform(get("/user/emails/10/confirm").param("token", "bad-token"))
                    .andExpect(status().isNotFound)
                    .andExpect(view().name("error/404"))
            }
        }

        describe("GET /user/editform") {
            it("로그인된 사용자라면 200 OK와 user/edit 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/user/editform").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/edit"))
                    .andExpect(model().attributeExists("user"))
            }
        }

        describe("GET /user/editform/emails") {
            it("로그인된 사용자라면 200 OK와 user/edit_emails 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)

                mockMvc.perform(get("/user/editform/emails").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/edit_emails"))
                    .andExpect(model().attributeExists("user", "emails"))
            }
        }

        describe("GET /user/editform/notifications") {
            it("로그인된 사용자라면 200 OK와 user/edit_notifications 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { watchRepository.findByUserAndResourceType(user, any()) } returns emptyList()

                mockMvc.perform(get("/user/editform/notifications").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("user/edit_notifications"))
                    .andExpect(model().attributeExists("user", "projects", "notiTypes", "notiMap", "notiTypeDescriptions"))
            }
        }
    }

    // yona UserApp.java:752 "!HIDE_PROJECT_LISTING || !currentUser().isAnonymous()" 대응 (P0-23).
    describe("HIDE_PROJECT_LISTING=true일 때 GET /user/{loginId}") {
        val hiddenController = UserViewController(
            userRepository, projectUserRepository, issueRepository, pullRequestRepository, watchRepository,
            projectRepository, userProjectNotificationRepository, attachmentRepository, postingRepository,
            favoriteProjectRepository, favoriteOrganizationRepository, organizationUserRepository,
            organizationRepository, userService, passwordEncodingService, accessControl, mentionService, recentIssueService,
            apiTokenService, oAuthAuthorizedAppsService, oAuthAppRegistrationService, sshKeyService, gpgKeyService,
            milestoneRepository, hideProjectListing = true
        )
        val model = ExtendedModelMap()

        it("비로그인 방문자에게는 프로젝트/이슈/PR 목록이 비어 있어야 한다") {
            val viewedUser = User(id = 20L, loginId = "viewed", name = "대상유저")
            every { userRepository.findByLoginId("viewed") } returns Optional.of(viewedUser)

            hiddenController.userProfile(loginId = "viewed", daysAgo = 14, selected = "issues", authentication = null, model = model)

            model.getAttribute("projects") shouldBe emptyList<Any>()
            model.getAttribute("issues") shouldBe emptyList<Any>()
            model.getAttribute("pullRequests") shouldBe emptyList<Any>()
        }

        it("로그인한 방문자에게는 영향이 없어야 한다") {
            val viewedUser = User(id = 20L, loginId = "viewed", name = "대상유저")
            val viewer = User(id = 30L, loginId = "viewer", name = "방문자")
            val viewerAuth = UsernamePasswordAuthenticationToken("viewer", "password")
            every { userRepository.findByLoginId("viewed") } returns Optional.of(viewedUser)
            every { userRepository.findByLoginId("viewer") } returns Optional.of(viewer)
            every { projectUserRepository.findByUserId(20L) } returns emptyList()
            every { issueRepository.findRecentlyByUser(20L, any()) } returns emptyList()
            every { pullRequestRepository.findByContributorAndUpdatedGreaterThanEqualOrderByUpdatedDescStateAsc(viewedUser, any()) } returns emptyList()

            val viewerModel = ExtendedModelMap()
            hiddenController.userProfile(loginId = "viewed", daysAgo = 14, selected = "issues", authentication = viewerAuth, model = viewerModel)

            viewerModel.getAttribute("currentUser") shouldBe viewer
        }
    }

    // yona UserApp.java:811-846 getAclValidatedIssues()/getAclValidatedPullRequests()/
    // collectProjects() 대응 (P0-25). 대상 사용자가 작성한 이슈/PR/소속 프로젝트 중 방문자가 [GL-controllers_UserApp-064]
    // READ 권한이 없는 것은 프로필에서 감춰져야 한다.
    describe("GET /user/{loginId} - 방문자의 프로젝트 READ 권한에 따른 필터링") {
        it("방문자가 READ 권한이 없는 프로젝트의 이슈/PR/소속 프로젝트는 감춰져야 한다") {
            val viewedUser = User(id = 20L, loginId = "viewed", name = "대상유저")
            val readableProject = Project(id = 1L, name = "readable", owner = "viewed")
            val hiddenProject = Project(id = 2L, name = "hidden", owner = "viewed")
            val readableIssue = Issue(id = 100L, title = "보이는 이슈", project = readableProject)
            val hiddenIssue = Issue(id = 101L, title = "숨겨진 이슈", project = hiddenProject)
            val readablePr = PullRequest(
                id = 200L, number = 1L, toProject = readableProject, fromProject = readableProject, contributor = viewedUser
            )
            val hiddenPr = PullRequest(
                id = 201L, number = 2L, toProject = hiddenProject, fromProject = hiddenProject, contributor = viewedUser
            )
            val memberRole = Role(id = RoleType.MEMBER.roleType)
            val readableProjectUser = ProjectUser(id = 900L, user = viewedUser, project = readableProject, role = memberRole)
            val hiddenProjectUser = ProjectUser(id = 901L, user = viewedUser, project = hiddenProject, role = memberRole)

            every { userRepository.findByLoginId("viewed") } returns Optional.of(viewedUser)
            every { projectUserRepository.findByUserId(20L) } returns listOf(readableProjectUser, hiddenProjectUser)
            every { issueRepository.findRecentlyByUser(20L, any()) } returns listOf(readableIssue, hiddenIssue)
            every { pullRequestRepository.findByContributorAndUpdatedGreaterThanEqualOrderByUpdatedDescStateAsc(viewedUser, any()) } returns listOf(readablePr, hiddenPr)
            every { accessControl.isAllowedToReadProject(null, readableProject) } returns true
            every { accessControl.isAllowedToReadProject(null, hiddenProject) } returns false

            mockMvc.perform(get("/user/viewed"))
                .andExpect(status().isOk)
                .andExpect(model().attribute("projects", listOf(readableProject)))
                .andExpect(model().attribute("issues", listOf(readableIssue)))
                .andExpect(model().attribute("pullRequests", listOf(readablePr)))
        }
    }

    // yona UserApp.java:754-759 Issue.findRecentlyIssuesByDaysAgo/PullRequest.findOpendPullRequestsByDaysAgo
    // 대응 (P2-38) — daysAgo 파라미터가 실제 쿼리에 반영되어야 한다. [GL-controllers_UserApp-060]
    describe("GET /user/{loginId}?daysAgo=... (P2-38)") {
        it("daysAgo 파라미터로 지정한 기간을 findRecentlyByUser/findByContributorAndUpdatedGreaterThanEqual...에 전달해야 한다") {
            val viewedUser = User(id = 20L, loginId = "viewed", name = "대상유저")
            every { userRepository.findByLoginId("viewed") } returns Optional.of(viewedUser)
            every { projectUserRepository.findByUserId(20L) } returns emptyList()
            val sinceSlot = slot<Instant>()
            every { issueRepository.findRecentlyByUser(20L, capture(sinceSlot)) } returns emptyList()
            every {
                pullRequestRepository.findByContributorAndUpdatedGreaterThanEqualOrderByUpdatedDescStateAsc(viewedUser, any())
            } returns emptyList()

            val before = Instant.now().minus(7, ChronoUnit.DAYS)
            mockMvc.perform(get("/user/viewed").param("daysAgo", "7"))
                .andExpect(status().isOk)
                .andExpect(model().attribute("daysAgo", 7))
            val after = Instant.now().minus(7, ChronoUnit.DAYS)

            (sinceSlot.captured >= before && sinceSlot.captured <= after) shouldBe true
        }

        it("daysAgo가 음수면 1로 보정해야 한다") {
            val viewedUser = User(id = 21L, loginId = "viewedneg", name = "대상유저2")
            every { userRepository.findByLoginId("viewedneg") } returns Optional.of(viewedUser)
            every { projectUserRepository.findByUserId(21L) } returns emptyList()
            every { issueRepository.findRecentlyByUser(21L, any()) } returns emptyList()
            every {
                pullRequestRepository.findByContributorAndUpdatedGreaterThanEqualOrderByUpdatedDescStateAsc(viewedUser, any())
            } returns emptyList()

            mockMvc.perform(get("/user/viewedneg").param("daysAgo", "-5"))
                .andExpect(status().isOk)
                .andExpect(model().attribute("daysAgo", 1))
        }
    }

    // userIssues() 커버리지 보강 — 미인증 리다이렉트, pageNum/orderDir 보정, filter LIKE 검색,
    // authorId/commenterId/favoriteId/sharerId/명시적 assigneeId 필터 각각의 조회 분기.
    describe("GET /user/issues - 추가 분기 커버리지") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        it("미인증 사용자는 로그인 폼으로 리다이렉트되어야 한다") {
            mockMvc.perform(get("/user/issues"))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/users/loginform"))
        }

        it("pageNum이 1 미만이면 0페이지(page index)로 보정해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { mentionService.getMentioningIssueIds(10L) } returns emptyList()
            every { issueRepository.countSharedByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countFavoriteByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countByAssigneeAndState(10L, State.OPEN) } returns 0L
            every { issueRepository.countByAssigneeAndState(10L, State.CLOSED) } returns 0L
            val pageableSlot = slot<Pageable>()
            every { issueRepository.findByAssigneeAndState(10L, State.OPEN, null, capture(pageableSlot)) } returns PageImpl(emptyList())

            mockMvc.perform(get("/user/issues").param("pageNum", "0").principal(userAuth))
                .andExpect(status().isOk)

            pageableSlot.captured.pageNumber shouldBe 0
        }

        it("orderDir=asc이면 오름차순 정렬을 사용해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { mentionService.getMentioningIssueIds(10L) } returns emptyList()
            every { issueRepository.countSharedByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countFavoriteByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countByAssigneeAndState(10L, State.OPEN) } returns 0L
            every { issueRepository.countByAssigneeAndState(10L, State.CLOSED) } returns 0L
            val pageableSlot = slot<Pageable>()
            every { issueRepository.findByAssigneeAndState(10L, State.OPEN, null, capture(pageableSlot)) } returns PageImpl(emptyList())

            mockMvc.perform(get("/user/issues").param("orderDir", "asc").principal(userAuth))
                .andExpect(status().isOk)

            pageableSlot.captured.sort.getOrderFor("updatedDate")?.direction shouldBe Sort.Direction.ASC
        }

        it("filter가 주어지면 LIKE 검색 패턴(%filter%)으로 조회해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { mentionService.getMentioningIssueIds(10L) } returns emptyList()
            every { issueRepository.countSharedByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countFavoriteByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countByAssigneeAndState(10L, State.OPEN) } returns 0L
            every { issueRepository.countByAssigneeAndState(10L, State.CLOSED) } returns 0L
            every { issueRepository.findByAssigneeAndState(10L, State.OPEN, "%bug%", any()) } returns PageImpl(emptyList())

            mockMvc.perform(get("/user/issues").param("filter", "bug").principal(userAuth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("filter", "bug"))

            verify(exactly = 1) { issueRepository.findByAssigneeAndState(10L, State.OPEN, "%bug%", any()) }
        }

        it("assigneeId를 명시적으로 지정하면 해당 담당자 기준으로 조회해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { mentionService.getMentioningIssueIds(10L) } returns emptyList()
            every { issueRepository.countSharedByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countFavoriteByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countByAssigneeAndState(77L, State.OPEN) } returns 0L
            every { issueRepository.countByAssigneeAndState(77L, State.CLOSED) } returns 0L
            every { issueRepository.findByAssigneeAndState(77L, State.OPEN, null, any()) } returns PageImpl(emptyList())

            mockMvc.perform(get("/user/issues").param("assigneeId", "77").principal(userAuth))
                .andExpect(status().isOk)
                .andExpect(model().attribute("assigneeId", 77L))

            verify(exactly = 1) { issueRepository.findByAssigneeAndState(77L, State.OPEN, null, any()) }
        }

        it("authorId가 지정되면 작성자 기준으로 조회해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { mentionService.getMentioningIssueIds(10L) } returns emptyList()
            every { issueRepository.countSharedByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countFavoriteByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countByAuthorIdAndState(55L, State.OPEN) } returns 0L
            every { issueRepository.countByAuthorIdAndState(55L, State.CLOSED) } returns 0L
            every { issueRepository.findByAuthorIdAndState(55L, State.OPEN, null, any()) } returns PageImpl(emptyList())

            mockMvc.perform(get("/user/issues").param("authorId", "55").principal(userAuth))
                .andExpect(status().isOk)

            verify(exactly = 1) { issueRepository.findByAuthorIdAndState(55L, State.OPEN, null, any()) }
        }

        it("commenterId가 지정되면 댓글 작성자 기준으로 조회해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { mentionService.getMentioningIssueIds(10L) } returns emptyList()
            every { issueRepository.countSharedByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countFavoriteByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countCommentedByState(66L, State.OPEN) } returns 0L
            every { issueRepository.countCommentedByState(66L, State.CLOSED) } returns 0L
            every { issueRepository.findCommentedByState(66L, State.OPEN, null, any()) } returns PageImpl(emptyList())

            mockMvc.perform(get("/user/issues").param("commenterId", "66").principal(userAuth))
                .andExpect(status().isOk)

            verify(exactly = 1) { issueRepository.findCommentedByState(66L, State.OPEN, null, any()) }
        }

        it("favoriteId가 지정되면 즐겨찾기 기준으로 조회해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { mentionService.getMentioningIssueIds(10L) } returns emptyList()
            every { issueRepository.countSharedByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countFavoriteByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countFavoriteByState(88L, State.OPEN) } returns 0L
            every { issueRepository.countFavoriteByState(88L, State.CLOSED) } returns 0L
            every { issueRepository.findFavoriteByState(88L, State.OPEN, null, any()) } returns PageImpl(emptyList())

            mockMvc.perform(get("/user/issues").param("favoriteId", "88").principal(userAuth))
                .andExpect(status().isOk)

            verify(exactly = 1) { issueRepository.findFavoriteByState(88L, State.OPEN, null, any()) }
        }

        it("sharerId가 지정되면 공유받은 이슈 기준으로 조회해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { mentionService.getMentioningIssueIds(10L) } returns emptyList()
            every { issueRepository.countSharedByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countFavoriteByState(10L, State.OPEN) } returns 0L
            every { issueRepository.countSharedByState(99L, State.OPEN) } returns 0L
            every { issueRepository.countSharedByState(99L, State.CLOSED) } returns 0L
            every { issueRepository.findSharedByState(99L, State.OPEN, null, any()) } returns PageImpl(emptyList())

            mockMvc.perform(get("/user/issues").param("sharerId", "99").principal(userAuth))
                .andExpect(status().isOk)

            verify(exactly = 1) { issueRepository.findSharedByState(99L, State.OPEN, null, any()) }
        }
    }

    // editUserProfileForm/editUserEmailsForm/editUserNotificationsForm/editUserPasswordForm/
    // editUserTokenForm 공통 "미인증 -> error/403" 분기 + editUserPasswordForm/editUserTokenForm의
    // 인증 성공 분기(기존 테스트에 없었음).
    describe("사용자 편집 화면 - 미인증/인증 분기") {
        it("editUserProfileForm은 미인증 시 error/403을 반환해야 한다") {
            userViewController.editUserProfileForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("editUserEmailsForm은 미인증 시 error/403을 반환해야 한다") {
            userViewController.editUserEmailsForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("editUserNotificationsForm은 미인증 시 error/403을 반환해야 한다") {
            userViewController.editUserNotificationsForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("editUserPasswordForm은 미인증 시 error/403, 인증 시 user/edit_password 뷰를 반환해야 한다") {
            userViewController.editUserPasswordForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"

            val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            val authedModel = ExtendedModelMap()
            val view = userViewController.editUserPasswordForm(UsernamePasswordAuthenticationToken("testuser", "password"), authedModel)
            view shouldBe "user/edit_password"
            authedModel.getAttribute("user") shouldNotBe null
        }

        it("editUserTokenForm은 미인증 시 error/403, 인증 시 user/edit_token 뷰를 반환해야 한다") {
            userViewController.editUserTokenForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"

            val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            val authedModel = ExtendedModelMap()
            val view = userViewController.editUserTokenForm(UsernamePasswordAuthenticationToken("testuser", "password"), authedModel)
            view shouldBe "user/edit_token"
            authedModel.getAttribute("user") shouldNotBe null
        }
    }

    // yona-wiki P3-02 Step6.6 — Fine-grained API 토큰 발급/관리 화면(레거시 edit_token.html과는
    // 별개). GitHub의 "Settings > Developer settings > Personal access tokens" 컨벤션대로 목록
    // (editApiTokensForm)과 발급 폼(newApiTokenForm)을 별개 페이지로 분리했다 —
    // issueApiToken(발급)은 성공 시 목록으로 리다이렉트 + 플래시 속성, 실패 시 발급 폼을 그대로
    // 다시 렌더링한다. revokeApiToken(폐기)의 미인증/성공 분기도 함께 검증한다.
    describe("GET/POST /user/editform/tokens (Fine-grained API 토큰)") {
        val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        it("editApiTokensForm은 미인증 시 error/403을 반환해야 한다") {
            userViewController.editApiTokensForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("editApiTokensForm은 인증 시 user/edit_tokens 뷰(목록)와 tokens 모델만 채워야 한다 — 발급 폼 필드는 없어야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { apiTokenService.listByOwner(loginUser) } returns emptyList()

            val model = ExtendedModelMap()
            val view = userViewController.editApiTokensForm(userAuth, model)

            view shouldBe "user/edit_tokens"
            model.getAttribute("tokens") shouldBe emptyList<Any>()
            // 목록 화면에는 더 이상 인라인 발급 폼이 없으므로 폼 전용 모델도 채우지 않는다.
            model.getAttribute("scopeGroups") shouldBe null
            model.getAttribute("candidateProjects") shouldBe null
        }

        it("newApiTokenForm은 미인증 시 error/403을 반환해야 한다") {
            userViewController.newApiTokenForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("newApiTokenForm은 인증 시 user/edit_tokens_new 뷰와 scopeGroups/candidateProjects 및 기본값 모델을 채워야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { projectUserRepository.findByUserId(10L) } returns emptyList()

            val model = ExtendedModelMap()
            val view = userViewController.newApiTokenForm(userAuth, model)

            view shouldBe "user/edit_tokens_new"
            model.getAttribute("scopeGroups") shouldNotBe null
            model.getAttribute("candidateProjects") shouldBe emptyList<Any>()
            model.getAttribute("submittedAllRepositories") shouldBe true
            model.getAttribute("submittedExpiresInDays") shouldBe 30L
        }

        it("issueApiToken은 미인증 시 error/403을 반환해야 한다") {
            val request = mockk<HttpServletRequest>(relaxed = true)
            userViewController.issueApiToken(
                name = "토큰", allRepositories = false, scopedProjectIds = null,
                expiresInDays = 30, request = request, authentication = null, model = ExtendedModelMap(),
                redirectAttributes = RedirectAttributesModelMap()
            ) shouldBe "error/403"
        }

        it("issueApiToken은 발급에 성공하면 목록으로 리다이렉트하고 issuedRawToken을 플래시 속성으로 채워야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            val issued = com.github.yonaprojects.yona.domain.apitoken.IssuedApiToken(
                apiToken = com.github.yonaprojects.yona.domain.apitoken.ApiToken(owner = loginUser, name = "CI 토큰", tokenHash = "hash"),
                rawToken = "raw-token-value"
            )
            every {
                apiTokenService.issue(loginUser, "CI 토큰", true, emptyList(), any(), 30)
            } returns issued
            val request = mockk<HttpServletRequest>(relaxed = true)
            every { request.getParameter(any()) } returns null

            val model = ExtendedModelMap()
            val redirectAttributes = RedirectAttributesModelMap()
            val view = userViewController.issueApiToken(
                name = "CI 토큰", allRepositories = true, scopedProjectIds = null,
                expiresInDays = 30, request = request, authentication = userAuth, model = model,
                redirectAttributes = redirectAttributes
            )

            view shouldBe "redirect:/user/editform/tokens"
            redirectAttributes.flashAttributes["issuedRawToken"] shouldBe "raw-token-value"
            redirectAttributes.flashAttributes["issuedTokenName"] shouldBe "CI 토큰"
        }

        it("issueApiToken은 발급이 거부되면(IllegalArgumentException) 발급 폼(edit_tokens_new)을 입력값과 함께 다시 렌더링해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { projectUserRepository.findByUserId(10L) } returns emptyList()
            every {
                apiTokenService.issue(loginUser, "", true, emptyList(), any(), 30)
            } throws IllegalArgumentException("토큰 이름은 필수입니다.")
            val request = mockk<HttpServletRequest>(relaxed = true)
            every { request.getParameter(any()) } returns null

            val model = ExtendedModelMap()
            val view = userViewController.issueApiToken(
                name = "", allRepositories = true, scopedProjectIds = null,
                expiresInDays = 30, request = request, authentication = userAuth, model = model,
                redirectAttributes = RedirectAttributesModelMap()
            )

            view shouldBe "user/edit_tokens_new"
            model.getAttribute("tokenIssueError") shouldBe "토큰 이름은 필수입니다."
            // 입력했던 값을 잃지 않아야 한다.
            model.getAttribute("submittedName") shouldBe ""
            model.getAttribute("submittedAllRepositories") shouldBe true
            model.getAttribute("submittedExpiresInDays") shouldBe 30L
        }

        it("revokeApiToken은 미인증 시 error/403을 반환해야 한다") {
            userViewController.revokeApiToken(id = 1L, authentication = null) shouldBe "error/403"
        }

        it("revokeApiToken은 인증 시 서비스에 위임하고 /user/editform/tokens로 리다이렉트해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { apiTokenService.revoke(loginUser, 5L) } just Runs

            val view = userViewController.revokeApiToken(id = 5L, authentication = userAuth)

            view shouldBe "redirect:/user/editform/tokens"
            verify(exactly = 1) { apiTokenService.revoke(loginUser, 5L) }
        }
    }

    // yona-wiki P3-03 Step3 — SSH 키 등록/관리 화면. GitHub 컨벤션대로 목록(editSshKeysForm)과
    // 등록 폼(newSshKeyForm)을 별개 페이지로 분리했다 — addSshKey(등록)는 성공 시 목록으로
    // 리다이렉트 + 플래시 성공 메시지, 실패 시 등록 폼을 입력값과 함께 다시 렌더링한다.
    describe("GET/POST /user/editform/ssh-keys (SSH 키)") {
        val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        it("editSshKeysForm은 미인증 시 error/403을 반환해야 한다") {
            userViewController.editSshKeysForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("editSshKeysForm은 인증 시 user/edit_ssh_keys 뷰(목록)와 sshKeys 모델만 채워야 한다 — 등록 폼 필드는 없어야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { sshKeyService.listByUser(loginUser) } returns emptyList()

            val model = ExtendedModelMap()
            val view = userViewController.editSshKeysForm(userAuth, model)

            view shouldBe "user/edit_ssh_keys"
            model.getAttribute("sshKeys") shouldBe emptyList<Any>()
            model.getAttribute("submittedTitle") shouldBe null
            model.getAttribute("submittedPublicKey") shouldBe null
        }

        it("newSshKeyForm은 미인증 시 error/403을 반환해야 한다") {
            userViewController.newSshKeyForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("newSshKeyForm은 인증 시 user/edit_ssh_keys_new 뷰를 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()

            val model = ExtendedModelMap()
            val view = userViewController.newSshKeyForm(userAuth, model)

            view shouldBe "user/edit_ssh_keys_new"
            model.getAttribute("submittedTitle") shouldBe ""
        }

        it("addSshKey는 미인증 시 error/403을 반환해야 한다") {
            userViewController.addSshKey(
                title = "제목", publicKey = "key", authentication = null, model = ExtendedModelMap(),
                redirectAttributes = RedirectAttributesModelMap()
            ) shouldBe "error/403"
        }

        it("addSshKey는 등록에 성공하면 목록으로 리다이렉트하고 sshKeyAdded를 플래시 속성으로 채워야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { sshKeyService.create(loginUser, "노트북", "ssh-ed25519 AAAA...") } returns
                com.github.yonaprojects.yona.domain.sshkey.SshKey(id = 1L, user = loginUser, title = "노트북")

            val redirectAttributes = RedirectAttributesModelMap()
            val view = userViewController.addSshKey(
                title = "노트북", publicKey = "ssh-ed25519 AAAA...", authentication = userAuth,
                model = ExtendedModelMap(), redirectAttributes = redirectAttributes
            )

            view shouldBe "redirect:/user/editform/ssh-keys"
            redirectAttributes.flashAttributes["sshKeyAdded"] shouldBe true
        }

        it("addSshKey는 등록이 거부되면(IllegalArgumentException) 등록 폼(edit_ssh_keys_new)을 입력값과 함께 다시 렌더링해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { sshKeyService.create(loginUser, "제목", "bad-key") } throws IllegalArgumentException("올바르지 않은 공개키입니다.")

            val model = ExtendedModelMap()
            val view = userViewController.addSshKey(
                title = "제목", publicKey = "bad-key", authentication = userAuth, model = model,
                redirectAttributes = RedirectAttributesModelMap()
            )

            view shouldBe "user/edit_ssh_keys_new"
            model.getAttribute("sshKeyError") shouldBe "올바르지 않은 공개키입니다."
            model.getAttribute("submittedTitle") shouldBe "제목"
            model.getAttribute("submittedPublicKey") shouldBe "bad-key"
        }

        it("deleteSshKey는 미인증 시 error/403을 반환해야 한다") {
            userViewController.deleteSshKey(id = 1L, authentication = null) shouldBe "error/403"
        }

        it("deleteSshKey는 인증 시 서비스에 위임하고 /user/editform/ssh-keys로 리다이렉트해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { sshKeyService.delete(loginUser, 5L) } just Runs

            val view = userViewController.deleteSshKey(id = 5L, authentication = userAuth)

            view shouldBe "redirect:/user/editform/ssh-keys"
            verify(exactly = 1) { sshKeyService.delete(loginUser, 5L) }
        }
    }

    // yona-wiki P3-03 Step7 — GPG 키 등록/관리 화면. SSH 키와 동일한 컨벤션(목록/등록 폼 분리).
    describe("GET/POST /user/editform/gpg-keys (GPG 키)") {
        val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        it("editGpgKeysForm은 미인증 시 error/403을 반환해야 한다") {
            userViewController.editGpgKeysForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("editGpgKeysForm은 인증 시 user/edit_gpg_keys 뷰(목록)와 gpgKeys 모델만 채워야 한다 — 등록 폼 필드는 없어야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { gpgKeyService.listByUser(loginUser) } returns emptyList()

            val model = ExtendedModelMap()
            val view = userViewController.editGpgKeysForm(userAuth, model)

            view shouldBe "user/edit_gpg_keys"
            model.getAttribute("gpgKeys") shouldBe emptyList<Any>()
            model.getAttribute("submittedArmoredPublicKey") shouldBe null
        }

        it("newGpgKeyForm은 미인증 시 error/403을 반환해야 한다") {
            userViewController.newGpgKeyForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("newGpgKeyForm은 인증 시 user/edit_gpg_keys_new 뷰를 반환해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()

            val model = ExtendedModelMap()
            val view = userViewController.newGpgKeyForm(userAuth, model)

            view shouldBe "user/edit_gpg_keys_new"
            model.getAttribute("submittedArmoredPublicKey") shouldBe ""
        }

        it("addGpgKey는 미인증 시 error/403을 반환해야 한다") {
            userViewController.addGpgKey(
                armoredPublicKey = "key", authentication = null, model = ExtendedModelMap(),
                redirectAttributes = RedirectAttributesModelMap()
            ) shouldBe "error/403"
        }

        it("addGpgKey는 등록에 성공하면 목록으로 리다이렉트하고 gpgKeyAdded를 플래시 속성으로 채워야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { gpgKeyService.create(loginUser, "armored-key") } returns
                com.github.yonaprojects.yona.domain.gpgkey.GpgKey(id = 1L, user = loginUser, keyId = "ABCDEF")

            val redirectAttributes = RedirectAttributesModelMap()
            val view = userViewController.addGpgKey(
                armoredPublicKey = "armored-key", authentication = userAuth,
                model = ExtendedModelMap(), redirectAttributes = redirectAttributes
            )

            view shouldBe "redirect:/user/editform/gpg-keys"
            redirectAttributes.flashAttributes["gpgKeyAdded"] shouldBe true
        }

        it("addGpgKey는 등록이 거부되면(IllegalArgumentException) 등록 폼(edit_gpg_keys_new)을 입력값과 함께 다시 렌더링해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { gpgKeyService.create(loginUser, "bad-key") } throws IllegalArgumentException("올바르지 않은 GPG 공개키입니다.")

            val model = ExtendedModelMap()
            val view = userViewController.addGpgKey(
                armoredPublicKey = "bad-key", authentication = userAuth, model = model,
                redirectAttributes = RedirectAttributesModelMap()
            )

            view shouldBe "user/edit_gpg_keys_new"
            model.getAttribute("gpgKeyError") shouldBe "올바르지 않은 GPG 공개키입니다."
            model.getAttribute("submittedArmoredPublicKey") shouldBe "bad-key"
        }

        it("deleteGpgKey는 미인증 시 error/403을 반환해야 한다") {
            userViewController.deleteGpgKey(id = 1L, authentication = null) shouldBe "error/403"
        }

        it("deleteGpgKey는 인증 시 서비스에 위임하고 /user/editform/gpg-keys로 리다이렉트해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { gpgKeyService.delete(loginUser, 5L) } just Runs

            val view = userViewController.deleteGpgKey(id = 5L, authentication = userAuth)

            view shouldBe "redirect:/user/editform/gpg-keys"
            verify(exactly = 1) { gpgKeyService.delete(loginUser, 5L) }
        }
    }

    // yona-wiki P3-07(MCP 서버) Step6 — "Authorized OAuth Apps" 화면(GitHub의 "Settings >
    // Applications > Authorized OAuth Apps"에 대응). editOAuthAuthorizedAppsForm(목록)/
    // revokeOAuthAuthorizedApp(취소) 두 엔드포인트의 미인증/성공 분기.
    describe("GET/POST /user/editform/oauth-apps (Authorized OAuth Apps)") {
        val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        it("editOAuthAuthorizedAppsForm은 미인증 시 error/403을 반환해야 한다") {
            userViewController.editOAuthAuthorizedAppsForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("editOAuthAuthorizedAppsForm은 인증 시 user/edit_oauth_apps 뷰와 authorizedApps 모델을 채워야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            val app = com.github.yonaprojects.yona.domain.oauth2server.AuthorizedAppView(
                clientId = "client-1", clientName = "Claude Code",
                scopes = listOf("issues:read", "issues:write"), dynamicallyRegistered = true
            )
            every { oAuthAuthorizedAppsService.listAuthorizedApps("testuser") } returns listOf(app)

            val model = ExtendedModelMap()
            val view = userViewController.editOAuthAuthorizedAppsForm(userAuth, model)

            view shouldBe "user/edit_oauth_apps"
            model.getAttribute("authorizedApps") shouldBe listOf(app)
        }

        it("revokeOAuthAuthorizedApp은 미인증 시 error/403을 반환해야 한다") {
            userViewController.revokeOAuthAuthorizedApp(clientId = "client-1", authentication = null) shouldBe "error/403"
        }

        it("revokeOAuthAuthorizedApp은 인증 시 서비스에 위임하고 /user/editform/oauth-apps로 리다이렉트해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { oAuthAuthorizedAppsService.revoke("testuser", "client-1") } just Runs

            val view = userViewController.revokeOAuthAuthorizedApp(clientId = "client-1", authentication = userAuth)

            view shouldBe "redirect:/user/editform/oauth-apps"
            verify(exactly = 1) { oAuthAuthorizedAppsService.revoke("testuser", "client-1") }
        }
    }

    // yona-wiki P3-17 — 사용자 셀프서비스 OAuth 앱 등록 화면(/user/editform/oauth-apps-owned).
    // [[p3-14]] 1라운드의 사이트 관리자 전용 등록(OAuthAppsAdminController)을 GitHub 컨벤션대로
    // 사용자 계정 단위 셀프서비스로 옮긴 것 — tokens/ssh-keys/gpg-keys와 동일한 목록/등록폼 분리
    // 패턴이다. 가장 중요한 테스트는 "다른 사용자가 등록한 앱을 삭제하려는 시도가 거부돼야 한다"
    // (IDOR 방지) 케이스다.
    describe("GET/POST /user/editform/oauth-apps-owned (사용자 셀프서비스 OAuth 앱 등록)") {
        val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val otherUser = User(id = 20L, loginId = "other", name = "다른사용자")
        val otherAuth = UsernamePasswordAuthenticationToken("other", "password")

        it("editOwnedOAuthAppsForm은 미인증 시 error/403을 반환해야 한다") {
            userViewController.editOwnedOAuthAppsForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("editOwnedOAuthAppsForm은 인증 시 user/edit_oauth_apps_owned 뷰(목록)와 본인 소유 앱만 채워야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            val ownApp = com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClient(
                id = "client-own", clientId = "cid-own", clientName = "내 앱",
                clientAuthenticationMethods = "none", authorizationGrantTypes = "authorization_code",
                scopes = "issues:read", ownerId = 10L
            )
            every { oAuthAppRegistrationService.listByOwner(10L) } returns listOf(ownApp)

            val model = ExtendedModelMap()
            val view = userViewController.editOwnedOAuthAppsForm(userAuth, model)

            view shouldBe "user/edit_oauth_apps_owned"
            model.getAttribute("ownedOAuthApps") shouldBe listOf(ownApp)
        }

        it("newOwnedOAuthAppForm은 미인증 시 error/403을 반환해야 한다") {
            userViewController.newOwnedOAuthAppForm(authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("newOwnedOAuthAppForm은 인증 시 user/edit_oauth_apps_owned_new 뷰와 availableScopes 모델을 채워야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { oAuthAppRegistrationService.availableScopes() } returns listOf("issues:read", "issues:write")

            val model = ExtendedModelMap()
            val view = userViewController.newOwnedOAuthAppForm(userAuth, model)

            view shouldBe "user/edit_oauth_apps_owned_new"
            model.getAttribute("availableScopes") shouldBe listOf("issues:read", "issues:write")
            model.getAttribute("submittedClientName") shouldBe ""
            model.getAttribute("submittedConfidential") shouldBe false
        }

        it("registerOwnedOAuthApp은 미인증 시 error/403을 반환해야 한다") {
            userViewController.registerOwnedOAuthApp(
                clientName = "앱", redirectUri = "https://example.com/callback", confidential = false, scopes = null,
                authentication = null, model = ExtendedModelMap(), redirectAttributes = RedirectAttributesModelMap()
            ) shouldBe "error/403"
        }

        it("registerOwnedOAuthApp은 등록에 성공하면 ownerId를 채워 등록하고, 목록으로 리다이렉트하며 registeredClientId/registeredPlainSecret을 플래시 속성으로 채워야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            val registeredClient = com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClient(
                id = "client-new", clientId = "cid-new", clientName = "My App",
                clientAuthenticationMethods = "client_secret_basic", authorizationGrantTypes = "authorization_code",
                scopes = "issues:read", ownerId = 10L
            )
            every {
                oAuthAppRegistrationService.register(
                    clientName = "My App", redirectUri = "https://example.com/callback",
                    confidential = true, scopes = listOf("issues:read"), ownerId = 10L
                )
            } returns com.github.yonaprojects.yona.domain.oauth2server.OAuthAppRegistrationService.Registered(
                client = registeredClient, plainSecret = "plain-secret-value"
            )

            val redirectAttributes = RedirectAttributesModelMap()
            val view = userViewController.registerOwnedOAuthApp(
                clientName = "My App", redirectUri = "https://example.com/callback", confidential = true,
                scopes = listOf("issues:read"), authentication = userAuth, model = ExtendedModelMap(),
                redirectAttributes = redirectAttributes
            )

            view shouldBe "redirect:/user/editform/oauth-apps-owned"
            redirectAttributes.flashAttributes["registeredClientId"] shouldBe "cid-new"
            redirectAttributes.flashAttributes["registeredPlainSecret"] shouldBe "plain-secret-value"
        }

        it("registerOwnedOAuthApp은 검증 실패(IllegalArgumentException)면 등록 폼(edit_oauth_apps_owned_new)을 입력값과 함께 다시 렌더링해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { oAuthAppRegistrationService.availableScopes() } returns listOf("issues:read")
            every {
                oAuthAppRegistrationService.register(
                    clientName = "", redirectUri = "https://example.com/callback",
                    confidential = false, scopes = null, ownerId = 10L
                )
            } throws IllegalArgumentException("앱 이름은 필수입니다.")

            val model = ExtendedModelMap()
            val view = userViewController.registerOwnedOAuthApp(
                clientName = "", redirectUri = "https://example.com/callback", confidential = false,
                scopes = null, authentication = userAuth, model = model,
                redirectAttributes = RedirectAttributesModelMap()
            )

            view shouldBe "user/edit_oauth_apps_owned_new"
            model.getAttribute("oauthAppError") shouldBe "앱 이름은 필수입니다."
            model.getAttribute("submittedClientName") shouldBe ""
            model.getAttribute("submittedRedirectUri") shouldBe "https://example.com/callback"
        }

        it("deleteOwnedOAuthApp은 미인증 시 error/403을 반환해야 한다") {
            userViewController.deleteOwnedOAuthApp(id = "client-1", authentication = null) shouldBe "error/403"
        }

        it("deleteOwnedOAuthApp은 본인 소유 앱이면 서비스에 위임해 삭제하고 목록으로 리다이렉트해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            val ownApp = com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClient(
                id = "client-own", clientId = "cid-own", clientName = "내 앱",
                clientAuthenticationMethods = "none", authorizationGrantTypes = "authorization_code",
                scopes = "issues:read", ownerId = 10L
            )
            every { oAuthAppRegistrationService.findOwned("client-own", 10L) } returns ownApp
            every { oAuthAppRegistrationService.deleteClientAndRelatedRecords(ownApp) } returns Unit

            val view = userViewController.deleteOwnedOAuthApp(id = "client-own", authentication = userAuth)

            view shouldBe "redirect:/user/editform/oauth-apps-owned"
            verify(exactly = 1) { oAuthAppRegistrationService.deleteClientAndRelatedRecords(ownApp) }
        }

        // IDOR 방지 회귀 테스트 — 이 스펙에서 가장 중요한 테스트. 다른 사용자(other, id=20)가
        // 소유한 앱(client-own, ownerId=10)을 삭제하려 시도하면 findOwned()가 소유자 불일치로
        // null을 반환하고, 컨트롤러는 실제 삭제(deleteClientAndRelatedRecords)를 절대 호출하지
        // 않은 채 403을 반환해야 한다.
        it("deleteOwnedOAuthApp은 다른 사용자가 소유한 앱을 삭제하려 하면 거부(403)해야 하고, 실제 삭제를 호출하면 안 된다(IDOR 방지)") {
            every { userRepository.findByLoginId("other") } returns Optional.of(otherUser)
            every { oAuthAppRegistrationService.findOwned("client-own", 20L) } returns null

            val view = userViewController.deleteOwnedOAuthApp(id = "client-own", authentication = otherAuth)

            view shouldBe "error/403"
            verify(exactly = 0) { oAuthAppRegistrationService.deleteClientAndRelatedRecords(any()) }
        }
    }

    // editUserNotificationsForm()의 watch.resourceId 파싱 실패/미존재 프로젝트 제외 분기와
    // 저장된 알림 설정 vs isNotifiedByDefault() 기본값(NEW_COMMENT는 false, 나머지는 true) 분기.
    describe("GET /user/editform/notifications - 분기 커버리지") {
        it("resourceId 파싱 실패/미존재 프로젝트는 제외하고, 저장된 알림 설정과 기본값을 함께 반영해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
            val project = Project(id = 1L, name = "proj1", owner = "testuser")
            val watchInvalid = Watch(id = 1L, user = loginUser, resourceType = ResourceType.PROJECT, resourceId = "not-a-number")
            val watchMissing = Watch(id = 2L, user = loginUser, resourceType = ResourceType.PROJECT, resourceId = "999")
            val watchValid = Watch(id = 3L, user = loginUser, resourceType = ResourceType.PROJECT, resourceId = "1")

            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { watchRepository.findByUserAndResourceType(loginUser, ResourceType.PROJECT) } returns listOf(watchInvalid, watchMissing, watchValid)
            every { projectRepository.findById(999L) } returns Optional.empty()
            every { projectRepository.findById(1L) } returns Optional.of(project)
            every { userProjectNotificationRepository.findByUserAndProjectAndNotificationType(loginUser, project, any()) } returns null
            every {
                userProjectNotificationRepository.findByUserAndProjectAndNotificationType(loginUser, project, EventType.NEW_ISSUE)
            } returns UserProjectNotification(user = loginUser, project = project, notificationType = EventType.NEW_ISSUE, allowed = false)

            val model = ExtendedModelMap()
            val view = userViewController.editUserNotificationsForm(UsernamePasswordAuthenticationToken("testuser", "password"), model)

            view shouldBe "user/edit_notifications"
            val projects = model.getAttribute("projects") as List<*>
            projects shouldBe listOf(project)

            @Suppress("UNCHECKED_CAST")
            val notiMap = model.getAttribute("notiMap") as Map<Long, Map<String, Boolean>>
            notiMap[1L]?.get("NEW_ISSUE") shouldBe false // 저장된 알림 설정을 그대로 사용
            notiMap[1L]?.get("NEW_COMMENT") shouldBe false // 기본값: NEW_COMMENT는 기본 비허용
            notiMap[1L]?.get("NEW_POSTING") shouldBe true // 기본값: 나머지는 기본 허용
        }
    }

    // usermenuTabContentList()의 미인증 조기 반환(모델 미설정) 분기와, 인증 시 즐겨찾기/조직/
    // 참여 프로젝트/watch(resourceId 파싱 실패·미존재 프로젝트 제외) 분기.
    describe("GET /user/usermenuTabContentList") {
        it("미인증 시에는 모델 세팅 없이 공용 뷰만 반환해야 한다") {
            val model = ExtendedModelMap()
            val view = userViewController.usermenuTabContentList(authentication = null, model = model)
            view shouldBe "common/usermenu_tab_content_list"
            model.getAttribute("currentUser") shouldBe null
        }

        it("인증 시 즐겨찾기/조직/프로젝트/방문이슈 목록을 모델에 채워야 하고 resourceId 파싱 불가/미존재 감시 프로젝트는 제외해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
            val ownedProject = Project(id = 5L, name = "owned", owner = "testuser")
            val memberRole = Role(id = RoleType.MEMBER.roleType)
            val memberProject = Project(id = 6L, name = "member-proj", owner = "otheruser")
            val projectUser = ProjectUser(id = 1L, user = loginUser, project = memberProject, role = memberRole)
            val watchValid = Watch(id = 1L, user = loginUser, resourceType = ResourceType.PROJECT, resourceId = "7")
            val watchInvalid = Watch(id = 2L, user = loginUser, resourceType = ResourceType.PROJECT, resourceId = "bad")
            val watchedProject = Project(id = 7L, name = "watched", owner = "someone")

            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { favoriteProjectRepository.findByUserId(10L) } returns emptyList()
            every { organizationUserRepository.findByUserId(10L) } returns emptyList()
            every { favoriteOrganizationRepository.findByUserId(10L) } returns emptyList()
            every { projectUserRepository.findByUserId(10L) } returns listOf(projectUser)
            every { projectRepository.findByOwner("testuser") } returns listOf(ownedProject)
            every { watchRepository.findByUserAndResourceType(loginUser, ResourceType.PROJECT) } returns listOf(watchValid, watchInvalid)
            every { projectRepository.findById(7L) } returns Optional.of(watchedProject)
            every { organizationRepository.findAll() } returns emptyList()

            val model = ExtendedModelMap()
            val view = userViewController.usermenuTabContentList(UsernamePasswordAuthenticationToken("testuser", "password"), model)

            view shouldBe "common/usermenu_tab_content_list"
            model.getAttribute("createdByMe") shouldBe listOf(ownedProject)
            model.getAttribute("watching") shouldBe listOf(watchedProject)
            model.getAttribute("joinmember") shouldBe listOf(memberProject)
        }
    }

    // userSidebar()의 미인증 리다이렉트, hash 유무에 따른 iframePath 조립, 참여 프로젝트
    // 유/무에 따른 최근 이슈 조회 분기.
    describe("GET /user/sidebar") {
        it("미인증 사용자는 로그인 폼으로 리다이렉트되어야 한다") {
            mockMvc.perform(get("/user/sidebar"))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/users/loginform"))
        }

        it("hash 파라미터가 있으면 iframePath에 #hash가 붙어야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { favoriteProjectRepository.findByUserId(10L) } returns emptyList()
            every { organizationUserRepository.findByUserId(10L) } returns emptyList()
            every { favoriteOrganizationRepository.findByUserId(10L) } returns emptyList()
            every { projectUserRepository.findByUserId(10L) } returns emptyList()
            every { projectRepository.findByOwner("testuser") } returns emptyList()
            every { watchRepository.findByUserAndResourceType(loginUser, ResourceType.PROJECT) } returns emptyList()
            every { organizationRepository.findAll() } returns emptyList()

            val model = ExtendedModelMap()
            userViewController.userSidebar(
                path = "/user/issues", hash = "comment-1",
                authentication = UsernamePasswordAuthenticationToken("testuser", "password"), model = model
            )

            model.getAttribute("iframePath") shouldBe "/user/issues#comment-1"
        }

        it("소속 프로젝트가 있으면 최근 이슈를 조회해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
            val project = Project(id = 1L, name = "proj1", owner = "testuser")
            val memberRole = Role(id = RoleType.MEMBER.roleType)
            val projectUser = ProjectUser(id = 1L, user = loginUser, project = project, role = memberRole)
            val recentIssue = Issue(id = 1L, title = "최근 이슈", project = project)

            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { favoriteProjectRepository.findByUserId(10L) } returns emptyList()
            every { organizationUserRepository.findByUserId(10L) } returns emptyList()
            every { favoriteOrganizationRepository.findByUserId(10L) } returns emptyList()
            every { projectUserRepository.findByUserId(10L) } returns listOf(projectUser)
            every { projectRepository.findByOwner("testuser") } returns emptyList()
            every { watchRepository.findByUserAndResourceType(loginUser, ResourceType.PROJECT) } returns emptyList()
            every { organizationRepository.findAll() } returns emptyList()
            every { issueRepository.findByProjectIn(listOf(project), any()) } returns PageImpl(listOf(recentIssue))

            val model = ExtendedModelMap()
            val view = userViewController.userSidebar(
                path = "/user/issues", hash = "",
                authentication = UsernamePasswordAuthenticationToken("testuser", "password"), model = model
            )

            view shouldBe "site/layout_framed"
            model.getAttribute("iframePath") shouldBe "/user/issues"
            model.getAttribute("recentIssues") shouldBe listOf(recentIssue)
        }
    }

    // userFiles()의 미인증/pageNum<1/filter 유무 분기.
    describe("GET /user/files") {
        it("미인증 시 error/403 뷰를 반환해야 한다") {
            userViewController.userFiles(filter = "", pageNum = 1, authentication = null, model = ExtendedModelMap()) shouldBe "error/403"
        }

        it("pageNum이 1 미만이면 error/404 뷰를 반환해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)

            userViewController.userFiles(
                filter = "", pageNum = 0,
                authentication = UsernamePasswordAuthenticationToken("testuser", "password"),
                model = ExtendedModelMap()
            ) shouldBe "error/404"
        }

        it("filter가 비어있으면 findByOwnerLoginId를 사용해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByOwnerLoginId("testuser", any()) } returns PageImpl(emptyList())

            userViewController.userFiles(
                filter = "", pageNum = 1,
                authentication = UsernamePasswordAuthenticationToken("testuser", "password"),
                model = ExtendedModelMap()
            ) shouldBe "user/userFiles"

            verify(exactly = 1) { attachmentRepository.findByOwnerLoginId("testuser", any()) }
        }

        it("filter가 있으면 findByOwnerLoginIdAndNameContainingIgnoreCase를 사용해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByOwnerLoginIdAndNameContainingIgnoreCase("testuser", "readme", any()) } returns PageImpl(emptyList())

            userViewController.userFiles(
                filter = "readme", pageNum = 1,
                authentication = UsernamePasswordAuthenticationToken("testuser", "password"),
                model = ExtendedModelMap()
            ) shouldBe "user/userFiles"

            verify(exactly = 1) { attachmentRepository.findByOwnerLoginIdAndNameContainingIgnoreCase("testuser", "readme", any()) }
        }
    }

    // getAttachmentUrl()은 GetMapping이 없는 순수 public 메서드라 컨트롤러를 직접 호출해서 검증한다.
    // containerId 파싱 실패, 4가지 ResourceType(존재/미존재), 미지원 타입, 예외(catch) 분기.
    describe("getAttachmentUrl") {
        it("containerId가 숫자로 파싱되지 않으면 null을 반환해야 한다") {
            val attachment = Attachment(containerType = ResourceType.ISSUE_POST, containerId = "not-a-number")
            userViewController.getAttachmentUrl(attachment) shouldBe null
        }

        it("ISSUE_POST 타입은 존재하면 이슈 URL, 없으면 null을 반환해야 한다") {
            val project = Project(id = 1L, name = "proj", owner = "owner1")
            val issue = Issue(id = 10L, title = "t", project = project, number = 5L)
            every { issueRepository.findById(10L) } returns Optional.of(issue)
            every { issueRepository.findById(11L) } returns Optional.empty()

            userViewController.getAttachmentUrl(Attachment(containerType = ResourceType.ISSUE_POST, containerId = "10")) shouldBe "/owner1/proj/issue/5"
            userViewController.getAttachmentUrl(Attachment(containerType = ResourceType.ISSUE_POST, containerId = "11")) shouldBe null
        }

        it("BOARD_POST 타입은 존재하면 게시글 URL, 없으면 null을 반환해야 한다") {
            val project = Project(id = 2L, name = "proj2", owner = "owner2")
            val posting = Posting(id = 20L, project = project, number = 3L)
            every { postingRepository.findById(20L) } returns Optional.of(posting)
            every { postingRepository.findById(21L) } returns Optional.empty()

            userViewController.getAttachmentUrl(Attachment(containerType = ResourceType.BOARD_POST, containerId = "20")) shouldBe "/owner2/proj2/post/3"
            userViewController.getAttachmentUrl(Attachment(containerType = ResourceType.BOARD_POST, containerId = "21")) shouldBe null
        }

        it("PULL_REQUEST 타입은 존재하면 PR URL, 없으면 null을 반환해야 한다") {
            val project = Project(id = 3L, name = "proj3", owner = "owner3")
            val contributor = User(id = 1L, loginId = "author", name = "작성자")
            val pr = PullRequest(id = 30L, number = 8L, toProject = project, fromProject = project, contributor = contributor)
            every { pullRequestRepository.findById(30L) } returns Optional.of(pr)
            every { pullRequestRepository.findById(31L) } returns Optional.empty()

            userViewController.getAttachmentUrl(Attachment(containerType = ResourceType.PULL_REQUEST, containerId = "30")) shouldBe "/owner3/proj3/pull/8"
            userViewController.getAttachmentUrl(Attachment(containerType = ResourceType.PULL_REQUEST, containerId = "31")) shouldBe null
        }

        it("PROJECT 타입은 존재하면 프로젝트 URL, 없으면 null을 반환해야 한다") {
            val project = Project(id = 4L, name = "proj4", owner = "owner4")
            every { projectRepository.findById(4L) } returns Optional.of(project)
            every { projectRepository.findById(5L) } returns Optional.empty()

            userViewController.getAttachmentUrl(Attachment(containerType = ResourceType.PROJECT, containerId = "4")) shouldBe "/owner4/proj4"
            userViewController.getAttachmentUrl(Attachment(containerType = ResourceType.PROJECT, containerId = "5")) shouldBe null
        }

        it("지원하지 않는 컨테이너 타입이면 null을 반환해야 한다") {
            userViewController.getAttachmentUrl(Attachment(containerType = ResourceType.USER_AVATAR, containerId = "1")) shouldBe null
        }

        it("리포지토리에서 예외가 발생하면 null을 반환해야 한다") {
            every { issueRepository.findById(99L) } throws RuntimeException("db error")
            userViewController.getAttachmentUrl(Attachment(containerType = ResourceType.ISSUE_POST, containerId = "99")) shouldBe null
        }
    }

    // editUserInfo()의 미인증/name blank/email 중복/avatar(없음, 첨부파일 없음, 비이미지, 이미지) 분기.
    describe("POST /user/edit (editUserInfo)") {
        it("미인증 시 error/403을 반환해야 한다") {
            userViewController.editUserInfo("이름", "a@a.com", null, null) shouldBe "error/403"
        }

        it("이름이 비어있으면 editform으로 리다이렉트해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "old", email = "old@old.com")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)

            userViewController.editUserInfo("  ", "a@a.com", null, UsernamePasswordAuthenticationToken("testuser", "password")) shouldBe "redirect:/user/editform"
        }

        it("변경하려는 이메일이 이미 사용 중이면 editform으로 리다이렉트해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "old", email = "old@old.com")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { userRepository.findByEmail("new@new.com") } returns Optional.of(User(id = 99L, loginId = "other", email = "new@new.com"))

            userViewController.editUserInfo("새이름", "new@new.com", null, UsernamePasswordAuthenticationToken("testuser", "password")) shouldBe "redirect:/user/editform"
        }

        it("이메일을 변경하지 않으면 중복 체크 없이 저장에 성공해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "old", email = "same@same.com")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { userRepository.save(any()) } returns loginUser

            val view = userViewController.editUserInfo("새이름", "same@same.com", null, UsernamePasswordAuthenticationToken("testuser", "password"))

            view shouldBe "redirect:/user/testuser"
            verify(exactly = 0) { userRepository.findByEmail(any()) }
            loginUser.name shouldBe "새이름"
        }

        it("avatarId가 없고 이메일이 변경 가능하면 아바타 변경 없이 새 이메일로 저장해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "old", email = "old@old.com")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { userRepository.findByEmail("brand-new@new.com") } returns Optional.empty()
            every { userRepository.save(any()) } returns loginUser

            userViewController.editUserInfo("새이름", "brand-new@new.com", null, UsernamePasswordAuthenticationToken("testuser", "password")) shouldBe "redirect:/user/testuser"
            verify(exactly = 0) { attachmentRepository.findById(any()) }
            loginUser.email shouldBe "brand-new@new.com"
        }

        it("avatarId에 해당하는 첨부파일이 없으면 아바타 변경 없이 저장해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "old", email = "old@old.com")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findById(500L) } returns Optional.empty()
            every { userRepository.save(any()) } returns loginUser

            userViewController.editUserInfo("새이름", "old@old.com", 500L, UsernamePasswordAuthenticationToken("testuser", "password")) shouldBe "redirect:/user/testuser"
            verify(exactly = 0) { attachmentRepository.save(any()) }
        }

        it("avatarId에 해당하는 첨부파일이 이미지가 아니면 아바타 변경 없이 저장해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "old", email = "old@old.com")
            val attachment = Attachment(id = 501L, mimeType = "text/plain")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findById(501L) } returns Optional.of(attachment)
            every { userRepository.save(any()) } returns loginUser

            userViewController.editUserInfo("새이름", "old@old.com", 501L, UsernamePasswordAuthenticationToken("testuser", "password")) shouldBe "redirect:/user/testuser"
            verify(exactly = 0) { attachmentRepository.save(any()) }
        }

        // mimeType이 null인 경우(safe-call `?.` 분기)도 "이미지가 아님"과 동일하게 처리되어야 한다.
        it("avatarId에 해당하는 첨부파일의 mimeType이 null이면 아바타 변경 없이 저장해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "old", email = "old@old.com")
            val attachment = Attachment(id = 503L, mimeType = null)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findById(503L) } returns Optional.of(attachment)
            every { userRepository.save(any()) } returns loginUser

            userViewController.editUserInfo("새이름", "old@old.com", 503L, UsernamePasswordAuthenticationToken("testuser", "password")) shouldBe "redirect:/user/testuser"
            verify(exactly = 0) { attachmentRepository.save(any()) }
        }

        it("avatarId에 해당하는 첨부파일이 이미지면 기존 아바타를 지우고 새 아바타로 저장해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", name = "old", email = "old@old.com")
            val attachment = Attachment(id = 502L, mimeType = "image/png")
            val oldAvatar = Attachment(id = 1L, containerType = ResourceType.USER_AVATAR, containerId = "10")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { attachmentRepository.findById(502L) } returns Optional.of(attachment)
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, "10") } returns listOf(oldAvatar)
            every { attachmentRepository.deleteAll(listOf(oldAvatar)) } just Runs
            every { attachmentRepository.save(attachment) } returns attachment
            every { userRepository.save(any()) } returns loginUser

            userViewController.editUserInfo("새이름", "old@old.com", 502L, UsernamePasswordAuthenticationToken("testuser", "password")) shouldBe "redirect:/user/testuser"

            verify(exactly = 1) { attachmentRepository.deleteAll(listOf(oldAvatar)) }
            verify(exactly = 1) { attachmentRepository.save(attachment) }
            attachment.containerType shouldBe ResourceType.USER_AVATAR
            attachment.containerId shouldBe "10"
        }
    }

    // resetUserPassword()의 미인증/기존 비밀번호 불일치/신규-재입력 불일치/4자 미만/성공(로그아웃 포함) 분기.
    describe("POST /user/resetPassword") {
        it("미인증 시 error/403을 반환해야 한다") {
            val request = mockk<HttpServletRequest>(relaxed = true)
            userViewController.resetUserPassword("old", "new1234", "new1234", null, request) shouldBe "error/403"
        }

        it("기존 비밀번호가 일치하지 않으면 비밀번호 편집 화면으로 리다이렉트해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", passwordSalt = "salt1", password = "다른해시값")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            val request = mockk<HttpServletRequest>(relaxed = true)

            userViewController.resetUserPassword(
                "wrong-old", "new1234", "new1234",
                UsernamePasswordAuthenticationToken("testuser", "password"), request
            ) shouldBe "redirect:/user/editform/password"

            verify(exactly = 0) { request.logout() }
        }

        it("새 비밀번호와 확인 비밀번호가 다르면 리다이렉트해야 한다") {
            val salt = "salt2"
            val loginUser = User(id = 10L, loginId = "testuser", passwordSalt = salt, password = hashPasswordLike("oldpass", salt))
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            val request = mockk<HttpServletRequest>(relaxed = true)

            userViewController.resetUserPassword(
                "oldpass", "new1234", "different",
                UsernamePasswordAuthenticationToken("testuser", "password"), request
            ) shouldBe "redirect:/user/editform/password"
        }

        it("새 비밀번호가 4자 미만이면 리다이렉트해야 한다") {
            val salt = "salt3"
            val loginUser = User(id = 10L, loginId = "testuser", passwordSalt = salt, password = hashPasswordLike("oldpass", salt))
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            val request = mockk<HttpServletRequest>(relaxed = true)

            userViewController.resetUserPassword(
                "oldpass", "abc", "abc",
                UsernamePasswordAuthenticationToken("testuser", "password"), request
            ) shouldBe "redirect:/user/editform/password"
        }

        it("모든 검증을 통과하면 비밀번호를 갱신하고 로그아웃 후 로그인 폼으로 리다이렉트해야 한다") {
            val salt = "salt4"
            val loginUser = User(id = 10L, loginId = "testuser", passwordSalt = salt, password = hashPasswordLike("oldpass", salt))
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { userRepository.save(any()) } returns loginUser
            val request = mockk<HttpServletRequest>(relaxed = true)

            val view = userViewController.resetUserPassword(
                "oldpass", "newpass1234", "newpass1234",
                UsernamePasswordAuthenticationToken("testuser", "password"), request
            )

            view shouldBe "redirect:/users/loginform"
            verify(exactly = 1) { request.logout() }
            verify(exactly = 1) { userRepository.save(loginUser) }
            loginUser.passwordSalt shouldNotBe salt
        }
    }

    describe("POST /user/resetVisitedList") {
        it("항상 editform으로 리다이렉트해야 한다") {
            userViewController.resetVisitedList() shouldBe "redirect:/user/editform"
        }
    }

    // addEmail/deleteEmail/setAsMainEmail/sendValidationEmail 공통 "미인증 -> error/403" 분기와
    // 서비스 호출 성공/예외(catch로 스윕) 분기. sendValidationEmail은 getServerUrl()의 80/443 포트
    // vs 그 외 포트 분기까지 함께 검증한다.
    describe("이메일 관리 액션") {
        val loginUser = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth2 = UsernamePasswordAuthenticationToken("testuser", "password")

        it("addEmail은 미인증 시 error/403을 반환해야 한다") {
            userViewController.addEmail("a@a.com", null) shouldBe "error/403"
        }

        it("addEmail 성공/예외 모두 이메일 편집 화면으로 리다이렉트해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { userService.addEmail(10L, "a@a.com") } returns mockk(relaxed = true)
            userViewController.addEmail("a@a.com", userAuth2) shouldBe "redirect:/user/editform/emails"

            every { userService.addEmail(10L, "dup@a.com") } throws RuntimeException("이미 존재")
            userViewController.addEmail("dup@a.com", userAuth2) shouldBe "redirect:/user/editform/emails"
        }

        it("deleteEmail은 미인증 시 error/403을 반환해야 한다") {
            userViewController.deleteEmail(1L, null) shouldBe "error/403"
        }

        it("deleteEmail 성공/예외 모두 이메일 편집 화면으로 리다이렉트해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { userService.deleteEmail(10L, 1L) } just Runs
            userViewController.deleteEmail(1L, userAuth2) shouldBe "redirect:/user/editform/emails"

            every { userService.deleteEmail(10L, 2L) } throws RuntimeException("불가")
            userViewController.deleteEmail(2L, userAuth2) shouldBe "redirect:/user/editform/emails"
        }

        it("setAsMainEmail은 미인증 시 error/403을 반환해야 한다") {
            userViewController.setAsMainEmail(1L, null) shouldBe "error/403"
        }

        it("setAsMainEmail 성공/예외 모두 이메일 편집 화면으로 리다이렉트해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { userService.setAsMainEmail(10L, 1L) } just Runs
            userViewController.setAsMainEmail(1L, userAuth2) shouldBe "redirect:/user/editform/emails"

            every { userService.setAsMainEmail(10L, 2L) } throws RuntimeException("불가")
            userViewController.setAsMainEmail(2L, userAuth2) shouldBe "redirect:/user/editform/emails"
        }

        it("sendValidationEmail은 미인증 시 error/403을 반환해야 한다") {
            val request = mockk<HttpServletRequest>(relaxed = true)
            userViewController.sendValidationEmail(1L, request, null) shouldBe "error/403"
        }

        it("sendValidationEmail은 80/443 포트면 scheme://host 형태로 서버 URL을 구성해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            val request = mockk<HttpServletRequest>()
            every { request.scheme } returns "http"
            every { request.serverName } returns "example.com"
            every { request.serverPort } returns 80
            every { userService.sendValidationEmail(10L, 1L, "http://example.com") } just Runs

            userViewController.sendValidationEmail(1L, request, userAuth2) shouldBe "redirect:/user/editform/emails"
            verify(exactly = 1) { userService.sendValidationEmail(10L, 1L, "http://example.com") }
        }

        it("sendValidationEmail은 80/443이 아닌 포트면 scheme://host:port 형태로 서버 URL을 구성해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            val request = mockk<HttpServletRequest>()
            every { request.scheme } returns "https"
            every { request.serverName } returns "example.com"
            every { request.serverPort } returns 8443
            every { userService.sendValidationEmail(10L, 2L, "https://example.com:8443") } just Runs

            userViewController.sendValidationEmail(2L, request, userAuth2) shouldBe "redirect:/user/editform/emails"
            verify(exactly = 1) { userService.sendValidationEmail(10L, 2L, "https://example.com:8443") }
        }

        it("sendValidationEmail이 예외를 던져도 이메일 편집 화면으로 리다이렉트해야 한다") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            val request = mockk<HttpServletRequest>(relaxed = true)
            every { userService.sendValidationEmail(10L, 3L, any()) } throws RuntimeException("불가")

            userViewController.sendValidationEmail(3L, request, userAuth2) shouldBe "redirect:/user/editform/emails"
        }
    }

    // resetToken()의 미인증/성공(토큰 갱신) 분기.
    describe("POST /user/editform/token_reset (resetToken)") {
        it("미인증 시 error/403을 반환해야 한다") {
            userViewController.resetToken(null) shouldBe "error/403"
        }

        it("인증된 사용자는 토큰을 갱신하고 편집 화면으로 리다이렉트해야 한다") {
            val loginUser = User(id = 10L, loginId = "testuser", token = "old-token")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(loginUser)
            every { userRepository.save(loginUser) } returns loginUser

            val view = userViewController.resetToken(UsernamePasswordAuthenticationToken("testuser", "password"))

            view shouldBe "redirect:/user/editform/token"
            loginUser.token shouldNotBe "old-token"
            verify(exactly = 1) { userRepository.save(loginUser) }
        }
    }

    // resetUserPasswordBySiteManager()의 미인증/비관리자/잘못된 action/대상 없음/성공 분기.
    describe("사이트 관리자 - 대상 사용자 비밀번호 초기화 (resetUserPasswordBySiteManager)") {
        it("미인증 시 403 FORBIDDEN을 반환해야 한다") {
            val response = userViewController.resetUserPasswordBySiteManager("target", "resetPassword", null)
            response.statusCode.value() shouldBe 403
            response.body?.get("isSuccess") shouldBe false
            response.body?.get("reason") shouldBe "FORBIDDEN"
        }

        it("사이트 관리자가 아니면 403 FORBIDDEN을 반환해야 한다") {
            val normalUser = User(id = 10L, loginId = "testuser", name = "일반유저")
            every { userRepository.findByLoginId("testuser") } returns Optional.of(normalUser)

            val response = userViewController.resetUserPasswordBySiteManager(
                "target", "resetPassword", UsernamePasswordAuthenticationToken("testuser", "password")
            )
            response.statusCode.value() shouldBe 403
        }

        it("action이 resetPassword가 아니면 400 BAD_REQUEST를 반환해야 한다") {
            val manager = User(id = 99L, loginId = "manager", name = "관리자", state = UserState.SITE_ADMIN)
            every { userRepository.findByLoginId("manager") } returns Optional.of(manager)

            val response = userViewController.resetUserPasswordBySiteManager(
                "target", "wrongAction", UsernamePasswordAuthenticationToken("manager", "password")
            )
            response.statusCode.value() shouldBe 400
            response.body?.get("reason") shouldBe "BAD_REQUEST"
        }

        it("대상 사용자가 존재하지 않으면 404 USER_NOT_FOUND를 반환해야 한다") {
            val manager = User(id = 99L, loginId = "manager", name = "관리자", state = UserState.SITE_ADMIN)
            every { userRepository.findByLoginId("manager") } returns Optional.of(manager)
            every { userRepository.findByLoginId("nouser") } returns Optional.empty()

            val response = userViewController.resetUserPasswordBySiteManager(
                "nouser", "resetPassword", UsernamePasswordAuthenticationToken("manager", "password")
            )
            response.statusCode.value() shouldBe 404
            response.body?.get("reason") shouldBe "USER_NOT_FOUND"
        }

        it("정상 요청이면 대상 사용자의 비밀번호를 초기화하고 200 OK를 반환해야 한다") {
            val manager = User(id = 99L, loginId = "manager", name = "관리자", state = UserState.SITE_ADMIN)
            val target = User(id = 20L, loginId = "target", name = "대상유저")
            every { userRepository.findByLoginId("manager") } returns Optional.of(manager)
            every { userRepository.findByLoginId("target") } returns Optional.of(target)
            every { userRepository.save(target) } returns target

            val response = userViewController.resetUserPasswordBySiteManager(
                "target", "resetPassword", UsernamePasswordAuthenticationToken("manager", "password")
            )
            response.statusCode.value() shouldBe 200
            response.body?.get("isSuccess") shouldBe true
            response.body?.get("loginId") shouldBe "target"
            verify(exactly = 1) { userRepository.save(target) }
        }
    }

    // verifyUserLegacy/confirmEmailLegacy는 verifyUser/confirmEmail로 위임하는 legacy 호환 라우트다.
    // 실제로 매핑된(@GetMapping) 진입점이라 도달 가능하지만 기존 테스트가 legacy 경로를 전혀
    // 호출하지 않아 METHOD 커버리지가 누락되어 있었다 — 위임 성공 케이스로 보강한다.
    describe("GET /verify/{loginId}/{verificationCode} (verifyUserLegacy)") {
        it("verifyUser와 동일하게 동작해 200 OK와 user/verified 뷰를 반환해야 한다") {
            every { userService.verifyUser("gildong", "legacy-code") } returns true

            mockMvc.perform(get("/verify/gildong/legacy-code"))
                .andExpect(status().isOk)
                .andExpect(view().name("user/verified"))
                .andExpect(model().attribute("loginId", "gildong"))
        }
    }

    describe("GET /user/email/confirm/{emailId}/{token} (confirmEmailLegacy)") {
        it("confirmEmail과 동일하게 동작해 /user/editform으로 리다이렉트해야 한다") {
            every { userService.confirmEmail(10L, "legacy-token") } returns true

            mockMvc.perform(get("/user/email/confirm/10/legacy-token"))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/user/editform"))
        }
    }

    // userProfile()의 "대상 사용자 없음" 404 분기와, 방문자가 인증되었지만 계정이 삭제된 경우
    // (findByLoginId가 Optional.empty를 반환) 익명 방문자와 동일하게 취급되는 분기.
    // openIssuesCount/closedIssuesCount는 이슈가 전부 OPEN 기본값이던 기존 테스트에서는
    // count{} 술어의 한쪽 결과만 나오므로 OPEN/CLOSED가 섞인 케이스로 양쪽을 모두 검증한다.
    describe("GET /user/{loginId} - 추가 분기 커버리지") {
        it("존재하지 않는 loginId면 error/404 뷰를 반환해야 한다") {
            every { userRepository.findByLoginId("ghost") } returns Optional.empty()

            val view = userViewController.userProfile(
                loginId = "ghost", daysAgo = 14, selected = "issues",
                authentication = null, model = ExtendedModelMap()
            )

            view shouldBe "error/404"
        }

        it("방문자가 인증되었으나 계정을 찾을 수 없으면 비로그인 방문자와 동일하게 처리해야 한다") {
            val viewedUser = User(id = 40L, loginId = "viewed2", name = "대상유저")
            val staleAuth = UsernamePasswordAuthenticationToken("ghostviewer", "password")
            every { userRepository.findByLoginId("viewed2") } returns Optional.of(viewedUser)
            every { userRepository.findByLoginId("ghostviewer") } returns Optional.empty()
            every { projectUserRepository.findByUserId(40L) } returns emptyList()
            every { issueRepository.findRecentlyByUser(40L, any()) } returns emptyList()
            every {
                pullRequestRepository.findByContributorAndUpdatedGreaterThanEqualOrderByUpdatedDescStateAsc(viewedUser, any())
            } returns emptyList()

            val model = ExtendedModelMap()
            userViewController.userProfile(loginId = "viewed2", daysAgo = 14, selected = "issues", authentication = staleAuth, model = model)

            model.getAttribute("currentUser") shouldBe null
        }

        it("OPEN/CLOSED 이슈가 섞여 있으면 각각의 개수를 올바르게 집계해야 한다") {
            val viewedUser = User(id = 41L, loginId = "viewed3", name = "대상유저")
            val project = Project(id = 9L, name = "proj9", owner = "viewed3")
            val openIssue = Issue(id = 500L, title = "open", project = project, state = State.OPEN)
            val closedIssue = Issue(id = 501L, title = "closed", project = project, state = State.CLOSED)
            every { userRepository.findByLoginId("viewed3") } returns Optional.of(viewedUser)
            every { projectUserRepository.findByUserId(41L) } returns emptyList()
            every { issueRepository.findRecentlyByUser(41L, any()) } returns listOf(openIssue, closedIssue)
            every {
                pullRequestRepository.findByContributorAndUpdatedGreaterThanEqualOrderByUpdatedDescStateAsc(viewedUser, any())
            } returns emptyList()

            val model = ExtendedModelMap()
            userViewController.userProfile(loginId = "viewed3", daysAgo = 14, selected = "issues", authentication = null, model = model)

            model.getAttribute("openIssuesCount") shouldBe 1
            model.getAttribute("closedIssuesCount") shouldBe 1
        }
    }

    // userIssues()의 "인증되었으나 사용자 레코드를 찾을 수 없는" 분기(세션은 살아있지만 계정이
    // 삭제된 경우) — 미인증(authentication==null) 분기와는 별개의 null 검사 지점이다.
    describe("GET /user/issues - 인증되었으나 사용자를 찾을 수 없는 경우") {
        it("로그인 폼으로 리다이렉트되어야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()

            mockMvc.perform(get("/user/issues").principal(UsernamePasswordAuthenticationToken("ghostuser", "password")))
                .andExpect(status().is3xxRedirection)
                .andExpect(redirectedUrl("/users/loginform"))
        }
    }

    // userSidebar()의 "인증되었으나 사용자 없음" 분기, watch.resourceId 파싱 실패/미존재 프로젝트
    // 제외 분기(editUserNotificationsForm/usermenuTabContentList에는 있었지만 userSidebar에는
    // 없었음), joinmember 필터의 "오너가 아닌 멤버(참여함에 포함)" 분기(기존 테스트는 반대로
    // "오너 본인(제외)" 케이스만 있었음).
    describe("GET /user/sidebar - 추가 분기 커버리지") {
        it("인증되었으나 사용자를 찾을 수 없으면 로그인 폼으로 리다이렉트되어야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()

            val view = userViewController.userSidebar(
                path = "/user/issues", hash = "",
                authentication = UsernamePasswordAuthenticationToken("ghostuser", "password"),
                model = ExtendedModelMap()
            )

            view shouldBe "redirect:/users/loginform"
        }

        it("watch의 resourceId 파싱 실패/미존재 프로젝트는 감시 목록에서 제외해야 한다") {
            val loginUser = User(id = 50L, loginId = "sidebaruser", name = "사이드바유저")
            val watchInvalid = Watch(id = 1L, user = loginUser, resourceType = ResourceType.PROJECT, resourceId = "not-a-number")
            val watchMissing = Watch(id = 2L, user = loginUser, resourceType = ResourceType.PROJECT, resourceId = "9999")
            val watchValid = Watch(id = 3L, user = loginUser, resourceType = ResourceType.PROJECT, resourceId = "12")
            val watchedProject = Project(id = 12L, name = "watched", owner = "someone")

            every { userRepository.findByLoginId("sidebaruser") } returns Optional.of(loginUser)
            every { favoriteProjectRepository.findByUserId(50L) } returns emptyList()
            every { organizationUserRepository.findByUserId(50L) } returns emptyList()
            every { favoriteOrganizationRepository.findByUserId(50L) } returns emptyList()
            every { projectUserRepository.findByUserId(50L) } returns emptyList()
            every { projectRepository.findByOwner("sidebaruser") } returns emptyList()
            every { watchRepository.findByUserAndResourceType(loginUser, ResourceType.PROJECT) } returns listOf(watchInvalid, watchMissing, watchValid)
            every { projectRepository.findById(9999L) } returns Optional.empty()
            every { projectRepository.findById(12L) } returns Optional.of(watchedProject)
            every { organizationRepository.findAll() } returns emptyList()

            val model = ExtendedModelMap()
            userViewController.userSidebar(
                path = "/user/issues", hash = "",
                authentication = UsernamePasswordAuthenticationToken("sidebaruser", "password"), model = model
            )

            model.getAttribute("watching") shouldBe listOf(watchedProject)
        }

        it("오너가 아닌 멤버로 참여한 프로젝트는 참여함(joinmember) 목록에 포함되어야 한다") {
            val loginUser = User(id = 51L, loginId = "member1", name = "멤버유저")
            val memberRole = Role(id = RoleType.MEMBER.roleType)
            val otherProject = Project(id = 13L, name = "other-proj", owner = "otherowner")
            val projectUser = ProjectUser(id = 2L, user = loginUser, project = otherProject, role = memberRole)

            every { userRepository.findByLoginId("member1") } returns Optional.of(loginUser)
            every { favoriteProjectRepository.findByUserId(51L) } returns emptyList()
            every { organizationUserRepository.findByUserId(51L) } returns emptyList()
            every { favoriteOrganizationRepository.findByUserId(51L) } returns emptyList()
            every { projectUserRepository.findByUserId(51L) } returns listOf(projectUser)
            every { projectRepository.findByOwner("member1") } returns emptyList()
            every { watchRepository.findByUserAndResourceType(loginUser, ResourceType.PROJECT) } returns emptyList()
            every { organizationRepository.findAll() } returns emptyList()
            every { issueRepository.findByProjectIn(listOf(otherProject), any()) } returns PageImpl(emptyList())

            val model = ExtendedModelMap()
            userViewController.userSidebar(
                path = "/user/issues", hash = "",
                authentication = UsernamePasswordAuthenticationToken("member1", "password"), model = model
            )

            model.getAttribute("joinmember") shouldBe listOf(otherProject)
        }
    }

    // editUserNotificationsForm()의 "인증되었으나 사용자 없음" 분기.
    describe("GET /user/editform/notifications - 인증되었으나 사용자를 찾을 수 없는 경우") {
        it("error/403을 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()

            userViewController.editUserNotificationsForm(
                UsernamePasswordAuthenticationToken("ghostuser", "password"), ExtendedModelMap()
            ) shouldBe "error/403"
        }
    }

    // usermenuTabContentList()의 "인증되었으나 사용자 없음" 분기와, joinmember 필터의
    // "오너 본인(제외)" 분기(기존 테스트는 반대로 "멤버로 참여(포함)" 케이스만 있었음).
    describe("GET /user/usermenuTabContentList - 추가 분기 커버리지") {
        it("인증되었으나 사용자를 찾을 수 없으면 모델 세팅 없이 공용 뷰만 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()

            val model = ExtendedModelMap()
            val view = userViewController.usermenuTabContentList(
                UsernamePasswordAuthenticationToken("ghostuser", "password"), model
            )

            view shouldBe "common/usermenu_tab_content_list"
            model.getAttribute("currentUser") shouldBe null
        }

        it("오너 본인이 속한 프로젝트는 참여함(joinmember) 목록에서 제외해야 한다") {
            val loginUser = User(id = 52L, loginId = "owner1", name = "오너유저")
            val memberRole = Role(id = RoleType.MEMBER.roleType)
            val ownProject = Project(id = 14L, name = "own-proj", owner = "owner1")
            val projectUser = ProjectUser(id = 3L, user = loginUser, project = ownProject, role = memberRole)

            every { userRepository.findByLoginId("owner1") } returns Optional.of(loginUser)
            every { favoriteProjectRepository.findByUserId(52L) } returns emptyList()
            every { organizationUserRepository.findByUserId(52L) } returns emptyList()
            every { favoriteOrganizationRepository.findByUserId(52L) } returns emptyList()
            every { projectUserRepository.findByUserId(52L) } returns listOf(projectUser)
            every { projectRepository.findByOwner("owner1") } returns listOf(ownProject)
            every { watchRepository.findByUserAndResourceType(loginUser, ResourceType.PROJECT) } returns emptyList()
            every { organizationRepository.findAll() } returns emptyList()

            val model = ExtendedModelMap()
            userViewController.usermenuTabContentList(UsernamePasswordAuthenticationToken("owner1", "password"), model)

            model.getAttribute("joinmember") shouldBe emptyList<Project>()
        }
    }

    // userFiles()의 "인증되었으나 사용자 없음" 분기.
    describe("GET /user/files - 인증되었으나 사용자를 찾을 수 없는 경우") {
        it("error/403 뷰를 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()

            userViewController.userFiles(
                filter = "", pageNum = 1,
                authentication = UsernamePasswordAuthenticationToken("ghostuser", "password"),
                model = ExtendedModelMap()
            ) shouldBe "error/403"
        }
    }

    // resetUserPassword()의 "인증되었으나 사용자 없음" 분기와, passwordSalt가 null인 사용자의
    // "?: \"\"" 엘비스 분기(기존 테스트는 모두 salt가 설정된 사용자만 사용했음).
    describe("POST /user/resetPassword - 추가 분기 커버리지") {
        it("인증되었으나 사용자를 찾을 수 없으면 error/403을 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()
            val request = mockk<HttpServletRequest>(relaxed = true)

            userViewController.resetUserPassword(
                "old", "new1234", "new1234",
                UsernamePasswordAuthenticationToken("ghostuser", "password"), request
            ) shouldBe "error/403"
        }

        it("기존 passwordSalt가 null이면 빈 문자열을 salt로 사용해 비교해야 한다") {
            val loginUser = User(id = 60L, loginId = "nosaltuser", password = hashPasswordLike("oldpass", ""))
            every { userRepository.findByLoginId("nosaltuser") } returns Optional.of(loginUser)
            every { userRepository.save(any()) } returns loginUser
            val request = mockk<HttpServletRequest>(relaxed = true)

            val view = userViewController.resetUserPassword(
                "oldpass", "newpass1234", "newpass1234",
                UsernamePasswordAuthenticationToken("nosaltuser", "password"), request
            )

            view shouldBe "redirect:/users/loginform"
            verify(exactly = 1) { request.logout() }
        }
    }

    // editUserProfileForm/editUserEmailsForm/editUserPasswordForm/editUserTokenForm의
    // "인증되었으나 사용자 없음" 분기(미인증/인증-성공 두 케이스만으로는 커버되지 않는, 세션은
    // 있으나 계정이 삭제된 경우의 별도 null 검사 지점).
    describe("사용자 편집 화면 - 인증되었으나 사용자를 찾을 수 없는 경우") {
        it("editUserProfileForm은 error/403을 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()
            userViewController.editUserProfileForm(
                UsernamePasswordAuthenticationToken("ghostuser", "password"), ExtendedModelMap()
            ) shouldBe "error/403"
        }

        it("editUserEmailsForm은 error/403을 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()
            userViewController.editUserEmailsForm(
                UsernamePasswordAuthenticationToken("ghostuser", "password"), ExtendedModelMap()
            ) shouldBe "error/403"
        }

        it("editUserPasswordForm은 error/403을 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()
            userViewController.editUserPasswordForm(
                UsernamePasswordAuthenticationToken("ghostuser", "password"), ExtendedModelMap()
            ) shouldBe "error/403"
        }

        it("editUserTokenForm은 error/403을 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()
            userViewController.editUserTokenForm(
                UsernamePasswordAuthenticationToken("ghostuser", "password"), ExtendedModelMap()
            ) shouldBe "error/403"
        }

        // fillAvatarId()의 "첨부파일이 존재함" 분기 — beforeTest의 전역 mock이 항상 빈 목록을
        // 반환해 attachments.isNotEmpty()가 한 번도 true였던 적이 없었다.
        it("아바타 첨부파일이 있으면 마지막 첨부파일 id를 avatarId로 채워야 한다") {
            val loginUser = User(id = 61L, loginId = "avataruser", name = "아바타유저")
            val avatar1 = Attachment(id = 700L)
            val avatar2 = Attachment(id = 701L)
            every { userRepository.findByLoginId("avataruser") } returns Optional.of(loginUser)
            every { attachmentRepository.findByContainerTypeAndContainerId(ResourceType.USER_AVATAR, "61") } returns listOf(avatar1, avatar2)

            userViewController.editUserProfileForm(UsernamePasswordAuthenticationToken("avataruser", "password"), ExtendedModelMap())

            loginUser.avatarId shouldBe 701L
        }
    }

    // editUserInfo/addEmail/deleteEmail/setAsMainEmail/sendValidationEmail/resetToken의
    // "인증되었으나 사용자 없음" 분기.
    describe("액션 엔드포인트 - 인증되었으나 사용자를 찾을 수 없는 경우") {
        it("editUserInfo는 error/403을 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()
            userViewController.editUserInfo(
                "이름", "a@a.com", null, UsernamePasswordAuthenticationToken("ghostuser", "password")
            ) shouldBe "error/403"
        }

        it("addEmail은 error/403을 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()
            userViewController.addEmail("a@a.com", UsernamePasswordAuthenticationToken("ghostuser", "password")) shouldBe "error/403"
        }

        it("deleteEmail은 error/403을 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()
            userViewController.deleteEmail(1L, UsernamePasswordAuthenticationToken("ghostuser", "password")) shouldBe "error/403"
        }

        it("setAsMainEmail은 error/403을 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()
            userViewController.setAsMainEmail(1L, UsernamePasswordAuthenticationToken("ghostuser", "password")) shouldBe "error/403"
        }

        it("sendValidationEmail은 error/403을 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()
            val request = mockk<HttpServletRequest>(relaxed = true)
            userViewController.sendValidationEmail(1L, request, UsernamePasswordAuthenticationToken("ghostuser", "password")) shouldBe "error/403"
        }

        it("resetToken은 error/403을 반환해야 한다") {
            every { userRepository.findByLoginId("ghostuser") } returns Optional.empty()
            userViewController.resetToken(UsernamePasswordAuthenticationToken("ghostuser", "password")) shouldBe "error/403"
        }
    }
})
