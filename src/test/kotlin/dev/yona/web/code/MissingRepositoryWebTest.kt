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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 저장소 디렉터리가 없는 프로젝트(이관으로 저장소를 옮기지 않았거나 이슈 트래커로만 쓰는 프로젝트)의 화면을 검증한다.
 * v1.6은 저장소를 열 때 존재를 요구하지 않아(`GitRepository.buildGitRepository`, `setMustExist` 없음) 디렉터리가 없으면 **빈 저장소와 같게** 동작한다.
 * 실제 v1.6(1.16.0)에서 관찰한 값: 코드 화면 200("The repository is empty!"), 프로젝트 설정 200, 커밋 404, 풀 리퀘스트 보내기 400.
 * 읽기 화면이 500이 되거나 요청이 디렉터리를 만들면 안 된다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-missing-repository"])
class MissingRepositoryWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    private fun manager(project: Project): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "mr$n", name = "mr$n", email = "mr$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        return user
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    /** 프로젝트 행만 있고 저장소는 만들지 않는다([withRepository]가 `true`이면 빈 저장소를 만든다). */
    private fun project(withRepository: Boolean): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "mro$n", name = "mrp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (withRepository) repositories.createRepository(p)
        return p
    }

    private val screens = listOf("/code", "/code/master", "/commits", "/commits/master", "/tags", "/settingform", "/newPullRequestForm", "/pullRequests", "/issues", "/milestones")

    private fun statuses(p: Project, session: MockHttpSession): Map<String, Int> =
        screens.associateWith { mockMvc.perform(get("/${p.owner}/${p.name}$it").session(session)).andReturn().response.status }

    @Test
    fun `저장소 디렉터리가 없는 프로젝트의 화면은 빈 저장소가 있는 프로젝트와 같은 상태 코드이다`() {
        val missing = project(withRepository = false); val empty = project(withRepository = true)
        val missingStatuses = statuses(missing, login(manager(missing)))
        val emptyStatuses = statuses(empty, login(manager(empty)))
        assertEquals(emptyStatuses, missingStatuses, "저장소가 없을 때와 빈 저장소일 때의 상태 코드가 같아야 한다")
        assertTrue(missingStatuses.values.none { it >= 500 }, "서버 오류가 나면 안 된다: $missingStatuses")
    }

    @Test
    fun `저장소가 없어도 v1_6처럼 코드 화면과 프로젝트 설정은 열린다`() {
        val p = project(withRepository = false)
        val session = login(manager(p))
        assertEquals(200, mockMvc.perform(get("/${p.owner}/${p.name}/code").session(session)).andReturn().response.status, "코드 화면은 빈 저장소 안내이다")
        assertEquals(200, mockMvc.perform(get("/${p.owner}/${p.name}/settingform").session(session)).andReturn().response.status)
        assertEquals(404, mockMvc.perform(get("/${p.owner}/${p.name}/commits").session(session)).andReturn().response.status)
    }

    @Test
    fun `읽기 화면은 없는 저장소 디렉터리를 만들지 않는다`() {
        val p = project(withRepository = false)
        val session = login(manager(p))
        statuses(p, session)
        assertFalse(File("build/tmp/it-missing-repository/repo/git/${p.owner}/${p.name}.git").exists(), "화면을 보는 것만으로 저장소가 생기면 안 된다")
    }
}
