package dev.yona.web.organization

import dev.yona.security.SessionPermissions
import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import org.springframework.stereotype.Component

/**
 * 조직 화면 템플릿이 쓰는 조직 정보 묶음이다. v1.6 템플릿이 `Organization`의 메서드와 정적 도우미
 * (`urlToOrganizationLogo`, `OrganizationUser.isGuest`, `User.enrolled` 등)로 구하던 값들을 한곳에서 제공한다.
 * 템플릿에서는 모델 이름 `orgView`로 접근한다.
 *
 * @property organization 대상 조직
 * @property viewer 화면을 보는 사용자. 익명이면 `null`
 */
class OrganizationViewContext(
    val organization: Organization,
    private val viewer: User?,
    private val deps: Dependencies
) {

    class Dependencies(
        val attachments: AttachmentRepository,
        val organizationUsers: OrganizationUserRepository,
        val users: UserRepository,
        val permissions: SessionPermissions,
        val accessControl: AccessControl,
        val avatars: AvatarService
    )

    /** 구성원 영역의 한 사람이다. */
    data class MemberItem(val loginId: String, val displayName: String, val avatarUrl: String)

    private val orgId: Long get() = requireNotNull(organization.id)
    private val viewerId: Long? get() = viewer?.id

    val name: String get() = organization.name.orEmpty()
    val descr: String? get() = organization.descr

    private val logoId: Long? by lazy {
        deps.attachments.findByContainerTypeAndContainerIdOrderById(ResourceType.ORGANIZATION, orgId).firstOrNull()?.id
    }

    /** 조직 로고가 있으면 `true`이다 (v1.6 `hasOrganizationLogo`). */
    val hasLogo: Boolean get() = logoId != null

    /** 조직 로고 주소이다. 없으면 기본 그림이다 (v1.6 `urlToOrganizationLogo`). */
    val logoUrl: String get() = logoId?.let { "/files/$it" } ?: DEFAULT_LOGO

    /** 사이트 관리자이면 `true`이다. */
    val siteManager: Boolean by lazy { deps.permissions.siteManager(viewerId) }

    /** 조직 관리자이면 `true`이다 (v1.6 `OrganizationUser.isAdmin`). */
    val admin: Boolean by lazy { hasRole(RoleType.ORG_ADMIN) }

    /** 조직 구성원(관리자가 아닌 일반 구성원)이면 `true`이다 (v1.6 `OrganizationUser.isMember`). */
    val member: Boolean by lazy { deps.permissions.organizationRole(viewerId, orgId, RoleType.ORG_MEMBER) }

    /**
     * 조직에 속하지 않은 로그인 사용자이면 `true`이다 (v1.6 `OrganizationUser.isGuest`). 사이트 관리자와 익명은 해당하지 않는다.
     */
    val guest: Boolean by lazy {
        val id = viewerId ?: return@lazy false
        !siteManager && !deps.organizationUsers.existsByOrganizationIdAndUserId(orgId, id)
    }

    /** 가입을 신청한 상태이면 `true`이다 (v1.6 `User.enrolled(organization)`). */
    val enrolled: Boolean by lazy { viewerId?.let { deps.users.hasEnrolledOrganization(it, orgId) } ?: false }

    /** 설정 메뉴를 보여도 되면 `true`이다 (조직 관리자 또는 사이트 관리자). */
    val canManage: Boolean get() = admin || siteManager

    /** 구성원 영역을 보여도 되면 `true`이다 (사이트 관리자, 일반 구성원, 조직 관리자). */
    val canSeeMembers: Boolean get() = siteManager || member || admin

    /**
     * 탈퇴할 수 없는 상태이면 `true`이다 (v1.6 `OrganizationApp.validateForLeave`의 오류). 조직을 고칠 수 없는 사용자이고
     * 관리자가 한 명뿐일 때 오류이다. 조건이 거꾸로 쓰여 있어서 관리자는 한 명뿐이어도 탈퇴할 수 있다([refactoring-backlog.md] R-19).
     */
    val leaveBlocked: Boolean by lazy {
        !deps.accessControl.isAllowed(viewer, organization.asResource(), Operation.LEAVE) &&
            deps.organizationUsers.countByOrganizationIdAndRoleId(orgId, RoleType.ORG_ADMIN.id) == 1L
    }

    /** 조직 관리자 목록이다. */
    val admins: List<MemberItem> by lazy { items(RoleType.ORG_ADMIN) }

    /** 일반 구성원 목록이다. */
    val members: List<MemberItem> by lazy { items(RoleType.ORG_MEMBER) }

    private fun items(roleType: RoleType): List<MemberItem> =
        deps.organizationUsers.findMembers(orgId).filter { it.role?.id == roleType.id }.mapNotNull { it.user }.map {
            MemberItem(it.loginId.orEmpty(), UserDisplay.of(it, viewer?.lang), deps.avatars.avatarUrl(it))
        }

    private fun hasRole(roleType: RoleType): Boolean =
        viewerId?.let { deps.organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(orgId, it, roleType.id) } ?: false

    companion object {
        const val DEFAULT_LOGO = "/assets/images/group_default.png"
    }
}

/** 현재 요청의 사용자로 [OrganizationViewContext]를 만든다. */
@Component
class OrganizationViewFactory(
    private val currentUserProvider: CurrentUserProvider,
    attachments: AttachmentRepository,
    organizationUsers: OrganizationUserRepository,
    users: UserRepository,
    permissions: SessionPermissions,
    accessControl: AccessControl,
    avatars: AvatarService
) {
    private val dependencies = OrganizationViewContext.Dependencies(attachments, organizationUsers, users, permissions, accessControl, avatars)

    fun of(organization: Organization): OrganizationViewContext =
        OrganizationViewContext(organization, currentUserProvider.get().user, dependencies)
}
