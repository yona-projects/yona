package com.github.yonaprojects.yona.domain.watch

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface WatchRepository : JpaRepository<Watch, Long> {
    fun findByResourceTypeAndResourceId(resourceType: ResourceType, resourceId: String): List<Watch>
    fun findByUserAndResourceTypeAndResourceId(user: User, resourceType: ResourceType, resourceId: String): Watch?
    fun findByUserAndResourceType(user: User, resourceType: ResourceType): List<Watch>
    fun countByResourceTypeAndResourceId(resourceType: ResourceType, resourceId: String): Long
}
