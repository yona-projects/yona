package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.user.LdapAuthResult
import com.github.yonaprojects.yona.domain.user.LdapService
import com.github.yonaprojects.yona.domain.user.LdapUserProvisioningService
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
import java.security.MessageDigest
import java.util.Base64

@Component
class YonaAuthenticationProvider(
    private val userDetailsService: UserDetailsService,
    private val ldapService: LdapService,
    private val ldapUserProvisioningService: LdapUserProvisioningService
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

        val hashedInput = hashPassword(password, userDetails.passwordSalt)
        if (hashedInput != userDetails.password) {
            throw BadCredentialsException("비밀번호가 일치하지 않습니다.")
        }

        return UsernamePasswordAuthenticationToken(userDetails, password, userDetails.authorities)
    }

    private fun checkAccountState(userDetails: YonaUserDetails) {
        if (!userDetails.isAccountNonLocked) {
            throw LockedException("계정이 잠겨 있습니다.")
        }
        if (!userDetails.isEnabled) {
            throw DisabledException("탈퇴한 계정입니다.")
        }
    }

    override fun supports(authentication: Class<*>): Boolean {
        return UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt.toByteArray(Charsets.UTF_8))
        var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
        for (i in 1 until 1024) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        return Base64.getEncoder().encodeToString(hashed)
    }
}
