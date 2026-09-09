package com.github.yonaprojects.yona.domain.support

// yona utils/LineEnding.java 대응(코드브라우저 편집 커밋에서 post.lineEnding 처리에 필요).
// changeLineEnding(contents, EndingType.DOS) 분기의 `contents.replace("\n", "\n")`는 yona 원본의
// `contents.replaceAll("\\n", "\\n")`과 동일한 무의미한 치환(no-op) 버그를 그대로 재현한 것 —
// DOS 개행 변환이 실제로는 동작하지 않는 legacy 동작을 의도적으로 보존한다.
object LineEnding {
    enum class EndingType(val value: String) {
        DOS("\r\n"), UNIX("\n"), UNDEFINED("")
    }

    val DEFAULT_ENDING_TYPE = EndingType.UNIX

    fun changeLineEnding(contents: String, to: String?): String {
        return if (!to.isNullOrEmpty() && to.equals("DOS", ignoreCase = true)) {
            changeLineEnding(contents, EndingType.DOS)
        } else {
            changeLineEnding(contents, EndingType.UNIX)
        }
    }

    fun changeLineEnding(contents: String, to: EndingType): String {
        val endingType = findLineEnding(contents)
        if (contents.isEmpty()) {
            return ""
        }
        if (endingType != EndingType.DOS && to == EndingType.DOS) {
            return contents.replace("\n", "\n")
        }
        if (endingType != EndingType.UNIX && to == EndingType.UNIX) {
            return contents.replace("\r\n", "\n")
        }
        return contents
    }

    fun addEOL(contents: String?): String? {
        if (contents == null) {
            return contents
        }
        var endingType = findLineEnding(contents)
        if (endingType == EndingType.UNDEFINED) {
            endingType = DEFAULT_ENDING_TYPE
        }
        if (!contents.endsWith(endingType.value)) {
            return contents + endingType.value
        }
        return contents
    }

    fun findLineEnding(contents: String?): EndingType {
        if (contents.isNullOrEmpty()) {
            return EndingType.UNDEFINED
        }
        return if (contents.contains("\r\n")) EndingType.DOS else EndingType.UNIX
    }
}
