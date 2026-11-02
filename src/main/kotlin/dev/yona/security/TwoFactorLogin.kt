package dev.yona.security

import dev.yona.domain.audit.AuditBoundary
import dev.yona.domain.twofactor.TwoFactorResult
import dev.yona.domain.twofactor.WebAuthnContext
import dev.yona.domain.twofactor.TwoFactorService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.io.Serializable

/**
 * 비밀번호는 맞았지만 2단계를 아직 마치지 않은 로그인이다(추가 개발 항목 X-2, `decisions.md` D-69). 세션에만 두며 인증 정보가 아니다.
 *
 * @property source 로그인을 시작한 입구(`password`, `social`, `email_link`)
 * @property rememberMe 2단계를 마친 뒤 로그인 유지 쿠키를 만들지이다
 * @property redirectUrl 2단계를 마친 뒤 이동할 주소(이미 인코딩한 값). 없으면 `/`이다
 * @property flash 마친 뒤 보일 안내(메시지 키)이다
 * @property createdMillis 비밀번호를 확인한 시각이다
 */
class PendingLogin(
    val userId: Long, val source: String, val rememberMe: Boolean, val redirectUrl: String?, val flash: Map<String, String>, val createdMillis: Long
) : Serializable

/** 로그인을 끝낸다(언어 갱신, 로그인 유지 쿠키, 세션 시작). 비밀번호 로그인과 2단계 뒤의 로그인이 같은 일을 하도록 한곳에 둔다. v1.6 `UserApp`의 로그인 성공 처리에 해당한다. */
@Component
class LoginCompletion(private val users: UserRepository, private val loginSession: LoginSessionService) {

    /**
     * [user]를 로그인시킨다. [rememberMe]이면 로그인 유지 쿠키를 만든다. [twoFactorVerified]는 2단계를 마쳤음을 뜻하며, 2단계 인증을 켠 사용자는 이 값이 참이어야 한다([LoginSessionService.start]).
     */
    fun complete(request: HttpServletRequest, response: HttpServletResponse, user: User, rememberMe: Boolean, twoFactorVerified: Boolean) {
        if (rememberMe) {
            response.addCookie(Cookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE, "${user.loginId}:${user.password}").apply {
                maxAge = YonaAuthenticationFilter.REMEMBER_ME_MAX_AGE
                path = "/"
            })
        }
        user.lang = LocaleContextHolder.getLocale().toLanguageTag()
        users.save(user)
        loginSession.start(request, response, user, twoFactorVerified)
    }
}

/** 2단계 코드를 제출한 결과이다. */
sealed interface TwoFactorSubmission {
    /** 맞았고 로그인했다. [redirectUrl]은 이동할 주소(없으면 `/`)이고 [flash]는 보일 안내이다. */
    data class Success(val redirectUrl: String?, val flash: Map<String, String>) : TwoFactorSubmission

    /** 진행 중인 로그인이 없거나 만료되었거나 사용자가 더는 로그인할 수 없다. */
    data object Expired : TwoFactorSubmission

    /** 코드가 맞지 않다. */
    data object Invalid : TwoFactorSubmission

    /** 실패가 쌓여 잠겼다(방금 잠겼거나 이미 잠겨 있다). */
    data object Locked : TwoFactorSubmission
}

/**
 * 로그인 입구가 공통으로 쓰는 2단계 관문이다(추가 개발 항목 X-2, `decisions.md` D-69). 입구(웹 폼, AJAX 팝업, 소셜 로그인, 메일 링크)는 사용자를 확인한 뒤 [required]로 2단계가 필요한지 묻고, 필요하면
 * [begin]으로 진행 중인 로그인을 세션에 두고 2단계 화면으로 보낸다. 코드를 제출하면 [submit]이 확인하고 맞으면 로그인을 끝낸다.
 */
