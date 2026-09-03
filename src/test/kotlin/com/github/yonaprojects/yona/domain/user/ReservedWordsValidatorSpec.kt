package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReservedWordsValidatorSpec : DescribeSpec({
    describe("ReservedWordsValidator.isReserved (P2-01)") {
        it("정적 최상위 경로와 겹치는 단어는 예약어로 판단해야 한다") {
            ReservedWordsValidator.isReserved("new") shouldBe true
            ReservedWordsValidator.isReserved("projects") shouldBe true
            ReservedWordsValidator.isReserved("organizations") shouldBe true
            ReservedWordsValidator.isReserved("api") shouldBe true
        }

        it("대소문자를 구분하지 않고 판단해야 한다") {
            ReservedWordsValidator.isReserved("New") shouldBe true
            ReservedWordsValidator.isReserved("API") shouldBe true
        }

        it("예약어가 아닌 일반적인 사용자명은 통과시켜야 한다") {
            ReservedWordsValidator.isReserved("gildong") shouldBe false
            ReservedWordsValidator.isReserved("my-cool-project") shouldBe false
        }

        it("RESERVED_WORDS 프로퍼티 자체를 직접 읽을 수 있어야 한다") {
            ReservedWordsValidator.RESERVED_WORDS shouldBe ReservedWordsValidator.RESERVED_WORDS
            ReservedWordsValidator.RESERVED_WORDS.contains("api") shouldBe true
        }
    }
})
