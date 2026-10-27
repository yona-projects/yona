package dev.yona.web.layout

import dev.yona.web.RedirectSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.StaticMessageSource
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 일회성 메시지를 화면 문구로 바꾸는 규칙을 검증한다(v1.6 `common/scripts.scala.html`). */
class FlashMessageTranslatorTest {

    private val messages = StaticMessageSource().apply {
        setUseCodeAsDefaultMessage(false)
        addMessage("user.login.alert", Locale.ENGLISH, "Please log in.")
        addMessage("quote", Locale.ENGLISH, "Don't <stop>")
    }
    private val translator = FlashMessageTranslator(messages)
    private val saved = LocaleContextHolder.getLocale()

    @BeforeEach
    fun english() = LocaleContextHolder.setLocale(Locale.ENGLISH)

    @AfterEach
    fun restore() = LocaleContextHolder.setLocale(saved)

    @Test
    fun `알림은 순서 정보가 있으면 그 순서대로 보인다 (v1_6 쿠키는 넣은 순서를 지킨다)`() {
        val flash = hashMapOf<String, Any?>("warning" to "user.login.alert", "failed" to "failed text", RedirectSupport.ORDER to "failed\u0001warning")
        assertEquals(listOf("failed text", "Please log in."), translator.translate(flash).notices)
    }

    @Test
    fun `순서 정보는 알림에 나오지 않는다`() {
        val flash = mapOf<String, Any?>("warning" to "user.login.alert", RedirectSupport.ORDER to "warning")
        assertEquals(listOf("Please log in."), translator.translate(flash).notices)
        assertNull(translator.translate(flash).alertTitle)
    }

    @Test
    fun `작은따옴표는 Twirl처럼 x27로 이스케이프한다`() {
        assertEquals(listOf("Don&#x27;t &lt;stop&gt;"), translator.translate(mapOf("info" to "quote")).notices)
    }
}
