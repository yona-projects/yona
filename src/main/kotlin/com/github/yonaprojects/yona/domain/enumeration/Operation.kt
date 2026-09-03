package com.github.yonaprojects.yona.domain.enumeration

// yona models/enumeration/Operation.java 대응 (P1-85 1b)
enum class Operation(private val value: String) {
    READ("read"),
    UPDATE("edit"),
    DELETE("delete"),
    ACCEPT("accept"),
    REOPEN("reopen"),
    CLOSE("close"),
    WATCH("watch"),
    LEAVE("leave"),

    // 이슈를 자기 자신에게 담당자로 배정하는 행위를 가리키는 연산
    ASSIGN_ISSUE("assign_issue");

    fun operation(): String = value

    companion object {
        fun getValue(value: String): Operation {
            return values().find { it.operation() == value } ?: READ
        }
    }
}
