package com.github.yonaprojects.yona.domain.support

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PropertySpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val property = Property(name = PropertyName.MAILBOX_LAST_SEEN_UID)

            property.id = 10L
            property.name = PropertyName.MAILBOX_LAST_UID_VALIDITY
            property.value = "123"

            property.id shouldBe 10L
            property.name shouldBe PropertyName.MAILBOX_LAST_UID_VALIDITY
            property.value shouldBe "123"
        }

        it("기본값만으로 생성하면 id/value가 null이어야 한다") {
            val property = Property(name = PropertyName.MAILBOX_LAST_SEEN_UID)

            property.id shouldBe null
            property.name shouldBe PropertyName.MAILBOX_LAST_SEEN_UID
            property.value shouldBe null
        }
    }
})
