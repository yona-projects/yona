package dev.yona.web.social

import dev.yona.domain.social.SocialAccountService
import dev.yona.domain.social.SocialIdentity
import dev.yona.domain.social.SocialLoginException
import dev.yona.domain.social.SocialLoginResult
import dev.yona.domain.social.SocialProviders
import dev.yona.domain.user.UserCredentialRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.security.LoginSessionService
import dev.yona.security.YonaAuthenticationFilter
import dev.yona.web.RedirectSupport
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

/**
 * 소셜 로그인(GitHub, Google)이다. v1.6 `Application.oAuth`, `oAuthDenied`, `oAuthLogout`(`app/controllers/Application.java:65-80`)과
 * play-authenticate의 인증 흐름, `Global`의 해석기(`app/Global.java:184-235`)에 해당한다.
 *
 * 흐름: 제공자로 보냈다가 인증 코드와 함께 돌아오면 코드를 토큰으로 바꿔 사용자 정보를 읽는다. 이미 연결된 신원이면 연결된 사용자로
 * 로그인하고, 처음 보는 신원이면 가입시킨다. 소셜 세션이 이미 있는 채로 연결되지 않은 다른 제공자로 인증하면 그 계정에 연결한다
 * (v1.6 설정 `accountAutoLink=true`, `accountMergeEnabled=false`). 끝나면 인증 전에 보던 주소로, 없으면 홈으로 보낸다.
 */
