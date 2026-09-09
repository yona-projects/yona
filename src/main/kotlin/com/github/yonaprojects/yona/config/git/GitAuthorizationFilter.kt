package com.github.yonaprojects.yona.config.git

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

@Component
class GitAuthorizationFilter(
    // 접근 판정 로직(requiresAuth/isMember/isGuestUser)을 RepoAccessPolicy로 추출해
    // SshAuthServiceImpl(SSH 경로)/SvnAuthorizationFilter(SVN HTTP)와 공유한다(기존
    // GitAccessPolicy를 VCS 중립적인 이름/패키지로 이동). 동작은 이전과 동일(순수 리팩터링).
    private val repoAccessPolicy: RepoAccessPolicy
) : OncePerRequestFilter() {

    private val gitUriPattern = Pattern.compile("^/(git|git-lfs)/([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val uri = request.requestURI
        val matcher = gitUriPattern.matcher(uri)

        if (!matcher.matches()) {
            filterChain.doFilter(request, response)
            return
        }

        val owner = matcher.group(2)
        val rawProjectName = matcher.group(3)
        // 위키 저장소(P3-42)는 "<owner>/<project>.wiki.git"로 물리 디렉터리가 별도지만, 접근 권한은
        // 그 프로젝트의 코드 저장소와 완전히 동일해야 한다(요구사항 6번 — 위키 쓰기 = 프로젝트
        // 쓰기 권한). ".wiki" 접미어를 떼어 실제 프로젝트를 찾는다 — 안 그러면 "<project>.wiki"라는
        // 이름의 프로젝트가 없어 매번 404가 나 clone/push 자체가 불가능해진다.
        val projectName = rawProjectName.removeSuffix(".wiki")

        val project = repoAccessPolicy.findProject(owner, projectName)
        if (project == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Project Not Found")
            return
        }

        val isWriteRequest = isWriteRequest(request)
        val requiresAuth = repoAccessPolicy.requiresAuth(project, isWriteRequest)

        if (requiresAuth) {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication == null || !authentication.isAuthenticated || isAnonymous(authentication)) {
                response.setHeader("WWW-Authenticate", "Basic realm=\"Git Repository\"")
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                return
            }

            // Deploy Key(저장소 스코프 자격증명)는 loginId 기반 멤버십 검사 대상이 아니다.
            // project.id가 정확히 일치하는 저장소에만 접근을 허용하고(다른 프로젝트 스코프로
            // 발급된 Deploy Key로는 이 프로젝트에 절대 접근할 수 없다), read_only 플래그가 켜져
            // 있으면 쓰기 요청을 거부한다.
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

    private fun isWriteRequest(request: HttpServletRequest): Boolean {
        val uri = request.requestURI
        val service = request.getParameter("service")

        return "git-receive-pack" == service
                || uri.endsWith("/git-receive-pack")
                || "PUT" == request.method
    }

    private fun isAnonymous(authentication: Authentication): Boolean {
        return authentication is AnonymousAuthenticationToken
    }
}
