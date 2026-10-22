package dev.yona.domain.misc

import dev.yona.domain.enumeration.ResourceType
import org.springframework.data.jpa.repository.JpaRepository

/** 구독(Watch) 저장소이다. */
interface WatchRepository : JpaRepository<Watch, Long> {

    /** 사용자가 리소스를 구독하고 있는지 확인한다. */
    fun existsByUserIdAndResourceTypeAndResourceId(userId: Long, resourceType: ResourceType, resourceId: String): Boolean

    /** 리소스를 구독한 행의 개수이다 (v1.6 `Watch.countBy`). */
    fun countByResourceTypeAndResourceId(resourceType: ResourceType, resourceId: String): Long
}

/** 구독 해제(Unwatch) 저장소이다. */
interface UnwatchRepository : JpaRepository<Unwatch, Long> {

    /** 사용자가 리소스의 구독을 명시적으로 해제했는지 확인한다. */
    fun existsByUserIdAndResourceTypeAndResourceId(userId: Long, resourceType: ResourceType, resourceId: String): Boolean

    /** 사용자의 구독 해제 기록을 조회한다. */
    fun findByUserIdAndResourceTypeAndResourceId(userId: Long, resourceType: ResourceType, resourceId: String): List<Unwatch>
}

/** 즐겨찾기한 프로젝트 저장소이다. */
interface FavoriteProjectRepository : JpaRepository<FavoriteProject, Long> {

    /** 사용자가 프로젝트를 즐겨찾기했는지 확인한다 (v1.6 `FavoriteProject.findByProjectId`). */
    fun existsByUserIdAndProjectId(userId: Long, projectId: Long): Boolean

    /** 프로젝트를 즐겨찾기한 모든 행을 조회한다 (v1.6 `FavoriteProject.updateFavoriteProject`). */
    fun findByProjectId(projectId: Long): List<FavoriteProject>
}
