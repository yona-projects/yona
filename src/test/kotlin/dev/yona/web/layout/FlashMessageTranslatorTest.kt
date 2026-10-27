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
    fun `알림 순서는 넣은 순서가 아니라 HashMap 키 순서이다 (v1_6 Http Flash는 HashMap이다)`() {
        val flash = linkedMapOf<String, Any?>("failed" to "failed text", "info" to "info text", "warning" to "user.login.alert", "success" to "ok text")
        assertEquals(listOf("ok text", "Please log in.", "failed text", "info text"), translator.translate(flash).notices)
    }

    @Test
    fun `제목이 없으면 확인 대화상자도 없다`() {
        val flash = mapOf<String, Any?>("warning" to "user.login.alert")
        assertEquals(listOf("Please log in."), translator.translate(flash).notices)
        assertNull(translator.translate(flash).alertTitle)
    }

    @Test
    fun `작은따옴표는 Twirl처럼 x27로 이스케이프한다`() {
        assertEquals(listOf("Don&#x27;t &lt;stop&gt;"), translator.translate(mapOf("info" to "quote")).notices)
    }

    @Test
    fun `키가 아닌 글은 그대로 보인다 (중괄호가 있어도 오류가 나지 않는다)`() {
        assertEquals(listOf("{title=third} 입니다"), translator.translate(mapOf("warning" to "{title=third} 입니다")).notices)
    }
}
