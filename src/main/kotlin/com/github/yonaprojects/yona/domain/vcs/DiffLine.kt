package com.github.yonaprojects.yona.domain.vcs

class DiffLine(
    var file: FileDiff?,
    val kind: DiffLineType,
    val numA: Int?,
    val numB: Int?,
    val content: String
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as DiffLine

        if (content != other.content) return false
        if (file != other.file) return false
        if (kind != other.kind) return false
        if (numA != other.numA) return false
        if (numB != other.numB) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + (numA ?: 0)
        result = 31 * result + (numB ?: 0)
        result = 31 * result + content.hashCode()
        result = 31 * result + (file?.hashCode() ?: 0)
        return result
    }
}
