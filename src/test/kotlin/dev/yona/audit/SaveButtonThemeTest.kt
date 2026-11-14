package dev.yona.audit

import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * 저장 단추가 테마색(`ybtn-success`)인 것을 지키는 구조 검사이다. v1.6은 파란 `ybtn-info`였고 선생님 지시로 화면마다 테마색으로 바꾸고 있다
 * (이슈 수정 A-567, 게시판 글 수정 A-573, 마일스톤 만들기와 수정 A-576).
 */
class SaveButtonThemeTest {

    private fun doc(path: String) = Jsoup.parse(File("src/main/resources/templates/$path").readText())

    @Test
    fun `마일스톤 만들기와 수정의 저장 단추는 테마색이다(A-576)`() {
        for (f in listOf("milestone/create.html", "milestone/edit.html")) {
            val save = doc(f).select("button[type=submit]").first { it.text().isBlank() || it.hasAttr("th:text") }
            assertTrue(save.hasClass("ybtn-success") && !save.hasClass("ybtn-info"), "$f 저장 단추: ${save.className()}")
        }
    }
}
