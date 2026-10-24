package dev.yona.web.help

import dev.yona.domain.project.ProjectService
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.DefaultProjectCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView

/**
 * 프로젝트 통계(`GET /:user/:project/statistics`)이다. v1.6 `StatisticsApp.statistics`(`app/controllers/StatisticsApp.java:31-40`)와
 * `app/views/project/statistics.scala.html`에 해당한다.
 *
 * v1.6의 화면은 프로젝트 레이아웃 안에 "Under Construction" 한 줄뿐이다(통계 데이터를 그리지 않는다). 같게 만든다.
 * 제목 메시지 키 `statistics`는 메시지 파일에 없어 키 이름이 그대로 제목에 나온다.
 */
@Controller
@AnonymousCheck
class StatisticsController(
    private val projectService: ProjectService,
    private val projectViews: ProjectViewFactory
) {

    @GetMapping("/{user}/{project}/statistics")
    @DefaultProjectCheck
    fun statistics(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        return ModelAndView("project/statistics").addObject("title", "statistics").addObject("projectView", projectViews.of(project))
    }
}
