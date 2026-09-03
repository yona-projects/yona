package com.github.yonaprojects.yona.domain.mail

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class EmailAddressDetailSpec : DescribeSpec({
    describe("EmailAddressDetail.of") {
        it("plus 기호가 있으면 detail 파트를 분리해야 한다 (yona+owner/project@example.com)") {
            val addr = EmailAddressDetail.of("yona+owner/project@example.com")

            addr.user shouldBe "yona"
            addr.detail shouldBe "owner/project"
            addr.domain shouldBe "example.com"
        }

        it("plus 기호가 없으면 detail은 빈 문자열이어야 한다") {
            val addr = EmailAddressDetail.of("yona@example.com")

            addr.user shouldBe "yona"
            addr.detail shouldBe ""
            addr.domain shouldBe "example.com"
        }

        it("detail에 여러 슬래시가 있어도 온전히 보존해야 한다") {
            val addr = EmailAddressDetail.of("yona+owner/project/issue/5@example.com")

            addr.detail shouldBe "owner/project/issue/5"
        }
    }

    describe("EmailAddressDetail.isToYona") {
        it("user와 domain이 기준 주소와 같으면(detail 무관) true여야 한다") {
            val base = "yona@example.com"
            val addr = EmailAddressDetail.of("yona+owner/project@example.com")

            addr.isToYona(base) shouldBe true
        }

        it("user가 다르면 false여야 한다") {
            val base = "yona@example.com"
            val addr = EmailAddressDetail.of("other+owner/project@example.com")

            addr.isToYona(base) shouldBe false
        }

        it("domain이 다르면 false여야 한다") {
            val base = "yona@example.com"
            val addr = EmailAddressDetail.of("yona+owner/project@other.com")

            addr.isToYona(base) shouldBe false
        }
    }

    describe("EmailAddressDetail.toString") {
        it("user+detail@domain 형식으로 재구성해야 한다") {
            val addr = EmailAddressDetail.of("yona+owner/project@example.com")

            addr.toString() shouldBe "yona+owner/project@example.com"
        }

        it("detail이 없으면 plus 없이 user@domain 형식이어야 한다") {
            val addr = EmailAddressDetail.of("yona@example.com")

            addr.toString() shouldBe "yona@example.com"
        }
    }
})
