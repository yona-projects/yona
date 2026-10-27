package dev.yona.web.organization

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationManagementService
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.project.ProjectSettingService
import dev.yona.domain.user.LoginIdRules
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.layout.FlashMessageTranslator
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.ModelAndView

/**
 * 조직 설정 폼, 저장, 삭제 화면, 삭제이다. v1.6 `OrganizationApp.settingForm`, `updateOrganizationInfo`, `deleteForm`,
 * `deleteOrganization`(`app/controllers/OrganizationApp.java:296-312, 337-430`)에 해당한다. 조직을 고칠 수 있는 사용자
 * (조직 관리자)만 쓸 수 있다.
 */
@Controller
@AnonymousCheck
class OrganizationSettingController(
    private val organizationService: OrganizationService,
    private val management: OrganizationManagementService,
    private val orgViews: OrganizationViewFactory,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val flashTranslator: FlashMessageTranslator,
    private val messages: MessageSource
) {

    @GetMapping("/organizations/{organizationName}/settingform")
    fun form(@PathVariable organizationName: String): Any {
        val organization = guard(organizationName) ?: return notFoundOrForbidden(organizationName)
        return settingView(organization, null, null)
    }

    @PostMapping("/organizations/{organizationName}/setting")
    @Transactional
    fun save(
        @PathVariable organizationName: String,
        @RequestParam(name = "logoPath", required = false) logo: MultipartFile?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val organization = organizationService.findByName(organizationName)
            ?: return ErrorViews.notFound("organization.member.unknownOrganization")
        val viewer = currentUserProvider.get().user
        if (!accessControl.isAllowed(viewer, organization.asResource(), Operation.UPDATE)) {
            return ErrorViews.forbidden("error.forbidden", orgViews.of(organization)).addObject("flashMessages", warning("organization.member.needManagerRole"))
        }

        val name = request.getParameter("name")
        val descr = request.getParameter("descr")
        // v1.6은 폼을 값으로 바꾸다 형식 오류가 있으면 IllegalStateException이 나서 500이 된다.
        check(!name.isNullOrEmpty() && LoginIdRules.isValidFormat(name) && !LoginIdRules.isReserved(name)) {
            "Cannot get value if form contains errors"
        }

        if (management.isDuplicateName(organization, name)) {
            return settingView(organization, "organization.name.duplicate", null).also { it.status = HttpStatus.BAD_REQUEST }
        }

        var logoWarning: String? = null
        val hasLogo = logo != null && !logo.originalFilename.isNullOrEmpty()
        if (hasLogo) {
            if (!isImageFile(requireNotNull(logo.originalFilename))) logoWarning = "project.logo.alert"
            if (logo.size > ProjectSettingService.LOGO_FILE_LIMIT_SIZE) logoWarning = "project.logo.fileSizeAlert"
        }
        if (logoWarning != null) {
            return settingView(organization, null, logoWarning).also { it.status = HttpStatus.BAD_REQUEST }
        }

        management.update(
            organization, name, descr,
            if (hasLogo) logo!!.originalFilename else null, if (hasLogo) ({ logo!!.inputStream }) else null,
            viewer?.loginId
        )
        return RedirectSupport.see(request, response, "/organizations/$name/settingform")
    }

    @GetMapping("/organizations/{organizationName}/deleteForm")
    fun deleteForm(@PathVariable organizationName: String): Any {
        val organization = guard(organizationName) ?: return notFoundOrForbidden(organizationName)
        return ModelAndView("organization/delete-form")
            .addObject("title", organization.name)
            .addObject("orgView", orgViews.of(organization))
    }

    @DeleteMapping("/organizations/{organizationName}")
    @Transactional
    fun delete(@PathVariable organizationName: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val organization = organizationService.findByName(organizationName)
            ?: return error(HttpStatus.NOT_FOUND, "organization.member.unknownOrganization")
        management.checkDeletable(organization, currentUserProvider.get().user)?.let { return error(HttpStatus.NOT_FOUND, it.messageKey) }
        management.delete(organization)
        return RedirectSupport.see(request, response, "/")
    }

    /** 설정을 고칠 수 있는 조직이면 돌려주고, 아니면 `null`이다. */
    private fun guard(name: String): Organization? {
        val organization = organizationService.findByName(name) ?: return null
        return organization.takeIf { accessControl.isAllowed(currentUserProvider.get().user, it.asResource(), Operation.UPDATE) }
    }

    private fun notFoundOrForbidden(name: String): ModelAndView =
        organizationService.findByName(name)?.let { ErrorViews.forbidden("error.forbidden", orgViews.of(it)) }
            ?: ErrorViews.notFound("organization.member.unknownOrganization")

    /** 설정 화면이다. [nameWarning]은 이름 입력란 아래에만 보이고, [flashKey]는 알림으로 같은 응답에서 보여 준다. */
    private fun settingView(organization: Organization, nameWarning: String?, flashKey: String?): ModelAndView {
        val view = ModelAndView("organization/setting")
            .addObject("title", organization.name)
            .addObject("orgView", orgViews.of(organization))
        // v1.6는 이름 중복이면 폼 오류만 만들고 flash는 만들지 않아 이름 아래 문구만 보인다 (OrganizationApp.java:404-407)
        nameWarning?.let { view.addObject("nameWarning", messages.getMessage(it, null, it, LocaleContextHolder.getLocale())) }
        flashKey?.let { view.addObject("flashMessages", warning(it)) }
        return view
    }

    private fun warning(key: String) = flashTranslator.translate(mapOf(RedirectSupport.WARNING to key))

    private fun error(status: HttpStatus, key: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(mapOf("errorMsg" to key))

    private fun isImageFile(filename: String) = listOf("jpg", "jpeg", "png", "gif", "bmp").any { filename.lowercase().endsWith(it) }
}
