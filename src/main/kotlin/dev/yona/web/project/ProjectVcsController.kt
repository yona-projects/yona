package dev.yona.web.project

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectVcsService
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 저장소 종류 변경 화면과 변경이다. v1.6 `ProjectApp.changeVCSForm`, `changeVCS`
 * (`app/controllers/ProjectApp.java:714-740`)에 해당한다. 프로젝트를 고칠 수 있는 사용자(관리자)만 쓸 수 있다.
 */
@Controller
@AnonymousCheck
class ProjectVcsController(
    private val projectService: ProjectService,
    private val vcsService: ProjectVcsService,
    private val views: ProjectViewFactory
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/{user}/{project}/changeVCS")
    @IsAllowed(Operation.UPDATE)
    fun form(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): ModelAndView {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        return ModelAndView("project/change-vcs")
            .addObject("title", "title.projectChangeVCS")
            .addObject("projectView", views.of(project))
    }

    @PostMapping("/{user}/{project}/changeVCS")
    @IsAllowed(Operation.UPDATE)
    fun change(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): Any {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        return try {
            vcsService.change(project)
            ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.LOCATION, "/$ownerId/$projectName").build<Void>()
        } catch (e: Exception) {
            // v1.6은 오류를 기록하고 본문 없는 500을 돌려준다.
            log.error(e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build<Void>()
        }
    }
}
