package dev.yona.domain.misc

import org.springframework.data.jpa.repository.Query
import dev.yona.domain.enumeration.ResourceType
import org.springframework.data.jpa.repository.JpaRepository

/** 구독(Watch) 저장소이다. */
interface WatchRepository : JpaRepository<Watch, Long> {

    /** 사용자가 리소스를 구독하고 있는지 확인한다. */
    fun existsByUserIdAndResourceTypeAndResourceId(userId: Long, resourceType: ResourceType, resourceId: String): Boolean

    /** 사용자가 구독한 [resourceType] 리소스의 행을 조회한다 (v1.6 `Watch.findBy(user, type)`). */
    fun findByUserIdAndResourceType(userId: Long, resourceType: ResourceType): List<Watch>

    /** 리소스를 구독한 행을 조회한다 (v1.6 `Watch.findBy(type, id)`). */
    fun findByResourceTypeAndResourceId(resourceType: ResourceType, resourceId: String): List<Watch>

    /** 사용자의 구독 행을 조회한다 (v1.6 `Watch.findBy`). */
    fun findByUserIdAndResourceTypeAndResourceId(userId: Long, resourceType: ResourceType, resourceId: String): List<Watch>

    /** 리소스를 구독한 행의 개수이다 (v1.6 `Watch.countBy`). */
    fun countByResourceTypeAndResourceId(resourceType: ResourceType, resourceId: String): Long
}

/** 구독 해제(Unwatch) 저장소이다. */
interface UnwatchRepository : JpaRepository<Unwatch, Long> {

    /** 사용자가 리소스의 구독을 명시적으로 해제했는지 확인한다. */
    fun existsByUserIdAndResourceTypeAndResourceId(userId: Long, resourceType: ResourceType, resourceId: String): Boolean

    /** 사용자가 구독을 해제한 [resourceType] 리소스의 행을 조회한다. */
    fun findByUserIdAndResourceType(userId: Long, resourceType: ResourceType): List<Unwatch>

    /** 리소스의 구독을 해제한 행을 조회한다 (v1.6 `Unwatch.findBy(type, id)`). */
    fun findByResourceTypeAndResourceId(resourceType: ResourceType, resourceId: String): List<Unwatch>

    /** 사용자의 구독 해제 기록을 조회한다. */
    fun findByUserIdAndResourceTypeAndResourceId(userId: Long, resourceType: ResourceType, resourceId: String): List<Unwatch>
}

/** 즐겨찾기한 프로젝트 저장소이다. */
interface FavoriteProjectRepository : JpaRepository<FavoriteProject, Long> {

    /** 사용자가 프로젝트를 즐겨찾기했는지 확인한다 (v1.6 `FavoriteProject.findByProjectId`). */
    fun existsByUserIdAndProjectId(userId: Long, projectId: Long): Boolean

    /** 사용자가 프로젝트를 즐겨찾기한 행을 조회한다 (v1.6 `User.removeFavoriteProject`). */
    fun findByUserIdAndProjectId(userId: Long, projectId: Long): List<FavoriteProject>

    /** 사용자의 즐겨찾기 프로젝트를 오래된 것부터 조회한다. */
    fun findByUserIdOrderByIdAsc(userId: Long): List<FavoriteProject>

    /** 프로젝트를 즐겨찾기한 모든 행을 조회한다 (v1.6 `FavoriteProject.updateFavoriteProject`). */
    fun findByProjectId(projectId: Long): List<FavoriteProject>
}

/** 즐겨찾기한 조직 저장소이다. */
interface FavoriteOrganizationRepository : JpaRepository<FavoriteOrganization, Long> {

    /** 사용자가 조직을 즐겨찾기한 행을 조회한다 (v1.6 `User.removeFavoriteOrganization`). */
    fun findByUserIdAndOrganizationId(userId: Long, organizationId: Long): List<FavoriteOrganization>

    /** 사용자의 즐겨찾기 조직을 오래된 것부터 조회한다. */
    fun findByUserIdOrderByIdAsc(userId: Long): List<FavoriteOrganization>

    /** 조직을 즐겨찾기한 모든 행을 조회한다 (v1.6 `FavoriteOrganization.updateFavoriteOrganization`). */
    fun findByOrganizationId(organizationId: Long): List<FavoriteOrganization>
}


/** 최근 본 이슈와 게시글 저장소이다. */
interface RecentIssueRepository : JpaRepository<RecentIssue, Long> {

    /** 사용자가 이슈를 본 기록을 찾는다 (v1.6 `RecentIssue.deletePreviousIssue`). */
    fun findByUserIdAndIssueId(userId: Long, issueId: Long): List<RecentIssue>

    /** 사용자가 게시글을 본 기록을 찾는다 (v1.6 `RecentIssue.deletePreviousPosting`). */
    fun findByUserIdAndPostingId(userId: Long, postingId: Long): List<RecentIssue>

    /** 사용자의 기록을 오래된 것부터 조회한다. */
    fun findByUserIdOrderByIdAsc(userId: Long): List<RecentIssue>

    /** 사용자의 기록을 최근 것부터 조회한다 (v1.6 `RecentIssue.getRecentIssues`). */
    fun findByUserIdOrderByIdDesc(userId: Long): List<RecentIssue>
}

/** 즐겨찾기한 이슈 저장소이다. */
interface FavoriteIssueRepository : JpaRepository<FavoriteIssue, Long> {

    /** 사용자가 이슈를 즐겨찾기한 행을 조회한다 (v1.6 `User.removeFavoriteIssue`). */
    fun findByUserIdAndIssueId(userId: Long, issueId: Long): List<FavoriteIssue>

    /** 사용자의 즐겨찾기 이슈를 오래된 것부터 조회한다. */
    fun findByUserIdOrderByIdAsc(userId: Long): List<FavoriteIssue>

    /** 사용자가 즐겨찾기한 열린 이슈 수이다 (v1.6 `FavoriteIssue.getNumberOpenFavoriteIssues`). */
    @Query("select count(f) from FavoriteIssue f where f.user.id = :userId and f.issue.state = dev.yona.domain.enumeration.State.OPEN")
    fun countOpen(userId: Long): Long
}
