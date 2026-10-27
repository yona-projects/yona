package dev.yona.util

/** Java `String` API와 결과가 다른 Kotlin 함수를 v1.6과 같게 쓰기 위한 도우미이다. */
object JavaStrings {

    /**
     * Java `String.split(String)`과 같다. 구분자가 없으면 원문 한 조각(빈 문자열이어도 `[""]`)이고, 있으면 끝의 빈 조각을 모두 버린다
     * (그래서 `"="`는 빈 목록이 된다). Kotlin `split`은 끝의 빈 조각을 남긴다.
     */
    fun split(text: String, delimiter: String): List<String> =
        if (!text.contains(delimiter)) listOf(text) else text.split(delimiter).dropLastWhile { it.isEmpty() }
}
