package com.github.yonaprojects.yona.domain.notification

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class NotificationMailSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val event = NotificationEvent()
            val mail = NotificationMail(notificationEvent = event)

            val newEvent = NotificationEvent()

            mail.id = 10L
            mail.notificationEvent = newEvent

            mail.id shouldBe 10L
            mail.notificationEvent shouldBe newEvent
        }

        it("기본값만으로 생성하면 id가 null이어야 한다") {
            val event = NotificationEvent()
            val mail = NotificationMail(notificationEvent = event)

            mail.id shouldBe null
            mail.notificationEvent shouldBe event
        }
    }
})
