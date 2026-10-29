package dev.yona.web.project

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.LogoUpload
import dev.yona.domain.project.ProjectForm
import dev.yona.domain.project.ProjectHomeService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectSettingForm
import dev.yona.domain.project.ProjectSettingService
import dev.yona.repository.RepositoryService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import dev.yona.web.layout.FlashMessageTranslator
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.ModelAndView

/**
 * 프로젝트 설정 폼과 저장이다. v1.6 `ProjectApp.settingForm`, `settingProject`
 * (`app/controllers/ProjectApp.java:160-166, 235-330`)에 해당한다. 프로젝트를 고칠 수 있는 사용자(관리자)만 쓸 수 있다.
 */
@Controller
@AnonymousCheck
class ProjectSettingController(
    private val projectService: ProjectService,
    private val settingService: ProjectSettingService,
    private val homeService: ProjectHomeService,
    private val repositories: RepositoryService,
    private val views: ProjectViewFactory,
    private val currentUserProvider: CurrentUserProvider,
    private val flashTranslator: FlashMessageTranslator,
    private val messages: MessageSource
) {

    @GetMapping("/{user}/{project}/settingform")
    @IsAllowed(Operation.UPDATE)
    fun form(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): ModelAndView {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        return formView(project, null)
    }

    // v1.6은 폼 본문 종류를 가리지 않고 권한과 검증을 먼저 한다(multipart가 아니면 파일 읽기에서 널 참조 오류, v2는 그대로 저장한다)
    @PostMapping("/{user}/{project}/setting")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun save(
        @PathVariable("user") ownerId: String,
        @PathVariable("project") projectName: String,
        @RequestParam(name = "logoPath", required = false) logoFile: MultipartFile?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        val form = bind(request)
        val logo = logoFile?.let(::toUpload)

        val validation = settingService.validate(project, form, logo)
        if (validation.hasErrors()) {
            return formView(project, validation.warning).also { it.status = HttpStatus.BAD_REQUEST }
        }

        settingService.update(project, form, logo, currentUserProvider.get().user?.loginId)
        return RedirectSupport.see(request, response, "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/settingform")
    }

    /** 폼이 보내는 항목만 묶는다. 메뉴 체크박스는 `true`, `on`, `yes`, `1`이면 켠 것으로 본다. */
    private fun bind(request: HttpServletRequest): ProjectSettingForm = ProjectSettingForm(
        name = request.getParameter("name"),
        overview = request.getParameter("overview"),
        projectScope = request.getParameter("projectScope"),
        isCodeAccessibleMemberOnly = request.getParameter("isCodeAccessibleMemberOnly"),
        isDismissingStaleApprovals = request.getParameter("isDismissingStaleApprovals"),
        allowMergeCommit = request.getParameter("allowMergeCommit"),
        allowSquashMerge = request.getParameter("allowSquashMerge"),
        allowRebaseMerge = request.getParameter("allowRebaseMerge"),
        defaultMergeMethod = request.getParameter("defaultMergeMethod"),
        defaultBranch = request.getParameter("defaultBranch"),
        menus = ProjectForm.MENU_NAMES.filter { request.getParameter(it)?.lowercase() in setOf("true", "on", "yes", "1") }.toSet()
    )

    private fun toUpload(file: MultipartFile) = LogoUpload(file.originalFilename, file.size) { file.inputStream }

    private fun formView(project: Project, warning: String?): ModelAndView {
        val view = views.of(project)
        val branches = if (project.vcs == RepositoryService.VCS_GIT) repositories.refNames(project) else emptyList()
        val defaultBranch = if (project.vcs == RepositoryService.VCS_GIT) repositories.defaultBranch(project) else "HEAD"
        val locale = LocaleContextHolder.getLocale()
        val oldPlace = if (!project.previousName.isNullOrBlank()) {
            val place = if (project.previousOwnerLoginId == null) "" else "${project.previousOwnerLoginId}/${project.previousName}"
            val argument = " <span style='color: red'>$place</span></div>"
            "<div>" + messages.getMessage("project.previous.place", arrayOf(argument), locale)
        } else null
        return ModelAndView("project/setting")
            .addObject("title", "title.projectSetting")
            .addObject("projectView", view)
            .addObject("branches", branches.filter { itemType(it) == "branch" })
            .addObject("defaultBranchName", itemName(defaultBranch))
            .addObject("branchItemNames", branches.filter { itemType(it) == "branch" }.associateWith { itemName(it) })
            .addObject("oldPlaceHtml", oldPlace)
            .addObject("protectionRules", project.branchProtectionRules.sortedBy { it.pattern })
            .also { mav ->
                if (warning != null) mav.also { dev.yona.web.RedirectSupport.flashNow(mapOf(RedirectSupport.WARNING to warning)) }
            }
    }

    /** 참조 이름의 종류이다 (v1.6 `TemplateHelper.Branches.itemType`). `refs/heads/...`는 `branch`, `refs/tags/...`는 `tag`이다. */
    private fun itemType(ref: String): String {
        val names = ref.split('/')
        return when {
            names.size >= 2 && names[0] == "refs" && names[1] == "heads" -> "branch"
            names.size >= 2 && names[0] == "refs" && names[1] == "tags" -> "tag"
            names.size >= 2 && names[0] == "refs" -> names[1]
            else -> ref
        }
    }

    /** 참조 이름에서 `refs/종류/`를 뗀 이름이다 (v1.6 `TemplateHelper.Branches.itemName`). */
    private fun itemName(ref: String): String {
        val names = ref.split("/", limit = 3)
        return if (names.size >= 3 && names[0] == "refs") names[2] else ref
    }
}
