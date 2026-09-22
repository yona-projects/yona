package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.enumeration.State
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class StateConverterSpec : DescribeSpec({
    val converter = StateConverter()

    describe("convert") {
        it("State가 스스로 노출하는 소문자 표기를 대응하는 상수로 변환한다") {
            converter.convert("open") shouldBe State.OPEN
            converter.convert("closed") shouldBe State.CLOSED
            converter.convert("merged") shouldBe State.MERGED
        }

        it("Spring 기본 규칙(상수명 그대로인 대문자)도 그대로 받아들인다") {
            converter.convert("OPEN") shouldBe State.OPEN
            converter.convert("CLOSED") shouldBe State.CLOSED
        }

        it("대소문자가 섞여 있어도 처리한다") {
            converter.convert("Open") shouldBe State.OPEN
        }
    }
})
