package com.github.yonaprojects.yona.domain.support

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

// v1.6 HtmlUtil.defaultSanitize(Sanitizers.FORMATTING): 서식 태그(<b> 등)는 남기고 스크립트·이벤트 핸들러·임의 요소는 제거한다.
class HtmlUtilSpec : DescribeSpec({
    describe("HtmlUtil.defaultSanitize") {
        it("script 요소를 제거한다") {
            HtmlUtil.defaultSanitize("<script>alert(1)</script>홍길동") shouldNotContain "<script"
            HtmlUtil.defaultSanitize("<script>alert(1)</script>홍길동") shouldContain "홍길동"
        }
        it("img 요소와 이벤트 핸들러를 제거한다") {
            val out = HtmlUtil.defaultSanitize("<img src=x onerror=alert1>이름")
            out shouldNotContain "<img"
            out shouldNotContain "onerror"
        }
        it("서식 태그는 남긴다") {
            HtmlUtil.defaultSanitize("<b>굵게</b>") shouldBe "<b>굵게</b>"
        }
        it("평문과 부서 표기는 그대로 둔다") {
            HtmlUtil.defaultSanitize("홍길동 [개발팀]") shouldBe "홍길동 [개발팀]"
        }
    }
})
