package com.github.yonaprojects.yona.domain.mail

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class MessageIdParserSpec : DescribeSpec({
    describe("MessageIdParser.parse") {
        it("단일 message-id 헤더 값을 파싱해야 한다") {
            val ids = MessageIdParser.parse("<abc123@example.com>")

            ids shouldBe listOf("<abc123@example.com>")
        }

        it("References 헤더처럼 공백으로 구분된 여러 message-id를 모두 파싱해야 한다") {
            val ids = MessageIdParser.parse("<first@example.com> <second@example.com> <third@example.com>")

            ids shouldBe listOf("<first@example.com>", "<second@example.com>", "<third@example.com>")
        }

        it("빈 문자열이면 빈 목록을 반환해야 한다") {
            MessageIdParser.parse("") shouldBe emptyList()
        }

        it("괄호로 된 코멘트가 섞여 있어도 message-id만 추출해야 한다") {
            val ids = MessageIdParser.parse("(comment) <real-id@example.com> (trailing comment)")

            ids shouldBe listOf("<real-id@example.com>")
        }
    }
})
