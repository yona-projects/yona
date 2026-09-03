package com.github.yonaprojects.yona.domain.mail

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class OriginalEmailSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val originalEmail = OriginalEmail()
            val handledDate = Instant.parse("2026-08-25T00:00:00Z")

            originalEmail.id = 1L
            originalEmail.messageId = "<abc@example.com>"
            originalEmail.resourceType = ResourceType.ISSUE_POST
            originalEmail.resourceId = "10"
            originalEmail.handledDate = handledDate

            originalEmail.id shouldBe 1L
            originalEmail.messageId shouldBe "<abc@example.com>"
            originalEmail.resourceType shouldBe ResourceType.ISSUE_POST
            originalEmail.resourceId shouldBe "10"
            originalEmail.handledDate shouldBe handledDate
        }

        it("handledDate에 null도 설정할 수 있어야 한다") {
            val originalEmail = OriginalEmail()

            originalEmail.handledDate = null

            originalEmail.handledDate shouldBe null
        }

        it("기본값만으로 생성하면 각 필드가 기본값을 가져야 한다(handledDate는 생성 시각으로 자동 채워짐)") {
            val originalEmail = OriginalEmail()

            originalEmail.id shouldBe null
            originalEmail.messageId shouldBe ""
            originalEmail.resourceType shouldBe ResourceType.NOT_A_RESOURCE
            originalEmail.resourceId shouldBe ""
            (originalEmail.handledDate != null) shouldBe true
        }
    }
})