@Controller
class SocialLoginController(
    private val providers: SocialProviders,
    private val social: SocialAccountService,
    private val socialSession: SocialSession,
    private val loginSession: LoginSessionService,
    private val twoFactorLogin: dev.yona.security.TwoFactorLogin,
    private val credentials: UserCredentialRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val audit: dev.yona.domain.audit.AuditBoundary
) {

    /** 인증을 시작하거나(코드 없음) 제공자가 돌아온 뒤의 처리를 한다(코드 있음). */
    @GetMapping("/authenticate/{provider}")
    fun authenticate(
        @PathVariable provider: String,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        // 사내 인증(LDAP, 사내 OIDC, 사내 SAML 2)을 쓰는 사이트는 소셜 로그인을 받지 않는다(D-81)
        if (providers.blockedByEnterpriseLogin()) return RedirectSupport.see(request, response, "/users/loginform", mapOf(RedirectSupport.WARNING to "social.error.disabled"))
        val found = providers.find(provider) ?: return providerNotFound(provider, response)
        // v1.6: 거절은 안내 화면으로, 그 밖의 오류는 오류 문구를 본문으로 한 500이다.
        if (error == "access_denied") return RedirectSupport.see(request, response, "/authenticate/$provider/denied")
        if (error != null) return providerError(request, response, error)
        val redirectUri = providers.redirectUri(provider, request.getHeader(HttpHeaders.HOST) ?: hostOf(request), request.contextPath)
        fun toProvider(): Any {
            noStore(response)
            return RedirectSupport.see(request, response, found.authorizationUri(redirectUri, socialSession.newState(request, provider)))
        }
        // v1.6: `code` 질의가 있으면(비어 있어도) 돌아온 요청으로 본다.
        if (!request.parameterMap.containsKey("code")) return toProvider()
        when (socialSession.checkState(request, provider, state)) {
            SocialSession.StateCheck.MISSING -> return toProvider()
            // play-authenticate는 state가 다르면 예외를 던지고 Global.onError가 500 화면을 낸다
            SocialSession.StateCheck.MISMATCH -> error("state가 보낸 값과 다릅니다")
            SocialSession.StateCheck.MATCH -> Unit
        }
        val identity = try {
            found.identityOf(code.orEmpty(), redirectUri)
        } catch (e: SocialLoginException) {
            return providerError(request, response, e.message)
        }
        return finish(identity, request, response)
    }

    /** v1.6 `Application.oAuth`는 알 수 없는 제공자에 Play의 `notFound("Provider … could not be found!")`를 돌려준다. 캐시 금지 머리글이 붙는다. */
    private fun providerNotFound(provider: String, response: HttpServletResponse): ResponseEntity<String> {
        noStore(response)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType("text", "plain", Charsets.UTF_8)).body("Provider $provider could not be found!")
    }

    /**
     * 제공자가 알려 준 오류는 오류 문구를 안내로 달아 로그인 화면으로 보낸다. v1.6은 Play의 `internalServerError(message)`처럼 문구만 본문에 담은 500이었다(R-59-12).
     */
    private fun providerError(request: HttpServletRequest, response: HttpServletResponse, message: String?): Any {
        noStore(response)
        return RedirectSupport.see(request, response, "/users/loginform", mapOf(RedirectSupport.WARNING to message.orEmpty()))
    }

    /** 사용자가 제공자의 인증 화면에서 거절했다. v1.6의 안내 문구를 그대로 쓴다. */
    @GetMapping("/authenticate/{provider}/denied")
    fun denied(request: HttpServletRequest, response: HttpServletResponse): Any {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader(HttpHeaders.EXPIRES, "0")
        return RedirectSupport.see(
            request, response, "/", mapOf(RedirectSupport.ERROR to "You need to accept the OAuth connection in order to use this website!")
        )
    }

    /** play-authenticate가 붙이는 캐시 금지 머리글이다. */
    private fun noStore(response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader(HttpHeaders.EXPIRES, "0")
    }

    /**
     * 로컬 세션과 소셜 세션을 모두 지우고 이전 화면으로 돌려보낸다 (v1.6 `oAuthLogout`). `Referer`가 없으면 v1.6의 `returnToReferer()`가
     * 돌려준 결과 객체의 문자열이 그대로 `Location`이 된다(결함 재현, R-03).
     */
    @GetMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): Any {
        loginSession.end(request, response)
        socialSession.end(request)
        noStore(response)
        val flash = mapOf<String, Any>(RedirectSupport.SUCCESS to "user.logout.success")
        val referer = request.getHeader(HttpHeaders.REFERER)?.takeIf { it.isNotEmpty() }
            ?: run {
                // 인코딩하지 않은 글자 그대로 `Location`에 쓴다
                RedirectSupport.saveFlash(request, response, flash)
                request.setAttribute(dev.yona.web.LocationEncodingFilter.RAW_ATTRIBUTE, true)
                return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, "Result(200, Map(Content-Type -> text/html; charset=utf-8))").build<Void>()
            }
        return RedirectSupport.see(request, response, referer, flash)
    }

    /** 소셜 세션이 있어야 볼 수 있는 시험 화면이다 (v1.6 `Restricted.index`, play-authenticate 예제에서 온 것이다). */
    @GetMapping("/restricted")
    @Transactional(readOnly = true)
    fun restricted(request: HttpServletRequest, response: HttpServletResponse): Any {
        val current = socialSession.current(request)
        val credential = current?.let { credentials.findActiveByProvider(it.provider, it.providerUserId).firstOrNull() }
        if (current == null || credential == null) {
            // v1.6 `@Security.Authenticated`의 거절 응답에는 기본 액션의 `Cache-Control: no-cache`가 붙지 않는다
            response.reset()
            return RedirectSupport.see(request, response, "/", mapOf("message" to "Nice try, but you need to log in first!"))
        }
        return ModelAndView("restricted")
            .addObject("title", "Restricted")
            .addObject("credential", credential)
            .addObject("auth", current)
    }

    private fun hostOf(request: HttpServletRequest): String =
        if (request.serverPort in listOf(80, 443)) request.serverName else "${request.serverName}:${request.serverPort}"

    /** 신원을 처리해 로그인시키고(필요하면 연결하고) 돌아갈 곳으로 보낸다. */
    private fun finish(identity: SocialIdentity, request: HttpServletRequest, response: HttpServletResponse): Any {
        // 소셜 세션이 가리키는 자격 증명이 없으면 소셜 세션을 지우고 로그인하지 않은 것으로 본다 (v1.6 `handleAuthentication`).
        var current = socialSession.current(request)
        if (current != null && !social.isLinked(SocialIdentity(current.provider, current.providerUserId, null, null))) {
            socialSession.end(request)
            current = null
        }
        val result = if (current != null && !(current.provider == identity.provider && current.providerUserId == identity.providerUserId)) {
            if (social.isLinked(identity)) {
                social.signIn(identity).also { socialSession.start(request, identity) }
            } else {
                social.link(SocialIdentity(current.provider, current.providerUserId, null, null), identity)
                SocialLoginResult()
            }
        } else {
            social.signIn(identity).also { socialSession.start(request, identity) }
        }
        var challenge = false
        result.user?.let { user ->
            if (twoFactorLogin.required(user, request)) {
                // 2단계 인증을 켠 사용자는 소셜 로그인 뒤에도 코드를 확인해야 한다. 로그인 유지 쿠키는 2단계를 마친 뒤에 만든다(X-2, D-69)
                twoFactorLogin.begin(request, user, "social", false, socialSession.popOriginalUrl(request) ?: "/", result.flash.mapValues { it.value.toString() })
                challenge = true
            } else {
                // 소셜 로그인에는 로그인 유지를 고르는 곳이 없으므로 로그인 유지 쿠키를 남기지 않는다. v1.6은 항상 남겼다(R-59-4)
                loginSession.start(request, response, user)
                audit.loginSuccess(user, "social")
            }
        }
        if (challenge) return RedirectSupport.see(request, response, dev.yona.security.TwoFactorLogin.CHALLENGE_PATH)
        if (result.forceLogout) socialSession.forceLogout(request)
        return RedirectSupport.see(request, response, socialSession.popOriginalUrl(request) ?: "/", result.flash)
    }
}
