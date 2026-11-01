package dev.yona.domain.project

/**
 * 프로젝트 이름의 검증 규칙이다. v1.6 `Project.name`의 `@Required`, `@Pattern`, `@Restricted`이다.
 *
 * 이름은 비어 있으면 안 되고, 영문, 숫자, `-`, `_`, `.`, 한글만 쓸 수 있으며, `.`, `..`, `.git`은 쓸 수 없다.
 * 이에 더해 `.wiki`로 끝나는 이름도 쓸 수 없다. 프로젝트 `demo`의 위키 저장소가 `demo.wiki.git`이라 이름이 `demo.wiki`인
 * 프로젝트의 코드 저장소와 같은 위치가 되기 때문이다(X-17, D-60. Gitea와 Forgejo도 `*.wiki`를 예약한다). 이미 있는 이름은 바꾸지 않는다.
 * 예약어는 대소문자를 가리지 않고 거부한다. v1.6 구현이 `ignoreCase` 분기를 거꾸로 써서 기본 설정에서 오히려
 * 대소문자를 무시하기 때문이다([refactoring-backlog.md] R-13).
 */
object ProjectNameRules {

    /** 프로젝트 이름에 쓸 수 있는 문자이다 (v1.6 `Project.name`의 `@Pattern`). */
    private val NAME_PATTERN = Regex("^[a-zA-Z0-9-_.가-힣]+$")

    /** 프로젝트 이름으로 쓸 수 없는 예약어이다 (v1.6 `@Restricted`). */
    private val RESERVED_NAMES = listOf(".", "..", ".git")

    /** 위키 저장소 이름과 겹치므로 이름의 끝으로 쓸 수 없는 접미사이다. */
    private const val WIKI_SUFFIX = ".wiki"

    /** 입력 묶기 단계의 오류 키이다. */
    const val REQUIRED = "error.required"
    const val PATTERN = "error.pattern"
    const val RESTRICTED = "error.restricted"

    /** 이름이 규칙을 어기면 그 오류 키들을 순서대로 돌려준다. 규칙을 지키면 빈 목록이다. */
    fun violations(name: String?): List<String> {
        if (name.isNullOrEmpty()) return listOf(REQUIRED)
        val result = mutableListOf<String>()
        if (!NAME_PATTERN.matches(name)) result += PATTERN
        if (RESERVED_NAMES.any { it.equals(name, ignoreCase = true) } || name.endsWith(WIKI_SUFFIX, ignoreCase = true)) result += RESTRICTED
        return result
    }

    /** 이름 규칙 위반 키를 사용자에게 보여 줄 메시지 키로 바꾼다. 해당하지 않으면 그대로 돌려준다. */
    fun toUserMessageKey(violation: String): String = when (violation) {
        PATTERN -> "project.name.alert"
        RESTRICTED -> "project.name.reserved.alert"
        else -> violation
    }
}
