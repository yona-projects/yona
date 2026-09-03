package com.github.yonaprojects.yona.domain.support

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// yona test/utils/DiffUtilTest.java 대응 (P2-02). 원본 테스트 케이스를 그대로 포팅.
class DiffUtilSpec : DescribeSpec({
    val diffDeletePrefix = "<span style='background-color: #fda9a6;padding: 2px 0;'>"
    val diffDeletePostfix = "</span>"

    val diffInsertPrefix = "<span style='background-color: #abdd52;padding: 2px 0;'>"
    val diffInsertPostfix = "</span>"

    val diffEqualPrefix = "<span style='color: #bdbdbd;font-size: 16px;font-family: serif;'>...&nbsp<br/>\n" +
        "......&nbsp<br/>\n" +
        "......&nbsp<br/>\n" +
        "...</span>"

    val diffNewLine = "\n"

    val diffDeletePlainPrefix = "--- "
    val diffInsertPlainPrefix = "+++ "

    val diffEqualPlainPrefix = "......\n" +
        "......\n" +
        "...\n"

    describe("DiffUtil.getDiffText") {
        it("oldValue가 null이면 전체를 INSERT로 표시해야 한다") {
            val newValue = "new value"
            val expected = diffInsertPrefix + newValue + diffInsertPostfix

            DiffUtil.getDiffText(null, newValue) shouldBe expected
        }

        it("newValue가 null이면 전체를 DELETE로 표시해야 한다") {
            val oldValue = "oldValue"
            val expected = diffDeletePrefix + oldValue + diffDeletePostfix

            DiffUtil.getDiffText(oldValue, null) shouldBe expected
        }

        it("동일한 값이 100자를 초과하면 앞뒤 50자만 남기고 생략 표시해야 한다") {
            val oldValue = "12345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                "123456789012345678901234567890"
            val newValue = oldValue
            val textLength = oldValue.length
            val expected = oldValue.substring(0, 50) + diffEqualPrefix + oldValue.substring(textLength - 50)

            DiffUtil.getDiffText(oldValue, newValue) shouldBe expected
        }

        it("동일한 값이 100자 이하면 그대로 반환해야 한다") {
            val oldValue = "12345678901234567890"
            val newValue = "12345678901234567890"

            DiffUtil.getDiffText(oldValue, newValue) shouldBe oldValue
        }

        it("삭제와 삽입이 섞이면 각각 스타일을 입혀 표시해야 한다") {
            val oldValue = "Hi, there?"
            val newValue = "Hello, mijeong?"
            val expected = oldValue.substring(0, 1) +
                diffDeletePrefix + oldValue.substring(1, oldValue.length - 1) + diffDeletePostfix +
                diffInsertPrefix + newValue.substring(1, newValue.length - 1) + diffDeletePostfix +
                oldValue.substring(oldValue.length - 1)

            DiffUtil.getDiffText(oldValue, newValue) shouldBe expected
        }
    }

    describe("DiffUtil.getDiffPlainText") {
        it("oldValue가 null이면 전체를 +++ 접두사로 표시해야 한다") {
            val newValue = "new value"
            val expected = diffInsertPlainPrefix + newValue + diffNewLine

            DiffUtil.getDiffPlainText(null, newValue) shouldBe expected
        }

        it("newValue가 null이면 전체를 --- 접두사로 표시해야 한다") {
            val oldValue = "oldValue"
            val expected = diffDeletePlainPrefix + oldValue + diffNewLine

            DiffUtil.getDiffPlainText(oldValue, null) shouldBe expected
        }

        it("동일한 값이 100자를 초과하면 앞뒤 50자만 남기고 생략 표시해야 한다") {
            val oldValue = "12345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                "123456789012345678901234567890"
            val newValue = oldValue
            val textLength = oldValue.length
            val expected = oldValue.substring(0, 50) + diffEqualPlainPrefix +
                oldValue.substring(textLength - 50) + diffNewLine

            DiffUtil.getDiffPlainText(oldValue, newValue) shouldBe expected
        }

        it("동일한 값이 100자 이하면 그대로 반환해야 한다") {
            val oldValue = "12345678901234567890"
            val newValue = "12345678901234567890"
            val expected = oldValue + diffNewLine

            DiffUtil.getDiffPlainText(oldValue, newValue) shouldBe expected
        }

        it("삭제와 삽입이 섞이면 각 줄에 접두사를 붙여 표시해야 한다") {
            val oldValue = "Hi, there?"
            val newValue = "Hello, mijeong?"
            val expected = oldValue.substring(0, 1) + diffNewLine +
                diffDeletePlainPrefix + oldValue.substring(1, oldValue.length - 1) + diffNewLine +
                diffInsertPlainPrefix + newValue.substring(1, newValue.length - 1) + diffNewLine +
                oldValue.substring(oldValue.length - 1) + diffNewLine

            DiffUtil.getDiffPlainText(oldValue, newValue) shouldBe expected
        }
    }
})
