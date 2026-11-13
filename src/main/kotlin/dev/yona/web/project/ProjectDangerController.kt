package dev.yona.web.project

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectVcsService
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView

/**
 * 프로젝트 설정의 "위험 구역" 화면이다(결정 A-535). v1.6에서 따로 있던 이관(`transfer`), 저장소 종류 변경(`changeVCS`), 삭제(`deleteform`)
 * 세 화면을 한 화면의 세 구역으로 합쳤다. GitHub 설정의 "Danger Zone"과 같은 구성이다.
 *
 * 이 화면은 보여 주기만 한다. 실제 동작은 구역의 단추가 각자 부르는 기존 요청이 한다:
 * 이관 `PUT /{user}/{project}/transfer`([ProjectTransferController]), 종류 변경 `POST .../changeVCS`([ProjectVcsController]),
 * 삭제 `DELETE .../delete`([ProjectDeleteController]). 옛 화면 주소(`GET transfer`, `deleteform`, `changeVCS`)는 이 화면으로 이동시킨다.
 */
@Controller
@AnonymousCheck
class ProjectDangerController(
    private val projectService: ProjectService,
    private val vcsService: ProjectVcsService,
    private val views: ProjectViewFactory,
    private val messages: MessageSource
) {
    /** 위험 구역 화면. 프로젝트를 고칠 수 있는 사용자(관리자)만 본다. 저장소 종류 구역은 코드 메뉴가 켜진 프로젝트에서만 있다. */
    @GetMapping("/{user}/{project}/setting/danger")
    @IsAllowed(Operation.UPDATE)
    fun form(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): ModelAndView {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        return view(project)
    }

    /** 위험 구역 화면의 모델이다. 이관 요청이 같은 소유자로 거절될 때도 이 화면을 다시 그린다. */
    fun view(project: dev.yona.domain.project.Project): ModelAndView {
        val projectView = views.of(project)
        val targets = vcsService.targets(project)
        return ModelAndView("project/danger")
            .addObject("title", "title.projectDanger")
            .addObject("projectView", projectView)
            .addObject("showVcs", projectView.menuSetting.code)
            // 저장소 종류 전부를 보여 주고 지금 종류는 "현재"로 막는다. 바꿀 수 있는 종류는 targets이다
            .addObject("allTypes", vcsService.types())
            .addObject("targets", targets)
            .addObject("texts", texts(targets))
    }

    /** 종류마다의 안내와 확인 문구이다. 선택을 바꾸면 화면이 갈아 끼운다(요청한 언어로 만든다). */
    private fun texts(targets: List<String>): Map<String, Map<String, String>> {
        val locale = LocaleContextHolder.getLocale()
        return targets.associateWith { target ->
            mapOf(
                "description" to messages.getMessage("project.changeVCS.description1", arrayOf<Any>(target), locale),
                "requestion" to messages.getMessage("project.changeVCS.requestion", arrayOf<Any>(target), locale)
            )
        }
    }

    /** 옛 화면 주소는 위험 구역으로 이동시킨다(북마크와 옛 링크를 살린다). 권한은 위험 구역과 같다. */
    @GetMapping("/{user}/{project}/transfer", "/{user}/{project}/deleteform", "/{user}/{project}/changeVCS")
    @IsAllowed(Operation.UPDATE)
    fun legacy(
        @PathVariable("user") ownerId: String,
        @PathVariable("project") projectName: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) = RedirectSupport.see(request, response, "/$ownerId/$projectName/setting/danger")
}
