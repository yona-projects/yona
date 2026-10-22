package dev.yona.web.project

import dev.yona.access.AccessControl
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.misc.UnwatchRepository
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/** 현재 요청의 사용자와 요청 정보로 [ProjectViewContext]를 만든다. */
@Component
class ProjectViewFactory(
    private val currentUserProvider: CurrentUserProvider,
    projectService: ProjectService,
    accessControl: AccessControl,
    menuSettings: ProjectMenuSettingRepository,
    attachments: AttachmentRepository,
    watches: WatchRepository,
    unwatches: UnwatchRepository,
    favorites: FavoriteProjectRepository,
    projectUsers: ProjectUserRepository,
    users: UserRepository,
    siteAdmins: SiteAdminRepository,
    repositoryUrls: RepositoryUrls
) {

    private val dependencies = ProjectViewContext.Dependencies(
        projectService, accessControl, menuSettings, attachments, watches, unwatches, favorites,
        projectUsers, users, siteAdmins, repositoryUrls
    )

    /** 현재 요청의 사용자가 보는 [project]의 화면 정보이다. */
    fun of(project: Project): ProjectViewContext = ProjectViewContext(
        project, currentUserProvider.get().user, currentRequest(), dependencies
    )

    private fun currentRequest(): HttpServletRequest =
        (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
}
