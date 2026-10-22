package dev.yona.web

/**
 * Accept 헤더에서 응답 형식을 고르는 규칙이다. v1.6 `HttpUtil.getPreferType`, `isJSONPreferred`와 같다.
 *
 * Accept 헤더의 미디어 범위를 선호도(`q` 값) 순서로 훑으면서, 범위가 받아들이는 첫 번째 지원 타입을 돌려준다.
 * 선호도가 같으면 헤더에 적힌 순서를 따른다. 일치하는 타입이 없으면 `text/html`이다.
 */
object HttpPreference {

    private const val HTML = "text/html"
    private const val JSON = "application/json"

    /**
     * @param acceptHeader `Accept` 요청 헤더 값. 없으면 `null`
     * @param types 서버가 지원하는 응답 타입 목록. 앞의 것이 우선한다
     * @return 가장 선호되는 타입
     */
    fun preferredType(acceptHeader: String?, vararg types: String): String {
        val ranges = parseRanges(acceptHeader)
        for (range in ranges) {
            for (type in types) {
                if (accepts(range, type)) return type
            }
        }
        return HTML
    }

    /** 클라이언트가 `application/json`을 `text/html`보다 선호하면 `true`이다. */
    fun isJsonPreferred(acceptHeader: String?): Boolean = preferredType(acceptHeader, HTML, JSON) == JSON

    /** 헤더를 선호도 내림차순(동률이면 원래 순서)의 미디어 범위 목록으로 바꾼다. 헤더가 없으면 모든 타입이다. */
    private fun parseRanges(header: String?): List<String> {
        if (header.isNullOrBlank()) return listOf("*/*")
        return header.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { part ->
                val pieces = part.split(';').map { it.trim() }
                val quality = pieces.drop(1)
                    .firstOrNull { it.startsWith("q=") }
                    ?.substringAfter('=')?.toDoubleOrNull() ?: 1.0
                pieces[0].lowercase() to quality
            }
            .sortedByDescending { it.second } // sortedBy는 안정 정렬이라 동률이면 헤더 순서가 유지된다
            .map { it.first }
    }

    private fun accepts(range: String, type: String): Boolean = when {
        range == "*/*" -> true
        range.endsWith("/*") -> type.startsWith(range.removeSuffix("*"))
        else -> range == type
    }
}
