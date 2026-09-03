package com.github.yonaprojects.yona.config.git

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.user.UserRepository
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
    private val projectService: ProjectService,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl
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
        val projectName = matcher.group(3)

        val project = projectService.findByOwnerAndName(owner, projectName)
        if (project == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Project Not Found")
            return
        }

        val isWriteRequest = isWriteRequest(request)

        // yona AccessControl READ 규칙(SvnAuthorizationFilter, P1-23와 동일하게) 대응 (P1-45):
        // PROTECTED도 PUBLIC과 동일하게 인증 없이 clone 가능했던 것을 PRIVATE와 같이 인증을 요구하도록 수정.
        // 조직 그룹멤버 우회는 P1-64에서 isMember()에 추가.
        val requiresAuth = project.projectScope != ProjectScope.PUBLIC
                || project.isCodeAccessibleMemberOnly
                || isWriteRequest

        if (requiresAuth) {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication == null || !authentication.isAuthenticated || isAnonymous(authentication)) {
                response.setHeader("WWW-Authenticate", "Basic realm=\"Git Repository\"")
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                return
            }

            val loginId = authentication.name
            if (!isMember(project, loginId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden")
                return
            }
        } else {
            // yona의 "!user.isGuest" 대응: PUBLIC 프로젝트라도 게스트 계정으로 인증된 요청은 거부한다.
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication != null && authentication.isAuthenticated && !isAnonymous(authentication)) {
                if (isGuestUser(authentication.name)) {
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

    // yona AccessControl.isAllowedIfGroupMember() 대응 (P1-64). 직접 멤버가 아니어도 프로젝트가 속한
    // 조직의 구성원이면(PUBLIC/PROTECTED에 한해) 접근을 허용한다.
    private fun isMember(project: Project, loginId: String): Boolean {
        val projectId = project.id ?: return false
        if (projectService.isMember(projectId, loginId)) {
            return true
        }
        val user = userRepository.findByLoginId(loginId).orElse(null) ?: return false
        return accessControl.isAllowedIfGroupMember(project, user)
    }

    private fun isGuestUser(loginId: String): Boolean {
        return userRepository.findByLoginId(loginId).map { it.isGuest }.orElse(false)
    }
}
