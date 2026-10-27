package dev.yona.web.project

import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.ProjectHomeService
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import dev.yona.domain.user.UserDisplay
import dev.yona.markdown.MarkdownRenderer
import dev.yona.markdown.ProjectMarkdownRenderer
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import tools.jackson.databind.JsonNode

/**
 * 프로젝트 홈과 설명 수정이다. v1.6 `ProjectApp.project`, `projectOverviewUpdate`
 * (`app/controllers/ProjectApp.java:85-150`)에 해당한다.
 *
 * 홈에는 README, 이력, 대시보드 탭이 있다. 이력 탭은 [ProjectHomeTabsAssembler.historyRows], 대시보드 탭은 [ProjectHomeTabsAssembler.dashboard]가 만든다.
 */
@Controller
@AnonymousCheck
class ProjectHomeController(
    private val projectService: ProjectService,
    private val projects: ProjectRepository,
    private val homeService: ProjectHomeService,
    private val views: ProjectViewFactory,
    private val markdown: MarkdownRenderer,
    private val projectMarkdown: ProjectMarkdownRenderer,
    private val recentVisits: RecentVisitService,
    private val currentUserProvider: CurrentUserProvider,
    private val accessControl: AccessControl,
    private val avatars: AvatarService,
    private val messages: MessageSource,
    private val tabs: ProjectHomeTabsAssembler
) {

    @GetMapping("/{user}/{project}")
    @IsAllowed(Operation.READ)
    fun home(
        @PathVariable("user") ownerId: String,
        @PathVariable("project") projectName: String,
        @RequestParam(name = "tabId", defaultValue = "readme") requestedTab: String
    ): ModelAndView {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        val viewer = currentUserProvider.get().user
        // v1.6은 tabId가 비어 있으면 readme를 쓴다.
        val tabId = requestedTab.ifBlank { "readme" }

        recentVisits.visits(viewer, project)

        val view = views.of(project)
        val viewerLang = viewer?.lang
        val readmeText = homeService.readmeText(project)
        val codeMenu = view.menuSetting.code == true
        val readmeBody = if (!codeMenu) homeService.readmePostingBody(project) else null
        val mav = ModelAndView("project/home")
            .addObject("title", "${project.name} - ${messages.getMessage("menu.home", null, LocaleContextHolder.getLocale())} |:| ${project.overview}")
            .addObject("projectView", view)
            .addObject("tabId", tabId)
            .addObject("overviewHtml", project.overview?.takeIf { it.isNotEmpty() }?.let { markdown.render(it) })
            .addObject("readmeFileName", if (readmeText != null) homeService.readmeFileName(project) else null)
            .addObject("readmeHtml", readmeHtml(project, readmeText, readmeBody, codeMenu))
            .addObject("members", homeService.members(project).map {
                MemberItem(it.loginId.orEmpty(), UserDisplay.of(it, viewerLang), avatars.avatarUrl(it, AvatarService.DEFAULT_SIZE))
            })
            .addObject("milestone", if (view.menuSetting.milestone == true) homeService.firstOpenMilestone(project) else null)
        // v1.6은 README가 아닌 탭이면 이력을 만들고, 이력 탭에서만 쓴다. 대시보드는 자기 영역을 따로 그린다.
        if (tabId == "history") mav.addObject("history", tabs.historyRows(ownerId, project))
        if (tabId == "dashboard") {
            mav.addObject("dashboard", tabs.dashboard(project, view.menuSetting.issue == true, view.menuSetting.pullRequest == true, view.git))
        }
        return mav
    }

    @PutMapping("/{user}/{project}")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun updateOverview(
        @PathVariable("user") ownerId: String,
        @PathVariable("project") projectName: String,
        @RequestBody body: JsonNode
    ): Any {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        project.overview = body.path("overview").takeIf { it.isString }?.stringValue()
        projects.save(project)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("overview" to project.overview))
    }

    /**
     * README 화면에 넣을 HTML이다. 코드 메뉴가 꺼져 있고 README 게시글이 있으면 게시글을, 아니면 저장소의 README 파일을 쓴다.
     * README가 없으면 `null`이다.
     */
    private fun readmeHtml(project: dev.yona.domain.project.Project, text: String?, postingBody: String?, codeMenu: Boolean): String? = when {
        text == null -> null
        !codeMenu && postingBody != null -> projectMarkdown.render(postingBody, project)
        else -> projectMarkdown.renderFileInReadme(text, project)
    }

    /** 홈의 구성원 목록 한 줄이다. */
    data class MemberItem(val loginId: String, val displayName: String, val avatarUrl: String)
}
