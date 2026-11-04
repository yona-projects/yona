package dev.yona.security

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * 세션 사용자의 권한 판정 기억이 v1.6 `User`의 `siteManager`, `projectManagerMemo`, `orgMembersMemo`처럼 동작하는지 검증한다.
 *
 * 원본: `app/models/User.java:564-577`(판정 기억), `:211-228`(조직 판정), `:579-582`(프로젝트 멤버는 기억하지 않음).
 */
@Transactional
class SessionPermissionsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var permissions: SessionPermissions
    @Autowired private lateinit var sessionUsers: SessionUserCache
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var roles: RoleRepository

    private var sequence = 0

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    /** 다음 요청이 시작된 것처럼 요청 범위 기억을 비운다. 시험 환경은 시험 하나를 요청 하나로 두기 때문이다. */
    private fun nextRequest() = org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
        org.springframework.web.context.request.ServletRequestAttributes(org.springframework.mock.web.MockHttpServletRequest())
    )

    private fun newUser(): User =
        users.save(User(loginId = "sp${++sequence}", name = "sp$sequence", email = "sp$sequence@example.com", state = UserState.ACTIVE))

    private fun newProject(): Project =
        projects.save(Project(owner = "spo${++sequence}", name = "spp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))

    /** 이 사용자를 현재 요청의 세션 사용자로 만들고 서버 캐시에 기억시킨다 (로그인 직후와 같다). */
    private fun asSessionUser(user: User, viaToken: Boolean = false) {
        SecurityContextHolder.getContext().authentication = YonaAuthentication.of(user, viaToken = viaToken)
        sessionUsers.remember(requireNotNull(user.id))
    }

    @Test
    fun `사이트 관리자 여부는 바로 반영된다 (R-106, v1_6은 처음 계산한 값이 로그인 동안 유지되었다)`() {
        val user = newUser()
        asSessionUser(user)
        assertEquals(false, permissions.siteManager(user.id))
        siteAdmins.save(SiteAdmin(admin = user))
        assertEquals(false, permissions.siteManager(user.id), "같은 요청 안에서는 처음 계산한 값을 쓴다")
        nextRequest()
        assertEquals(true, permissions.siteManager(user.id), "다음 요청에는 권한이 바뀐 것이 바로 반영된다")
    }

    @Test
    fun `API 토큰으로 식별한 사용자는 요청마다 새로 조회한다`() {
        val user = newUser()
        asSessionUser(user, viaToken = true)
        assertEquals(false, permissions.siteManager(user.id))
        siteAdmins.save(SiteAdmin(admin = user))
        assertEquals(true, permissions.siteManager(user.id))
    }

    @Test
    fun `현재 사용자가 아닌 사용자의 판정은 기억하지 않는다`() {
        val me = newUser(); val other = newUser()
        asSessionUser(me); sessionUsers.remember(other.id!!)
        assertEquals(false, permissions.siteManager(other.id))
        siteAdmins.save(SiteAdmin(admin = other))
        assertEquals(true, permissions.siteManager(other.id))
    }

    @Test
    fun `요청 밖이나 익명 요청에서는 항상 새로 조회한다`() {
        val user = newUser()
        assertEquals(false, permissions.siteManager(user.id))
        siteAdmins.save(SiteAdmin(admin = user))
        assertEquals(true, permissions.siteManager(user.id))
        assertEquals(false, permissions.siteManager(null))
    }

    @Test
    fun `프로젝트 매니저 여부는 프로젝트마다 기억하고 프로젝트 멤버 여부는 기억하지 않는다`() {
        val user = newUser(); val project = newProject(); val other = newProject()
        asSessionUser(user)
        assertEquals(false, permissions.projectManager(user.id, project.id))
        assertEquals(false, permissions.projectMember(user.id, project.id))
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        nextRequest()
        assertEquals(true, permissions.projectManager(user.id, project.id), "다음 요청에는 권한이 바뀐 것이 바로 반영된다")
        assertEquals(true, permissions.projectMember(user.id, project.id), "프로젝트 멤버 여부는 매번 조회한다")
        projectUsers.save(ProjectUser(user = user, project = other, role = roles.findById(RoleType.MANAGER.id).get()))
        assertEquals(true, permissions.projectManager(user.id, other.id), "다른 프로젝트는 처음 계산한다")
    }

    @Test
    fun `조직 역할은 조직과 역할마다 기억한다`() {
        val user = newUser()
        val org = organizations.save(Organization(name = "spo${++sequence}"))
        asSessionUser(user)
        assertEquals(false, permissions.organizationRole(user.id, org.id, RoleType.ORG_ADMIN))
        organizationUsers.save(OrganizationUser(user = user, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get()))
        nextRequest()
        assertEquals(true, permissions.organizationRole(user.id, org.id, RoleType.ORG_ADMIN), "다음 요청에는 권한이 바뀐 것이 바로 반영된다")
        assertEquals(false, permissions.organizationRole(user.id, org.id, RoleType.ORG_MEMBER))
    }
}
