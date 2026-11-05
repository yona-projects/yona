package dev.yona.repository.diff

import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.EditList
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * [FileDiff.updateRange]가 관심 있는 이전 쪽 줄과 이후 쪽 줄이 같은 바뀐 곳에 함께 맞아도 그 바뀐 곳을 한 번만 남김을 검증한다 (C150).
 * v1.6은 두 조건을 따로 검사해 같은 바뀐 곳을 두 번 더했다. 이 함수를 부르는 곳은 아직 없다.
 */
class FileDiffUpdateRangeTest {

    private fun diff(vararg edits: Edit) = FileDiff().apply { editList = EditList().also { list -> edits.forEach { list.add(it) } }; context = 3 }

    @Test
    fun `이전 쪽과 이후 쪽 줄이 같은 바뀐 곳에 맞아도 한 번만 남긴다`() {
        val fileDiff = diff(Edit(10, 12, 10, 13), Edit(100, 101, 120, 121))
        fileDiff.updateRange(11, 11)
        assertEquals(1, fileDiff.editList!!.size)
        assertEquals(Edit(10, 12, 10, 13), fileDiff.editList!![0])
    }

    @Test
    fun `한쪽만 맞으면 그 바뀐 곳을 남기고 둘 다 벗어나면 지운다`() {
        val fileDiff = diff(Edit(10, 12, 10, 13), Edit(100, 101, 120, 121))
        fileDiff.updateRange(null, 121)
        assertEquals(listOf(Edit(100, 101, 120, 121)), fileDiff.editList!!.toList())
        fileDiff.updateRange(null, 500)
        assertEquals(0, fileDiff.editList!!.size)
    }
}
