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

    /** 폼 화면이다. [warningKey]가 있으면 알림으로 안내하고, 폼 위에는 앞 요청의 안내를 보인다(v1.6). */
    private fun formView(name: String?, descr: String?, warningKey: String?): ModelAndView {
        val view = ModelAndView("organization/create")
            .addObject("title", "title.newOrganization")
            .addObject("name", name)
            .addObject("descr", descr)
        // v1.6 `create.scala.html`은 `requestHeader.flash.get("warning")`, 곧 앞 요청이 남긴 안내를 폼 위에 보인다.
        // 이번 요청의 안내는 알림(`$yobi.notify`)으로 보이고 다음 요청의 폼 위에 보인다.
        val previous = RedirectSupport.incomingFlash()[RedirectSupport.WARNING]?.toString()
        if (previous != null) {
            view.addObject("warning", messages.getMessage(previous, null, previous, LocaleContextHolder.getLocale()))
        }
        if (warningKey != null) {
            view.also { dev.yona.web.RedirectSupport.flashNow(mapOf(RedirectSupport.WARNING to warningKey)) }
        }
        return view
    }
}
