package dev.yona.web.user

import dev.yona.security.SessionPermissions
import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserMenuService
import org.springframework.stereotype.Component
import java.net.URLDecoder

/** 사용자 메뉴의 목록을 화면에 맞게 모은다. v1.6 `index/myOrganizationList*`, `myProjectList*`, `myRecentIssueList*`, `allProjectList_partial`이 계산하던 값이다. */
@Component
class UserMenuAssembler(
    private val menu: UserMenuService,
    private val projects: ProjectRepository,
    private val attachments: AttachmentRepository,
    private val permissions: SessionPermissions,
    private val accessControl: AccessControl
) {

    /** [viewer]의 사용자 메뉴이다. 로그인하지 않았으면 모든 목록이 비어 있다. */
    fun assemble(viewer: User?): UserMenuModel {
        if (viewer == null) return UserMenuModel(null, emptyList(), emptyList(), true, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val login = viewer.loginId.orEmpty()
        val favoriteProjects = menu.favoriteProjects(viewer)
        val favoredIds = favoriteProjects.mapNotNull { it.id }.toSet()
        val favored = menu.favoriteOrganizations(viewer)
        val others = menu.organizationsOfFavoriteProjects(viewer)
        val ownProjects = menu.ownProjects(viewer)
        val groups = favored.mapIndexed { index, org -> organization(viewer, org, true, index == favored.lastIndex, favoredIds) } +
            others.filter { org -> favored.none { it.id == org.id } }.map { organization(viewer, it, false, false, favoredIds) }
        // 소속 조직이 없고 소유자가 내가 아닌 즐겨찾기 프로젝트이다 (v1.6 `!project.hasGroup && !owner.equals(currentUser.loginId)`).
        val etcFavorites = favoriteProjects.filter { it.organization == null && it.owner != login }.map { project(it, true) }
        return UserMenuModel(
            loginId = login,
            ownProjects = ownProjects.filter { readable(viewer, it) }.map { project(it, it.id in favoredIds) },
            organizations = groups,
            // v1.6은 이 즐겨찾기를 보지 않아 내 프로젝트와 조직이 없으면 즐겨찾기가 있어도 탭 전체가 "결과 없음"이었다(R-31)
            noOrganizations = groups.isEmpty() && favored.isEmpty() && ownProjects.isEmpty() && etcFavorites.isEmpty(),
            etcFavorites = etcFavorites,
            recentlyVisited = menu.visitedProjects(viewer).map { project(it, false) },
            watching = menu.watchingProjects(viewer).map { project(it, false) },
            createdByMe = menu.createdByMe(viewer).map { project(it, false) },
            joinMember = menu.justMember(viewer).map { project(it, false) },
            recentIssues = menu.recentIssues(viewer).map { recent ->
                val url = recent.url.orEmpty()
                val paths = url.split("/")
                // v1.6 `RecentIssue.getNumber`: 주소의 프로젝트 이름과 번호를 `프로젝트 #번호`로 줄인다. 짧은 주소는 그대로이다.
                val label = if (paths.size < 5) url else "${paths[2]} #${paths[4]}"
                MenuRecentIssue(recent.title, url, URLDecoder.decode(label, Charsets.UTF_8))
            }
        )
    }

    private fun organization(viewer: User, org: Organization, favored: Boolean, last: Boolean, favoredIds: Set<Long>): MenuOrganization {
        val orgProjects = projects.findByOrganizationId(requireNotNull(org.id)).sortedBy { it.name.orEmpty().lowercase() }
        val showCount = accessControl.isSiteManager(viewer) || permissions.organizationRole(viewer.id, org.id, RoleType.ORG_ADMIN)
        return MenuOrganization(
            org.id!!, org.name.orEmpty(), if (showCount) orgProjects.size else null, favored, last,
            orgProjects.filter { readable(viewer, it) }.map { project(it, it.id in favoredIds) }
        )
    }

    private fun readable(viewer: User, project: Project) = accessControl.isAllowed(viewer, project.asResource(), Operation.READ)

    private fun project(project: Project, favored: Boolean): MenuProject {
        val logo = attachments.findByContainerTypeAndContainerIdOrderById(ResourceType.PROJECT, requireNotNull(project.id)).firstOrNull()?.id?.let { "/files/$it" }
        return MenuProject(project.id!!, project.owner.orEmpty(), project.name.orEmpty(), project.overview, project.isPrivate(), logo, favored)
    }
}
