package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.support.CodeRange
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull

class CodeRangeRequestSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("전체 필드를 채운 생성자와 각 프로퍼티 접근이 정상 동작해야 한다") {
            val request = CodeRangeRequest(
                path = "src/main/kotlin/App.kt",
                startSide = "a",
                startLine = 1,
                startColumn = 2,
                endSide = "b",
                endLine = 3,
                endColumn = 4
            )

            request.path shouldBe "src/main/kotlin/App.kt"
            request.startSide shouldBe "a"
            request.startLine shouldBe 1
            request.startColumn shouldBe 2
            request.endSide shouldBe "b"
            request.endLine shouldBe 3
            request.endColumn shouldBe 4
        }

        it("기본값 생성자는 전체 필드가 null이어야 한다") {
            val request = CodeRangeRequest()

            request.path.shouldBeNull()
            request.startSide.shouldBeNull()
            request.startLine.shouldBeNull()
            request.startColumn.shouldBeNull()
            request.endSide.shouldBeNull()
            request.endLine.shouldBeNull()
            request.endColumn.shouldBeNull()
        }
    }

    describe("toCodeRange()") {
        it("startLine이 null이면 null을 반환해야 한다") {
            val request = CodeRangeRequest(path = "App.kt", startLine = null)

            request.toCodeRange().shouldBeNull()
        }

        it("startLine이 있고 startSide/endSide가 모두 있으면 CodeRange로 변환해야 한다") {
            val request = CodeRangeRequest(
                path = "App.kt",
                startSide = "a",
                startLine = 1,
                startColumn = 2,
                endSide = "b",
                endLine = 3,
                endColumn = 4
            )

            val result = request.toCodeRange()

            result?.path shouldBe "App.kt"
            result?.startSide shouldBe CodeRange.Side.A
            result?.startLine shouldBe 1
            result?.startColumn shouldBe 2
            result?.endSide shouldBe CodeRange.Side.B
            result?.endLine shouldBe 3
            result?.endColumn shouldBe 4
        }

        it("startLine이 있어도 startSide/endSide가 null이면 CodeRange의 side도 null이어야 한다") {
            val request = CodeRangeRequest(path = "App.kt", startLine = 1, endLine = 3)

            val result = request.toCodeRange()

            result?.path shouldBe "App.kt"
            result?.startSide.shouldBeNull()
            result?.startLine shouldBe 1
            result?.endSide.shouldBeNull()
            result?.endLine shouldBe 3
        }

        it("side 문자열이 소문자여도 대문자로 변환해 파싱해야 한다") {
            val request = CodeRangeRequest(startSide = "b", startLine = 5, endSide = "a", endLine = 6)

            val result = request.toCodeRange()

            result?.startSide shouldBe CodeRange.Side.B
            result?.endSide shouldBe CodeRange.Side.A
        }
    }
})
