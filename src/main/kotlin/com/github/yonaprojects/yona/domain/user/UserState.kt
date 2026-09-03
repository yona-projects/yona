package com.github.yonaprojects.yona.domain.user

enum class UserState(val state: String) {
    ACTIVE("ACTIVE"),
    LOCKED("LOCKED"),
    DELETED("DELETED"),
    GUEST("GUEST"),
    SITE_ADMIN("SITE_ADMIN");

    companion object {
        fun of(value: String): UserState? {
            return entries.find { it.state.equals(value, ignoreCase = true) }
        }
    }
}
