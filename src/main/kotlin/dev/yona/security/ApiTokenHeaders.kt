package dev.yona.security

import jakarta.servlet.http.HttpServletRequest

/**
 * 요청 헤더에서 개인용 API 토큰을 읽는다(추가 개발 항목 X-5, `decisions.md` D-36). 형식을 엄격하게 본다.
 * `Authorization: token <값>`, `Authorization: Bearer <값>`(방식 이름은 대소문자 무시), `Yona-Token: <값>`(v1.6 호환)이며 `Authorization`이 먼저이다.
 * 값에는 공백이 없어야 한다. v1.6과 이전 v2는 `Authorization`에 `token `이 들어 있기만 해도 토큰으로 읽는 느슨한 분석이었다.
 */
object ApiTokenHeaders {

    const val YONA_TOKEN_HEADER = "Yona-Token"

    private val AUTHORIZATION = Regex("^(?i:token|bearer)\\s+(\\S+)$")

    /** 토큰 값을 돌려준다. 형식이 맞지 않거나 헤더가 없으면 `null`이다. */
    fun extract(request: HttpServletRequest): String? {
        request.getHeader("Authorization")?.trim()?.let { header ->
            AUTHORIZATION.matchEntire(header)?.let { return it.groupValues[1] }
        }
        return request.getHeader(YONA_TOKEN_HEADER)?.trim()?.takeIf { it.isNotEmpty() && it.none { c -> c.isWhitespace() } }
    }
}
