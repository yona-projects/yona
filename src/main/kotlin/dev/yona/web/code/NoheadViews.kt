package dev.yona.web.code

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.project.ProjectViewFactory
import dev.yona.web.project.RepositoryUrls
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.ModelAndView

/**
 * 저장소가 비어 있을 때 처음 올리는 방법을 안내하는 화면(`code/nohead`)을 만든다. 코드 화면과 커밋 이력 화면이 함께 쓴다.
 * v1.6 `app/views/code/nohead.scala.html`에 해당한다.
 *
 * 복제 주소는 프로젝트 구성원(관리자, 멤버)에게는 `로그인ID@`를 넣어 인증 정보를 미리 채워 준다. 안내 명령은 수정 권한이 있는 사용자에게만 보인다.
 */
@Component
class NoheadViews(
    private val views: ProjectViewFactory,
    private val repositoryUrls: RepositoryUrls,
    private val projectUsers: ProjectUserRepository,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val messages: MessageSource
) {

    fun of(project: Project, request: HttpServletRequest): ModelAndView {
        val viewer = currentUserProvider.get().user
        val role = viewer?.id?.let { id -> projectUsers.findFirstByUserIdAndProjectIdAndRoleIdNot(id, requireNotNull(project.id), RoleType.SITEMANAGER.id)?.role?.id }
        val loginId = if (role == RoleType.MANAGER.id || role == RoleType.MEMBER.id) viewer?.loginId else null
        return ModelAndView("code/nohead")
            .addObject("title", "${project.name} - " + messages.getMessage("menu.code", null, LocaleContextHolder.getLocale()))
            .addObject("projectView", views.of(project))
            .addObject("cloneUrl", repositoryUrls.urlFor(project, request, loginId))
            .addObject("canUpdate", accessControl.isAllowed(viewer, ProjectResource(requireNotNull(project.id).toString(), ResourceType.CODE, project), Operation.UPDATE))
    }
}
