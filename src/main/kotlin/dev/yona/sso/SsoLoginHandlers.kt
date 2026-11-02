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
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import dev.yona.domain.sso.SsoPolicy
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * SSO 로그인 결과를 이 프로젝트의 세션 로그인으로 잇는다(X-16, `decisions.md` D-80 1, 11번). 프로토콜(OIDC, SAML 2)이 검증한 신원을 [SsoIdentity]로 받아
 * [SsoAccountService]에 맡기고, 로그인시킬 사용자가 정해지면 [LoginSessionService]로 세션을 시작한다. **스프링의 인증 객체는 세션에 남기지 않는다**(우리 세션 하나만 쓴다).
 * SSO 로그인은 Yona의 2단계 인증을 거치지 않는다(IdP가 MFA를 맡는다). 로그인 유지 쿠키는 만들지 않는다(세션이 끝나면 IdP로 다시 확인한다).
 */
@Component
class SsoSignInCompleter(
    private val accounts: SsoAccountService,
    private val loginSession: LoginSessionService,
    private val audit: AuditBoundary
) {

    /** [identity]로 로그인을 마친다. 거절되면 로그인 화면으로 보내고 실패를 남긴다. */
    fun complete(request: HttpServletRequest, response: HttpServletResponse, identity: SsoIdentity, policy: SsoPolicy) {
        // 스프링이 세션에 둔 인증 객체는 지운다. 로그인은 아래에서 우리 인증 객체로 새로 시작한다
        SecurityContextHolder.clearContext()
        request.getSession(false)?.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
        val protocol = if (identity.protocol == SsoProtocol.SAML2) "saml2" else "oidc"
        when (val result = accounts.signIn(identity, policy)) {
            is SsoSignInResult.Rejected -> SsoLoginFailures.redirect(request, response, audit, protocol, result.reason.name.lowercase(), identity.email, result.reason.messageKey)
            is SsoSignInResult.Success -> {
                val initiated = request.getSession(false)?.getAttribute(SsoReturn.INITIATED) == true
                loginSession.start(request, response, result.user, twoFactorVerified = true)
                val extra = mapOf("created" to result.created, "linked" to result.linked)
                audit.loginSuccess(result.user, "sso-$protocol", if (initiated) extra + ("initiated" to "idp") else extra)
                request.getSession(false)?.removeAttribute(SsoReturn.INITIATED)
                response.sendRedirect(response.encodeRedirectURL(request.contextPath.orEmpty() + SsoReturn.pop(request)))
            }
        }
    }
}

/** OIDC 인가 코드 흐름이 ID 토큰 검증을 마친 뒤의 처리이다. ID 토큰의 클레임을 [SsoIdentity]로 바꿔 [SsoSignInCompleter]에 넘긴다. */
@Component
class SsoLoginSuccessHandler(private val settings: SsoSettingsService, private val completer: SsoSignInCompleter, private val audit: AuditBoundary) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
        val oidcUser = (authentication as? OAuth2AuthenticationToken)?.principal as? OidcUser
        if (oidcUser == null) {
            SecurityContextHolder.clearContext()
            return SsoLoginFailures.redirect(request, response, audit, "oidc", "invalid_principal", null, "sso.error.failed")
        }
        val row = settings.oidc()
        fun claim(name: String?) = name?.takeIf { it.isNotBlank() }?.let { oidcUser.claims[it] as? String }?.takeIf { it.isNotBlank() }
        val verified = oidcUser.claims["email_verified"].let { it == true || (it is String && it.equals("true", ignoreCase = true)) }
        val identity = SsoIdentity(
            SsoProtocol.OIDC, oidcUser.idToken.issuer?.toString().orEmpty(), oidcUser.subject.orEmpty(), claim(row.emailClaim), verified, claim(row.nameClaim), claim(row.loginIdClaim)
        )
        completer.complete(request, response, identity, settings.oidcPolicy())
    }
}

/** SAML 2 응답 검증을 마친 뒤의 처리이다. NameID와 설정한 속성에서 [SsoIdentity]를 만든다. IdP의 이메일 속성은 사이트 관리자가 신뢰하기로 한 IdP의 값이므로 확인된 것으로 본다. */
@Component
class SsoSaml2SuccessHandler(private val settings: SsoSettingsService, private val completer: SsoSignInCompleter, private val audit: AuditBoundary) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
        val principal = authentication.principal as? Saml2AuthenticatedPrincipal
        if (principal == null) {
            SecurityContextHolder.clearContext()
            return SsoLoginFailures.redirect(request, response, audit, "saml2", "invalid_principal", null, "sso.error.failed")
        }
        val row = settings.saml2()
        fun attribute(name: String?) = name?.takeIf { it.isNotBlank() }?.let { principal.getFirstAttribute<Any?>(it)?.toString() }?.takeIf { it.isNotBlank() }
        val nameId = principal.name
        val email = attribute(row.emailAttribute) ?: nameId.takeIf { EMAIL_LIKE.matches(it) }
        val identity = SsoIdentity(SsoProtocol.SAML2, row.idpEntityId.orEmpty(), nameId, email, true, attribute(row.nameAttribute), attribute(row.loginIdAttribute))
        completer.complete(request, response, identity, settings.saml2Policy())
    }

    private companion object { val EMAIL_LIKE = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") }
}

/** 프로토콜 오류(서명, 발급자, 대상, 만료, nonce, state, IdP 오류)로 SSO 로그인이 실패했을 때의 처리이다. 로그인 화면으로 보내고 감사 기록을 남긴다. */
@Component
class SsoLoginFailureHandler(private val audit: AuditBoundary) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(request: HttpServletRequest, response: HttpServletResponse, exception: AuthenticationException) {
        val saml = exception as? Saml2AuthenticationException
        val code = saml?.saml2Error?.errorCode ?: (exception as? OAuth2AuthenticationException)?.error?.errorCode ?: exception.javaClass.simpleName
        SsoLoginFailures.redirect(request, response, audit, if (saml != null) "saml2" else "oidc", code.lowercase().take(60), null, "sso.error.failed")
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
