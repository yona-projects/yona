package dev.yona.web.project

import dev.yona.access.AccessControl
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.project.FormErrors
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.ProjectCreateDenied
import dev.yona.domain.project.ProjectCreateService
import dev.yona.domain.project.ProjectForm
import dev.yona.domain.user.UserDisplay
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView
import tools.jackson.databind.json.JsonMapper

/**
 * 프로젝트 생성 폼과 생성이다. v1.6 `ProjectApp.newProjectForm`, `newProject`
 * (`app/controllers/ProjectApp.java:152-231`)에 해당한다.
 *
 * - `GET /projectform`: 로그인해야 열 수 있다(안내 `user.login.alert`). `owner` 쿼리로 소유자를 미리 고를 수 있다.
 * - `POST /projects`: 권한이 없으면 403, 검증에 실패하면 400과 함께 같은 폼을 다시 보여 주고, 성공하면 프로젝트 홈으로 `303`.
 */
@Controller
@AnonymousCheck
class ProjectCreateController(
    private val currentUserProvider: CurrentUserProvider,
    private val createService: ProjectCreateService,
    private val organizationUsers: OrganizationUserRepository,
    private val attachments: AttachmentRepository,
    private val avatars: AvatarService,
    private val messages: MessageSource,
    private val properties: YonaProperties,
    private val accessControl: AccessControl
) {

    private val json = JsonMapper.builder().build()

    @GetMapping("/projectform")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    fun form(request: HttpServletRequest): ModelAndView {
        // v1.6은 폼을 owner 파라미터만 묶고 오류는 버린다.
        return formView(ProjectForm(owner = request.getParameter("owner")), FormErrors())
    }

    @PostMapping("/projects")
    @Transactional
    fun create(request: HttpServletRequest, response: HttpServletResponse): Any {
        val form = bind(request)
        val creator = currentUserProvider.get().user

        when (createService.checkPermission(creator, form.owner)) {
            null -> Unit
            else -> return ErrorViews.forbidden("'${displayNameOf(creator)}' has no permission")
        }

        val errors = createService.validate(form, creator)
        if (errors.hasErrors()) {
            return formView(form, errors).also { it.status = HttpStatus.BAD_REQUEST }
        }

        val project = createService.create(form, requireNotNull(creator))
        return RedirectSupport.see(request, response, dev.yona.util.UrlPaths.projectPath(project.owner, project.name))
    }

    // --- 입력과 화면 ---

    /** 요청 파라미터를 폼으로 묶는다. 메뉴 체크박스는 `true`, `on`, `yes`, `1`이면 켠 것으로 본다. */
    private fun bind(request: HttpServletRequest): ProjectForm = ProjectForm(
        owner = request.getParameter("owner"),
        name = request.getParameter("name"),
        overview = request.getParameter("overview"),
        projectScope = request.getParameter("projectScope"),
        vcs = request.getParameter("vcs"),
        menus = ProjectForm.MENU_NAMES.filter { isOn(request.getParameter(it)) }.toSet()
    )

    private fun isOn(value: String?): Boolean = value?.lowercase() in setOf("true", "on", "yes", "1")

    private fun displayNameOf(user: dev.yona.domain.user.User?): String =
        if (user == null) {
            messages.getMessage("user.notExists.name", null, LocaleContextHolder.getLocale())
        } else {
            UserDisplay.of(user, user.lang)
        }

    private fun formView(form: ProjectForm, errors: FormErrors): ModelAndView {
        val user = currentUserProvider.get().user
        val adminOrganizations = user?.id?.let { organizationUsers.findByUserIdAndRoleId(it, RoleType.ORG_ADMIN.id) }.orEmpty()
            .mapNotNull { it.organization }
            .map { OrganizationOption(it.name.orEmpty(), organizationLogoUrl(it.id)) }
        val locale = LocaleContextHolder.getLocale()
        val translated = errors.fields().mapValues { (_, keys) ->
            keys.map { messages.getMessage(it, null, it, locale) ?: it }
        }
        return ModelAndView("project/create")
            .addObject("title", "title.newProject")
            .addObject("form", form)
            .addObject("fieldErrors", translated)
            .addObject("errorsJson", json.writeValueAsString(translated))
            .addObject("ownerAvatarUrl", user?.let { avatars.avatarUrl(it, AvatarService.DEFAULT_SIZE) })
            .addObject("organizations", adminOrganizations)
            .addObject("ownerIsOrganization", form.owner?.let { owner -> adminOrganizations.any { it.name.equals(owner, ignoreCase = true) } } ?: false)
            .addObject("privateIsDefault", properties.projectDefaultScope.equals("private", ignoreCase = true))
            .addObject("defaultMenus", properties.projectDefaultMenus.replace(" ", "").split(",").toSet())
            .addObject("vcsTypes", VCS_TYPES)
    }

    private fun organizationLogoUrl(organizationId: Long?): String {
        val file = organizationId?.let {
            attachments.findByContainerTypeAndContainerIdOrderById(ResourceType.ORGANIZATION, it).firstOrNull()
        }
        return file?.let { "/files/${it.id}" } ?: DEFAULT_ORGANIZATION_LOGO
    }

    /** 소유자 선택지에 보이는 조직이다. */
    data class OrganizationOption(val name: String, val logoUrl: String)

    /** 저장소 종류 선택지이다. [labelKey]는 표시 문구의 메시지 키이다. */
    data class VcsOption(val value: String, val labelKey: String)

    private companion object {
        const val DEFAULT_ORGANIZATION_LOGO = "/assets/images/group_default.png"
        val VCS_TYPES = listOf(
            VcsOption("GIT", "project.new.vcsType.git"),
            VcsOption("Subversion", "project.new.vcsType.subversion")
        )
    }
}
