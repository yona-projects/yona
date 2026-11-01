package dev.yona.domain.project

import dev.yona.domain.enumeration.ProjectScope

/**
 * 프로젝트 생성 폼의 입력값이다. v1.6은 Play의 `Form<Project>`로 요청 파라미터를 `Project`에 직접 묶었다.
 * 값은 요청에서 온 그대로(검증 전) 담는다.
 *
 * @property owner 소유자의 로그인 ID 또는 조직 이름
 * @property name 프로젝트 이름
 * @property overview 설명
 * @property projectScope 공개 범위 이름(`PUBLIC`, `PROTECTED`, `PRIVATE`)
 * @property vcs 저장소 종류(`GIT`, `Subversion`)
 * @property menus 켜기로 한 메뉴 이름 모음(`code`, `issue`, `pullRequest`, `review`, `milestone`, `board`, `wiki`)
 */
data class ProjectForm(
    val owner: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val projectScope: String? = null,
    val vcs: String? = null,
    val menus: Set<String> = emptySet()
) {
    /** 문자열 [projectScope]를 열거형으로 바꾼다. 비어 있거나 알 수 없는 값이면 `null`이다. */
    fun scope(): ProjectScope? = projectScope?.let { value -> ProjectScope.entries.firstOrNull { it.name == value } }

    companion object {
        /** 메뉴 체크박스 이름이다 (`ProjectMenuSetting`의 필드 이름과 같다). */
        val MENU_NAMES = listOf("code", "issue", "pullRequest", "review", "milestone", "board", "wiki")
    }
}

/**
 * 폼 검증 오류 모음이다. 필드 이름마다 오류 메시지 **키**를 순서대로 쌓는다.
 * v1.6 Play `Form`의 `reject`, `error`, `errorsAsJson`에 해당한다.
 */
class FormErrors {

    // v1.6 Play `Form.errors()`는 HashMap이라 오류가 생긴 순서가 아니라 필드 이름의 해시 순서로 나온다(화면의 htError JSON 순서)
    private val errors = HashMap<String, MutableList<String>>()

    /** [field]에 오류 [messageKey]를 더한다. */
    fun reject(field: String, messageKey: String) {
        errors.getOrPut(field) { mutableListOf() } += messageKey
    }

    /** [field]의 첫 번째 오류 키이다. 없으면 `null`이다 (v1.6 `Form.error(field)`). */
    fun first(field: String): String? = errors[field]?.firstOrNull()

    /** [field]의 오류를 모두 지운다. */
    fun clear(field: String) {
        errors.remove(field)
    }

    fun hasErrors(): Boolean = errors.isNotEmpty()

    fun fields(): Map<String, List<String>> = errors
}
