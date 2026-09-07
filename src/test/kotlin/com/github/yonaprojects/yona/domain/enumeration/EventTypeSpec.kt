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
            // P3-15(PR 승인/변경요청 워크플로)가 PULL_REQUEST_REVIEWED를 추가하며 27에서 늘어남
            // — 테스트가 갱신되지 않아 실패하던 것(환경 문제 아님). order 필드값은 15가 빠져있는
            // 등 연속적이지 않으므로(최댓값 29 ≠ 개수) 실제 enum 상수 개수(28개)로 검증한다.
            EventType.values().size shouldBe 28
        }
    }
})
