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
        // startLine(Int?)과 달리 startSide/endSide(String?)는 폼에 빈 문자열로 제출돼도 Spring
        // 바인딩이 null로 바꿔주지 않는다 - Side.valueOf("")가 예외를 던지므로 빈 문자열도
        // "값 없음"으로 취급한다(ReviewViewController.newCommitComment의 SVN 분기에서 동일한
        // 문제를 실제로 재현해 발견한 것과 같은 계열).
        return CodeRange(
            path = path,
            startSide = startSide?.takeIf { it.isNotBlank() }?.let { CodeRange.Side.valueOf(it.uppercase()) },
            startLine = startLine,
            startColumn = startColumn,
            endSide = endSide?.takeIf { it.isNotBlank() }?.let { CodeRange.Side.valueOf(it.uppercase()) },
            endLine = endLine,
            endColumn = endColumn
        )
    }
}
