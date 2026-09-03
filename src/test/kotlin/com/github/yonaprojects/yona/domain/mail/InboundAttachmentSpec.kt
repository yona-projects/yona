package com.github.yonaprojects.yona.domain.mail

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class InboundAttachmentSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드를 읽을 수 있어야 한다") {
            val bytes = byteArrayOf(1, 2, 3)
            val attachment = InboundAttachment(
                fileName = "a.png",
                contentType = "image/png",
                bytes = bytes,
                contentId = "cid-1"
            )

            attachment.fileName shouldBe "a.png"
            attachment.contentType shouldBe "image/png"
            attachment.bytes shouldBe bytes
            attachment.contentId shouldBe "cid-1"
        }

        it("contentId 없이도 생성 가능해야 한다") {
            val bytes = byteArrayOf(4, 5, 6)
            val attachment = InboundAttachment(
                fileName = "b.png",
                contentType = "image/png",
                bytes = bytes
            )

            attachment.contentId shouldBe null
        }

        it("data class 자동생성 메서드가 정상 동작해야 한다") {
            val bytes = byteArrayOf(7, 8, 9)
            val a = InboundAttachment(fileName = "c.png", contentType = "image/png", bytes = bytes)
            val b = a.copy(fileName = "d.png")

            a.component1() shouldBe "c.png"
            a.component2() shouldBe "image/png"
            a.component3() shouldBe bytes
            a.component4() shouldBe null
            b.fileName shouldBe "d.png"
            a.toString() shouldBe a.toString()
        }
    }
})
