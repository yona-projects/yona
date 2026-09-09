package com.github.yonaprojects.yona.domain.mention

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User

interface MentionService {
    // yona Mention.update(Resource, Set<User>) 대응.
    fun update(resourceType: ResourceType, resourceId: String, mentionedUsers: Set<User>)

    // yona Mention.getMentioningIssueIds(Long) 대응.
    fun getMentioningIssueIds(userId: Long): List<Long>
}
