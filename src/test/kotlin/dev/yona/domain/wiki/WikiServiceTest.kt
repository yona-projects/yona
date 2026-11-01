package dev.yona.domain.wiki

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.repository.DataDirectory
import dev.yona.repository.WikiRepositories
import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 위키 서비스를 검증한다 (X-17 W2, D-60). 페이지는 위키 저장소의 최상위 `.md` 파일이고 제목의 공백은 파일 이름에서 `-`이다.
 */
class WikiServiceTest {

    @TempDir
    lateinit var dataDir: File

    private lateinit var wikis: WikiRepositories
    private lateinit var service: WikiService
    private var clash = false
    private val project = Project(owner = "alice", name = "demo", vcs = "GIT")
    private val bob = User(loginId = "bob", name = "Bob", email = "bob@example.com")
    private val carol = User(loginId = "carol", name = "Carol", email = "carol@example.com")

    @BeforeEach
    fun setUp() {
        wikis = WikiRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        service = WikiService(wikis, { _, _ -> clash })
    }

    /** 위키 저장소의 HEAD 커밋 수이다. */
    private fun commitCount(): Int = wikis.open("alice", "demo").use { Git(it).log().call().count() }

    /** 사용자가 git push로 올린 것처럼 위키 저장소에 파일을 직접 더한다. */
    private fun pushRaw(vararg files: Pair<String, String>) {
        val work = File(dataDir, "work-${System.nanoTime()}")
        Git.cloneRepository().setURI(wikis.directoryOf("alice", "demo").canonicalPath).setDirectory(work).call().use { git ->
            files.forEach { (path, text) -> File(work, path).apply { parentFile.mkdirs() }.writeText(text) }
            git.add().addFilepattern(".").call()
            git.commit().setMessage("raw").setAuthor("Raw", "raw@example.com").setCommitter("Raw", "raw@example.com").call()
            git.push().call()
        }
    }

    // --- 비어 있는 위키 ---

    @Test
    fun `아직 위키가 없으면 목록은 비어 있고 저장소를 만들지 않는다`() {
        assertTrue(service.pages(project).isEmpty())
        assertNull(service.page(project, "Home"))
        assertFalse(wikis.exists("alice", "demo"))
    }

    // --- 만들기 ---

    @Test
    fun `첫 페이지를 만들면 위키 저장소가 생기고 내용과 작성자가 남는다`() {
        val page = service.create(project, bob, "Home", "# 안녕\n본문", null)
        assertEquals("Home", page.name)
        assertEquals("# 안녕\n본문", service.page(project, "Home")?.content)
        val commit = service.history(project, "Home", 0, 10).single()
        assertEquals("Bob", commit.authorName)
        assertEquals("bob@example.com", commit.authorEmail)
        assertEquals("Create Home", commit.shortMessage)
    }

    @Test
    fun `제목의 공백은 파일 이름에서 하이픈이 되고 어느 쪽 표기로도 찾는다`() {
        val page = service.create(project, bob, "My First Page", "x", null)
        assertEquals("My-First-Page", page.name)
        assertEquals("My First Page", page.title)
        assertEquals("x", service.page(project, "My First Page")?.content)
        assertEquals("x", service.page(project, "My-First-Page")?.content)
        assertEquals(listOf("My-First-Page"), wikis.open("alice", "demo").use { repo ->
            org.eclipse.jgit.treewalk.TreeWalk(repo).use { walk ->
                walk.addTree(repo.parseCommit(repo.resolve("HEAD")).tree)
                generateSequence { if (walk.next()) walk.nameString.removeSuffix(".md") else null }.toList()
            }
        })
    }

    @Test
    fun `한글 제목도 만든다`() {
        service.create(project, bob, "시작 하기", "내용", null)
        assertEquals("내용", service.page(project, "시작-하기")?.content)
    }

    @Test
    fun `커밋 메시지를 주면 그대로 쓰고 비어 있으면 기본 메시지를 쓴다`() {
        service.create(project, bob, "A", "1", "첫 글")
        service.create(project, bob, "B", "2", "   ")
        assertEquals("첫 글", service.history(project, "A", 0, 10).single().shortMessage)
        assertEquals("Create B", service.history(project, "B", 0, 10).single().shortMessage)
    }

    @Test
    fun `잘못된 제목은 거부한다`() {
        val bad = listOf("", "   ", "a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b", "_Other", "_", ".", "..", "a\nb", "x".repeat(201))
        bad.forEach { title ->
            assertFailsWith<WikiTitleInvalidException>(title) { service.create(project, bob, title, "x", null) }
        }
        assertFalse(wikis.exists("alice", "demo"))
    }

