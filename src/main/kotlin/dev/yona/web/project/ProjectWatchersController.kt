package dev.yona.web.project

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.notification.NotificationReceivers
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.UserDisplay
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView

/**
 * 프로젝트를 구독한 사용자 목록이다. v1.6 `ProjectApp.watchers`(`app/controllers/ProjectApp.java:368-382`)에 해당한다.
 * 프로젝트를 읽을 수 있으면 로그인하지 않아도 볼 수 있다. 구독자 가운데 프로젝트를 읽을 수 없는 사용자는 뺀다.
 */
@Controller
@AnonymousCheck
class ProjectWatchersController(
    private val projectService: ProjectService,
    private val notificationReceivers: NotificationReceivers,
    private val currentUserProvider: CurrentUserProvider,
    private val avatars: AvatarService,
    private val views: ProjectViewFactory
) {

    /** 프로젝트를 구독한 사용자 목록이다. 프로젝트를 읽을 수 있는 사용자만 보여 준다. */
    @GetMapping("/{user}/{project}/watchers")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun watchers(@PathVariable("user") ownerId: String, @PathVariable("project") projectName: String): ModelAndView {
        val project = projectService.findByOwnerAndProjectName(ownerId, projectName) ?: return ErrorViews.notFound("error.notfound")
        val viewer = currentUserProvider.get().user
        val items = notificationReceivers.actualWatchers(ResourceType.PROJECT, requireNotNull(project.id).toString(), emptyList())
            .sortedBy { it.id }
            .map { WatcherItem(it.loginId.orEmpty(), UserDisplay.of(it, viewer?.lang), avatars.avatarUrl(it)) }
        return ModelAndView("project/watchers")
            .addObject("title", "title.projectWatchers")
            .addObject("projectView", views.of(project))
            .addObject("watchers", items)
    }
}

/** 구독자 목록의 한 줄이다. */
data class WatcherItem(val loginId: String, val displayName: String, val avatarUrl: String)
