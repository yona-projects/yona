package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// yona models/User.java:65-66,80 LOGIN_ID_PATTERN 대응 (P1-104).
class LoginIdFormatValidatorSpec : DescribeSpec({
    describe("LoginIdFormatValidator.isValid") {
        it("영문/숫자로만 구성된 아이디를 허용해야 한다") {
            LoginIdFormatValidator.isValid("gildong123") shouldBe true
        }

        it("한글 아이디를 허용해야 한다") {
            LoginIdFormatValidator.isValid("길동") shouldBe true
        }

        it("하이픈을 포함한 아이디를 허용해야 한다") {
            LoginIdFormatValidator.isValid("gil-dong") shouldBe true
        }

        it("밑줄(_)이나 마침표(.)로 구분된 아이디를 허용해야 한다") {
            LoginIdFormatValidator.isValid("gil_dong") shouldBe true
            LoginIdFormatValidator.isValid("gil.dong") shouldBe true
        }

        it("공백이 포함된 아이디는 거부해야 한다") {
            LoginIdFormatValidator.isValid("gil dong") shouldBe false
        }

        it("슬래시가 포함된 아이디는 거부해야 한다") {
            LoginIdFormatValidator.isValid("gil/dong") shouldBe false
        }

        it("빈 문자열은 거부해야 한다") {
            LoginIdFormatValidator.isValid("") shouldBe false
        }

        it("밑줄/마침표로 시작하는 아이디는 거부해야 한다") {
            LoginIdFormatValidator.isValid("_gildong") shouldBe false
            LoginIdFormatValidator.isValid(".gildong") shouldBe false
        }

        it("@ 같은 그 외 특수문자가 포함된 아이디는 거부해야 한다") {
            LoginIdFormatValidator.isValid("gildong@") shouldBe false
        }
    }
})
