package dev.yona.web.access

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 프로젝트 설정에서 이슈, 마일스톤 메뉴를 끄면 그 기능의 주소도 막히는지 검증한다(issue-F24, 선생님 결정 2026-11-11).
 * v1.6은 메뉴만 숨기고 주소로는 계속 열려 있었다. 데이터는 지우지 않으므로 메뉴를 다시 켜면 그대로 열린다.
 */
@AutoConfigureMockMvc
@Transactional
class ProjectMenuOffAccessTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository

    private fun fixture(issue: Boolean, milestone: Boolean): Triple<Project, MockHttpSession, ProjectMenuSetting> {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "mo1", name = "mo1", email = "mo1@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val project = projects.save(Project(owner = "moo", name = "mop", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        val setting = menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = issue, pullRequest = true, review = true, milestone = milestone, board = true))
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", "mo1").param("password", "1234")) }
        return Triple(project, session, setting)
    }

    @Test
    fun `이슈 메뉴가 꺼지면 이슈 목록과 새 이슈 화면은 404이고 켜면 열린다`() {
        val (project, session, setting) = fixture(issue = false, milestone = true)
        val base = "/${project.owner}/${project.name}"
        mockMvc.perform(get("$base/issues").session(session)).andExpect(status().isNotFound)
        mockMvc.perform(get("$base/issueform").session(session)).andExpect(status().isNotFound)
        mockMvc.perform(get("$base/issue/1").session(session)).andExpect(status().isNotFound)
        setting.issue = true; menuSettings.save(setting)
        mockMvc.perform(get("$base/issues").session(session)).andExpect(status().isOk)
    }

    @Test
    fun `마일스톤 메뉴가 꺼지면 마일스톤 목록과 새 마일스톤 화면은 404이고 이슈는 열린다`() {
        val (project, session, _) = fixture(issue = true, milestone = false)
        val base = "/${project.owner}/${project.name}"
        mockMvc.perform(get("$base/milestones").session(session)).andExpect(status().isNotFound)
        mockMvc.perform(get("$base/newMilestoneForm").session(session)).andExpect(status().isNotFound)
        mockMvc.perform(get("$base/issues").session(session)).andExpect(status().isOk)
    }
}
