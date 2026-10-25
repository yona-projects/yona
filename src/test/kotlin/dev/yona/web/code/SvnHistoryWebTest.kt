package dev.yona.web.code

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Subversion 프로젝트의 커밋 이력 화면을 검증한다. 원본: `CodeHistoryApp.history`(`app/controllers/CodeHistoryApp.java:60-105`),
 * `app/views/code/history.scala.html`, `playRepository.SVNRepository.getHistory`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-svn-history"])
class SvnHistoryWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var svn: SvnRepositories

    private var sequence = 0

    private fun user(loginId: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = loginId, name = "이름$loginId", email = "$loginId@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "sho$n", name = "shp$n", vcs = "Subversion", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(p)
        return p
    }

    private fun commit(p: Project, author: String, message: String, files: Map<String, String>) =
        SvnFixtures.commit(svn.directoryOf(p.owner!!, p.name!!), author, message, files.mapValues { it.value.toByteArray() })

    private fun page(path: String): Document = Jsoup.parse(mockMvc.perform(get(java.net.URI.create(path))).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `커밋 이력은 최근 것이 위에 오고 리비전 번호가 링크이다`() {
        val p = project()
        user("hdev")
        commit(p, "hdev", "첫째\n자세한 설명", mapOf("a.txt" to "1"))
        commit(p, "stranger", "둘째", mapOf("b.txt" to "2"))
        val doc = page("/${p.owner}/${p.name}/commits")
        assertEquals(listOf("2", "1"), doc.select("td.commit-id a").map { it.text().trim() })
        assertEquals("/${p.owner}/${p.name}/commit/2", doc.selectFirst("td.commit-id a")!!.attr("href"))
        assertEquals(listOf("둘째", "첫째"), doc.select("td.messages a.commitMsg.short").map { it.text() })
        // 두 번째 줄부터는 펼쳐 보는 설명이다.
        assertEquals("자세한 설명", doc.selectFirst("pre.commitMsg.desc")!!.text())
    }

    @Test
    fun `작성자는 사용자이면 링크와 아바타이고 아니면 이름만 보인다`() {
        val p = project()
        user("hdev2")
        commit(p, "hdev2", "하나", mapOf("a.txt" to "1"))
        commit(p, "stranger", "둘", mapOf("b.txt" to "2"))
        val authors = page("/${p.owner}/${p.name}/commits").select("tbody td.author")
        assertEquals("", authors[0].select("a").attr("href"))
        assertEquals("stranger", authors[0].selectFirst("span:not(.avatar-wrap)")?.text(), authors.outerHtml())
        assertEquals("/hdev2", authors[1].selectFirst("a.avatar-wrap")!!.attr("href"))
    }

    @Test
    fun `Subversion은 브랜치 탭이 없고 날짜 열은 커밋 날짜이다`() {
        val p = project()
        commit(p, "hdev", "하나", mapOf("a.txt" to "1"))
        val doc = page("/${p.owner}/${p.name}/commits/HEAD/")
        assertEquals(listOf("Files", "Commit"), doc.select(".code-browse-wrap ul.nav-tabs li a").map { it.text() })
        assertEquals("Commit date", doc.selectFirst("td.date strong")!!.text())
    }

    @Test
    fun `한 쪽은 25개에 경계 리비전이 겹쳐 26개이고 이전과 다음 쪽으로 이어진다`() {
        val p = project()
        (1..30).forEach { commit(p, "hdev", "커밋 $it", mapOf("f$it.txt" to "x")) }
        val first = page("/${p.owner}/${p.name}/commits")
        assertEquals((30 downTo 5).map { it.toString() }, first.select("td.commit-id a").map { it.text().trim() })
        assertEquals("/${p.owner}/${p.name}/commits?page=1", first.select("div.actrow a").single { it.text() == "Older" }.attr("href"))
        val second = page("/${p.owner}/${p.name}/commits?page=1")
        assertEquals((5 downTo 1).map { it.toString() }, second.select("td.commit-id a").map { it.text().trim() })
        assertTrue(second.select("div.actrow a").none { it.text() == "Older" })
        assertTrue(second.select("div.actrow a").any { it.text() == "Newer" })
    }

    @Test
    fun `경로를 정하면 그 경로를 바꾼 커밋만 모은다`() {
        val p = project()
        commit(p, "hdev", "a1", mapOf("a/x.txt" to "1"))
        commit(p, "hdev", "b1", mapOf("b/y.txt" to "1"))
        commit(p, "hdev", "a2", mapOf("a/x.txt" to "2"))
        val doc = page("/${p.owner}/${p.name}/commits/HEAD/a")
        assertEquals(listOf("3", "1"), doc.select("td.commit-id a").map { it.text().trim() })
        assertTrue(doc.select("td.browse a").first()!!.attr("href").endsWith("/code/3/a"))
    }

    @Test
    fun `커밋이 없는 저장소도 이력 화면에 커밋이 없다고 보인다`() {
        val p = project()
        val doc = page("/${p.owner}/${p.name}/commits")
        assertEquals("No commit exists", doc.selectFirst("td.warning-none")!!.text())
    }
}
