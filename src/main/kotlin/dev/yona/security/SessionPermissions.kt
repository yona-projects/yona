package dev.yona.security

import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.SiteAdminRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 사이트 관리자, 프로젝트 매니저, 조직 역할 판정의 단일 진입점이다. v1.6 `User.isSiteManager`, `isManagerOf(Project)`,
 * `isMemberOf(Organization, RoleType)`, `isAdminOf`에 해당한다.
 *
 * v1.6은 이 세 판정을 세션 사용자 객체에 처음 계산한 값으로 기억해 승격이나 해임이 로그인 동안 반영되지 않았다(R-106). 지금은 요청 하나 안에서만
 * 기억하므로 현재 요청의 **세션 사용자**에 대한 반복 질문은 DB를 한 번만 보고, 다음 요청에는 바뀐 권한이 바로 반영된다.
 *
 * 다음은 기억하지 않고 매번 DB에서 조회한다. v1.6도 새로 읽은 인스턴스나 정적 메서드를 쓰는 경우이다.
 * - API 토큰 헤더로 식별한 사용자: 요청마다 DB에서 새로 읽는다(`User.findUserIfTokenExist`).
 * - 현재 요청의 사용자가 아닌 다른 사용자에 대한 질문
 * - 요청 밖(예약 작업 등)에서의 질문
 * - 프로젝트 멤버 여부([projectMember]): v1.6 `User.isMemberOf(Project)`는 기억하지 않는다.
 */
@Component
class SessionPermissions(
    private val siteAdmins: SiteAdminRepository,
    private val projectUsers: ProjectUserRepository,
    private val organizationUsers: OrganizationUserRepository
) {

    /** 사용자가 사이트 관리자이면 `true`이다. [userId]가 `null`이면 `false`이다. */
    fun siteManager(userId: Long?): Boolean {
        if (userId == null) return false
        val compute = { siteAdmins.existsByAdminId(userId) }
        return memoOf(userId)?.siteManager(compute) ?: compute()
    }

    /** 사용자가 프로젝트의 매니저이면 `true`이다. */
    fun projectManager(userId: Long?, projectId: Long?): Boolean {
        if (userId == null || projectId == null) return false
        val compute = { projectUsers.existsByUserIdAndProjectIdAndRoleId(userId, projectId, RoleType.MANAGER.id) }
        return memoOf(userId)?.projectManager(projectId, compute) ?: compute()
    }

    /** 사용자가 프로젝트에 어떤 역할로든 속해 있으면 `true`이다. v1.6처럼 기억하지 않고 매번 조회한다. */
    fun projectMember(userId: Long?, projectId: Long?): Boolean {
        if (userId == null || projectId == null) return false
        return projectUsers.existsByUserIdAndProjectId(userId, projectId)
    }

    /** 사용자가 조직에서 [roleType] 역할을 가지고 있으면 `true`이다. */
    fun organizationRole(userId: Long?, organizationId: Long?, roleType: RoleType): Boolean {
        if (userId == null || organizationId == null) return false
        val compute = { organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(organizationId, userId, roleType.id) }
        return memoOf(userId)?.orgMember(organizationId, roleType.name, compute) ?: compute()
    }

    /**
     * [userId]가 현재 요청의 세션 사용자일 때만 그 사용자의 기억을 돌려준다. API 토큰 사용자이거나 다른 사용자이거나 요청 밖이면 `null`이다.
     */
    private fun memoOf(userId: Long): SessionUserMemo? {
        val authentication = SecurityContextHolder.getContext().authentication as? YonaAuthentication ?: return null
        if (authentication.viaToken || authentication.principal.userId != userId) return null
        // 판정은 요청 하나 안에서만 기억한다. v1.6은 로그인 동안 기억해 승격과 해임이 반영되지 않았다(R-106)
        val attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() ?: return null
        val key = "dev.yona.security.requestMemo.$userId"
        return attributes.getAttribute(key, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST) as? SessionUserMemo
            ?: SessionUserMemo().also { attributes.setAttribute(key, it, org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST) }
    }
}
