package com.github.yonaprojects.yona.config.git

import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.RecentProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.regex.Pattern

// yona GitApp.java의 service()에서 user.visits(project)를 부르는 부분 대응. git 프로토콜
// (clone/fetch/push)로만 접근하는 사용자는 웹 UI를 거치지 않아 "최근 방문 프로젝트"에 전혀
// 기록되지 않던 결손을 해결한다. yona는 advertise 단계(GET .../info/refs)가 아니라 실제 RPC
// (POST git-upload-pack/git-receive-pack) 처리 단계에서만 방문을 기록하므로 동일하게 재현한다.
@Component
class GitProjectVisitRecorder(
    private val projectService: ProjectService,
    private val userRepository: UserRepository,
    private val recentProjectRepository: RecentProjectRepository
) {
    private val gitUriPattern = Pattern.compile("^/(git|git-lfs)/([^/]+)/([^/]+?)(?:\\.git)?(?:/.*)?$")

    fun recordIfApplicable(request: HttpServletRequest) {
        if (!isRpcRequest(request)) return

        val matcher = gitUriPattern.matcher(request.requestURI)
        if (!matcher.matches()) return

        val owner = matcher.group(2)
        val projectName = matcher.group(3)
        val project = projectService.findByOwnerAndName(owner, projectName) ?: return

        val user = resolveCurrentUser() ?: return
        recentProjectRepository.recordVisit(user, project)
    }

    private fun isRpcRequest(request: HttpServletRequest): Boolean {
        return request.method == "POST" &&
            (request.requestURI.endsWith("/git-upload-pack") || request.requestURI.endsWith("/git-receive-pack"))
    }

    private fun resolveCurrentUser(): User? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        if (!authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) {
            return null
        }
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }
}