    @Test
    fun `특수 페이지 제목은 허용한다`() {
        service.create(project, bob, "_Sidebar", "side", null)
        service.create(project, bob, "_Footer", "foot", null)
        assertEquals("side", service.sidebar(project))
        assertEquals("foot", service.footer(project))
    }

    @Test
    fun `이미 있는 제목은 거부하고 공백과 하이픈은 같은 제목이다`() {
        service.create(project, bob, "a b", "1", null)
        assertFailsWith<WikiPageExistsException> { service.create(project, bob, "a b", "2", null) }
        assertFailsWith<WikiPageExistsException> { service.create(project, bob, "a-b", "2", null) }
        assertEquals(1, commitCount())
    }

    // --- 목록과 검색 ---

    @Test
    fun `목록은 제목순이고 대소문자를 가리지 않으며 특수 페이지는 뺀다`() {
        listOf("banana", "Apple", "cherry", "_Sidebar", "_Footer").forEach { service.create(project, bob, it, "x", null) }
        assertEquals(listOf("Apple", "banana", "cherry"), service.pages(project).map { it.title })
    }

    @Test
    fun `push로 들어온 하위 폴더와 마크다운이 아닌 파일은 목록에서 숨긴다`() {
        service.create(project, bob, "Home", "x", null)
        pushRaw("docs/Guide.md" to "g", "image.png" to "p", "Extra.md" to "e")
        assertEquals(listOf("Extra", "Home"), service.pages(project).map { it.name })
        assertNull(service.page(project, "docs/Guide"))
    }

    @Test
    fun `제목 검색은 부분 일치이고 대소문자를 가리지 않는다`() {
        listOf("Install Guide", "Upgrade guide", "FAQ").forEach { service.create(project, bob, it, "x", null) }
        assertEquals(listOf("Install Guide", "Upgrade guide"), service.search(project, "GUIDE").map { it.title })
        assertEquals(3, service.search(project, "").size)
        assertTrue(service.search(project, "none").isEmpty())
    }

    // --- 고치기와 이름 바꾸기 ---

    @Test
    fun `내용을 고치면 새 커밋이 생기고 이전 리비전을 읽을 수 있다`() {
        service.create(project, bob, "Home", "v1", null)
        val first = service.history(project, "Home", 0, 10).single().id
        service.update(project, carol, "Home", null, "v2", null)
        assertEquals("v2", service.page(project, "Home")?.content)
        assertEquals("v1", service.page(project, "Home", first)?.content)
        val history = service.history(project, "Home", 0, 10)
        assertEquals(listOf("Update Home", "Create Home"), history.map { it.shortMessage })
        assertEquals("Carol", history.first().authorName)
    }

    @Test
    fun `제목을 바꾸면 한 커밋으로 이름이 바뀌고 내용은 그대로이다`() {
        service.create(project, bob, "Old Name", "body", null)
        service.update(project, bob, "Old Name", "New Name", "body", null)
        assertNull(service.page(project, "Old Name"))
        assertEquals("body", service.page(project, "New Name")?.content)
        assertEquals(2, commitCount())
        assertEquals("Rename Old-Name to New-Name", service.history(project, "New Name", 0, 10).first().shortMessage)
    }

    @Test
    fun `제목을 바꾸면서 내용도 고칠 수 있다`() {
        service.create(project, bob, "A", "1", null)
        service.update(project, bob, "A", "B", "2", "바꿈")
        assertEquals("2", service.page(project, "B")?.content)
        assertNull(service.page(project, "A"))
    }

    @Test
    fun `이미 있는 제목으로 바꾸려 하면 거부한다`() {
        service.create(project, bob, "A", "1", null)
        service.create(project, bob, "B", "2", null)
        assertFailsWith<WikiPageExistsException> { service.update(project, bob, "A", "B", "1", null) }
        assertEquals("1", service.page(project, "A")?.content)
        assertEquals("2", service.page(project, "B")?.content)
    }

    @Test
    fun `같은 제목을 다시 주면 내용만 고친다`() {
        service.create(project, bob, "A b", "1", null)
        service.update(project, bob, "A b", "A-b", "2", null)
        assertEquals("2", service.page(project, "A b")?.content)
        assertEquals("Update A-b", service.history(project, "A b", 0, 10).first().shortMessage)
    }

