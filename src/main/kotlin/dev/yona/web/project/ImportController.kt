package dev.yona.web.project

import dev.yona.access.AccessControl
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.project.FormErrors
import dev.yona.domain.project.ImportResult
import dev.yona.domain.project.ProjectImportForm
import dev.yona.domain.project.ProjectImportService
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.enumeration.ResourceType
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
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView
import tools.jackson.databind.json.JsonMapper

/**
 * 다른 Git 저장소를 가져와 프로젝트를 만든다. v1.6 `ImportApp.importForm`, `newProject`(`app/controllers/ImportApp.java`)에 해당한다.
 *
 * - `GET /_import`: 로그인해야 한다(안내 `user.login.alert`). `owner`로 소유자를 미리 고를 수 있다.
 * - `POST /_import`: 전역 리소스를 만들 권한이 필요하다(없으면 403). 입력이 틀렸거나 저장소를 가져오지 못하면 400과 함께 같은 폼을 다시 보여 주고,
 *   성공하면 프로젝트 홈으로 303이다. 소유자 오류도 이 폼으로 보인다([decisions.md] A-83).
 */
@Controller
@AnonymousCheck
class ImportController(
    private val importService: ProjectImportService,
    private val organizationUsers: OrganizationUserRepository,
    private val attachments: AttachmentRepository,
    private val avatars: AvatarService,
    private val accessControl: AccessControl,
    private val properties: YonaProperties,
    private val messages: MessageSource,
    private val currentUserProvider: CurrentUserProvider
) {

    private val json = JsonMapper.builder().build()

    @GetMapping("/_import")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    fun form(request: HttpServletRequest): ModelAndView = formView(ProjectImportForm(request.getParameter("owner"), null, null, null, null, null, null, DEFAULT_MENUS), FormErrors())

    @PostMapping("/_import")
    @Transactional
    fun create(request: HttpServletRequest, response: HttpServletResponse): Any {
        val creator = currentUserProvider.get().user
        if (!accessControl.isGlobalResourceCreatable(creator)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("'${creator?.name ?: messages.getMessage("user.notExists.name", null, java.util.Locale.getDefault())}' has no permission")
        }
        val form = ProjectImportForm(
            request.getParameter("owner"), request.getParameter("name"), request.getParameter("overview"), request.getParameter("projectScope"),
            request.getParameter("url"), request.getParameter("authId"), request.getParameter("authPw"),
            ALL_MENUS.filter { request.getParameter(it)?.lowercase() in TRUE_VALUES }.toSet()
        )
        return when (val result = importService.import(form, requireNotNull(creator))) {
            is ImportResult.Created -> RedirectSupport.see(request, response, dev.yona.util.UrlPaths.projectPath(result.project.owner, result.project.name))
            is ImportResult.Rejected -> formView(form, result.errors).also { it.status = HttpStatus.BAD_REQUEST }
            ImportResult.Forbidden -> ErrorViews.forbidden("'${creator.name}' has no permission")
        }
    }

    private fun formView(form: ProjectImportForm, errors: FormErrors): ModelAndView {
        val user = currentUserProvider.get().user
        val adminOrganizations = user?.id?.let { organizationUsers.findByUserIdAndRoleId(it, RoleType.ORG_ADMIN.id) }.orEmpty()
            .mapNotNull { it.organization }.map { OrganizationOption(it.name.orEmpty(), organizationLogoUrl(it.id)) }
        val locale = LocaleContextHolder.getLocale()
        val translated = errors.fields().mapValues { (_, keys) -> keys.map { messages.getMessage(it, null, it, locale) ?: it } }
        return ModelAndView("project/importing")
            .addObject("title", "title.newProject")
            .addObject("form", form)
            .addObject("fieldErrors", translated)
            .addObject("errorsJson", json.writeValueAsString(translated))
            .addObject("ownerAvatarUrl", user?.let { avatars.avatarUrl(it, AvatarService.DEFAULT_SIZE) })
            .addObject("organizations", adminOrganizations)
            .addObject("ownerIsOrganization", form.owner?.let { owner -> adminOrganizations.any { it.name.equals(owner, ignoreCase = true) } } ?: false)
            .addObject("privateIsDefault", properties.projectDefaultScope.equals("private", ignoreCase = true))
            .addObject("repoAuthChecked", !form.authId.isNullOrEmpty() || errors.first("repoAuth") != null)
    }

    private fun organizationLogoUrl(organizationId: Long?): String {
        val file = organizationId?.let { attachments.findByContainerTypeAndContainerIdOrderById(ResourceType.ORGANIZATION, it).firstOrNull() }
        return file?.let { "/files/${it.id}" } ?: DEFAULT_ORGANIZATION_LOGO
    }

    /** 소유자 선택지에 보이는 조직이다. */
    data class OrganizationOption(val name: String, val logoUrl: String)

    private companion object {
        const val DEFAULT_ORGANIZATION_LOGO = "/assets/images/group_default.png"
        val ALL_MENUS = listOf("code", "issue", "pullRequest", "review", "milestone", "board")
        val DEFAULT_MENUS = ALL_MENUS.toSet()
        val TRUE_VALUES = setOf("true", "on", "yes", "1")
    }
}
