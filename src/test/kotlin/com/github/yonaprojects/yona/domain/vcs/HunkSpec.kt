package com.github.yonaprojects.yona.domain.vcs

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class HunkSpec : DescribeSpec({
    fun line(content: String) = DiffLine(file = null, kind = DiffLineType.CONTEXT, numA = 1, numB = 1, content = content)

    describe("size()") {
        it("lines가 비어있으면 0을 반환해야 한다") {
            Hunk().size() shouldBe 0
        }

        it("각 라인 content 길이의 합을 반환해야 한다") {
            val hunk = Hunk(lines = mutableListOf(line("abc"), line("de")))
            hunk.size() shouldBe 5
        }
    }

    describe("equals()") {
        val base = Hunk(beginA = 1, endA = 2, beginB = 3, endB = 4, lines = mutableListOf(line("x")))

        it("동일 인스턴스면 true여야 한다") {
            (base == base) shouldBe true
        }

        it("null과 비교하면 false여야 한다") {
            (base.equals(null)) shouldBe false
        }

        it("다른 클래스와 비교하면 false여야 한다") {
            (base.equals("not a hunk")) shouldBe false
        }

        it("beginA가 다르면 false여야 한다") {
            val other = Hunk(beginA = 99, endA = 2, beginB = 3, endB = 4, lines = mutableListOf(line("x")))
            (base == other) shouldBe false
        }

        it("beginB가 다르면 false여야 한다") {
            val other = Hunk(beginA = 1, endA = 2, beginB = 99, endB = 4, lines = mutableListOf(line("x")))
            (base == other) shouldBe false
        }

        it("endA가 다르면 false여야 한다") {
            val other = Hunk(beginA = 1, endA = 99, beginB = 3, endB = 4, lines = mutableListOf(line("x")))
            (base == other) shouldBe false
        }

        it("endB가 다르면 false여야 한다") {
            val other = Hunk(beginA = 1, endA = 2, beginB = 3, endB = 99, lines = mutableListOf(line("x")))
            (base == other) shouldBe false
        }

        it("lines가 다르면 false여야 한다") {
            val other = Hunk(beginA = 1, endA = 2, beginB = 3, endB = 4, lines = mutableListOf(line("different")))
            (base == other) shouldBe false
        }

        it("모든 필드가 같으면 true여야 한다") {
            val other = Hunk(beginA = 1, endA = 2, beginB = 3, endB = 4, lines = mutableListOf(line("x")))
            (base == other) shouldBe true
        }
    }

    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val hunk = Hunk()
            hunk.beginA = 1
            hunk.endA = 2
            hunk.beginB = 3
            hunk.endB = 4
            hunk.lines = mutableListOf(line("x"))

            hunk.beginA shouldBe 1
            hunk.endA shouldBe 2
            hunk.beginB shouldBe 3
            hunk.endB shouldBe 4
            hunk.lines shouldBe mutableListOf(line("x"))
        }
    }

    describe("hashCode()") {
        it("동등한 두 객체는 같은 hashCode를 가져야 한다") {
            val a = Hunk(beginA = 1, endA = 2, beginB = 3, endB = 4, lines = mutableListOf(line("x")))
            val b = Hunk(beginA = 1, endA = 2, beginB = 3, endB = 4, lines = mutableListOf(line("x")))
            a.hashCode() shouldBe b.hashCode()
        }
    }
})
