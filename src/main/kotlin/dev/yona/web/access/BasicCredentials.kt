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
            val decoded = try { String(Base64.getDecoder().decode(header.substring(6).trim()), Charsets.ISO_8859_1) } catch (e: IllegalArgumentException) { return MALFORMED }
            val colon = decoded.indexOf(':')
            if (colon < 0) return MALFORMED
            return BasicCredentials(decoded.substring(0, colon), decoded.substring(colon + 1))
        }
    }
}
