package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.user.LdapAuthResult
import com.github.yonaprojects.yona.domain.user.LdapService
import com.github.yonaprojects.yona.domain.user.LdapUserProvisioningService
import com.github.yonaprojects.yona.domain.user.PasswordEncodingService
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class YonaAuthenticationProvider(
    private val userDetailsService: UserDetailsService,
    private val ldapService: LdapService,
    private val ldapUserProvisioningService: LdapUserProvisioningService,
    private val passwordEncodingService: PasswordEncodingService,
    private val userRepository: UserRepository
) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication {
        val loginId = authentication.name
        val password = authentication.credentials.toString()

        if (ldapService.enabled) {
            return authenticateWithLdap(loginId, password)
        }

        return authenticateLocally(loginId, password)
    }

    private fun authenticateWithLdap(loginId: String, password: String): Authentication {
        return when (val result = ldapService.authenticate(loginId, password)) {
            is LdapAuthResult.Success -> {
                val reconciledUser = ldapUserProvisioningService.reconcile(result.user, password)
                val userDetails = userDetailsService.loadUserByUsername(reconciledUser.loginId) as YonaUserDetails
                checkAccountState(userDetails)
                UsernamePasswordAuthenticationToken(userDetails, password, userDetails.authorities)
            }
            is LdapAuthResult.InvalidCredentials -> {
                if (ldapService.fallbackToLocalLogin) {
                    authenticateLocally(loginId, password)
                } else {
                    throw BadCredentialsException("LDAP 인증에 실패했습니다.")
                }
            }
            is LdapAuthResult.ConnectionFailed -> {
                if (ldapService.fallbackToLocalLogin) {
                    authenticateLocally(loginId, password)
                } else {
                    throw AuthenticationServiceException("LDAP 서버에 연결할 수 없습니다.", result.cause)
                }
            }
        }
    }

    private fun authenticateLocally(loginId: String, password: String): Authentication {
        val userDetails = userDetailsService.loadUserByUsername(loginId) as YonaUserDetails
        checkAccountState(userDetails)

        if (!passwordEncodingService.matches(password, userDetails.password, userDetails.passwordSalt)) {
            onLoginFailure(userDetails.id)
            throw BadCredentialsException("비밀번호가 일치하지 않습니다.")
        }

        onLoginSuccess(userDetails.id, password, userDetails.password)

        return UsernamePasswordAuthenticationToken(userDetails, password, userDetails.authorities)
    }

    private fun checkAccountState(userDetails: YonaUserDetails) {
        if (!userDetails.isAccountNonLocked) {
            throw LockedException("계정이 잠겨 있습니다.")
        }
        if (!userDetails.isEnabled) {
            throw DisabledException("탈퇴한 계정입니다.")
        }
        // 위 UserState.LOCKED(관리자 수동 잠금)와는 완전히 별개 축인 브루트포스 자동 잠금.
        // 상태를 바꾸지 않으므로 관리자 화면의 계정 상태에는 드러나지 않지만, 로그인 자체는
        // lockedUntil이 지날 때까지(또는 관리자가 해제할 때까지) 막힌다.
        val lockedUntil = userDetails.lockedUntil
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            throw LockedException("로그인 실패 횟수가 많아 계정이 일시적으로 잠겼습니다. 잠시 후 다시 시도하거나 관리자에게 문의하세요.")
        }
    }

    // 계정별 실패 횟수를 세어 MAX_FAILED_LOGIN_ATTEMPTS회에 도달하면 LOCK_DURATION 동안 자동
    // 잠근다. lockedUntil이 지나면(checkAccountState 참고) 별도 조작 없이 자동 해제된다 —
    // 관리자가 즉시 풀어주고 싶다면 UserController의 unlockBruteForceLock() 엔드포인트를 쓴다.
    private fun onLoginFailure(userId: Long) {
        userRepository.findById(userId).ifPresent { user ->
            user.failedLoginAttempts += 1
            if (user.failedLoginAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                user.lockedUntil = Instant.now().plus(LOCK_DURATION)
            }
            userRepository.save(user)
        }
    }

    private fun onLoginSuccess(userId: Long, rawPassword: String, storedHash: String) {
        userRepository.findById(userId).ifPresent { user ->
            var dirty = false
            if (user.failedLoginAttempts != 0 || user.lockedUntil != null) {
                user.failedLoginAttempts = 0
                user.lockedUntil = null
                dirty = true
            }
            // 레거시 SHA-256 해시로 저장된 계정이 로그인에 성공한 순간 그 자리에서 Argon2id로
            // 재해싱해 저장한다 — 강제 비밀번호 재설정 없이 기존 가입자를 전부 새 포맷으로
            // 점진적으로 이전하기 위함(PasswordEncodingService 주석 참고).
            if (passwordEncodingService.needsUpgrade(storedHash)) {
                user.password = passwordEncodingService.encode(rawPassword)
                user.passwordSalt = null
                dirty = true
            }
            if (dirty) userRepository.save(user)
        }
    }

    override fun supports(authentication: Class<*>): Boolean {
        return UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
    }

    companion object {
        private const val MAX_FAILED_LOGIN_ATTEMPTS = 5
        private val LOCK_DURATION: Duration = Duration.ofMinutes(15)
    }
}
