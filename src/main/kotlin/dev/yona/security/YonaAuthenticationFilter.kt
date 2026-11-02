package dev.yona.security

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청을 보낸 사용자를 찾아 보안 컨텍스트에 채우는 필터이다. v1.6 `UserApp.currentUser()`의 확인 순서와 같다.
 *
 * 1. 세션: Spring Security가 세션에서 복원한 인증 정보. 사용자가 사라졌거나 LOCKED이거나 서버 캐시([SessionUserCache])에 없으면 무효로 보고 비운다
 *    (v1.6 `getUserFromSession`이 캐시에 없는 사용자를 무효 세션으로 처리한다).
 * 2. API 토큰: `/-_-api` 아래 경로에서만 `Authorization: token`, `Bearer`, `Yona-Token` 헤더를 본다(추가 개발 항목 X-5, D-36). 이 경로에서는 세션과 쿠키를 무시하고
 *    토큰만 본다. 세션에는 기록하지 않는다. 그 밖의 경로는 토큰을 인정하지 않는다.
 * 3. remember-me 쿠키 `yobi.token`(`로그인ID:해시된패스워드`). 성공하면 세션에도 기록한다.
 *    값이 잘못되었으면 쿠키를 지운다. 잠기거나 지워진 계정은 v1.6처럼 쿠키가 유효한 동안 로그인 상태로 두되 세션에는 기록하지 않는다.
 *
 * 쿠키에 해시된 패스워드가 그대로 들어가는 것은 v1.6의 설계이며 그대로 따른다([refactoring-backlog.md] R-05).
 */
