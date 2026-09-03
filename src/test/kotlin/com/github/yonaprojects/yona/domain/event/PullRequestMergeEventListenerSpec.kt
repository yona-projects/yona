package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueService
import com.github.yonaprojects.yona.domain.notification.NotificationEvent
import com.github.yonaprojects.yona.domain.notification.NotificationEventRecorder
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestCommit
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEvent
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEventRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestMergeResult
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestService
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

class PullRequestMergeEventListenerSpec : DescribeSpec({
    val pullRequestRepository = mockk<PullRequestRepository>()
    val pullRequestCommitRepository = mockk<PullRequestCommitRepository>()
    val issueRepository = mockk<IssueRepository>()
    val issueService = mockk<IssueService>()
    val pullRequestService = mockk<PullRequestService>()
    val notificationEventRecorder = mockk<NotificationEventRecorder>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val pullRequestEventRepository = mockk<PullRequestEventRepository>(relaxed = true)
    // 매 테스트 사이엔 clear()로 리셋한다(listener가 val이라 registry 참조 자체를 나중에 바꿀 수 없음).
    val meterRegistry = SimpleMeterRegistry()

    val listener = PullRequestMergeEventListener(
        pullRequestRepository, pullRequestCommitRepository, issueRepository, issueService,
        pullRequestService, notificationEventRecorder, eventPublisher, pullRequestEventRepository,
        meterRegistry
    )

    val project = Project(id = 1L, name = "yona-project", owner = "gildong")
    val contributor = User(id = 5L, loginId = "contributor", name = "기여자")
    val sender = User(id = 9L, loginId = "pusher", name = "푸셔")

    fun pr(id: Long, conflict: Boolean?) = PullRequest(
        id = id, title = "관련 PR", body = "...", toProject = project, fromProject = project,
        toBranch = "master", fromBranch = "feature", contributor = contributor,
        state = State.OPEN, number = id, isConflict = conflict, isMerging = false
    )

    beforeTest {
        clearMocks(
            pullRequestRepository, pullRequestCommitRepository, issueRepository, issueService,
            pullRequestService, notificationEventRecorder, eventPublisher, pullRequestEventRepository, answers = false
        )
        every { notificationEventRecorder.record(any()) } answers { firstArg() }
        meterRegistry.clear()
    }

    describe("PullRequestMergeEventListener.handlePullRequestMergeEvent") {

        it("should return if PR not found") {
            every { pullRequestRepository.findById(999L) } returns Optional.empty()
            listener.handlePullRequestMergeEvent(PullRequestMergeEvent(pullRequestId = 999L, sender = sender, isNewPullRequest = false))
            verify(exactly = 0) { pullRequestRepository.save(any()) }
        }
        
        it("should handle exceptions when closing issues") {
            val mergedPr = pr(201L, conflict = false)
            every { pullRequestRepository.findById(201L) } returns Optional.of(mergedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestEventRepository.save(any()) } answers { firstArg() }
            every { pullRequestCommitRepository.findByPullRequest(mergedPr) } throws RuntimeException("DB Error")
            
            listener.handlePullRequestMergeEvent(PullRequestMergeEvent(pullRequestId = 201L, sender = sender, isNewPullRequest = false))
            
            mergedPr.state shouldBe State.MERGED
        }

        it("PR을 병합하면 PullRequestEvent 타임라인 항목이 생성되어야 한다") {
            val mergedPr = pr(200L, conflict = false)
            every { pullRequestRepository.findById(200L) } returns Optional.of(mergedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestCommitRepository.findByPullRequest(mergedPr) } returns emptyList()

            val captured = slot<PullRequestEvent>()
            every { pullRequestEventRepository.save(capture(captured)) } answers { firstArg() }

            listener.handlePullRequestMergeEvent(PullRequestMergeEvent(pullRequestId = 200L, sender = sender, isNewPullRequest = false))

            mergedPr.state shouldBe State.MERGED
            captured.captured.eventType.name shouldBe "PULL_REQUEST_STATE_CHANGED"
            captured.captured.newValue shouldBe "MERGED"
            captured.captured.senderLoginId shouldBe "pusher"
            meterRegistry.counter("yona.pullrequest.merge.processed", "trigger", "direct").count() shouldBe 1.0
        }
    }

    
    describe("PullRequestMergeEventListener.closeReferredIssues") {
        it("should close issues found in PR title, body, and commit messages") {
            val pr = pr(300L, conflict = false)
            pr.title = "fix #1"
            pr.body = "close #2"
            
            val commit1 = mockk<PullRequestCommit>()
            every { commit1.commitMessage } returns "resolve #3"
            every { pullRequestCommitRepository.findByPullRequest(pr) } returns listOf(commit1)
            every { issueService.changeState(any(), any(), any()) } returns mockk(relaxed = true)
            
            val issue1 = mockk<Issue>(relaxed = true)
            every { issue1.id } returns 101L
            every { issue1.number } returns 1L
            every { issue1.state } returns State.OPEN
            
            val issue2 = mockk<Issue>(relaxed = true)
            every { issue2.id } returns 102L
            every { issue2.number } returns 2L
            every { issue2.state } returns State.OPEN
            
            val issue3 = mockk<Issue>(relaxed = true)
            every { issue3.id } returns 103L
            every { issue3.number } returns 3L
            every { issue3.state } returns State.CLOSED
            
            every { issueRepository.findByProjectAndNumber(project, 1L) } returns issue1
            every { issueRepository.findByProjectAndNumber(project, 2L) } returns issue2
            every { issueRepository.findByProjectAndNumber(project, 3L) } returns issue3
            every { issueRepository.findByProjectAndNumber(project, 4L) } returns null
            
            listener.closeReferredIssues(pr, "pusher")
            
            verify(exactly = 1) { issueService.changeState(101L, State.CLOSED, "pusher") }
            verify(exactly = 1) { issueService.changeState(102L, State.CLOSED, "pusher") }
            verify(exactly = 0) { issueService.changeState(103L, any(), any()) }
        }
        
        // closePattern이 매치돼도 숫자가 Long 범위를 넘으면 toLongOrNull()이 null을 반환해
        // ?.let{} 이 스킵된다 — 기존 테스트는 전부 작은 숫자만 써서 이 분기가 비어 있었다.
        it("이슈 번호가 Long 범위를 넘으면 무시하고 나머지 정상 번호만 처리해야 한다") {
            val pr = pr(302L, conflict = false)
            pr.title = "fix #99999999999999999999 and closes #5"
            pr.body = null
            every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()
            every { issueService.changeState(any(), any(), any()) } returns mockk(relaxed = true)

            val issue5 = mockk<Issue>(relaxed = true)
            every { issue5.id } returns 105L
            every { issue5.number } returns 5L
            every { issue5.state } returns State.OPEN
            every { issueRepository.findByProjectAndNumber(project, 5L) } returns issue5

            listener.closeReferredIssues(pr, "pusher")

            verify(exactly = 1) { issueService.changeState(105L, State.CLOSED, "pusher") }
            verify(exactly = 1) { issueRepository.findByProjectAndNumber(project, any()) }
        }

        it("언급된 이슈 번호가 프로젝트에 없으면 건너뛰고 나머지만 처리해야 한다") {
            val pr = pr(303L, conflict = false)
            pr.title = "fix #7"
            pr.body = null
            every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()
            every { issueService.changeState(any(), any(), any()) } returns mockk(relaxed = true)
            every { issueRepository.findByProjectAndNumber(project, 7L) } returns null

            listener.closeReferredIssues(pr, "pusher")

            verify(exactly = 1) { issueRepository.findByProjectAndNumber(project, 7L) }
            verify(exactly = 0) { issueService.changeState(any(), any(), any()) }
        }

        it("should return early if no issue numbers are found") {
            val pr = pr(301L, conflict = false)
            pr.title = "just a PR"
            pr.body = null
            every { pullRequestCommitRepository.findByPullRequest(pr) } returns emptyList()
            
            listener.closeReferredIssues(pr, "pusher")
            
            verify(exactly = 0) { issueRepository.findByProjectAndNumber(any(), any()) }
        }
    }

    describe("PullRequestMergeEventListener.handleRelatedPullRequestMergeEvent") {

        it("should ignore PRs without ID") {
            val noIdPr = pr(100L, conflict = false)
            noIdPr.id = null
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(noIdPr)
            
            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))
            
            verify(exactly = 0) { pullRequestRepository.save(any()) }
        }
        
        it("should handle when contributor is the same as sender for notification") {
            val relatedPr = pr(105L, conflict = false)
            relatedPr.contributor = sender
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(105L, sender, false) } answers {
                relatedPr.isConflict = true
                PullRequestMergeResult(pullRequest = relatedPr)
            }
            every { pullRequestRepository.findById(105L) } returns Optional.of(relatedPr)
            
            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))
            
            verify(exactly = 1) { eventPublisher.publishEvent(any<NotificationEvent>()) }
        }

        it("재병합 검사 후 isMerging을 다시 false로 되돌려야 한다") {
            val relatedPr = pr(100L, conflict = false)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(100L, sender, false) } returns PullRequestMergeResult(pullRequest = relatedPr)
            every { pullRequestRepository.findById(100L) } returns Optional.of(relatedPr)

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            relatedPr.isMerging shouldBe false
            verify(exactly = 1) { pullRequestService.processMergeCheck(100L, sender, false) }
            // yona-wiki P3-01(Observability) 계측 지점 5 검증 — 관련 PR 재검사 1건 처리 + 소요시간 기록.
            meterRegistry.counter("yona.pullrequest.merge.processed", "trigger", "related").count() shouldBe 1.0
            meterRegistry.timer("yona.pullrequest.merge.check.duration").count() shouldBe 1L
        }

        it("충돌이 새로 발생하면(false to true) 알림 이벤트를 발행해야 한다") {
            val relatedPr = pr(101L, conflict = false)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(101L, sender, false) } answers {
                relatedPr.isConflict = true
                PullRequestMergeResult(pullRequest = relatedPr)
            }
            every { pullRequestRepository.findById(101L) } returns Optional.of(relatedPr)

            val captured = slot<NotificationEvent>()
            every { eventPublisher.publishEvent(capture(captured)) } returns Unit

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            verify(exactly = 1) { eventPublisher.publishEvent(any<NotificationEvent>()) }
            captured.captured.eventType.name shouldBe "PULL_REQUEST_MERGED"
            meterRegistry.counter("yona.pullrequest.conflict.transition", "direction", "into_conflict").count() shouldBe 1.0
        }

        it("충돌이 해소되면(true to false) 알림 이벤트를 발행해야 한다") {
            val relatedPr = pr(102L, conflict = true)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(102L, sender, false) } answers {
                relatedPr.isConflict = false
                PullRequestMergeResult(pullRequest = relatedPr)
            }
            every { pullRequestRepository.findById(102L) } returns Optional.of(relatedPr)

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            verify(exactly = 1) { eventPublisher.publishEvent(any<NotificationEvent>()) }
            meterRegistry.counter("yona.pullrequest.conflict.transition", "direction", "resolved").count() shouldBe 1.0
        }

        it("충돌 상태에 변화가 없으면 알림 이벤트를 발행하지 않아야 한다") {
            val relatedPr = pr(103L, conflict = false)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(103L, sender, false) } returns PullRequestMergeResult(pullRequest = relatedPr)
            every { pullRequestRepository.findById(103L) } returns Optional.of(relatedPr)

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            verify(exactly = 0) { eventPublisher.publishEvent(any<NotificationEvent>()) }
        }

        // pullRequestRepository.findById(id).orElse(pullRequest) — 기존 테스트는 전부 findById가
        // Optional.of(...)를 반환해서 orElse의 fallback(Optional.empty())쪽이 비어 있었다.
        it("재조회 결과가 없으면(Optional.empty) 기존 pullRequest 객체로 계속 진행해야 한다") {
            val relatedPr = pr(106L, conflict = false)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(106L, sender, false) } returns PullRequestMergeResult(pullRequest = relatedPr)
            every { pullRequestRepository.findById(106L) } returns Optional.empty()

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            relatedPr.isMerging shouldBe false
            verify(exactly = 1) { pullRequestRepository.findById(106L) }
        }

        it("isConflict가 null이면 false로 취급해 재검사 후 충돌이 발생하면 알림을 발행해야 한다") {
            val relatedPr = pr(107L, conflict = null)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(107L, sender, false) } answers {
                relatedPr.isConflict = null
                PullRequestMergeResult(pullRequest = relatedPr)
            }
            every { pullRequestRepository.findById(107L) } returns Optional.of(relatedPr)

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            verify(exactly = 0) { eventPublisher.publishEvent(any<NotificationEvent>()) }
        }

        it("notificationEventRecorder.record()가 null을 반환하면 이벤트를 발행하지 않아야 한다") {
            val relatedPr = pr(108L, conflict = false)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(108L, sender, false) } answers {
                relatedPr.isConflict = true
                PullRequestMergeResult(pullRequest = relatedPr)
            }
            every { pullRequestRepository.findById(108L) } returns Optional.of(relatedPr)
            every { notificationEventRecorder.record(any()) } returns null

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            verify(exactly = 0) { eventPublisher.publishEvent(any<NotificationEvent>()) }
        }

        it("processMergeCheck가 예외를 던져도 isMerging은 false로 복구되어야 한다") {
            val relatedPr = pr(104L, conflict = false)
            every { pullRequestRepository.findRelatedPullRequests(project, "feature") } returns listOf(relatedPr)
            every { pullRequestRepository.save(any()) } answers { firstArg() }
            every { pullRequestService.processMergeCheck(104L, sender, false) } throws RuntimeException("git error")
            every { pullRequestRepository.findById(104L) } returns Optional.of(relatedPr)

            listener.handleRelatedPullRequestMergeEvent(RelatedPullRequestMergeEvent(project, "feature", sender))

            relatedPr.isMerging shouldBe false
        }
    }
})
