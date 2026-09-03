package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserSetting
import com.github.yonaprojects.yona.domain.user.UserSettingRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldBeEmpty
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.Optional
import io.mockk.clearMocks

class IndexControllerSpec : DescribeSpec({
    val notificationEventRepository = mockk<NotificationEventRepository>()
    val userRepository = mockk<UserRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val userSettingRepository = mockk<UserSettingRepository>()

    val indexController = IndexController(
        notificationEventRepository,
        userRepository,
        projectRepository,
        issueRepository,
        postingRepository,
        pullRequestRepository,
        organizationRepository,
        milestoneRepository,
        userSettingRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(indexController).build()

    beforeTest {
        clearMocks(
            notificationEventRepository,
            userRepository,
            projectRepository,
            issueRepository,
            postingRepository,
            pullRequestRepository,
            organizationRepository,
            userSettingRepository,
            milestoneRepository
        )
    }

    describe("IndexController 웹 테스트") {
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        val event = NotificationEvent(
            id = 1L,
            title = "새로운 이슈 등록",
            senderId = 20L,
            receivers = mutableSetOf(user),
            created = Instant.now(),
            resourceType = ResourceType.ISSUE_POST,
            resourceId = "100",
            eventType = EventType.NEW_ISSUE
        )

        describe("GET /") {
            it("로그인된 유저가 접속 시 최근 20개의 알림 목록을 모델에 담고 index 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { userSettingRepository.findByUserId(10L) } returns Optional.empty()
                every { notificationEventRepository.findByReceiver(user, PageRequest.of(0, 20)) } returns PageImpl(listOf(event))
                every { issueRepository.findAllById(any()) } returns emptyList()
                every { userRepository.findAllById(any()) } returns emptyList()

                mockMvc.perform(
                    get("/")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("index"))
                    .andExpect(model().attributeExists("notifications"))
            }

            it("비로그인 유저가 접속 시 데이터 바인딩 없이 index 뷰를 반환해야 한다") {
                mockMvc.perform(
                    get("/")
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("index"))
                    .andExpect(model().attributeDoesNotExist("notifications"))
            }

            // yona Application.java:45-52 index()의 loginDefaultPage 리다이렉트 대응 (P2-11) [GL-controllers_Application-009;GL-controllers_Application-010;GL-controllers_Application-011;GL-controllers_Application-012]
            it("기본 페이지가 설정된 로그인 유저가 접속하면 해당 경로로 리다이렉트해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { userSettingRepository.findByUserId(10L) } returns Optional.of(
                    UserSetting(id = 1L, user = user, loginDefaultPage = "notifications")
                )

                mockMvc.perform(
                    get("/")
                        .principal(userAuth)
                )
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("notifications"))
            }
        }

        describe("GET /_notifications") {
            it("비동기 알림 목록 HTML 스니펫을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { notificationEventRepository.findByReceiver(user, PageRequest.of(1, 20)) } returns PageImpl(listOf(event))
                every { issueRepository.findAllById(any()) } returns emptyList()
                every { userRepository.findAllById(any()) } returns emptyList()

                mockMvc.perform(
                    get("/_notifications")
                        .param("from", "20")
                        .param("size", "20")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("index/partial_notifications"))
                    .andExpect(model().attributeExists("notifications"))
            }

            // yona actions/AnonymousCheckAction.java 대응 — 비로그인 사용자는 별도 401 에러 페이지가
            // 아니라 로그인 폼으로 302 리다이렉트된다(Secured.onUnauthorized()/AnonymousCheckAction 둘 다
            // redirect(loginFormUrl) 패턴). legacy에 "401 상태를 보여주는 페이지" 자체가 없음을
            // 재확인(TASK-0264)하고 발견한 divergence를 수정.
            it("비로그인 상태로 요청하면 로그인 폼으로 리다이렉트해야 한다") {
                mockMvc.perform(get("/_notifications").param("from", "0").param("size", "20"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            // size=0이면 pageIndex/pageSize 계산의 두 `if (size > 0)` 분기가 모두 false가 되어
            // PageRequest.of(0, 20)으로 폴백해야 한다 — 기존 테스트(size=20)는 두 분기 모두 true만 커버함.
            it("size가 0이면 페이지 인덱스 0, 페이지 크기 20으로 조회해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { notificationEventRepository.findByReceiver(user, PageRequest.of(0, 20)) } returns PageImpl(listOf(event))
                every { issueRepository.findAllById(any()) } returns emptyList()
                every { userRepository.findAllById(any()) } returns emptyList()

                mockMvc.perform(
                    get("/_notifications")
                        .param("from", "5")
                        .param("size", "0")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(view().name("index/partial_notifications"))
            }
        }

        describe("GET /notifications") {
            it("비로그인 유저가 접속하면 로그인 폼으로 리다이렉트해야 한다") {
                mockMvc.perform(get("/notifications"))
                    .andExpect(status().is3xxRedirection)
                    .andExpect(redirectedUrl("/users/loginform"))
            }

            it("로그인 유저가 접속하면 최근 20개의 알림 목록을 모델에 담고 notifications 뷰를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { notificationEventRepository.findByReceiver(user, PageRequest.of(0, 20)) } returns PageImpl(listOf(event))
                every { issueRepository.findAllById(any()) } returns emptyList()
                every { userRepository.findAllById(any()) } returns emptyList()

                mockMvc.perform(get("/notifications").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("index/notifications"))
                    .andExpect(model().attributeExists("notifications"))
                    .andExpect(model().attributeExists("currentUser"))
            }

            // mapNotificationsToView의 `if (events.isEmpty()) return emptyList()` 이른 반환 분기.
            it("알림이 없는 로그인 유저는 빈 알림 목록을 받아야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { notificationEventRepository.findByReceiver(user, PageRequest.of(0, 20)) } returns PageImpl(emptyList())

                val result = mockMvc.perform(get("/notifications").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("index/notifications"))
                    .andReturn()

                @Suppress("UNCHECKED_CAST")
                val notifications = result.modelAndView!!.model["notifications"] as List<IndexController.NotificationViewDto>
                notifications.shouldBeEmpty()
            }
        }

        describe("GET / - loginDefaultPage 공백 분기") {
            // loginDefaultPage.isNullOrBlank()는 null 뿐 아니라 빈 문자열/공백 문자열도 true로 취급한다.
            // 기존 테스트는 null(Optional.empty)과 실제 경로 문자열만 다뤘으므로 빈 문자열/공백 케이스를 추가한다.
            it("loginDefaultPage가 빈 문자열이면 리다이렉트하지 않고 알림 목록을 보여줘야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { userSettingRepository.findByUserId(10L) } returns Optional.of(
                    UserSetting(id = 2L, user = user, loginDefaultPage = "")
                )
                every { notificationEventRepository.findByReceiver(user, PageRequest.of(0, 20)) } returns PageImpl(listOf(event))
                every { issueRepository.findAllById(any()) } returns emptyList()
                every { userRepository.findAllById(any()) } returns emptyList()

                mockMvc.perform(get("/").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("index"))
                    .andExpect(model().attributeExists("notifications"))
            }

            it("loginDefaultPage가 공백 문자로만 이루어지면 리다이렉트하지 않고 알림 목록을 보여줘야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { userSettingRepository.findByUserId(10L) } returns Optional.of(
                    UserSetting(id = 3L, user = user, loginDefaultPage = "   ")
                )
                every { notificationEventRepository.findByReceiver(user, PageRequest.of(0, 20)) } returns PageImpl(listOf(event))
                every { issueRepository.findAllById(any()) } returns emptyList()
                every { userRepository.findAllById(any()) } returns emptyList()

                mockMvc.perform(get("/").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(view().name("index"))
                    .andExpect(model().attributeExists("notifications"))
            }
        }

        describe("mapNotificationsToView - 이벤트/리소스 타입별 분기") {
            val project1 = Project(id = 1L, owner = "owner1", name = "proj1")
            val org1 = Organization(id = 2L, name = "org1")
            val milestone1 = Milestone(id = 3L, project = project1, title = "ms1")
            val issue1 = Issue(id = 4L, project = project1, number = 5L, title = "issue-title")
            val posting1 = Posting(id = 6L, project = project1, number = 7L, title = "posting-title")
            val contributor = User(id = 90L, loginId = "contributor", name = "기여자")
            val pr1 = PullRequest(id = 8L, toProject = project1, fromProject = project1, contributor = contributor, number = 9L, title = "pr-title")
            val sender = User(id = 20L, loginId = "sender1", name = "발신자")

            fun ev(
                id: Long? = 100L,
                eventType: EventType,
                resourceType: ResourceType = ResourceType.NOT_A_RESOURCE,
                resourceId: String = "0",
                newValue: String? = null,
                senderId: Long? = null
            ) = NotificationEvent(
                id = id,
                title = "알림",
                senderId = senderId,
                receivers = mutableSetOf(user),
                created = Instant.now(),
                resourceType = resourceType,
                resourceId = resourceId,
                eventType = eventType,
                newValue = newValue
            )

            fun stubUser() {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            }

            fun notificationsOf(events: List<NotificationEvent>) {
                every { notificationEventRepository.findByReceiver(user, PageRequest.of(0, 20)) } returns PageImpl(events)
            }

            fun fetchNotifications(): List<IndexController.NotificationViewDto> {
                val result = mockMvc.perform(get("/notifications").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn()
                @Suppress("UNCHECKED_CAST")
                return result.modelAndView!!.model["notifications"] as List<IndexController.NotificationViewDto>
            }

            it("NEW_ISSUE 이벤트의 이슈가 존재하지 않으면 url이 null이고 list-alt 아이콘을 사용해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.NEW_ISSUE, resourceType = ResourceType.ISSUE_POST, resourceId = "999")))
                every { issueRepository.findAllById(setOf(999L)) } returns emptyList()

                val list = fetchNotifications()
                list[0].url shouldBe null
                list[0].iconClass shouldBe "list-alt"
            }

            it("ISSUE_STATE_CHANGED newValue=closed면 닫힘 메시지와 list-alt closed 아이콘을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.ISSUE_STATE_CHANGED, resourceType = ResourceType.ISSUE_POST, resourceId = "4", newValue = "closed")))
                every { issueRepository.findAllById(setOf(4L)) } returns listOf(issue1)

                val list = fetchNotifications()
                list[0].message shouldBe "이슈가 닫혔습니다."
                list[0].iconClass shouldBe "list-alt closed"
                list[0].url shouldBe "/owner1/proj1/issue/5"
            }

            it("ISSUE_STATE_CHANGED newValue가 closed가 아니면 재오픈 메시지와 list-alt 아이콘을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.ISSUE_STATE_CHANGED, resourceType = ResourceType.ISSUE_POST, resourceId = "4", newValue = "open")))
                every { issueRepository.findAllById(setOf(4L)) } returns listOf(issue1)

                val list = fetchNotifications()
                list[0].message shouldBe "이슈가 다시 열렸습니다."
                list[0].iconClass shouldBe "list-alt"
            }

            it("ISSUE_ASSIGNEE_CHANGED 이벤트는 friends changed 아이콘과 빈 메시지를 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.ISSUE_ASSIGNEE_CHANGED, resourceType = ResourceType.ISSUE_POST, resourceId = "4")))
                every { issueRepository.findAllById(setOf(4L)) } returns listOf(issue1)

                val list = fetchNotifications()
                list[0].iconClass shouldBe "friends changed"
                list[0].message shouldBe ""
            }

            it("NEW_POSTING 이벤트는 게시글 URL과 edit2 아이콘을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.NEW_POSTING, resourceType = ResourceType.BOARD_POST, resourceId = "6")))
                every { postingRepository.findAllById(setOf(6L)) } returns listOf(posting1)

                val list = fetchNotifications()
                list[0].url shouldBe "/owner1/proj1/post/7"
                list[0].iconClass shouldBe "edit2"
            }

            it("NEW_POSTING 이벤트의 게시글이 없으면 url이 null이어야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.NEW_POSTING, resourceType = ResourceType.BOARD_POST, resourceId = "999")))
                every { postingRepository.findAllById(setOf(999L)) } returns emptyList()

                val list = fetchNotifications()
                list[0].url shouldBe null
            }

            it("NEW_PULL_REQUEST 이벤트는 pullRequest URL과 merge 아이콘을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.NEW_PULL_REQUEST, resourceType = ResourceType.PULL_REQUEST, resourceId = "8")))
                every { pullRequestRepository.findAllById(setOf(8L)) } returns listOf(pr1)

                val list = fetchNotifications()
                list[0].url shouldBe "/owner1/proj1/pullRequest/9"
                list[0].iconClass shouldBe "merge"
            }

            it("PULL_REQUEST_COMMIT_CHANGED 이벤트의 풀 리퀘스트가 없으면 url이 null이어야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.PULL_REQUEST_COMMIT_CHANGED, resourceType = ResourceType.PULL_REQUEST, resourceId = "999")))
                every { pullRequestRepository.findAllById(setOf(999L)) } returns emptyList()

                val list = fetchNotifications()
                list[0].url shouldBe null
                list[0].iconClass shouldBe "merge"
            }

            it("PULL_REQUEST_STATE_CHANGED newValue=closed면 닫힘 메시지와 merge closed 아이콘을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.PULL_REQUEST_STATE_CHANGED, resourceType = ResourceType.PULL_REQUEST, resourceId = "8", newValue = "closed")))
                every { pullRequestRepository.findAllById(setOf(8L)) } returns listOf(pr1)

                val list = fetchNotifications()
                list[0].message shouldBe "풀 리퀘스트가 닫혔습니다."
                list[0].iconClass shouldBe "merge closed"
            }

            it("PULL_REQUEST_STATE_CHANGED newValue=merged면 병합 메시지와 merge merged 아이콘을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.PULL_REQUEST_STATE_CHANGED, resourceType = ResourceType.PULL_REQUEST, resourceId = "8", newValue = "merged")))
                every { pullRequestRepository.findAllById(setOf(8L)) } returns listOf(pr1)

                val list = fetchNotifications()
                list[0].message shouldBe "풀 리퀘스트가 병합되었습니다."
                list[0].iconClass shouldBe "merge merged"
            }

            it("PULL_REQUEST_STATE_CHANGED newValue가 closed/merged가 아니면 재오픈 메시지와 merge 아이콘을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.PULL_REQUEST_STATE_CHANGED, resourceType = ResourceType.PULL_REQUEST, resourceId = "8", newValue = "reopened")))
                every { pullRequestRepository.findAllById(setOf(8L)) } returns listOf(pr1)

                val list = fetchNotifications()
                list[0].message shouldBe "풀 리퀘스트가 다시 열렸습니다."
                list[0].iconClass shouldBe "merge"
            }

            it("RESOURCE_DELETED + PROJECT 리소스는 프로젝트 URL과 megaphone 아이콘을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.RESOURCE_DELETED, resourceType = ResourceType.PROJECT, resourceId = "1", newValue = null)))
                every { projectRepository.findAllById(setOf(1L)) } returns listOf(project1)

                val list = fetchNotifications()
                list[0].url shouldBe "/owner1/proj1"
                list[0].iconClass shouldBe "megaphone"
                list[0].message shouldBe ""
            }

            it("RESOURCE_DELETED + PROJECT 리소스가 없으면 url이 null이어야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.RESOURCE_DELETED, resourceType = ResourceType.PROJECT, resourceId = "999")))
                every { projectRepository.findAllById(setOf(999L)) } returns emptyList()

                val list = fetchNotifications()
                list[0].url shouldBe null
            }

            it("RESOURCE_DELETED + ORGANIZATION 리소스는 조직 URL을 반환하고 newValue를 그대로 메시지로 사용해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.RESOURCE_DELETED, resourceType = ResourceType.ORGANIZATION, resourceId = "2", newValue = "어떤값")))
                every { organizationRepository.findAllById(setOf(2L)) } returns listOf(org1)

                val list = fetchNotifications()
                list[0].url shouldBe "/organizations/org1"
                list[0].message shouldBe "어떤값"
            }

            it("RESOURCE_DELETED + ORGANIZATION 리소스가 없으면 url이 null이어야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.RESOURCE_DELETED, resourceType = ResourceType.ORGANIZATION, resourceId = "999")))
                every { organizationRepository.findAllById(setOf(999L)) } returns emptyList()

                val list = fetchNotifications()
                list[0].url shouldBe null
            }

            it("ISSUE_MILESTONE_CHANGED 이벤트는 마일스톤 URL을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.ISSUE_MILESTONE_CHANGED, resourceType = ResourceType.MILESTONE, resourceId = "3")))
                every { milestoneRepository.findAllById(setOf(3L)) } returns listOf(milestone1)

                val list = fetchNotifications()
                list[0].url shouldBe "/owner1/proj1/milestone/3"
            }

            it("ISSUE_MILESTONE_CHANGED 이벤트의 마일스톤이 없으면 url이 null이어야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.ISSUE_MILESTONE_CHANGED, resourceType = ResourceType.MILESTONE, resourceId = "999")))
                every { milestoneRepository.findAllById(setOf(999L)) } returns emptyList()

                val list = fetchNotifications()
                list[0].url shouldBe null
            }

            it("처리되지 않는 리소스 타입이면 url이 null이고 comment2 아이콘을 사용해야 한다") {
                stubUser()
                // CODE는 ISSUE_POST/BOARD_POST/PULL_REQUEST/PROJECT/ORGANIZATION/MILESTONE 어디에도
                // 속하지 않아 중첩 when의 else 분기(url=null)로 빠져야 한다.
                notificationsOf(listOf(ev(eventType = EventType.NEW_COMMENT, resourceType = ResourceType.CODE, resourceId = "1")))

                val list = fetchNotifications()
                list[0].url shouldBe null
                list[0].iconClass shouldBe "comment2"
            }

            it("PULL_REQUEST_REVIEW_STATE_CHANGED 이벤트는 preview changed 아이콘을 사용해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, resourceType = ResourceType.PULL_REQUEST, resourceId = "8")))
                every { pullRequestRepository.findAllById(setOf(8L)) } returns listOf(pr1)

                val list = fetchNotifications()
                list[0].iconClass shouldBe "preview changed"
            }

            it("ISSUE_BODY_CHANGED 이벤트는 ellipsis-horizontal 아이콘을 사용해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.ISSUE_BODY_CHANGED, resourceType = ResourceType.ISSUE_POST, resourceId = "4")))
                every { issueRepository.findAllById(setOf(4L)) } returns listOf(issue1)

                val list = fetchNotifications()
                list[0].iconClass shouldBe "ellipsis-horizontal"
            }

            it("COMMENT_UPDATED 이벤트는 ellipsis-horizontal 아이콘을 사용해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.COMMENT_UPDATED, resourceType = ResourceType.ISSUE_POST, resourceId = "4")))
                every { issueRepository.findAllById(setOf(4L)) } returns listOf(issue1)

                val list = fetchNotifications()
                list[0].iconClass shouldBe "ellipsis-horizontal"
            }

            it("NEW_COMMIT 이벤트는 커밋 목록 URL과 push 아이콘을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.NEW_COMMIT, resourceType = ResourceType.PROJECT, resourceId = "1")))
                every { projectRepository.findAllById(setOf(1L)) } returns listOf(project1)

                val list = fetchNotifications()
                list[0].url shouldBe "/owner1/proj1/commits"
                list[0].iconClass shouldBe "push"
            }

            it("NEW_COMMIT 이벤트의 프로젝트가 없으면 url이 null이어야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.NEW_COMMIT, resourceType = ResourceType.PROJECT, resourceId = "999")))
                every { projectRepository.findAllById(setOf(999L)) } returns emptyList()

                val list = fetchNotifications()
                list[0].url shouldBe null
            }

            it("MEMBER_ENROLL_REQUEST 이벤트는 프로젝트 멤버 URL, addfriend 아이콘, 가입 신청 메시지를 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.MEMBER_ENROLL_REQUEST, resourceType = ResourceType.PROJECT, resourceId = "1")))
                every { projectRepository.findAllById(setOf(1L)) } returns listOf(project1)

                val list = fetchNotifications()
                list[0].url shouldBe "/projects/owner1/proj1/members"
                list[0].iconClass shouldBe "addfriend"
                list[0].message shouldBe "프로젝트 가입 신청이 등록되었습니다."
            }

            it("MEMBER_ENROLL_REQUEST newValue=ACCEPT면 addfriend closed 아이콘을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.MEMBER_ENROLL_REQUEST, resourceType = ResourceType.PROJECT, resourceId = "1", newValue = "ACCEPT")))
                every { projectRepository.findAllById(setOf(1L)) } returns listOf(project1)

                val list = fetchNotifications()
                list[0].iconClass shouldBe "addfriend closed"
            }

            it("MEMBER_ENROLL_REQUEST newValue=CANCEL이면 addfriend rejected 아이콘을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.MEMBER_ENROLL_REQUEST, resourceType = ResourceType.PROJECT, resourceId = "1", newValue = "CANCEL")))
                every { projectRepository.findAllById(setOf(1L)) } returns listOf(project1)

                val list = fetchNotifications()
                list[0].iconClass shouldBe "addfriend rejected"
            }

            it("MEMBER_ENROLL_REQUEST 이벤트의 프로젝트가 없으면 url이 null이어야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.MEMBER_ENROLL_REQUEST, resourceType = ResourceType.PROJECT, resourceId = "999")))
                every { projectRepository.findAllById(setOf(999L)) } returns emptyList()

                val list = fetchNotifications()
                list[0].url shouldBe null
            }

            it("MEMBER_ENROLL_ACCEPT 이벤트는 프로젝트 멤버 URL, 승인 메시지, megaphone 아이콘을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.MEMBER_ENROLL_ACCEPT, resourceType = ResourceType.PROJECT, resourceId = "1")))
                every { projectRepository.findAllById(setOf(1L)) } returns listOf(project1)

                val list = fetchNotifications()
                list[0].url shouldBe "/projects/owner1/proj1/members"
                list[0].message shouldBe "프로젝트 가입 신청이 승인되었습니다."
                list[0].iconClass shouldBe "megaphone"
            }

            it("MEMBER_ENROLL_ACCEPT 이벤트의 프로젝트가 없으면 url이 null이어야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.MEMBER_ENROLL_ACCEPT, resourceType = ResourceType.PROJECT, resourceId = "999")))
                every { projectRepository.findAllById(setOf(999L)) } returns emptyList()

                val list = fetchNotifications()
                list[0].url shouldBe null
            }

            it("ORGANIZATION_MEMBER_ENROLL_REQUEST 이벤트는 조직 멤버 URL을 반환해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, resourceType = ResourceType.ORGANIZATION, resourceId = "2")))
                every { organizationRepository.findAllById(setOf(2L)) } returns listOf(org1)

                val list = fetchNotifications()
                list[0].url shouldBe "/organizations/org1/members"
            }

            it("ORGANIZATION_MEMBER_ENROLL_ACCEPT 이벤트의 조직이 없으면 url이 null이어야 한다") {
                stubUser()
                notificationsOf(listOf(ev(eventType = EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT, resourceType = ResourceType.ORGANIZATION, resourceId = "999")))
                every { organizationRepository.findAllById(setOf(999L)) } returns emptyList()

                val list = fetchNotifications()
                list[0].url shouldBe null
            }

            it("발신자가 있으면 발신자 정보를 채우고, id가 null이면 0L로 대체해야 한다") {
                stubUser()
                notificationsOf(listOf(ev(id = null, eventType = EventType.NEW_COMMENT, resourceType = ResourceType.ISSUE_POST, resourceId = "4", senderId = 20L)))
                every { issueRepository.findAllById(setOf(4L)) } returns listOf(issue1)
                every { userRepository.findAllById(setOf(20L)) } returns listOf(sender)

                val list = fetchNotifications()
                list[0].id shouldBe 0L
                list[0].senderLoginId shouldBe "sender1"
                list[0].senderName shouldBe "발신자"
                list[0].senderAvatarUrl shouldNotBe null
            }
        }
    }
})
