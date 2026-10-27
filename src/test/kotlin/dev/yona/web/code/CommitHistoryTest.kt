package dev.yona.web.code

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 커밋 이력 화면을 검증한다.
 *
 * 원본: `app/controllers/CodeHistoryApp.java`(`history`, `historyUntilHead`), `app/views/code/history.scala.html`, `app/views/common/commitMsg.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class CommitHistoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    private fun project(): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "cho$n", name = "chp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(p)
        return p
    }

    private fun user(email: String, loginId: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = loginId, name = "이름$loginId", email = email, state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun page(path: String): Document = Jsoup.parse(mockMvc.perform(get(java.net.URI.create(path))).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun history(count: Int): Project = project().also { p ->
        val commits = (1..count).map { TestCommit("커밋 $it", mapOf("f$it.txt" to "x".toByteArray(), "common.txt" to "v$it".toByteArray())) }
        GitTestSupport.pushCommits(repositories, p, *commits.toTypedArray())
    }

    @Test
    fun `최근 커밋이 위에 오고 커밋 보기 링크와 탭, 브랜치 선택이 나온다`() {
        val p = history(3)
        val doc = page("/${p.owner}/${p.name}/commits")
        assertEquals(listOf("커밋 3", "커밋 2", "커밋 1"), doc.select("td.messages a.commitMsg.short").map { it.text() })
        val first = doc.selectFirst("tbody tr")!!
        assertTrue(first.selectFirst("td.commit-id a")!!.attr("href").startsWith("/${p.owner}/${p.name}/commit/"))
        assertEquals(7, first.selectFirst("td.commit-id a")!!.text().length)
        assertEquals(40, first.selectFirst(".btn-copy-commitId")!!.attr("data-commitId").length)
        assertEquals(listOf("Files", "Commit", "Branches"), doc.select(".code-browse-wrap ul.nav-tabs li").map { it.text() })
        assertEquals("/${p.owner}/${p.name}/commits/refs%252Fheads%252Fmaster/", doc.selectFirst("select#branches option")!!.attr("value"))
        assertNull(doc.selectFirst("#breadcrumbs"))
        assertNull(doc.selectFirst(".actrow a"))
    }

    @Test
    fun `25개씩 쪽으로 나누어 새로 돌아가기와 이전으로 가기를 보여 준다`() {
        val p = history(30)
        val first = page("/${p.owner}/${p.name}/commits")
        assertEquals(25, first.select("tbody tr").size)
        assertEquals(listOf("Older"), first.select(".actrow a").map { it.text() })
        assertEquals("/${p.owner}/${p.name}/commits?page=1", first.selectFirst(".actrow a")!!.attr("href"))
        val second = page("/${p.owner}/${p.name}/commits?page=1")
        // v1.6는 `page * size + size`개를 읽어 마지막 size개를 남기므로 마지막 쪽은 가장 오래된 25개이다(앞 쪽과 겹친다, R-40).
        assertEquals((25 downTo 1).map { "커밋 $it" }, second.select("td.messages a.commitMsg.short").map { it.text() })
        assertEquals(listOf("Newer"), second.select(".actrow a").map { it.text() })
    }

    @Test
    fun `주소의 소유자와 프로젝트 이름은 대소문자가 달라도 링크는 프로젝트의 실제 이름으로 만든다 (v1_6 project 모델의 이름)`() {
        val p = history(2)
        val doc = page("/${p.owner!!.uppercase()}/${p.name!!.uppercase()}/commits/master/")
        val links = doc.select("a[href], option[value]").map { it.attr("href").ifEmpty { it.attr("value") } }.filter { it.startsWith("/") && it.contains("/commits") }
        assertTrue(links.isNotEmpty())
        links.forEach { assertTrue(it.startsWith("/${p.owner}/${p.name}/"), it) }
    }

    @Test
    fun `끝에 개행만 있는 한 줄 메시지에는 더 보기 단추가 없다`() {
        val p = project()
        // git commit -m 이 저장하는 메시지는 "제목\n"이다. v1.6 commitMsg는 Java split이 뒤쪽 빈 문자열을 버려 단추를 내지 않는다.
        GitTestSupport.pushCommits(repositories, p,
            TestCommit("끝 개행 한 줄\n", mapOf("a.txt" to "a".toByteArray())),
            TestCommit("제목\n\n본문\n", mapOf("b.txt" to "b".toByteArray())))
        val rows = page("/${p.owner}/${p.name}/commits/master/").select("tbody tr")
        assertNotNull(rows[0].selectFirst("button.moreBtn")); assertEquals("본문", rows[0].selectFirst("pre.commitMsg.desc")!!.text().trim())
        assertNull(rows[1].selectFirst("button.moreBtn")); assertNull(rows[1].selectFirst("pre.commitMsg.desc"))
    }

    @Test
    fun `여러 줄 메시지는 더 보기 단추와 숨은 본문을, 알려진 작성자는 사용자 링크를 보여 준다`() {
        val p = project(); val dev = user("dev@example.com", "devuser")
        GitTestSupport.pushCommits(repositories, p,
            TestCommit("제목 <b>줄</b>\n\n본문 #1 입니다", mapOf("a.txt" to "a".toByteArray()), authorEmail = dev.email!!),
            TestCommit("낯선 사람", mapOf("b.txt" to "b".toByteArray()), authorName = "나그네", authorEmail = "stranger@example.com"))
        val doc = page("/${p.owner}/${p.name}/commits/master/")
        val rows = doc.select("tbody tr")
        assertNotNull(rows[1].selectFirst("button.moreBtn")); assertTrue(rows[1].selectFirst("pre.commitMsg.desc")!!.hasClass("hidden"))
        assertEquals("제목 <b>줄</b>", rows[1].selectFirst("a.commitMsg.short")!!.text())
        assertTrue(rows[1].selectFirst("pre.commitMsg.desc")!!.text().contains("본문"))
        assertEquals("/devuser", rows[1].selectFirst("td.author a.avatar-wrap")!!.attr("href"))
        assertNull(rows[0].selectFirst("button.moreBtn"))
        assertNull(rows[0].selectFirst("td.author a")); assertEquals("stranger@example.com", rows[0].selectFirst("td.author span.avatar-wrap")!!.attr("title"))
    }

    @Test
    fun `경로를 정하면 그 경로를 바꾼 커밋만 이동 경로와 코드 보기 단추와 함께 보여 준다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p,
            TestCommit("a 만듦", mapOf("src/a.txt" to "1".toByteArray(), "other.txt" to "o".toByteArray())),
            TestCommit("other 수정", mapOf("other.txt" to "o2".toByteArray())),
            TestCommit("a 수정", mapOf("src/a.txt" to "2".toByteArray())))
        val doc = page("/${p.owner}/${p.name}/commits/master/src/a.txt")
        assertEquals(listOf("a 수정", "a 만듦"), doc.select("td.messages a.commitMsg.short").map { it.text() })
        assertEquals(listOf("src", "a.txt"), doc.select("#breadcrumbs a").drop(1).map { it.text() })
        assertNull(doc.selectFirst("select#branches")); assertNull(doc.selectFirst(".code-browse-wrap ul.nav-tabs"))
        val browse = doc.selectFirst("td.browse a.ybtn")!!
        assertTrue(browse.attr("href").endsWith("/src/a.txt"), browse.attr("href"))
        assertTrue(doc.selectFirst("td.commit-id a")!!.attr("href").contains("?branch=master&path=src/a.txt#src-a-txt"))
    }

    @Test
    fun `커밋이 없으면 일반 찾을 수 없음 화면으로 404이다`() {
        val p = project()
        // v1.6 CodeHistoryApp: 빈 저장소의 getHistory가 null이라 error.notfound 404이고, 처음 올리는 방법을 안내하는 nohead 분기에는 도달하지 않는다
        val response = mockMvc.perform(get("/${p.owner}/${p.name}/commits")).andExpect(status().isNotFound).andReturn().response
        assertTrue(!response.contentAsString.contains("alert-block"), response.contentAsString)
        mockMvc.perform(get("/${p.owner}/${p.name}/commits/HEAD/")).andExpect(status().isNotFound)
    }

    @Test
    fun `없는 브랜치와 올바르지 않은 쪽 번호는 오류이다`() {
        val p = history(1)
        mockMvc.perform(get("/${p.owner}/${p.name}/commits/nobranch/")).andExpect(status().isNotFound)
        mockMvc.perform(get("/${p.owner}/${p.name}/commits?page=abc")).andExpect(status().isBadRequest)
    }
}
