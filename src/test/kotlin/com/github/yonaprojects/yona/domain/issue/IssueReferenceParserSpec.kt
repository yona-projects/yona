package com.github.yonaprojects.yona.domain.issue

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class IssueReferenceParserSpec : DescribeSpec({
    describe("IssueReferenceParser.findReferredIssueNumbers") {
        it("커밋 메시지에서 #숫자 형태의 이슈 참조를 추출해야 한다") {
            IssueReferenceParser.findReferredIssueNumbers("fix #123 bug") shouldBe setOf(123L)
        }

        it("여러 개의 이슈 참조를 모두 추출해야 한다") {
            IssueReferenceParser.findReferredIssueNumbers("relates to #1 and #22, also #333") shouldBe setOf(1L, 22L, 333L)
        }

        it("같은 이슈가 여러 번 언급돼도 한 번만 반환해야 한다") {
            IssueReferenceParser.findReferredIssueNumbers("#5 ... see also #5 again") shouldBe setOf(5L)
        }

        it("이슈 참조가 없으면 빈 집합을 반환해야 한다") {
            IssueReferenceParser.findReferredIssueNumbers("no issue reference here") shouldBe emptySet()
        }

        it("빈 문자열이면 빈 집합을 반환해야 한다") {
            IssueReferenceParser.findReferredIssueNumbers("") shouldBe emptySet()
        }
    }
})
