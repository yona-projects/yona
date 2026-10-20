package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

// v1.6 NotificationEvent의 webhookRequest(...)는 add()의 수신자 유무·병합/상쇄 여부와 무관하게 호출됐다.
// next는 record()가 null을 돌려주면(수신자 없음/상쇄) 서비스가 publish를 하지 않아 웹훅이 사라졌다.
// record()가 저장하지 않은 이벤트는 recorder가 직접 publish해 웹훅 리스너가 받게 한다.
class NotificationEventRecorderWebhookSpec : DescribeSpec({
    val eventRepo = mockk<NotificationEventRepository>(relaxed = true)
    val mailRepo = mockk<NotificationMailRepository>(relaxed = true)
    val receiverFilter = mockk<NotificationReceiverFilter>(relaxed = true)
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val recorder = NotificationEventRecorder(eventRepo, mailRepo, SimpleMeterRegistry(), receiverFilter, publisher)
    val receiver = User(id = 2L, loginId = "r", name = "수신자")

    fun event(vararg receivers: User) = NotificationEvent(
        title = "t", senderId = 9L, resourceType = ResourceType.ISSUE_POST, resourceId = "10",
        eventType = EventType.NEW_ISSUE, receivers = receivers.toMutableSet()
    )

    beforeTest {
        clearMocks(eventRepo, mailRepo, publisher, answers = false)
        every { eventRepo.findFirstByResourceTypeAndResourceIdAndCreatedAfterOrderByIdDesc(any(), any(), any()) } returns null
        every { eventRepo.save(any<NotificationEvent>()) } answers { firstArg() }
        every { mailRepo.save(any<NotificationMail>()) } answers { firstArg() }
    }

    describe("NotificationEventRecorder.record - 웹훅용 publish") {
        it("수신자가 없어 저장하지 않는 이벤트도 웹훅 리스너로 publish한다") {
            val e = event()

            recorder.record(e) shouldBe null

            verify(exactly = 1) { publisher.publishEvent(e) }
            verify(exactly = 0) { eventRepo.save(any<NotificationEvent>()) }
        }

        it("읽기 필터로 수신자가 모두 제거돼도 publish한다") {
            val e = event(receiver)
            every { receiverFilter.filter(e) } answers { e.receivers.clear() }

            recorder.record(e) shouldBe null

            verify(exactly = 1) { publisher.publishEvent(e) }
        }

        // v1.6은 라벨·공유자 변경 같은 이벤트에 웹훅을 보내지 않았다 — 저장하지 않은 이벤트 중 웹훅 대상이 아닌 것은 publish하지 않는다.
        it("v1.6에서 웹훅 대상이 아니던 이벤트(라벨/공유자 변경, 게시글 삭제)는 저장되지 않아도 publish하지 않는다") {
            for (type in listOf(EventType.ISSUE_LABEL_CHANGED, EventType.ISSUE_SHARER_CHANGED, EventType.RESOURCE_DELETED)) {
                val e = NotificationEvent(
                    title = "t", senderId = 9L, resourceType = ResourceType.ISSUE_POST, resourceId = "10", eventType = type,
                    receivers = mutableSetOf()
                )
                recorder.record(e) shouldBe null
            }

            verify(exactly = 0) { publisher.publishEvent(any<Any>()) }
        }

        it("저장된 이벤트는 호출자가 publish하므로 recorder는 중복 publish하지 않는다") {
            val e = event(receiver)

            recorder.record(e) shouldBe e

            verify(exactly = 0) { publisher.publishEvent(any<Any>()) }
        }
    }
})
