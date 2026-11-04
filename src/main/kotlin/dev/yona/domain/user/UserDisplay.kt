package dev.yona.domain.user

import dev.yona.util.LegacyText

/**
 * 사용자 이름을 화면에 보여 줄 형태로 만든다. v1.6 `User.getDisplayName(User forCurrentUser)`,
 * `User.extractDepartmentPart`이다.
 *
 * 영문 이름이 있고 사용자의 언어가 설정되어 있으며 **보는 사람의 언어가 영어**이면 `영문이름 부서` 형태로,
 * 아니면 이름 그대로 보여 준다. 부서는 이름에서 `(` 또는 `[`가 처음 나오는 곳부터 끝까지이다.
 */
object UserDisplay {

    /**
     * @param target 이름을 보여 줄 사용자
     * @param viewerLang 보는 사람의 언어 코드(예: `en-US`, `ko-KR`). 없으면 영어가 아닌 것으로 본다
     */
    fun of(target: User, viewerLang: String?): String {
        val name = target.name
        val english = target.englishName
        return if ((english != null && LegacyText.isNotBlank(english)) && target.lang != null && viewerLang?.startsWith("en") == true) {
            "$english ${departmentPart(name.orEmpty())}"
        } else {
            name.orEmpty()
        }
    }

    /**
     * 이름에서 부서 부분을 뽑는다. `[`와 `(` 중 먼저 나오는 구분자부터 끝까지이다.
     * v1.6은 `[`를 찾은 뒤 `(`를 다시 찾아 둘 다 있으면 `(` 부분만 남기고, `(`가 앞서도 `[` 부분만 남겨 나머지가 버려졌다(C140).
     */
    fun departmentPart(name: String): String {
        val first = listOf("[", "(").map { name.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return name
        return name.substring(first)
    }

    /**
     * 이름에서 부서를 뺀 순수한 이름이다 (v1.6 `User.getPureNameOnly`). 영문 이름이 있고 사용자의 언어가 설정되어 있으며 보는 사람의
     * 언어가 영어이면 영문 이름이다. 보는 사람의 언어가 없으면 `ko-KR`로 본다.
     */
    fun pureName(target: User, viewerLang: String?): String {
        val language = viewerLang ?: "ko-KR"
        val english = target.englishName
        if ((english != null && LegacyText.isNotBlank(english)) && target.lang != null && language.startsWith("en")) return english
        val name = target.name.orEmpty()
        var pure = name
        for (splitter in listOf("[", "(")) {
            if (pure.contains(splitter)) pure = name.substring(0, name.indexOf(splitter)).trim()
        }
        return pure
    }
}
