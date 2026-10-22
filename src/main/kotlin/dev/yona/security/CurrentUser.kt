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
 * @property viaToken API 토큰 헤더로 인증되었으면 `true`이다. v1.6은 이 경로에서 계정 상태(LOCKED)를
 *   확인하지 않는다([refactoring-backlog.md] R-04).
 */
class YonaAuthentication(val principalInfo: YonaPrincipal, val viaToken: Boolean = false) :
    AbstractAuthenticationToken(emptyList()) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any = ""

    override fun getPrincipal(): YonaPrincipal = principalInfo

    companion object {
        private const val serialVersionUID = 1L

        /** 사용자 엔티티로부터 인증 객체를 만든다. */
        fun of(user: User, viaToken: Boolean = false) =
            YonaAuthentication(YonaPrincipal(requireNotNull(user.id), user.loginId, user.name), viaToken)
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

    /** 계정이 잠겼으면 `true`이다. */
    val isLocked: Boolean get() = user?.state == UserState.LOCKED

    companion object {
        /** 로그인하지 않은 사용자이다. */
        val ANONYMOUS = CurrentUser(null)
    }
}
