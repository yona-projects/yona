package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

// yona notification/INotificationEvent.java 대응 (P1-27 정정). NotificationEvent(단일 이벤트)와
// MergedNotificationEvent(병합 이벤트)가 legacy처럼 진짜 다형적으로 다뤄지는지 검증한다 —
// 이전에는 MergedNotificationEvent가 이 인터페이스 없이 NotificationEvent를 감싸는 단순 래퍼
// 클래스였는데, 사용자 지적으로 인터페이스 기반 다형성을 되돌렸다.
class INotificationEventSpec : DescribeSpec({
    val user1 = User(id = 1L, loginId = "u1", name = "유저1")
    val user2 = User(id = 2L, loginId = "u2", name = "유저2")

    // 어떤 구현체가 오든(단일/병합) 같은 계약으로 다룰 수 있어야 한다는 것 자체가 이 테스트의 핵심.
    fun describeEvent(event: INotificationEvent): String =
        "[${event.eventType}] ${event.resourceType}/${event.resourceId} title=${event.title} receivers=${event.receivers.size}"

    describe("NotificationEvent와 MergedNotificationEvent는 INotificationEvent로 다형적으로 다뤄져야 한다") {
        it("NotificationEvent를 INotificationEvent로 취급해도 자기 자신의 속성을 그대로 노출해야 한다") {
            val created = Instant.now()
            val event = NotificationEvent(
                id = 10L,
                title = "이슈 제목",
                senderId = 5L,
                receivers = mutableSetOf(user1, user2),
                created = created,
                resourceType = ResourceType.ISSUE_POST,
                resourceId = "10",
                eventType = EventType.NEW_ISSUE
            )

            val asInterface: INotificationEvent = event

            asInterface.senderId shouldBe 5L
            asInterface.title shouldBe "이슈 제목"
            asInterface.eventType shouldBe EventType.NEW_ISSUE
            asInterface.resourceType shouldBe ResourceType.ISSUE_POST
            asInterface.resourceId shouldBe "10"
            asInterface.created shouldBe created
            asInterface.receivers shouldBe setOf(user1, user2)
            describeEvent(asInterface) shouldBe "[NEW_ISSUE] ISSUE_POST/10 title=이슈 제목 receivers=2"
        }

        it("MergedNotificationEvent는 main의 속성을 그대로 위임해야 한다(setReceivers로 재계산 전)") {
            val main = NotificationEvent(
                id = 20L,
                title = "상태변경",
                senderId = 7L,
                receivers = mutableSetOf(user1),
                created = Instant.now(),
                resourceType = ResourceType.ISSUE_STATE,
                resourceId = "20",
                eventType = EventType.ISSUE_STATE_CHANGED
            )
            val merged = MergedNotificationEvent(main)

            val asInterface: INotificationEvent = merged

            asInterface.senderId shouldBe main.senderId
            asInterface.title shouldBe main.title
            asInterface.eventType shouldBe main.eventType
            asInterface.resourceType shouldBe main.resourceType
            asInterface.resourceId shouldBe main.resourceId
            asInterface.created shouldBe main.created
            asInterface.receivers shouldBe setOf(user1)
        }

        it("MergedNotificationEvent.setReceivers()로 재계산된 수신자는 main이 아니라 재계산된 값을 노출해야 한다") {
            val main = NotificationEvent(
                id = 21L,
                senderId = 7L,
                receivers = mutableSetOf(user1),
                resourceType = ResourceType.ISSUE_STATE,
                resourceId = "21",
                eventType = EventType.ISSUE_STATE_CHANGED
            )
            val merged = MergedNotificationEvent(main)
            merged.setReceivers(setOf(user2))

            val asInterface: INotificationEvent = merged

            asInterface.receivers shouldBe setOf(user2)
            main.receivers shouldBe setOf(user1)
        }

        it("같은 함수가 NotificationEvent/MergedNotificationEvent를 구분 없이 처리할 수 있어야 한다") {
            val single = NotificationEvent(
                id = 30L, resourceType = ResourceType.ISSUE_POST, resourceId = "30", eventType = EventType.NEW_ISSUE
            )
            val merged = MergedNotificationEvent(
                NotificationEvent(id = 31L, resourceType = ResourceType.COMMENT_THREAD, resourceId = "31", eventType = EventType.REVIEW_THREAD_STATE_CHANGED)
            )

            val events: List<INotificationEvent> = listOf(single, merged)

            events.map { it.resourceType } shouldBe listOf(ResourceType.ISSUE_POST, ResourceType.COMMENT_THREAD)
        }
    }
})
