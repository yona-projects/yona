package dev.yona.web.access

import dev.yona.domain.user.User
import dev.yona.security.CurrentUserProvider
import dev.yona.security.LocalAuthenticator
import dev.yona.security.LoginSessionService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component

/**
 * Git, Subversion HTTP 요청의 사용자를 정한다. v1.6 `BasicAuthAction`과 그 뒤 컨트롤러의 `UserApp.currentUser()`에 해당한다.
 *
 * - Basic 자격이 맞으면 사용자를 세션에 로그인시켜(`addUserInfoToSession`) 응답에 세션 쿠키를 심고 그 사용자로 처리한다.
 * - Basic 자격이 없거나 틀리면 세션, API 토큰, 로그인 유지 쿠키로 식별한 현재 사용자로 되돌아간다. 모두 없으면 익명(`null`)이다.
 *
 * 쿠키를 보내지 않는 git, svn 클라이언트는 요청마다 서버 세션이 하나씩 생긴다(P-10 결정 1).
 */
@Component
class BasicAuthResolver(
    private val authenticator: LocalAuthenticator,
    private val loginSession: LoginSessionService,
    private val currentUserProvider: CurrentUserProvider
) {

    fun resolve(request: HttpServletRequest, response: HttpServletResponse, credentials: BasicCredentials?): User? {
        if (credentials != null) {
            val user = authenticator.authenticateBasic(credentials.loginId, credentials.password)
            if (user != null) {
                loginSession.start(request, response, user)
                return user
            }
        }
        return currentUserProvider.get().user
    }
}
