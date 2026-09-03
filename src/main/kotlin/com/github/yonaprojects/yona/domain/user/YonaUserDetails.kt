package com.github.yonaprojects.yona.domain.user

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class YonaUserDetails(
    val id: Long,
    val loginId: String,
    private val passwordVal: String,
    val passwordSalt: String,
    private val authoritiesVal: Collection<GrantedAuthority>,
    private val state: UserState = UserState.ACTIVE
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = authoritiesVal
    override fun getPassword(): String = passwordVal
    override fun getUsername(): String = loginId
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = state != UserState.LOCKED
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = state != UserState.DELETED
}
