package dev.yona.web.layout

import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUser
import java.util.Locale

/**
 * 템플릿에서 `currentUser`로 쓰는 현재 사용자 뷰이다. v1.6 템플릿이 `UserApp.currentUser()`로 부르던
 * `User` 객체(`isAnonymous`, `isGuest`, `isSiteManager`, `avatarUrl(size)`, `getPreferredLanguage`)에 해당한다.
 *
 * DB 조회가 필요한 값은 템플릿이 실제로 쓸 때만 한 번 읽는다.
 */
class ViewerContext(
    private val current: CurrentUser,
    private val siteAdmins: SiteAdminRepository,
    private val issues: IssueRepository,
    private val avatars: AvatarService,
    private val organizationUsers: OrganizationUserRepository
) {

    /** 로그인하지 않았으면 `true`이다. */
    val anonymous: Boolean get() = current.isAnonymous

    val loginId: String? get() = current.user?.loginId

    val name: String? get() = current.user?.name

    /** 게스트 계정이면 `true`이다 (v1.6 `User.isGuest`). */
    val guest: Boolean get() = current.user?.isGuest == true

    /** 사이트 관리자이면 `true`이다 (v1.6 `User.isSiteManager`). */
    val siteManager: Boolean by lazy { current.user?.id?.let { siteAdmins.existsByAdminId(it) } ?: false }

    /**
     * 사용자가 선호하는 언어이다. 저장된 언어가 없으면 서버의 기본 언어를 쓴다
     * (v1.6 `User.getPreferredLanguage`, 익명 사용자도 같다).
     */
    val preferredLanguage: String get() = current.user?.lang ?: Locale.getDefault().language

    /** 담당자로 지정된 열린 이슈의 개수이다 (v1.6 `Issue.countOpenIssuesByUser`). */
    val openIssueCount: Long by lazy {
        current.user?.id?.let { issues.countByAssigneeUserIdAndState(it, State.OPEN) } ?: 0L
    }

    /** 조직의 일반 구성원이면 `true`이다 (v1.6 `User.isMemberOf(Organization)`). */
    fun memberOf(organization: Organization?): Boolean = hasRole(organization, RoleType.ORG_MEMBER)

    /** 조직의 관리자이면 `true`이다 (v1.6 `User.isAdminOf(Organization)`). */
    fun adminOf(organization: Organization?): Boolean = hasRole(organization, RoleType.ORG_ADMIN)

    private fun hasRole(organization: Organization?, roleType: RoleType): Boolean {
        val organizationId = organization?.id ?: return false
        val userId = current.user?.id ?: return false
        return organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(organizationId, userId, roleType.id)
    }

    /** 아바타 이미지 주소이다. 로그인한 사용자에게만 쓴다. */
    fun avatarUrl(size: Int): String = current.user?.let { avatars.avatarUrl(it, size) } ?: AvatarService.DEFAULT_AVATAR_URL
}
