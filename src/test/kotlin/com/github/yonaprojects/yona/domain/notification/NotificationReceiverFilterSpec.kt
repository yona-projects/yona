package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

// v1.6 NotificationEvent.add()는 filterReceivers()로 "리소스를 읽을 수 없는 수신자"와 loginId 없는 수신자를 제거했다.
// next는 멘션된 사용자를 그대로 수신자에 더해, 비공개 프로젝트 댓글의 내용이 비멤버에게 메일로 나갈 수 있었다.
class NotificationReceiverFilterSpec : DescribeSpec({
    val issueRepository = mockk<IssueRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val accessControl = mockk<AccessControl>()
    val filter = NotificationReceiverFilter(
        issueRepository, issueCommentRepository, mockk<PostingRepository>(), mockk<PostingCommentRepository>(),
        mockk<PullRequestRepository>(), mockk<CommitCommentRepository>(), mockk<ReviewCommentRepository>(),
        mockk<CommentThreadRepository>(), mockk<ProjectRepository>(), accessControl
    )

    val project = Project(id = 1L, name = "Secret", owner = "owner", projectScope = ProjectScope.PRIVATE)
    val member = User(id = 1L, loginId = "member", name = "멤버")
    val outsider = User(id = 2L, loginId = "outsider", name = "외부인")
    val blank = User(id = 3L, loginId = "", name = "로그인ID없음")
    val issue = Issue(id = 10L, title = "비공개 이슈", project = project, number = 1L)

    fun commentEvent(vararg receivers: User) = NotificationEvent(
        title = "t", senderId = 99L, resourceType = ResourceType.ISSUE_COMMENT, resourceId = "20",
        eventType = EventType.NEW_COMMENT, receivers = receivers.toMutableSet()
    )

    beforeTest {
        every { issueCommentRepository.findById(20L) } returns Optional.of(IssueComment(id = 20L, issue = issue))
        every { accessControl.isAllowed(any<User>(), project, any<IssueComment>(), Operation.READ) } answers { accessControl.isAllowed(firstArg<User>(), project, Operation.READ) }
        every { accessControl.isAllowed(any<User>(), project, any<Issue>(), Operation.READ) } answers { accessControl.isAllowed(firstArg<User>(), project, Operation.READ) }
        every { accessControl.isAllowed(member, project, Operation.READ) } returns true
        every { accessControl.isAllowed(outsider, project, Operation.READ) } returns false
        every { accessControl.isAllowed(blank, project, Operation.READ) } returns true
    }

    describe("NotificationReceiverFilter.filter") {
        it("리소스가 속한 프로젝트를 읽을 수 없는 수신자를 제거한다") {
            val event = commentEvent(member, outsider)
            filter.filter(event)
            event.receivers.map { it.loginId } shouldBe listOf("member")
        }

        // v1.6 filterReceivers는 AccessControl.isAllowed(receiver, event.getResource(), READ) — 리소스 단위 판정이라 비공개 프로젝트의 이슈
        // 공유자(sharer)·작성자·담당자는 프로젝트 READ 권한이 없어도 통과했다. 프로젝트 READ만 보면 공유 알림이 통째로 사라진다.
        it("프로젝트는 못 읽어도 이슈 자체는 읽을 수 있는 수신자(공유 대상 등)는 유지한다") {
            val sharer = User(id = 4L, loginId = "sharer", name = "공유대상")
            every { accessControl.isAllowed(sharer, project, Operation.READ) } returns false
            every { accessControl.isAllowed(sharer, project, issue, Operation.READ) } returns true
            every { accessControl.isAllowed(sharer, project, any<IssueComment>(), Operation.READ) } returns true
            val event = NotificationEvent(
                title = "t", senderId = 99L, resourceType = ResourceType.ISSUE_POST, resourceId = "10",
                eventType = EventType.ISSUE_SHARER_CHANGED, receivers = mutableSetOf(sharer, outsider)
            )
            every { issueRepository.findById(10L) } returns Optional.of(issue)
            every { accessControl.isAllowed(outsider, project, issue, Operation.READ) } returns false

            filter.filter(event)

            event.receivers.map { it.loginId } shouldBe listOf("sharer")
        }

        it("loginId가 비어 있는 수신자를 제거한다") {
            val event = commentEvent(member, blank)
            filter.filter(event)
            event.receivers.map { it.loginId } shouldBe listOf("member")
        }

        it("프로젝트를 알 수 없는 리소스 타입의 이벤트는 건드리지 않는다") {
            val event = NotificationEvent(
                title = "t", senderId = 99L, resourceType = ResourceType.ORGANIZATION, resourceId = "5",
                eventType = EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, receivers = mutableSetOf(outsider)
            )
            filter.filter(event)
            event.receivers.size shouldBe 1
        }
    }

    describe("NotificationEventRecorder.record") {
        it("필터로 수신자가 모두 제거되면 저장하지 않고 null을 반환한다") {
            val eventRepo = mockk<NotificationEventRepository>(relaxed = true)
            val mailRepo = mockk<NotificationMailRepository>(relaxed = true)
            every { eventRepo.findFirstByResourceTypeAndResourceIdAndCreatedAfterOrderByIdDesc(any(), any(), any()) } returns null
            val receiverFilter = mockk<NotificationReceiverFilter>()
            every { receiverFilter.filter(any()) } answers { firstArg<NotificationEvent>().receivers.clear() }
            val recorder = NotificationEventRecorder(eventRepo, mailRepo, SimpleMeterRegistry(), receiverFilter, mockk(relaxed = true))

            val result = recorder.record(commentEvent(outsider))

            result shouldBe null
            verify(exactly = 1) { receiverFilter.filter(any()) }
            verify(exactly = 0) { eventRepo.save(any<NotificationEvent>()) }
        }
    }
})
