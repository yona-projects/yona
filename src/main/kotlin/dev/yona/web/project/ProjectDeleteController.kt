package dev.yona.web.project

import dev.yona.web.AjaxRequests
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.project.ProjectDeletionService
import dev.yona.domain.project.ProjectService
import dev.yona.repository.RepositoryService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView

/**
 * 프로젝트 삭제 화면과 삭제이다. v1.6 `ProjectApp.deleteForm`, `deleteProject`
 * (`app/controllers/ProjectApp.java:331-356`)에 해당한다. 프로젝트를 지울 수 있는 사용자(관리자)만 쓸 수 있다.
 */
@Controller
@AnonymousCheck
class ProjectDeleteController(
    private val projectService: ProjectService,
    private val deletion: ProjectDeletionService,
    private val repositories: RepositoryService,
    private val favorites: FavoriteProjectRepository,
    private val views: ProjectViewFactory,
    private val currentUserProvider: CurrentUserProvider
) {

    @GetMapping("/{user}/{project}/deleteform")
    @IsAllowed(Operation.DELETE)
    fun form(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): ModelAndView {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        return ModelAndView("project/delete")
            .addObject("title", "title.projectDelete")
            .addObject("projectView", views.of(project))
    }

    @DeleteMapping("/{user}/{project}/delete")
    @IsAllowed(Operation.DELETE)
    @Transactional
    fun delete(
        @PathVariable("user") ownerId: String,
        @PathVariable("project") projectName: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName)
            ?: return RedirectSupport.see(request, response, "/")
        val projectId = requireNotNull(project.id)

        // 지우는 사용자의 즐겨찾기를 먼저 정리한다 (v1.6 `User.removeFavoriteProject`).
        currentUserProvider.get().user?.id?.let { userId ->
            favorites.findByUserIdAndProjectId(userId, projectId).forEach { favorites.delete(it) }
            favorites.flush()
        }
        deletion.delete(project)
        repositories.deleteRepository(project)
        repositories.deleteWiki(project)
        repositories.deleteLfs(project)

        // v1.6은 Ajax 요청이면 303 대신 Location 헤더와 204를 돌려준다.
        if (AjaxRequests.isXhr(request)) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.LOCATION, "/").build<Void>()
        }
        return RedirectSupport.see(request, response, "/")
    }
}
