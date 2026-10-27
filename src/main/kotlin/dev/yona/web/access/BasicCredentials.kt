package dev.yona.web.access

import jakarta.servlet.http.HttpServletRequest
import java.util.Base64

/**
 * HTTP Basic 인증의 자격이다. Git과 Subversion의 HTTP 접근이 함께 쓴다. v1.6 `utils.BasicAuthAction.parseCredentials`
 * (`app/utils/BasicAuthAction.java:52-86`)에 해당한다.
 *
 * @property loginId 사용자 로그인 ID
 * @property password 비밀번호(`:`을 포함할 수 있다)
 */
class BasicCredentials(val loginId: String, val password: String) {

    companion object {
        /** 사용자와 비밀번호를 가르는 `:`가 없거나 Base64가 아닌 자격을 나타낸다. 요청은 `400`이다. */
        val MALFORMED = BasicCredentials("", "")

        /**
         * `Authorization: Basic …`의 자격이다. Basic 헤더가 아니면 `null`(다른 방식으로 식별), 형식이 틀리면 [MALFORMED]이다.
         * ISO-8859-1로 읽는다(v1.6은 RFC 2047이 아니라 ISO-8859-1만 쓴다).
         */
        fun parse(request: HttpServletRequest): BasicCredentials? {
            val header = request.getHeader("Authorization") ?: return null
            if (!header.startsWith("Basic ")) return null
            val decoded = String(lenientDecode(header.substring(6)), Charsets.ISO_8859_1)
            val colon = decoded.indexOf(':')
            if (colon < 0) return MALFORMED
            return BasicCredentials(decoded.substring(0, colon), decoded.substring(colon + 1))
        }

        /**
         * v1.6의 commons-codec `decodeBase64`처럼 관대하게 읽는다. URL 안전 문자(`-`, `_`)는 `+`, `/`로 보고, 알파벳 밖의 글자와 패딩(`=`)은
         * 버린 뒤 패딩 없이 읽는다. 마지막에 한 글자만 남으면(6비트) 그 글자는 버린다.
         */
        private fun lenientDecode(value: String): ByteArray {
            val cleaned = StringBuilder(value.length)
            for (c in value) {
                when {
                    c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' -> cleaned.append(c)
                    c == '-' -> cleaned.append('+')
                    c == '_' -> cleaned.append('/')
                }
            }
            if (cleaned.length % 4 == 1) cleaned.setLength(cleaned.length - 1)
            return Base64.getDecoder().decode(cleaned.toString())
        }
    }
}
