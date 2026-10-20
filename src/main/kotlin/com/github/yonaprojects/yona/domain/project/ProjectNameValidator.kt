package com.github.yonaprojects.yona.domain.project

/**
 * yona models/Project.java의 `@ExConstraints.Restricted({".", "..", ".git"})` 대응.
 * legacy `ExConstraints.RestrictedValidator.isValid()`는 `ignoreCase=false`(기본값, Project.name에
 * 적용된 실제 값)일 때 오히려 `equalsIgnoreCase`로 비교하는 버그가 있어, 대소문자 무관하게
 * ".", "..", ".git"과 정확히 일치하면 거부된다 — 그 동작을 그대로 재현한다.
 */
object ProjectNameValidator {
    private val RESTRICTED_NAMES: Set<String> = setOf(".", "..", ".git")

    // v1.6 Project.java:55-58 `@Pattern("^[a-zA-Z0-9-_\\.가-힣]+$")` 대응. 저장소 경로($owner/$name.git)로 조립되는 값이라
    // 구분자(/, \\)나 공백 등이 들어오면 저장소 루트 밖을 가리킬 수 있으므로 서버에서 형식을 강제한다.
    private val NAME_FORMAT = Regex("^[a-zA-Z0-9\\-_.가-힣]+$")

    fun isValidFormat(name: String): Boolean = NAME_FORMAT.matches(name)

    fun isRestricted(name: String): Boolean {
        return RESTRICTED_NAMES.any { it.equals(name, ignoreCase = true) }
    }
}
