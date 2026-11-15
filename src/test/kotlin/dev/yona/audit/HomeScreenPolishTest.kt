package dev.yona.audit

import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 첫 화면 알림 다듬기(A-601, 선생님 지시)의 화면 구조를 지키는 구조 검사이다. 템플릿 파일을 직접 읽는다. */
class HomeScreenPolishTest {

    private fun text(path: String) = File("src/main/resources/templates/$path").readText()

    @Test
    fun `안내 접기 단추는 무엇을 하는지 힌트와 상태를 알린다`() {
        val button = Jsoup.parse(text("index/notifications.html")).selectFirst("button#toggleIntro")
        assertNotNull(button)
        assertTrue(button.attr("th:title").contains("index.intro.toggle.show"), "마우스를 올리면 보이는 힌트")
        assertTrue(button.attr("th:aria-label").contains("index.intro.toggle.show"), "화면 읽기 프로그램용 이름")
        assertEquals("false", button.attr("aria-expanded"), "처음에는 접혀 있다(A-493)")
        assertTrue(button.attr("th:attr").contains("data-title-show") && button.attr("th:attr").contains("data-title-hide"), "펼침과 접힘 문구")
        assertTrue(text("index/notifications.html").contains("aria-expanded"), "누를 때와 처음 펼친 사용자에게 상태를 바꾼다")
    }
}
