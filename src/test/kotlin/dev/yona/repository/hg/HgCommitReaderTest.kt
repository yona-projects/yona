package dev.yona.repository.hg

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.repository.DataDirectory
import dev.yona.support.HgTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Date

/** Mercurial 저장소의 커밋 이력과 커밋 하나, 부모를 실제 `hg`로 만든 저장소에서 읽는다. */
class HgCommitReaderTest {

    @TempDir lateinit var dataDir: File
    private lateinit var repos: HgRepositories
    private lateinit var reader: HgCommitReader
    private val project = Project(owner = "tester", name = "proj", vcs = "Mercurial")
    private lateinit var dir: File
    private lateinit var c0: String
    private lateinit var c1: String
    private lateinit var c2: String

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        repos = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        reader = HgCommitReader(repos)
        repos.create("tester", "proj")
        dir = repos.directoryOf("tester", "proj")
        c0 = HgTestSupport.commit(dir, "first commit", mapOf("a.txt" to "one\n", "run.sh" to "#!/bin/sh\n", "sub/b.txt" to "b\n"), "2026-11-01 10:00 +0900", "Alice Kim <alice@example.com>")
        HgTestSupport.rename(dir, "sub/b.txt", "sub/c.txt")
        c1 = HgTestSupport.commit(dir, "second\n\nbody line", mapOf("a.txt" to "one\ntwo\n"), "2026-11-02 10:00 +0900", "bob")
        c2 = HgTestSupport.commit(dir, "docs only", mapOf("docs/readme.txt" to "hi\n"), "2026-11-03 10:00 +0900", "Alice Kim <alice@example.com>")
        HgTestSupport.bookmark(dir, "main")
    }

    @Test
    fun `이력은 최근 것이 앞이고 쪽으로 나눈다`() {
        assertThat(reader.history(project, 0, 2, null, null)!!.map { it.id }).containsExactly(c2, c1)
        assertThat(reader.history(project, 1, 2, null, null)!!.map { it.id }).containsExactly(c0)
        assertThat(reader.history(project, 2, 2, null, null)).isEmpty()
    }

    @Test
    fun `리비전은 이름표, 노드, 짧은 노드, 번호로 줄 수 있고 그 리비전부터 거슬러 오른다`() {
        assertThat(reader.history(project, 0, 10, "refs/heads/main", null)!!.map { it.id }).containsExactly(c2, c1, c0)
        assertThat(reader.history(project, 0, 10, "main", null)!!.map { it.id }).containsExactly(c2, c1, c0)
        assertThat(reader.history(project, 0, 10, c1, null)!!.map { it.id }).containsExactly(c1, c0)
        assertThat(reader.history(project, 0, 10, c1.take(12), null)!!.map { it.id }).containsExactly(c1, c0)
        assertThat(reader.history(project, 0, 10, "0", null)!!.map { it.id }).containsExactly(c0)
    }

    @Test
    fun `경로를 주면 그 파일이나 폴더를 바꾼 커밋만 모은다`() {
        assertThat(reader.history(project, 0, 10, null, "a.txt")!!.map { it.id }).containsExactly(c1, c0)
        assertThat(reader.history(project, 0, 10, null, "sub")!!.map { it.id }).containsExactly(c1, c0)
        assertThat(reader.history(project, 0, 10, null, "docs")!!.map { it.id }).containsExactly(c2)
        assertThat(reader.history(project, 0, 10, null, "docs/readme.txt")!!.map { it.id }).containsExactly(c2)
        assertThat(reader.history(project, 0, 10, null, "nothing")!!).isEmpty()
        // 이름이 같은 접두어인 다른 경로는 맞지 않는다
        assertThat(reader.history(project, 0, 10, null, "doc")!!).isEmpty()
    }

    @Test
    fun `풀 수 없는 리비전과 비어 있는 저장소의 이력은 null이다`() {
        assertThat(reader.history(project, 0, 10, "없는이름", null)).isNull()
        repos.create("tester", "empty")
        assertThat(reader.history(Project(owner = "tester", name = "empty", vcs = "Mercurial"), 0, 10, null, null)).isNull()
    }

    @Test
    fun `커밋의 값은 Git 커밋과 같은 모양이다`() {
        val c = reader.commit(project, c1)!!
        assertThat(c.id).isEqualTo(c1).hasSize(40)
        assertThat(c.shortId).isEqualTo(c1.take(12))
        assertThat(c.shortMessage).isEqualTo("second")
        assertThat(c.message).isEqualTo("second\n\nbody line")
        // 사용자 이름에 이메일이 없으면 이메일은 비어 있다
        assertThat(c.authorName).isEqualTo("bob"); assertThat(c.authorEmail).isEmpty()
        // 2026-11-02 10:00 +0900
        assertThat(c.authorDate).isEqualTo(Date(1793581200L * 1000)); assertThat(c.commitTime).isEqualTo(c.authorDate)
        assertThat(c.committerName).isEqualTo("bob")
        assertThat(c.parentCount).isEqualTo(1)
        assertThat(c.signature).isNull()
        val first = reader.commit(project, c0)!!
        assertThat(first.authorName).isEqualTo("Alice Kim"); assertThat(first.authorEmail).isEqualTo("alice@example.com")
        assertThat(first.committerEmail).isEqualTo("alice@example.com"); assertThat(first.parentCount).isEqualTo(0)
    }

    @Test
    fun `커밋은 짧은 노드와 이름표로도 찾고 없으면 null이다`() {
        assertThat(reader.commit(project, c1.take(12))!!.id).isEqualTo(c1)
        assertThat(reader.commit(project, "main")!!.id).isEqualTo(c2)
        assertThat(reader.commit(project, "refs/heads/main")!!.id).isEqualTo(c2)
        assertThat(reader.commit(project, "ffffffffffff")).isNull()
        assertThat(reader.commit(project, "없는이름")).isNull()
    }

    @Test
    fun `여러 커밋을 한 번에 읽으면 준 번호를 열쇠로 돌려주고 풀 수 없는 번호는 빠진다`() {
        val map = reader.commits(project, listOf(c0, c1.take(12), "ffffffffffff", c0))
        assertThat(map.keys).containsExactlyInAnyOrder(c0, c1.take(12))
        assertThat(map.getValue(c1.take(12)).id).isEqualTo(c1)
        assertThat(reader.commits(project, emptyList())).isEmpty()
    }

    @Test
    fun `부모는 첫 부모이고 첫 커밋은 부모가 없다`() {
        assertThat(reader.parentOf(project, c1)!!.id).isEqualTo(c0)
        assertThat(reader.parentOf(project, c0)).isNull()
        assertThat(reader.parentOf(project, "ffffffffffff")).isNull()
    }

    @Test
    fun `병합 커밋은 부모가 둘이고 부모는 첫 부모이다`() {
        // c1에서 갈라진 두 줄기를 병합한다
        HgTestSupport.hg(dir, "update", "-C", c1)
        val left = HgTestSupport.commit(dir, "left", mapOf("left.txt" to "l\n"), "2026-11-04 10:00 +0900")
        HgTestSupport.hg(dir, "update", "-C", c2)
        val right = HgTestSupport.commit(dir, "right", mapOf("right.txt" to "r\n"), "2026-11-05 10:00 +0900")
        HgTestSupport.hg(dir, "merge", left)
        HgTestSupport.hg(dir, "commit", "-m", "merge", "-d", "2026-11-06 10:00 +0900")
        val merge = HgTestSupport.hg(dir, "log", "-r", ".", "--template", "{node}")
        assertThat(reader.commit(project, merge)!!.parentCount).isEqualTo(2)
        assertThat(reader.parentOf(project, merge)!!.id).isEqualTo(right)
        // 병합 커밋의 이력에는 양쪽 줄기가 모두 든다
        assertThat(reader.history(project, 0, 20, merge, null)!!.map { it.id }).contains(left, right, c0)
    }
}
