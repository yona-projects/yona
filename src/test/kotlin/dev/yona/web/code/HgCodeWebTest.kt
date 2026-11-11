package dev.yona.web.code

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.repository.RepositoryService
import dev.yona.repository.hg.HgRepositories
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.HgTestSupport
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.enumeration.Side
import dev.yona.domain.enumeration.ThreadState
import java.util.Date
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mercurial 프로젝트의 코드 화면(코드, 커밋 이력, 커밋 보기, 브랜치, 태그, 원본, 내려받기)이 실제 `hg`로 만든 저장소에서 Git과 같은 화면으로 나오는지 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-web"])
class HgCodeWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var hgRepositories: HgRepositories
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var reviewComments: ReviewCommentRepository
    @Autowired private lateinit var users: dev.yona.domain.user.UserRepository

    private var sequence = 0
    private lateinit var project: Project
    private lateinit var dir: File
    private lateinit var c0: String
    private lateinit var c1: String

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val n = ++sequence
        project = projects.save(Project(owner = "hwo$n", name = "hwp$n", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(project)
        dir = hgRepositories.directoryOf(project.owner!!, project.name!!)
        c0 = HgTestSupport.commit(dir, "첫 커밋", mapOf("README.md" to "# 안녕\n", "src/main.txt" to "one\ntwo\n"), "2026-11-01 10:00 +0900", "Alice <alice@example.com>")
        c1 = HgTestSupport.commit(dir, "둘째 커밋\n\n설명", mapOf("src/main.txt" to "one\ntwo\nthree\n", "new.txt" to "새 파일\n"), "2026-11-02 10:00 +0900", "Alice <alice@example.com>")
        HgTestSupport.bookmark(dir, "main")
        HgTestSupport.bookmark(dir, "release", "0")
    }

    private fun base() = "/${project.owner}/${project.name}"

    private fun page(path: String): Document =
        Jsoup.parse(mockMvc.perform(get(java.net.URI.create(base() + path))).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `코드 주소는 기본 이름표의 화면으로 보내고 그 화면이 폴더 목록을 보여 준다`() {
        val result = mockMvc.perform(get(java.net.URI.create(base() + "/code"))).andReturn()
        assertEquals(303, result.response.status)
        assertEquals("${base()}/code/main", result.response.getHeader("Location"))
        val html = page("/code/main").html()
        assertTrue("README.md" in html && "src" in html && "new.txt" in html, html.take(400))
    }

    @Test
    fun `이름표가 하나도 없으면 HEAD로 보내고 default 브랜치의 끝을 보여 준다`() {
        HgTestSupport.hg(dir, "bookmark", "-d", "main"); HgTestSupport.hg(dir, "bookmark", "-d", "release")
        val result = mockMvc.perform(get(java.net.URI.create(base() + "/code"))).andReturn()
        assertEquals(303, result.response.status)
        assertEquals("${base()}/code/HEAD", result.response.getHeader("Location"))
        val html = page("/code/HEAD").html()
        assertTrue("new.txt" in html, html.take(400))
    }

    @Test
    fun `이름표가 하나도 없어도 브랜치 화면이 열리고 기본 위치가 기본 브랜치로 보인다 (code-F7, 탭은 보이는데 404였다)`() {
        HgTestSupport.hg(dir, "bookmark", "-d", "main"); HgTestSupport.hg(dir, "bookmark", "-d", "release")
        val html = page("/branches").html()
        assertTrue("HEAD" in html, html.take(500))
    }

    @Test
    fun `파일 보기의 리비전 글자는 목록과 같은 12자이다 (code-F7, Mercurial 관례)`() {
        val doc = page("/code/main/src/main.txt")
        assertEquals(c1.take(12), doc.selectFirst("#revisionNo a")!!.text().trim())
    }

    @Test
    fun `이름표를 정해 코드와 하위 폴더와 파일을 본다`() {
        assertTrue("new.txt" !in page("/code/refs%2Fheads%2Frelease").html())
        assertTrue("main.txt" in page("/code/main/src").html())
        val file = page("/code/main/src/main.txt").html()
        assertTrue("three" in file, file.take(300))
    }

    @Test
    fun `원본 파일을 내려받는다`() {
        val body = mockMvc.perform(get(java.net.URI.create(base() + "/rawcode/main/src/main.txt"))).andExpect(status().isOk).andReturn().response.contentAsString
        assertEquals("one\ntwo\nthree\n", body)
    }

    @Test
    fun `커밋 이력은 최근 커밋이 앞이고 메시지가 나온다`() {
        val html = page("/commits").text()
        assertTrue("둘째 커밋" in html && "첫 커밋" in html, html.take(400))
        assertTrue(html.indexOf("둘째 커밋") < html.indexOf("첫 커밋"))
    }

    @Test
    fun `커밋 보기는 변경된 파일의 diff를 보여 준다`() {
        val html = page("/commit/$c1").html()
        assertTrue("둘째 커밋" in html, html.take(300))
        assertTrue("new.txt" in html && "three" in html, html.take(600))
    }

    @Test
    fun `짧은 노드로도 커밋을 본다`() {
        val html = page("/commit/${c1.take(12)}").html()
        assertTrue("둘째 커밋" in html)
    }

    @Test
    fun `브랜치 화면은 이름표를 나열한다`() {
        val html = page("/branches").html()
        assertTrue("main" in html && "release" in html, html.take(500))
    }

    @Test
    fun `태그 화면이 열린다`() {
        HgTestSupport.tag(dir, "v1", "0")
        val html = page("/tags").html()
        assertTrue("v1" in html, html.take(400))
    }

    @Test
    fun `이름표를 zip으로 내려받는다`() {
        val bytes = mockMvc.perform(get(java.net.URI.create(base() + "/code/main/download"))).andExpect(status().isOk).andReturn().response.contentAsByteArray
        val names = ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> generateSequence { zip.nextEntry }.map { it.name }.toList() }
        assertTrue("src/main.txt" in names && "new.txt" in names, names.toString())
    }

    @Test
    fun `빈 저장소의 안내는 hg 명령이고 git 명령이 아니다`() {
        val n = ++sequence
        val empty = projects.save(Project(owner = "hwe$n", name = "hwq$n", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = empty, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(empty)
        val html = Jsoup.parse(mockMvc.perform(get(java.net.URI.create("/${empty.owner}/${empty.name}/code"))).andExpect(status().isOk).andReturn().response.contentAsString)
        val commands = html.select("pre code").joinToString("\n") { it.text() }
        // 수정 권한이 없는 익명에게는 명령이 보이지 않으므로 안내 상자만 확인한다
        assertTrue("The repository is empty!" in html.text(), html.text().take(200))
        assertTrue("git " !in commands)
    }

    @Test
    fun `프로젝트 홈이 열린다`() {
        val html = page("").html()
        assertTrue(html.isNotEmpty())
    }

    // --- 줄 코멘트 스레드(X-7 9단계 보완, A-388): Git과 같은 화면과 개수 ---

    private fun thread(commitId: String, ranged: Boolean): Long {
        val salt = dev.yona.security.LegacyPasswordHasher.generateSalt()
        val author = users.save(dev.yona.domain.user.User(loginId = "hrv${System.nanoTime()}", name = "리뷰어", email = "hrv${System.nanoTime()}@example.com", state = dev.yona.domain.enumeration.UserState.ACTIVE, passwordSalt = salt, password = dev.yona.security.LegacyPasswordHasher.hash("1234", salt)))
        val t = if (ranged) CodeCommentThread(commitId = commitId, path = "src/main.txt", startSide = Side.B, startLine = 2, startColumn = 0, endSide = Side.B, endLine = 2, endColumn = 3)
        else NonRangedCodeCommentThread(commitId = commitId)
        t.project = project; t.state = ThreadState.OPEN; t.authorId = author.id; t.authorLoginId = author.loginId; t.authorName = author.name; t.createdDate = Date()
        threads.save(t)
        reviewComments.save(ReviewComment(contents = "리뷰 내용 ${t.id}", createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, thread = t))
        t.reviewComments.add(reviewComments.findAll().last())
        return requireNotNull(t.id)
    }

    @Test
    fun `파일 보기의 댓글 수는 코드 범위 스레드도 센다`() {
        thread(c1, true); thread(c1, false)
        assertEquals("2", page("/code/main/src/main.txt").selectFirst(".number-of-comments")!!.text().trim())
    }

    @Test
    fun `커밋 이력에는 댓글 수가 나온다`() {
        thread(c1, true); thread(c1, false)
        assertEquals("2", page("/commits").selectFirst("tbody tr .number-of-comments")!!.text().trim())
    }

    @Test
    fun `커밋 보기에는 그 커밋의 리뷰 스레드가 나온다`() {
        val id = thread(c1, true)
        val doc = page("/commit/$c1")
        assertTrue(doc.selectFirst("#thread-$id .comment-body")!!.text().contains("리뷰 내용 $id"), doc.select("[id^=thread-]").toString().take(300))
    }

    @Test
    fun `프로젝트 홈의 최근 이력에 Mercurial 커밋이 12자 번호로 나온다`() {
        val html = mockMvc.perform(get(java.net.URI.create(base() + "?tabId=history"))).andExpect(status().isOk).andReturn().response.contentAsString
        val doc = Jsoup.parse(html)
        val link = doc.selectFirst("a[href\$=/commit/$c1]")
        assertTrue(link != null, "최근 이력에 커밋 링크가 있어야 한다: " + doc.select("a[href*=/commit/]").map { it.attr("href") })
        assertEquals(c1.take(12), link!!.text().trim(), "짧은 번호는 12자이다")
        // 대시보드 탭도 Mercurial 프로젝트에서 열린다
        mockMvc.perform(get(java.net.URI.create(base() + "?tabId=dashboard"))).andExpect(status().isOk)
    }
}
