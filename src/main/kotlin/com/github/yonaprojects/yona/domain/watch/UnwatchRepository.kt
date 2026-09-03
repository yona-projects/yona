package com.github.yonaprojects.yona.domain.watch

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UnwatchRepository : JpaRepository<Unwatch, Long> {
    fun findByResourceTypeAndResourceId(resourceType: ResourceType, resourceId: String): List<Unwatch>
    fun findByUserAndResourceTypeAndResourceId(user: User, resourceType: ResourceType, resourceId: String): Unwatch?
    fun findByUserAndResourceType(user: User, resourceType: ResourceType): List<Unwatch>
}
