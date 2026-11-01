package dev.yona.repository

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.support.GitTestSupport
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 온라인 커밋을 검증한다.
 *
 * 원본: `app/playRepository/BareCommit.java`, `app/utils/GitUtil.java`.
 */
class OnlineCommitTest {

    @TempDir
    lateinit var dataDir: File

    private lateinit var repositories: RepositoryService
    private lateinit var online: OnlineCommit
    private val project = Project(owner = "alice", name = "demo", vcs = "GIT")
    private val author = User(loginId = "bob", name = "Bob", email = "bob@example.com")

    @BeforeEach
    fun setUp() {
        repositories = DataDirectory(YonaProperties(dataDir = dataDir.path)).let { RepositoryService(GitRepositories(it), dev.yona.repository.svn.SvnRepositories(it), WikiRepositories(it)) }
        online = OnlineCommit(repositories)
        repositories.createRepository(project)
    }

    private fun files(ref: String = Constants.HEAD): Map<String, String> = repositories.openRepository(project).use { repository ->
        val id = repository.resolve("$ref^{commit}") ?: return emptyMap()
        RevWalk(repository).use { walk ->
            TreeWalk(repository).use { tree ->
                tree.addTree(walk.parseCommit(id).tree); tree.isRecursive = true
                val result = linkedMapOf<String, String>()
                while (tree.next()) result[tree.pathString] = String(repository.open(tree.getObjectId(0)).bytes)
                result
            }
        }
    }

    private fun head() = repositories.openRepository(project).use { it.resolve(Constants.HEAD) }

    // --- 최상위 파일 한 개를 HEAD에 커밋 (BareCommit.commitTextFile 3인자) ---

    @Test
    fun `빈 저장소에 첫 커밋을 만든다`() {
        val id = online.commitRootFile(project, author, "README.md", "# 안녕", "Update README.md")
        assertNotNull(id); assertEquals(id, head())
        assertEquals(mapOf("README.md" to "# 안녕\n"), files())
        repositories.openRepository(project).use { repository ->
            val commit = RevWalk(repository).use { it.parseCommit(id) }
            assertEquals("Update README.md", commit.fullMessage)
            assertEquals("Bob", commit.authorIdent.name); assertEquals("bob@example.com", commit.committerIdent.emailAddress)
            assertEquals(0, commit.parentCount)
        }
    }

    @Test
    fun `기존 파일을 대체하고 다른 파일은 유지하며 이전 커밋을 부모로 한다`() {
        GitTestSupport.commitFiles(repositories, project, mapOf("README.md" to "old".toByteArray(), "a.txt" to "a".toByteArray(), "z.txt" to "z".toByteArray()))
        val before = head()
        val id = online.commitRootFile(project, author, "README.md", "new", "msg")
        assertEquals(mapOf("README.md" to "new\n", "a.txt" to "a", "z.txt" to "z"), files())
        repositories.openRepository(project).use { repository ->
            val commit = RevWalk(repository).use { it.parseCommit(id) }
            assertEquals(before, commit.getParent(0))
        }
    }

    @Test
    fun `새 파일은 이름 순서에 맞게 끼워 넣는다`() {
        GitTestSupport.commitFiles(repositories, project, mapOf("a.txt" to "a".toByteArray(), "z.txt" to "z".toByteArray()))
        online.commitRootFile(project, author, "m.md", "m", "add")
        assertEquals(listOf("a.txt", "m.md", "z.txt"), files().keys.toList())
        online.commitRootFile(project, author, "zz.md", "zz", "add")
        assertEquals(listOf("a.txt", "m.md", "z.txt", "zz.md"), files().keys.toList())
    }

