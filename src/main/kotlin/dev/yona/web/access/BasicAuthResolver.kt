package dev.yona.web.access

import dev.yona.config.YonaProperties
import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.security.ApiRequirement
import dev.yona.security.ApiScopeRules
import dev.yona.security.CurrentUserProvider
import dev.yona.security.LocalAuthenticator
import dev.yona.security.LoginSessionService
import dev.yona.security.TokenFailureLimiter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component

/**
 * Basic 인증의 결과이다(Git, Subversion HTTP).
 *
 * @property user 인증한 사용자. 인증하지 못했으면 세션, 로그인 유지 쿠키로 식별한 현재 사용자이고 모두 없으면 `null`(익명)이다
 * @property tokenScopes 개인용 API 토큰으로 인증했으면 그 토큰의 범위이다. 비밀번호나 세션은 `null`이며 범위 검사를 받지 않는다
 * @property blocked 같은 주소에서 인증에 연달아 실패해 막혔으면 `true`이다(429)
 */
class BasicIdentity(val user: User?, val tokenScopes: Set<String>? = null, val blocked: Boolean = false)

/**
 * Git, Subversion HTTP 요청의 사용자를 정한다. v1.6 `BasicAuthAction`과 그 뒤 컨트롤러의 `UserApp.currentUser()`에 해당한다.
 *
 * 비밀번호 칸의 값이 개인용 API 토큰이면 토큰으로 인증한다(추가 개발 항목 X-5, `decisions.md` D-36):
 * - 새 형식 토큰(`yona_pat_` 접두사)은 확인에 실패해도 **비밀번호로 넘기지 않고** 거절한다. 이관한 옛 토큰의 형식(44자 Base64)은 비밀번호와 겹칠 수 있어
 *   토큰 확인에 실패하면 비밀번호 확인으로 넘어간다.
 * - 사용자 이름(로그인 ID)이 토큰 주인과 같아야 한다. 토큰 인증은 세션을 만들지 않으며 범위(`repo`, 공개 프로젝트는 `public_repo`)를 컨트롤러가 프로젝트를 읽은 뒤
 *   [allowsRepository]로 확인한다.
 *
 * 그 밖의 값은 비밀번호로 확인한다(설정 키 `yona.git-http-password-auth`가 `false`이면 받지 않는다). 비밀번호가 맞으면 사용자를 세션에 로그인시켜(`addUserInfoToSession`)
 * 응답에 세션 쿠키를 심고 그 사용자로 처리한다. 자격이 없거나 틀리면 세션, 로그인 유지 쿠키로 식별한 현재 사용자로 되돌아간다. 틀린 자격은 주소별로 세어
 * 5회 실패하면 15분 막는다([TokenFailureLimiter]).
 *
 * 쿠키를 보내지 않는 git, svn 클라이언트는 요청마다 서버 세션이 하나씩 생긴다(P-10 결정 1).
 */
@Component
class BasicAuthResolver(
    private val authenticator: LocalAuthenticator,
    private val loginSession: LoginSessionService,
    private val currentUserProvider: CurrentUserProvider,
    private val apiTokens: ApiTokenService,
    private val failures: TokenFailureLimiter,
    private val properties: YonaProperties,
    private val audit: dev.yona.domain.audit.AuditBoundary
) {

    /** 사용자만 필요한 호출을 위한 단축이다. 막힌 요청과 범위는 [identify]를 쓴다. */
    fun resolve(request: HttpServletRequest, response: HttpServletResponse, credentials: BasicCredentials?): User? = identify(request, response, credentials).user

    fun identify(request: HttpServletRequest, response: HttpServletResponse, credentials: BasicCredentials?): BasicIdentity {
        if (credentials == null) return BasicIdentity(currentUserProvider.get().user)
        val address = request.remoteAddr.orEmpty()
        if (failures.isBlocked(address)) {
            audit.blocked(address, "git_http")
            return BasicIdentity(null, blocked = true)
        }

        val password = credentials.password
        val newToken = NEW_TOKEN.matches(password)
        if (newToken || LEGACY_TOKEN.matches(password)) {
            val token = apiTokens.authenticate(password)
            val owner = token?.user
            if (token != null && owner != null && owner.loginId.equals(credentials.loginId, ignoreCase = true)) {
                failures.recordSuccess(address)
                return BasicIdentity(owner, ApiScopes.parse(token.scopes))
            }
            if (newToken) {
                failures.recordFailure(address)
                audit.basicFailure(credentials.loginId, "git_http", "invalid_token")
                return BasicIdentity(currentUserProvider.get().user)
            }
        }

        // 비밀번호 인증을 끈 사이트는 비밀번호를 받지 않는다. 설정을 아직 고치지 못한 사용자가 토큰을 만든 뒤에도 막혀 있지 않도록 실패로 세지 않는다
        if (!properties.gitHttpPasswordAuth) return BasicIdentity(currentUserProvider.get().user)
        val user = authenticator.authenticateBasic(credentials.loginId, password)
        if (user != null) {
            failures.recordSuccess(address)
            loginSession.start(request, response, user)
            return BasicIdentity(user)
        }
        failures.recordFailure(address)
        audit.basicFailure(credentials.loginId, "git_http", "invalid_credentials")
        return BasicIdentity(currentUserProvider.get().user)
    }

    /**
     * 토큰으로 인증한 요청이 [project]의 저장소에 접근할 수 있는지이다. `repo`는 모든 프로젝트, `public_repo`는 공개(`PUBLIC`) 프로젝트만이다.
     * 비밀번호나 세션으로 인증한 요청은 범위 검사를 받지 않는다.
     */
    fun allowsRepository(identity: BasicIdentity, project: Project): Boolean =
        identity.tokenScopes == null || ApiScopeRules.allows(ApiRequirement.Repo, identity.tokenScopes, project.projectScope == ProjectScope.PUBLIC)

    private companion object {
        /** 새 토큰: `yona_pat_` + 임의 40자 */
        val NEW_TOKEN = Regex("^yona_pat_[0-9A-Za-z]{40}$")

        /** 이관한 옛 토큰: 32바이트의 Base64(44자) */
        val LEGACY_TOKEN = Regex("^[A-Za-z0-9+/]{43}=$")
    }
}
