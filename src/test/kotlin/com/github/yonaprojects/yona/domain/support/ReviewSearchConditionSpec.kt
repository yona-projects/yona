package com.github.yonaprojects.yona.domain.support

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReviewSearchConditionSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val condition = ReviewSearchCondition()

            condition.state = "CLOSED"
            condition.authorId = 1L
            condition.participantId = 2L
            condition.orderBy = "updatedDate"
            condition.orderDir = "asc"
            condition.filter = "키워드"
            condition.pageNum = 3

            condition.state shouldBe "CLOSED"
            condition.authorId shouldBe 1L
            condition.participantId shouldBe 2L
            condition.orderBy shouldBe "updatedDate"
            condition.orderDir shouldBe "asc"
            condition.filter shouldBe "키워드"
            condition.pageNum shouldBe 3
        }

        it("기본값만으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val condition = ReviewSearchCondition()

            condition.state shouldBe "OPEN"
            condition.authorId shouldBe null
            condition.participantId shouldBe null
            condition.orderBy shouldBe "createdDate"
            condition.orderDir shouldBe "desc"
            condition.filter shouldBe ""
            condition.pageNum shouldBe 1
        }

        it("data class 자동생성 메서드(equals/hashCode/toString/copy/componentN)가 정상 동작해야 한다") {
            val a = ReviewSearchCondition(state = "OPEN", authorId = 1L)
            val b = ReviewSearchCondition(state = "OPEN", authorId = 1L)
            val c = a.copy(state = "CLOSED")

            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
            (a == c) shouldBe false
            c.state shouldBe "CLOSED"
            a.component1() shouldBe "OPEN"
            a.component2() shouldBe 1L
            a.toString() shouldBe a.toString()
        }
    }

    describe("clone()") {
        it("copy()로 동일한 값을 가진 새 인스턴스를 반환해야 한다") {
            val original = ReviewSearchCondition(state = "CLOSED", authorId = 5L, pageNum = 2)

            val cloned = original.clone()

            cloned shouldBe original
            (cloned === original) shouldBe false
        }
    }

    describe("fluent setter") {
        it("setState()는 state를 설정하고 자기 자신을 반환해야 한다") {
            val condition = ReviewSearchCondition()

            val result = condition.setState("CLOSED")

            condition.state shouldBe "CLOSED"
            (result === condition) shouldBe true
        }

        it("setAuthorId()는 authorId를 설정하고(null 포함) 자기 자신을 반환해야 한다") {
            val condition = ReviewSearchCondition()

            val result = condition.setAuthorId(10L)
            condition.authorId shouldBe 10L
            (result === condition) shouldBe true

            condition.setAuthorId(null)
            condition.authorId shouldBe null
        }

        it("setParticipantId()는 participantId를 설정하고(null 포함) 자기 자신을 반환해야 한다") {
            val condition = ReviewSearchCondition()

            val result = condition.setParticipantId(20L)
            condition.participantId shouldBe 20L
            (result === condition) shouldBe true

            condition.setParticipantId(null)
            condition.participantId shouldBe null
        }
    }
})
