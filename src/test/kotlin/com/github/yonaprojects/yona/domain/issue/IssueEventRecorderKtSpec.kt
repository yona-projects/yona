package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant

// yona models/IssueEvent.java의 add()/addWithoutSkipEvent() 대응(P1-38). recordWithDraftMerge()는
// IssueEventRepository의 확장 함수라 IssueServiceImpl 등을 통해 간접적으로만 테스트돼 LINE은 이미
// 100%였지만, 실제로 발생하는 모든 분기 조합을 직접 목으로 격리해 검증한 적은 없었다.
class IssueEventRecorderKtSpec : DescribeSpec({
    val repository = mockk<IssueEventRepository>()
    val project = Project(id = 1L, name = "proj", owner = "owner")
    val issue = Issue(id = 100L, title = "이슈", project = project, number = 1L)
    lateinit var meterRegistry: SimpleMeterRegistry

    beforeTest {
        clearMocks(repository)
        every { repository.delete(any()) } returns Unit
        meterRegistry = SimpleMeterRegistry()
    }

    // yona-wiki P3-01(Observability) 계측 지점 2 대응 — outcome 태그별 카운터 값 확인 헬퍼.
    fun draftMergeCount(outcome: String) =
        meterRegistry.counter("yona.event.draft_merge", "resourceType", "issue", "outcome", outcome).count()

    fun eventOf(oldValue: String?, newValue: String?, eventType: EventType = EventType.ISSUE_STATE_CHANGED, senderLoginId: String? = "gildong") =
        IssueEvent(
            issue = issue,
            senderLoginId = senderLoginId,
            oldValue = oldValue,
            newValue = newValue,
            created = Instant.now(),
            eventType = eventType
        )

    describe("recordWithDraftMerge") {
        it("직전 이벤트가 없으면 병합 없이 그대로 저장해야 한다") {
            every { repository.findFirstByIssueAndCreatedAfterOrderByIdDesc(issue, any()) } returns null
            val event = eventOf("OPEN", "CLOSED")
            every { repository.save(event) } returns event

            val result = repository.recordWithDraftMerge(event, skipWaypoint = true, meterRegistry = meterRegistry)

            result shouldBe event
            verify(exactly = 0) { repository.delete(any()) }
            draftMergeCount("saved") shouldBe 1.0
            draftMergeCount("merged_or_cancelled") shouldBe 0.0
        }

        it("직전 이벤트가 있어도 eventType이 다르면 병합 없이 그대로 저장해야 한다") {
            val lastEvent = eventOf("OPEN", "CLOSED", eventType = EventType.ISSUE_STATE_CHANGED)
            every { repository.findFirstByIssueAndCreatedAfterOrderByIdDesc(issue, any()) } returns lastEvent
            val event = eventOf("a", "b", eventType = EventType.ISSUE_ASSIGNEE_CHANGED)
            every { repository.save(event) } returns event

            val result = repository.recordWithDraftMerge(event, skipWaypoint = true, meterRegistry = meterRegistry)

            result shouldBe event
            verify(exactly = 0) { repository.delete(any()) }
        }

        it("직전 이벤트가 있고 eventType은 같아도 senderLoginId가 다르면 병합 없이 그대로 저장해야 한다") {
            val lastEvent = eventOf("OPEN", "CLOSED", senderLoginId = "gildong")
            every { repository.findFirstByIssueAndCreatedAfterOrderByIdDesc(issue, any()) } returns lastEvent
            val event = eventOf("a", "b", senderLoginId = "other")
            every { repository.save(event) } returns event

            val result = repository.recordWithDraftMerge(event, skipWaypoint = true, meterRegistry = meterRegistry)

            result shouldBe event
            verify(exactly = 0) { repository.delete(any()) }
        }

        it("skipWaypoint=true이고 병합 후 값이 달라지면 직전 이벤트를 지우고 병합된 이벤트를 저장해야 한다") {
            val lastEvent = eventOf("OPEN", "CLOSED")
            every { repository.findFirstByIssueAndCreatedAfterOrderByIdDesc(issue, any()) } returns lastEvent
            val event = eventOf("CLOSED", "REJECTED")
            every { repository.save(event) } returns event

            val result = repository.recordWithDraftMerge(event, skipWaypoint = true, meterRegistry = meterRegistry)

            result shouldBe event
            event.oldValue shouldBe "OPEN"
            verify(exactly = 1) { repository.delete(lastEvent) }
            verify(exactly = 1) { repository.save(event) }
        }

        it("skipWaypoint=true이고 병합 후 정확히 원상복구되면 직전 이벤트를 지우고 저장 없이 null을 반환해야 한다") {
            val lastEvent = eventOf("OPEN", "CLOSED")
            every { repository.findFirstByIssueAndCreatedAfterOrderByIdDesc(issue, any()) } returns lastEvent
            val event = eventOf("CLOSED", "OPEN")

            val result = repository.recordWithDraftMerge(event, skipWaypoint = true, meterRegistry = meterRegistry)

            result shouldBe null
            event.oldValue shouldBe "OPEN"
            verify(exactly = 1) { repository.delete(lastEvent) }
            verify(exactly = 0) { repository.save(any()) }
            draftMergeCount("merged_or_cancelled") shouldBe 1.0
            draftMergeCount("saved") shouldBe 0.0
        }

        it("skipWaypoint=false이고 정확히 되돌아오면 직전 이벤트를 지우고 저장 없이 null을 반환해야 한다") {
            val lastEvent = eventOf("", "sharer1")
            every { repository.findFirstByIssueAndCreatedAfterOrderByIdDesc(issue, any()) } returns lastEvent
            val event = eventOf("sharer1", "")

            val result = repository.recordWithDraftMerge(event, skipWaypoint = false, meterRegistry = meterRegistry)

            result shouldBe null
            verify(exactly = 1) { repository.delete(lastEvent) }
            verify(exactly = 0) { repository.save(any()) }
        }

        it("skipWaypoint=false이고 첫 값부터 직전 이벤트와 이어지지 않으면 병합/상쇄 없이 그대로 저장해야 한다") {
            val lastEvent = eventOf("", "sharer1")
            every { repository.findFirstByIssueAndCreatedAfterOrderByIdDesc(issue, any()) } returns lastEvent
            val event = eventOf("unrelated", "sharer3")
            every { repository.save(event) } returns event

            val result = repository.recordWithDraftMerge(event, skipWaypoint = false, meterRegistry = meterRegistry)

            result shouldBe event
            verify(exactly = 0) { repository.delete(any()) }
        }

        it("skipWaypoint=false이고 첫 값은 이어지지만 두 번째 값이 되돌아오지 않으면 병합/상쇄 없이 그대로 저장해야 한다") {
            val lastEvent = eventOf("", "sharer1")
            every { repository.findFirstByIssueAndCreatedAfterOrderByIdDesc(issue, any()) } returns lastEvent
            val event = eventOf("sharer1", "sharer2")
            every { repository.save(event) } returns event

            val result = repository.recordWithDraftMerge(event, skipWaypoint = false, meterRegistry = meterRegistry)

            result shouldBe event
            verify(exactly = 0) { repository.delete(any()) }
        }
    }
})
