package dev.yona.security

import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.config.YonaProperties
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Service

/** 로그인 시도의 판정 결과이다. 화면 쪽은 결과에 따라 v1.6과 같은 안내 문구를 고른다. */
sealed interface LoginResult {
    /**
     * 인증에 성공했다.
     *
     * @property flash 다음 화면에 한 번만 보일 안내. LDAP 인증이 남긴 것이다
     */
    data class Success(val user: User, val flash: Map<String, String> = emptyMap()) : LoginResult

    /**
     * 가입 승인제에서 승인 대기(LOCKED) 계정이다(`user.locked`). 패스워드를 보기 전에 거부하며,
     * 로그인 폼으로 돌려보낸다.
     */
    data object Locked : LoginResult

    /**
     * 패스워드는 맞았지만 계정이 LOCKED이다(`user.locked`). v1.6은 이 경우 로그아웃 처리까지 하고
     * 이전 화면으로 돌려보낸다(`UserApp.java:225-228`).
     */
    data object LockedAfterAuthentication : LoginResult

    /** 같은 아이디로 연달아 틀려 잠겨 있다(`user.login.tooMany`, usersec-F11). v1.6에는 횟수 제한이 없었다. */
    data object TooManyAttempts : LoginResult

    /** 계정이 삭제되었다. 화면에는 [InvalidCredentials]와 같게 알려 계정의 존재를 드러내지 않는다(usersec-F11, v1.6은 `user.deleted`였다). */
    data object Deleted : LoginResult

    /**
     * SSO 전용 모드(X-16, `decisions.md` D-80 12번)라 로컬 비밀번호 로그인을 받지 않는다. 사이트 관리자의 로컬 로그인만 비상 경로로 남는다.
     */
    data object SsoOnly : LoginResult

    /** 계정이 없거나 패스워드가 틀렸다(`user.login.invalid`). */
    data object InvalidCredentials : LoginResult

    /**
     * LDAP 인증이 실패하면서 안내를 남겼다(예: 가입 승인 대기 `user.signup.requested`). 화면은 [InvalidCredentials]와 같게 처리하되
     * 이 안내를 함께 보인다.
     */
    data class InvalidWithNotice(val flash: Map<String, String>) : LoginResult
}

/**
 * 로컬 계정(로그인 ID 또는 이메일 + 패스워드)으로 로그인 가능 여부를 판정한다.
 *
 * 판정은 스프링 시큐리티의 `ProviderManager`에 맡기고([YonaPasswordAuthenticationProvider]), 이 클래스는 그 결과를 화면이 쓰는 [LoginResult]로 옮긴다
 * (로드맵 X-43 1단계, `decisions.md` D-129). v1.6 `UserApp.loginByFormRequest`(`app/controllers/UserApp.java:197-259`)의 판정 순서는 제공자 쪽 설명을 본다.
 *
 * LDAP을 쓰는 사이트(`yona.ldap.enabled`)에서는 계정이 없어도 LDAP으로 인증을 시도하고(v1.6에서는 처음 로그인하는 LDAP 사용자가
 * 로컬에 없다), 비밀번호는 로컬이 아니라 LDAP이 판정한다. 소셜 로그인은 제거했다(로드맵 X-48).
 *
 * @property properties `yona.signup.require-admin-confirm`(v1.6 `signup.require.admin.confirm`)을 읽는다.
 */
@Service
class LocalAuthenticator(
    users: UserRepository,
    properties: YonaProperties,
    ldap: dev.yona.domain.ldap.LdapAuthService,
    passwords: UserPasswordService,
    siteSettings: dev.yona.domain.site.SiteSettingService,
    siteAdmins: dev.yona.domain.user.SiteAdminRepository
) {

    /**
     * 비밀번호 로그인의 인증 관리자이다. 제공자를 스프링 빈으로 등록하지 않으려고 직접 조립한다(전역 인증 관리자에 자동 등록되지 않게 한다).
     * 비밀번호 로그인 필터([dev.yona.web.user.PasswordLoginFilter])가 이것을 쓴다.
     */
    val authenticationManager: org.springframework.security.authentication.AuthenticationManager = ProviderManager(YonaPasswordAuthenticationProvider(users, properties, ldap, passwords, siteSettings, siteAdmins))

    /**
     * @param loginIdOrEmail 사용자가 입력한 로그인 ID 또는 이메일
     * @param password 사용자가 입력한 평문 패스워드
     * @throws IllegalArgumentException 계정에 솔트나 패스워드가 저장되어 있지 않을 때.
     *   v1.6의 `UserApp.hashedPassword`가 같은 조건에서 같은 예외를 던진다(HTTP 500).
     */
    fun authenticate(loginIdOrEmail: String, password: String): LoginResult =
        try {
            val details = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(loginIdOrEmail, password)).details as PasswordLoginDetails
            LoginResult.Success(details.user, details.flash)
        } catch (e: LoginDeniedException) {
            e.result
        }
}
