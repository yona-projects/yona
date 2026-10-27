package dev.yona.web.project

import dev.yona.web.AjaxRequests
import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectTransferService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import dev.yona.web.layout.FlashMessageTranslator
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 프로젝트 이전 화면, 요청, 수락이다. v1.6 `ProjectApp.transferForm`, `transferProject`, `acceptTransfer`
 * (`app/controllers/ProjectApp.java:596-690`)에 해당한다. 화면과 요청은 프로젝트를 지울 수 있는 사용자(관리자)만 쓸 수 있다.
 */
@Controller
@AnonymousCheck
class ProjectTransferController(
    private val projectService: ProjectService,
    private val transferService: ProjectTransferService,
    private val views: ProjectViewFactory,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val flashTranslator: FlashMessageTranslator
) {

    @GetMapping("/{user}/{project}/transfer")
    @IsAllowed(Operation.DELETE)
    fun form(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): ModelAndView {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        return formView(project)
    }

    @PutMapping("/{user}/{project}/transfer")
    @IsAllowed(Operation.DELETE)
    @Transactional
    fun transfer(
        @PathVariable("user") ownerId: String,
        @PathVariable("project") projectName: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        val sender = requireNotNull(currentUserProvider.get().user)

        when (transferService.request(project, sender, request.getParameter("owner"))) {
            ProjectTransferService.RequestResult.UnknownDestination -> return ErrorViews.badRequest()
            ProjectTransferService.RequestResult.SameOwner ->
                return formView(project).also { dev.yona.web.RedirectSupport.flashNow(mapOf(RedirectSupport.INFO to "project.transfer.has.same.owner")) }
            is ProjectTransferService.RequestResult.Requested -> Unit
        }

        val url = "/$ownerId/$projectName"
        // Ajax 요청이면 303 대신 Location 헤더와 204를 돌려준다. 안내는 다음 화면에서 보인다.
        if (AjaxRequests.isXhr(request)) {
            RedirectSupport.see(request, response, url, mapOf(RedirectSupport.INFO to "project.transfer.is.requested"))
            return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.LOCATION, url).build<Void>()
        }
        return RedirectSupport.see(request, response, url, mapOf(RedirectSupport.INFO to "project.transfer.is.requested"))
    }

    @GetMapping("/project/transfer/{id}/{key}")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    fun accept(
        @PathVariable id: Long,
        @PathVariable key: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val user = currentUserProvider.get().user
        return when (val result = transferService.accept(id, key, user) { accessControl.isAllowed(user, it.asResource(), Operation.ACCEPT) }) {
            ProjectTransferService.AcceptResult.NotFound -> ErrorViews.notFound()
            ProjectTransferService.AcceptResult.WrongKey -> ErrorViews.badRequest()
            ProjectTransferService.AcceptResult.Forbidden -> ErrorViews.forbidden()
            is ProjectTransferService.AcceptResult.Done ->
                RedirectSupport.see(request, response, "/${result.project.owner}/${result.project.name}")
        }
    }

    private fun formView(project: dev.yona.domain.project.Project): ModelAndView =
        ModelAndView("project/transfer")
            .addObject("title", "title.projectTransfer")
            .addObject("projectView", views.of(project))
}
