package dev.yona.config

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.test.context.TestPropertySource
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 실제 애플리케이션의 `messageSource` 빈이 지원 언어 목록(`yona.langs`)으로 한정되는지 확인한다. */
@TestPropertySource(properties = ["yona.langs=ja-JP,en-US"])
class RestrictedMessageSourceWiringTest : AbstractIntegrationTest() {

    @Autowired private lateinit var messages: MessageSource

    @Test
    fun `목록에 없는 한국어는 영어 문구이고 목록의 일본어는 일본어 문구이다`() {
        assertTrue(messages is RestrictedMessageSource, messages.javaClass.name)
        assertEquals("Issue has been closed", messages.getMessage("notification.issue.closed", null, Locale.forLanguageTag("ko-KR")))
        assertEquals("イシューの状態がクローズになりました", messages.getMessage("notification.issue.closed", null, Locale.forLanguageTag("ja-JP")))
    }
}
