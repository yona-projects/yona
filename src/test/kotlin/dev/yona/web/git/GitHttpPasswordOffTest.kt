package dev.yona.web.git

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Base64

/**
 * 설정 키 `yona.git-http-password-auth=false`이면 Git의 HTTP 접근이 비밀번호를 받지 않고 토큰만 받는지 검증한다(X-5, D-36 12번).
 * 비밀번호 시도는 실패로 세지 않는다(설정을 아직 고치지 못한 사용자가 토큰을 만든 뒤에도 막혀 있지 않게 한다).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-git-token-off", "yona.git-http-password-auth=false"])
class GitHttpPasswordOffTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var tokens: ApiTokenService

    private fun basic(user: String, password: String) = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())

    @Test
    fun `비밀번호는 받지 않고 토큰은 받는다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        val owner = users.save(User(loginId = "gpo1", name = "gpo1", email = "gpo1@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val p = projects.save(Project(owner = owner.loginId, name = "gpoff1", vcs = "GIT", projectScope = ProjectScope.PRIVATE))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = owner, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        val t = (tokens.create(owner, "git", setOf("repo"), null) as TokenCreation.Created).value
        fun refs(password: String) = mockMvc.perform(get("/${p.owner}/${p.name}/info/refs").param("service", "git-receive-pack").header("Authorization", basic(owner.loginId!!, password)))
        refs("1234").andExpect(status().isUnauthorized)
        repeat(10) { refs("1234").andExpect(status().isUnauthorized) }
        refs(t).andExpect(status().isOk)
    }
}
