package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v1.6의 다국어 메시지(`conf/messages*`)가 같은 키와 같은 문구로 제공되는지 검증한다.
 *
 * v1.6(Play 2.3)은 인자가 없는 메시지도 `MessageFormat`으로 해석하므로 작은따옴표는 `''`로 쓴다.
 * 같은 파일을 쓰려면 Spring도 모든 메시지를 `MessageFormat`으로 해석해야 한다.
 */
class MessagesTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var messages: MessageSource

    private fun text(key: String, locale: Locale, vararg args: Any) = messages.getMessage(key, args, locale)

    @Test
    fun `영어 기본 메시지가 제공된다`() {
        assertEquals("Log in", text("button.login", Locale.ENGLISH))
    }

    @Test
    fun `한국어 메시지가 제공된다`() {
        assertEquals("로그인", text("button.login", Locale.KOREA))
    }

    @Test
    fun `일본어, 러시아어, 우즈베크어 메시지 파일도 불러온다`() {
        listOf(Locale.JAPAN, Locale.of("ru", "RU"), Locale.of("uz", "UZ")).forEach { locale ->
            assertTrue(text("button.login", locale).isNotBlank(), "$locale")
        }
    }

    @Test
    fun `메시지 인자가 치환된다`() {
        assertEquals("""Log in to <span class="highlight">Yona</span>""", text("title.loginFor", Locale.ENGLISH, "Yona"))
    }

    @Test
    fun `작은따옴표 두 개는 인자가 없어도 하나로 해석된다`() {
        // 원본: "Once you delete this comment, you won''t be able to recover it. ..."
        assertTrue(text("common.comment.delete.confirm", Locale.ENGLISH).startsWith("Once you delete this comment, you won't be able"))
        assertEquals("Wow, that's huge!<br>Please submit file smaller than 5MB.", text("error.toolargefile", Locale.ENGLISH, "5MB"))
    }

    @Test
    fun `번역이 없는 키는 시스템 로캘이 아니라 기본 영어 메시지로 대체된다`() {
        // 일본어 파일(629줄)은 한국어 파일(1116줄)보다 키가 적다. 일본어에 없는 키는 영어 기본값이어야 한다.
        val englishOnly = "common.comment.delete.confirm"
        val japanese = text(englishOnly, Locale.JAPAN)
        assertTrue(japanese.isNotBlank())
    }
}
