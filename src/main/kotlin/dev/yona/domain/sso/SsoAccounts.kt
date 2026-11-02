package dev.yona.domain.sso

import dev.yona.domain.user.User

/** SSO 프로토콜이다. [providerKey]는 `linked_account.provider_key`에 저장하는 값이다(소셜 로그인의 `github`, `google`과 겹치지 않는다). */
enum class SsoProtocol(val providerKey: String) {
    OIDC("sso-oidc"),
    SAML2("sso-saml2")
}

/**
 * IdP가 알려 준 사용자이다(추가 개발 항목 X-16, `decisions.md` D-80). 계정을 알아보는 값은 이메일이 아니라 **발급자와 subject**(SAML은 발급자와 NameID)이다.
 *
 * @property issuer IdP의 발급자 식별자(OIDC `iss`, SAML 발급자 Entity ID)
 * @property subject IdP 안에서 사용자를 가리키는 값(OIDC `sub`, SAML NameID). 이메일이 바뀌어도 같다
 * @property email IdP가 알려 준 이메일. 알 수 없으면 `null`
 * @property emailVerified IdP가 이메일을 확인했다고 알렸으면 `true`(OIDC `email_verified`, SAML은 관리자가 신뢰하기로 한 IdP의 속성)
 * @property preferredLoginId 로그인 ID 후보로 쓸 값(설정한 클레임, 예: `preferred_username`)
 */
data class SsoIdentity(
    val protocol: SsoProtocol, val issuer: String, val subject: String, val email: String?, val emailVerified: Boolean, val name: String?, val preferredLoginId: String? = null
) {
    /** `linked_account.provider_user_id`에 저장하는 값이다. 같은 subject라도 발급자가 다르면 다른 사람이다. */
    val providerUserId: String get() = "$issuer|$subject"
}

/**
 * 프로토콜별 SSO 설정 가운데 계정을 맞추는 데 쓰는 규칙이다(`decisions.md` D-80 7, 8, 10번).
 *
 * @property autoCreate 처음 보는 사용자를 자동으로 만든다(기본 켬)
 * @property linkByEmail 같은 이메일의 기존 계정에 처음 로그인할 때 연결한다(기본 켬)
 * @property allowUnverifiedEmail IdP가 확인하지 않은 이메일도 연결과 생성에 쓴다(기본 끔. 가로채기 위험이 있다)
 * @property nameSync 로그인마다 이름을 IdP 값으로 바꾼다(기본 끔)
 */
data class SsoPolicy(val autoCreate: Boolean = true, val linkByEmail: Boolean = true, val allowUnverifiedEmail: Boolean = false, val nameSync: Boolean = false)

/** 로그인을 거절한 이유이다. [messageKey]는 로그인 화면에 보일 메시지 키이다. */
enum class SsoRejection(val messageKey: String) {
    EMAIL_MISSING("sso.error.emailMissing"),
    EMAIL_UNVERIFIED("sso.error.emailUnverified"),
    EMAIL_IN_USE("sso.error.emailInUse"),
    EMAIL_DOMAIN("sso.error.emailDomain"),
    USER_NOT_FOUND("sso.error.userNotFound"),
    LOCKED("sso.error.locked")
}

/** [SsoAccountService.signIn]의 결과이다. */
sealed interface SsoSignInResult {

    /** 로그인시킬 [user]를 정했다. [created]는 새로 만든 사용자, [linked]는 이번에 신원을 연결했다는 뜻이다. */
    data class Success(val user: User, val created: Boolean, val linked: Boolean) : SsoSignInResult

    /** 로그인시키지 않는다. */
    data class Rejected(val reason: SsoRejection) : SsoSignInResult
}
