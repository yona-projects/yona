package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

class CommitIssueReferenceServiceSpec : DescribeSpec({
    val issueRepository = mockk<IssueRepository>()
    val issueEventRepository = mockk<IssueEventRepository>()
    val service = CommitIssueReferenceService(issueRepository, issueEventRepository)
    val project = Project(id = 1L, name = "project", owner = "pusher")
    val sender = User(id = 9L, loginId = "pusher", email = "pusher@example.com")
    val saved = mutableListOf<IssueEvent>()

    beforeTest {
        clearMocks(issueRepository, issueEventRepository)
        saved.clear()
        every { issueEventRepository.save(capture(saved)) } answers { firstArg() }
    }

    describe("record") {
        it("records a commit without a Yona sender at its original time") {
            val issue = Issue(id = 42L, project = project, number = 42L)
            every { issueRepository.findByProjectAndNumber(project, 42L) } returns issue
            val original = Instant.parse("2007-05-06T07:08:09Z")
            service.record(project, CommitReference("r7", "refs #42", sender = null, created = original))
            saved.single().senderLoginId shouldBe null
            saved.single().senderEmail shouldBe null
            saved.single().created shouldBe original
            saved.single().newValue shouldBe "r7"
        }

        it("preserves the pusher, processing time and exact ID without deduplicating repeated processing") {
            val issue = Issue(id = 42L, project = project, number = 42L)
            every { issueRepository.findByProjectAndNumber(project, 42L) } returns issue
            val before = Instant.now()
            repeat(2) { service.record(project, CommitReference("same-id", "#42 #42", sender)) }
            val after = Instant.now()
            saved.size shouldBe 2
            saved.forEach {
                it.issue shouldBe issue
                it.senderLoginId shouldBe sender.loginId
                it.senderEmail shouldBe sender.email
                it.newValue shouldBe "same-id"
                it.oldValue shouldBe null
                it.eventType shouldBe EventType.ISSUE_REFERRED_FROM_COMMIT
                (it.created >= before && it.created <= after) shouldBe true
            }
            verify(exactly = 2) { issueRepository.findByProjectAndNumber(project, 42L) }
        }

        it("looks up each number only in the supplied project and skips missing issues") {
            val otherProject = Project(id = 2L, name = "other")
            val first = Issue(id = 1L, project = project, number = 1L)
            val second = Issue(id = 2L, project = project, number = 2L)
            val other = Issue(id = 3L, project = otherProject, number = 1L)
            every { issueRepository.findByProjectAndNumber(project, 1L) } returns first
            every { issueRepository.findByProjectAndNumber(project, 2L) } returns second
            every { issueRepository.findByProjectAndNumber(project, 999L) } returns null
            every { issueRepository.findByProjectAndNumber(otherProject, 1L) } returns other
            service.record(project, CommitReference("unchanged-id", "#1 #2 #999 #1", sender))
            saved.map { it.issue }.toSet() shouldBe setOf(first, second)
            saved.size shouldBe 2
            verify(exactly = 0) { issueRepository.findByProjectAndNumber(otherProject, any()) }
        }

        it("does not query or save when no issue is referenced") {
            service.record(project, CommitReference("id", "ordinary commit", sender))
            service.record(project, CommitReference("id", "", sender))
            verify(exactly = 0) { issueRepository.findByProjectAndNumber(any(), any()) }
            verify(exactly = 0) { issueEventRepository.save(any()) }
        }

        it("propagates direct save failures") {
            every { issueRepository.findByProjectAndNumber(project, 42L) } returns Issue(project = project, number = 42L)
            val failure = IllegalStateException("save failed")
            every { issueEventRepository.save(any()) } throws failure
            shouldThrow<IllegalStateException> {
                service.record(project, CommitReference("id", "#42", sender))
            } shouldBe failure
            verify(exactly = 1) { issueEventRepository.save(any()) }
        }
    }
})
