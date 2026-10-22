package dev.yona.support

import dev.yona.domain.project.ProjectRepository
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.ModelAndView

/** 오류 화면 도우미(`ErrorViews`)와 예외 처리를 확인하기 위한 테스트 전용 엔드포인트이다. */
@Controller
class ErrorViewTestController(private val projects: ProjectRepository, private val views: ProjectViewFactory) {

    @GetMapping("/__test/err/project-forbidden/{id}")
    fun projectForbidden(@PathVariable id: Long): ModelAndView =
        ErrorViews.forbidden("error.forbidden", views.of(projects.findById(id).orElseThrow()))

    @GetMapping("/__test/err/project-notfound/{id}")
    fun projectNotFound(@PathVariable id: Long, @RequestParam(required = false) type: String?): ModelAndView =
        ErrorViews.notFound("error.notfound", views.of(projects.findById(id).orElseThrow()), type)

    @GetMapping("/__test/err/project-badrequest/{id}")
    fun projectBadRequest(@PathVariable id: Long): ModelAndView =
        ErrorViews.badRequest("error.badrequest", views.of(projects.findById(id).orElseThrow()), "PROJECT_HOME")


    @GetMapping("/__test/err/forbidden")
    fun forbidden(): ModelAndView = ErrorViews.forbidden("error.forbidden")

    @GetMapping("/__test/err/forbidden-custom")
    fun forbiddenCustom(): ModelAndView = ErrorViews.forbidden("error.auth.unauthorized.waringMessage")

    @GetMapping("/__test/err/notfound")
    fun notFound(): ModelAndView = ErrorViews.notFound("error.notfound.project")

    @GetMapping("/__test/err/badrequest")
    fun badRequest(): ModelAndView = ErrorViews.badRequest("error.badrequest")

    @GetMapping("/__test/err/boom")
    fun boom(): ModelAndView = throw IllegalStateException("의도한 실패")

    @GetMapping("/__test/err/timeout")
    fun timeout(): ModelAndView =
        throw RuntimeException("request failed", jakarta.persistence.PersistenceException("query timed out"))
}
