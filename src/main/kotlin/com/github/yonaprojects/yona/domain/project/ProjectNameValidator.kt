package com.github.yonaprojects.yona.domain.project

/**
 * yona models/Project.java:62 `@ExConstraints.Restricted({".", "..", ".git"})` 대응 (P1-145).
 * legacy `ExConstraints.RestrictedValidator.isValid()`는 `ignoreCase=false`(기본값, Project.name에
 * 적용된 실제 값)일 때 오히려 `equalsIgnoreCase`로 비교하는 버그가 있어, 대소문자 무관하게
 * ".", "..", ".git"과 정확히 일치하면 거부된다 — 그 동작을 그대로 재현한다.
 */
object ProjectNameValidator {
    private val RESTRICTED_NAMES: Set<String> = setOf(".", "..", ".git")

    fun isRestricted(name: String): Boolean {
        return RESTRICTED_NAMES.any { it.equals(name, ignoreCase = true) }
    }
}
