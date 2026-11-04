package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 외부 Git 저장소 가져오기(`/_import`)를 검증한다. 가져올 저장소는 같은 서버의 다른 프로젝트 저장소를 `file:` 주소로 가리킨다.
 *
 * 원본: `app/controllers/ImportApp.java`, `app/views/project/importing.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class ImportSourceRestrictionTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var repositories: dev.yona.repository.RepositoryService

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "isr$n", name = "isr$n", email = "isr$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun send(session: MockHttpSession, owner: String, url: String) =
        post("/_import").session(session).param("owner", owner).param("name", "blocked${++sequence}").param("url", url).param("projectScope", "PUBLIC").param("vcs", "GIT").param("code", "true")

    @Test
    fun `file 주소와 경로와 서버 안의 주소로는 가져오지 못한다 (R-46-3)`() {
        val me = user(); val session = login(me)
        // v1.6은 가져올 주소를 그대로 JGit에 넘겨 로그인한 누구나 서버 안의 다른 저장소(비공개 포함)를 복제해 읽을 수 있었다
        val n = ++sequence
        val source = projects.save(Project(owner = "isrsrc$n", name = "src$n", vcs = "GIT", projectScope = ProjectScope.PRIVATE))
        repositories.createRepository(source)
        dev.yona.support.GitTestSupport.pushCommits(repositories, source, dev.yona.support.GitTestSupport.TestCommit("비공개", mapOf("secret.txt" to "x".toByteArray())))
        val directory = repositories.openRepository(source).use { it.directory }
        for (bad in listOf(directory.toURI().toString(), directory.absolutePath, "http://127.0.0.1:9/x.git", "http://localhost:9/x.git")) {
            val html = mockMvc.perform(send(session, me.loginId!!, bad)).andExpect(status().isBadRequest).andReturn().response.contentAsString
            org.junit.jupiter.api.Assertions.assertTrue(html.contains("\"url\""), "$bad -> ${html.take(200)}")
        }
    }
}
