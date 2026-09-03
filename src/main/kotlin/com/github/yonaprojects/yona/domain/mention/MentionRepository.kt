package com.github.yonaprojects.yona.domain.mention

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import org.springframework.data.jpa.repository.JpaRepository

interface MentionRepository : JpaRepository<Mention, Long> {
    // yona Mention.java:33-49 update()의 조회 대응. [GL-models_Mention-004;GL-models_Mention-005;GL-models_Mention-006;GL-models_Mention-007;GL-models_Mention-008]
    fun findByResourceTypeAndResourceId(resourceType: ResourceType, resourceId: String): List<Mention>

    // yona Mention.java:51-72 getMentioningIssueIds()의 조회 대응.
    fun findByUserIdAndResourceTypeIn(userId: Long, resourceTypes: List<ResourceType>): List<Mention>
}