    @Test
    fun `내용 끝에 줄바꿈을 붙이고 CRLF 파일에도 v1_6처럼 LF로 쓴다`() {
        GitTestSupport.commitFiles(repositories, project, mapOf("README.md" to "a\r\nb\r\n".toByteArray()))
        online.commitRootFile(project, author, "README.md", "x\ny", "msg")
        // 바꾸라는 대상이 DOS여도 v1.6의 변환은 아무것도 하지 않는다(R-27). 끝에는 파일의 줄바꿈 종류(UNIX, 내용 기준)가 붙는다.
        assertEquals("x\ny\n", files()["README.md"])
    }

    @Test
    fun `줄바꿈은 요청한 파일이 아니라 최상위 트리의 첫 엔트리로 판정한다 (v1_6 결함 재현)`() {
        // v1.6 BareRepository.getFileObjectId는 TreeWalk.next() 없이 getObjectId(0)을 읽어 깃 정렬 순서의 첫 엔트리를 본다
        GitTestSupport.commitFiles(repositories, project, mapOf("ISSUE_TEMPLATE.md" to "a\r\nb\r\n".toByteArray(), "README.md" to "a\nb\n".toByteArray()))
        online.commitRootFile(project, author, "README.md", "x\r\ny", "msg")
        // 첫 엔트리 ISSUE_TEMPLATE.md가 CRLF라 DOS로 판정한다. 변환은 하지 않고 끝에 CRLF가 붙는다.
        assertEquals("x\r\ny\r\n", files()["README.md"])
    }

    @Test
    fun `없는 파일을 새로 만들 때도 첫 엔트리로 판정한다`() {
        GitTestSupport.commitFiles(repositories, project, mapOf("README.md" to "a\nb\n".toByteArray(), "a.txt" to "x".toByteArray()))
        online.commitRootFile(project, author, "ISSUE_TEMPLATE.md", "t\r\nu", "msg")
        // 첫 엔트리 README.md가 LF라 UNIX로 판정해 본문의 CRLF를 LF로 바꾼다
        assertEquals("t\nu\n", files()["ISSUE_TEMPLATE.md"])
    }

    // --- 지정한 브랜치에 경로로 커밋 (BareCommit.commitTextFile 4인자, GitUtil.commitTextFile) ---

    @Test
    fun `빈 저장소의 브랜치에 중첩 경로 파일을 커밋한다`() {
        val id = online.commitTextFile(project, author, "master", "docs/guide/a.md", "내용", "Add a.md")
        assertEquals(id, repositories.openRepository(project).use { it.resolve("refs/heads/master") })
        assertEquals(mapOf("docs/guide/a.md" to "내용"), files("refs/heads/master"))
    }

    @Test
    fun `기존 브랜치의 다른 파일을 유지하고 경로의 파일만 바꾼다`() {
        GitTestSupport.commitFiles(repositories, project, mapOf("a.txt" to "a".toByteArray(), "b.txt" to "b".toByteArray()))
        val branch = repositories.openRepository(project).use { it.exactRef(Constants.HEAD).target.name.removePrefix("refs/heads/") }
        online.commitTextFile(project, author, branch, "b.txt", "B2", "edit b")
        online.commitTextFile(project, author, branch, "dir/c.txt", "c", "add c")
        assertEquals(mapOf("a.txt" to "a", "b.txt" to "B2", "dir/c.txt" to "c"), files("refs/heads/$branch"))
    }

    @Test
    fun `내용을 가공하지 않는다`() {
        online.commitTextFile(project, author, "master", "a.txt", "no newline", "m")
        assertEquals("no newline", files("refs/heads/master")["a.txt"])
    }

    // --- 읽기 (GitUtil.getReadTextFile) ---

    @Test
    fun `브랜치의 파일을 읽고 없으면 null이다`() {
        online.commitTextFile(project, author, "master", "docs/a.md", "읽기", "m")
        assertEquals("읽기", online.readTextFile(project, "master", "docs/a.md"))
        assertNull(online.readTextFile(project, "master", "none.md"))
        assertNull(online.readTextFile(project, "nobranch", "docs/a.md"))
    }
}
