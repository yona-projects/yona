package com.github.yonaprojects.yona.domain.user

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        // v1.6 User.findByLoginKey 대응: 로그인 폼은 "아이디 또는 이메일"을 받으므로 loginId로 못 찾으면
        // 대표 이메일로 한 번 더 찾는다. 이후 인증 토큰의 principal은 YonaUserDetails(loginId 기준)다.
        val user = userRepository.findByLoginId(username)
            .or { userRepository.findByEmail(username) }
            .orElseThrow { UsernameNotFoundException("사용자를 찾을 수 없습니다: $username") }

        val authorities = mutableListOf(SimpleGrantedAuthority("ROLE_${user.state.name}"))
        // v1.6 isSiteManager() = SiteAdmin 소속. 잠금(state)과 독립된 축이라 관리자도 ROLE_ACTIVE를 갖는다.
        if (user.isSiteAdmin) {
            authorities.add(SimpleGrantedAuthority("ROLE_SITE_ADMIN"))
            authorities.add(SimpleGrantedAuthority("ROLE_ADMIN"))
        }

        return YonaUserDetails(
            id = user.id ?: 0L,
            loginId = user.loginId,
            passwordVal = user.password ?: "",
            passwordSalt = user.passwordSalt ?: "",
            authoritiesVal = authorities,
            state = user.state,
            lockedUntil = user.lockedUntil
        )
    }
}
