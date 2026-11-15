package dev.yona.audit

import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 환영 화면 다듬기(A-603, 선생님 지시)의 화면 구조를 지키는 구조 검사이다. 템플릿과 스타일을 직접 읽는다. */
class WelcomeScreenPolishTest {

    private fun text(path: String) = File(path).readText()

    @Test
    fun `비밀 안내 폼은 검색 상자용 secret-form 규칙을 쓰지 않는다`() {
        val d = Jsoup.parse(text("src/main/resources/templates/welcome/secret.html"))
        assertNull(d.selectFirst("form.secret-form"), "글자 크기 0인 검색 묶음 규칙이 오류 문구를 지웠다")
        assertNotNull(d.selectFirst("form.welcome-form"))
        assertFalse(text("app/assets/stylesheets/scss/_search-form.scss").contains(".secret-form"))
    }

    @Test
    fun `환영 화면 둘에는 Powered by 푸터가 없다`() {
        for (f in listOf("secret", "restart")) {
            val d = Jsoup.parse(text("src/main/resources/templates/welcome/$f.html"))
            assertNull(d.selectFirst("footer.page-footer-outer"), "$f: Powered by 푸터")
        }
    }

    @Test
    fun `재시작 안내 문구는 해야 합니다로 띄어 쓴다`() {
        assertTrue(text("src/main/resources/messages_ko_KR.properties").lines().any { it == "app.restart.notice = 서버를 재시작해야 합니다." })
        assertEquals(0, text("src/main/resources/messages_ko_KR.properties").lines().count { it.startsWith("app.restart.notice") && it.contains("해야합니다") })
    }
}
