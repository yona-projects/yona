package com.github.yonaprojects.yona.domain.enumeration

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class OperationSpec : DescribeSpec({
    describe("operation()") {
        it("각 enum 값에 대응하는 문자열 값을 반환해야 한다") {
            Operation.READ.operation() shouldBe "read"
            Operation.UPDATE.operation() shouldBe "edit"
            Operation.DELETE.operation() shouldBe "delete"
            Operation.ACCEPT.operation() shouldBe "accept"
            Operation.REOPEN.operation() shouldBe "reopen"
            Operation.CLOSE.operation() shouldBe "close"
            Operation.WATCH.operation() shouldBe "watch"
            Operation.LEAVE.operation() shouldBe "leave"
            Operation.ASSIGN_ISSUE.operation() shouldBe "assign_issue"
        }
    }

    describe("getValue()") {
        it("일치하는 값 문자열이 있으면 해당 enum을 반환해야 한다") {
            Operation.getValue("delete") shouldBe Operation.DELETE
        }

        it("일치하는 값이 없으면 기본값 READ를 반환해야 한다") {
            Operation.getValue("no-such-value") shouldBe Operation.READ
        }
    }
})
