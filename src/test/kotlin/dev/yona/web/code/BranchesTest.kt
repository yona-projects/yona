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
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 브랜치 목록, 기본 브랜치 지정, 브랜치 삭제를 검증한다.
 *
 * 원본: `app/controllers/BranchApp.java`, `app/views/code/branches.scala.html`, `partial_branchrow.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class BranchesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "br$n", name = "br$n", email = "br$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun member(p: Project, role: RoleType = RoleType.MANAGER): User = user().also { projectUsers.save(ProjectUser(user = it, project = p, role = roles.findById(role.id).get())) }

    private fun project(vcs: String = "GIT"): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "bro$n", name = "brp$n", vcs = vcs, projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (vcs == "GIT") repositories.createRepository(p)
        return p
    }

    private fun twoBranches(): Project = project().also {
        GitTestSupport.pushCommits(repositories, it, TestCommit("마스터", mapOf("a.txt" to "a".toByteArray())))
        GitTestSupport.pushCommits(repositories, it, TestCommit("기능", mapOf("b.txt" to "b".toByteArray())), branch = "feature", startTime = 1_800_000_000_000L)
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `기본 브랜치를 맨 위에 두고 나머지는 최근 커밋 순으로 보여 준다`() {
        val p = twoBranches()
        val doc = page("/${p.owner}/${p.name}/branches")
        val rows = doc.select("table.branch-list-wrap tbody tr")
        assertEquals(listOf("master", "feature"), rows.map { it.selectFirst("td.branchName a")!!.text() })
        assertTrue(rows[0].hasClass("head")); assertNotNull(rows[0].selectFirst(".headBranch"))
        assertNull(rows[1].selectFirst(".headBranch"))
        assertEquals("/${p.owner}/${p.name}/code/refs%252Fheads%252Fmaster/", rows[0].selectFirst("td.branchName a")!!.attr("href"))
        assertEquals(7, rows[1].selectFirst("a.commitId")!!.text().length)
        assertNotNull(rows[1].selectFirst("td.pullRequest .disabled"))
    }

    @Test
    fun `탭은 코드, 커밋, 브랜치이고 브랜치가 활성이다`() {
        val p = twoBranches()
        val tabs = page("/${p.owner}/${p.name}/branches").select(".code-browse-wrap ul.nav-tabs li")
        assertEquals(listOf("Files", "Commit", "Branches"), tabs.map { it.text() }); assertTrue(tabs[2].hasClass("active"))
    }

    @Test
    fun `구성원에게는 기본 브랜치 지정과 삭제 단추가 나온다`() {
        val p = twoBranches(); val m = member(p)
        val rows = page("/${p.owner}/${p.name}/branches", login(m)).select("table.branch-list-wrap tbody tr")
        assertNull(rows[0].selectFirst("button[data-request-method=post]")); assertNull(rows[0].selectFirst("a[data-request-method=delete]"))
        assertEquals("/${p.owner}/${p.name}/code/refs%252Fheads%252Ffeature/setAsDefault", rows[1].selectFirst("button[data-request-method=post]")!!.attr("data-request-uri"))
        assertEquals("/${p.owner}/${p.name}/code/refs%252Fheads%252Ffeature/", rows[1].selectFirst("a[data-request-method=delete]")!!.attr("href"))
        assertNull(page("/${p.owner}/${p.name}/branches").selectFirst("td.actions"))
    }

    @Test
    fun `일반 구성원에게는 기본 브랜치 지정과 삭제 단추가 나오지 않는다`() {
        val p = twoBranches(); val m = member(p, RoleType.MEMBER)
        // v1.6 BranchApp과 branches.scala.html은 project.asResource()(전역 리소스)로 판정한다. 사이트 관리자, 프로젝트 매니저, 조직 관리자만 통과한다.
        val doc = page("/${p.owner}/${p.name}/branches", login(m))
        assertNull(doc.selectFirst("td.actions")); assertNull(doc.selectFirst("button[data-request-method=post]")); assertNull(doc.selectFirst("a[data-request-method=delete]"))
        mockMvc.perform(post("/${p.owner}/${p.name}/code/refs%2Fheads%2Ffeature/setAsDefault").session(login(m))).andExpect(status().isForbidden)
    }

    @Test
    fun `기본 브랜치를 바꾸면 HEAD가 그 브랜치를 가리킨다`() {
        val p = twoBranches(); val m = member(p)
        mockMvc.perform(post("/${p.owner}/${p.name}/code/refs%2Fheads%2Ffeature/setAsDefault").session(login(m))).andExpect(status().is3xxRedirection).andExpect(redirectedUrl("/${p.owner}/${p.name}/branches"))
        assertEquals("refs/heads/feature", repositories.defaultBranch(p))
        mockMvc.perform(post("/${p.owner}/${p.name}/code/refs%2Fheads%2Fmaster/setAsDefault").session(login(m)).header("X-Requested-With", "XMLHttpRequest")).andExpect(status().isOk)
        assertEquals("refs/heads/master", repositories.defaultBranch(p))
    }

    @Test
    fun `브랜치를 지우면 목록으로 돌아간다`() {
        val p = twoBranches(); val m = member(p)
        mockMvc.perform(delete("/${p.owner}/${p.name}/code/refs%2Fheads%2Ffeature/").session(login(m))).andExpect(status().is3xxRedirection).andExpect(redirectedUrl("/${p.owner}/${p.name}/branches"))
        assertEquals(listOf("refs/heads/master"), repositories.refNames(p))
    }

    @Test
    fun `구성원이 아니면 바꾸거나 지울 수 없다`() {
        val p = twoBranches(); val outsider = user()
        mockMvc.perform(post("/${p.owner}/${p.name}/code/refs%2Fheads%2Ffeature/setAsDefault").session(login(outsider))).andExpect(status().isForbidden)
        mockMvc.perform(delete("/${p.owner}/${p.name}/code/refs%2Fheads%2Ffeature/").session(login(outsider))).andExpect(status().isForbidden)
        assertEquals(2, repositories.refNames(p).size)
    }

    @Test
    fun `Git이 아닌 프로젝트는 쓸 수 없다`() {
        val p = project(vcs = "Subversion")
        mockMvc.perform(get("/${p.owner}/${p.name}/branches")).andExpect(status().isBadRequest)
    }
}
