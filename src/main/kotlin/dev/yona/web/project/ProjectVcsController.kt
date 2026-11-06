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
import org.springframework.web.bind.annotation.RequestParam
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
    private val views: ProjectViewFactory,
    private val messages: org.springframework.context.MessageSource
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/{user}/{project}/changeVCS")
    @IsAllowed(Operation.UPDATE)
    fun form(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): ModelAndView {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        return ModelAndView("project/change-vcs")
            .addObject("title", "title.projectChangeVCS")
            .addObject("projectView", views.of(project))
            // 바꿀 수 있는 종류가 하나뿐이면 v1.6 화면 그대로이고, 둘 이상이면 고르게 한다
            .addObject("targets", vcsService.targets(project))
            .addObject("texts", texts(vcsService.targets(project)))
    }

    /** 종류마다의 안내와 확인 문구이다. 선택을 바꾸면 화면이 갈아 끼운다(요청한 언어로 만든다). */
    private fun texts(targets: List<String>): Map<String, Map<String, String>> {
        val locale = org.springframework.context.i18n.LocaleContextHolder.getLocale()
        return targets.associateWith { target ->
            mapOf(
                "description" to messages.getMessage("project.changeVCS.description1", arrayOf<Any>(target), locale),
                "requestion" to messages.getMessage("project.changeVCS.requestion", arrayOf<Any>(target), locale)
            )
        }
    }

    @PostMapping("/{user}/{project}/changeVCS")
    @IsAllowed(Operation.UPDATE)
    fun change(
        @PathVariable("user") ownerId: String, @PathVariable("project") projectName: String,
        @RequestParam(required = false) targetVcs: String?
    ): Any {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        // 대상을 주지 않으면 v1.6처럼 번갈아 바꾼다. 지금 종류이거나 바꿀 수 없는 종류를 주면 아무것도 바꾸지 않고 400이다
        if (targetVcs != null && targetVcs !in vcsService.targets(project)) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build<Void>()
        return try {
            vcsService.change(project, targetVcs)
            ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.LOCATION, "/$ownerId/$projectName").build<Void>()
        } catch (e: Exception) {
            // v1.6은 오류를 기록하고 본문 없는 500을 돌려준다.
            log.error(e.message, e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build<Void>()
        }
    }
}
