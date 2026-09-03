package com.github.yonaprojects.yona.domain.mail

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class InboundEmailMessageSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드를 읽을 수 있어야 한다") {
            val attachment = InboundAttachment(fileName = "a.png", contentType = "image/png", bytes = byteArrayOf(1))
            val message = InboundEmailMessage(
                messageId = "<msg@example.com>",
                subject = "제목",
                fromAddress = "a@example.com",
                fromName = "보낸이",
                recipientAddresses = listOf("b@example.com"),
                inReplyTo = "<parent@example.com>",
                references = "<ref@example.com>",
                textBody = "본문",
                isHtml = true,
                attachments = listOf(attachment)
            )

            message.messageId shouldBe "<msg@example.com>"
            message.subject shouldBe "제목"
            message.fromAddress shouldBe "a@example.com"
            message.fromName shouldBe "보낸이"
            message.recipientAddresses shouldBe listOf("b@example.com")
            message.inReplyTo shouldBe "<parent@example.com>"
            message.references shouldBe "<ref@example.com>"
            message.textBody shouldBe "본문"
            message.isHtml shouldBe true
            message.attachments shouldBe listOf(attachment)
        }

        it("isHtml/attachments 기본값이 각각 false/빈 리스트여야 한다") {
            val message = InboundEmailMessage(
                messageId = "<msg@example.com>",
                subject = "제목",
                fromAddress = "a@example.com",
                fromName = "보낸이",
                recipientAddresses = emptyList(),
                inReplyTo = null,
                references = null,
                textBody = "본문"
            )

            message.isHtml shouldBe false
            message.attachments shouldBe emptyList()
        }

        it("data class 자동생성 메서드가 정상 동작해야 한다") {
            val a = InboundEmailMessage(
                messageId = "<msg@example.com>", subject = "제목", fromAddress = "a@example.com",
                fromName = "보낸이", recipientAddresses = emptyList(), inReplyTo = null,
                references = null, textBody = "본문"
            )
            val b = a.copy(subject = "다른 제목")

            (a == b) shouldBe false
            b.subject shouldBe "다른 제목"
            a.component1() shouldBe "<msg@example.com>"
            a.toString() shouldBe a.toString()
        }
    }
})
