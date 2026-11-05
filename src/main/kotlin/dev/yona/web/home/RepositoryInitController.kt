package dev.yona.web.home

import dev.yona.domain.project.ProjectRepository
import dev.yona.repository.RepositoryService
import dev.yona.web.RedirectSupport
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * 모든 프로젝트의 저장소를 지우고 빈 저장소로 다시 만든다. v1.6 `Application.init`, `makeTestRepository`(`app/controllers/Application.java:106-126`)에 해당한다.
 *
 * v1.6의 개발용 경로이다. v1.6은 접근 제한이 없어 누구나 호출하면 모든 프로젝트의 기존 커밋과 브랜치가 사라졌다([refactoring-backlog.md] R-66).
 * 선생님 결정으로 **서버와 같은 컴퓨터(루프백 주소 `127.0.0.1`, `::1`)에서 직접 온 요청만** 받고 그 밖에는 403이다(A-366).
 * 한 프로젝트에서 실패해도 오류를 기록하고 다음 프로젝트로 넘어간다. 끝나면 홈으로 보낸다.
 */
@Controller
class RepositoryInitController(
    private val projects: ProjectRepository,
    private val repositories: RepositoryService
) {

    @GetMapping("/_init")
    fun init(request: HttpServletRequest, response: HttpServletResponse): Any {
        if (!isLoopback(request)) return dev.yona.web.error.ErrorViews.forbidden()
        for (project in projects.findAll()) {
            log.debug("makeTestRepository: {}", project.name)
            try {
                repositories.createRepository(project)
            } catch (e: Exception) {
                log.warn("저장소를 다시 만들지 못했습니다: {}/{}", project.owner, project.name, e)
            }
        }
        return RedirectSupport.see(request, response, "/")
    }

    /**
     * 요청한 쪽의 주소가 서버와 같은 컴퓨터(루프백)이면 `true`이다. 같은 컴퓨터의 리버스 프록시를 거치면 연결 주소가 프록시의 루프백이 되어
     * 바깥 요청도 루프백으로 보이므로, 프록시를 거쳤다는 헤더(`X-Forwarded-For`, `Forwarded`)가 있으면 받지 않는다.
     */
    private fun isLoopback(request: HttpServletRequest): Boolean {
        if (request.getHeader("X-Forwarded-For") != null || request.getHeader("Forwarded") != null) return false
        return try {
            java.net.InetAddress.getByName(request.remoteAddr).isLoopbackAddress
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(RepositoryInitController::class.java)
    }
}
