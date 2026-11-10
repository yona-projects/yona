package dev.yona.security

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.ldap.LdapAuthService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException

/**
 * 비밀번호 로그인이 거절되었음을 [LoginResult]와 함께 알리는 인증 예외이다. 화면 쪽이 v1.6과 같은 안내 문구를 고르는 데 결과가 필요해서, 스프링의
 * 일반 예외(`BadCredentialsException`, `LockedException` 등)로 뭉뚱그리지 않고 결과를 그대로 싣는다.
 *
 * @property result 거절한 이유. [LoginResult.Success]는 아니다
 */
class LoginDeniedException(val result: LoginResult) : AuthenticationException(result::class.simpleName)

/**
 * 비밀번호 로그인에 성공했을 때 인증 객체의 `details`에 싣는 값이다. 로그인한 사용자 엔티티와 다음 화면에 한 번만 보일 안내(LDAP 인증이 남긴 것)이다.
 *
 * @property user 로그인한 사용자
 * @property flash 다음 화면에 보일 안내
 */
data class PasswordLoginDetails(val user: User, val flash: Map<String, String> = emptyMap())

/**
 * 로그인 ID 또는 이메일과 비밀번호를 판정하는 스프링 [AuthenticationProvider]이다(로드맵 X-43 1단계 작업 1-5, `decisions.md` D-129).
 * v1.6 `UserApp.loginByFormRequest`(`app/controllers/UserApp.java:197-259`)의 판정 순서를 그대로 따른다.
 *
 * 0. SSO 전용 모드이면 사이트 관리자가 아닌 ID는 비밀번호를 보기 전에 [LoginResult.SsoOnly]이다(없는 ID도 같다).
 * 1. 로그인 ID 또는 이메일로 계정을 찾는다. 없고 LDAP도 쓰지 않으면 [LoginResult.InvalidCredentials]이다.
 * 2. 가입 승인제이고 계정이 LOCKED이면 비밀번호를 보기 전에 [LoginResult.Locked]이다.
 * 3. 계정이 DELETED이면 [LoginResult.Deleted]이다.
 * 4. 비밀번호가 틀리면 [LoginResult.InvalidCredentials]이다. LDAP을 쓰면 비밀번호는 로컬이 아니라 LDAP이 판정하고, 계정이 없어도 LDAP으로 시도한다.
 * 5. 비밀번호가 맞아도 계정이 LOCKED이면 [LoginResult.LockedAfterAuthentication]이다.
 *
 * 성공하면 [YonaAuthentication]을 돌려주고 사용자와 안내는 `details`([PasswordLoginDetails])에 싣는다. 거절하면 [LoginDeniedException]을 던진다.
 * 솔트나 해시가 없는 계정은 v1.6처럼 [IllegalArgumentException]이 그대로 올라간다(HTTP 500).
 *
 * **스프링 빈이 아니다.** `AuthenticationProvider` 빈이 하나뿐이면 스프링이 전역 인증 관리자에 자동으로 등록해 다른 보안 체인에 영향을 주므로,
 * [LocalAuthenticator]가 직접 만들어 자기만의 `ProviderManager`에서 쓴다.
 */
class YonaPasswordAuthenticationProvider(
    private val users: UserRepository,
    private val properties: YonaProperties,
    private val ldap: LdapAuthService,
    private val passwords: UserPasswordService,
    private val siteSettings: dev.yona.domain.site.SiteSettingService,
    private val siteAdmins: dev.yona.domain.user.SiteAdminRepository
) : AuthenticationProvider {

    override fun supports(authentication: Class<*>): Boolean = UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)

    override fun authenticate(authentication: Authentication): Authentication {
        val loginKey = authentication.name
        val password = authentication.credentials?.toString().orEmpty()
        val source = users.findByLoginKey(loginKey)
        // SSO 전용 모드(X-16, D-80 12번)에서는 사이트 관리자만 로컬로 로그인한다(비상 경로). 입력한 ID가 관리자가 아니면 비밀번호를 확인하기 전에 거절한다
        if (siteSettings.ssoOnly() && source?.id?.let { siteAdmins.existsByAdminId(it) } != true) throw LoginDeniedException(LoginResult.SsoOnly)
        if (source == null && !ldap.enabled) throw LoginDeniedException(LoginResult.InvalidCredentials)

        if (source != null) {
            if (properties.signup.required() && source.state == UserState.LOCKED) throw LoginDeniedException(LoginResult.Locked)
            if (source.state == UserState.DELETED) throw LoginDeniedException(LoginResult.Deleted)
        }

        val user: User?
        var flash: Map<String, String> = emptyMap()
        if (ldap.enabled) {
            val result = ldap.authenticate(loginKey, password)
            user = result.user
            flash = result.flash
        } else {
            user = source?.takeIf { passwords.matches(it, password) }
            // v1.6 해시로 로그인에 성공했으면 그 자리에서 argon2id로 바꿔 저장한다(D-131). 상태 확인(잠금) 전이지만 비밀번호는 맞은 것이다
            if (user != null && passwords.needsUpgrade(user)) {
                passwords.set(user, password)
                users.save(user)
            }
        }
        if (user == null) throw LoginDeniedException(if (flash.isEmpty()) LoginResult.InvalidCredentials else LoginResult.InvalidWithNotice(flash))
        if (user.state == UserState.LOCKED) throw LoginDeniedException(LoginResult.LockedAfterAuthentication)
        return YonaAuthentication.of(user).also { it.details = PasswordLoginDetails(user, flash) }
    }
}
