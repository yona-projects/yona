package dev.yona.domain.notification

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 멘션 기록 저장소이다. */
interface MentionRepository : JpaRepository<Mention, Long> {

    /** 글이나 댓글에 걸린 멘션을 찾는다. */
    fun findByResourceTypeAndResourceId(resourceType: ResourceType, resourceId: String): List<Mention>

    /** 사용자가 멘션된 [resourceType] 기록을 찾는다 (v1.6 `Mention.getMentioningIssueIds`). */
    fun findByUserIdAndResourceType(userId: Long, resourceType: ResourceType): List<Mention>
}

/**
 * 본문의 `@멘션`을 읽어 멘션 기록(`mention`)을 맞춘다. v1.6 `NotificationEvent.getMentionedUsers`와
 * `Mention.update`(`app/models/NotificationEvent.java:1518-1580`, `app/models/Mention.java`)에 해당한다.
 *
 * `@아이디`는 그 사용자, `@조직이름`은 조직 구성원 모두, `@소유자/프로젝트`는 프로젝트 구성원 모두를 뜻한다.
 */
@Service
class MentionService(
    private val mentions: MentionRepository,
    private val users: UserRepository,
    private val organizations: OrganizationRepository,
    private val organizationUsers: OrganizationUserRepository,
    private val projectService: ProjectService,
    private val projectUsers: ProjectUserRepository
) {

    /** [body]에서 멘션된 사용자를 모은다. 없는 아이디는 건너뛴다. */
    @Transactional(readOnly = true)
    fun mentionedUsers(body: String): Set<User> {
        val result = linkedMapOf<Long, User>()
        PATTERN.findAll(body).forEach { match ->
            val word = match.value.substring(1)
            (organizationMembers(word) + projectMembers(word) + listOfNotNull(users.findByLoginIdIgnoreCase(word))).forEach { user ->
                user.id?.let { result.putIfAbsent(it, user) }
            }
        }
        return LinkedHashSet(result.values)
    }

    /**
     * 글(또는 댓글)의 멘션 기록을 본문에 맞춘다. 사라진 멘션은 지우고 새로 생긴 멘션만 더한다. [body]가 `null`이면 아무것도 하지 않는다.
     */
    @Transactional
    fun update(resourceType: ResourceType, resourceId: String, body: String?) {
        if (body == null) return
        val wanted = mentionedUsers(body).associateBy { it.id }.toMutableMap()
        for (mention in mentions.findByResourceTypeAndResourceId(resourceType, resourceId)) {
            if (wanted.remove(mention.user?.id) == null) mentions.delete(mention)
        }
        wanted.values.forEach { mentions.save(Mention(resourceType = resourceType, resourceId = resourceId, user = it)) }
    }

    private fun organizationMembers(word: String): List<User> {
        val organization = organizations.findByNameIgnoreCase(word) ?: return emptyList()
        return organizationUsers.findMembers(requireNotNull(organization.id)).mapNotNull { it.user }
    }

    private fun projectMembers(word: String): List<User> {
        if (!word.contains("/")) return emptyList()
        val project = projectService.findByOwnerAndProjectName(word.substringBeforeLast("/"), word.substringAfterLast("/")) ?: return emptyList()
        return projectUsers.findMembers(requireNotNull(project.id), RoleType.SITEMANAGER.id).mapNotNull { it.user }
    }

    private companion object {
        /** v1.6 `User.LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH`. */
        val PATTERN = Regex("@[a-zA-Z0-9-/]+([_.][a-z_.A-Z0-9-/]+)*")
    }
}
