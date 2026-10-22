package dev.yona.repository.diff

import dev.yona.domain.enumeration.Side
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.EditList
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.FileMode

/** 차이 줄의 종류이다. 바뀌지 않은 앞뒤 줄(CONTEXT), 추가한 줄(ADD), 지운 줄(REMOVE). */
enum class DiffLineType { CONTEXT, ADD, REMOVE }

/**
 * 차이의 한 줄이다. v1.6 `playRepository.DiffLine`에 해당한다.
 *
 * @property numA 바뀌기 전 파일의 줄 번호(0부터). 추가한 줄은 `null`
 * @property numB 바뀐 뒤 파일의 줄 번호(0부터). 지운 줄은 `null`
 * @property file 이 줄이 속한 파일의 차이
 */
class DiffLine(val file: FileDiff, val kind: DiffLineType, val numA: Int?, val numB: Int?, val content: String)

/**
 * 파일 하나에서 이어진 바뀐 부분이다(앞뒤 문맥 줄 포함). v1.6 `playRepository.Hunk`에 해당한다.
 * `begin`은 포함, `end`는 미포함인 줄 번호이다(0부터).
 */
class Hunk(val beginA: Int, val endA: Int, val beginB: Int, val endB: Int, val lines: MutableList<DiffLine> = ArrayList()) {
    /** 줄 내용의 글자 수 합이다. */
    fun size(): Int = lines.sumOf { it.content.length }
}

/** 파일의 헝크 모음이다. [totalSize]는 글자 수, [totalLines]는 줄 수의 합이다(v1.6 `Hunks.size`, `Hunks.lines`). */
open class Hunks : ArrayList<Hunk>() {
    var totalSize = 0
    var totalLines = 0
}

/** 헝크가 너무 커서 보여 주지 않는 표시이다. */
class SizeExceededHunks : Hunks()

/**
 * 파일 하나의 차이이다. v1.6 `playRepository.FileDiff`(`app/playRepository/FileDiff.java`)에 해당한다. 헝크를 계산하는 부분은 JGit
 * `DiffFormatter`에서 가져온 코드를 옮겼다.
 *
 * @property commitA 바뀌기 전 커밋 번호. 처음이면 `null`
 * @property pathA 바뀌기 전 경로(추가한 파일이면 `null`)
 * @property pathB 바뀐 뒤 경로(지운 파일이면 `null`)
 * @property a 바뀌기 전 내용(이진이거나 너무 크면 `null`)
 * @property editList 바뀐 곳의 목록. 파일을 추가하거나 지웠으면 `null`
 * @property interestLine 관심 있는 줄. 정하면 그 줄을 담은 헝크 하나만 만든다(코드 리뷰 댓글 위치)
 * @property context 앞뒤로 보여 줄 문맥 줄 수
 */
class FileDiff {

    enum class Error { A_SIZE_EXCEEDED, B_SIZE_EXCEEDED, DIFF_SIZE_EXCEEDED, OTHERS_SIZE_EXCEEDED }

    var a: RawText? = null
    var b: RawText? = null
    var editList: EditList? = null
    var commitA: String? = null
    var commitB: String? = null
    var pathA: String? = null
    var pathB: String? = null
    var context: Int = 3
    var isBinaryA: Boolean = false
    var isBinaryB: Boolean = false
    var changeType: DiffEntry.ChangeType? = null
    var oldMode: FileMode? = null
    var newMode: FileMode? = null

    private val errors = LinkedHashSet<Error>()
    private var hunks: Hunks? = null

    var interestLine: Int? = null
        set(value) { field = value; hunks = null }
    var interestSide: Side? = null
        set(value) { field = value; hunks = null }

    /** 헝크 목록이다. 바뀐 곳 목록이 없으면(추가하거나 지운 파일) `null`이다. 너무 크면 [SizeExceededHunks]이다. */
    fun hunks(): Hunks? {
        hunks?.let { return it }
        val edits = editList ?: return null
        val textA = requireNotNull(a)
        val textB = requireNotNull(b)
        var size = 0
        var lines = 0
        val result = Hunks()
        hunks = result

        var curIdx = 0
        while (curIdx < edits.size) {
            var curEdit = edits[curIdx]
            val endIdx = findCombinedEnd(edits, curIdx)
            val endEdit = edits[endIdx]
            var aCur = maxOf(0, curEdit.beginA - context)
            var bCur = maxOf(0, curEdit.beginB - context)
            val aEnd = minOf(textA.size(), endEdit.endA + context)
            val bEnd = minOf(textB.size(), endEdit.endB + context)
            val hunk = Hunk(aCur, aEnd, bCur, bEnd)

            while (aCur < aEnd || bCur < bEnd) {
                if (aCur < curEdit.beginA || endIdx + 1 < curIdx) {
                    hunk.lines.add(DiffLine(this, DiffLineType.CONTEXT, aCur, bCur, textA.getString(aCur)))
                    aCur++
                    bCur++
                } else if (aCur < curEdit.endA) {
                    hunk.lines.add(DiffLine(this, DiffLineType.REMOVE, aCur, null, textA.getString(aCur)))
                    aCur++
                } else if (bCur < curEdit.endB) {
                    hunk.lines.add(DiffLine(this, DiffLineType.ADD, null, bCur, textB.getString(bCur)))
                    bCur++
                }
                if (end(curEdit, aCur, bCur) && ++curIdx < edits.size) curEdit = edits[curIdx]
            }

            val line = interestLine
            val side = interestSide
            if (line != null && side != null) {
                val wanted = when (side) {
                    Side.A -> hunk.beginA <= line && hunk.endA >= line
                    Side.B -> hunk.beginB <= line && hunk.endB >= line
                }
                if (wanted) {
                    result.add(hunk)
                    size += hunk.size()
                    lines += hunk.lines.size
                    break
                }
            } else {
                result.add(hunk)
                size += hunk.size()
                lines += hunk.lines.size
            }

            if (size > SIZE_LIMIT || lines > LINE_LIMIT) {
                hunks = SizeExceededHunks()
                return hunks
            }
        }
        result.totalSize = size
        result.totalLines = lines
        return result
    }

