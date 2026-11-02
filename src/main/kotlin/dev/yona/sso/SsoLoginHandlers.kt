package dev.yona.sso

import dev.yona.domain.audit.AuditBoundary
import dev.yona.domain.sso.SsoAccountService
import dev.yona.domain.sso.SsoIdentity
import dev.yona.domain.sso.SsoProtocol
import dev.yona.domain.sso.SsoSettingsService
import dev.yona.domain.sso.SsoSignInResult
import dev.yona.security.LoginSessionService
import dev.yona.web.RedirectSupport
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * SSO 로그인 결과를 이 프로젝트의 세션 로그인으로 잇는다(X-16, `decisions.md` D-80 1, 11번). 스프링 시큐리티가 ID 토큰을 검증한 뒤의 사용자 정보를 [SsoIdentity]로 바꿔
 * [SsoAccountService]에 맡기고, 로그인시킬 사용자가 정해지면 [LoginSessionService]로 세션을 시작한다. **스프링의 인증 객체는 세션에 남기지 않는다**(우리 세션 하나만 쓴다).
 * OIDC 로그인은 Yona의 2단계 인증을 거치지 않는다(IdP가 MFA를 맡는다). 로그인 유지 쿠키는 만들지 않는다(세션이 끝나면 IdP로 다시 확인한다).
 */
@Component
class SsoLoginSuccessHandler(
    private val settings: SsoSettingsService,
    private val accounts: SsoAccountService,
    private val loginSession: LoginSessionService,
    private val audit: AuditBoundary
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
        val oidcUser = (authentication as? OAuth2AuthenticationToken)?.principal as? OidcUser
        // 스프링이 세션에 둔 OAuth 인증 객체는 지운다. 로그인은 아래에서 우리 인증 객체로 새로 시작한다
        SecurityContextHolder.clearContext()
        request.getSession(false)?.removeAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
        if (oidcUser == null) return fail(request, response, "invalid_principal", null, "sso.error.failed")
        val row = settings.oidc()
        fun claim(name: String?) = name?.takeIf { it.isNotBlank() }?.let { oidcUser.claims[it] as? String }?.takeIf { it.isNotBlank() }
        val verified = oidcUser.claims["email_verified"].let { it == true || (it is String && it.equals("true", ignoreCase = true)) }
        val identity = SsoIdentity(
            SsoProtocol.OIDC, oidcUser.idToken.issuer?.toString().orEmpty(), oidcUser.subject.orEmpty(), claim(row.emailClaim), verified, claim(row.nameClaim), claim(row.loginIdClaim)
        )
        when (val result = accounts.signIn(identity, settings.oidcPolicy())) {
            is SsoSignInResult.Rejected -> fail(request, response, result.reason.name.lowercase(), identity.email, result.reason.messageKey)
            is SsoSignInResult.Success -> {
                val initiated = request.getSession(false)?.getAttribute(SsoReturn.INITIATED) == true
                loginSession.start(request, response, result.user, twoFactorVerified = true)
                audit.loginSuccess(result.user, "sso-oidc", if (initiated) mapOf("initiated" to "idp", "created" to result.created, "linked" to result.linked) else mapOf("created" to result.created, "linked" to result.linked))
                request.getSession(false)?.removeAttribute(SsoReturn.INITIATED)
                response.sendRedirect(response.encodeRedirectURL(requestContext(request) + SsoReturn.pop(request)))
            }
        }
    }

    private fun fail(request: HttpServletRequest, response: HttpServletResponse, reason: String, email: String?, messageKey: String) =
        SsoLoginFailures.redirect(request, response, audit, "oidc", reason, email, messageKey)

    private fun requestContext(request: HttpServletRequest) = request.contextPath.orEmpty()
}

/** 프로토콜 오류(서명, 발급자, 대상, 만료, nonce, state, IdP 오류)로 SSO 로그인이 실패했을 때의 처리이다. 로그인 화면으로 보내고 감사 기록을 남긴다. */
@Component
class SsoLoginFailureHandler(private val audit: AuditBoundary) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(request: HttpServletRequest, response: HttpServletResponse, exception: AuthenticationException) {
        val code = (exception as? org.springframework.security.oauth2.core.OAuth2AuthenticationException)?.error?.errorCode ?: exception.javaClass.simpleName
        SsoLoginFailures.redirect(request, response, audit, "oidc", code.lowercase().take(60), null, "sso.error.failed")
    }
}

/** 두 처리기가 함께 쓰는 실패 응답이다. */
internal object SsoLoginFailures {

    fun redirect(request: HttpServletRequest, response: HttpServletResponse, audit: AuditBoundary, protocol: String, reason: String, email: String?, messageKey: String) {
        audit.ssoFailure(protocol, reason, email)
        request.getSession(false)?.apply { removeAttribute(SsoReturn.RETURN_TO); removeAttribute(SsoReturn.INITIATED); removeAttribute(SsoReturn.LOGIN_HINT) }
        val location = request.contextPath.orEmpty() + "/users/loginform"
        RedirectSupport.saveFlashBeforeDispatch(request, response, mapOf(RedirectSupport.WARNING to messageKey))
        response.status = HttpStatus.SEE_OTHER.value()
        response.setHeader("Location", location)
    }
}
