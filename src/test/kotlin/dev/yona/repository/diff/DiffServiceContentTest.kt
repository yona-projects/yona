package dev.yona.repository.diff

import dev.yona.domain.project.Project
import dev.yona.domain.vcs.ContentTooLargeException
import dev.yona.domain.vcs.DiffSource
import dev.yona.domain.vcs.FileChange
import dev.yona.domain.vcs.FileChangeType
import dev.yona.domain.vcs.TextDiffAlgorithm
import dev.yona.domain.vcs.VcsType
import dev.yona.observability.VcsMetrics
import dev.yona.repository.DiffReader
import dev.yona.repository.VcsBackends
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DiffService]가 저장소 종류와 무관하게 변경 목록과 파일 내용([DiffReader])만으로 파일별 차이를 만드는지 확인한다(Mercurial 도입을 위한 공통화 4단계).
 * 실제 저장소 없이 가짜 구현으로 시험하므로 Git이 아닌 종류도 같은 차이 화면과 JSON을 쓸 수 있다.
 */
class DiffServiceContentTest {

    private val project = Project(owner = "o", name = "p", vcs = "GIT")

    private fun lines(vararg l: String) = l.joinToString("\n", postfix = "\n").toByteArray()

    private class Source(override val commitA: String?, override val commitB: String?, override val changes: List<FileChange>) : DiffSource {
        var closed = false
        override val algorithm = TextDiffAlgorithm.HISTOGRAM
        override fun close() { closed = true }
    }

    private class FakeReader(private val source: Source?) : DiffReader {
        override val type = VcsType.GIT
        override fun commitDiff(project: Project, revision: String): DiffSource? = source
        override fun diff(project: Project, revA: String, revB: String): DiffSource = source!!
        override fun diff(projectA: Project, revA: String, projectB: Project, revB: String): DiffSource = source!!
    }

    private fun service(source: Source?) = DiffService(VcsBackends.of(diff = listOf(FakeReader(source))), VcsMetrics(SimpleMeterRegistry()))

    private fun change(type: FileChangeType, pathA: String?, pathB: String?, a: ByteArray? = null, b: ByteArray? = null, oldMode: Int = 0x81A4, newMode: Int = 0x81A4) =
        FileChange(type, pathA, pathB, oldMode, newMode, { a }, { b })

    @Test
    fun `수정한 파일은 내용으로 바뀐 곳을 구해 헝크로 나오고 변경 종류와 번호가 실린다`() {
        val before = lines("1", "2", "3", "4", "5", "6", "7", "8")
        val after = lines("1", "2", "3", "4 수정", "5", "6", "7", "8")
        val source = Source("a".repeat(40), "b".repeat(40), listOf(change(FileChangeType.MODIFY, "f.txt", "f.txt", before, after)))
        val diff = service(source).commitDiff(project, "b")!!.single()
        assertEquals(FileChangeType.MODIFY, diff.changeType)
        assertEquals("a".repeat(40), diff.commitA); assertEquals("b".repeat(40), diff.commitB)
        val hunk = diff.hunks()!!.single()
        assertTrue(hunk.lines.any { it.kind == DiffLineType.REMOVE && it.content.startsWith("4") })
        assertTrue(hunk.lines.any { it.kind == DiffLineType.ADD && it.content.startsWith("4 수정") })
        assertTrue(source.closed, "다 쓴 뒤에는 변경 목록의 저장소 자원을 닫아야 한다")
    }

    @Test
    fun `추가와 삭제는 헝크 없이 내용만 싣고 이진 파일은 내용을 싣지 않는다`() {
        val source = Source(null, "b".repeat(40), listOf(
            change(FileChangeType.ADD, null, "new.txt", b = lines("가", "나")),
            change(FileChangeType.DELETE, "gone.txt", null, a = lines("x")),
            change(FileChangeType.ADD, null, "bin.dat", b = byteArrayOf(0, 1, 2, 0, 3))
        ))
        val diffs = service(source).commitDiff(project, "b")!!.associateBy { it.pathB ?: it.pathA }
        assertNull(diffs.getValue("new.txt").hunks()); assertNotNull(diffs.getValue("new.txt").b); assertNull(diffs.getValue("new.txt").pathA)
        assertNotNull(diffs.getValue("gone.txt").a); assertNull(diffs.getValue("gone.txt").pathB)
        assertTrue(diffs.getValue("bin.dat").isBinaryB); assertNull(diffs.getValue("bin.dat").b)
    }

    @Test
    fun `이름 바꿈과 권한 변경이 그대로 실린다`() {
        val source = Source("a".repeat(40), "b".repeat(40), listOf(
            change(FileChangeType.RENAME, "old.txt", "new.txt", lines("같음"), lines("같음")),
            change(FileChangeType.MODIFY, "run.sh", "run.sh", lines("#!/bin/sh"), lines("#!/bin/sh"), oldMode = 0x81A4, newMode = 0x81ED)
        ))
        val diffs = service(source).commitDiff(project, "b")!!
        assertEquals(FileChangeType.RENAME, diffs[0].changeType); assertEquals("old.txt", diffs[0].pathA); assertEquals("new.txt", diffs[0].pathB)
        assertTrue(diffs[1].isFileModeChanged()); assertFalse(diffs[0].isFileModeChanged())
    }

    @Test
    fun `너무 큰 내용은 오류 표시만 남기고 계속 진행한다`() {
        val tooBig = FileChange(FileChangeType.MODIFY, "big.txt", "big.txt", 0x81A4, 0x81A4, { throw ContentTooLargeException() }, { lines("작음") })
        val source = Source("a".repeat(40), "b".repeat(40), listOf(tooBig, change(FileChangeType.ADD, null, "ok.txt", b = lines("ok"))))
        val diffs = service(source).commitDiff(project, "b")!!
        assertTrue(diffs[0].hasError(FileDiff.Error.A_SIZE_EXCEEDED)); assertNotNull(diffs[0].b)
        assertNull(diffs[0].hunks()); assertEquals("ok.txt", diffs[1].pathB)
    }

    @Test
    fun `풀 수 없는 리비전이면 null이다`() {
        assertNull(service(null).commitDiff(project, "없음"))
    }
}
