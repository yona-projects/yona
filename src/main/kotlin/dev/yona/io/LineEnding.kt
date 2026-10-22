package dev.yona.io

/**
 * 텍스트의 줄바꿈 종류를 알아내고 바꾼다. v1.6 `utils.LineEnding`(`app/utils/LineEnding.java`)에 해당한다.
 *
 * v1.6의 DOS 변환은 `\n`을 `\n`으로 바꾸는 코드라 실제로는 아무것도 바꾸지 않는다. 같은 결과를 내도록 그대로 재현한다
 * ([refactoring-backlog.md] R-27).
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

    /** 줄바꿈을 [to]에 맞춘다. DOS로는 바꾸지 않는다(v1.6 결함 재현). */
    fun change(contents: String?, to: EndingType): String {
        val current = find(contents)
        if (contents.isNullOrEmpty()) return ""
        if (current != EndingType.DOS && to == EndingType.DOS) return contents.replace("\n", "\n")
        if (current != EndingType.UNIX && to == EndingType.UNIX) return contents.replace("\r\n", "\n")
        return contents
    }

    /** 내용이 줄바꿈으로 끝나지 않으면 내용의 줄바꿈 종류(모르면 UNIX)로 한 줄 끝을 붙인다. [contents]가 `null`이면 `null`이다. */
    fun addEol(contents: String?): String? {
        if (contents == null) return null
        val type = find(contents).let { if (it == EndingType.UNDEFINED) DEFAULT_ENDING_TYPE else it }
        return if (contents.endsWith(type.value)) contents else contents + type.value
    }
}
