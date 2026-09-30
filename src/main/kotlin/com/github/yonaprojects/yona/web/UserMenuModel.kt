package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.RecentIssueService
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.FavoriteOrganizationRepository
import com.github.yonaprojects.yona.domain.user.FavoriteProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import org.springframework.stereotype.Component

@Component
class UserMenuModel(
    private val favoriteProjectRepository: FavoriteProjectRepository,
    private val favoriteOrganizationRepository: FavoriteOrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val projectRepository: ProjectRepository,
    private val watchRepository: WatchRepository,
    private val recentIssueService: RecentIssueService
) {
    fun load(user: User): Map<String, Any> {
        val userId = user.id!!
        val favoriteProjects = favoriteProjectRepository.findByUserId(userId).map { it.project }
        val organizations = organizationUserRepository.findByUserId(userId).map { it.organization }
        val favoriteOrganizations = favoriteOrganizationRepository.findByUserId(userId).map { it.organization }
        val allUserProjects = projectUserRepository.findByUserId(userId).map { it.project }
        val createdByMe = projectRepository.findByOwner(user.loginId!!)
        val watching = watchRepository.findByUserAndResourceType(user, ResourceType.PROJECT).mapNotNull {
            projectRepository.findById(it.resourceId.toLongOrNull() ?: return@mapNotNull null).orElse(null)
        }
        return mapOf(
            "currentUser" to user,
            "favoriteProjects" to favoriteProjects,
            "favoriteOrganizations" to favoriteOrganizations,
            "organizations" to organizations,
            "recentlyVisited" to allUserProjects.sortedByDescending { it.createdDate }.take(10),
            "createdByMe" to createdByMe,
            "watching" to watching,
            "joinmember" to allUserProjects.filter { it.owner != user.loginId },
            "visitedIssues" to recentIssueService.getRecentIssues(user)
        )
    }
}
