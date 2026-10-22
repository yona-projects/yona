package dev.yona.repository.diff

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.Side
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.eclipse.jgit.diff.DiffEntry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 커밋 사이의 파일별 차이와 헝크 계산을 검증한다.
 *
 * 원본: `GitRepository.getDiff`, `getFileDiffs`, `playRepository.FileDiff`, `Hunk`, `DiffLine`.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class DiffServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: DiffService
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var projects: ProjectRepository

    private var sequence = 0

    private fun project(): Project {
        val p = projects.save(Project(owner = "dso${++sequence}", name = "dsp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        repositories.createRepository(p)
        return p
    }

    private fun lines(vararg l: String) = l.joinToString("\n", postfix = "\n")

    private fun revisions(p: Project): List<String> = repositories.openRepository(p).use { r -> org.eclipse.jgit.api.Git(r).log().call().map { it.name }.reversed() }

    @Test
    fun `첫 커밋은 모든 파일이 추가이다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to lines("1", "2").toByteArray(), "dir/b.txt" to "b".toByteArray())))
        val diffs = service.commitDiff(p, "master")!!
        assertEquals(setOf("a.txt", "dir/b.txt"), diffs.mapNotNull { it.pathB }.toSet())
        assertTrue(diffs.all { it.changeType == DiffEntry.ChangeType.ADD && it.pathA == null && it.commitA == null })
        assertNull(diffs.first().hunks())
        assertNotNull(diffs.first { it.pathB == "a.txt" }.b)
    }

    @Test
    fun `수정한 파일은 바뀐 줄 앞뒤 문맥과 함께 헝크로 나온다`() {
        val p = project()
        val before = (1..20).map { "줄 $it" }.toTypedArray()
        val after = before.copyOf().also { it[9] = "줄 10 수정" }
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to lines(*before).toByteArray())), TestCommit("수정", mapOf("a.txt" to lines(*after).toByteArray())))
        val diff = service.commitDiff(p, "master")!!.single()
        assertEquals(DiffEntry.ChangeType.MODIFY, diff.changeType)
        val hunks = diff.hunks()!!
        assertEquals(1, hunks.size)
        val hunk = hunks.single()
        assertEquals(listOf(6, 13, 6, 13), listOf(hunk.beginA, hunk.endA, hunk.beginB, hunk.endB))
        assertEquals(listOf(DiffLineType.CONTEXT, DiffLineType.CONTEXT, DiffLineType.CONTEXT, DiffLineType.REMOVE, DiffLineType.ADD, DiffLineType.CONTEXT, DiffLineType.CONTEXT, DiffLineType.CONTEXT), hunk.lines.map { it.kind })
        val removed = hunk.lines.first { it.kind == DiffLineType.REMOVE }
        assertEquals(9, removed.numA); assertNull(removed.numB); assertTrue(removed.content.startsWith("줄 10"))
        val added = hunk.lines.first { it.kind == DiffLineType.ADD }
        assertNull(added.numA); assertEquals(9, added.numB); assertTrue(added.content.startsWith("줄 10 수정"))
        assertEquals(hunk.lines.sumOf { it.content.length }, hunk.size())
    }

    @Test
    fun `가까운 변경은 한 헝크로 합치고 먼 변경은 나눈다`() {
        val p = project()
        val before = (1..60).map { "줄 $it" }.toTypedArray()
        val after = before.copyOf().also { it[5] = "A"; it[8] = "B"; it[50] = "C" }
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to lines(*before).toByteArray())), TestCommit("수정", mapOf("a.txt" to lines(*after).toByteArray())))
        assertEquals(2, service.commitDiff(p, "master")!!.single().hunks()!!.size)
    }

    @Test
    fun `지우거나 이름을 바꾼 파일과 이진 파일을 구분한다`() {
        val p = project()
        val binary = byteArrayOf(0, 1, 2, 3, 0, 5)
        GitTestSupport.pushCommits(repositories, p,
            TestCommit("처음", mapOf("gone.txt" to "x".toByteArray(), "old.txt" to lines("가", "나", "다", "라").toByteArray(), "bin.dat" to binary)),
            TestCommit("정리", mapOf("new.txt" to lines("가", "나", "다", "라").toByteArray(), "bin.dat" to byteArrayOf(0, 9, 9, 0)), deleted = listOf("gone.txt", "old.txt")))
        val byPath = service.commitDiff(p, "master")!!.associateBy { it.pathB ?: it.pathA }
        assertEquals(DiffEntry.ChangeType.DELETE, byPath["gone.txt"]!!.changeType); assertNull(byPath["gone.txt"]!!.pathB)
        assertEquals(DiffEntry.ChangeType.RENAME, byPath["new.txt"]!!.changeType); assertEquals("old.txt", byPath["new.txt"]!!.pathA)
        val bin = byPath["bin.dat"]!!
        assertTrue(bin.isBinaryA && bin.isBinaryB); assertNull(bin.a); assertNull(bin.editList)
    }

    @Test
    fun `두 리비전을 비교한다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p,
            TestCommit("1", mapOf("a.txt" to lines("1").toByteArray())), TestCommit("2", mapOf("b.txt" to "b".toByteArray())), TestCommit("3", mapOf("a.txt" to lines("1", "2").toByteArray())))
        val revs = revisions(p)
        val diffs = service.diff(p, revs[0], revs[2])
        assertEquals(setOf("a.txt", "b.txt"), diffs.map { it.pathB ?: it.pathA }.toSet())
        assertEquals(revs[0], diffs.first().commitA); assertEquals(revs[2], diffs.first().commitB)
    }

    @Test
    fun `서로 다른 저장소의 커밋도 비교한다`() {
        val base = project(); val fork = project()
        GitTestSupport.pushCommits(repositories, base, TestCommit("공통", mapOf("a.txt" to lines("1").toByteArray())))
        GitTestSupport.pushCommits(repositories, fork, TestCommit("포크만", mapOf("only-in-fork.txt" to lines("x", "y").toByteArray())))
        val diffs = service.diff(base, "master", fork, "master")
        val added = diffs.first { it.pathB == "only-in-fork.txt" }
        assertEquals(DiffEntry.ChangeType.ADD, added.changeType); assertNotNull(added.b)
        assertEquals(DiffEntry.ChangeType.DELETE, diffs.first { it.pathA == "a.txt" }.changeType)
    }

    @Test
    fun `관심 줄을 정하면 그 줄이 든 헝크 하나만 만든다`() {
        val p = project()
        val before = (1..60).map { "줄 $it" }.toTypedArray()
        val after = before.copyOf().also { it[5] = "A"; it[50] = "C" }
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to lines(*before).toByteArray())), TestCommit("수정", mapOf("a.txt" to lines(*after).toByteArray())))
        val diff = service.commitDiff(p, "master")!!.single()
        diff.interestLine = 50; diff.interestSide = Side.B
        val hunks = diff.hunks()!!
        assertEquals(1, hunks.size)
        assertTrue(hunks.single().lines.any { it.content.startsWith("C") })
    }

    @Test
    fun `큰 파일은 내용을 싣지 않고 크기 초과로 표시한다`() {
        val p = project()
        val big = (1..FileDiff.LINE_LIMIT + 10).joinToString("\n", postfix = "\n") { "line $it" }
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("big.txt" to big.toByteArray())))
        val diff = service.commitDiff(p, "master")!!.single()
        assertTrue(diff.hasError(FileDiff.Error.B_SIZE_EXCEEDED))
        assertFalse(diff.hasError(FileDiff.Error.A_SIZE_EXCEEDED))
    }

    @Test
    fun `풀 수 없는 리비전은 null이다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to "a".toByteArray())))
        assertNull(service.commitDiff(p, "nobranch"))
    }
}
