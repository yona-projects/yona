package dev.yona.markdown

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 참조 구현(GraalJS + marked.js)이 동작하는지 확인한다. 이 결과가 v1.6의 렌더링 기준이다. */
class MarkedReferenceTest {

    @Test
    fun `마크다운을 HTML로 바꾼다`() {
        val html = MarkedReference.render("# 제목\n\n**굵게** 와 *기울임*")
        assertTrue(html.contains("<h1"), html)
        assertTrue(html.contains("<strong>굵게</strong>"), html)
    }

    @Test
    fun `줄바꿈 옵션이 켜져 있어 한 줄 바꿈이 br이 된다`() {
        assertEquals("<p>a<br>b</p>\n", MarkedReference.render("a\nb"))
    }
}
