package com.github.yonaprojects.yona.domain.role

enum class RoleType(val roleType: Long) {
    MANAGER(1L),
    MEMBER(2L),
    SITEMANAGER(3L),
    ANONYMOUS(4L),
    GUEST(5L),
    ORG_ADMIN(6L),
    ORG_MEMBER(7L);

    fun getLowerCasedName(): String {
        return name.lowercase()
    }
}
