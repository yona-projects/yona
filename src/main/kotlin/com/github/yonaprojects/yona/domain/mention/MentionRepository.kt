package com.github.yonaprojects.yona.domain.mention

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import org.springframework.data.jpa.repository.JpaRepository

interface MentionRepository : JpaRepository<Mention, Long> {
    // legacy Mention.java update()의 조회 대응.
    fun findByResourceTypeAndResourceId(resourceType: ResourceType, resourceId: String): List<Mention>

    // legacy Mention.java getMentioningIssueIds()의 조회 대응.
    fun findByUserIdAndResourceTypeIn(userId: Long, resourceTypes: List<ResourceType>): List<Mention>
}
