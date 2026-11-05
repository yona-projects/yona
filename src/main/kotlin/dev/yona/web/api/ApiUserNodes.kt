package dev.yona.web.api

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.security.AvatarService
import dev.yona.web.HttpPreference
import jakarta.persistence.EntityManager
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

/**
 * 자동 완성 API가 함께 쓰는 사용자 후보 검색과 JSON 노드 구성이다. v1.6 `IssueApi`의 `getUserExpressionList`, `addUserToUsers`,
 * `addUserToUsersWithCustomName`, `gatheringUsersFromExpressionList`에 해당한다.
 */
@Component
class ApiUserNodes(
    private val avatars: AvatarService,
    private val projectUsers: ProjectUserRepository,
    private val organizationUsers: OrganizationUserRepository,
    private val entityManager: EntityManager,
    private val messages: MessageSource
) {

    /** 활성 사용자를 로그인 ID, 이름, 영문 이름에서 찾는다. [type]이 있으면 그 항목이 [query]와 같은 사용자만이다. */
    fun search(query: String, type: String?): List<User> {
        val where = if (type.isNullOrBlank()) {
            "(lower(u.loginId) like lower(concat('%', :q, '%')) escape '\\' or lower(u.name) like lower(concat('%', :q, '%')) escape '\\' or lower(u.englishName) like lower(concat('%', :q, '%')) escape '\\')"
        } else {
            val field = TYPES[type] ?: return emptyList()
            "u.$field = :q"
        }
        return entityManager.createQuery("select u from User u where u.state = :state and $where order by u.id", User::class.java)
            .setParameter("state", UserState.ACTIVE).setParameter("q", if (type.isNullOrBlank()) dev.yona.util.LikeEscape.escape(query) else query).resultList
    }

    /** 검색 결과 중 이 프로젝트의 담당자나 공유자 후보가 될 수 있는지 본다. 공개 프로젝트는 누구나, 비공개는 구성원(과 조직 구성원)이다. */
    fun isCandidate(project: Project, user: User): Boolean {
        if (project.isPublic()) return true
        val userId = requireNotNull(user.id)
        val organizationId = project.organization?.id
        return projectUsers.existsByUserIdAndProjectId(userId, requireNotNull(project.id)) ||
            (organizationId != null && organizationUsers.existsByOrganizationIdAndUserId(organizationId, userId))
    }

    /** 사용자 노드를 이미 같은 노드가 없을 때만 덧붙인다. */
    fun userNode(user: User, viewer: User?, nodes: MutableList<Map<String, Any?>>) {
        val node = linkedMapOf<String, Any?>(
            "loginId" to user.loginId, "name" to UserDisplay.of(user, viewer?.lang), "pureNameOnly" to UserDisplay.pureName(user, viewer?.lang),
            "avatarUrl" to avatars.avatarUrl(user), "type" to "user"
        )
        if (node !in nodes) nodes += node
    }

    /** 이름을 바꿔 보여 주는 노드이다("나에게 할당" 등). 아바타는 비운다. */
    fun customNode(loginId: String?, name: String, nodes: MutableList<Map<String, Any?>>) {
        val node = linkedMapOf<String, Any?>("loginId" to loginId, "name" to name, "avatarUrl" to "")
        if (node !in nodes) nodes += node
    }

    /** `Accept`에 JSON이 들어 있는지 본다. */
    fun acceptsJson(request: HttpServletRequest): Boolean = HttpPreference.acceptsType(request.getHeader(HttpHeaders.ACCEPT), "application/json")

    /** 현재 언어의 메시지이다. 없으면 키 그대로이다. */
    fun message(key: String): String = messages.getMessage(key, null, key, LocaleContextHolder.getLocale()) ?: key

    /** JSON 본문 응답이다. */
    fun json(body: Any): ResponseEntity<*> = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)

    companion object {
        /** 화면의 자동 완성 입력란이 한 번에 보여 주는 개수와 같다 (v1.6 `UserApp.MAX_FETCH_USERS`). */
        const val MAX_FETCH_USERS = 10

        /** `type`으로 지정할 수 있는 항목이다. 화면은 `loginId`만 쓴다([decisions.md] A-24). */
        val TYPES = mapOf("loginId" to "loginId", "name" to "name", "email" to "email", "englishName" to "englishName")
    }
}
