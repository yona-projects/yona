package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.vcs.DiffLine
import com.github.yonaprojects.yona.domain.vcs.FileDiff
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

@Embeddable
class CodeRange(
    var path: String? = null,

    @Enumerated(EnumType.STRING)
    var startSide: Side? = null,

    var startLine: Int? = null,
    var startColumn: Int? = null,

    @Enumerated(EnumType.STRING)
    var endSide: Side? = null,

    var endLine: Int? = null,
    var endColumn: Int? = null
) {

    fun isFor(diff: FileDiff): Boolean {
        if (endSide == Side.B && diff.pathB != path) {
            return false
        }
        if (endSide == Side.A && diff.pathA != path) {
            return false
        }
        return true
    }

    fun endsWith(line: DiffLine): Boolean {
        return (endSide == Side.A && endLine == line.numA) ||
               (endSide == Side.B && endLine == line.numB)
    }

    enum class Side {
        A, B
    }
}
