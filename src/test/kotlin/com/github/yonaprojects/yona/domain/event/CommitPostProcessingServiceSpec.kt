package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.CommitIssueReferenceService
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueEvent
import com.github.yonaprojects.yona.domain.issue.IssueEventRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.watch.WatchService
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommit
import com.github.yonaprojects.yona.domain.webhook.PushedVcsCommits
import com.github.yonaprojects.yona.domain.webhook.WebhookService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant

class CommitPostProcessingServiceSpec : DescribeSpec({
    val notificationEventRecorder = mockk<NotificationEventRecorder>()
    val issueRepository = mockk<IssueRepository>()
    val issueEventRepository = mockk<IssueEventRepository>()
    val webhookService = mockk<WebhookService>(relaxed = true)
    val watchService = mockk<WatchService>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val service = CommitPostProcessingService(
        notificationEventRecorder,
        CommitIssueReferenceService(issueRepository, issueEventRepository),
        webhookService,
        watchService,
        eventPublisher
    )
    val sender = User(id = 9L, loginId = "pusher", name = "Pusher", email = "pusher@example.com")
    val originalTime = Instant.parse("2000-01-01T00:00:00Z")
    val firstCommit = PushedVcsCommit("first-id", "fix #42 #42", "Author", "author@example.com", originalTime)
    val secondCommit = PushedVcsCommit("second-id", "also fixes #42", "Other author", "other@example.com", originalTime.plusSeconds(60))
    val pushed = PushedVcsCommits(listOf(firstCommit, secondCommit), listOf("refs/heads/main"))

    beforeTest {
        clearMocks(notificationEventRecorder, issueRepository, issueEventRepository, webhookService, watchService, eventPublisher)
        every { notificationEventRecorder.record(any()) } answers { firstArg() }
        every { watchService.findActualWatchers(any(), any(), any(), any(), eventType = any()) } returns emptySet()
        every { issueRepository.findByProjectAndNumber(any(), any()) } returns null
        every { issueEventRepository.save(any()) } answers { firstArg() }
    }

    describe("CommitPostProcessingService.process") {
        it("does nothing for an empty push") {
            val project = Project(id = 1L, name = "yona", vcs = "GIT")
            service.process(project, PushedVcsCommits(emptyList(), listOf("refs/heads/main")), sender)

            verify {
                listOf(notificationEventRecorder, issueRepository, issueEventRepository, webhookService, watchService, eventPublisher) wasNot Called
            }
        }

        for (vcs in listOf("GIT", "HG")) {
            for (refs in listOf(emptyList(), listOf("refs/heads/main"), listOf("refs/heads/main", "refs/heads/topic"))) {
                it("uses the first commit target and the ${refs.size}-ref title for $vcs") {
                    val project = Project(id = 1L, name = "yona", vcs = vcs)
                    val notification = slot<NotificationEvent>()
                    every { notificationEventRecorder.record(capture(notification)) } answers { firstArg() }
                    val push = pushed.copy(refNames = refs)

                    service.process(project, push, sender)

                    val title = if (refs.size == 1) {
                        "[yona] 2개의 커밋이 refs/heads/main 브랜치로 푸시되었습니다."
                    } else {
                        "[yona] 2개의 커밋이 푸시되었습니다."
                    }
                    notification.captured.title shouldBe title
                    notification.captured.newValue shouldBe title
                    notification.captured.resourceType shouldBe ResourceType.COMMIT
                    notification.captured.resourceId shouldBe firstCommit.id
                    notification.captured.eventType shouldBe EventType.NEW_COMMIT
                    notification.captured.senderId shouldBe sender.id
                    verify(exactly = 1) { webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, push) }
                }
            }
        }

        for (vcs in listOf("SVN", "svn", "SUBVERSION", "subversion")) {
            it("uses the project target for $vcs") {
                val project = Project(id = 73L, name = "yona", vcs = vcs)
                val notification = slot<NotificationEvent>()
                every { notificationEventRecorder.record(capture(notification)) } answers { firstArg() }

                service.process(project, pushed.copy(refNames = emptyList()), sender)

                notification.captured.resourceType shouldBe ResourceType.PROJECT
                notification.captured.resourceId shouldBe "73"
                notification.captured.title shouldBe "[yona] 2개의 커밋이 푸시되었습니다."
            }
        }

        it("filters the sender by id and publishes the recorded result before webhook and issue references") {
            val project = Project(id = 1L, name = "yona", vcs = "GIT")
            val watcher = User(id = 10L, loginId = "watcher")
            val senderAsWatcher = User(id = sender.id, loginId = "another-instance")
            every {
                watchService.findActualWatchers(emptySet(), ResourceType.PROJECT, "1", 1L, eventType = EventType.NEW_COMMIT)
            } returns setOf(senderAsWatcher, watcher)
            val notification = slot<NotificationEvent>()
            val recorded = NotificationEvent(id = 100L, title = "recorded result")
            every { notificationEventRecorder.record(capture(notification)) } returns recorded
            val issue = Issue(id = 42L, number = 42L, title = "bug", project = project)
            every { issueRepository.findByProjectAndNumber(project, 42L) } returns issue
            val events = mutableListOf<IssueEvent>()
            every { issueEventRepository.save(capture(events)) } answers { firstArg() }
            val before = Instant.now()

            service.process(project, pushed, sender)

            val after = Instant.now()
            notification.captured.receivers shouldBe mutableSetOf(watcher)
            events.size shouldBe 2
            events.map { it.newValue } shouldBe listOf(firstCommit.id, secondCommit.id)
            events.forEach {
                it.issue shouldBe issue
                it.senderLoginId shouldBe sender.loginId
                it.senderEmail shouldBe sender.email
                it.oldValue shouldBe null
                it.eventType shouldBe EventType.ISSUE_REFERRED_FROM_COMMIT
                (it.created >= before && it.created <= after) shouldBe true
            }
            verifyOrder {
                watchService.findActualWatchers(emptySet(), ResourceType.PROJECT, "1", 1L, eventType = EventType.NEW_COMMIT)
                notificationEventRecorder.record(any())
                eventPublisher.publishEvent(recorded)
                webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, pushed)
                issueRepository.findByProjectAndNumber(project, 42L)
                issueEventRepository.save(events[0])
                issueRepository.findByProjectAndNumber(project, 42L)
                issueEventRepository.save(events[1])
            }
            verify(exactly = 1) { eventPublisher.publishEvent(any<Any>()) }
        }

        it("still sends the webhook and records references when the recorder returns null") {
            val project = Project(id = 1L, name = "yona", vcs = "HG")
            every { notificationEventRecorder.record(any()) } returns null
            val issue = Issue(id = 42L, number = 42L, title = "bug", project = project)
            every { issueRepository.findByProjectAndNumber(project, 42L) } returns issue
            val event = slot<IssueEvent>()
            every { issueEventRepository.save(capture(event)) } answers { firstArg() }
            val push = pushed.copy(commits = listOf(firstCommit))

            service.process(project, push, sender)

            event.captured.issue shouldBe issue
            event.captured.newValue shouldBe firstCommit.id
            event.captured.senderLoginId shouldBe sender.loginId
            event.captured.senderEmail shouldBe sender.email
            event.captured.eventType shouldBe EventType.ISSUE_REFERRED_FROM_COMMIT
            verify { eventPublisher wasNot Called }
            verifyOrder {
                notificationEventRecorder.record(any())
                webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, push)
                issueEventRepository.save(any())
            }
        }

        it("does not record issue references if webhook delivery throws") {
            val project = Project(id = 1L, name = "yona", vcs = "GIT")
            val failure = IllegalStateException("webhook failed")
            every { webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, pushed) } throws failure

            shouldThrow<IllegalStateException> { service.process(project, pushed, sender) } shouldBe failure

            verifyOrder {
                notificationEventRecorder.record(any())
                eventPublisher.publishEvent(any<Any>())
                webhookService.sendWebhook(project, EventType.NEW_COMMIT, sender, pushed)
            }
            verify { listOf(issueRepository, issueEventRepository) wasNot Called }
        }
    }
})
