package dev.yona.web.access

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.security.ApiRequirement
import dev.yona.security.ApiScopeRules
import dev.yona.security.CurrentUserProvider
import dev.yona.security.YonaAuthentication
import org.springframework.security.core.context.SecurityContextHolder
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
 * @property passwordRefused 비밀번호 칸에 토큰이 아닌 값을 보내 거부했으면 `true`이다. 컨트롤러가 401 본문에 토큰을 쓰라고 안내한다(`decisions.md` D-70)
 */
class BasicIdentity(val user: User?, val tokenScopes: Set<String>? = null, val blocked: Boolean = false, val passwordRefused: Boolean = false) {

    companion object {
        /** 비밀번호 칸에 토큰이 아닌 값을 보낸 요청의 401 본문이다. */
        const val PASSWORD_REFUSED_MESSAGE = "Password authentication is not supported. Use a personal access token as the password."
    }
}

/**
 * Git, Subversion HTTP 요청의 사용자를 정한다. v1.6 `BasicAuthAction`과 그 뒤 컨트롤러의 `UserApp.currentUser()`에 해당한다.
 *
 * **비밀번호 칸에는 개인용 API 토큰만 받는다**(추가 개발 항목 X-5, `decisions.md` D-36과 D-70). 비밀번호는 항상 거부하며 v1.6과 달리 Basic 인증으로 웹 세션을 만들지도 않는다.
 * - 새 형식 토큰(`yona_pat_` 접두사)이나 이관한 옛 토큰의 형식(44자 Base64)이면 토큰으로 인증한다. 사용자 이름(로그인 ID)이 토큰 주인과 같아야 한다. 토큰 인증은 세션을 만들지 않으며
 *   범위(`repo`, 공개 프로젝트는 `public_repo`)를 컨트롤러가 프로젝트를 읽은 뒤 [allowsRepository]로 확인한다.
 * - 토큰 형식인데 맞지 않으면 토큰 추측이므로 실패로 세어 주소별로 5회 실패하면 15분 막는다([TokenFailureLimiter]).
 * - 그 밖의 값은 비밀번호로 보고 거부한다. 비밀번호를 확인하지 않아 무차별 대입 위험이 없고, 아직 바꾸지 못한 사용자가 토큰으로 바꾼 뒤에도 막히지 않도록 실패로 세지 않는다.
 *
 * 인증하지 못한 요청은 세션, 로그인 유지 쿠키로 식별한 현재 사용자(없으면 익명)로 처리한다. 공개 프로젝트의 읽기는 익명으로도 된다.
 */
@Component
class BasicAuthResolver(
    private val currentUserProvider: CurrentUserProvider,
    private val apiTokens: ApiTokenService,
    private val failures: TokenFailureLimiter,
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
        if (!newToken && !LEGACY_TOKEN.matches(password)) {
            // 비밀번호는 받지 않는다(D-70). 확인하지 않으므로 실패로 세지 않는다
            return BasicIdentity(currentUserProvider.get().user, passwordRefused = true)
        }
        val token = apiTokens.authenticate(password)
        val owner = token?.user
        if (token != null && owner != null && owner.loginId.equals(credentials.loginId, ignoreCase = true)) {
            failures.recordSuccess(address)
            val scopes = ApiScopes.parse(token.scopes)
            // 이 요청에서만 토큰의 사용자로 본다(세션에 저장하지 않는다). 푸시 훅의 감사 기록이 행위자(토큰과 범위)를 남길 수 있다
            SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext().also { it.authentication = YonaAuthentication.of(owner, viaToken = true, scopes = scopes, tokenId = token.id) })
            return BasicIdentity(owner, scopes)
        }
        failures.recordFailure(address)
        audit.basicFailure(credentials.loginId, "git_http", "invalid_token")
        return BasicIdentity(currentUserProvider.get().user)
    }

    /**
     * 토큰으로 인증한 요청이 [project]의 저장소에 접근할 수 있는지이다. `repo`는 모든 프로젝트, `public_repo`는 공개(`PUBLIC`) 프로젝트만이다.
     * 세션으로 식별한 요청은 범위 검사를 받지 않는다.
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
