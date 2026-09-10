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
        val user = userRepository.findByLoginId(username)
            .orElseThrow { UsernameNotFoundException("사용자를 찾을 수 없습니다: $username") }

        val authorities = mutableListOf(SimpleGrantedAuthority("ROLE_${user.state.name}"))
        if (user.state == UserState.SITE_ADMIN) {
            // 테스트 커버리지 도달 불가: 이 분기에 들어오면 이미 위 라인에서 "ROLE_SITE_ADMIN"이
            // authorities의 유일한 원소로 들어가 있으므로 none{}은 항상 false — 중복 방지용 방어
            // 코드지만 이 시점에는 중복이 성립할 수 없다.
            if (authorities.none { it.authority == "ROLE_SITE_ADMIN" }) {
                authorities.add(SimpleGrantedAuthority("ROLE_SITE_ADMIN"))
            }
            if (authorities.none { it.authority == "ROLE_ADMIN" }) {
                authorities.add(SimpleGrantedAuthority("ROLE_ADMIN"))
            }
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
