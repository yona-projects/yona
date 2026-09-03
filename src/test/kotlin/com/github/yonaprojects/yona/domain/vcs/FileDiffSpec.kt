package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.support.CodeRange
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.EditList
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.FileMode

// FileDiff는 GitRepository.getDiff() 등에서 두 커밋(또는 인덱스) 사이의 파일 하나에 대한
// 통합 diff 결과(원본/변경본 텍스트, EditList, 파일 경로, 파일 모드 등)를 담아
// PullRequestServiceImpl / NotificationMessageResolver 등에서 hunk 단위 화면 렌더링,
// 파일 모드 변경 여부, 크기 초과 에러 표시에 사용하는 도메인 객체다.
private fun rawTextOf(lines: List<String>): RawText =
    RawText((lines.joinToString("\n") + "\n").toByteArray())

private fun editListOf(vararg edits: Edit): EditList {
    val editList = EditList()
    edits.forEach { editList.add(it) }
    return editList
}

private fun newFileDiff(
    aLines: List<String>,
    bLines: List<String>,
    edits: EditList,
    context: Int = 3
): FileDiff {
    val fileDiff = FileDiff()
    fileDiff.a = rawTextOf(aLines)
    fileDiff.b = rawTextOf(bLines)
    fileDiff.context = context
    fileDiff.editList = edits
    return fileDiff
}

