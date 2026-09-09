package com.github.yonaprojects.yona.config.svn

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

@Component
class SvnAuthorizationFilter(
    // findProject/requiresAuth/isMember/isGuestUser는 GitAuthorizationFilter가 쓰는
    // RepoAccessPolicy와 로직을 공유한다. project.vcs 검증과 SVN 고유의 쓰기요청 판정
    // (HTTP 메서드 allowlist)만 RepoAccessPolicy에 없는 SVN 전용 로직이라 이 필터에 남겨둔다.
    private val repoAccessPolicy: RepoAccessPolicy
) : OncePerRequestFilter() {

    private val svnUriPattern = Pattern.compile("^/svn/([^/]+)/([^/]+?)(?:/.*)?$")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val uri = request.requestURI
        val matcher = svnUriPattern.matcher(uri)

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

        // vcs 종류 검증은 RepoAccessPolicy에 없는 SVN 전용 로직 — Git/SSH 경로는 project.vcs를
        // 아예 확인하지 않으므로 이 필터에 남겨둔다.
        val vcs = project.vcs?.lowercase()
        if (vcs != "subversion" && vcs != "svn") {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not a Subversion project")
            return
        }

        val isWriteRequest = isWriteRequest(request)
        val requiresAuth = repoAccessPolicy.requiresAuth(project, isWriteRequest)

        if (requiresAuth) {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication == null || !authentication.isAuthenticated || isAnonymous(authentication)) {
                response.setHeader("WWW-Authenticate", "Basic realm=\"SVN Repository\"")
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                return
            }

            // GitAuthorizationFilter의 Deploy Key 분기와 동일한 로직. 이 분기가 없으면,
            // DeployKeyAuthenticationProvider가 SecurityConfig에 전역으로 등록돼 있는 탓에
            // SVN 요청도 Deploy Key로 "인증"까지는 통과하지만 authentication.name이
            // "x-access-deploykey" 고정 문자열이라 isMember()가 항상 false를 반환해 무조건
            // 403이 나는 죽은 기능이 된다(웹 UI가 vcs 종류로 Deploy Key 생성을 막지 않으므로
            // 실제로 발생할 수 있는 함정).
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
            // (완전한 익명 요청은 애초에 guest로 분류되지 않으므로 영향받지 않는다.)
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
        val method = request.method.uppercase()
        val readMethods = setOf("GET", "PROPFIND", "OPTIONS", "REPORT", "HEAD")
        return !readMethods.contains(method)
    }

    private fun isAnonymous(authentication: Authentication): Boolean {
        return authentication is AnonymousAuthenticationToken
    }
}
