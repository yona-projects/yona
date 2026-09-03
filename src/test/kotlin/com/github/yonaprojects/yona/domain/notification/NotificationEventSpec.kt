package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class NotificationEventSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val event = NotificationEvent()
            val user = User()
            val now = Instant.now()
            val mail = NotificationMail(notificationEvent = event)

            event.id = 1L
            event.title = "제목"
            event.senderId = 2L
            event.receivers = mutableSetOf(user)
            event.created = now
            event.resourceType = ResourceType.ISSUE_POST
            event.resourceId = "3"
            event.eventType = EventType.NEW_COMMENT
            event.oldValue = "old"
            event.newValue = "new"
            event.notificationMail = mail

            event.id shouldBe 1L
            event.title shouldBe "제목"
            event.senderId shouldBe 2L
            event.receivers shouldBe mutableSetOf(user)
            event.created shouldBe now
            event.resourceType shouldBe ResourceType.ISSUE_POST
            event.resourceId shouldBe "3"
            event.eventType shouldBe EventType.NEW_COMMENT
            event.oldValue shouldBe "old"
            event.newValue shouldBe "new"
            event.notificationMail shouldBe mail
        }

        it("기본값으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val event = NotificationEvent()

            event.id shouldBe null
            event.title shouldBe ""
            event.senderId shouldBe null
            event.receivers shouldBe mutableSetOf()
            event.created shouldBe null
            event.resourceType shouldBe ResourceType.NOT_A_RESOURCE
            event.resourceId shouldBe ""
            event.eventType shouldBe EventType.NEW_ISSUE
            event.oldValue shouldBe null
            event.newValue shouldBe null
            event.notificationMail shouldBe null
        }
    }
})
