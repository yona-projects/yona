package dev.yona.web.project

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.ProjectService
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.view.RedirectView
import org.springframework.http.HttpStatus

/**
 * 프로젝트 바로가기이다. 사용자 메뉴가 프로젝트를 누르면 이리로 온다. v1.6 `ProjectApp.goConventionMenu`(`app/controllers/ProjectApp.java:1336-1352`)에 해당한다.
 *
 * 이슈 메뉴가 켜져 있으면 주소를 바꾸지 않고 이슈 목록을 그대로 보여 주고(`state`, `format`, 검색 조건은 요청 그대로 이슈 목록이 읽는다),
 * 아니면 게시판 목록으로, 그것도 꺼져 있으면 프로젝트 홈으로 보낸다.
 */
@Controller
@AnonymousCheck
class ProjectGoController(
    private val projectService: ProjectService,
    private val views: ProjectViewFactory
) {

    @GetMapping("/{user}/{project}/go")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun go(
        @PathVariable("user") owner: String, @PathVariable("project") name: String,
        @RequestParam(name = "pageNum", defaultValue = "1") pageNum: Int
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val menu = views.of(project).menuSetting
        val base = dev.yona.util.UrlPaths.projectPath(project.owner, project.name)
        return when {
            menu.issue == true -> ModelAndView("forward:$base/issues")
            menu.board == true -> RedirectView(if (pageNum == 1) "$base/posts" else "$base/posts?pageNum=$pageNum").also { it.setStatusCode(HttpStatus.SEE_OTHER) }
            else -> RedirectView(base).also { it.setStatusCode(HttpStatus.SEE_OTHER) }
        }
    }
}
