package com.github.yonaprojects.yona.domain.user

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.time.Instant

// OAuth2 동의(consent) 처리 과정에서
// Spring Authorization Server가 인증된 Principal(이 클래스의 인스턴스)을 OAuth2Authorization의
// attributes 맵에 그대로 담아 JSON으로 직렬화/역직렬화한다(OAuthObjectMapper 참고). Jackson은
// 기본적으로 getter 기반 프로퍼티명(getPassword() -> "password", getAuthorities() -> "authorities")으로
// 직렬화하는데, 생성자 파라미터명(passwordVal/authoritiesVal)과 이름이 달라 왕복 역직렬화가 실패한다
// (ValueInstantiationException: passwordVal이 null).
// @JsonCreator/@JsonProperty로 생성자 파라미터와 직렬화된 프로퍼티명을 명시적으로 맞춘다.
class YonaUserDetails @JsonCreator constructor(
    @JsonProperty("id") val id: Long,
    @JsonProperty("loginId") val loginId: String,
    @JsonProperty("password") private val passwordVal: String,
    @JsonProperty("passwordSalt") val passwordSalt: String,
    @JsonProperty("authorities") private val authoritiesVal: Collection<GrantedAuthority>,
    // isEnabled()/isAccountNonLocked()는 state를 다른 의미로(단방향) 파생시킬
    // 뿐이라 그 값들로는 원래의 UserState(ACTIVE/LOCKED/DELETED)를 복원할 수 없다 — state 자체를
    // public 프로퍼티(getState())로 노출해 직렬화/역직렬화가 같은 프로퍼티명("state")을 왕복하게 한다.
    @JsonProperty("state") val state: UserState = UserState.ACTIVE,
    // 법적 컴플라이언스 감사 #4 대응(브루트포스 방어) — YonaAuthenticationProvider가 매 로그인
    // 시도마다 User를 다시 조회하지 않고도 일시 잠금 여부를 판단할 수 있도록 스냅샷으로 들고 있다.
    @JsonProperty("lockedUntil") val lockedUntil: Instant? = null
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = authoritiesVal
    override fun getPassword(): String = passwordVal
    override fun getUsername(): String = loginId
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = state != UserState.LOCKED
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = state != UserState.DELETED
}
