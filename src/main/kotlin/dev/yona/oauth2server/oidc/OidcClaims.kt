package dev.yona.oauth2server.oidc

import dev.yona.domain.user.User
import dev.yona.security.AvatarService
import org.springframework.stereotype.Component

/**
 * 사용자의 OIDC 클레임이다(추가 개발 항목 X-40, `decisions.md` D-77). 범위가 정한 만큼만 낸다: `profile` → `name`, `preferred_username`(로그인 ID), `picture`, `updated_at`,
 * `email` → `email`. `sub`은 사용자 번호(불변)이다. 기본 이메일에는 인증 표시가 없어 `email_verified`는 내지 않고, 조직 소속 같은 Yona 고유 클레임도 내지 않는다.
 * ID 토큰과 사용자 정보 주소가 같은 값을 쓴다.
 */
@Component
class OidcClaims(private val avatars: AvatarService) {

    /** [user]의 클레임을 [scopes]에 맞게 만든다. [base]는 서버 주소(출처와 컨텍스트 경로)이고 상대 주소의 프로필 사진을 절대 주소로 바꾸는 데 쓴다. */
    fun of(user: User, scopes: Set<String>, base: String): Map<String, Any> {
        val claims = LinkedHashMap<String, Any>()
        claims["sub"] = requireNotNull(user.id).toString()
        if ("profile" in scopes) {
            user.name?.let { claims["name"] = it }
            user.loginId?.let { claims["preferred_username"] = it }
            avatars.avatarUrl(user).takeIf { it.isNotBlank() }?.let { claims["picture"] = if (it.startsWith("/")) base + it else it }
            (user.lastStateModifiedDate ?: user.createdDate)?.let { claims["updated_at"] = it.time / 1000 }
        }
        if ("email" in scopes) user.email?.let { claims["email"] = it }
        return claims
    }

    companion object {
        /** 이 서버가 내는 클레임 이름이다(발견 문서의 `claims_supported`). */
        val SUPPORTED = listOf("sub", "name", "preferred_username", "picture", "updated_at", "email")
    }
}
