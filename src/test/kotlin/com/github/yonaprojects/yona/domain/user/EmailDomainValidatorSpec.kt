package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class EmailDomainValidatorSpec : DescribeSpec({
    describe("EmailDomainValidator.isAllowed") {
        it("설정이 비어있으면 모든 도메인을 허용해야 한다") {
            EmailDomainValidator.isAllowed("gildong@anywhere.com", "") shouldBe true
        }

        it("설정된 도메인 중 하나와 일치하면 허용해야 한다") {
            EmailDomainValidator.isAllowed("gildong@example.com", "example.com, other.com") shouldBe true
        }

        it("설정된 도메인과 일치하지 않으면 거부해야 한다") {
            EmailDomainValidator.isAllowed("gildong@notallowed.com", "example.com, other.com") shouldBe false
        }

        it("대소문자를 구분하지 않고 비교해야 한다") {
            EmailDomainValidator.isAllowed("gildong@Example.COM", "example.com") shouldBe true
        }

        it("이메일에 @가 없으면 거부해야 한다") {
            EmailDomainValidator.isAllowed("invalid-email", "example.com") shouldBe false
        }

        it("도메인 목록 항목에 공백이 섞여 있어도 정상 매칭해야 한다") {
            EmailDomainValidator.isAllowed("gildong@example.com", "  example.com  ,other.com") shouldBe true
        }

        it("이메일이 @로 끝나면 거부해야 한다") {
            EmailDomainValidator.isAllowed("gildong@", "example.com") shouldBe false
        }

        it("도메인 설정에 빈 문자열이나 공백만 있는 항목이 포함되어도 무시해야 한다") {
            EmailDomainValidator.isAllowed("gildong@example.com", "example.com, ,  ,other.com") shouldBe true
        }
    }
})
