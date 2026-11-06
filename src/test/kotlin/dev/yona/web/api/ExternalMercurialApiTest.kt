package dev.yona.web.api

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
import dev.yona.repository.hg.HgRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.HgTestSupport
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mercurial 프로젝트가 REST에서 Git과 같이 동작하는지 검증한다(X-7 7c): 프로젝트 만들기, 포크, 배포 키, 사람이 쓴 커밋, 태그 목록. 저장소 디렉터리가
 * 실행 사이에 남으므로 이름에 시각을 넣는다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalMercurialApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var hgRepositories: HgRepositories

    private var sequence = 0
    private val stamp = System.nanoTime()
    private val tokens = mutableMapOf<Long, String>()

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "hg$n-$stamp", name = "hg$n", email = "hg$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        tokens[saved.id!!] = "ht$n$stamp"
        dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!))
        return saved
    }

    private fun tokenOf(user: User) = tokens.getValue(user.id!!)

    /** 사용자가 관리하는 Mercurial 프로젝트이다. 사용자의 이메일로 쓴 커밋 둘과 이름표 `main`, 태그 `v1`이 있다. */
    private fun hgProject(manager: User): Project {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val n = ++sequence
        val p = projects.save(Project(owner = "hgo$n-$stamp", name = "hgp$n", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 0, lastPostingNumber = 0, createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = manager, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        val dir = hgRepositories.directoryOf(p.owner!!, p.name!!)
        val c0 = HgTestSupport.commit(dir, "첫 커밋", mapOf("a.txt" to "a\n"), user = "작성자 <${manager.email}>")
        HgTestSupport.commit(dir, "둘째 커밋", mapOf("b.txt" to "b\n"), "2026-11-02 10:00 +0900", "작성자 <${manager.email}>")
        HgTestSupport.bookmark(dir, "main")
        HgTestSupport.tag(dir, "v1", c0)
        return p
    }

    private fun json(request: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String, body: String? = null) =
        mockMvc.perform(request.header("Authorization", "token $token").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    @Test
    fun `종류를 Mercurial로 주면 Mercurial 프로젝트와 저장소를 만든다`() {
        val user = newUser()
        json(post("/-_-api/v1/user/projects"), tokenOf(user), """{"name":"hgnew$stamp","vcs":"Mercurial"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.vcs").value("Mercurial"))
        assertEquals("Mercurial", projects.findByOwnerIgnoreCaseAndNameIgnoreCase(user.loginId!!, "hgnew$stamp")!!.vcs)
        assertTrue(java.io.File(hgRepositories.directoryOf(user.loginId!!, "hgnew$stamp"), ".hg").isDirectory)
    }

    @Test
    fun `모르는 종류는 400이고 안내에 Mercurial도 들어 있다`() {
        val user = newUser()
        json(post("/-_-api/v1/user/projects"), tokenOf(user), """{"name":"hgbad$stamp","vcs":"Bazaar"}""").andExpect(status().isBadRequest).andExpect(content().string(org.hamcrest.Matchers.containsString("Mercurial")))
    }

    @Test
    fun `Mercurial 프로젝트도 REST로 포크한다`() {
        val user = newUser(); val original = hgProject(user)
        json(post("/-_-api/v1/owners/${original.owner}/projects/${original.name}/forks"), tokenOf(user), """{"name":"hgfork$stamp"}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.result.vcs").value("Mercurial")).andExpect(jsonPath("$.result.name").value("hgfork$stamp"))
        assertEquals(listOf("refs/heads/main", "refs/tags/v1"), repositories.refNames(projects.findByOwnerIgnoreCaseAndNameIgnoreCase(user.loginId!!, "hgfork$stamp")!!))
    }

    @Test
    fun `Mercurial 프로젝트의 배포 키 목록과 태그 목록과 사람이 쓴 커밋을 읽는다`() {
        val user = newUser(); val project = hgProject(user)
        val base = "/-_-api/v1/owners/${project.owner}/projects/${project.name}"
        json(get("$base/deploy_keys"), tokenOf(user)).andExpect(status().isOk)
        json(get("$base/tags"), tokenOf(user)).andExpect(status().isOk).andExpect(jsonPath("$.tags[0].name").value("v1"))
        json(get("$base/activity/commits").param("author", user.loginId!!), tokenOf(user)).andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].authorEmail").value(user.email))
    }
}
