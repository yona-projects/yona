package dev.yona.web.code

import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.Side
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.repository.RepositoryService
import dev.yona.repository.diff.DiffService
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 파일별 차이를 화면용 HTML로 그리는 규칙을 검증한다.
 *
 * 원본: `app/views/partial_diff.scala.html`, `partial_filediff.scala.html`, `partial_diff_line.scala.html`, `TemplateHelper.DiffRenderer`.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class DiffHtmlRendererTest : AbstractIntegrationTest() {

    @Autowired private lateinit var renderer: DiffHtmlRenderer
    @Autowired private lateinit var diffs: DiffService
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var messages: org.springframework.context.MessageSource

    private fun msg(key: String, vararg args: Any) = messages.getMessage(key, args, org.springframework.context.i18n.LocaleContextHolder.getLocale())

    private var sequence = 0

    private fun project(): Project {
        val p = projects.save(Project(owner = "dho${++sequence}", name = "dhp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        repositories.createRepository(p)
        return p
    }

    private fun lines(vararg l: String) = l.joinToString("\n", postfix = "\n")

    private fun html(p: Project, threads: List<CodeCommentThread> = emptyList(), stub: (List<CodeCommentThread>) -> String = { "<div class='thread'>${it.size}</div>" }) =
        Jsoup.parse(renderer.render(diffs.commitDiff(p, "master")!!, threads, p, p) { stub(it) })

    @Test
    fun `수정한 파일은 범위 줄과 줄 종류, 번호, 접두어를 그린다`() {
        val p = project()
        val before = (1..12).map { "줄 $it" }.toTypedArray()
        val after = before.copyOf().also { it[5] = "줄 6 <b>수정</b>" }
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to lines(*before).toByteArray())), TestCommit("수정", mapOf("a.txt" to lines(*after).toByteArray())))
        val doc = html(p)
        assertEquals("a.txt", doc.selectFirst(".diff-partial-file .filename")!!.text())
        assertEquals("--- a.txt", doc.select(".patch-header .path")[0].text()); assertEquals("+++ a.txt", doc.select(".patch-header .path")[1].text())
        assertEquals("@@ -3,7 +3,7 @@", doc.selectFirst("td.hunk")!!.text())
        val rows = doc.select("table.diff-container tbody tr").filter { !it.hasClass("range") }
        assertEquals(listOf("context", "context", "context", "remove", "add", "context", "context", "context"), rows.map { it.className() })
        val removed = rows.first { it.hasClass("remove") }
        assertEquals("A", removed.attr("data-side")); assertEquals("6", removed.attr("data-line"))
        assertEquals("6", removed.select(".line-number")[0].attr("data-line-num")); assertEquals("", removed.select(".line-number")[1].attr("data-line-num"))
        assertEquals("-줄 6", removed.selectFirst("pre.diff-partial-codeline")!!.text())
        val added = rows.first { it.hasClass("add") }
        assertEquals("B", added.attr("data-side")); assertEquals("+줄 6 <b>수정</b>", added.selectFirst("pre.diff-partial-codeline")!!.text())
        assertEquals(" 줄 3", rows[0].selectFirst("pre")!!.wholeText())
        assertEquals("a.txt", doc.selectFirst("table.diff-container")!!.attr("data-file-path"))
        assertEquals("a-txt", doc.selectFirst(".diff-partial-outer")!!.id())
    }

    @Test
    fun `추가한 파일과 지운 파일은 전체 줄을 그리고 머리글에 종류를 쓴다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("gone.txt" to lines("x", "y").toByteArray())),
            TestCommit("정리", mapOf("dir/new.txt" to lines("가", "나", "다").toByteArray()), deleted = listOf("gone.txt")))
        val doc = html(p)
        val files = doc.select(".diff-partial-outer").associateBy { it.id() }
        val added = files["dir-new-txt"]!!
        assertEquals(msg("code.addedPath", "dir/new.txt"), added.selectFirst(".filename")!!.text()); assertEquals("@@ -0,0 +1,3 @@", added.selectFirst("td.hunk")!!.text())
        assertEquals(listOf("+가", "+나", "+다"), added.select("tr.add pre").map { it.text() })
        assertNull(added.selectFirst("tr[data-side=A]"))
        val removed = files["gone-txt"]!!
        assertEquals(msg("code.deletedPath", "gone.txt"), removed.selectFirst(".filename")!!.text()); assertEquals("@@ -1,2 +0,0 @@", removed.selectFirst("td.hunk")!!.text())
        assertEquals(listOf("-x", "-y"), removed.select("tr.remove pre").map { it.text() })
    }

    @Test
    fun `이름을 바꾼 파일과 이진 파일을 알린다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("old.txt" to lines("가", "나", "다", "라").toByteArray(), "bin.dat" to byteArrayOf(0, 1, 0, 2))),
            TestCommit("정리", mapOf("new.txt" to lines("가", "나", "다", "라").toByteArray(), "bin.dat" to byteArrayOf(0, 9, 0)), deleted = listOf("old.txt")))
        val doc = html(p)
        val files = doc.select(".diff-partial-outer").associateBy { it.id() }
        assertEquals(msg("code.renamedPath", "old.txt", "new.txt"), files["new-txt"]!!.selectFirst(".filename")!!.text())
        assertEquals("bin.dat (Binary)", files["bin-dat"]!!.selectFirst(".filename")!!.text())
        assertEquals(msg("code.isBinary"), files["bin-dat"]!!.selectFirst("td.isBinary")!!.text())
    }

    @Test
    fun `줄 끝 줄바꿈이 없으면 표시한다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to "첫 줄\n끝".toByteArray())))
        assertTrue(html(p).selectFirst("tr.add:last-of-type .code")!!.text().contains(msg("code.eolMissing")))
    }

    @Test
    fun `스레드는 해당 줄 바로 뒤에 끼워 넣는다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to lines("1", "2", "3").toByteArray())))
        val thread = CodeCommentThread(commitId = "abc", path = "a.txt", startSide = Side.B, startLine = 2, endSide = Side.B, endLine = 2)
        val doc = html(p, listOf(thread))
        val rows = doc.select("table.diff-container tbody tr")
        val index = rows.indexOfFirst { it.hasClass("comments") }
        assertEquals("2", rows[index - 1].attr("data-line")); assertEquals("abc", rows[index].attr("data-commit-id"))
        assertEquals("1", rows[index].selectFirst("td[colspan=3] .thread")!!.text())
    }

    @Test
    fun `파일이 한도에 이르면 안내한다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to "a".toByteArray())))
        val many = diffs.commitDiff(p, "master")!!.let { one -> List(DiffService.DIFF_FILE_LIMIT) { one.first() } }
        val doc = Jsoup.parse(renderer.render(many, emptyList(), p, p) { "" })
        assertNotNull(doc.selectFirst("p.alert")); assertEquals(msg("code.fileDiffLimitExceeded", DiffService.DIFF_FILE_LIMIT), doc.selectFirst("p.alert")!!.text())
    }
}
