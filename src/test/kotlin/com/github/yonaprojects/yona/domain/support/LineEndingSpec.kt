package com.github.yonaprojects.yona.domain.support

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class LineEndingSpec : DescribeSpec({
    describe("findLineEnding()") {
        it("null이면 UNDEFINED를 반환해야 한다") {
            LineEnding.findLineEnding(null) shouldBe LineEnding.EndingType.UNDEFINED
        }

        it("빈 문자열이면 UNDEFINED를 반환해야 한다") {
            LineEnding.findLineEnding("") shouldBe LineEnding.EndingType.UNDEFINED
        }

        it("\\r\\n이 포함되어 있으면 DOS를 반환해야 한다") {
            LineEnding.findLineEnding("line1\r\nline2") shouldBe LineEnding.EndingType.DOS
        }

        it("\\r\\n이 없으면(순수 \\n만) UNIX를 반환해야 한다") {
            LineEnding.findLineEnding("line1\nline2") shouldBe LineEnding.EndingType.UNIX
        }
    }

    describe("changeLineEnding(contents, to: String?)") {
        it("to가 null이면 UNIX 개행 처리로 위임해야 한다") {
            val result = LineEnding.changeLineEnding("line1\r\nline2", null)
            result shouldBe "line1\nline2"
        }

        it("to가 빈 문자열이면 UNIX 개행 처리로 위임해야 한다") {
            val result = LineEnding.changeLineEnding("line1\r\nline2", "")
            result shouldBe "line1\nline2"
        }

        it("to가 DOS도 아니고 빈 값도 아니면 UNIX 개행 처리로 위임해야 한다") {
            val result = LineEnding.changeLineEnding("line1\r\nline2", "UNIX")
            result shouldBe "line1\nline2"
        }

        it("to가 정확히 \"DOS\"이면 DOS 개행 처리로 위임해야 한다") {
            // yona 원본의 no-op 버그(contents.replace("\n","\n"))를 그대로 보존하므로
            // 실제로는 변환되지 않고 원본 그대로 반환된다(LineEnding.kt 상단 주석 참고).
            val result = LineEnding.changeLineEnding("line1\nline2", "DOS")
            result shouldBe "line1\nline2"
        }

        it("to가 대소문자 다른 \"dos\"여도(ignoreCase) DOS 개행 처리로 위임해야 한다") {
            val result = LineEnding.changeLineEnding("line1\nline2", "dos")
            result shouldBe "line1\nline2"
        }
    }

    describe("changeLineEnding(contents, to: EndingType)") {
        it("contents가 빈 문자열이면 빈 문자열을 반환해야 한다") {
            LineEnding.changeLineEnding("", LineEnding.EndingType.DOS) shouldBe ""
        }

        it("UNIX 개행인 내용을 DOS로 바꾸도록 요청하면 no-op 치환이 실행되어 원본 그대로 반환된다(legacy 버그 보존)") {
            val result = LineEnding.changeLineEnding("line1\nline2", LineEnding.EndingType.DOS)
            result shouldBe "line1\nline2"
        }

        it("DOS 개행인 내용을 UNIX로 바꾸면 \\r\\n이 \\n으로 치환되어야 한다") {
            val result = LineEnding.changeLineEnding("line1\r\nline2", LineEnding.EndingType.UNIX)
            result shouldBe "line1\nline2"
        }

        it("이미 DOS인 내용을 DOS로 요청하면 원본 그대로 반환해야 한다(변경 불필요 분기)") {
            val result = LineEnding.changeLineEnding("line1\r\nline2", LineEnding.EndingType.DOS)
            result shouldBe "line1\r\nline2"
        }

        it("이미 UNIX인 내용을 UNIX로 요청하면 원본 그대로 반환해야 한다(변경 불필요 분기)") {
            val result = LineEnding.changeLineEnding("line1\nline2", LineEnding.EndingType.UNIX)
            result shouldBe "line1\nline2"
        }

        it("UNDEFINED로 요청하면 두 변환 조건 모두 해당하지 않아 원본 그대로 반환해야 한다") {
            val result = LineEnding.changeLineEnding("line1\nline2", LineEnding.EndingType.UNDEFINED)
            result shouldBe "line1\nline2"
        }
    }

    describe("addEOL()") {
        it("null이면 null을 그대로 반환해야 한다") {
            LineEnding.addEOL(null) shouldBe null
        }

        it("빈 문자열이면(UNDEFINED) 기본 개행(UNIX, \\n)을 추가해야 한다") {
            LineEnding.addEOL("") shouldBe "\n"
        }

        it("이미 UNIX 개행으로 끝나면 그대로 반환해야 한다") {
            LineEnding.addEOL("line1\n") shouldBe "line1\n"
        }

        it("UNIX 개행이 있지만 마지막에 없으면 \\n을 추가해야 한다") {
            LineEnding.addEOL("line1\nline2") shouldBe "line1\nline2\n"
        }

        it("이미 DOS 개행으로 끝나면 그대로 반환해야 한다") {
            LineEnding.addEOL("line1\r\n") shouldBe "line1\r\n"
        }

        it("DOS 개행이 있지만 마지막에 없으면 \\r\\n을 추가해야 한다") {
            LineEnding.addEOL("line1\r\nline2") shouldBe "line1\r\nline2\r\n"
        }
    }

    describe("EndingType enum") {
        it("각 타입의 value가 올바른 개행 문자열이어야 한다") {
            LineEnding.EndingType.DOS.value shouldBe "\r\n"
            LineEnding.EndingType.UNIX.value shouldBe "\n"
            LineEnding.EndingType.UNDEFINED.value shouldBe ""
        }

        it("DEFAULT_ENDING_TYPE은 UNIX여야 한다") {
            LineEnding.DEFAULT_ENDING_TYPE shouldBe LineEnding.EndingType.UNIX
        }
    }
})
