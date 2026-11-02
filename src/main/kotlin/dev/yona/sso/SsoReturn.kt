package dev.yona.sso

import jakarta.servlet.http.HttpServletRequest

/**
 * SSO 로그인이 끝난 뒤 돌아갈 곳과 시작 방식을 세션에 두는 규칙이다(X-16, `decisions.md` D-80). 돌아갈 곳은 **같은 사이트의 경로만** 받는다(열린 리디렉션 방지).
 */
object SsoReturn {

    /** 세션 속성: 로그인 뒤 돌아갈 경로. */
    const val RETURN_TO = "dev.yona.sso.returnTo"

    /** 세션 속성: IdP(회사 포털)가 시작한 로그인이면 `true`. */
    const val INITIATED = "dev.yona.sso.initiated"

    /** 세션 속성: IdP에 알릴 `login_hint`. */
    const val LOGIN_HINT = "dev.yona.sso.loginHint"

    /** [value]가 사이트 안의 경로이면 그대로, 아니면 `null`이다. `/`로 시작하고 `//`, `\\`, 제어 문자가 없어야 한다. */
    fun safePath(value: String?): String? {
        if (value.isNullOrEmpty() || !value.startsWith("/") || value.startsWith("//") || value.contains('\\') || value.any { it.isISOControl() }) return null
        return value
    }

    /**
     * [value]를 돌아갈 경로로 바꾼다. 사이트 안의 경로이거나 [origin]이 같은 절대 주소(경로만 취한다)이면 받고, 그 밖은 `null`이다.
     */
    fun resolve(value: String?, origin: String): String? {
        safePath(value)?.let { return it }
        if (value.isNullOrEmpty()) return null
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
        if (uri.scheme == null || "${uri.scheme}://${uri.authority}" != origin) return null
        return safePath((uri.rawPath ?: "/") + (uri.rawQuery?.let { "?$it" } ?: ""))
    }

    /** 세션에서 돌아갈 곳을 꺼내고 지운다. 없으면 `/`이다. */
    fun pop(request: HttpServletRequest): String {
        // IdP가 시작한 SAML 로그인은 세션이 없어 요청 속성에 둔다
        val fromRequest = request.getAttribute(RETURN_TO) as? String
        val session = request.getSession(false)
        val value = fromRequest ?: session?.getAttribute(RETURN_TO) as? String
        session?.removeAttribute(RETURN_TO)
        return safePath(value) ?: "/"
    }
}
