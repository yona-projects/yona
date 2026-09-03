package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.support.CodeRange

data class CodeRangeRequest(
    val path: String? = null,
    val startSide: String? = null,
    val startLine: Int? = null,
    val startColumn: Int? = null,
    val endSide: String? = null,
    val endLine: Int? = null,
    val endColumn: Int? = null
) {
    fun toCodeRange(): CodeRange? {
        if (startLine == null) return null
        return CodeRange(
            path = path,
            startSide = startSide?.let { CodeRange.Side.valueOf(it.uppercase()) },
            startLine = startLine,
            startColumn = startColumn,
            endSide = endSide?.let { CodeRange.Side.valueOf(it.uppercase()) },
            endLine = endLine,
            endColumn = endColumn
        )
    }
}
