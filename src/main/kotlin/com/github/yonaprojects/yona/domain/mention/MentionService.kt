package com.github.yonaprojects.yona.domain.mention

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User

interface MentionService {
    // yona Mention.java:33-49 update(Resource, Set<User>) 대응. [GL-models_Mention-004;GL-models_Mention-005;GL-models_Mention-006;GL-models_Mention-007;GL-models_Mention-008]
    fun update(resourceType: ResourceType, resourceId: String, mentionedUsers: Set<User>)

    // yona Mention.java:51-72 getMentioningIssueIds(Long) 대응.
    fun getMentioningIssueIds(userId: Long): List<Long>
}
