package dev.yona.util

/**
 * v1.6의 문자열 판정과 같은 결과를 내는 도우미이다.
 *
 * Kotlin의 `isBlank()`와 `trim()`은 줄 바꿈 없는 공백(`U+00A0`, `U+2007`, `U+202F`)까지 공백으로 본다. v1.6이 쓰던
 * commons-lang `StringUtils.isBlank`는 `Character.isWhitespace`만 공백으로 보므로 이 세 글자만 든 값을 비어 있지 않다고 판단하고,
 * Java `String.trim()`은 `U+0020` 이하의 글자(제어 문자 포함)만 지운다. 입력 검증에서 두 결과가 갈리지 않게 이 도우미를 쓴다.
 */
object LegacyText {

    /** `StringUtils.isBlank(s)`: `null`이거나 모든 글자가 `Character.isWhitespace`이다. */
    fun isBlank(s: CharSequence?): Boolean = s == null || s.all { Character.isWhitespace(it) }

    /** `StringUtils.isNotBlank(s)`. */
    fun isNotBlank(s: CharSequence?): Boolean = !isBlank(s)

    /** Java `String.trim()`: 앞뒤의 `U+0020` 이하 글자를 지운다. */
    fun trim(s: String): String = s.trim { it <= ' ' }
}
