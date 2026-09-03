package com.github.yonaprojects.yona.domain.enumeration

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class EventTypeSpec : DescribeSpec({
    describe("EventType") {
        it("messageKey/order가 정확해야 한다") {
            EventType.NEW_ISSUE.messageKey shouldBe "notification.type.new.issue"
            EventType.NEW_ISSUE.order shouldBe 1
        }

        it("isCreating()이 생성 계열 이벤트에서 true, 그 외는 false여야 한다") {
            EventType.NEW_ISSUE.isCreating() shouldBe true
            EventType.NEW_POSTING.isCreating() shouldBe true
            EventType.NEW_PULL_REQUEST.isCreating() shouldBe true
            EventType.NEW_COMMENT.isCreating() shouldBe true
            EventType.NEW_REVIEW_COMMENT.isCreating() shouldBe true
            EventType.ISSUE_STATE_CHANGED.isCreating() shouldBe false
        }

        it("valueOf()/values()가 정상 동작해야 한다") {
            EventType.valueOf("NEW_ISSUE") shouldBe EventType.NEW_ISSUE
            EventType.values().size shouldBe 27
        }
    }
})
