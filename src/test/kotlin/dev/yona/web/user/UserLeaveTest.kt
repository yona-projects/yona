package dev.yona.web.user

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * 프로젝트 탈퇴(`POST /info/leave/:user/:project`)를 검증한다. v1.6은 GET이라 링크나 이미지 태그만으로 로그인한 사용자를 탈퇴시킬 수 있었다(R-48-2).
 *
 * 원본: `app/controllers/UserApp.java:992-996`(`leave`), `app/controllers/ProjectApp.java:1004-1030`(`deleteMember`).
 */
@AutoConfigureMockMvc
@Transactional
class UserLeaveTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "lv$n", name = "lv$n", email = "lv$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun project(owner: User, name: String = "proj"): Project =
        projects.save(Project(owner = owner.loginId, name = name, vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date()))

    private fun join(user: User, project: Project, role: RoleType = RoleType.MEMBER) =
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))

    private fun isMember(user: User, project: Project) =
        projectUsers.findFirstByUserIdAndProjectIdAndRoleIdNot(user.id!!, project.id!!, RoleType.SITEMANAGER.id) != null

    @Test
    fun `GET으로는 탈퇴되지 않는다 (R-48-2)`() {
        val owner = newUser(); val me = newUser(); val p = project(owner)
        join(me, p)
        mockMvc.perform(get("/info/leave/${owner.loginId}/proj").session(login(me))).andExpect(status().isNotFound)
        assertEquals(true, isMember(me, p))
    }

    @Test
    fun `구성원이 탈퇴하면 구성원에서 빠지고 내 프로젝트 탭으로 이동한다`() {
        val owner = newUser(); val me = newUser(); val p = project(owner)
        join(me, p)
        mockMvc.perform(post("/info/leave/${owner.loginId}/proj").session(login(me)))
            .andExpect(status().isSeeOther).andExpect(redirectedUrl("/${me.loginId}?daysAgo=14&selected=projects"))
        assertEquals(false, isMember(me, p))
    }

    @Test
    fun `소유자는 탈퇴되지 않지만 같은 곳으로 이동한다`() {
        val owner = newUser(); val p = project(owner)
        join(owner, p, RoleType.MANAGER)
        mockMvc.perform(post("/info/leave/${owner.loginId}/proj").session(login(owner)))
            .andExpect(status().isSeeOther).andExpect(redirectedUrl("/${owner.loginId}?daysAgo=14&selected=projects"))
        assertEquals(true, isMember(owner, p))
    }

    @Test
    fun `구성원이 아니어도 같은 곳으로 이동하고 다른 구성원은 그대로이다`() {
        val owner = newUser(); val member = newUser(); val stranger = newUser(); val p = project(owner)
        join(member, p)
        mockMvc.perform(post("/info/leave/${owner.loginId}/proj").session(login(stranger)))
            .andExpect(status().isSeeOther).andExpect(redirectedUrl("/${stranger.loginId}?daysAgo=14&selected=projects"))
        assertEquals(true, isMember(member, p))
    }
}
