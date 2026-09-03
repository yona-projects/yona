package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.mail.MailRecipient
import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.pullrequest.CodeCommentThread
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitComment
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.support.MarkdownService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.Optional

// yona models/NotificationMail.startSchedule()/sendMail()/sendNotification() 대응 (P1-27).
class NotificationMailDigestSchedulerSpec : DescribeSpec({
    val notificationMailRepository = mockk<NotificationMailRepository>()
    val notificationEventMerger = mockk<NotificationEventMerger>()
    val messageResolver = mockk<NotificationMessageResolver>()
    val urlResolver = mockk<NotificationUrlResolver>()
    val mailRenderer = mockk<NotificationMailRenderer>()
    val markdownService = mockk<MarkdownService>()
    val mailService = mockk<MailService>(relaxed = true)
    val userRepository = mockk<UserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val issueCommentRepository = mockk<IssueCommentRepository>()
    val postingCommentRepository = mockk<PostingCommentRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commentThreadRepository = mockk<CommentThreadRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val organizationRepository = mockk<OrganizationRepository>()
    lateinit var meterRegistry: SimpleMeterRegistry

    fun scheduler(
        enabled: Boolean = true,
        hideAddress: Boolean = true,
        recipientLimit: Int = 0,
        allowedDomains: String = "",
        imapAddress: String = ""
    ) = NotificationMailDigestScheduler(
        notificationMailRepository, notificationEventMerger, messageResolver, urlResolver, mailRenderer,
        markdownService, mailService, userRepository, issueRepository, postingRepository,
        issueCommentRepository, postingCommentRepository, pullRequestRepository, commitCommentRepository,
        reviewCommentRepository, commentThreadRepository, projectRepository, organizationRepository,
        enabled, hideAddress, recipientLimit, 180000L, allowedDomains, imapAddress, "yona.example.com", "Yona",
        meterRegistry
    )

    // resourceType/eventType을 자유롭게 조합할 수 있는 범용 이벤트 빌더.
    fun notifEvent(
        resourceType: ResourceType,
        resourceId: String,
        receivers: Set<User>,
        eventType: EventType = EventType.NEW_COMMENT,
        id: Long = 1L,
        senderId: Long? = null
    ) = NotificationEvent(
        id = id, title = "제목", created = Instant.now(), senderId = senderId,
        resourceType = resourceType, resourceId = resourceId,
        eventType = eventType, newValue = "본문",
        receivers = receivers.toMutableSet()
    )

    // NotificationMailRepository/notificationEventMerger/messageResolver 배선을 표준 세팅해두고
    // sendMail()을 실행한다 — resourceExists/projectOf/getReplyTo/computeReferences 분기 테스트에서 반복 사용.
    fun runSendMail(event: NotificationEvent, schedulerInstance: NotificationMailDigestScheduler = scheduler()) {
        val mail = NotificationMail(id = (event.id ?: 0L) + 9000L, notificationEvent = event)
        every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
        every { notificationMailRepository.delete(mail) } returns Unit
        every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
        every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
        every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
        schedulerInstance.sendMail()
    }

    beforeTest {
        clearMocks(
            notificationMailRepository, notificationEventMerger, messageResolver, urlResolver, mailRenderer,
            markdownService, mailService, userRepository, issueRepository, postingRepository,
            issueCommentRepository, postingCommentRepository, pullRequestRepository, commitCommentRepository,
            reviewCommentRepository, commentThreadRepository, projectRepository, organizationRepository,
            answers = false
        )
        every { markdownService.render(any(), any(), any(), any()) } answers { firstArg() }
        every { mailRenderer.render(any(), any(), any(), any(), any(), any()) } answers { firstArg() }
        every { mailRenderer.renderPlain(any()) } answers { firstArg() }
        every { urlResolver.getUrlToView(any()) } returns null
        every { userRepository.findById(any()) } returns Optional.empty()
        meterRegistry = SimpleMeterRegistry()
    }

    fun receiver(id: Long, email: String = "u$id@yona.io", lang: String? = "ko", state: UserState = UserState.ACTIVE) =
        User(id = id, loginId = "user$id", name = "사용자$id", email = email, state = state, lang = lang)

    fun issueEvent(receivers: Set<User>, id: Long = 1L) = NotificationEvent(
        id = id, title = "제목", created = Instant.now(),
        resourceType = ResourceType.ISSUE_POST, resourceId = "1",
        eventType = EventType.NEW_ISSUE, newValue = "본문",
        receivers = receivers.toMutableSet()
    )

    describe("sendDueNotificationMails") {
        it("발송 비활성화 설정이면 아무 것도 조회하지 않는다") {
            scheduler(enabled = false).sendDueNotificationMails()

            verify(exactly = 0) { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) }
        }
    }

    describe("sendMail") {
        it("지연 시간이 지난 NotificationMail을 큐에서 꺼내 삭제하고, 이벤트를 병합해서 처리한다") {
            val receiverUser = receiver(2L)
            val event = issueEvent(setOf(receiverUser))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            scheduler().sendMail()

            verify(exactly = 1) { notificationMailRepository.delete(mail) }
            verify(exactly = 1) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        // yona-wiki P3-01(Observability) 계측 지점 3 검증 — 정상 발송 1건은 duration 타이머 1회 기록+
        // sent 카운터 1 증가로 남아야 한다(1개 원본 이벤트가 병합 없이 1개로 그대로 나갔으므로 merged는 0).
        it("정상 발송되면 duration 타이머와 sent 카운터가 기록되고 merged는 늘지 않아야 한다") {
            val receiverUser = receiver(2L)
            val event = issueEvent(setOf(receiverUser))
            val mail = NotificationMail(id = 101L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            scheduler().sendMail()

            meterRegistry.timer("yona.notification.digest.duration").count() shouldBe 1L
            meterRegistry.counter("yona.notification.digest.sent").count() shouldBe 1.0
            meterRegistry.counter("yona.notification.digest.failed").count() shouldBe 0.0
            meterRegistry.counter("yona.notification.digest.merged").count() shouldBe 0.0
        }

        // sendNotification() 내부(mailService 호출)에서 예외가 나면 바깥 for문의 catch에 잡혀
        // 로깅만 되고 넘어가는데, 이 경로에서도 failed 카운터가 증가해야 대시보드에서 실패를 알 수 있다.
        it("발송 중 예외가 발생하면 failed 카운터가 증가해야 한다") {
            val receiverUser = receiver(2L)
            val event = issueEvent(setOf(receiverUser))
            val mail = NotificationMail(id = 102L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } throws RuntimeException("boom")

            scheduler().sendMail()

            meterRegistry.counter("yona.notification.digest.failed").count() shouldBe 1.0
            meterRegistry.counter("yona.notification.digest.sent").count() shouldBe 0.0
        }

        it("리소스가 이미 삭제됐으면(resourceExists=false) 메일을 보내지 않는다") {
            val receiverUser = receiver(2L)
            val event = issueEvent(setOf(receiverUser))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns false

            scheduler().sendMail()

            verify(exactly = 0) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("비활성(UserState.LOCKED 등) 사용자는 수신자에서 제외한다") {
            val activeUser = receiver(2L, state = UserState.ACTIVE)
            val lockedUser = receiver(3L, state = UserState.LOCKED)
            val event = issueEvent(setOf(activeUser, lockedUser))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            val bccSlot = slot<List<MailRecipient>>()
            every {
                mailService.sendNotificationMail(any(), capture(bccSlot), any(), any(), any(), any(), any(), any(), any(), any())
            } returns Unit

            scheduler(hideAddress = true).sendMail()

            bccSlot.captured.map { it.email } shouldBe listOf("u2@yona.io")
        }

        it("허용 도메인 설정이 있으면 다른 도메인 수신자는 제외한다") {
            val allowed = receiver(2L, email = "allowed@ok.com")
            val disallowed = receiver(3L, email = "blocked@bad.com")
            val event = issueEvent(setOf(allowed, disallowed))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            val bccSlot = slot<List<MailRecipient>>()
            every {
                mailService.sendNotificationMail(any(), capture(bccSlot), any(), any(), any(), any(), any(), any(), any(), any())
            } returns Unit

            scheduler(allowedDomains = "ok.com").sendMail()

            bccSlot.captured.map { it.email } shouldBe listOf("allowed@ok.com")
        }

        it("언어가 다른 수신자는 각자 다른 로케일로 별도 발송된다") {
            val korean = receiver(2L, lang = "ko")
            val english = receiver(3L, lang = "en")
            val event = issueEvent(setOf(korean, english))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            scheduler().sendMail()

            verify(exactly = 2) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            // yona Markdown.render(source, project, lang) 대응 (P1-140) — 이 스케줄러는 요청 스레드가
            // 아니라 LocaleContextHolder로 언어를 알 수 없으므로, 배치별로 계산해둔 수신자 언어를
            // markdownService.render()에 명시적으로 넘겨야 한다(각 배치가 자기 로케일을 그대로 받는지 검증).
            verify(exactly = 1) { markdownService.render("메시지", true, any(), "ko") }
            verify(exactly = 1) { markdownService.render("메시지", true, any(), "en") }
        }

        it("recipientLimit이 설정되면 hideAddress일 때 (limit-1)명 단위로 쪼개 발송한다") {
            val users = (1..5L).map { receiver(it + 1) }.toSet()
            val event = issueEvent(users)
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            // recipientLimit=3, hideAddress=true -> partialRecipientSize = 3-1 = 2 -> 5명을 3그룹(2,2,1)으로 분할
            scheduler(recipientLimit = 3, hideAddress = true).sendMail()

            verify(exactly = 3) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("hideAddress=false면 To에 실제 수신자를 담고 Bcc는 비운다") {
            val user = receiver(2L)
            val event = issueEvent(setOf(user))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            val toSlot = slot<List<MailRecipient>>()
            val bccSlot = slot<List<MailRecipient>>()
            every {
                mailService.sendNotificationMail(capture(toSlot), capture(bccSlot), any(), any(), any(), any(), any(), any(), any(), any())
            } returns Unit

            scheduler(hideAddress = false).sendMail()

            toSlot.captured.map { it.email } shouldBe listOf("u2@yona.io")
            bccSlot.captured shouldBe emptyList()
        }

        it("isCreating() 이벤트 타입이면 결정론적 Message-ID를 지정한다") {
            val user = receiver(2L)
            val event = issueEvent(setOf(user))
            val mail = NotificationMail(id = 100L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            val messageIdSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), capture(messageIdSlot), any(), any())
            } returns Unit

            scheduler().sendMail()

            messageIdSlot.captured shouldBe "<issue_post/1@yona.example.com>"
        }

        it("REVIEW_COMMENT 이벤트는 References로 스레드의 첫 리뷰 댓글 Message-ID를 채운다") {
            val project = Project(id = 3L, name = "proj", owner = "owner")
            val thread = CodeCommentThread(id = 10L, project = project)
            val firstComment = ReviewComment(id = 50L, thread = thread)
            val newComment = ReviewComment(id = 55L, thread = thread)

            val user = receiver(2L)
            val event = NotificationEvent(
                id = 1L, title = "제목", created = Instant.now(),
                resourceType = ResourceType.REVIEW_COMMENT, resourceId = "55",
                eventType = EventType.NEW_REVIEW_COMMENT, newValue = "댓글 내용",
                receivers = mutableSetOf(user)
            )
            val mail = NotificationMail(id = 101L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { reviewCommentRepository.existsById(55L) } returns true
            every { reviewCommentRepository.findById(55L) } returns Optional.of(newComment)
            every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(10L) } returns listOf(firstComment, newComment)
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"

            val referencesSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), capture(referencesSlot), any())
            } returns Unit

            scheduler().sendMail()

            referencesSlot.captured shouldBe "<review_comment/50@yona.example.com>"
        }

        it("COMMIT_COMMENT 이벤트는 References로 커밋 리소스의 Message-ID를 채운다") {
            val project = Project(id = 3L, name = "proj", owner = "owner")
            val commitComment = CommitComment(id = 77L, project = project, commitId = "abc123")

            val user = receiver(2L)
            val event = NotificationEvent(
                id = 1L, title = "제목", created = Instant.now(),
                resourceType = ResourceType.COMMIT_COMMENT, resourceId = "77",
                eventType = EventType.NEW_COMMENT, newValue = "댓글 내용",
                receivers = mutableSetOf(user)
            )
            val mail = NotificationMail(id = 102L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { commitCommentRepository.existsById(77L) } returns true
            every { commitCommentRepository.findById(77L) } returns Optional.of(commitComment)
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"

            val referencesSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), capture(referencesSlot), any())
            } returns Unit

            scheduler().sendMail()

            referencesSlot.captured shouldBe "<commit/3:abc123@yona.example.com>"
        }

        it("REVIEW_THREAD_STATE_CHANGED 이벤트(컨테이너 없음)는 References를 채우지 않는다") {
            val user = receiver(2L)
            val event = NotificationEvent(
                id = 1L, title = "제목", created = Instant.now(),
                resourceType = ResourceType.COMMENT_THREAD, resourceId = "10",
                eventType = EventType.REVIEW_THREAD_STATE_CHANGED, oldValue = "OPEN", newValue = "CLOSED",
                receivers = mutableSetOf(user)
            )
            val mail = NotificationMail(id = 103L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { commentThreadRepository.existsById(10L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"

            scheduler().sendMail()

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), isNull(), any())
            }
        }
    }

    describe("sendDueNotificationMails 추가 분기") {
        it("발송 활성화 상태면 sendMail()에 실제로 위임한다") {
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns emptyList()

            scheduler(enabled = true).sendDueNotificationMails()

            verify(exactly = 1) { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) }
        }

        it("sendMail() 처리 중 예외가 발생해도 예외를 삼키고 로깅만 한다") {
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } throws RuntimeException("DB 오류")

            // 예외 없이 정상 반환되면 성공.
            scheduler(enabled = true).sendDueNotificationMails()
        }
    }

    describe("extractEventsAndDelete") {
        it("notificationEvent가 null인 메일은 건너뛰고 나머지만 이벤트로 수집한다") {
            val validEvent = issueEvent(setOf(receiver(2L)))
            // NotificationMail.notificationEvent는 Kotlin 타입상 non-null이지만, DB FK가 nullable이라
            // Hibernate가 로딩 시 실제로 null을 채워 넣을 수 있다(그래서 production 코드가 "?: continue"로
            // 방어한다) — 생성자로는 null을 넣을 수 없으므로 리플렉션으로 필드를 직접 null화해 재현한다.
            val nullEventMail = NotificationMail(id = 400L, notificationEvent = validEvent)
            val field = NotificationMail::class.java.getDeclaredField("notificationEvent")
            field.isAccessible = true
            field.set(nullEventMail, null)
            val validMail = NotificationMail(id = 401L, notificationEvent = validEvent)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(nullEventMail, validMail)
            every { notificationMailRepository.delete(validMail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(validEvent)) } returns listOf(MergedNotificationEvent(validEvent))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            scheduler().sendMail()

            verify(exactly = 0) { notificationMailRepository.delete(nullEventMail) }
            verify(exactly = 1) { notificationMailRepository.delete(validMail) }
        }

        it("메일 삭제 중 예외가 발생한 항목은 건너뛰고 나머지 메일은 계속 처리한다") {
            val event1 = issueEvent(setOf(receiver(2L)), id = 1L)
            val event2 = issueEvent(setOf(receiver(3L)), id = 2L)
            val failingMail = NotificationMail(id = 402L, notificationEvent = event1)
            val okMail = NotificationMail(id = 403L, notificationEvent = event2)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(failingMail, okMail)
            every { notificationMailRepository.delete(failingMail) } throws RuntimeException("삭제 실패")
            every { notificationMailRepository.delete(okMail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event2)) } returns listOf(MergedNotificationEvent(event2))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            scheduler().sendMail()

            // failingMail의 이벤트(event1)는 events 목록에 추가되기 전에 예외가 나서 병합 대상에서 빠져야 한다.
            verify(exactly = 1) { notificationEventMerger.mergeEvents(listOf(event2)) }
        }
    }

    describe("resourceExists ResourceType별 분기") {
        it("BOARD_POST: 게시글이 존재하면 발송한다") {
            val event = notifEvent(ResourceType.BOARD_POST, "5", setOf(receiver(2L)))
            every { postingRepository.existsById(5L) } returns true
            every { postingRepository.findById(5L) } returns Optional.empty()
            runSendMail(event)
            verify(exactly = 1) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("BOARD_POST: 게시글이 삭제됐으면 발송하지 않는다") {
            val event = notifEvent(ResourceType.BOARD_POST, "5", setOf(receiver(2L)))
            every { postingRepository.existsById(5L) } returns false
            runSendMail(event)
            verify(exactly = 0) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("ISSUE_COMMENT: 댓글이 존재하면 발송한다") {
            val event = notifEvent(ResourceType.ISSUE_COMMENT, "6", setOf(receiver(2L)))
            every { issueCommentRepository.existsById(6L) } returns true
            every { issueCommentRepository.findById(6L) } returns Optional.empty()
            runSendMail(event)
            verify(exactly = 1) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("NONISSUE_COMMENT: 댓글이 존재하면 발송한다") {
            val event = notifEvent(ResourceType.NONISSUE_COMMENT, "7", setOf(receiver(2L)))
            every { postingCommentRepository.existsById(7L) } returns true
            every { postingCommentRepository.findById(7L) } returns Optional.empty()
            runSendMail(event)
            verify(exactly = 1) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("PULL_REQUEST: PR이 존재하면 발송한다") {
            val event = notifEvent(ResourceType.PULL_REQUEST, "8", setOf(receiver(2L)))
            every { pullRequestRepository.existsById(8L) } returns true
            every { pullRequestRepository.findById(8L) } returns Optional.empty()
            runSendMail(event)
            verify(exactly = 1) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("PROJECT: 프로젝트가 존재하면 발송한다") {
            val event = notifEvent(ResourceType.PROJECT, "9", setOf(receiver(2L)))
            every { projectRepository.existsById(9L) } returns true
            every { projectRepository.findById(9L) } returns Optional.empty()
            runSendMail(event)
            verify(exactly = 1) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("ORGANIZATION: 조직이 존재하면 발송한다") {
            val event = notifEvent(ResourceType.ORGANIZATION, "10", setOf(receiver(2L)))
            every { organizationRepository.existsById(10L) } returns true
            runSendMail(event)
            verify(exactly = 1) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("when절에 없는 ResourceType(else)은 존재 확인 없이 항상 발송한다") {
            val event = notifEvent(ResourceType.CODE, "11", setOf(receiver(2L)))
            runSendMail(event)
            verify(exactly = 1) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("resourceId가 숫자가 아니면 존재 확인 없이 발송한다") {
            val event = notifEvent(ResourceType.ISSUE_POST, "not-a-number", setOf(receiver(2L)))
            runSendMail(event)
            verify(exactly = 0) { issueRepository.existsById(any()) }
            verify(exactly = 1) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    describe("sendNotification 경계 조건") {
        it("허용 도메인 필터링 후 수신자가 하나도 없으면 발송하지 않는다") {
            val user = receiver(2L, email = "blocked@bad.com")
            val event = issueEvent(setOf(user))
            val mail = NotificationMail(id = 500L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true

            scheduler(allowedDomains = "ok.com").sendMail()

            verify(exactly = 0) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("recipientLimit=1, hideAddress=true면 partialRecipientSize가 0이 되어 발송하지 않는다") {
            val user = receiver(2L)
            val event = issueEvent(setOf(user))
            val mail = NotificationMail(id = 501L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true

            scheduler(recipientLimit = 1, hideAddress = true).sendMail()

            verify(exactly = 0) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("recipientLimit이 설정되고 hideAddress=false면 limit명 단위로 쪼개 발송한다") {
            val users = (1..5L).map { receiver(it + 1) }.toSet()
            val event = issueEvent(users)
            val mail = NotificationMail(id = 502L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } returns listOf(MergedNotificationEvent(event))
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            // recipientLimit=2, hideAddress=false -> partialRecipientSize = 2 -> 5명을 3그룹(2,2,1)으로 분할
            scheduler(recipientLimit = 2, hideAddress = false).sendMail()

            verify(exactly = 3) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    describe("sendMail(event, toList, bccList, locale) 세부 분기") {
        it("발신자 ID가 있고 사용자가 존재하면 발신자 이름을 fromName으로 사용한다") {
            val sender = receiver(99L, email = "sender@yona.io")
            val user = receiver(2L)
            val event = notifEvent(ResourceType.ISSUE_POST, "1", setOf(user), senderId = 99L)
            every { userRepository.findById(99L) } returns Optional.of(sender)
            every { issueRepository.existsById(1L) } returns true
            every { issueRepository.findById(1L) } returns Optional.empty()

            val fromNameSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), capture(fromNameSlot), any(), any(), any(), any(), any(), any(), any())
            } returns Unit

            runSendMail(event)

            fromNameSlot.captured shouldBe "사용자99"
        }

        it("발신자 ID가 있지만 사용자가 삭제됐으면 사이트 이름을 fromName으로 사용한다") {
            val user = receiver(2L)
            val event = notifEvent(ResourceType.ISSUE_POST, "1", setOf(user), senderId = 999L)
            every { userRepository.findById(999L) } returns Optional.empty()
            every { issueRepository.existsById(1L) } returns true
            every { issueRepository.findById(1L) } returns Optional.empty()

            val fromNameSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), capture(fromNameSlot), any(), any(), any(), any(), any(), any(), any())
            } returns Unit

            runSendMail(event)

            fromNameSlot.captured shouldBe "Yona"
        }

        it("ISSUE_BODY_CHANGED 이벤트는 마크다운 렌더링 없이 원본 메시지를 그대로 htmlBody 원본으로 사용한다") {
            val user = receiver(2L)
            val event = notifEvent(ResourceType.ISSUE_POST, "1", setOf(user), eventType = EventType.ISSUE_BODY_CHANGED)
            every { issueRepository.existsById(1L) } returns true
            every { issueRepository.findById(1L) } returns Optional.empty()

            runSendMail(event)

            verify(exactly = 0) { markdownService.render(any(), any(), any(), any()) }
            verify(exactly = 1) { mailRenderer.render("메시지", any(), any(), any(), any(), any()) }
        }

        it("isCreating()이 아닌 이벤트 타입은 Message-ID를 채우지 않는다") {
            val user = receiver(2L)
            val event = notifEvent(ResourceType.ISSUE_POST, "1", setOf(user), eventType = EventType.ISSUE_STATE_CHANGED)
            every { issueRepository.existsById(1L) } returns true
            every { issueRepository.findById(1L) } returns Optional.empty()

            runSendMail(event)

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), isNull(), any(), any())
            }
        }

        it("toList가 비어있으면 발송하지 않는다 (getToList가 항상 비어있지 않은 값만 만들어 공개 경로로는 도달하지 않는 방어 분기 - private 메서드를 리플렉션으로 직접 호출해 검증)") {
            val event = issueEvent(setOf(receiver(2L)))
            val merged = MergedNotificationEvent(event)
            val method = NotificationMailDigestScheduler::class.java.getDeclaredMethod(
                "sendMail", MergedNotificationEvent::class.java, List::class.java, List::class.java, Locale::class.java
            )
            method.isAccessible = true

            method.invoke(scheduler(), merged, emptyList<MailRecipient>(), emptyList<MailRecipient>(), Locale.KOREAN)

            verify(exactly = 0) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    describe("unwatchTarget / getReplyTo / computeReferences / projectOf") {
        val projectA = Project(id = 3L, name = "proj", owner = "owner")

        it("imapAddress가 비어있으면 getReplyTo는 항상 null이다") {
            val event = notifEvent(ResourceType.ISSUE_POST, "1", setOf(receiver(2L)))
            every { issueRepository.existsById(1L) } returns true
            every { issueRepository.findById(1L) } returns Optional.of(Issue(id = 1L, project = projectA))

            runSendMail(event, scheduler(imapAddress = ""))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), any(), any())
            }
        }

        it("ISSUE_COMMENT: 댓글을 찾으면 이슈 상세 주소로 회신 라우팅하고, unwatch도 이슈로 리다이렉트하며, References는 이슈의 Message-ID다") {
            val issue = Issue(id = 20L, project = projectA)
            val comment = IssueComment(id = 30L, issue = issue)
            val event = notifEvent(ResourceType.ISSUE_COMMENT, "30", setOf(receiver(2L)))
            every { issueCommentRepository.existsById(30L) } returns true
            every { issueCommentRepository.findById(30L) } returns Optional.of(comment)

            val replyToSlot = slot<String>()
            val referencesSlot = slot<String>()
            val unwatchTypeSlot = slot<ResourceType>()
            val unwatchIdSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), capture(replyToSlot), any(), capture(referencesSlot), any())
            } returns Unit
            every {
                mailRenderer.render(any(), any(), capture(unwatchTypeSlot), capture(unwatchIdSlot), any(), any())
            } answers { firstArg() }

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            replyToSlot.captured shouldBe "yona+owner/proj/issue_post/20@example.com"
            referencesSlot.captured shouldBe "<issue_post/20@yona.example.com>"
            unwatchTypeSlot.captured shouldBe ResourceType.ISSUE_POST
            unwatchIdSlot.captured shouldBe "20"
        }

        it("ISSUE_COMMENT: 댓글을 못 찾으면 회신/References가 없고, unwatch는 원래 리소스(댓글)로 유지된다") {
            val event = notifEvent(ResourceType.ISSUE_COMMENT, "31", setOf(receiver(2L)))
            every { issueCommentRepository.existsById(31L) } returns true
            every { issueCommentRepository.findById(31L) } returns Optional.empty()

            val unwatchTypeSlot = slot<ResourceType>()
            val unwatchIdSlot = slot<String>()
            every {
                mailRenderer.render(any(), any(), capture(unwatchTypeSlot), capture(unwatchIdSlot), any(), any())
            } answers { firstArg() }

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), isNull(), any())
            }
            unwatchTypeSlot.captured shouldBe ResourceType.ISSUE_COMMENT
            unwatchIdSlot.captured shouldBe "31"
        }

        it("NONISSUE_COMMENT: 댓글을 찾으면 게시글 상세 주소로 회신 라우팅하고 References는 게시글의 Message-ID다") {
            val posting = Posting(id = 21L, project = projectA)
            val comment = PostingComment(id = 32L, posting = posting)
            val event = notifEvent(ResourceType.NONISSUE_COMMENT, "32", setOf(receiver(2L)))
            every { postingCommentRepository.existsById(32L) } returns true
            every { postingCommentRepository.findById(32L) } returns Optional.of(comment)

            val replyToSlot = slot<String>()
            val referencesSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), capture(replyToSlot), any(), capture(referencesSlot), any())
            } returns Unit

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            replyToSlot.captured shouldBe "yona+owner/proj/board_post/21@example.com"
            referencesSlot.captured shouldBe "<board_post/21@yona.example.com>"
        }

        it("NONISSUE_COMMENT: 댓글을 못 찾으면 회신/References가 없다") {
            val event = notifEvent(ResourceType.NONISSUE_COMMENT, "33", setOf(receiver(2L)))
            every { postingCommentRepository.existsById(33L) } returns true
            every { postingCommentRepository.findById(33L) } returns Optional.empty()

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), isNull(), any())
            }
        }

        it("REVIEW_COMMENT: 스레드가 있으면 COMMENT_THREAD 상세 주소로 회신 라우팅한다") {
            val thread = CodeCommentThread(id = 40L, project = projectA)
            val comment = ReviewComment(id = 41L, thread = thread)
            val event = notifEvent(ResourceType.REVIEW_COMMENT, "41", setOf(receiver(2L)))
            every { reviewCommentRepository.existsById(41L) } returns true
            every { reviewCommentRepository.findById(41L) } returns Optional.of(comment)
            // computeReferences()가 같은 이벤트에서 스레드의 첫 리뷰 댓글도 함께 조회한다 — References는
            // 이 테스트의 관심사가 아니므로 빈 목록으로 응답해 그 분기(firstOrNull()==null -> null)만 통과시킨다.
            every { reviewCommentRepository.findByThreadIdOrderByCreatedDateAsc(40L) } returns emptyList()

            val replyToSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), capture(replyToSlot), any(), any(), any())
            } returns Unit

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            replyToSlot.captured shouldBe "yona+owner/proj/comment_thread/40@example.com"
        }

        it("REVIEW_COMMENT: 스레드가 없으면 회신을 만들지 않는다") {
            val comment = ReviewComment(id = 42L, thread = null)
            val event = notifEvent(ResourceType.REVIEW_COMMENT, "42", setOf(receiver(2L)))
            every { reviewCommentRepository.existsById(42L) } returns true
            every { reviewCommentRepository.findById(42L) } returns Optional.of(comment)

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), any(), any())
            }
        }

        it("REVIEW_COMMENT: 댓글을 못 찾으면 회신/References가 없다") {
            val event = notifEvent(ResourceType.REVIEW_COMMENT, "43", setOf(receiver(2L)))
            every { reviewCommentRepository.existsById(43L) } returns true
            every { reviewCommentRepository.findById(43L) } returns Optional.empty()

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), isNull(), any())
            }
        }

        it("COMMIT_COMMENT: 프로젝트가 있으면 자기 자신(COMMIT_COMMENT) 상세 주소로 회신 라우팅한다") {
            val comment = CommitComment(id = 44L, project = projectA, commitId = "deadbeef")
            val event = notifEvent(ResourceType.COMMIT_COMMENT, "44", setOf(receiver(2L)))
            every { commitCommentRepository.existsById(44L) } returns true
            every { commitCommentRepository.findById(44L) } returns Optional.of(comment)

            val replyToSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), capture(replyToSlot), any(), any(), any())
            } returns Unit

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            replyToSlot.captured shouldBe "yona+owner/proj/code_comment/44@example.com"
        }

        it("COMMIT_COMMENT: 소속 프로젝트가 없으면 회신/References가 없다") {
            val comment = CommitComment(id = 45L, project = null, commitId = "deadbeef")
            val event = notifEvent(ResourceType.COMMIT_COMMENT, "45", setOf(receiver(2L)))
            every { commitCommentRepository.existsById(45L) } returns true
            every { commitCommentRepository.findById(45L) } returns Optional.of(comment)

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), isNull(), any())
            }
        }

        it("COMMIT_COMMENT: 댓글을 못 찾으면 회신/References가 없다") {
            val event = notifEvent(ResourceType.COMMIT_COMMENT, "46", setOf(receiver(2L)))
            every { commitCommentRepository.existsById(46L) } returns true
            every { commitCommentRepository.findById(46L) } returns Optional.empty()

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), isNull(), any())
            }
        }

        it("ISSUE_POST: 이슈를 찾으면 이슈 자신의 상세 주소로 회신 라우팅하고 unwatch도 그대로 이슈다") {
            val event = notifEvent(ResourceType.ISSUE_POST, "50", setOf(receiver(2L)))
            every { issueRepository.existsById(50L) } returns true
            every { issueRepository.findById(50L) } returns Optional.of(Issue(id = 50L, project = projectA))

            val replyToSlot = slot<String>()
            val unwatchTypeSlot = slot<ResourceType>()
            val unwatchIdSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), capture(replyToSlot), any(), any(), any())
            } returns Unit
            every {
                mailRenderer.render(any(), any(), capture(unwatchTypeSlot), capture(unwatchIdSlot), any(), any())
            } answers { firstArg() }

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            replyToSlot.captured shouldBe "yona+owner/proj/issue_post/50@example.com"
            unwatchTypeSlot.captured shouldBe ResourceType.ISSUE_POST
            unwatchIdSlot.captured shouldBe "50"
        }

        it("ISSUE_POST: 이슈가 삭제됐으면 회신을 만들지 않는다") {
            val event = notifEvent(ResourceType.ISSUE_POST, "51", setOf(receiver(2L)))
            every { issueRepository.existsById(51L) } returns true
            every { issueRepository.findById(51L) } returns Optional.empty()

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), any(), any())
            }
        }

        it("BOARD_POST: 게시글을 찾으면 게시글 자신의 상세 주소로 회신 라우팅한다") {
            val event = notifEvent(ResourceType.BOARD_POST, "52", setOf(receiver(2L)))
            every { postingRepository.existsById(52L) } returns true
            every { postingRepository.findById(52L) } returns Optional.of(Posting(id = 52L, project = projectA))

            val replyToSlot = slot<String>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), capture(replyToSlot), any(), any(), any())
            } returns Unit

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            replyToSlot.captured shouldBe "yona+owner/proj/board_post/52@example.com"
        }

        it("BOARD_POST: 게시글이 삭제됐으면 회신을 만들지 않는다") {
            val event = notifEvent(ResourceType.BOARD_POST, "53", setOf(receiver(2L)))
            every { postingRepository.existsById(53L) } returns true
            every { postingRepository.findById(53L) } returns Optional.empty()

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), any(), any())
            }
        }

        it("getReplyTo가 다루지 않는 ResourceType(else, 예: PROJECT)은 회신을 만들지 않는다") {
            val event = notifEvent(ResourceType.PROJECT, "60", setOf(receiver(2L)))
            every { projectRepository.existsById(60L) } returns true
            every { projectRepository.findById(60L) } returns Optional.of(projectA)

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), any(), any())
            }
        }

        it("프로젝트의 owner가 없으면 상세 주소를 만들 수 없어 회신을 만들지 않는다") {
            val ownerlessProject = Project(id = 4L, name = "no-owner", owner = null)
            val event = notifEvent(ResourceType.ISSUE_POST, "61", setOf(receiver(2L)))
            every { issueRepository.existsById(61L) } returns true
            every { issueRepository.findById(61L) } returns Optional.of(Issue(id = 61L, project = ownerlessProject))

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), any(), any())
            }
        }

        it("imapAddress 형식이 올바르지 않으면(EmailAddressDetail 파싱 실패) 회신을 만들지 않는다") {
            val event = notifEvent(ResourceType.ISSUE_POST, "62", setOf(receiver(2L)))
            every { issueRepository.existsById(62L) } returns true
            every { issueRepository.findById(62L) } returns Optional.of(Issue(id = 62L, project = projectA))

            runSendMail(event, scheduler(imapAddress = "invalid-address-without-at-sign"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), any(), any())
            }
        }

        it("PULL_REQUEST 이벤트는 toProject를 마크다운 렌더링 대상 프로젝트로 전달한다(projectOf)") {
            val pr = PullRequest(id = 70L, toProject = projectA, fromProject = projectA, contributor = receiver(2L))
            val event = notifEvent(ResourceType.PULL_REQUEST, "70", setOf(receiver(2L)))
            every { pullRequestRepository.existsById(70L) } returns true
            every { pullRequestRepository.findById(70L) } returns Optional.of(pr)

            val projectSlot = slot<Project>()
            every {
                markdownService.render(any(), any(), capture(projectSlot), any())
            } answers { firstArg() }

            runSendMail(event, scheduler())

            projectSlot.captured shouldBe projectA
        }

        it("PROJECT 이벤트는 그 프로젝트 자신을 마크다운 렌더링 대상 프로젝트로 전달한다(projectOf)") {
            val event = notifEvent(ResourceType.PROJECT, "71", setOf(receiver(2L)))
            every { projectRepository.existsById(71L) } returns true
            every { projectRepository.findById(71L) } returns Optional.of(projectA)

            val projectSlot = slot<Project>()
            every {
                markdownService.render(any(), any(), capture(projectSlot), any())
            } answers { firstArg() }

            runSendMail(event, scheduler())

            projectSlot.captured shouldBe projectA
        }
    }

    // resourceExists()는 resourceId가 숫자가 아니면(toLongOrNull()==null) 무조건 true를 반환해
    // 별도로 커버했지만, getReplyTo/computeReferences/projectOf/unwatchTarget은 각자 자기 when절
    // 안에서 독립적으로 "event.resourceId.toLongOrNull()?.let {...}"를 호출한다 - 같은 모양의 코드라도
    // 함수마다 별개의 분기 포인트라 서로 다른 이벤트로 각각 커버해야 한다.
    describe("resourceId가 숫자가 아닌 경우의 getReplyTo/computeReferences/projectOf/unwatchTarget 분기") {
        it("ISSUE_COMMENT: resourceId가 숫자가 아니면 회신/References가 없고 unwatch는 원래 리소스로 유지된다") {
            val event = notifEvent(ResourceType.ISSUE_COMMENT, "not-a-number", setOf(receiver(2L)))

            val unwatchTypeSlot = slot<ResourceType>()
            val unwatchIdSlot = slot<String>()
            every {
                mailRenderer.render(any(), any(), capture(unwatchTypeSlot), capture(unwatchIdSlot), any(), any())
            } answers { firstArg() }

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), isNull(), any())
            }
            unwatchTypeSlot.captured shouldBe ResourceType.ISSUE_COMMENT
            unwatchIdSlot.captured shouldBe "not-a-number"
        }

        it("NONISSUE_COMMENT: resourceId가 숫자가 아니면 회신/References가 없다") {
            val event = notifEvent(ResourceType.NONISSUE_COMMENT, "not-a-number", setOf(receiver(2L)))

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), isNull(), any())
            }
        }

        it("REVIEW_COMMENT: resourceId가 숫자가 아니면 회신/References가 없다") {
            val event = notifEvent(ResourceType.REVIEW_COMMENT, "not-a-number", setOf(receiver(2L)))

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), isNull(), any())
            }
        }

        it("COMMIT_COMMENT: resourceId가 숫자가 아니면 회신/References가 없다") {
            val event = notifEvent(ResourceType.COMMIT_COMMENT, "not-a-number", setOf(receiver(2L)))

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), isNull(), any())
            }
        }

        it("ISSUE_POST: resourceId가 숫자가 아니면 회신이 없다") {
            val event = notifEvent(ResourceType.ISSUE_POST, "not-a-number", setOf(receiver(2L)))

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), any(), any())
            }
        }

        it("BOARD_POST: resourceId가 숫자가 아니면 회신이 없다") {
            val event = notifEvent(ResourceType.BOARD_POST, "not-a-number", setOf(receiver(2L)))

            runSendMail(event, scheduler(imapAddress = "yona@example.com"))

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), isNull(), any(), any(), any())
            }
        }

        it("PULL_REQUEST: resourceId가 숫자가 아니면 projectOf가 null을 반환한다") {
            val event = notifEvent(ResourceType.PULL_REQUEST, "not-a-number", setOf(receiver(2L)))
            every { markdownService.render(any(), any(), isNull(), any()) } answers { firstArg() }

            runSendMail(event, scheduler())

            verify(exactly = 1) { markdownService.render(any(), any(), isNull(), any()) }
        }

        it("PROJECT: resourceId가 숫자가 아니면 projectOf가 null을 반환한다") {
            val event = notifEvent(ResourceType.PROJECT, "not-a-number", setOf(receiver(2L)))

            runSendMail(event, scheduler())

            verify(exactly = 1) { markdownService.render(any(), any(), isNull(), any()) }
        }

        it("REVIEW_COMMENT: 스레드는 있지만 아직 저장되지 않아 id가 없으면(신규/미영속) References를 채우지 않는다") {
            val unsavedThread = CodeCommentThread(id = null, project = Project(id = 3L, name = "proj", owner = "owner"))
            val comment = ReviewComment(id = 90L, thread = unsavedThread)
            val event = notifEvent(ResourceType.REVIEW_COMMENT, "90", setOf(receiver(2L)))
            every { reviewCommentRepository.existsById(90L) } returns true
            every { reviewCommentRepository.findById(90L) } returns Optional.of(comment)

            runSendMail(event, scheduler())

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), isNull(), any())
            }
        }

        it("COMMIT_COMMENT: 프로젝트는 있지만 아직 저장되지 않아 id가 없으면(신규/미영속) References를 채우지 않는다") {
            val unsavedProject = Project(id = null, name = "proj", owner = "owner")
            val comment = CommitComment(id = 91L, project = unsavedProject, commitId = "abc")
            val event = notifEvent(ResourceType.COMMIT_COMMENT, "91", setOf(receiver(2L)))
            every { commitCommentRepository.existsById(91L) } returns true
            every { commitCommentRepository.findById(91L) } returns Optional.of(comment)

            runSendMail(event, scheduler())

            verify(exactly = 1) {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), isNull(), any())
            }
        }
    }

    describe("나머지 자잘한 분기 보강") {
        it("이벤트 병합(mergeEvents) 자체가 실패하면 원본 이벤트를 그대로 개별 발송해야 한다") {
            val event = issueEvent(setOf(receiver(2L)))
            val mail = NotificationMail(id = 600L, notificationEvent = event)
            every { notificationMailRepository.findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(any()) } returns listOf(mail)
            every { notificationMailRepository.delete(mail) } returns Unit
            every { notificationEventMerger.mergeEvents(listOf(event)) } throws RuntimeException("병합 실패")
            every { issueRepository.existsById(1L) } returns true
            every { messageResolver.getMessage(any<MergedNotificationEvent>(), any()) } returns "메시지"
            every { messageResolver.getPlainMessage(any<MergedNotificationEvent>(), any()) } returns "평문 메시지"
            every { issueRepository.findById(1L) } returns Optional.empty()

            scheduler().sendMail()

            verify(exactly = 1) { mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        it("POSTING_BODY_CHANGED 이벤트도 마크다운 렌더링 없이 원본 메시지를 그대로 사용한다") {
            val user = receiver(2L)
            val event = notifEvent(ResourceType.BOARD_POST, "1", setOf(user), eventType = EventType.POSTING_BODY_CHANGED)
            every { postingRepository.existsById(1L) } returns true
            every { postingRepository.findById(1L) } returns Optional.empty()

            runSendMail(event)

            verify(exactly = 0) { markdownService.render(any(), any(), any(), any()) }
            verify(exactly = 1) { mailRenderer.render("메시지", any(), any(), any(), any(), any()) }
        }

        it("이벤트의 created가 없으면(null) 현재 시각으로 발송 시각을 채운다") {
            val user = receiver(2L)
            val event = NotificationEvent(
                id = 1L, title = "제목", created = null,
                resourceType = ResourceType.ISSUE_POST, resourceId = "1",
                eventType = EventType.NEW_ISSUE, newValue = "본문",
                receivers = mutableSetOf(user)
            )
            every { issueRepository.existsById(1L) } returns true
            every { issueRepository.findById(1L) } returns Optional.empty()

            val sentDateSlot = slot<Date>()
            every {
                mailService.sendNotificationMail(any(), any(), any(), any(), any(), any(), any(), any(), any(), capture(sentDateSlot))
            } returns Unit

            runSendMail(event)

            sentDateSlot.isCaptured shouldBe true
        }
    }
})
