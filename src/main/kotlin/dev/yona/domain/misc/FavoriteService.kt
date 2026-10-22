package dev.yona.domain.misc

import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.RecentProjectRepository
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 이슈, 프로젝트, 조직의 즐겨찾기를 켜고 끈다. v1.6 `User.toggleFavoriteIssue`, `toggleFavoriteProject`, `toggleFavoriteOrganization`
 * (`app/models/User.java:951-1100`)에 해당한다.
 */
@Service
class FavoriteService(
    private val favoriteIssues: FavoriteIssueRepository,
    private val favoriteProjects: FavoriteProjectRepository,
    private val favoriteOrganizations: FavoriteOrganizationRepository,
    private val recentProjects: RecentProjectRepository,
    private val issues: IssueRepository,
    private val projects: ProjectRepository,
    private val organizations: OrganizationRepository
) {

    /**
     * 이슈 즐겨찾기를 뒤집는다.
     *
     * @return 켜졌으면 `true`, 꺼졌으면 `false`
     * @throws NoSuchElementException 없는 이슈를 켜려 할 때
     */
    @Transactional
    fun toggleIssue(user: User, issueId: Long): Boolean {
        val existing = favoriteIssues.findByUserIdAndIssueId(requireNotNull(user.id), issueId)
        if (existing.isNotEmpty()) { favoriteIssues.deleteAll(existing); return false }
        favoriteIssues.save(FavoriteIssue(user = user, issue = issues.findById(issueId).orElseThrow()))
        return true
    }

    /**
     * 프로젝트 즐겨찾기를 뒤집는다. 켜면 그때의 소유자와 이름을 함께 적고, 끄면 그 프로젝트의 최근 방문 기록도 지운다.
     *
     * @throws NoSuchElementException 없는 프로젝트를 켜려 할 때
     */
    @Transactional
    fun toggleProject(user: User, projectId: Long): Boolean {
        val userId = requireNotNull(user.id)
        val existing = favoriteProjects.findByUserIdAndProjectId(userId, projectId)
        if (existing.isNotEmpty()) {
            favoriteProjects.deleteAll(existing)
            recentProjects.deleteAll(recentProjects.findByUserIdAndProjectId(userId, projectId))
            return false
        }
        val project = projects.findById(projectId).orElseThrow()
        favoriteProjects.save(FavoriteProject(user = user, project = project, owner = project.owner, projectName = project.name))
        return true
    }

    /**
     * 조직 즐겨찾기를 뒤집는다. 켜면 그때의 조직 이름을 함께 적는다.
     *
     * @throws NoSuchElementException 없는 조직을 켜려 할 때
     */
    @Transactional
    fun toggleOrganization(user: User, organizationId: Long): Boolean {
        val existing = favoriteOrganizations.findByUserIdAndOrganizationId(requireNotNull(user.id), organizationId)
        if (existing.isNotEmpty()) { favoriteOrganizations.deleteAll(existing); return false }
        val organization = organizations.findById(organizationId).orElseThrow()
        favoriteOrganizations.save(FavoriteOrganization(user = user, organization = organization, organizationName = organization.name))
        return true
    }
}