class FileDiffSpec : DescribeSpec({

    describe("생성자와 프로퍼티(getter/setter)") {
        it("모든 프로퍼티를 설정하고 다시 읽을 수 있다") {
            val fileDiff = FileDiff()

            val a = rawTextOf(listOf("a0", "a1"))
            val b = rawTextOf(listOf("b0", "b1"))
            val edits = editListOf(Edit(0, 1, 0, 1))

            fileDiff.a = a
            fileDiff.b = b
            fileDiff.editList = edits
            fileDiff.commitA = "commitA-sha"
            fileDiff.commitB = "commitB-sha"
            fileDiff.pathA = "path/A.kt"
            fileDiff.pathB = "path/B.kt"
            fileDiff.context = 5
            fileDiff.isBinaryA = true
            fileDiff.isBinaryB = true
            fileDiff.changeType = DiffEntry.ChangeType.MODIFY
            fileDiff.interestLine = 3
            fileDiff.interestSide = CodeRange.Side.A
            fileDiff.oldMode = FileMode.REGULAR_FILE
            fileDiff.newMode = FileMode.EXECUTABLE_FILE

            fileDiff.a shouldBe a
            fileDiff.b shouldBe b
            fileDiff.editList shouldBe edits
            fileDiff.commitA shouldBe "commitA-sha"
            fileDiff.commitB shouldBe "commitB-sha"
            fileDiff.pathA shouldBe "path/A.kt"
            fileDiff.pathB shouldBe "path/B.kt"
            fileDiff.context shouldBe 5
            fileDiff.isBinaryA shouldBe true
            fileDiff.isBinaryB shouldBe true
            fileDiff.changeType shouldBe DiffEntry.ChangeType.MODIFY
            fileDiff.interestLine shouldBe 3
            fileDiff.interestSide shouldBe CodeRange.Side.A
            fileDiff.oldMode shouldBe FileMode.REGULAR_FILE
            fileDiff.newMode shouldBe FileMode.EXECUTABLE_FILE
        }

        it("새로 생성한 FileDiff는 모든 프로퍼티가 기본값(주로 null)을 가진다") {
            val fileDiff = FileDiff()

            fileDiff.a shouldBe null
            fileDiff.b shouldBe null
            fileDiff.editList shouldBe null
            fileDiff.commitA shouldBe null
            fileDiff.commitB shouldBe null
            fileDiff.pathA shouldBe null
            fileDiff.pathB shouldBe null
            fileDiff.context shouldBe 3
            fileDiff.isBinaryA shouldBe false
            fileDiff.isBinaryB shouldBe false
            fileDiff.changeType shouldBe null
            fileDiff.interestLine shouldBe null
            fileDiff.interestSide shouldBe null
            fileDiff.oldMode shouldBe null
            fileDiff.newMode shouldBe null
        }
    }

    describe("companion object의 크기 제한 유틸리티") {
        it("getRawTextSize는 각 라인 길이의 합을 반환한다(0라인 포함)") {
            FileDiff.getRawTextSize(rawTextOf(listOf("ab", "cde"))) shouldBe 5
            FileDiff.getRawTextSize(rawTextOf(listOf(""))) shouldBe 0
        }

        it("isRawTextSizeExceeds는 문자 수와 라인 수 중 하나라도 초과하면 true다") {
            val small = rawTextOf(listOf("short"))
            FileDiff.isRawTextSizeExceeds(small) shouldBe false

            // 문자 수(SIZE_LIMIT=500*1024) 초과 - 라인 수는 1로 LINE_LIMIT 이내
            val sizeExceeded = rawTextOf(listOf("x".repeat(FileDiff.SIZE_LIMIT + 1)))
            FileDiff.isRawTextSizeExceeds(sizeExceeded) shouldBe true

            // 라인 수(LINE_LIMIT=5000) 초과 - 각 라인은 짧아 문자 수는 초과하지 않음
            val linesExceeded = rawTextOf((1..(FileDiff.LINE_LIMIT + 1)).map { "l" })
            FileDiff.isRawTextSizeExceeds(linesExceeded) shouldBe true
        }
    }

    describe("getHunks() - editList/a/b 중 하나라도 없으면 null") {
        it("editList가 null이면 null을 반환한다") {
            val fileDiff = FileDiff()
            fileDiff.a = rawTextOf(listOf("a0"))
            fileDiff.b = rawTextOf(listOf("b0"))

            fileDiff.getHunks() shouldBe null
        }

        it("a가 null이면 null을 반환한다") {
            val fileDiff = FileDiff()
            fileDiff.editList = editListOf(Edit(0, 1, 0, 1))
            fileDiff.b = rawTextOf(listOf("b0"))

            fileDiff.getHunks() shouldBe null
        }

        it("b가 null이면 null을 반환한다") {
            val fileDiff = FileDiff()
            fileDiff.editList = editListOf(Edit(0, 1, 0, 1))
            fileDiff.a = rawTextOf(listOf("a0"))

            fileDiff.getHunks() shouldBe null
        }
    }

    describe("getHunks() - 캐시 동작") {
        it("한번 계산된 hunks는 캐시되며, interestLine/interestSide를 설정하면 캐시가 무효화된다") {
            val fileDiff = newFileDiff(
                aLines = (0..9).map { "a$it" },
                bLines = (0..9).map { if (it == 5) "CHANGED" else "a$it" },
                edits = editListOf(Edit(5, 6, 5, 6))
            )

            val first = fileDiff.getHunks()
            val second = fileDiff.getHunks()
            (first === second) shouldBe true

            fileDiff.interestLine = 5
            val third = fileDiff.getHunks()
            (first === third) shouldBe false

            val fourth = fileDiff.getHunks()
            (third === fourth) shouldBe true

            fileDiff.interestSide = CodeRange.Side.A
            val fifth = fileDiff.getHunks()
            (fourth === fifth) shouldBe false
        }
    }

    describe("getHunks() - 단일 edit 기본 동작(리딩/트레일링 컨텍스트 포함)") {
        it("REMOVE/ADD 라인 앞뒤로 context 줄만큼 CONTEXT 라인을 만든다") {
            val fileDiff = newFileDiff(
                aLines = (0..9).map { "line$it" },
                bLines = (0..9).map { if (it == 5) "CHANGED" else "line$it" },
                edits = editListOf(Edit(5, 6, 5, 6)),
                context = 3
            )

            val hunks = fileDiff.getHunks()
            hunks shouldNotBe null
            hunks!!.size shouldBe 1

            val hunk = hunks[0]
            hunk.beginA shouldBe 2
            hunk.endA shouldBe 9
            hunk.beginB shouldBe 2
            hunk.endB shouldBe 9
            hunk.lines.map { it.kind } shouldBe listOf(
                DiffLineType.CONTEXT, DiffLineType.CONTEXT, DiffLineType.CONTEXT,
                DiffLineType.REMOVE, DiffLineType.ADD,
                DiffLineType.CONTEXT, DiffLineType.CONTEXT, DiffLineType.CONTEXT
            )
            hunk.lines[3].content shouldBe "line5"
            hunk.lines[4].content shouldBe "CHANGED"
            hunks.totalSize shouldBe hunk.size()
            hunks.lines shouldBe hunk.lines.size
        }

        it("파일 맨 앞(0번째 줄) 변경은 beginA/beginB가 0으로 잘린다") {
            val fileDiff = newFileDiff(
                aLines = (0..9).map { "line$it" },
                bLines = (0..9).map { if (it == 0) "CHANGED" else "line$it" },
                edits = editListOf(Edit(0, 1, 0, 1)),
                context = 3
            )

            val hunk = fileDiff.getHunks()!![0]
            hunk.beginA shouldBe 0
            hunk.beginB shouldBe 0
        }

        it("파일 맨 끝 줄 변경은 endA/endB가 파일 크기로 잘린다") {
            val fileDiff = newFileDiff(
                aLines = (0..9).map { "line$it" },
                bLines = (0..9).map { if (it == 9) "CHANGED" else "line$it" },
                edits = editListOf(Edit(9, 10, 9, 10)),
                context = 3
            )

            val hunk = fileDiff.getHunks()!![0]
            hunk.endA shouldBe 10
            hunk.endB shouldBe 10
        }
    }

    describe("getHunks() - findCombinedEnd/combineA/combineB (hunk 병합)") {
        it("두 edit 사이 간격이 2*context 이내면 하나의 hunk로 합쳐진다") {
            val fileDiff = newFileDiff(
                aLines = (0..19).map { "a$it" },
                bLines = (0..19).map { "a$it" },
                edits = editListOf(Edit(2, 3, 2, 3), Edit(7, 8, 7, 8)),
                context = 3
            )

            val hunks = fileDiff.getHunks()!!
            hunks.size shouldBe 1
            hunks[0].beginA shouldBe 0
            hunks[0].endA shouldBe 11
        }

        it("두 edit 사이 간격이 2*context를 초과하면 별도의 hunk로 나뉜다") {
            val fileDiff = newFileDiff(
                aLines = (0..19).map { "a$it" },
                bLines = (0..19).map { "a$it" },
                edits = editListOf(Edit(2, 3, 2, 3), Edit(15, 16, 15, 16)),
                context = 3
            )

            val hunks = fileDiff.getHunks()!!
            hunks.size shouldBe 2
        }

        it("A축 간격은 멀어도 B축 간격이 가까우면 combineB에 의해 병합된다") {
            val fileDiff = newFileDiff(
                aLines = (0..29).map { "a$it" },
                bLines = (0..19).map { "b$it" },
                edits = editListOf(Edit(2, 3, 2, 3), Edit(20, 21, 7, 8)),
                context = 3
            )

            val hunks = fileDiff.getHunks()!!
            hunks.size shouldBe 1
        }
    }

    describe("getHunks() - interestLine/interestSide 필터") {
        val edits = editListOf(Edit(2, 3, 2, 3), Edit(15, 16, 15, 16))

        it("interestSide=A일 때 interestLine이 hunk의 A범위 안이면 그 hunk 하나만 반환하고 이후 edit는 처리하지 않는다") {
            val fileDiff = newFileDiff(
                aLines = (0..19).map { "a$it" },
                bLines = (0..19).map { "a$it" },
                edits = edits,
                context = 3
            )
            fileDiff.interestLine = 4
            fileDiff.interestSide = CodeRange.Side.A

            val hunks = fileDiff.getHunks()!!
            hunks.size shouldBe 1
            hunks[0].beginA shouldBe 0
            hunks[0].endA shouldBe 6
        }

        it("interestSide=A일 때 interestLine이 어떤 hunk의 A범위에도 속하지 않으면 빈 결과다") {
            val fileDiff = newFileDiff(
                aLines = (0..19).map { "a$it" },
                bLines = (0..19).map { "a$it" },
                edits = edits,
                context = 3
            )
            fileDiff.interestLine = 100
            fileDiff.interestSide = CodeRange.Side.A

            val hunks = fileDiff.getHunks()!!
            hunks.size shouldBe 0
        }

        it("interestSide=B일 때 interestLine이 hunk의 B범위 안이면 그 hunk만 반환한다") {
            val fileDiff = newFileDiff(
                aLines = (0..19).map { "a$it" },
                bLines = (0..19).map { "a$it" },
                edits = edits,
                context = 3
            )
            fileDiff.interestLine = 4
            fileDiff.interestSide = CodeRange.Side.B

            val hunks = fileDiff.getHunks()!!
            hunks.size shouldBe 1
            hunks[0].beginB shouldBe 0
            hunks[0].endB shouldBe 6
        }

        it("interestSide=B일 때 interestLine이 어떤 hunk의 B범위에도 속하지 않으면 빈 결과다") {
            val fileDiff = newFileDiff(
                aLines = (0..19).map { "a$it" },
                bLines = (0..19).map { "a$it" },
                edits = edits,
                context = 3
            )
            fileDiff.interestLine = 100
            fileDiff.interestSide = CodeRange.Side.B

            val hunks = fileDiff.getHunks()!!
            hunks.size shouldBe 0
        }

        it("interestLine만 설정되고 interestSide가 null이면 필터링 없이 전체 hunk를 반환한다") {
            val fileDiff = newFileDiff(
                aLines = (0..19).map { "a$it" },
                bLines = (0..19).map { "a$it" },
                edits = edits,
                context = 3
            )
            fileDiff.interestLine = 4

            val hunks = fileDiff.getHunks()!!
            hunks.size shouldBe 2
        }

        it("interestSide=A일 때 interestLine이 어떤 hunk의 beginA보다도 작으면(첫 hunk 이후, 다음 hunk 이전) 빈 결과다") {
            val fileDiff = newFileDiff(
                aLines = (0..19).map { "a$it" },
                bLines = (0..19).map { "a$it" },
                edits = edits,
                context = 3
            )
            // 8은 hunk1의 endA(6)보다 크고 hunk2의 beginA(12)보다 작다:
            // hunk1은 endA조건에서, hunk2는 beginA조건에서 각각 false가 되어 매치되는 hunk가 없다.
            fileDiff.interestLine = 8
            fileDiff.interestSide = CodeRange.Side.A

            val hunks = fileDiff.getHunks()!!
            hunks.size shouldBe 0
        }

        it("interestSide=B일 때 interestLine이 어떤 hunk의 beginB보다도 작으면(첫 hunk 이후, 다음 hunk 이전) 빈 결과다") {
            val fileDiff = newFileDiff(
                aLines = (0..19).map { "a$it" },
                bLines = (0..19).map { "a$it" },
                edits = edits,
                context = 3
            )
            fileDiff.interestLine = 8
            fileDiff.interestSide = CodeRange.Side.B

            val hunks = fileDiff.getHunks()!!
            hunks.size shouldBe 0
        }

        it("interestSide만 설정되고 interestLine이 null이면 필터링 없이 전체 hunk를 반환한다") {
            val fileDiff = newFileDiff(
                aLines = (0..19).map { "a$it" },
                bLines = (0..19).map { "a$it" },
                edits = edits,
                context = 3
            )
            fileDiff.interestSide = CodeRange.Side.A

            val hunks = fileDiff.getHunks()!!
            hunks.size shouldBe 2
        }
    }

    describe("getHunks() - 크기 초과") {
        it("한 라인의 문자 수 합이 SIZE_LIMIT을 초과하면 SizeExceededHunks를 반환한다") {
            val hugeLine = "x".repeat(FileDiff.SIZE_LIMIT + 1)
            val fileDiff = newFileDiff(
                aLines = listOf("a"),
                bLines = listOf(hugeLine),
                edits = editListOf(Edit(0, 1, 0, 1)),
                context = 0
            )

            val hunks = fileDiff.getHunks()
            hunks.shouldBeInstanceOf<FileDiff.SizeExceededHunks>()
        }

        it("변경된 라인 수 합이 LINE_LIMIT을 초과하면 SizeExceededHunks를 반환한다") {
            val lineCount = FileDiff.LINE_LIMIT + 1
            val fileDiff = newFileDiff(
                aLines = (0 until lineCount).map { "a$it" },
                bLines = (0 until lineCount).map { "b$it" },
                edits = editListOf(Edit(0, lineCount, 0, lineCount)),
                context = 0
            )

            val hunks = fileDiff.getHunks()
            hunks.shouldBeInstanceOf<FileDiff.SizeExceededHunks>()
        }
    }

    describe("updateRange(lineA, lineB)") {
        fun freshEditList() = editListOf(Edit(10, 12, 10, 12))

        it("editList가 null이면 아무 동작도 하지 않는다") {
            val fileDiff = FileDiff()
            fileDiff.updateRange(10, 10)
            fileDiff.editList shouldBe null
        }

        it("lineA/lineB가 모두 null이면 결과 editList는 비어있다") {
            val fileDiff = FileDiff()
            fileDiff.editList = freshEditList()
            fileDiff.updateRange(null, null)
            fileDiff.editList!!.size shouldBe 0
        }

        it("lineA가 edit의 [beginA-context, endA+context] 범위 안이면 그 edit을 포함한다") {
            val fileDiff = FileDiff()
            fileDiff.editList = freshEditList()
            fileDiff.updateRange(10, null)
            fileDiff.editList!!.size shouldBe 1
        }

        it("lineA가 범위보다 작으면 포함하지 않는다") {
            val fileDiff = FileDiff()
            fileDiff.editList = freshEditList()
            fileDiff.updateRange(6, null) // beginA(10)-context(3)=7보다 작음
            fileDiff.editList!!.size shouldBe 0
        }

        it("lineA가 범위보다 크면 포함하지 않는다") {
            val fileDiff = FileDiff()
            fileDiff.editList = freshEditList()
            fileDiff.updateRange(16, null) // endA(12)+context(3)=15보다 큼
            fileDiff.editList!!.size shouldBe 0
        }

        it("lineB가 범위 안이면 포함한다") {
            val fileDiff = FileDiff()
            fileDiff.editList = freshEditList()
            fileDiff.updateRange(null, 10)
            fileDiff.editList!!.size shouldBe 1
        }

        it("lineB가 범위보다 작으면 포함하지 않는다") {
            val fileDiff = FileDiff()
            fileDiff.editList = freshEditList()
            fileDiff.updateRange(null, 6)
            fileDiff.editList!!.size shouldBe 0
        }

        it("lineB가 범위보다 크면 포함하지 않는다") {
            val fileDiff = FileDiff()
            fileDiff.editList = freshEditList()
            fileDiff.updateRange(null, 16)
            fileDiff.editList!!.size shouldBe 0
        }

        it("lineA와 lineB가 모두 매치하면 같은 edit이 두번 추가될 수 있다") {
            val fileDiff = FileDiff()
            fileDiff.editList = freshEditList()
            fileDiff.updateRange(10, 10)
            fileDiff.editList!!.size shouldBe 2
        }

        it("editList가 비어있으면 결과도 비어있다") {
            val fileDiff = FileDiff()
            fileDiff.editList = EditList()
            fileDiff.updateRange(10, 10)
            fileDiff.editList!!.size shouldBe 0
        }
    }

    describe("isFileModeChanged()") {
        it("oldMode/newMode가 모두 null이면(둘 다 MISSING과 동일한 0) false다") {
            val fileDiff = FileDiff()
            fileDiff.isFileModeChanged() shouldBe false
        }

        it("newMode가 null(0)이고 oldMode가 실제 모드면 MISSING 취급으로 false다") {
            val fileDiff = FileDiff()
            fileDiff.oldMode = FileMode.REGULAR_FILE
            fileDiff.isFileModeChanged() shouldBe false
        }

        it("oldMode가 null(0)이고 newMode가 실제 모드면 MISSING 취급으로 false다") {
            val fileDiff = FileDiff()
            fileDiff.newMode = FileMode.REGULAR_FILE
            fileDiff.isFileModeChanged() shouldBe false
        }

        it("oldMode와 newMode가 다르면 true다") {
            val fileDiff = FileDiff()
            fileDiff.oldMode = FileMode.REGULAR_FILE
            fileDiff.newMode = FileMode.EXECUTABLE_FILE
            fileDiff.isFileModeChanged() shouldBe true
        }

        it("oldMode와 newMode가 같으면 false다") {
            val fileDiff = FileDiff()
            fileDiff.oldMode = FileMode.REGULAR_FILE
            fileDiff.newMode = FileMode.REGULAR_FILE
            fileDiff.isFileModeChanged() shouldBe false
        }
    }

    describe("에러 관리: addError/hasError/hasAnyError/refreshErrors") {
        it("addError로 추가한 에러를 hasError(error)로 조회할 수 있다") {
            val fileDiff = FileDiff()
            fileDiff.addError(FileDiff.Error.OTHERS_SIZE_EXCEEDED)

            fileDiff.hasError(FileDiff.Error.OTHERS_SIZE_EXCEEDED) shouldBe true
            fileDiff.hasError(FileDiff.Error.A_SIZE_EXCEEDED) shouldBe false
        }

        it("에러가 하나도 없으면 hasError()는 false, 하나라도 있으면 true다") {
            val fileDiff = FileDiff()
            fileDiff.hasError() shouldBe false

            fileDiff.addError(FileDiff.Error.OTHERS_SIZE_EXCEEDED)
            fileDiff.hasError() shouldBe true
        }

        it("hasAnyError는 vararg 중 하나라도 포함되면 true, 전부 없으면 false다") {
            val fileDiff = FileDiff()
            fileDiff.addError(FileDiff.Error.A_SIZE_EXCEEDED)

            fileDiff.hasAnyError(FileDiff.Error.A_SIZE_EXCEEDED, FileDiff.Error.B_SIZE_EXCEEDED) shouldBe true
            fileDiff.hasAnyError(FileDiff.Error.B_SIZE_EXCEEDED, FileDiff.Error.DIFF_SIZE_EXCEEDED) shouldBe false
        }

        it("refreshErrors는 getHunks가 SizeExceededHunks면 DIFF_SIZE_EXCEEDED를 추가한다") {
            val lineCount = FileDiff.LINE_LIMIT + 1
            val fileDiff = newFileDiff(
                aLines = (0 until lineCount).map { "a$it" },
                bLines = (0 until lineCount).map { "b$it" },
                edits = editListOf(Edit(0, lineCount, 0, lineCount)),
                context = 0
            )

            fileDiff.hasError(FileDiff.Error.DIFF_SIZE_EXCEEDED) shouldBe true
        }

        it("refreshErrors는 editList가 null이고 a가 크기를 초과하면 A_SIZE_EXCEEDED를 추가한다") {
            val fileDiff = FileDiff()
            fileDiff.a = rawTextOf((1..(FileDiff.LINE_LIMIT + 1)).map { "l" })

            fileDiff.hasError(FileDiff.Error.A_SIZE_EXCEEDED) shouldBe true
        }

        it("refreshErrors는 editList가 null이고 b가 크기를 초과하면 B_SIZE_EXCEEDED를 추가한다") {
            val fileDiff = FileDiff()
            fileDiff.b = rawTextOf((1..(FileDiff.LINE_LIMIT + 1)).map { "l" })

            fileDiff.hasError(FileDiff.Error.B_SIZE_EXCEEDED) shouldBe true
        }

        it("refreshErrors는 editList가 null이고 a/b가 크기 이내면 에러를 추가하지 않는다") {
            val fileDiff = FileDiff()
            fileDiff.a = rawTextOf(listOf("short"))
            fileDiff.b = rawTextOf(listOf("short"))

            fileDiff.hasError() shouldBe false
        }

        it("editList가 non-null이면 a/b 크기 초과 여부와 무관하게 A/B_SIZE_EXCEEDED를 추가하지 않는다") {
            val fileDiff = FileDiff()
            fileDiff.a = rawTextOf((1..(FileDiff.LINE_LIMIT + 1)).map { "l" })
            fileDiff.b = rawTextOf((1..(FileDiff.LINE_LIMIT + 1)).map { "l" })
            fileDiff.editList = EditList()

            fileDiff.hasError(FileDiff.Error.A_SIZE_EXCEEDED) shouldBe false
            fileDiff.hasError(FileDiff.Error.B_SIZE_EXCEEDED) shouldBe false
        }
    }

    describe("equals()") {
        fun base(): FileDiff {
            val fileDiff = FileDiff()
            fileDiff.commitA = "ca"
            fileDiff.commitB = "cb"
            fileDiff.editList = editListOf(Edit(0, 1, 0, 1))
            fileDiff.pathA = "pa"
            fileDiff.pathB = "pb"
            fileDiff.changeType = DiffEntry.ChangeType.MODIFY
            return fileDiff
        }

        it("자기 자신과는 true다") {
            val fileDiff = base()
            (fileDiff == fileDiff) shouldBe true
        }

        it("null과 비교하면 false다") {
            val fileDiff = base()
            fileDiff.equals(null) shouldBe false
        }

        it("다른 클래스의 객체와 비교하면 false다") {
            val fileDiff = base()
            fileDiff.equals("not a FileDiff") shouldBe false
        }

        it("모든 필드가 같으면 true다") {
            base() shouldBe base()
        }

        it("commitA가 다르면 false다") {
            val other = base()
            other.commitA = "different"
            base() shouldBe base()
            (base() == other) shouldBe false
        }

        it("commitB가 다르면 false다") {
            val other = base()
            other.commitB = "different"
            (base() == other) shouldBe false
        }

        it("editList가 다르면 false다") {
            val other = base()
            other.editList = editListOf(Edit(0, 2, 0, 2))
            (base() == other) shouldBe false
        }

        it("pathA가 다르면 false다") {
            val other = base()
            other.pathA = "different"
            (base() == other) shouldBe false
        }

        it("pathB가 다르면 false다") {
            val other = base()
            other.pathB = "different"
            (base() == other) shouldBe false
        }

        it("changeType이 다르면 false다") {
            val other = base()
            other.changeType = DiffEntry.ChangeType.DELETE
            (base() == other) shouldBe false
        }
    }

    describe("hashCode()") {
        it("모든 관련 필드가 null이면 hashCode는 0이다") {
            val fileDiff = FileDiff()
            fileDiff.hashCode() shouldBe 0
        }

        it("모든 관련 필드가 채워져 있으면 각 필드의 hashCode를 조합한 값을 반환하고, equals와 일관된다") {
            val fileDiff = FileDiff()
            fileDiff.editList = editListOf(Edit(0, 1, 0, 1))
            fileDiff.commitA = "ca"
            fileDiff.commitB = "cb"
            fileDiff.pathA = "pa"
            fileDiff.pathB = "pb"
            fileDiff.changeType = DiffEntry.ChangeType.MODIFY

            var expected = fileDiff.editList.hashCode()
            expected = 31 * expected + fileDiff.commitA.hashCode()
            expected = 31 * expected + fileDiff.commitB.hashCode()
            expected = 31 * expected + fileDiff.pathA.hashCode()
            expected = 31 * expected + fileDiff.pathB.hashCode()
            expected = 31 * expected + fileDiff.changeType.hashCode()

            fileDiff.hashCode() shouldBe expected

            val other = FileDiff()
            other.editList = fileDiff.editList
            other.commitA = fileDiff.commitA
            other.commitB = fileDiff.commitB
            other.pathA = fileDiff.pathA
            other.pathB = fileDiff.pathB
            other.changeType = fileDiff.changeType

            (fileDiff == other) shouldBe true
            fileDiff.hashCode() shouldBe other.hashCode()
        }
    }

    describe("toString()") {
        it("주요 식별 필드를 포함한 문자열을 반환한다") {
            val fileDiff = FileDiff()
            fileDiff.commitA = "ca"
            fileDiff.commitB = "cb"
            fileDiff.pathA = "pa"
            fileDiff.pathB = "pb"
            fileDiff.changeType = DiffEntry.ChangeType.MODIFY

            val result = fileDiff.toString()

            result shouldContain "ca"
            result shouldContain "cb"
            result shouldContain "pa"
            result shouldContain "pb"
            result shouldContain "MODIFY"
        }
    }
})
