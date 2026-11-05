package dev.yona.io

/**
 * 텍스트의 줄바꿈 종류를 알아내고 바꾼다. v1.6 `utils.LineEnding`(`app/utils/LineEnding.java`)에 해당한다.
 *
 * v1.6의 DOS 변환은 `\n`을 `\n`으로 바꾸는 코드라 실제로는 아무것도 바꾸지 않아, CRLF 파일을 온라인으로 고쳐도 LF로 저장되었다.
 * DOS로 바꿀 때는 먼저 `\r\n`을 `\n`으로 정규화한 뒤 `\n`을 `\r\n`으로 바꾼다(R-27).
 */
object LineEnding {

    /** 줄바꿈 종류이다. [UNDEFINED]는 내용이 비어 알 수 없는 경우이다. */
    enum class EndingType(val value: String) { DOS("\r\n"), UNIX("\n"), UNDEFINED("") }

    /** 줄바꿈 종류를 알 수 없을 때 쓰는 기본값이다. */
    val DEFAULT_ENDING_TYPE = EndingType.UNIX

    /** [contents]에 `\r\n`이 있으면 DOS, 비어 있으면 UNDEFINED, 아니면 UNIX이다. */
    fun find(contents: String?): EndingType = when {
        contents.isNullOrEmpty() -> EndingType.UNDEFINED
        contents.contains("\r\n") -> EndingType.DOS
        else -> EndingType.UNIX
    }

    /** 줄바꿈을 [to]("DOS"이면 DOS, 그 밖은 UNIX)에 맞춘다. */
    fun change(contents: String?, to: String?): String =
        change(contents, if (to.equals("DOS", ignoreCase = true)) EndingType.DOS else EndingType.UNIX)

    /** 줄바꿈을 [to]에 맞춘다. DOS이면 섞여 있는 줄바꿈까지 모두 CRLF로, UNIX이면 CRLF를 LF로 바꾼다. */
    fun change(contents: String?, to: EndingType): String {
        val current = find(contents)
        if (contents.isNullOrEmpty()) return ""
        if (to == EndingType.DOS) return contents.replace("\r\n", "\n").replace("\n", "\r\n")
        if (current != EndingType.UNIX && to == EndingType.UNIX) return contents.replace("\r\n", "\n")
        return contents
    }

    /** 내용이 줄바꿈으로 끝나지 않으면 내용의 줄바꿈 종류(모르면 UNIX)로 한 줄 끝을 붙인다. [contents]가 `null`이면 `null`이다. */
    fun addEol(contents: String?): String? {
        if (contents == null) return null
        // 빈 내용은 그대로 둔다. v1.6은 개행 한 줄을 붙여 빈 파일이 개행 한 줄짜리 파일이 되었다(C079)
        if (contents.isEmpty()) return contents
        val type = find(contents).let { if (it == EndingType.UNDEFINED) DEFAULT_ENDING_TYPE else it }
        return if (contents.endsWith(type.value)) contents else contents + type.value
    }
}
