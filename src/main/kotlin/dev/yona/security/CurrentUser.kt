package dev.yona.security

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import org.springframework.security.authentication.AbstractAuthenticationToken
import java.io.Serializable

/**
 * 세션에 저장되는 로그인 사용자의 최소 정보이다.
 *
 * v1.6은 세션에 `userId`, `loginId`, `userName`만 넣고 사용자 객체는 서버 캐시에서 찾았다
 * (`UserApp.addUserInfoToSession`). 같은 이유로 엔티티 대신 직렬화 가능한 값만 담고, 매 요청마다
 * [CurrentUserProvider]가 최신 사용자 정보를 DB에서 읽는다.
 */
data class YonaPrincipal(val userId: Long, val loginId: String?, val name: String?) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * 로그인한 사용자를 나타내는 Spring Security 인증 객체이다. 권한(role)은 쓰지 않는다.
 *
 * @property viaToken 개인용 API 토큰으로 인증되었으면 `true`이다. 화면 레이아웃과 세션 기억(`/-_-api` 홈 별칭이 그린다)이 세션 값을 쓰지 않게 가르는 데 쓴다.
 *   잠기거나 삭제된 사용자의 토큰은 인증 단계에서 거절하므로(`decisions.md` A-206) v1.6처럼 계정 상태를 건너뛰는 예외는 두지 않는다([refactoring-backlog.md] R-04).
 * @property viaCookie 로그인 유지 쿠키로 인증되었으면 `true`이다. v1.6은 요청마다 `Global`이 쿠키 사용자를 요청 캐시(`TOKEN_USER`)에
 *   넣고 `getUserFromContext`가 그 값을 잠금 검사 없이 돌려주므로, 잠기거나 지워진 계정도 쿠키가 유효한 동안 로그인 상태이다.
 *   이런 계정은 세션에는 기록되지 않는다(`addUserInfoToSession`이 건너뛴다).
 */
class YonaAuthentication(
    val principalInfo: YonaPrincipal,
    val viaToken: Boolean = false,
    val viaCookie: Boolean = false,
    /** 개인용 API 토큰으로 인증했으면 그 토큰의 범위이다(추가 개발 항목 X-5). 세션과 쿠키 인증은 `null`이며 범위 검사를 받지 않는다. */
    val scopes: Set<String>? = null,
    /** 개인용 API 토큰으로 인증했으면 그 토큰의 번호이다. 감사 로그(X-9)가 어느 토큰으로 한 일인지 남기는 데 쓴다. */
    val tokenId: Long? = null
) :
    AbstractAuthenticationToken(emptyList()) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any = ""

    override fun getPrincipal(): YonaPrincipal = principalInfo

    /** 사용자 번호의 글이다. OAuth 인가 서버(X-1)가 승인과 인가의 주인 이름으로 쓴다. */
    override fun getName(): String = principalInfo.userId.toString()

    companion object {
        private const val serialVersionUID = 1L

        /** 사용자 엔티티로부터 인증 객체를 만든다. */
        fun of(user: User, viaToken: Boolean = false, viaCookie: Boolean = false, scopes: Set<String>? = null, tokenId: Long? = null) =
            YonaAuthentication(YonaPrincipal(requireNotNull(user.id), user.loginId, user.name), viaToken, viaCookie, scopes, tokenId)
    }
}

/**
 * 이 요청을 보낸 사용자이다. v1.6의 `UserApp.currentUser()`가 돌려주던 `User` 또는 `User.anonymous`에 해당한다.
 *
 * @property user 로그인한 사용자. 익명이면 `null`
 */
data class CurrentUser(val user: User?) {

    /** 로그인하지 않았으면 `true`이다 (v1.6 `isAnonymous`). */
    val isAnonymous: Boolean get() = user == null

    /** 계정이 잠겼거나 지워졌으면 `true`이다 (v1.6 `User.isLocked()`는 `LOCKED || DELETED`이다). */
    val isLocked: Boolean get() = user?.state.isLockedOut()

    companion object {
        /** 로그인하지 않은 사용자이다. */
        val ANONYMOUS = CurrentUser(null)
    }
}

/** 잠겼거나 지워진 계정이면 `true`이다. 이런 계정의 세션과 로그인 유지 쿠키는 쓰지 못한다 (v1.6 `User.isLocked()`). */
fun UserState?.isLockedOut(): Boolean = this == UserState.LOCKED || this == UserState.DELETED
