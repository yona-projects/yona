package dev.yona.domain.issue

import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 이슈 목록 화면이 쓰는 보조 조회이다. v1.6 `User.findIssueAuthorsByProjectIdAndMe`, `findIssueAssigneeByProjectIdAndMe`,
 * `User.findUsersByProjectAndOrganization`과 `Issue`의 개수 조회 메서드에 해당한다.
 */
@Service
class IssueListService(private val entityManager: EntityManager) {

    /**
     * 프로젝트에 이슈를 쓴 사람을 이름 순으로 돌려준다. [viewer]가 없으면 더하고 이름 순으로 다시 정렬한다
     * (이름 순서는 v1.6 `UserComparator`처럼 문자열 값 비교이다).
     */
    @Transactional(readOnly = true)
    fun authors(project: Project, viewer: User?): List<User> = withViewer(
        entityManager.createQuery(
            "select distinct u from User u, Issue i where i.authorId = u.id and i.project.id = :project order by u.name", User::class.java
        ).setParameter("project", project.id).resultList,
        viewer
    )

    /** 프로젝트에서 이슈 담당자로 등록된 사람을 이름 순으로 돌려준다. [viewer]가 없으면 더한다. */
    @Transactional(readOnly = true)
    fun assignees(project: Project, viewer: User?): List<User> = withViewer(
        entityManager.createQuery(
            "select distinct u from Assignee a join a.user u where a.project.id = :project order by u.name", User::class.java
        ).setParameter("project", project.id).resultList,
        viewer
    )

    /**
     * 이슈 담당자로 정할 수 있는 사람이다. 프로젝트 구성원(사이트 관리자 역할 제외)과 소속 조직의 구성원(비공개 프로젝트는 조직 관리자만),
     * 사이트 관리자인 [viewer]를 이름 순으로 모은다 (v1.6 `User.findUsersByProjectAndOrganization`).
     */
    @Transactional(readOnly = true)
    fun assignableUsers(project: Project, viewer: User?, viewerIsSiteManager: Boolean): List<User> {
        val ids = linkedSetOf<Long>()
        entityManager.createQuery("select pu.user.id from ProjectUser pu where pu.project.id = :project and pu.role.id <> :siteManager", java.lang.Long::class.java)
            .setParameter("project", project.id).setParameter("siteManager", RoleType.SITEMANAGER.id).resultList.forEach { ids += it.toLong() }
        project.organization?.let { organization ->
            val jpql = if (project.isPrivate()) {
                "select ou.user.id from OrganizationUser ou where ou.organization.id = :organization and ou.role.id = :admin"
            } else {
                "select ou.user.id from OrganizationUser ou where ou.organization.id = :organization"
            }
            val query = entityManager.createQuery(jpql, java.lang.Long::class.java).setParameter("organization", organization.id)
            if (project.isPrivate()) query.setParameter("admin", RoleType.ORG_ADMIN.id)
            query.resultList.forEach { ids += it.toLong() }
        }
        if (viewerIsSiteManager) viewer?.id?.let { ids += it }
        if (ids.isEmpty()) return emptyList()
        return entityManager.createQuery("select u from User u where u.id in :ids order by u.name", User::class.java).setParameter("ids", ids).resultList
    }

    /** 이슈별 댓글 수이다. 댓글이 없는 이슈는 담지 않는다. */
    @Transactional(readOnly = true)
    fun commentCounts(issueIds: Collection<Long>): Map<Long, Int> = counts("select c.issue.id, count(c) from IssueComment c where c.issue.id in :ids group by c.issue.id", issueIds)

    /** 이슈별 추천(보팅) 수이다. */
    @Transactional(readOnly = true)
    fun voterCounts(issueIds: Collection<Long>): Map<Long, Int> = counts("select i.id, count(v) from Issue i join i.voters v where i.id in :ids group by i.id", issueIds)

    /** 이슈별 공유자 수이다. */
    @Transactional(readOnly = true)
    fun sharerCounts(issueIds: Collection<Long>): Map<Long, Int> = counts("select s.issue.id, count(s) from IssueSharer s where s.issue.id in :ids group by s.issue.id", issueIds)

