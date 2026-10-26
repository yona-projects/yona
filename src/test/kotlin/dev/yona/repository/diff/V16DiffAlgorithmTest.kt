package dev.yona.repository.diff

import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.HistogramDiff
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * JGit 4.5.0(v1.6)의 히스토그램 diff 결과를 재현하는지 검증한다. 기대값은 4.5.0에서 실제로 얻은 값이고,
 * JGit 7.8 단독으로는 마지막이 아닌 편집도 뒤로 밀려(`normalize`) 위치가 다르다.
 */
class V16DiffAlgorithmTest {

    private fun text(lines: String) = RawText(lines.split(" ").joinToString("") { "$it\n" }.toByteArray())

    private fun edits(algorithm: DiffAlgorithm, a: String, b: String): String =
        algorithm.diff(RawTextComparator.DEFAULT, text(a), text(b)).joinToString(" ") { "${it.type}(${it.beginA},${it.endA},${it.beginB},${it.endB})" }

    private val v16 = V16DiffAlgorithm(HistogramDiff())

    @Test
    fun `4_5와 같은 위치를 낸다`() {
        assertEquals("INSERT(0,0,0,1) DELETE(1,2,2,2)", edits(v16, "a b b", "c a b"))
        assertEquals("DELETE(0,1,0,0) DELETE(2,3,1,1) DELETE(5,6,3,3)", edits(v16, "a b a a c b", "b a c"))
        assertEquals("DELETE(0,1,0,0) INSERT(2,2,1,2) DELETE(4,5,4,4)", edits(v16, "a b a c b", "b a a c"))
        assertEquals("DELETE(0,1,0,0) DELETE(2,3,1,1)", edits(v16, "a b a a", "b a"))
    }

    @Test
    fun `7_8 단독은 위치가 다르다 (래퍼가 필요한 이유)`() {
        assertEquals("INSERT(0,0,0,1) DELETE(2,3,3,3)", edits(HistogramDiff(), "a b b", "c a b"))
    }

    @Test
    fun `같거나 한 줄 차이인 입력은 기본 처리와 같다`() {
        assertEquals("", edits(v16, "a b", "a b"))
        assertEquals("REPLACE(1,2,1,2)", edits(v16, "a b c", "a x c"))
        assertEquals(Edit.Type.INSERT, v16.diff(RawTextComparator.DEFAULT, text("a"), text("a b"))[0].type)
    }
}
