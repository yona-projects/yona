package com.github.yonaprojects.yona.domain.user

/**
 * yona User.LOGIN_ID_PATTERN 대응.
 * 영문/숫자/한글/하이픈과, `_`/`.` 구분자로 이어지는 다음 토큰(영문/숫자/한글/하이픈)을 허용한다
 * (예: "gil-dong", "gil.dong", "길동_2" 허용, 공백·슬래시 등은 불허). ReservedWordsValidator와
 * 동일하게 컨트롤러에서 명시적으로 호출하는 유틸 object 컨벤션을 따른다.
 */
object LoginIdFormatValidator {
    private val LOGIN_ID_REGEX = Regex("^[a-zA-Z0-9가-힣-]+([_.][a-zA-Z0-9가-힣_.-]+)*$")

    fun isValid(loginId: String): Boolean {
        return LOGIN_ID_REGEX.matches(loginId)
    }
}