    /** 부모 이슈별로 초안이 아닌 하위 이슈를 상태(열림, 닫힘)별로 센다. 키는 부모 이슈 번호와 상태이다. */
    @Transactional(readOnly = true)
    fun childCounts(parentIds: Collection<Long>): Map<Pair<Long, State>, Int> {
        if (parentIds.isEmpty()) return emptyMap()
        return entityManager.createQuery(
            "select i.parent.id, i.state, count(i) from Issue i where i.parent.id in :ids and i.state in :states group by i.parent.id, i.state", Array<Any>::class.java
        ).setParameter("ids", parentIds).setParameter("states", listOf(State.OPEN, State.CLOSED)).resultList
            .associate { Pair((it[0] as Number).toLong(), it[1] as State) to (it[2] as Number).toInt() }
    }

    /** 초안이 아닌 하위 이슈가 있는 부모 이슈 번호를 모은다 (v1.6 `Issue.hasChildIssue`). */
    @Transactional(readOnly = true)
    fun parentsWithChildren(parentIds: Collection<Long>): Set<Long> {
        if (parentIds.isEmpty()) return emptySet()
        return entityManager.createQuery(
            "select distinct i.parent.id from Issue i where i.parent.id in :ids and i.isDraft = false", java.lang.Long::class.java
        ).setParameter("ids", parentIds).resultList.map { it.toLong() }.toSet()
    }

    /** 부모 이슈의 하위 이슈를 상태별로 번호 순으로 돌려준다 (v1.6 `Issue.findByParentIssueIdAndState`). */
    @Transactional(readOnly = true)
    fun children(parentId: Long, state: State): List<Issue> =
        entityManager.createQuery("select i from Issue i where i.parent.id = :parent and i.state = :state order by i.number", Issue::class.java)
            .setParameter("parent", parentId).setParameter("state", state).resultList

    /** 현재 사용자가 쓴 초안 이슈를 번호 내림차순으로 돌려준다 (v1.6 `IssueApp.findDraftIssues`). */
    @Transactional(readOnly = true)
    fun drafts(project: Project, authorLoginId: String?): List<Issue> {
        if (authorLoginId == null) return emptyList()
        return entityManager.createQuery(
            "select i from Issue i where i.authorLoginId = :login and i.project.id = :project and i.isDraft = true order by i.number desc", Issue::class.java
        ).setParameter("login", authorLoginId).setParameter("project", project.id).resultList
    }

    private fun withViewer(users: List<User>, viewer: User?): List<User> {
        if (viewer == null || users.any { it.id == viewer.id }) return users
        return (users + viewer).sortedBy { it.name }
    }

    private fun counts(jpql: String, ids: Collection<Long>): Map<Long, Int> {
        if (ids.isEmpty()) return emptyMap()
        return entityManager.createQuery(jpql, Array<Any>::class.java).setParameter("ids", ids).resultList
            .associate { (it[0] as Number).toLong() to (it[1] as Number).toInt() }
    }

    /**
     * 이슈를 옮기거나 하위 이슈로 만들 때 고를 수 있는 프로젝트이다. 즐겨찾기한 프로젝트, 최근 방문한 프로젝트, 속한 프로젝트를
     * 소유자, 이름 순(대소문자 무시)으로 모은다 (v1.6 `User.getIssueMovableProject`).
     */
    @Transactional(readOnly = true)
    fun movableProjects(user: User): List<Project> =
        entityManager.createQuery(
            "select p from Project p where p.id in (select f.project.id from FavoriteProject f where f.user.id = :user) " +
                "or p.id in (select r.project.id from RecentProject r where r.userId = :user) " +
                "or p.id in (select pu.project.id from ProjectUser pu where pu.user.id = :user)", Project::class.java
        ).setParameter("user", user.id).resultList
            .sortedWith(compareBy<Project, String>(String.CASE_INSENSITIVE_ORDER) { it.owner.orEmpty() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })

    /** 하위 이슈의 부모로 고를 수 있는 이슈이다. 부모가 없는 이슈를 최근 것부터 [limit]개까지 돌려준다 (v1.6 `Issue.findParentIssueByProject`). */
    @Transactional(readOnly = true)
    fun parentCandidates(project: Project, limit: Int): List<Issue> =
        entityManager.createQuery("select i from Issue i where i.project.id = :project and i.parent is null order by i.createdDate desc, i.id desc", Issue::class.java)
            .setParameter("project", project.id).setMaxResults(limit).resultList
}
