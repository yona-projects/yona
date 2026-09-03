package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.support.CodeRange
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.EditList
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.lib.FileMode
import java.util.HashSet

class FileDiff {
    companion object {
        const val SIZE_LIMIT = 500 * 1024
        const val LINE_LIMIT = 5000

        fun isRawTextSizeExceeds(rawText: RawText): Boolean {
            return getRawTextSize(rawText) > SIZE_LIMIT || rawText.size() > LINE_LIMIT
        }

        fun getRawTextSize(rawText: RawText): Int {
            var size = 0
            for (i in 0 until rawText.size()) {
                size += rawText.getString(i).length
            }
            return size
        }
    }

    private val errors: MutableSet<Error> = HashSet()

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

    var interestLine: Int? = null
        set(value) {
            field = value
            hunks = null
        }

    var interestSide: CodeRange.Side? = null
        set(value) {
            field = value
            hunks = null
        }

    var oldMode: FileMode? = null
    var newMode: FileMode? = null
    private var hunks: Hunks? = null

    enum class Error {
        A_SIZE_EXCEEDED, B_SIZE_EXCEEDED, DIFF_SIZE_EXCEEDED, OTHERS_SIZE_EXCEEDED
    }

    open class Hunks : ArrayList<Hunk>() {
        var totalSize: Int = 0
        var lines: Int = 0
    }

    class SizeExceededHunks : Hunks()

    fun getHunks(): Hunks? {
        if (hunks != null) {
            return hunks
        }

        val currentEditList = editList ?: return null
        val currentA = a ?: return null
        val currentB = b ?: return null

        var size = 0
        var lines = 0

        val newHunks = Hunks()
        var curIdx = 0
        while (curIdx < currentEditList.size) {
            val hunk = Hunk()
            var curEdit = currentEditList[curIdx]
            val endIdx = findCombinedEnd(currentEditList, curIdx)
            val endEdit = currentEditList[endIdx]

            var aCur = Math.max(0, curEdit.beginA - context)
            var bCur = Math.max(0, curEdit.beginB - context)
            val aEnd = Math.min(currentA.size(), endEdit.endA + context)
            val bEnd = Math.min(currentB.size(), endEdit.endB + context)

            hunk.beginA = aCur
            hunk.endA = aEnd
            hunk.beginB = bCur
            hunk.endB = bEnd

            while (aCur < aEnd || bCur < bEnd) {
                if (aCur < curEdit.beginA || endIdx + 1 < curIdx) {
                    hunk.lines.add(DiffLine(this, DiffLineType.CONTEXT, aCur, bCur, currentA.getString(aCur)))
                    aCur++
                    bCur++
                } else if (aCur < curEdit.endA) {
                    hunk.lines.add(DiffLine(this, DiffLineType.REMOVE, aCur, null, currentA.getString(aCur)))
                    aCur++
                } else if (bCur < curEdit.endB) {
                    hunk.lines.add(DiffLine(this, DiffLineType.ADD, null, bCur, currentB.getString(bCur)))
                    bCur++
                }

                if (end(curEdit, aCur, bCur) && ++curIdx < currentEditList.size) {
                    curEdit = currentEditList[curIdx]
                }
            }

            val targetInterestLine = interestLine
            val targetInterestSide = interestSide
            if (targetInterestLine != null && targetInterestSide != null) {
                var added = false
                when (targetInterestSide) {
                    CodeRange.Side.A -> {
                        if (hunk.beginA <= targetInterestLine && hunk.endA >= targetInterestLine) {
                            newHunks.add(hunk)
                            size += hunk.size()
                            lines += hunk.lines.size
                            added = true
                        }
                    }
                    CodeRange.Side.B -> {
                        if (hunk.beginB <= targetInterestLine && hunk.endB >= targetInterestLine) {
                            newHunks.add(hunk)
                            size += hunk.size()
                            lines += hunk.lines.size
                            added = true
                        }
                    }
                }
                if (added) {
                    break
                }
            } else {
                newHunks.add(hunk)
                size += hunk.size()
                lines += hunk.lines.size
            }

            if (size > SIZE_LIMIT || lines > LINE_LIMIT) {
                hunks = SizeExceededHunks()
                return hunks
            }
        }

        newHunks.totalSize = size
        newHunks.lines = lines
        hunks = newHunks

        return hunks
    }

    private fun findCombinedEnd(edits: List<Edit>, i: Int): Int {
        var end = i + 1
        while (end < edits.size && (combineA(edits, end) || combineB(edits, end))) {
            end++
        }
        return end - 1
    }

    private fun combineA(e: List<Edit>, i: Int): Boolean {
        return e[i].beginA - e[i - 1].endA <= 2 * context
    }

    private fun combineB(e: List<Edit>, i: Int): Boolean {
        return e[i].beginB - e[i - 1].endB <= 2 * context
    }

    private fun end(edit: Edit, a: Int, b: Int): Boolean {
        return edit.endA <= a && edit.endB <= b
    }

    fun updateRange(lineA: Int?, lineB: Int?) {
        val currentEditList = editList ?: return

        val newEditList = EditList()
        for (edit in currentEditList) {
            if (lineA != null) {
                if (lineA >= edit.beginA - context && lineA <= edit.endA + context) {
                    newEditList.add(edit)
                }
            }
            if (lineB != null) {
                if (lineB >= edit.beginB - context && lineB <= edit.endB + context) {
                    newEditList.add(edit)
                }
            }
        }
        editList = newEditList
    }

    fun isFileModeChanged(): Boolean {
        val oldBits = oldMode?.bits ?: 0
        val newBits = newMode?.bits ?: 0
        if (FileMode.MISSING.bits == oldBits || FileMode.MISSING.bits == newBits) {
            return false
        }
        return oldBits != newBits
    }

    fun addError(error: Error) {
        this.errors.add(error)
    }

    fun hasAnyError(vararg checkErrors: Error): Boolean {
        refreshErrors()
        return checkErrors.any { this.errors.contains(it) }
    }

    private fun refreshErrors() {
        if (getHunks() is SizeExceededHunks) {
            addError(Error.DIFF_SIZE_EXCEEDED)
        }
        if (editList == null) {
            a?.let {
                if (isRawTextSizeExceeds(it)) {
                    addError(Error.A_SIZE_EXCEEDED)
                }
            }
            b?.let {
                if (isRawTextSizeExceeds(it)) {
                    addError(Error.B_SIZE_EXCEEDED)
                }
            }
        }
    }

    fun hasError(error: Error): Boolean {
        refreshErrors()
        return this.errors.contains(error)
    }

    fun hasError(): Boolean {
        refreshErrors()
        return this.errors.isNotEmpty()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as FileDiff

        if (commitA != other.commitA) return false
        if (commitB != other.commitB) return false
        if (editList != other.editList) return false
        if (pathA != other.pathA) return false
        if (pathB != other.pathB) return false
        if (changeType != other.changeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = editList?.hashCode() ?: 0
        result = 31 * result + (commitA?.hashCode() ?: 0)
        result = 31 * result + (commitB?.hashCode() ?: 0)
        result = 31 * result + (pathA?.hashCode() ?: 0)
        result = 31 * result + (pathB?.hashCode() ?: 0)
        result = 31 * result + (changeType?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "FileDiff(commitA=$commitA, commitB=$commitB, pathA=$pathA, pathB=$pathB, changeType=$changeType)"
    }
}