@Component
class TwoFactorLogin(
    private val twoFactor: TwoFactorService,
    private val gate: TwoFactorGate,
    private val devices: dev.yona.domain.twofactor.KnownDeviceService,
    private val webAuthn: dev.yona.domain.twofactor.WebAuthnService,
    private val users: UserRepository,
    private val completion: LoginCompletion,
    private val attempts: TwoFactorAttempts,
    private val audit: AuditBoundary
) {

    /** [user]가 이 요청에서 2단계를 거쳐야 하면 `true`이다(켜지 않았거나 알려진 기기이면 `false`). */
    fun required(user: User, request: HttpServletRequest): Boolean = !gate.passes(user, request)

    /** 진행 중인 로그인을 세션에 둔다. 세션 식별자를 바꾸어 세션 고정을 막는다. */
    fun begin(request: HttpServletRequest, user: User, source: String, rememberMe: Boolean, redirectUrl: String?, flash: Map<String, String> = emptyMap()) {
        if (request.getSession(false) != null) request.changeSessionId()
        request.getSession(true).setAttribute(PENDING, PendingLogin(requireNotNull(user.id), source, rememberMe, redirectUrl, flash, attempts.now()))
    }

    /** 진행 중인 로그인이다. 없거나 [TwoFactorAttempts.PENDING_VALID]가 지났으면 `null`이다(지난 것은 지운다). */
    fun pending(request: HttpServletRequest): PendingLogin? {
        val session = request.getSession(false) ?: return null
        val pending = session.getAttribute(PENDING) as? PendingLogin ?: return null
        if (attempts.now() - pending.createdMillis > TwoFactorAttempts.PENDING_VALID.toMillis()) { session.removeAttribute(PENDING); return null }
        return pending
    }

    /** [code]를 확인한다. 맞으면 로그인을 끝내고 진행 중인 로그인을 지운다. */
    fun submit(request: HttpServletRequest, response: HttpServletResponse, code: String, rememberDevice: Boolean = false): TwoFactorSubmission =
        attempt(request, response, rememberDevice) { user -> twoFactor.verify(user, code) != TwoFactorResult.INVALID }

    /**
     * 진행 중인 로그인의 사용자에게 보안 키 인증 요청을 만들어 돌려주고 세션에 둔다(WebAuthn). 진행 중인 로그인이 없거나 보안 키가 없으면 `null`이다.
     */
    fun startSecurityKey(request: HttpServletRequest, context: WebAuthnContext): String? {
        val pending = pending(request) ?: return null
        val user = users.findById(pending.userId).orElse(null) ?: return null
        val options = webAuthn.startAssertionOrNull(user, context) ?: return null
        request.getSession(true).setAttribute(ASSERTION, options.server)
        return options.browser
    }

    /** 보안 키 인증 응답 [responseJson]을 확인한다. 세션에 둔 요청은 한 번만 쓴다. 나머지는 [submit]과 같다. */
    fun submitSecurityKey(request: HttpServletRequest, response: HttpServletResponse, responseJson: String, context: WebAuthnContext, rememberDevice: Boolean = false): TwoFactorSubmission {
        val session = request.getSession(false)
        val assertion = session?.getAttribute(ASSERTION) as? String
        session?.removeAttribute(ASSERTION)
        return attempt(request, response, rememberDevice) { user -> assertion != null && webAuthn.finishAssertion(user, assertion, responseJson, context) }
    }

    /** 진행 중인 로그인의 사용자가 [check]를 통과하면 로그인을 끝낸다. 실패는 세고(5번이면 잠근다) 기록한다. */
    private fun attempt(request: HttpServletRequest, response: HttpServletResponse, rememberDevice: Boolean, check: (User) -> Boolean): TwoFactorSubmission {
        val pending = pending(request) ?: return TwoFactorSubmission.Expired
        val user = users.findById(pending.userId).orElse(null)
        if (user == null || user.state.isLockedOut()) { forget(request); return TwoFactorSubmission.Expired }
        val userId = pending.userId
        if (attempts.isLocked(userId)) {
            forget(request)
            audit.loginFailure(user.loginId, "two_factor_locked", denied = true)
            return TwoFactorSubmission.Locked
        }
        if (!check(user)) {
            val locked = attempts.recordFailure(userId)
            audit.loginFailure(user.loginId, if (locked) "two_factor_locked" else "two_factor_invalid", denied = locked)
            if (locked) { forget(request); return TwoFactorSubmission.Locked }
            return TwoFactorSubmission.Invalid
        }
        attempts.clear(userId)
        forget(request)
        if (rememberDevice) {
            val token = devices.issue(user, deviceName(request), request.remoteAddr.orEmpty())
            response.addCookie(Cookie(dev.yona.domain.twofactor.KnownDeviceService.COOKIE, token).apply {
                maxAge = (dev.yona.domain.twofactor.KnownDeviceService.VALID.seconds).toInt(); path = "/"; isHttpOnly = true; secure = request.isSecure
            })
        }
        completion.complete(request, response, user, pending.rememberMe || pending.source == "social", twoFactorVerified = true)
        audit.loginSuccess(user, "${pending.source}+two_factor")
        return TwoFactorSubmission.Success(pending.redirectUrl, pending.flash)
    }

    /** 기기를 알아보는 이름이다: 브라우저 신원 문자열에서 브라우저와 운영체제를 추려 쓴다. */
    private fun deviceName(request: HttpServletRequest): String {
        val agent = request.getHeader("User-Agent").orEmpty()
        val browser = listOf("Edg" to "Edge", "OPR" to "Opera", "Firefox" to "Firefox", "Chrome" to "Chrome", "Safari" to "Safari").firstOrNull { agent.contains(it.first) }?.second ?: "Browser"
        val system = listOf("Windows", "Android", "iPhone" to "iOS", "iPad" to "iOS", "Mac OS" to "macOS", "Linux").map { if (it is Pair<*, *>) it else (it to it) }.firstOrNull { agent.contains(it.first as String) }?.second ?: "Unknown"
        return "$browser on $system"
    }

    /** 진행 중인 로그인을 지운다. */
    fun forget(request: HttpServletRequest) { request.getSession(false)?.run { removeAttribute(PENDING); removeAttribute(ASSERTION) } }

    companion object {
        /** 2단계 화면의 주소이다. */
        const val CHALLENGE_PATH = "/users/login/2fa"
        private const val PENDING = "dev.yona.security.PendingLogin"
        private const val ASSERTION = "dev.yona.security.PendingAssertion"
    }
}
