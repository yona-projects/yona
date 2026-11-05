package dev.yona.domain.user

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.misc.FavoriteOrganizationRepository
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.misc.RecentIssue
import dev.yona.domain.misc.RecentIssueRepository
import dev.yona.domain.misc.UnwatchRepository
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.RecentProjectRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자 메뉴(사이드바)가 보여 주는 목록을 모은다. v1.6 `User.ownProjects`, `getFavoriteProjects`, `getFavoriteOrganizations`, `getVisitedProjects`,
 * `getWatchingProjects`, `getVisitedIssues`와 `Organization.findAllOrganizations(loginId)`, `Project.findProjectsCreatedByUser`,
 * `findProjectsJustMemberAndNotOwner`에 해당한다.
 */
@Service
class UserMenuService(
    private val favoriteProjects: FavoriteProjectRepository,
    private val favoriteOrganizations: FavoriteOrganizationRepository,
    private val recentProjects: RecentProjectRepository,
    private val recentIssues: RecentIssueRepository,
    private val watches: WatchRepository,
    private val unwatches: UnwatchRepository,
    private val organizations: OrganizationRepository,
    private val entityManager: EntityManager
) {

    /** 사용자가 소유한 프로젝트를 이름 순서로 돌려준다 (대소문자 무시). */
    @Transactional(readOnly = true)
    fun ownProjects(user: User): List<Project> =
        entityManager.createQuery("select p from Project p where lower(p.owner) = lower(:owner) order by p.name asc", Project::class.java)
            .setParameter("owner", user.loginId.orEmpty()).resultList

    /**
     * 즐겨찾기한 프로젝트이다. 내 프로젝트가 먼저이고, 나머지는 소유자 이름, 프로젝트 이름 순서이다(대소문자 무시).
     * v1.6의 비교 함수는 내 프로젝트가 끼면 항상 같은 값을 돌려줘 순서가 정해져 있지 않았다. 의도에 맞게 정렬한다([decisions.md] A-53).
     */
    @Transactional(readOnly = true)
    fun favoriteProjects(user: User): List<Project> {
        val login = user.loginId.orEmpty()
        return favoriteProjects.findByUserIdOrderByIdAsc(requireNotNull(user.id)).mapNotNull { it.project }
            .sortedWith(compareBy<Project>({ !it.owner.equals(login) }, { it.owner.orEmpty().lowercase() }, { it.name.orEmpty().lowercase() }))
    }

    /** 즐겨찾기한 조직이다. 가장 나중에 즐겨찾기한 조직이 먼저이다. */
    @Transactional(readOnly = true)
    fun favoriteOrganizations(user: User): List<Organization> =
        favoriteOrganizations.findByUserIdOrderByIdAsc(requireNotNull(user.id)).mapNotNull { it.organization }.reversed()

    /** 즐겨찾기한 프로젝트의 소유자 가운데 조직인 것을 소유자 이름 순서(대소문자 무시)로 돌려준다 (v1.6 `Organization.findAllOrganizations(loginId)`). */
    @Transactional(readOnly = true)
    fun organizationsOfFavoriteProjects(user: User): List<Organization> =
        favoriteProjects.findByUserIdOrderByIdAsc(requireNotNull(user.id)).mapNotNull { it.owner }
            .toSortedSet(String.CASE_INSENSITIVE_ORDER).mapNotNull { organizations.findByNameIgnoreCase(it) }

    /** 최근 방문한 프로젝트를 최근 순서로 돌려준다. 지워진 프로젝트는 뺀다. */
    @Transactional(readOnly = true)
    fun visitedProjects(user: User): List<Project> =
        recentProjects.findByUserIdOrderByIdDesc(requireNotNull(user.id)).mapNotNull { it.project }

    /** 구독 중인 프로젝트를 이름 순서로 돌려준다. 구독 해제한 프로젝트는 뺀다 (v1.6 `User.getWatchingProjects("name ASC")`). v1.6은 Ebean의 메모리 정렬이 `Comparable.compareTo`라 대소문자를 구분했지만, 다른 프로젝트 목록과 같게 대소문자를 구분하지 않는다([refactoring-backlog.md] R-101). */
    @Transactional(readOnly = true)
    fun watchingProjects(user: User): List<Project> {
        val userId = requireNotNull(user.id)
        val ids = watches.findByUserIdAndResourceType(userId, ResourceType.PROJECT).mapNotNull { it.resourceId }.toMutableList()
        unwatches.findByUserIdAndResourceType(userId, ResourceType.PROJECT).forEach { ids.remove(it.resourceId) }
        return ids.mapNotNull { id -> id.toLongOrNull()?.let { entityManager.find(Project::class.java, it) } }.sortedBy { it.name.orEmpty().lowercase() }
    }

    /** 내가 만든(소유한) 프로젝트를 만든 시각 내림차순으로 돌려준다 (v1.6 `Project.findProjectsCreatedByUser`). */
    @Transactional(readOnly = true)
    fun createdByMe(user: User): List<Project> =
        entityManager.createQuery("select p from Project p where p.owner = :owner order by p.createdDate desc", Project::class.java)
            .setParameter("owner", user.loginId.orEmpty()).resultList

    /** 구성원이지만 소유자는 아닌 프로젝트를 이름 순서로 돌려준다. 사이트 관리자 역할로 들어간 것은 뺀다 (v1.6 `Project.findProjectsJustMemberAndNotOwner`). */
    @Transactional(readOnly = true)
    fun justMember(user: User): List<Project> =
        entityManager.createQuery(
            "select p from Project p where p.id in (select pu.project.id from ProjectUser pu where pu.user.id = :user and pu.role.id <> :siteManager) " +
                "and (p.owner is null or p.owner <> :owner) order by p.name asc", Project::class.java
        ).setParameter("user", user.id).setParameter("siteManager", RoleType.SITEMANAGER.id).setParameter("owner", user.loginId.orEmpty()).resultList

    /** 최근 본 이슈와 글을 최근 순서로 돌려준다. */
    @Transactional(readOnly = true)
    fun recentIssues(user: User): List<RecentIssue> = recentIssues.findByUserIdOrderByIdDesc(requireNotNull(user.id))
}
