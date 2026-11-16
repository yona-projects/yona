package dev.yona.web.code

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
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** SSH를 켜지 않은 사이트의 Subversion 빈 저장소 안내는 HTTP 인증(토큰) 설명만 보여 주고 SSH 설명은 없다(X-55). */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-nohead-svn-auth"])
class NoheadSvnAuthNoteTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService

    @Test
    fun `SSH를 끈 사이트의 Subversion 안내에는 토큰 설명만 있다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "nsa1", name = "nsa1", email = "nsa1@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val p = projects.save(Project(owner = "nsao1", name = "nsap1", vcs = "Subversion", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = user, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", "nsa1").param("password", "1234")) }
        val doc = Jsoup.parse(mockMvc.perform(get("/${p.owner}/${p.name}/code").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertNotNull(doc.selectFirst(".nohead-auth-http"))
        assertNull(doc.selectFirst(".nohead-auth-ssh"), "SSH를 켜지 않았으면 SSH 설명이 없어야 한다")
    }
}
