package dev.yona.web.lfs

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import java.util.Locale
import kotlin.test.assertEquals

/** LFS 화면 문구(X-8)를 검증한다: 영어는 객체 수에 맞게 단수와 복수를 가르고, 한국어는 개수를 그대로 쓴다. */
class LfsMessagesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var messages: MessageSource

    private fun usage(count: Long, locale: Locale) = messages.getMessage("lfs.usage", arrayOf<Any>(count, "1.4 KB"), locale)

    @Test
    fun `영어 사용량은 객체 수에 따라 단수와 복수를 가른다`() {
        assertEquals("0 objects, 1.4 KB", usage(0, Locale.ENGLISH)); assertEquals("1 object, 1.4 KB", usage(1, Locale.ENGLISH)); assertEquals("2 objects, 1.4 KB", usage(2, Locale.ENGLISH))
        assertEquals("1,234 objects, 1.4 KB", usage(1234, Locale.ENGLISH))
    }

    @Test
    fun `한국어 사용량은 개수를 그대로 쓴다`() {
        assertEquals("객체 1개, 1.4 KB", usage(1, Locale.of("ko", "KR")))
    }
}
