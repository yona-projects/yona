package dev.yona.support

import dev.yona.domain.project.ProjectRepository
import dev.yona.web.project.ProjectViewFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

/** 프로젝트 레이아웃을 확인하기 위한 테스트 전용 엔드포인트이다. 테스트 소스에만 있다. */
@Controller
class ProjectPageTestController(
    private val projects: ProjectRepository,
    private val views: ProjectViewFactory
) {

    @GetMapping("/__test/project-page/{id}")
    fun page(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "") title: String,
        @RequestParam(defaultValue = "PROJECT_HOME") menu: String
    ): ModelAndView {
        val project = projects.findById(id).orElseThrow()
        return ModelAndView("support/project-page")
            .addObject("title", title)
            .addObject("menuType", menu)
            .addObject("projectView", views.of(project))
    }
}
