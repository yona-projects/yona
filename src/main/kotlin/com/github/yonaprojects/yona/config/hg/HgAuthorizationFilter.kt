package com.github.yonaprojects.yona.config.hg

import com.github.yonaprojects.yona.config.git.DeployKeyAuthenticationToken
import com.github.yonaprojects.yona.config.vcs.RepoAccessPolicy
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.regex.Pattern

// yona-wiki P3-12(Mercurial 지원) 2라운드 — GitAuthorizationFilter/SvnAuthorizationFilter와
// 대칭인 Mercurial HTTP 전용 인가 필터. RepoAccessPolicy(findProject/requiresAuth/isMember/
// isGuestUser)를 그대로 재사용하고, 이 필터에만 필요한 것은 (1) URI에서 owner/project를 뽑는
// 정규식과 (2) hg wire protocol 고유의 read/write 판정 로직뿐이다.
//
// **읽기/쓰기 판정**: git처럼 URL 세그먼트나 서비스명 하나로 정해지지 않는다 — hg wire protocol은
// 한 커넥션(하나의 URL 프리픽스) 안에서 v1 `?cmd=<command>` 쿼리 파라미터(대부분 pull류 읽기,
// `unbundle`만 push) 또는 v2 `/api/<namespace>/<ro|rw>/<command>` URL 세그먼트로 명령을 구분한다
// (`HgHttpWireServer`의 doGet/service 참고). 이 필터는 그 두 신호만 보고 판정하며, 실제 push
// 승인/거부(파일 잠금, pretxnchangegroup 훅 등)는 HgController가 등록하는
// registerPreChangegroupHook에서 이미 계산된 이 필터의 판정 결과에 따라 최종 강제된다.
@Component
class HgAuthorizationFilter(
    private val repoAccessPolicy: RepoAccessPolicy
) : OncePerRequestFilter() {

    private val hgUriPattern = Pattern.compile("^/hg/([^/]+)/([^/]+?)(?:/.*)?$")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val uri = request.requestURI
        val matcher = hgUriPattern.matcher(uri)

        if (!matcher.matches()) {
            filterChain.doFilter(request, response)
            return
        }

        val owner = matcher.group(1)
        val projectName = matcher.group(2)

        val project = repoAccessPolicy.findProject(owner, projectName)
        if (project == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Project Not Found")
            return
        }

        // vcs 종류 검증은 RepoAccessPolicy에 없는 Hg 전용 로직 — Git/SVN/SSH 경로와 동일한 패턴으로
        // 이 필터에 남겨둔다(SvnAuthorizationFilter의 vcs 검증과 대칭).
        val vcs = project.vcs?.uppercase()
        if (vcs != "MERCURIAL" && vcs != "HG") {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not a Mercurial project")
            return
        }

        val isWriteRequest = isWriteRequest(request)
        val requiresAuth = repoAccessPolicy.requiresAuth(project, isWriteRequest)

        if (requiresAuth) {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication == null || !authentication.isAuthenticated || isAnonymous(authentication)) {
                response.setHeader("WWW-Authenticate", "Basic realm=\"Mercurial Repository\"")
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                return
            }

            // yona-wiki P3-03 Step2와 동일한 Deploy Key 분기(Git/SVN 필터와 대칭).
            if (authentication is DeployKeyAuthenticationToken) {
                val deployKey = authentication.deployKey
                if (deployKey.project?.id != project.id) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
                    return
                }
                if (isWriteRequest && deployKey.readOnly) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
                    return
                }
                filterChain.doFilter(request, response)
                return
            }

            val loginId = authentication.name
            if (!repoAccessPolicy.isMember(project, loginId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
                return
            }
        } else {
            // yona의 "!user.isGuest" 대응: PUBLIC 프로젝트라도 게스트 계정으로 인증된 요청은 거부한다.
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication != null && authentication.isAuthenticated && !isAnonymous(authentication)) {
                if (repoAccessPolicy.isGuestUser(authentication.name)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
                    return
                }
            }
        }

        filterChain.doFilter(request, response)
    }

    // 코디네이터 리뷰(2026-09-08)에서 실측으로 발견/수정한 보안 결함 — 최초 구현은 v1의
    // `unbundle`만 쓰기로 분류하고 `pushkey`를 읽기로 잘못 분류했다. 실제 hg 서버는 pushkey도
    // push 권한이 필요한 명령이다(mercurial/hgweb/common.py의 checkauthz가 "push requires POST
    // request"로 강제하는 대상이 unbundle과 pushkey 둘 다 — hg4j의 HgRemoteClient.java 934행
    // 주석이 실제 hg 소스를 인용해 이 사실을 이미 확인해뒀다). pushkey는 서버 저장소 상태를
    // 실제로 변경한다(Wire2Commands.applyPushkey()가 북마크 파일을 직접 덮어씀 — 북마크
    // 생성/이동/삭제) — 읽기로 잘못 분류하면 PRIVATE 저장소의 비멤버나 익명 사용자가
    // `GET /hg/{owner}/{project}?cmd=pushkey&namespace=bookmarks&key=<name>&old=&new=<hash>`
    // 하나로 인가 검사를 완전히 우회해 북마크를 조작할 수 있었다(쓰기 인가 없이).
    //
    // hg wire protocol v1: `unbundle`/`pushkey`(둘 다 push)만 쓰기, 그 외(capabilities/batch/
    // heads/known/branchmap/getbundle/lookup/...)는 읽기. v2: URL의
    // `/api/<namespace>/<ro|rw>/<command>` 세그먼트가 명시적으로 rw/ro를 알려준다
    // (HgHttpWireServer.service() 참고).
    private fun isWriteRequest(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        val apiIdx = uri.indexOf("/api/")
        if (apiIdx != -1) {
            val segments = uri.substring(apiIdx + "/api/".length).split("/")
            if (segments.size >= 2) {
                return segments[1] == "rw"
            }
        }

        val query = request.queryString ?: return false
        for (pair in query.split("&")) {
            val eq = pair.indexOf('=')
            if (eq == -1) continue
            val key = pair.substring(0, eq)
            val value = pair.substring(eq + 1)
            if (key == "cmd") {
                return value == "unbundle" || value == "pushkey"
            }
        }
        return false
    }

    private fun isAnonymous(authentication: Authentication): Boolean {
        return authentication is AnonymousAuthenticationToken
    }
}
