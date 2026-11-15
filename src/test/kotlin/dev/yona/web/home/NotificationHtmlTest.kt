package dev.yona.web.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 알림 본문의 줄바꿈 변환(A-602)을 검증한다. 일반 줄바꿈은 `<br/>`가 되고, 이미 `<br>`가 있는 곳은 줄을 더 바꾸지 않는다.
 */
class NotificationHtmlTest {

    @Test
    fun `일반 줄바꿈은 br로 바뀐다`() {
        assertEquals("a<br/>\nb", NotificationHtml.fromMessage("a\nb"))
    }

    @Test
    fun `이미 br이 있는 곳의 줄바꿈은 더하지 않는다`() {
        val html = NotificationHtml.fromMessage("확인했습니다.\n\n<br />\n\n--- Original issue from @u  at 8:57 AM ---\n\n<br />\n\n본문")
        assertEquals(2, Regex("<br").findAll(html).count(), html)
        assertFalse(html.contains("<br/><br/>"), html)
        assertEquals("확인했습니다.<br/>--- Original issue from @u  at 8:57 AM ---<br/>본문", html.replace("\n", ""))
    }

    @Test
    fun `br 태그 모양과 대소문자가 달라도 한 번만 줄을 바꾼다`() {
        for (tag in listOf("<br>", "<br/>", "<BR />")) {
            val html = NotificationHtml.fromMessage("a\n$tag\nb")
            assertEquals(1, Regex("(?i)<br").findAll(html).count(), "$tag -> $html")
        }
    }
}
