package com.github.yonaprojects.yona.domain.vcs

class Hunk(
    var beginA: Int = 0,
    var endA: Int = 0,
    var beginB: Int = 0,
    var endB: Int = 0,
    var lines: MutableList<DiffLine> = mutableListOf()
) {

    fun size(): Int {
        var length = 0
        for (line in lines) {
            length += line.content.length
        }
        return length
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as Hunk

        if (beginA != other.beginA) return false
        if (beginB != other.beginB) return false
        if (endA != other.endA) return false
        if (endB != other.endB) return false
        if (lines != other.lines) return false

        return true
    }

    override fun hashCode(): Int {
        var result = beginA
        result = 31 * result + endA
        result = 31 * result + beginB
        result = 31 * result + endB
        result = 31 * result + lines.hashCode()
        return result
    }
}
