package dev.yona.web

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 폼 본문 크기 한도(`parsers.text.maxLength`, 100KB)를 검증한다. 한도를 넘으면 폼 값을 읽지 못한 것처럼 처리한다.
 *
 * 라벨 만들기(`POST issue/labels`)로 확인한다. 값이 비면 항목별 오류 JSON으로 알려 주기 때문이다.
 */
@AutoConfigureMockMvc
@Transactional
class TextBodyLimitFilterTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository

    private fun fixture(): Pair<Project, MockHttpSession> {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "tb1", name = "tb1", email = "tb1@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val project = projects.save(Project(owner = "tbo", name = "tbp", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", "tb1").param("password", "1234")) }
        return project to session
    }

    @Test
    fun `한도 이내의 폼은 그대로 읽는다`() {
        val (project, session) = fixture()
        mockMvc.perform(
            post("/${project.owner}/${project.name}/issue/labels").session(session).contentType("application/x-www-form-urlencoded")
                .content("labelName=a&labelColor=%23fff&categoryName=" + "c".repeat(100))
        ).andExpect(status().isCreated)
    }

    @Test
    fun `한도를 넘는 폼은 값이 비어 있는 것처럼 처리한다`() {
        val (project, session) = fixture()
        mockMvc.perform(
            post("/${project.owner}/${project.name}/issue/labels").session(session).contentType("application/x-www-form-urlencoded")
                .content("labelName=a&labelColor=%23fff&categoryName=c&padding=" + "p".repeat(102_400))
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.labelName[0]").value("Name is a required field."))
    }

    @Test
    fun `쿼리 문자열의 값은 한도를 넘어도 읽는다`() {
        val (project, session) = fixture()
        mockMvc.perform(
            post("/${project.owner}/${project.name}/issue/labels?labelName=a&labelColor=%23fff&categoryName=c").session(session)
                .contentType("application/x-www-form-urlencoded").content("padding=" + "p".repeat(102_400))
        ).andExpect(status().isCreated)
    }
}
