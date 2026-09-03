package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.mention.MentionService
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-02 16라운드(TASK-0440) — UserStatusRestApiController(GET /api/v1/user/status),
// `gh status` 대응. UserIssueStatusRestApiControllerSpec과 동일한 mockk + standaloneSetup 패턴.
class UserStatusRestApiControllerSpec : DescribeSpec({
    val issueRepository = mockk<IssueRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val mentionService = mockk<MentionService>()
    val notificationEventRepository = mockk<NotificationEventRepository>()
    val userRepository = mockk<UserRepository>()

    val controller = UserStatusRestApiController(
        issueRepository, pullRequestRepository, mentionService, notificationEventRepository, userRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(issueRepository, pullRequestRepository, mentionService, notificationEventRepository, userRepository)
    }

    val auth = UsernamePasswordAuthenticationToken("tester", "password")
    val user = User(id = 1L, loginId = "tester", name = "테스터")
    val project = Project(id = 1L, owner = "yona", name = "yona")

    fun stubEmpty(userId: Long) {
        val emptyIssuePage = PageImpl<Issue>(emptyList(), PageRequest.of(0, 20), 0)
        val emptyPrPage = PageImpl<PullRequest>(emptyList(), PageRequest.of(0, 20), 0)
        every { issueRepository.findByAssigneeAndState(userId, State.OPEN, null, any()) } returns emptyIssuePage
        every { issueRepository.countByAssigneeAndState(userId, State.OPEN) } returns 0L
        every { issueRepository.countByAssigneeAndState(userId, State.CLOSED) } returns 0L
        every { pullRequestRepository.findByAssigneeUserIdAndState(userId, State.OPEN, any()) } returns emptyPrPage
        every { pullRequestRepository.countByAssigneeUserIdAndState(userId, State.OPEN) } returns 0L
        every { pullRequestRepository.countByAssigneeUserIdAndState(userId, State.CLOSED) } returns 0L
        every { pullRequestRepository.findByReviewerIdAndState(userId, State.OPEN, any()) } returns emptyPrPage
        every { pullRequestRepository.countByReviewerIdAndState(userId, State.OPEN) } returns 0L
        every { pullRequestRepository.countByReviewerIdAndState(userId, State.CLOSED) } returns 0L
        every { mentionService.getMentioningIssueIds(userId) } returns emptyList()
        every { notificationEventRepository.findByReceiver(any(), any()) } returns PageImpl(emptyList(), PageRequest.of(0, 20), 0)
    }

    describe("GET /api/v1/user/status") {
        it("비로그인 사용자는 401을 반환한다") {
            mockMvc.perform(get("/api/v1/user/status"))
                .andExpect(status().isUnauthorized)
        }

        it("담당 이슈/담당 PR/리뷰요청 PR/멘션된 이슈/저장소 활동을 한 번에 반환한다") {
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            stubEmpty(1L)

            val assignedIssue = Issue(id = 10L, number = 1L, title = "담당 이슈", project = project)
            every { issueRepository.findByAssigneeAndState(1L, State.OPEN, null, any()) } returns
                PageImpl(listOf(assignedIssue), PageRequest.of(0, 20), 1)
            every { issueRepository.countByAssigneeAndState(1L, State.OPEN) } returns 1L

            val contributor = User(id = 2L, loginId = "contrib", name = "기여자")
            val assignedPr = PullRequest(
                id = 20L, number = 1L, title = "담당 PR",
                toProject = project, fromProject = project, contributor = contributor
            )
            every { pullRequestRepository.findByAssigneeUserIdAndState(1L, State.OPEN, any()) } returns
                PageImpl(listOf(assignedPr), PageRequest.of(0, 20), 1)
            every { pullRequestRepository.countByAssigneeUserIdAndState(1L, State.OPEN) } returns 1L

            val reviewPr = PullRequest(
                id = 21L, number = 2L, title = "리뷰요청 PR",
                toProject = project, fromProject = project, contributor = contributor
            )
            every { pullRequestRepository.findByReviewerIdAndState(1L, State.OPEN, any()) } returns
                PageImpl(listOf(reviewPr), PageRequest.of(0, 20), 1)
            every { pullRequestRepository.countByReviewerIdAndState(1L, State.OPEN) } returns 1L

            val mentionedIssue = Issue(id = 11L, number = 3L, title = "멘션된 이슈", project = project)
            every { mentionService.getMentioningIssueIds(1L) } returns listOf(11L)
            every { issueRepository.findMentionedByState(listOf(11L), State.OPEN, null, any()) } returns
                PageImpl(listOf(mentionedIssue), PageRequest.of(0, 20), 1)
            every { issueRepository.countMentionedByState(listOf(11L), State.OPEN) } returns 1L
            every { issueRepository.countMentionedByState(listOf(11L), State.CLOSED) } returns 0L

            val activityEvent = NotificationEvent(
                id = 30L, title = "새 이슈가 등록되었습니다.", senderId = 2L,
                resourceType = ResourceType.ISSUE_POST, resourceId = "10", eventType = EventType.NEW_ISSUE
            )
            every { notificationEventRepository.findByReceiver(user, any()) } returns
                PageImpl(listOf(activityEvent), PageRequest.of(0, 20), 1)

            mockMvc.perform(get("/api/v1/user/status").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.assignedIssues.openCount").value(1))
                .andExpect(jsonPath("$.assignedIssues.items[0].title").value("담당 이슈"))
                .andExpect(jsonPath("$.assignedPullRequests.openCount").value(1))
                .andExpect(jsonPath("$.assignedPullRequests.items[0].title").value("담당 PR"))
                .andExpect(jsonPath("$.reviewRequests.openCount").value(1))
                .andExpect(jsonPath("$.reviewRequests.items[0].title").value("리뷰요청 PR"))
                .andExpect(jsonPath("$.mentionedIssues.openCount").value(1))
                .andExpect(jsonPath("$.mentionedIssues.items[0].title").value("멘션된 이슈"))
                .andExpect(jsonPath("$.repositoryActivity[0].eventType").value("NEW_ISSUE"))
                .andExpect(jsonPath("$.repositoryActivity[0].title").value("새 이슈가 등록되었습니다."))
        }

        it("담당/리뷰/멘션/활동이 전부 없으면 빈 섹션을 반환한다") {
            every { userRepository.findByLoginId("tester") } returns Optional.of(user)
            stubEmpty(1L)

            mockMvc.perform(get("/api/v1/user/status").principal(auth))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.assignedIssues.items.length()").value(0))
                .andExpect(jsonPath("$.assignedPullRequests.items.length()").value(0))
                .andExpect(jsonPath("$.reviewRequests.items.length()").value(0))
                .andExpect(jsonPath("$.mentionedIssues.items.length()").value(0))
                .andExpect(jsonPath("$.repositoryActivity.length()").value(0))
        }
    }
})
