package com.github.yonaprojects.yona.config.svn

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
class SvnAuthorizationFilter(
    private val projectService: ProjectService,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl
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

        val project = projectService.findByOwnerAndName(owner, projectName)
        if (project == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Project Not Found")
            return
        }

        val vcs = project.vcs?.lowercase()
        if (vcs != "subversion" && vcs != "svn") {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not a Subversion project")
            return
        }

        val isWriteRequest = isWriteRequest(request)

        // yona AccessControl READ 규칙(project.isPublic() && !user.isGuest || user.isMemberOf(project) || ...
        // || isAllowedIfGroupMember(...)) 대응 (P1-23/P1-64): PROTECTED도 PUBLIC과 동일하게 인증 없이
        // 열람 가능했던 것을 PRIVATE와 같이 인증을 요구하도록 수정. 조직 그룹멤버 우회는 P1-64에서
        // isMember()에 추가.
        val requiresAuth = project.projectScope != ProjectScope.PUBLIC
                || project.isCodeAccessibleMemberOnly
                || isWriteRequest

        if (requiresAuth) {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication == null || !authentication.isAuthenticated || isAnonymous(authentication)) {
                response.setHeader("WWW-Authenticate", "Basic realm=\"SVN Repository\"")
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
            // (완전한 익명 요청은 애초에 guest로 분류되지 않으므로 영향받지 않는다.)
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
        val method = request.method.uppercase()
        val readMethods = setOf("GET", "PROPFIND", "OPTIONS", "REPORT", "HEAD")
        return !readMethods.contains(method)
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
