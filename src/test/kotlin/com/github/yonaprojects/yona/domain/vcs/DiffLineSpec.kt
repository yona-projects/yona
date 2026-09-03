package com.github.yonaprojects.yona.domain.vcs

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class DiffLineSpec : DescribeSpec({
    val file1 = FileDiff().apply { pathA = "a.txt" }
    val file2 = FileDiff().apply { pathA = "b.txt" }

    describe("equals()") {
        val base = DiffLine(file = file1, kind = DiffLineType.ADD, numA = 1, numB = 2, content = "content")

        it("동일 인스턴스면 true여야 한다") {
            (base == base) shouldBe true
        }

        it("null과 비교하면 false여야 한다") {
            base.equals(null) shouldBe false
        }

        it("다른 클래스와 비교하면 false여야 한다") {
            base.equals("not a diffline") shouldBe false
        }

        it("content가 다르면 false여야 한다") {
            (base == DiffLine(file = file1, kind = DiffLineType.ADD, numA = 1, numB = 2, content = "different")) shouldBe false
        }

        it("file이 다르면 false여야 한다") {
            (base == DiffLine(file = file2, kind = DiffLineType.ADD, numA = 1, numB = 2, content = "content")) shouldBe false
        }

        it("kind가 다르면 false여야 한다") {
            (base == DiffLine(file = file1, kind = DiffLineType.REMOVE, numA = 1, numB = 2, content = "content")) shouldBe false
        }

        it("numA가 다르면 false여야 한다") {
            (base == DiffLine(file = file1, kind = DiffLineType.ADD, numA = 99, numB = 2, content = "content")) shouldBe false
        }

        it("numB가 다르면 false여야 한다") {
            (base == DiffLine(file = file1, kind = DiffLineType.ADD, numA = 1, numB = 99, content = "content")) shouldBe false
        }

        it("모든 필드가 같으면 true여야 한다") {
            (base == DiffLine(file = file1, kind = DiffLineType.ADD, numA = 1, numB = 2, content = "content")) shouldBe true
        }
    }

    describe("프로퍼티 접근자") {
        it("file setter/getter와 나머지 필드 getter가 정상 동작해야 한다") {
            val line = DiffLine(file = null, kind = DiffLineType.CONTEXT, numA = 1, numB = 2, content = "c")
            line.file = file1

            line.file shouldBe file1
            line.kind shouldBe DiffLineType.CONTEXT
            line.numA shouldBe 1
            line.numB shouldBe 2
            line.content shouldBe "c"
        }
    }

    describe("hashCode()") {
        it("numA/numB/file이 전부 null이어도 예외 없이 계산되어야 한다") {
            val line = DiffLine(file = null, kind = DiffLineType.CONTEXT, numA = null, numB = null, content = "c")
            line.hashCode() shouldBe line.hashCode()
        }

        it("numA/numB/file이 전부 값이 있을 때도 예외 없이 계산되어야 한다") {
            val line = DiffLine(file = file1, kind = DiffLineType.ADD, numA = 1, numB = 2, content = "c")
            line.hashCode() shouldBe line.hashCode()
        }

        it("동등한 두 객체는 같은 hashCode를 가져야 한다") {
            val a = DiffLine(file = file1, kind = DiffLineType.ADD, numA = 1, numB = 2, content = "c")
            val b = DiffLine(file = file1, kind = DiffLineType.ADD, numA = 1, numB = 2, content = "c")
            a.hashCode() shouldBe b.hashCode()
        }
    }
})
