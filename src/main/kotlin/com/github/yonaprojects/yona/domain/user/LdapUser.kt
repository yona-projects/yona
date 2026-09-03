package com.github.yonaprojects.yona.domain.user

/**
 * yona의 models/support/LdapUser.java 대응.
 */
data class LdapUser(
    val displayName: String,
    val email: String,
    val loginId: String,
    val department: String? = null,
    val englishName: String? = null,
    val isGuestUser: Boolean = false
) {
    val fullDisplayName: String
        get() = if (!department.isNullOrBlank()) "$displayName [$department]" else displayName
}
