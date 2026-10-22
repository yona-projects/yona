package dev.yona.web.organization

import dev.yona.domain.project.OrganizationService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.GuestProhibit
import dev.yona.web.layout.FlashMessageTranslator
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

/**
 * 조직 생성 폼과 생성이다. v1.6 `OrganizationApp.newForm`, `newOrganization`
 * (`app/controllers/OrganizationApp.java:60-115`)에 해당한다. 로그인해야 하고 게스트는 쓸 수 없다.
 */
@Controller
@AnonymousCheck
class OrganizationCreateController(
    private val organizationService: OrganizationService,
    private val currentUserProvider: CurrentUserProvider,
    private val flashTranslator: FlashMessageTranslator,
    private val messages: MessageSource
) {

    @GetMapping("/organizations/new")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    fun form(): ModelAndView = formView(null, null, null)

    @PostMapping("/organizations/new")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @GuestProhibit
    @Transactional
    fun create(request: HttpServletRequest, response: HttpServletResponse): Any {
        val name = request.getParameter("name")
        val descr = request.getParameter("descr")
        val error = organizationService.validateNewName(name)
        if (error != null) {
            return formView(name, descr, error.messageKey).also { it.status = HttpStatus.BAD_REQUEST }
        }
        val organization = organizationService.create(requireNotNull(name), descr, requireNotNull(currentUserProvider.get().user))
        return RedirectSupport.see(request, response, "/organizations/${organization.name}")
    }

    /** 폼 화면이다. [warningKey]가 있으면 폼 위와 알림으로 같은 응답에서 안내한다. */
    private fun formView(name: String?, descr: String?, warningKey: String?): ModelAndView {
        val view = ModelAndView("organization/create")
            .addObject("title", "title.newOrganization")
            .addObject("name", name)
            .addObject("descr", descr)
        if (warningKey != null) {
            view.addObject("warning", messages.getMessage(warningKey, null, warningKey, LocaleContextHolder.getLocale()))
            view.addObject("flashMessages", flashTranslator.translate(mapOf(RedirectSupport.WARNING to warningKey)))
        }
        return view
    }
}
