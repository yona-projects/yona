package com.github.yonaprojects.yona.domain.attachment

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class AttachmentSpec : DescribeSpec({
    describe("Attachment") {
        it("객체를 생성하고 속성을 설정할 수 있어야 한다") {
            val now = Instant.now()
            val attachment = Attachment(
                id = 1L,
                name = "test.txt",
                hash = "abcdef",
                containerType = ResourceType.ISSUE_POST,
                containerId = "123",
                mimeType = "text/plain",
                size = 1024L,
                createdDate = now,
                ownerLoginId = "user1"
            )

            attachment.id shouldBe 1L
            attachment.name shouldBe "test.txt"
            attachment.hash shouldBe "abcdef"
            attachment.containerType shouldBe ResourceType.ISSUE_POST
            attachment.containerId shouldBe "123"
            attachment.mimeType shouldBe "text/plain"
            attachment.size shouldBe 1024L
            attachment.createdDate shouldBe now
            attachment.ownerLoginId shouldBe "user1"
        }

        it("모든 프로퍼티를 setter로 재할당할 수 있어야 한다") {
            val attachment = Attachment()
            val now = Instant.now()

            attachment.id = 2L
            attachment.name = "changed.txt"
            attachment.hash = "fedcba"
            attachment.containerType = ResourceType.ISSUE_COMMENT
            attachment.containerId = "456"
            attachment.mimeType = "image/png"
            attachment.size = 2048L
            attachment.createdDate = now
            attachment.ownerLoginId = "user2"

            attachment.id shouldBe 2L
            attachment.name shouldBe "changed.txt"
            attachment.hash shouldBe "fedcba"
            attachment.containerType shouldBe ResourceType.ISSUE_COMMENT
            attachment.containerId shouldBe "456"
            attachment.mimeType shouldBe "image/png"
            attachment.size shouldBe 2048L
            attachment.createdDate shouldBe now
            attachment.ownerLoginId shouldBe "user2"
        }
    }
})
