package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.vcs.DiffLine
import com.github.yonaprojects.yona.domain.vcs.DiffLineType
import com.github.yonaprojects.yona.domain.vcs.FileDiff
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class CodeRangeSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val range = CodeRange()

            range.path = "a.txt"
            range.startSide = CodeRange.Side.A
            range.startLine = 1
            range.startColumn = 2
            range.endSide = CodeRange.Side.B
            range.endLine = 3
            range.endColumn = 4

            range.path shouldBe "a.txt"
            range.startSide shouldBe CodeRange.Side.A
            range.startLine shouldBe 1
            range.startColumn shouldBe 2
            range.endSide shouldBe CodeRange.Side.B
            range.endLine shouldBe 3
            range.endColumn shouldBe 4
        }

        it("Side enum 값이 A/B 두 개여야 한다") {
            CodeRange.Side.values().toList() shouldBe listOf(CodeRange.Side.A, CodeRange.Side.B)
        }
    }

    describe("isFor()") {
        val diff = FileDiff().apply { pathA = "a.txt"; pathB = "b.txt" }

        it("endSide가 B이고 diff.pathB와 path가 다르면 false를 반환해야 한다") {
            val range = CodeRange(path = "other.txt", endSide = CodeRange.Side.B)
            range.isFor(diff) shouldBe false
        }

        it("endSide가 B이고 diff.pathB와 path가 같으면 true를 반환해야 한다") {
            val range = CodeRange(path = "b.txt", endSide = CodeRange.Side.B)
            range.isFor(diff) shouldBe true
        }

        it("endSide가 A이고 diff.pathA와 path가 다르면 false를 반환해야 한다") {
            val range = CodeRange(path = "other.txt", endSide = CodeRange.Side.A)
            range.isFor(diff) shouldBe false
        }

        it("endSide가 A이고 diff.pathA와 path가 같으면 true를 반환해야 한다") {
            val range = CodeRange(path = "a.txt", endSide = CodeRange.Side.A)
            range.isFor(diff) shouldBe true
        }

        it("endSide가 null이면 어느 쪽 경로 검사도 타지 않고 true를 반환해야 한다") {
            val range = CodeRange(path = "anything.txt", endSide = null)
            range.isFor(diff) shouldBe true
        }
    }

    describe("endsWith()") {
        it("endSide가 A이고 endLine이 line.numA와 같으면 true를 반환해야 한다") {
            val range = CodeRange(endSide = CodeRange.Side.A, endLine = 10)
            val line = DiffLine(file = null, kind = DiffLineType.CONTEXT, numA = 10, numB = 20, content = "c")
            range.endsWith(line) shouldBe true
        }

        it("endSide가 A이고 endLine이 line.numA와 다르면 false를 반환해야 한다") {
            val range = CodeRange(endSide = CodeRange.Side.A, endLine = 99)
            val line = DiffLine(file = null, kind = DiffLineType.CONTEXT, numA = 10, numB = 20, content = "c")
            range.endsWith(line) shouldBe false
        }

        it("endSide가 B이고 endLine이 line.numB와 같으면 true를 반환해야 한다") {
            val range = CodeRange(endSide = CodeRange.Side.B, endLine = 20)
            val line = DiffLine(file = null, kind = DiffLineType.CONTEXT, numA = 10, numB = 20, content = "c")
            range.endsWith(line) shouldBe true
        }

        it("endSide가 B이고 endLine이 line.numB와 다르면 false를 반환해야 한다") {
            val range = CodeRange(endSide = CodeRange.Side.B, endLine = 99)
            val line = DiffLine(file = null, kind = DiffLineType.CONTEXT, numA = 10, numB = 20, content = "c")
            range.endsWith(line) shouldBe false
        }

        it("endSide가 null이면 false를 반환해야 한다") {
            val range = CodeRange(endSide = null, endLine = 10)
            val line = DiffLine(file = null, kind = DiffLineType.CONTEXT, numA = 10, numB = 20, content = "c")
            range.endsWith(line) shouldBe false
        }
    }
})
