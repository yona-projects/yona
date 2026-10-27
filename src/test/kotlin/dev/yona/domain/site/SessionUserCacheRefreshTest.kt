package dev.yona.domain.site

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationManagementService
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMemberService
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.SessionUserCache
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * v1.6이 `CacheStore.yonaUsers`를 갱신하거나 무효화하는 시점을 v2가 같게 따르는지 검증한다.
 *
 * 원본: `SiteApp.java:202`(관리자 권한 변경은 `invalidate`), `SiteApp.java:270`(게스트 모드), `User.java:719`(`changeState`),
 * `Project.cleanEnrolledUsers`(`User.cancelEnroll`의 `put`). 새 인스턴스로 `put`하면 기억된 판정이 비워진다.
 */
@Transactional
class SessionUserCacheRefreshTest : AbstractIntegrationTest() {

    @Autowired private lateinit var siteUsers: SiteUserService
    @Autowired private lateinit var sessionUsers: SessionUserCache
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var members: ProjectMemberService
    @Autowired private lateinit var organizationService: OrganizationManagementService
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository

    private var sequence = 0

    private fun newUser(): User =
        users.save(User(loginId = "rf${++sequence}", name = "rf$sequence", email = "rf$sequence@example.com", state = UserState.ACTIVE))

    @Test
    fun `사이트 관리자 권한을 바꾸면 대상의 서버 캐시 항목이 사라진다 (다음 요청의 세션이 무효가 된다)`() {
        val user = newUser()
        sessionUsers.remember(user.id!!)
        siteUsers.toggleSiteAdmin(user)
        assertFalse(sessionUsers.contains(user.id!!))
    }

    @Test
    fun `게스트 모드를 바꾸면 대상의 항목이 새것으로 바뀐다`() {
        val user = newUser()
        sessionUsers.remember(user.id!!)
        val before = sessionUsers.memoOf(user.id!!)
        siteUsers.toggleGuest(user)
        assertNotSame(before, sessionUsers.memoOf(user.id!!))
    }

    @Test
    fun `상태를 바꾸거나 삭제하면 대상의 항목이 새것으로 바뀐다`() {
        val locked = newUser(); val deleted = newUser()
        sessionUsers.remember(locked.id!!); sessionUsers.remember(deleted.id!!)
        val lockedBefore = sessionUsers.memoOf(locked.id!!); val deletedBefore = sessionUsers.memoOf(deleted.id!!)
        siteUsers.changeState(locked, UserState.LOCKED)
        siteUsers.deleteUser(deleted)
        assertNotSame(lockedBefore, sessionUsers.memoOf(locked.id!!))
        assertNotSame(deletedBefore, sessionUsers.memoOf(deleted.id!!))
    }

    @Test
    fun `구성원이 된 사용자의 가입 신청을 정리하면 그 사용자의 항목이 새것으로 바뀐다`() {
        val user = newUser(); val other = newUser()
        val project = projects.save(Project(owner = "rfo${++sequence}", name = "rfp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        user.enrolledProjects.add(project); other.enrolledProjects.add(project)
        users.save(user); users.save(other)
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        sessionUsers.remember(user.id!!); sessionUsers.remember(other.id!!)
        val memberBefore = sessionUsers.memoOf(user.id!!); val otherBefore = sessionUsers.memoOf(other.id!!)
        members.cleanEnrolledUsers(project, null)
        assertNotSame(memberBefore, sessionUsers.memoOf(user.id!!))
        assertSame(otherBefore, sessionUsers.memoOf(other.id!!), "구성원이 아닌 신청자는 정리 대상이 아니다")
        assertTrue(sessionUsers.contains(other.id!!))
    }

    @Test
    fun `구성원이 된 사용자의 조직 가입 신청을 정리하면 그 사용자의 항목이 새것으로 바뀐다`() {
        val user = newUser()
        val organization = organizations.save(Organization(name = "rfg${++sequence}"))
        user.enrolledOrganizations.add(organization)
        users.save(user)
        organizationUsers.save(OrganizationUser(user = user, organization = organization, role = roles.findById(RoleType.ORG_MEMBER.id).get()))
        sessionUsers.remember(user.id!!)
        val before = sessionUsers.memoOf(user.id!!)
        organizationService.cleanEnrolledUsers(organization, null)
        assertNotSame(before, sessionUsers.memoOf(user.id!!))
    }
}