class YonaAuthenticationFilter(
    private val users: UserRepository,
    private val sessionUsers: SessionUserCache,
    private val apiTokens: ApiTokenService,
    private val failures: TokenFailureLimiter,
    private val audit: dev.yona.domain.audit.AuditBoundary,
    private val gate: TwoFactorGate,
    private val oauthTokens: dev.yona.domain.oauth2server.OAuthAccessTokenService
) : OncePerRequestFilter() {

    private val securityContextRepository = HttpSessionSecurityContextRepository()

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (isApiPath(request)) {
            // 외부 API(`/-_-api/v1`)는 토큰 전용이다. 세션과 쿠키는 무시하고 토큰만 본다(추가 개발 항목 X-5, `decisions.md` D-36)
            if (!authenticateApi(request, response)) return
        } else if (dev.yona.mcp.McpAccessFilter.isMcpPath(request)) {
            // MCP 서버(`/mcp`)는 OAuth 토큰 전용이고 인증은 McpAccessFilter가 맡는다. 세션과 쿠키는 보지 않는다(X-6, D-72)
            SecurityContextHolder.clearContext()
        } else if (!hasValidSession()) {
            // 그 밖의 경로는 세션 전용이다. 토큰 헤더는 인정하지 않는다(Git, SVN의 HTTP 접근은 Basic 해결기가 따로 처리한다)
            authenticateByRememberMeCookie(request, response)
        }
        chain.doFilter(request, response)
    }

    /** 요청이 외부 API 경로(`/-_-api` 자신과 그 아래)인지이다. 홈의 별칭 `/-_-api`도 같은 이름 공간이라 세션을 보지 않는다. */
    private fun isApiPath(request: HttpServletRequest): Boolean {
        val path = request.requestURI.removePrefix(request.contextPath.orEmpty())
        return path == API_PREFIX || path.startsWith("$API_PREFIX/")
    }

    /**
     * 외부 API 요청을 토큰으로 인증한다. 세션이 복원한 인증 정보는 지운다(토큰 헤더가 있어도 세션이 이기던 v1.6의 구멍을 막는다).
     * 토큰이 없으면 익명이다. 토큰 인증에 연달아 실패한 주소는 막는다(429). 토큰이 맞으면 토큰의 범위를 인증 객체에 싣는다. 응답을 끝냈으면 `false`이다.
     */
    private fun authenticateApi(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        SecurityContextHolder.clearContext()
        val value = ApiTokenHeaders.extract(request) ?: return true
        val address = request.remoteAddr.orEmpty()
        if (failures.isBlocked(address)) {
            audit.blocked(address, "api")
            response.status = 429
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write("{\"message\":\"too many failed token attempts\"}")
            return false
        }
        if (value.startsWith(dev.yona.domain.oauth2server.OAuthAccessTokenService.PREFIX)) {
            // OAuth 액세스 토큰(X-1): 개인용 토큰과 같은 실패 제한과 범위 검사를 받는다
            val grant = oauthTokens.authenticate(value)
            if (grant == null) {
                failures.recordFailure(address)
                audit.tokenFailure("oauth")
                return true
            }
            // 토큰의 대상(RFC 8707): `resource` 없이 발급되었거나 외부 API를 가리켜 발급된 토큰만 받는다. 다른 자원(MCP)용 토큰은 여기서 쓸 수 없다
            if (!grant.allows(dev.yona.oauth2server.ProtectedResources.api(request), default = true)) return true
            failures.recordSuccess(address)
            val oauthContext = SecurityContextHolder.createEmptyContext()
            oauthContext.authentication = YonaAuthentication.of(grant.user, viaToken = true, scopes = grant.scopes, oauthClientRowId = grant.clientRowId)
            SecurityContextHolder.setContext(oauthContext)
            return true
        }
        val token = apiTokens.authenticate(value)
        val owner = token?.user
        if (token == null || owner == null) {
            failures.recordFailure(address)
            audit.tokenFailure("api")
            return true
        }
        failures.recordSuccess(address)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = YonaAuthentication.of(owner, viaToken = true, scopes = ApiScopes.parse(token.scopes), tokenId = token.id)
        SecurityContextHolder.setContext(context)
        return true
    }

    /** 세션 인증 정보가 아직 유효한지 확인한다. 유효하지 않으면 보안 컨텍스트를 비운다. */
    private fun hasValidSession(): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication as? YonaAuthentication ?: return false
        val user = users.findById(authentication.principal.userId).orElse(null)
        if (user == null || user.state.isLockedOut() || !sessionUsers.contains(authentication.principal.userId)) {
            SecurityContextHolder.clearContext()
            return false
        }
        return true
    }

    private fun authenticateByRememberMeCookie(request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val cookie = request.cookies?.firstOrNull { it.name == REMEMBER_ME_COOKIE } ?: return false
        val parts = cookie.value.split(TOKEN_SEPARATOR).filter { it.isNotEmpty() }
        val user = if (parts.size == TOKEN_LENGTH) users.findByLoginId(parts[0]) else null
        if (user == null || user.password != parts[1]) {
            discardRememberMeCookie(response)
            return false
        }
        // 2단계 인증을 켠 사용자는 알려진 기기 쿠키가 함께 있을 때만 이 쿠키로 인정한다(X-2, D-69). 쿠키는 지우지 않고 익명으로 둔다
        if (!gate.passes(user, request)) return false
        val authentication = YonaAuthentication.of(user, viaCookie = true)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        if (!user.state.isLockedOut()) {
            securityContextRepository.saveContext(context, request, response)
            // v1.6 initTokenUser: 세션이 비어 있는 쿠키 사용자는 addUserInfoToSession으로 서버 캐시에도 들어간다
            sessionUsers.remember(requireNotNull(user.id))
        }
        return true
    }

    companion object {
        /** remember-me 쿠키 이름이다 (v1.6 `UserApp.TOKEN`). */
        const val REMEMBER_ME_COOKIE = "yobi.token"

        /** 쿠키 값의 구분자이다 (v1.6 `TOKEN_SEPARATOR`). */
        const val TOKEN_SEPARATOR = ":"
        private const val TOKEN_LENGTH = 2

        /** remember-me 쿠키의 유효 기간(초)이다. 30일이다 (v1.6 `MAX_AGE`). */
        const val REMEMBER_ME_MAX_AGE = 30 * 24 * 60 * 60

        /** 개인 API 토큰 헤더 이름이다 (v1.6 `USER_TOKEN_HEADER`). */
        const val TOKEN_HEADER = ApiTokenHeaders.YONA_TOKEN_HEADER

        /** 토큰 전용 외부 API의 경로 접두사이다(`/-_-api/v1`을 포함한 그 아래 전부). */
        const val API_PREFIX = "/-_-api"

        /** remember-me 쿠키를 즉시 만료시킨다. */
        fun discardRememberMeCookie(response: HttpServletResponse) {
            response.addCookie(Cookie(REMEMBER_ME_COOKIE, "").apply {
                maxAge = 0
                path = "/"
            })
        }
    }
}
