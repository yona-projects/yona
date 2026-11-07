package dev.yona.repository

import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 운영자가 한 번 돌리는 스크립트 `tools/migrate-git-refs.sh`(D-93)를 검증한다.
 * v1.6 저장소의 PR 숨은 참조 `refs/yobi/...`를 `refs/yona/...`로 옮긴다. 가리키는 커밋은 그대로이고 이름만 바뀐다.
 */
class GitRefsMigrationScriptTest {
    @TempDir
    lateinit var data: File

    private val script = File("tools/migrate-git-refs.sh").absolutePath

    /** `<data>/repo/git/<owner>/<name>.git` bare 저장소를 만들고 커밋 하나와 숨은 참조를 심는다. 커밋 id를 돌려준다. */
    private fun repo(owner: String, name: String, vararg refs: String, pack: Boolean = false): String {
        val work = File(data, "work-$owner-$name")
        val id = Git.init().setDirectory(work).call().use { git ->
            File(work, "a.txt").writeText("a")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("first").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").setSign(false).call().id.name
        }
        val bare = File(data, "repo/git/$owner/$name.git")
        Git.cloneRepository().setURI(work.toURI().toString()).setDirectory(bare).setBare(true).call().close()
        Git.open(bare).use { g ->
            for (ref in refs) g.repository.updateRef(ref).apply { setNewObjectId(g.repository.resolve("HEAD")); update() }
        }
        if (pack) ProcessBuilder("git", "-C", bare.path, "pack-refs", "--all").redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor()
        return id
    }

    private fun run(vararg args: String): Pair<Int, String> {
        val p = ProcessBuilder(listOf("bash", script) + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        return p.waitFor() to out
    }

    private fun refs(owner: String, name: String, prefix: String): Map<String, String> =
        Git.open(File(data, "repo/git/$owner/$name.git")).use { g ->
            g.repository.refDatabase.getRefsByPrefix(prefix).associate { it.name to it.objectId.name }
        }

    @Test
    fun `refs yobi 아래 참조를 refs yona 아래로 옮기고 커밋은 그대로다`() {
        val id = repo("alice", "p1", "refs/yobi/pull/1/head", "refs/yobi/pull/1/merged", "refs/yobi/pull-check/bob/p/x")
        val (code, out) = run(data.path)
        assertEquals(0, code, out)
        assertEquals(emptyMap(), refs("alice", "p1", "refs/yobi/"))
        assertEquals(
            mapOf("refs/yona/pull/1/head" to id, "refs/yona/pull/1/merged" to id, "refs/yona/pull-check/bob/p/x" to id),
            refs("alice", "p1", "refs/yona/"),
        )
        assertTrue(out.contains("3"), "옮긴 개수를 알려야 한다: $out")
    }

    @Test
    fun `packed refs에 든 참조도 옮기고 브랜치는 건드리지 않는다`() {
        val id = repo("alice", "p2", "refs/yobi/pull/7/head", pack = true)
        val before = refs("alice", "p2", "refs/heads/")
        val (code, out) = run(data.path)
        assertEquals(0, code, out)
        assertEquals(mapOf("refs/yona/pull/7/head" to id), refs("alice", "p2", "refs/yona/"))
        assertEquals(emptyMap(), refs("alice", "p2", "refs/yobi/"))
        assertEquals(before, refs("alice", "p2", "refs/heads/"))
    }

    @Test
    fun `여러 저장소를 한 번에 옮기고 다시 돌려도 결과가 같다 (멱등)`() {
        repo("alice", "a", "refs/yobi/pull/1/head")
        repo("bob", "b", "refs/yobi/pull/2/head")
        repo("bob", "c")
        assertEquals(0, run(data.path).first)
        val first = listOf(refs("alice", "a", "refs/yona/"), refs("bob", "b", "refs/yona/"), refs("bob", "c", "refs/yona/"))
        val (code, out) = run(data.path)
        assertEquals(0, code, out)
        assertEquals(first, listOf(refs("alice", "a", "refs/yona/"), refs("bob", "b", "refs/yona/"), refs("bob", "c", "refs/yona/")))
        assertEquals(1, first[0].size); assertEquals(1, first[1].size); assertEquals(0, first[2].size)
    }

    @Test
    fun `dry-run은 아무것도 바꾸지 않는다`() {
        repo("alice", "d", "refs/yobi/pull/1/head")
        val (code, out) = run("--dry-run", data.path)
        assertEquals(0, code, out)
        assertEquals(1, refs("alice", "d", "refs/yobi/").size)
        assertEquals(0, refs("alice", "d", "refs/yona/").size)
        assertTrue(out.contains("refs/yobi/pull/1/head"), "옮길 참조를 보여 줘야 한다: $out")
    }

    @Test
    fun `데이터 디렉터리가 없거나 인자가 없으면 실패한다`() {
        assertTrue(run().first != 0)
        assertTrue(run(File(data, "none").path).first != 0)
    }
}
