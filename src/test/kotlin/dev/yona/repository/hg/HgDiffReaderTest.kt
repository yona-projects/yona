package dev.yona.repository.hg

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.domain.vcs.DiffSource
import dev.yona.domain.vcs.FileChange
import dev.yona.domain.vcs.FileChangeType
import dev.yona.repository.DataDirectory
import dev.yona.support.HgTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** Mercurial 저장소의 커밋 사이 변경 목록(추가, 수정, 삭제, 이름 바꿈, 복사, 권한)을 실제 `hg`로 만든 저장소에서 확인한다. */
class HgDiffReaderTest {

    @TempDir lateinit var dataDir: File
    private lateinit var repos: HgRepositories
    private lateinit var reader: HgDiffReader
    private val project = Project(owner = "tester", name = "proj", vcs = "Mercurial")
    private lateinit var dir: File

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        repos = HgRepositories(DataDirectory(YonaProperties(dataDir = dataDir.path)))
        reader = HgDiffReader(repos)
        repos.create("tester", "proj")
        dir = repos.directoryOf("tester", "proj")
    }

    private fun changes(source: DiffSource?) = source!!.changes.associateBy { it.pathB ?: it.pathA!! }

    private fun text(bytes: ByteArray?) = bytes?.let { String(it) }

    @Test
    fun `첫 커밋은 모든 파일이 추가이고 앞쪽이 없다`() {
        val c0 = HgTestSupport.commit(dir, "처음", mapOf("a.txt" to "one\n", "dir/b.txt" to "b\n"))
        reader.commitDiff(project, c0)!!.use { source ->
            assertThat(source.commitA).isNull(); assertThat(source.commitB).isEqualTo(c0)
            val all = changes(source)
            assertThat(all.keys).containsExactlyInAnyOrder("a.txt", "dir/b.txt")
            assertThat(all.values).allSatisfy { assertThat(it.type).isEqualTo(FileChangeType.ADD); assertThat(it.pathA).isNull(); assertThat(it.oldMode).isEqualTo(0) }
            assertThat(text(all.getValue("a.txt").contentB())).isEqualTo("one\n"); assertThat(all.getValue("a.txt").contentA()).isNull()
            assertThat(all.getValue("a.txt").newMode).isEqualTo(0x81A4)
        }
    }

    @Test
    fun `수정과 삭제와 추가는 부모와 비교한다`() {
        val c0 = HgTestSupport.commit(dir, "처음", mapOf("a.txt" to "one\n", "gone.txt" to "g\n"))
        val c1 = HgTestSupport.commit(dir, "둘째", mapOf("a.txt" to "one\ntwo\n", "gone.txt" to null, "new.txt" to "n\n"))
        reader.commitDiff(project, c1)!!.use { source ->
            assertThat(source.commitA).isEqualTo(c0); assertThat(source.commitB).isEqualTo(c1)
            val all = changes(source)
            assertThat(all.getValue("a.txt").type).isEqualTo(FileChangeType.MODIFY)
            assertThat(text(all.getValue("a.txt").contentA())).isEqualTo("one\n"); assertThat(text(all.getValue("a.txt").contentB())).isEqualTo("one\ntwo\n")
            assertThat(all.getValue("gone.txt").type).isEqualTo(FileChangeType.DELETE)
            assertThat(all.getValue("gone.txt").pathB).isNull(); assertThat(text(all.getValue("gone.txt").contentA())).isEqualTo("g\n")
            assertThat(all.getValue("new.txt").type).isEqualTo(FileChangeType.ADD)
            assertThat(all.keys).containsExactlyInAnyOrder("a.txt", "gone.txt", "new.txt")
        }
    }

    @Test
    fun `이름을 바꾸면 복사 기록으로 이름 바꿈이 되고 원래 파일을 남기면 복사이다`() {
        HgTestSupport.commit(dir, "처음", mapOf("old.txt" to "내용\n", "keep.txt" to "유지\n"))
        HgTestSupport.rename(dir, "old.txt", "renamed.txt")
        HgTestSupport.hg(dir, "copy", "keep.txt", "copied.txt")
        val c1 = HgTestSupport.commit(dir, "이름 바꿈과 복사", emptyMap())
        reader.commitDiff(project, c1)!!.use { source ->
            val renamed = source.changes.single { it.pathB == "renamed.txt" }
            assertThat(renamed.type).isEqualTo(FileChangeType.RENAME); assertThat(renamed.pathA).isEqualTo("old.txt")
            assertThat(text(renamed.contentA())).isEqualTo("내용\n"); assertThat(text(renamed.contentB())).isEqualTo("내용\n")
            val copied = source.changes.single { it.pathB == "copied.txt" }
            assertThat(copied.type).isEqualTo(FileChangeType.COPY); assertThat(copied.pathA).isEqualTo("keep.txt")
            // 이름을 바꾼 원래 파일은 삭제로 따로 나오지 않는다
            assertThat(source.changes.none { it.type == FileChangeType.DELETE && it.pathA == "old.txt" }).isTrue()
        }
    }

    @Test
    fun `권한만 바뀌어도 수정이고 모드 비트가 실린다`() {
        HgTestSupport.commit(dir, "처음", mapOf("run.sh" to "#!/bin/sh\n"))
        File(dir, "run.sh").setExecutable(true)
        val c1 = HgTestSupport.hg(dir, "commit", "-m", "권한", "-d", "2026-11-02 10:00 +0900").let { HgTestSupport.hg(dir, "log", "-r", ".", "--template", "{node}") }
        reader.commitDiff(project, c1)!!.use { source ->
            val change = source.changes.single()
            assertThat(change.type).isEqualTo(FileChangeType.MODIFY)
            assertThat(change.oldMode).isEqualTo(0x81A4); assertThat(change.newMode).isEqualTo(0x81ED)
        }
    }

    @Test
    fun `임의의 두 리비전 사이도 비교하고 풀 수 없는 앞쪽은 빈 쪽으로 본다`() {
        val c0 = HgTestSupport.commit(dir, "처음", mapOf("a.txt" to "1\n"))
        HgTestSupport.commit(dir, "둘째", mapOf("b.txt" to "2\n"))
        val c2 = HgTestSupport.commit(dir, "셋째", mapOf("c.txt" to "3\n"))
        reader.diff(project, c0, c2).use { source ->
            assertThat(source.commitA).isEqualTo(c0); assertThat(source.changes.map { it.pathB }).containsExactlyInAnyOrder("b.txt", "c.txt")
        }
        reader.diff(project, "없는이름", c2).use { source ->
            assertThat(source.commitA).isNull(); assertThat(source.changes.map { it.type }.toSet()).containsExactly(FileChangeType.ADD)
        }
    }

    @Test
    fun `풀 수 없는 리비전이면 null이고 병합 커밋은 첫 부모와 비교한다`() {
        val c0 = HgTestSupport.commit(dir, "처음", mapOf("a.txt" to "1\n"))
        assertThat(reader.commitDiff(project, "없는이름")).isNull()
        val left = HgTestSupport.commit(dir, "left", mapOf("left.txt" to "l\n"), "2026-11-02 10:00 +0900")
        HgTestSupport.hg(dir, "update", "-C", c0)
        val right = HgTestSupport.commit(dir, "right", mapOf("right.txt" to "r\n"), "2026-11-03 10:00 +0900")
        HgTestSupport.hg(dir, "merge", left)
        HgTestSupport.hg(dir, "commit", "-m", "merge", "-d", "2026-11-04 10:00 +0900")
        val merge = HgTestSupport.hg(dir, "log", "-r", ".", "--template", "{node}")
        reader.commitDiff(project, merge)!!.use { source ->
            // 첫 부모(right)와 비교하면 left의 변경이 추가로 보인다
            assertThat(source.commitA).isEqualTo(right)
            assertThat(source.changes.map { it.pathB }).containsExactly("left.txt")
        }
    }

    @Test
    fun `서로 다른 저장소의 커밋도 비교한다(포크 사이)`() {
        val c0 = HgTestSupport.commit(dir, "처음", mapOf("a.txt" to "1\n"))
        repos.create("tester", "fork")
        val forkDir = repos.directoryOf("tester", "fork")
        HgTestSupport.hg(forkDir, "pull", dir.path)
        HgTestSupport.hg(forkDir, "update", "-r", c0)
        val f1 = HgTestSupport.commit(forkDir, "포크에서", mapOf("a.txt" to "1\n2\n", "extra.txt" to "x\n"), "2026-11-02 10:00 +0900")
        val fork = Project(owner = "tester", name = "fork", vcs = "Mercurial")
        reader.diff(project, c0, fork, f1).use { source ->
            assertThat(source.commitA).isEqualTo(c0); assertThat(source.commitB).isEqualTo(f1)
            val all = changes(source)
            assertThat(all.getValue("a.txt").type).isEqualTo(FileChangeType.MODIFY)
            assertThat(text(all.getValue("a.txt").contentB())).isEqualTo("1\n2\n")
            assertThat(all.getValue("extra.txt").type).isEqualTo(FileChangeType.ADD)
        }
    }
}
