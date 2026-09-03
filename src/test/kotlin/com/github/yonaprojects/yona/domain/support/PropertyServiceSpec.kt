package com.github.yonaprojects.yona.domain.support

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

// yona models/Property.java의 get()/getLong()/set() 대응 (P1-55).
class PropertyServiceSpec : DescribeSpec({
    val propertyRepository = mockk<PropertyRepository>()
    val service = PropertyService(propertyRepository)

    describe("PropertyService.get/getLong") {
        it("저장된 값이 없으면 null을 반환해야 한다") {
            every { propertyRepository.findByName(PropertyName.MAILBOX_LAST_SEEN_UID) } returns null

            service.get(PropertyName.MAILBOX_LAST_SEEN_UID) shouldBe null
            service.getLong(PropertyName.MAILBOX_LAST_SEEN_UID) shouldBe null
        }

        it("저장된 값이 있으면 문자열/Long으로 반환해야 한다") {
            every { propertyRepository.findByName(PropertyName.MAILBOX_LAST_SEEN_UID) } returns
                Property(id = 1L, name = PropertyName.MAILBOX_LAST_SEEN_UID, value = "42")

            service.get(PropertyName.MAILBOX_LAST_SEEN_UID) shouldBe "42"
            service.getLong(PropertyName.MAILBOX_LAST_SEEN_UID) shouldBe 42L
        }
    }

    describe("PropertyService.set") {
        it("기존 값이 없으면 새로 생성해야 한다") {
            every { propertyRepository.findByName(PropertyName.MAILBOX_LAST_UID_VALIDITY) } returns null
            val captured = slot<Property>()
            every { propertyRepository.save(capture(captured)) } answers { firstArg() }

            service.set(PropertyName.MAILBOX_LAST_UID_VALIDITY, 100L)

            captured.captured.name shouldBe PropertyName.MAILBOX_LAST_UID_VALIDITY
            captured.captured.value shouldBe "100"
        }

        it("기존 값이 있으면 같은 행을 갱신해야 한다") {
            val existing = Property(id = 5L, name = PropertyName.MAILBOX_LAST_SEEN_UID, value = "10")
            every { propertyRepository.findByName(PropertyName.MAILBOX_LAST_SEEN_UID) } returns existing
            every { propertyRepository.save(any()) } answers { firstArg() }

            service.set(PropertyName.MAILBOX_LAST_SEEN_UID, "20")

            verify(exactly = 1) { propertyRepository.save(existing) }
            existing.value shouldBe "20"
        }
    }
})
