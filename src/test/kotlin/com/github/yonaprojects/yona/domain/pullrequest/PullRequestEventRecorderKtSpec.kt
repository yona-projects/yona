package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

// yona models/PullRequestEvent.java의 add() 대응(P1-40). recordWithDraftMerge()는
// PullRequestEventRepository의 확장 함수라 간접적으로만 테스트돼 왔다 — needToDelete의
// 4항 AND 조건(lastEvent!=null / event.eventType==REVIEW_STATE_CHANGED /
// lastEvent.eventType==REVIEW_STATE_CHANGED / senderLoginId 일치) 각 단락평가 지점을
// mockk로 격리해 직접 검증한다.
class PullRequestEventRecorderKtSpec : DescribeSpec({
    val repository = mockk<PullRequestEventRepository>()
    lateinit var meterRegistry: SimpleMeterRegistry

    beforeTest {
        clearMocks(repository)
        every { repository.delete(any()) } returns Unit
        meterRegistry = SimpleMeterRegistry()
    }

    // yona-wiki P3-01(Observability) 계측 지점 2 대응 — outcome 태그별 카운터 값 확인 헬퍼.
    fun draftMergeCount(outcome: String) =
        meterRegistry.counter("yona.event.draft_merge", "resourceType", "pull_request", "outcome", outcome).count()
    val pullRequest = PullRequest(
        toProject = Project(id = 1L, name = "proj", owner = "owner"),
        fromProject = Project(id = 1L, name = "proj", owner = "owner"),
        contributor = User(id = 1L, loginId = "gildong", name = "홍길동")
    )

    fun eventOf(
        oldValue: String?,
        newValue: String?,
        eventType: EventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED,
        senderLoginId: String? = "gildong"
    ) = PullRequestEvent(
        pullRequest = pullRequest,
        senderLoginId = senderLoginId,
        oldValue = oldValue,
        newValue = newValue,
        created = Instant.now(),
        eventType = eventType
    )

    describe("recordWithDraftMerge") {
        it("직전 이벤트가 없으면 병합 없이 그대로 저장해야 한다") {
            every { repository.findFirstByPullRequestAndCreatedAfterOrderByCreatedDesc(pullRequest, any()) } returns null
            val event = eventOf("OPEN", "CLOSED")
            every { repository.save(event) } returns event

            val result = repository.recordWithDraftMerge(event, meterRegistry)

            result shouldBe event
            verify(exactly = 0) { repository.delete(any()) }
            draftMergeCount("saved") shouldBe 1.0
            draftMergeCount("merged_or_cancelled") shouldBe 0.0
        }

        it("직전 이벤트가 있어도 새 이벤트의 eventType이 REVIEW_STATE_CHANGED가 아니면 그대로 저장해야 한다") {
            val lastEvent = eventOf("OPEN", "CLOSED", eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED)
            every { repository.findFirstByPullRequestAndCreatedAfterOrderByCreatedDesc(pullRequest, any()) } returns lastEvent
            val event = eventOf("a", "b", eventType = EventType.PULL_REQUEST_STATE_CHANGED)
            every { repository.save(event) } returns event

            val result = repository.recordWithDraftMerge(event, meterRegistry)

            result shouldBe event
            verify(exactly = 0) { repository.delete(any()) }
        }

        it("새 이벤트는 REVIEW_STATE_CHANGED여도 직전 이벤트의 eventType이 다르면 그대로 저장해야 한다") {
            val lastEvent = eventOf("OPEN", "CLOSED", eventType = EventType.PULL_REQUEST_STATE_CHANGED)
            every { repository.findFirstByPullRequestAndCreatedAfterOrderByCreatedDesc(pullRequest, any()) } returns lastEvent
            val event = eventOf("a", "b", eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED)
            every { repository.save(event) } returns event

            val result = repository.recordWithDraftMerge(event, meterRegistry)

            result shouldBe event
            verify(exactly = 0) { repository.delete(any()) }
        }

        it("둘 다 REVIEW_STATE_CHANGED여도 senderLoginId가 다르면 그대로 저장해야 한다") {
            val lastEvent = eventOf("OPEN", "CLOSED", senderLoginId = "gildong")
            every { repository.findFirstByPullRequestAndCreatedAfterOrderByCreatedDesc(pullRequest, any()) } returns lastEvent
            val event = eventOf("a", "b", senderLoginId = "other")
            every { repository.save(event) } returns event

            val result = repository.recordWithDraftMerge(event, meterRegistry)

            result shouldBe event
            verify(exactly = 0) { repository.delete(any()) }
        }

        it("둘 다 REVIEW_STATE_CHANGED이고 senderLoginId도 같으면 직전 이벤트를 지우고 저장 없이 null을 반환해야 한다") {
            val lastEvent = eventOf("OPEN", "CLOSED", senderLoginId = "gildong")
            every { repository.findFirstByPullRequestAndCreatedAfterOrderByCreatedDesc(pullRequest, any()) } returns lastEvent
            val event = eventOf("CLOSED", "REJECTED", senderLoginId = "gildong")

            val result = repository.recordWithDraftMerge(event, meterRegistry)

            result shouldBe null
            verify(exactly = 1) { repository.delete(lastEvent) }
            verify(exactly = 0) { repository.save(any()) }
            draftMergeCount("merged_or_cancelled") shouldBe 1.0
            draftMergeCount("saved") shouldBe 0.0
        }
    }
})
