package dev.yona.web.social

import dev.yona.domain.social.SocialIdentity
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.security.SecureRandom
import java.util.Base64

/**
 * 소셜 로그인이 세션에 남기는 값이다. v1.6 play-authenticate가 세션에 쓰던 값에 해당한다. 사이트의 로그인 세션과는 별개이며,
 * 소셜 로그인으로 들어온 사용자인지 알아보고(로그아웃 주소 선택, `/restricted`) 인증 후 돌아갈 주소를 보관하는 데 쓴다.
 *
 * 세션 키 이름은 play-authenticate 0.6.9와 같게 `pa.p.id`(제공자), `pa.u.id`(제공자 쪽 사용자 ID), `pa.u.exp`(만료 시각),
 * `pa.url.orig`(인증 후 돌아갈 주소)로 한다. 라이브러리 jar를 디컴파일해 확인한 값이다.
 */
@Component
class SocialSession {

    /** 소셜 로그인 세션이다. */
    data class Current(val provider: String, val providerUserId: String, val expires: Long)

    private val random = SecureRandom()

    /** 현재 소셜 세션이다. 없거나 만료했으면 `null`이며 만료한 세션은 지운다. */
    fun current(request: HttpServletRequest): Current? = current(request.getSession(false))

    private fun current(session: HttpSession?): Current? {
        session ?: return null
        val provider = session.getAttribute(PROVIDER) as? String ?: return null
        val id = session.getAttribute(USER_ID) as? String ?: return null
        val expires = session.getAttribute(EXPIRES) as? Long ?: -1
        if (expires != -1L && expires < System.currentTimeMillis()) {
            clear(session)
            return null
        }
        return Current(provider, id, expires)
    }

    /** [identity]로 소셜 세션을 시작한다. */
    fun start(request: HttpServletRequest, identity: SocialIdentity) {
        val session = request.getSession(true)
        session.setAttribute(PROVIDER, identity.provider)
        session.setAttribute(USER_ID, identity.providerUserId)
        session.setAttribute(EXPIRES, identity.expires)
    }

    /** 소셜 세션과 인증 후 돌아갈 주소를 지운다 (v1.6 `PlayAuthenticate.logout`). */
    fun end(request: HttpServletRequest) {
        val session = request.getSession(false) ?: return
        clear(session)
        session.removeAttribute(ORIGINAL_URL)
    }

    /** 만료하지 않은 소셜 세션이 있으면 `true`이다. v1.6은 만료를 보지 않고 키만 있으면 참이라 컨트롤러가 만료로 보는 것과 어긋났다(R-59-13). */
    fun hasKeys(request: HttpServletRequest): Boolean = current(request) != null

    private fun clear(session: HttpSession?) {
        session ?: return
        listOf(PROVIDER, USER_ID, EXPIRES).forEach { session.removeAttribute(it) }
    }

    /** 인증 후 돌아갈 주소를 꺼내며 지운다. */
    fun popOriginalUrl(request: HttpServletRequest): String? {
        val session = request.getSession(false) ?: return null
        return (session.getAttribute(ORIGINAL_URL) as? String).also { session.removeAttribute(ORIGINAL_URL) }
    }

    /** 인증을 마친 뒤 곧바로 로그아웃으로 보내도록 돌아갈 주소를 로그아웃 주소로 바꾼다 (v1.6 `forceOAuthLogout`). */
    fun forceLogout(request: HttpServletRequest) = request.getSession(true).setAttribute(ORIGINAL_URL, "/logout")

    /** 제공자로 보내기 전에 만든 `state` 값을 세션에 두고 돌려준다. */
    fun newState(request: HttpServletRequest, provider: String): String {
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(24).also { random.nextBytes(it) })
        request.getSession(true).setAttribute(STATE + provider, state)
        return state
    }

    /** 돌아온 `state`의 확인 결과이다. */
    enum class StateCheck {
        /** 보낸 값이 없다(시간이 지나 사라졌다). v1.6은 이 경우 제공자로 다시 보낸다. */
        MISSING,

        /** 보낸 값과 같다. */
        MATCH,

        /** 보낸 값과 다르다(위조). */
        MISMATCH
    }

    /** 돌아온 `state`가 보낸 값과 같은지 확인한다. 한 번 확인하면 값을 지워 다시 쓸 수 없다(v1.6은 가입할 때만 지운다, R-59). */
    fun checkState(request: HttpServletRequest, provider: String, given: String?): StateCheck {
        val session = request.getSession(false) ?: return StateCheck.MISSING
        val expected = session.getAttribute(STATE + provider) as? String ?: return StateCheck.MISSING
        session.removeAttribute(STATE + provider)
        return if (expected == given) StateCheck.MATCH else StateCheck.MISMATCH
    }

    /**
     * 익명 사용자가 전체 화면을 볼 때 그 주소를 인증 후 돌아올 곳으로 기억한다. 템플릿의 사용자 메뉴와 사이드바가 부른다
     * (v1.6 `usermenu.scala.html`, `sidebar.scala.html`, `PlayAuthenticate.storeOriginalUrl`). `redirectUrl` 질의가 있으면 그 값을 쓴다.
     * 없으면 GET 요청이고 경로가 로그인 주소(홈 `/`)가 아닐 때 현재 주소를 기억하고, 그 밖에는 기억해 둔 주소를 지운다.
     * 소셜 세션 값이 이미 있으면 기억하지 않는다. 템플릿에서 값을 쓰지 않으므로 빈 문자열을 돌려준다.
     */
    fun rememberOriginalUrl(): String {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: return ""
        if (hasKeys(request)) return ""
        val redirect = request.getParameter("redirectUrl")
        val session = request.getSession(true)
        if (!redirect.isNullOrEmpty()) {
            session.setAttribute(ORIGINAL_URL, redirect)
        } else if (request.method == "GET" && request.requestURI.removePrefix(request.contextPath) != "/") {
            session.setAttribute(ORIGINAL_URL, listOfNotNull(request.requestURI, request.queryString).joinToString("?"))
        } else {
            session.removeAttribute(ORIGINAL_URL)
        }
        return ""
    }

    companion object {
        const val PROVIDER = "pa.p.id"
        const val USER_ID = "pa.u.id"
        const val EXPIRES = "pa.u.exp"
        const val ORIGINAL_URL = "pa.url.orig"
        private const val STATE = "pa.state."
    }
}
