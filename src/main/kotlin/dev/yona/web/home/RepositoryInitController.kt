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
 * v1.6의 개발용 경로를 선생님 결정(D-27)에 따라 그대로 옮겼다. **접근 제한이 없어** 로그인하지 않아도 호출할 수 있고 기존 커밋과 브랜치는 모두 사라진다
 * ([refactoring-backlog.md] R-66). 한 프로젝트에서 실패해도 오류를 기록하고 다음 프로젝트로 넘어간다. 끝나면 홈으로 보낸다.
 */
@Controller
class RepositoryInitController(
    private val projects: ProjectRepository,
    private val repositories: RepositoryService
) {

    @GetMapping("/_init")
    fun init(request: HttpServletRequest, response: HttpServletResponse): Any {
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

    private companion object {
        val log = LoggerFactory.getLogger(RepositoryInitController::class.java)
    }
}
