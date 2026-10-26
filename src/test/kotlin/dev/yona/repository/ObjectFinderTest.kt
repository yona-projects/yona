package dev.yona.repository

import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.lib.TreeFormatter
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Date
import java.util.TimeZone
import kotlin.test.assertEquals

/**
 * 폴더 목록의 "마지막으로 바꾼 커밋" 선택 규칙을 검증한다 (v1.6 `ObjectFinder`).
 *
 * 기대값은 v1.6 알고리즘을 JGit 4.5로 직접 실행해 얻은 값이다. `git log -1 -- 경로`는 병합 커밋과 모드만 바뀐 커밋, 같은 내용을 양쪽에서 추가한 경우에
 * 다른 커밋을 고른다.
 */
class ObjectFinderTest {

    @TempDir lateinit var dir: File
    private lateinit var repository: Repository

    @BeforeEach
    fun open() { repository = RepositoryBuilder().setGitDir(File(dir, "r.git")).setBare().build().also { it.create(true) } }

    @AfterEach
    fun close() = repository.close()

    private data class Entry(val content: String, val mode: FileMode = FileMode.REGULAR_FILE)

    /** [time]초에 [parents]를 부모로 하고 [files]를 최상위 파일로 담은 커밋을 만든다. 메시지는 [name]이다. */
    private fun commit(name: String, time: Long, parents: List<ObjectId>, files: Map<String, Entry>): ObjectId {
        repository.newObjectInserter().use { inserter ->
            val tree = TreeFormatter()
            files.toSortedMap().forEach { (n, e) -> tree.append(n, e.mode, inserter.insert(Constants.OBJ_BLOB, e.content.toByteArray())) }
            val ident = PersonIdent("t", "t@example.com", Date(time * 1000), TimeZone.getTimeZone("UTC"))
            val id = inserter.insert(CommitBuilder().apply { setTreeId(inserter.insert(tree)); setParentIds(parents); author = ident; committer = ident; message = name })
            inserter.flush()
            return id
        }
    }

    /** [head]에서 최상위 목록의 항목마다 마지막으로 바꾼 커밋의 메시지를 찾는다. */
    private fun find(head: ObjectId): Map<String, String?> {
        val commit = RevWalk(repository).use { it.parseCommit(head) }
        val walk = TreeWalk(repository).also { it.addTree(commit.tree) }
        val found = ObjectFinder(repository, "", walk, head, { entry, c -> entry["msg"] = c.shortMessage }, { it }).find()
        val names = TreeWalk(repository).also { it.addTree(commit.tree) }.use { w -> generateSequence { if (w.next()) w.nameString else null }.toList() }
        return names.associateWith { found[it]?.get("msg") as String? }
    }

    private fun f(c: String) = Entry(c)

    @Test
    fun `병합이 한 부모와 같으면 다른 가지의 커밋을 고른다 (git log는 병합 쪽 부모를 고른다)`() {
        val c1 = commit("c1", 100, emptyList(), mapOf("F" to f("1"), "G" to f("g")))
        val s = commit("S", 200, listOf(c1), mapOf("F" to f("2"), "G" to f("g")))
        val c = commit("C", 300, listOf(c1), mapOf("F" to f("3"), "G" to f("g")))
        val m = commit("M", 400, listOf(c, s), mapOf("F" to f("2"), "G" to f("g")))
        // 충돌을 S 쪽으로 푼 병합: v1.6은 M이 부모 S와 같아 건너뛰고, 모든 부모와 다른 F=3을 가진 C를 고른다
        assertEquals(mapOf("F" to "C", "G" to "c1"), find(m))
    }

    @Test
    fun `같은 내용을 양쪽 가지에서 추가하면 더 최근 커밋을 고른다`() {
        val e1 = commit("e1", 4100, emptyList(), mapOf("A" to f("a")))
        val l = commit("eL", 4200, listOf(e1), mapOf("A" to f("a"), "N" to f("n")))
        val r = commit("eR", 4300, listOf(e1), mapOf("A" to f("a"), "N" to f("n")))
        val m = commit("eMM", 4400, listOf(l, r), mapOf("A" to f("a"), "N" to f("n")))
        assertEquals(mapOf("A" to "e1", "N" to "eR"), find(m))
    }

    @Test
    fun `모드만 바뀐 커밋은 바꾼 것으로 보지 않는다`() {
        val h1 = commit("h1", 7100, emptyList(), mapOf("F" to f("1")))
        val h2 = commit("h2", 7200, listOf(h1), mapOf("F" to Entry("1", FileMode.EXECUTABLE_FILE)))
        // 객체 번호가 같으므로 v1.6은 h1을 고른다(git log -- F는 h2)
        assertEquals(mapOf("F" to "h1"), find(h2))
    }

    @Test
    fun `선형 이력은 git log와 같다`() {
        val c1 = commit("c1", 100, emptyList(), mapOf("F" to f("1"), "G" to f("1")))
        val c2 = commit("c2", 200, listOf(c1), mapOf("F" to f("1"), "G" to f("2")))
        val c3 = commit("c3", 300, listOf(c2), mapOf("F" to f("3"), "G" to f("2")))
        assertEquals(mapOf("F" to "c3", "G" to "c2"), find(c3))
    }
}
