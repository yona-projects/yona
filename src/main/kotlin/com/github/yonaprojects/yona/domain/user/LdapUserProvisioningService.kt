package com.github.yonaprojects.yona.domain.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * yona의 UserApp.authenticateWithLdap() 성공 분기(LDAP 인증 자체가 아니라
 * "인증된 LDAP 사용자 정보를 로컬 User와 어떻게 맞출 것인가")에 대응.
 * LDAP 디렉터리 바인딩(LdapService)과 분리해 실제 LDAP 서버 없이 단위테스트 가능하다.
 */
@Service
class LdapUserProvisioningService(
    private val userRepository: UserRepository,
    private val passwordEncodingService: PasswordEncodingService
) {
    @Transactional
    fun reconcile(ldapUser: LdapUser, rawPassword: String): User {
        val existing = userRepository.findByEmail(ldapUser.email).orElse(null)
        return if (existing == null) {
            createNewUser(ldapUser, rawPassword)
        } else {
            syncExistingUser(existing, ldapUser, rawPassword)
        }
    }

    private fun createNewUser(ldapUser: LdapUser, rawPassword: String): User {
        val user = User(
            loginId = ldapUser.loginId,
            name = ldapUser.fullDisplayName,
            email = ldapUser.email,
            password = passwordEncodingService.encode(rawPassword),
            passwordSalt = null,
            isGuest = ldapUser.isGuestUser,
            state = UserState.ACTIVE
        )
        if (!ldapUser.englishName.isNullOrBlank()) {
            user.englishName = ldapUser.englishName
        }
        return userRepository.save(user)
    }

    private fun syncExistingUser(user: User, ldapUser: LdapUser, rawPassword: String): User {
        if (!passwordEncodingService.matches(rawPassword, user.password, user.passwordSalt)) {
            user.password = passwordEncodingService.encode(rawPassword)
            user.passwordSalt = null
        }
        user.name = ldapUser.fullDisplayName
        if (!ldapUser.englishName.isNullOrBlank()) {
            user.englishName = ldapUser.englishName
        }
        user.isGuest = ldapUser.isGuestUser
        return userRepository.save(user)
    }
}
