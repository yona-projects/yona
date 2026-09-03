package com.github.yonaprojects.yona.domain.mention

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MentionServiceImpl(
    private val mentionRepository: MentionRepository,
    private val issueCommentRepository: IssueCommentRepository
) : MentionService {

    // yona Mention.java:33-49 update(Resource, Set<User>) 대응 — diff-sync: 기존 행 중 새 멘션 [GL-models_Mention-004;GL-models_Mention-005;GL-models_Mention-006;GL-models_Mention-007;GL-models_Mention-008]
    // 집합에 없는 것은 삭제하고, 새로 추가된 멘션만 insert한다.
    @Transactional
    override fun update(resourceType: ResourceType, resourceId: String, mentionedUsers: Set<User>) {
        val remaining = mentionedUsers.toMutableSet()
        for (mention in mentionRepository.findByResourceTypeAndResourceId(resourceType, resourceId)) {
            if (remaining.contains(mention.user)) {
                remaining.remove(mention.user)
            } else {
                mentionRepository.delete(mention)
            }
        }
        for (user in remaining) {
            mentionRepository.save(Mention(resourceType = resourceType, resourceId = resourceId, user = user))
        }
    }

    // yona Mention.java:51-72 getMentioningIssueIds(Long) 대응 — ISSUE_POST 행은 그대로, ISSUE_COMMENT
    // 행은 그 댓글이 달린 부모 이슈 id로 치환해 합친다.
    override fun getMentioningIssueIds(userId: Long): List<Long> {
        val ids = mutableSetOf<Long>()
        val commentIds = mutableSetOf<Long>()

        for (mention in mentionRepository.findByUserIdAndResourceTypeIn(
            userId,
            listOf(ResourceType.ISSUE_POST, ResourceType.ISSUE_COMMENT)
        )) {
            when (mention.resourceType) {
                ResourceType.ISSUE_POST -> mention.resourceId.toLongOrNull()?.let { ids.add(it) }
                ResourceType.ISSUE_COMMENT -> mention.resourceId.toLongOrNull()?.let { commentIds.add(it) }
                else -> {}
            }
        }

        if (commentIds.isNotEmpty()) {
            issueCommentRepository.findAllById(commentIds).forEach { comment ->
                comment.issue.id?.let { ids.add(it) }
            }
        }

        return ids.toList()
    }
}
