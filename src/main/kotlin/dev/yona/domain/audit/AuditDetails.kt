package dev.yona.domain.audit

import tools.jackson.databind.json.JsonMapper

/**
 * 감사 기록의 상세를 JSON 글로 만든다. 비밀 값은 값을 지우고 `[redacted]`로 바꾼다(키 이름으로 판단한다): 비밀번호, 비밀값(secret), 자격 증명, `Authorization`,
 * 서명, API 키, `token` 자체(토큰 번호나 접두사는 비밀이 아니므로 그대로 둔다). 중첩된 맵과 목록도 훑는다. 너무 길면 앞부분만 남기고 잘렸음을 표시한다.
 */
object AuditDetails {

    /** 상세 JSON 글의 대략적인 최대 길이이다. */
    const val MAX_LENGTH = 4000

    private const val REDACTED = "[redacted]"
    private const val PREVIEW_LENGTH = 1900
    private val mapper = JsonMapper.builder().build()
    private val SENSITIVE_PARTS = listOf("password", "passwd", "secret", "credential", "authorization", "signature", "apikey", "privatekey", "accesstoken", "refreshtoken", "bearer")

    /** 비밀 값을 가린 상세를 JSON 글로 만든다. 비어 있으면 `null`이다. */
    fun toJson(details: Map<String, Any?>): String? {
        if (details.isEmpty()) return null
        val json = mapper.writeValueAsString(sanitize(details))
        if (json.length <= MAX_LENGTH) return json
        return mapper.writeValueAsString(linkedMapOf("_truncated" to true, "preview" to json.take(PREVIEW_LENGTH)))
    }

    private fun sanitize(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.associateTo(linkedMapOf<String, Any?>()) { (k, v) -> k.toString() to if (sensitive(k.toString())) REDACTED else sanitize(v) }
        is Iterable<*> -> value.map { sanitize(it) }
        is Array<*> -> value.map { sanitize(it) }
        else -> value
    }

    private fun sensitive(key: String): Boolean {
        val plain = key.lowercase().filter { it.isLetterOrDigit() }
        return plain == "token" || plain.endsWith("token") || SENSITIVE_PARTS.any { plain.contains(it) }
    }
}