    @Test
    fun `없는 페이지를 고치면 찾을 수 없다`() {
        assertFailsWith<WikiPageNotFoundException> { service.update(project, bob, "Nope", null, "x", null) }
        service.create(project, bob, "Home", "x", null)
        assertFailsWith<WikiPageNotFoundException> { service.update(project, bob, "Nope", null, "x", null) }
    }

    @Test
    fun `다른 페이지는 고쳐도 그대로이다`() {
        service.create(project, bob, "A", "a", null)
        service.create(project, bob, "B", "b", null)
        service.update(project, bob, "A", null, "a2", null)
        assertEquals("b", service.page(project, "B")?.content)
    }

    // --- 지우기 ---

    @Test
    fun `지우면 페이지가 사라지고 지운 커밋이 남는다`() {
        service.create(project, bob, "A", "a", null)
        service.create(project, bob, "B", "b", null)
        service.delete(project, carol, "A", null)
        assertNull(service.page(project, "A"))
        assertEquals("b", service.page(project, "B")?.content)
        assertEquals("Delete A", service.history(project, "A", 0, 10).first().shortMessage)
    }

    @Test
    fun `없는 페이지를 지우면 찾을 수 없다`() {
        service.create(project, bob, "A", "a", null)
        assertFailsWith<WikiPageNotFoundException> { service.delete(project, bob, "Nope", null) }
    }

    // --- 이력과 비교 ---

    @Test
    fun `이력은 최신순이고 쪽으로 나눈다`() {
        service.create(project, bob, "Home", "1", null)
        (2..5).forEach { service.update(project, bob, "Home", null, "$it", "v$it") }
        assertEquals(listOf("v5", "v4"), service.history(project, "Home", 0, 2).map { it.shortMessage })
        assertEquals(listOf("v3", "v2"), service.history(project, "Home", 1, 2).map { it.shortMessage })
        assertEquals(listOf("Create Home"), service.history(project, "Home", 2, 2).map { it.shortMessage })
        assertTrue(service.history(project, "Nope", 0, 10).isEmpty())
    }

    @Test
    fun `커밋 하나의 변경을 diff로 본다`() {
        service.create(project, bob, "Home", "line1\n", null)
        service.update(project, bob, "Home", null, "line1\nline2\n", null)
        val ids = service.history(project, "Home", 0, 10).map { it.id }
        val diff = service.diff(project, "Home", ids[0])
        assertContains(diff, "+line2")
        assertFalse(diff.contains("-line1"))
        assertContains(service.diff(project, "Home", ids[1]), "+line1")
    }

    @Test
    fun `두 리비전을 비교한다`() {
        service.create(project, bob, "Home", "a\n", null)
        service.update(project, bob, "Home", null, "b\n", null)
        service.update(project, bob, "Home", null, "c\n", null)
        val ids = service.history(project, "Home", 0, 10).map { it.id }
        val diff = service.compare(project, "Home", ids[2], ids[0])
        assertContains(diff, "-a")
        assertContains(diff, "+c")
    }

    @Test
    fun `없는 리비전이나 페이지의 diff는 찾을 수 없다`() {
        service.create(project, bob, "Home", "a", null)
        assertFailsWith<WikiPageNotFoundException> { service.diff(project, "Home", "0".repeat(40)) }
        assertFailsWith<WikiPageNotFoundException> { service.compare(project, "Home", "zzz", "yyy") }
    }

    // --- 사용할 수 없는 위키 ---

    @Test
    fun `같은 소유자에 이름이 demo 점 wiki 인 프로젝트가 있으면 위키를 쓸 수 없다`() {
        clash = true
        assertFalse(service.available(project))
        assertTrue(service.pages(project).isEmpty())
        assertNull(service.page(project, "Home"))
        assertFailsWith<WikiUnavailableException> { service.create(project, bob, "Home", "x", null) }
        assertFalse(wikis.exists("alice", "demo"))
    }

    // --- 동시 수정 ---

    @Test
    fun `저장소를 연 사이에 다른 커밋이 들어오면 충돌로 거부한다`() {
        service.create(project, bob, "Home", "1", null)
        val racing = WikiService(wikis, { _, _ -> clash })
        racing.beforeCommit = { service.update(project, carol, "Home", null, "carol", null) }
        assertFailsWith<WikiConflictException> { racing.update(project, bob, "Home", null, "bob", null) }
        assertEquals("carol", service.page(project, "Home")?.content)
        assertNotNull(service.page(project, "Home"))
    }
}