    private fun findCombinedEnd(edits: List<Edit>, i: Int): Int {
        var end = i + 1
        while (end < edits.size && (combineA(edits, end) || combineB(edits, end))) end++
        return end - 1
    }

    private fun combineA(e: List<Edit>, i: Int) = e[i].beginA - e[i - 1].endA <= 2 * context

    private fun combineB(e: List<Edit>, i: Int) = e[i].beginB - e[i - 1].endB <= 2 * context

    private fun end(edit: Edit, a: Int, b: Int) = edit.endA <= a && edit.endB <= b

    /** 파일 모드(권한)가 바뀌었으면 `true`이다. 추가하거나 지운 파일은 아니다. */
    fun isFileModeChanged(): Boolean {
        val old = oldMode ?: return false
        val new = newMode ?: return false
        if (old.bits == FileMode.MISSING.bits || new.bits == FileMode.MISSING.bits) return false
        return old.bits != new.bits
    }

    fun addError(error: Error) { errors.add(error) }

    fun hasAnyError(vararg wanted: Error): Boolean {
        refreshErrors()
        return wanted.any { it in errors }
    }

    fun hasError(error: Error): Boolean { refreshErrors(); return error in errors }

    fun hasError(): Boolean { refreshErrors(); return errors.isNotEmpty() }

    private fun refreshErrors() {
        if (hunks() is SizeExceededHunks) addError(Error.DIFF_SIZE_EXCEEDED)
        // 바뀐 곳 목록이 이미 있으면 원문 크기는 신경 쓰지 않는다.
        if (editList == null && a != null && isRawTextSizeExceeds(a!!)) addError(Error.A_SIZE_EXCEEDED)
        if (editList == null && b != null && isRawTextSizeExceeds(b!!)) addError(Error.B_SIZE_EXCEEDED)
    }

    /**
     * 관심 있는 줄과 관련 없는 바뀐 곳을 모두 지운다. 파일을 추가하거나 지웠으면(바뀐 곳 목록이 없으면) 아무것도 하지 않는다.
     *
     * @param lineA 관심 있는 이전 쪽 줄(없으면 `null`)
     * @param lineB 관심 있는 이후 쪽 줄(없으면 `null`)
     */
    fun updateRange(lineA: Int?, lineB: Int?) {
        val edits = editList ?: return
        val kept = EditList()
        for (edit in edits) {
            if (lineA != null && lineA >= edit.beginA - context && lineA <= edit.endA + context) kept.add(edit)
            if (lineB != null && lineB >= edit.beginB - context && lineB <= edit.endB + context) kept.add(edit)
        }
        editList = kept
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is FileDiff && commitA == other.commitA && commitB == other.commitB && editList == other.editList &&
            pathA == other.pathA && pathB == other.pathB && changeType == other.changeType)

    override fun hashCode(): Int {
        var result = editList?.hashCode() ?: 0
        result = 31 * result + (commitA?.hashCode() ?: 0)
        result = 31 * result + (commitB?.hashCode() ?: 0)
        result = 31 * result + (pathA?.hashCode() ?: 0)
        result = 31 * result + (pathB?.hashCode() ?: 0)
        result = 31 * result + (changeType?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "FileDiff{commitA='$commitA', commitB='$commitB', pathA='$pathA', pathB='$pathB', changeType=$changeType}"

    companion object {
        /** 파일 하나를 보여 주는 최대 글자 수(500KB)이다. */
        const val SIZE_LIMIT = 500 * 1024

        /** 파일 하나를 보여 주는 최대 줄 수이다. */
        const val LINE_LIMIT = 5000

        /** [rawText]가 보여 줄 수 있는 크기를 넘으면 `true`이다. */
        fun isRawTextSizeExceeds(rawText: RawText): Boolean = rawTextSize(rawText) > SIZE_LIMIT || rawText.size() > LINE_LIMIT

        fun rawTextSize(rawText: RawText): Int = (0 until rawText.size()).sumOf { rawText.getString(it).length }
    }
}
