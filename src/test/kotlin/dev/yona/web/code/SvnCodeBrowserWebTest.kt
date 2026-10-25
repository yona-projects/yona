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
import dev.yona.repository.svn.SvnFixtures
import dev.yona.repository.svn.SvnRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Subversion 프로젝트의 코드 브라우저 화면, JSON, 원본, 내려받기를 검증한다.
 * 원본: `app/controllers/CodeApp.java`, `app/views/code/nohead_svn.scala.html`, `partial_view_file.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-svn-web"])
class SvnCodeBrowserWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var svn: SvnRepositories

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "sw$n", name = "sw$n", email = "sw$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "swo$n", name = "swp$n", vcs = "Subversion", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(p)
        return p
    }

    private fun member(p: Project): User = user().also { projectUsers.save(ProjectUser(user = it, project = p, role = roles.findById(RoleType.MEMBER.id).get())) }

    private fun commit(p: Project, author: String, message: String, files: Map<String, String>) =
        SvnFixtures.commit(svn.directoryOf(p.owner!!, p.name!!), author, message, files.mapValues { it.value.toByteArray() })

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(java.net.URI.create(path)).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun filled(): Project = project().also { commit(it, "alice", "처음", mapOf("README.md" to "# 제목", "src/App.kt" to "fun main() {}\n", "src/util/Tool.kt" to "object Tool\n")) }

    @Test
    fun `빈 저장소는 처음 올리는 방법을 svn 명령으로 안내한다`() {
        val p = project()
        val writer = member(p)
        val doc = page("/${p.owner}/${p.name}/code", login(writer))
        assertTrue(doc.text().contains("The repository is empty!"))
        val commands = doc.select("pre code").text()
        assertTrue(commands.contains("svn co http://localhost/svn/${p.owner}/${p.name} --username ${writer.loginId}"), commands)
        assertTrue(commands.contains("svn add README.md") && commands.contains("svn commit -m \"first commit\""), commands)
        assertTrue(doc.title().startsWith("Commit history"), doc.title())
    }

    @Test
    fun `수정 권한이 없으면 안내 명령을 보이지 않는다`() {
        val p = project()
        val doc = page("/${p.owner}/${p.name}/code")
        assertTrue(doc.select("pre code").isEmpty())
    }

    @Test
    fun `커밋이 있으면 코드 주소는 HEAD 코드 화면으로 보낸다`() {
        val p = filled()
        mockMvc.perform(get("/${p.owner}/${p.name}/code")).andExpect(status().isFound).andExpect(header().string("Location", "/${p.owner}/${p.name}/code/HEAD/"))
    }

    @Test
    fun `루트 폴더 목록은 폴더 먼저 항목마다 리비전 링크를 담는다`() {
        val p = filled()
        val doc = page("/${p.owner}/${p.name}/code/HEAD/")
        assertEquals(listOf("src", "README.md"), doc.select(".listitem .filename a").map { it.text() })
        assertEquals(listOf("HEAD"), doc.select("select#branches option").map { it.text() })
        assertTrue(doc.select("a[href='/${p.owner}/${p.name}/commit/1']").isNotEmpty(), doc.select(".listitem").outerHtml())
    }

    @Test
    fun `파일 화면은 리비전 번호를 Revision으로 보이고 원본 주소에 리비전을 쓴다`() {
        val p = filled()
        val doc = page("/${p.owner}/${p.name}/code/HEAD/src/App.kt")
        assertEquals("Revision 1", doc.selectFirst("#revisionNo a")!!.text().trim())
        assertEquals("/${p.owner}/${p.name}/rawcode/1/src/App.kt", doc.select("a:contains(Raw)").first()!!.attr("href"))
        assertEquals("/${p.owner}/${p.name}/files/1/src/App.kt", doc.selectFirst("#open-in-browser")!!.attr("href"))
        assertEquals("fun main() {}\n", doc.selectFirst("#codeVal")!!.text() + "\n")
    }

    @Test
    fun `메타데이터 JSON을 돌려준다`() {
        val p = filled()
        mockMvc.perform(get("/${p.owner}/${p.name}/code/HEAD/!/src/App.kt")).andExpect(status().isOk)
            .andExpect(jsonPath("$.type").value("file")).andExpect(jsonPath("$.revisionNo").value("1")).andExpect(jsonPath("$.author").value("alice"))
        mockMvc.perform(get("/${p.owner}/${p.name}/code/!/src")).andExpect(status().isOk).andExpect(jsonPath("$.type").value("folder")).andExpect(jsonPath("$.data.util.type").value("folder"))
        mockMvc.perform(get("/${p.owner}/${p.name}/code/HEAD/!/none")).andExpect(status().isNotFound)
    }

    @Test
    fun `원본 파일을 리비전으로 보여 준다`() {
        val p = filled()
        commit(p, "alice", "둘", mapOf("src/App.kt" to "fun main() { v2 }\n"))
        mockMvc.perform(get("/${p.owner}/${p.name}/rawcode/1/src/App.kt")).andExpect(status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("fun main() {}\n"))
        mockMvc.perform(get("/${p.owner}/${p.name}/rawcode/HEAD/src/App.kt")).andExpect(status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("fun main() { v2 }\n"))
        mockMvc.perform(get("/${p.owner}/${p.name}/rawcode/HEAD/none")).andExpect(status().isNotFound)
    }

    @Test
    fun `내려받기는 빈 압축 응답이다`() {
        val p = filled()
        val result = mockMvc.perform(get("/${p.owner}/${p.name}/code/HEAD/download")).andExpect(status().isOk).andReturn()
        assertEquals("attachment; filename=${p.name}-HEAD.zip", result.response.getHeader("Content-Disposition"))
        assertEquals(0, result.response.contentAsByteArray.size)
    }

    @Test
    fun `지원하지 않는 VCS는 501이다`() {
        val n = ++sequence
        val p = projects.save(Project(owner = "swo$n", name = "swp$n", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        mockMvc.perform(get("/${p.owner}/${p.name}/code")).andExpect(status().isNotImplemented)
    }
}
