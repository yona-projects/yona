package dev.yona.util

/**
 * SQL `LIKE` 패턴에 넣을 사용자 입력을 이스케이프한다. 쿼리는 `escape '\\'`를 함께 선언해야 한다.
 *
 * `%`, `_`, `\\`는 `LIKE`에서 특별한 뜻이라 이스케이프하지 않으면 사용자가 입력한 `_`가 "아무 한 글자"로 읽혀 `_`만 검색해도 모든 값이 맞는다.
 * v1.6(Ebean `icontains`)은 이스케이프하지 않았다(R-96). 이 함수는 입력을 글자 그대로 찾도록 세 글자를 `\\`로 앞서 막는다.
 */
object LikeEscape {

    /** [value]의 `\\`, `%`, `_`를 이스케이프한 문자열이다. */
    @JvmStatic
    fun escape(value: String?): String = value.orEmpty().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** [value]를 소문자로 만들어 앞뒤에 `%`를 붙인 "포함" 패턴이다. */
    @JvmStatic
    fun contains(value: String?): String = "%" + escape(value).lowercase() + "%"
}
