package com.github.yonaprojects.yona.domain.enumeration

enum class State(private val value: String) {
    ALL("all"),
    OPEN("open"),
    CLOSED("closed"),
    REJECTED("rejected"),
    CONFLICT("conflict"),
    RESOLVED("resolved"),
    MERGED("merged"),
    DRAFT("draft");

    fun state(): String = value

    companion object {
        fun getValue(value: String): State {
            return values().find { it.value == value } ?: OPEN
        }
    }
}
