package dev.yona.web.project

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.misc.WatchService
import dev.yona.domain.notification.ProjectNotificationService
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

/**
 * 프로젝트 구독과 구독 해제, 이벤트 종류별 알림 설정이다. v1.6 `WatchProjectApp`(`app/controllers/WatchProjectApp.java`)에 해당한다.
 * 구독자 목록은 로그인 없이도 볼 수 있어 [ProjectWatchersController]가 맡는다.
 *
 * 프로젝트를 구독하면 새 이슈나 새 글 같은 프로젝트의 알림을 받는다. 구독을 해제하면 그 프로젝트에서 정해 둔 알림 설정도 함께 지운다.
 */
@Controller
@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
class WatchProjectController(
    private val projectService: ProjectService,
    private val projects: ProjectRepository,
    private val watchService: WatchService,
    private val watches: WatchRepository,
    private val notificationService: ProjectNotificationService,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val views: ProjectViewFactory,
    private val messages: MessageSource
) {

    @PostMapping("/{user}/{project}/watch")
    @IsAllowed(Operation.READ)
    @Transactional
    fun watch(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): ResponseEntity<Void> {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ResponseEntity.notFound().build()
        watchService.watch(requireNotNull(currentUserProvider.get().user), ResourceType.PROJECT, requireNotNull(project.id).toString())
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{user}/{project}/unwatch")
    @IsAllowed(Operation.READ)
    @Transactional
    fun unwatch(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): ResponseEntity<Void> {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ResponseEntity.notFound().build()
        val user = requireNotNull(currentUserProvider.get().user)
        watchService.unwatch(user, ResourceType.PROJECT, requireNotNull(project.id).toString())
        notificationService.deleteAll(user, project)
        return ResponseEntity.ok().build()
    }

    /**
     * 구독 중인 프로젝트에서 [notificationType] 알림을 받을지 뒤집는다. 구독하지 않았으면 400이다.
     * 알 수 없는 종류는 v1.6처럼 처리하지 못해 오류(500)가 된다.
     */
    @PostMapping("/noti/toggle/{projectId}/{notificationType}")
    @Transactional
    fun toggle(@PathVariable projectId: Long, @PathVariable notificationType: String): Any {
        // v1.6는 알 수 없는 종류이면 예외로 500이었다(C096). 잘못된 요청이므로 400이다
        val type = EventType.entries.firstOrNull { it.name == notificationType } ?: return ResponseEntity.badRequest().build<Void>()
        val project = projects.findById(projectId).orElse(null) ?: return ErrorViews.notFound("error.notfound.project")
        val user = currentUserProvider.get().user
        if (user == null || !accessControl.isAllowed(user, project.asResource(), Operation.READ)) {
            return ErrorViews.forbidden("error.forbidden", views.of(project))
        }
        if (!watches.existsByUserIdAndResourceTypeAndResourceId(requireNotNull(user.id), ResourceType.PROJECT, projectId.toString())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType("text", "plain", Charsets.UTF_8))
                .body(messages.getMessage("error.notfound.watch", null, LocaleContextHolder.getLocale()))
        }
        notificationService.toggle(user, project, type)
        return ResponseEntity.ok().build<Void>()
    }
}
