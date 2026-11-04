package dev.yona.web

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RequestState
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.OrganizationMemberRequestEvent
import dev.yona.domain.notification.ProjectMemberRequestEvent
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
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
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 프로젝트와 조직의 가입 신청, 신청 취소를 검증한다.
 *
 * 원본: `app/controllers/EnrollProjectApp.java`, `app/controllers/EnrollOrganizationApp.java`, `app/models/User.java:634-683`.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class EnrollTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var events: ApplicationEvents

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "en$n", name = "en$n", email = "en$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "eno$n", name = "ep$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun newOrg(): Organization = organizations.save(Organization(name = "eorg${++sequence}", created = Date()))

    private fun post(path: String, session: MockHttpSession?): ResultActions =
        mockMvc.perform(post(path).apply { session?.let { session(it) } })

    // --- 프로젝트 ---

    @Test
    fun `프로젝트에 속하지 않은 사용자가 가입을 신청하면 신청이 저장되고 이벤트가 알려진다`() {
        val project = newProject()
        val applicant = newUser()
        post("/${project.owner}/${project.name}/enroll", login(applicant)).andExpect(status().isOk).andExpect(content().string(""))
        assertTrue(users.hasEnrolled(applicant.id!!, project.id!!))
        assertEquals(listOf(RequestState.REQUEST), events.stream(ProjectMemberRequestEvent::class.java).map { it.state }.toList())
    }

    @Test
    fun `이미 신청했으면 다시 신청해도 이벤트를 또 알리지 않는다`() {
        val project = newProject()
        val applicant = newUser()
        val session = login(applicant)
        post("/${project.owner}/${project.name}/enroll", session)
        post("/${project.owner}/${project.name}/enroll", session).andExpect(status().isOk)
        assertEquals(1, events.stream(ProjectMemberRequestEvent::class.java).count())
    }

    @Test
    fun `신청을 취소하면 신청이 지워지고 취소 이벤트가 알려진다`() {
        val project = newProject()
        val applicant = newUser()
        val session = login(applicant)
        post("/${project.owner}/${project.name}/enroll", session)
        post("/${project.owner}/${project.name}/cancel/enroll", session).andExpect(status().isOk)
        assertFalse(users.hasEnrolled(applicant.id!!, project.id!!))
        assertEquals(listOf(RequestState.REQUEST, RequestState.CANCEL), events.stream(ProjectMemberRequestEvent::class.java).map { it.state }.toList())
    }

    @Test
    fun `신청하지 않은 채 취소해도 오류가 아니다`() {
        val project = newProject()
        post("/${project.owner}/${project.name}/cancel/enroll", login(newUser())).andExpect(status().isOk)
        assertEquals(0, events.stream(ProjectMemberRequestEvent::class.java).count())
    }

    @Test
    fun `이미 구성원이면 신청할 수 없다`() {
        val project = newProject()
        val member = newUser()
        projectUsers.save(ProjectUser(user = member, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        post("/${project.owner}/${project.name}/enroll", login(member)).andExpect(status().isBadRequest)
        post("/${project.owner}/${project.name}/cancel/enroll", login(member)).andExpect(status().isBadRequest)
        assertFalse(users.hasEnrolled(member.id!!, project.id!!))
    }

    @Test
    fun `프로젝트 신청은 로그인해야 하고 없는 프로젝트는 오류이다`() {
        val project = newProject()
        post("/${project.owner}/${project.name}/enroll", null).andExpect(status().isSeeOther)
        post("/nobody/nothing/enroll", login(newUser())).andExpect(status().is4xxClientError)
    }

    @Test
    fun `로그인하지 않아도 없는 프로젝트의 신청과 취소는 403이다 (v1_6 DefaultProjectCheck가 AnonymousCheck보다 바깥이다)`() {
        post("/nobody/nothing/enroll", null).andExpect(status().isForbidden)
        post("/nobody/nothing/cancel/enroll", null).andExpect(status().isForbidden)
    }

    // --- 조직 ---

    @Test
    fun `조직에 속하지 않은 사용자가 가입을 신청하면 202와 안내를 받는다`() {
        val org = newOrg()
        val applicant = newUser()
        post("/organizations/${org.name}/enroll", login(applicant))
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.message").value("You enrolled in ${org.name}."))
            .andExpect(jsonPath("$.statusMonitorUrl").value("http://localhost/organizations/${org.name}"))
        assertTrue(users.hasEnrolledOrganization(applicant.id!!, org.id!!))
        assertEquals(listOf(RequestState.REQUEST), events.stream(OrganizationMemberRequestEvent::class.java).map { it.state }.toList())
    }

    @Test
    fun `조직 신청을 취소하면 202와 안내를 받는다`() {
        val org = newOrg()
        val applicant = newUser()
        val session = login(applicant)
        post("/organizations/${org.name}/enroll", session)
        post("/organizations/${org.name}/cancel/enroll", session)
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.message").value("You canceled to enroll in ${org.name}."))
        assertFalse(users.hasEnrolledOrganization(applicant.id!!, org.id!!))
        assertEquals(listOf(RequestState.REQUEST, RequestState.CANCEL), events.stream(OrganizationMemberRequestEvent::class.java).map { it.state }.toList())
    }

    @Test
    fun `이미 구성원이 되었어도 남아 있는 신청 기록은 취소할 수 있다 (C097)`() {
        val org = newOrg()
        val applicant = newUser()
        val session = login(applicant)
        post("/organizations/${org.name}/enroll", session)
        // 신청이 받아들여져 구성원이 되었지만 신청 기록이 남은 상태이다. v1.6은 구성원이면 취소 요청을 400으로 막아 기록을 지울 수 없었다
        organizationUsers.save(OrganizationUser(user = applicant, organization = org, role = roles.findById(RoleType.ORG_MEMBER.id).get()))
        post("/organizations/${org.name}/cancel/enroll", session).andExpect(status().isAccepted)
        assertFalse(users.hasEnrolledOrganization(applicant.id!!, org.id!!))
    }

    @Test
    fun `조직 구성원이거나 익명이거나 없는 조직이면 400이다`() {
        val org = newOrg()
        val member = newUser()
        organizationUsers.save(OrganizationUser(user = member, organization = org, role = roles.findById(RoleType.ORG_MEMBER.id).get()))
        post("/organizations/${org.name}/enroll", login(member)).andExpect(status().isBadRequest)
        post("/organizations/${org.name}/cancel/enroll", login(member)).andExpect(status().isBadRequest)
        post("/organizations/${org.name}/enroll", null).andExpect(status().isBadRequest)
        post("/organizations/nothing/enroll", login(newUser())).andExpect(status().isBadRequest)
    }
}
