package dev.yona.web.access

import org.junit.jupiter.api.Test
import java.util.Date
import java.util.TimeZone
import kotlin.test.assertEquals

/** v1.6 `AccessLogger` 한 줄 형식을 검증한다. 기대값은 v1.6 본문을 `TZ=Asia/Seoul`로 실행해 얻었다. */
class AccessLogFormatTest {

    @Test
    fun `javadoc 예제와 같은 줄을 만든다`() {
        val old = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        try {
            assertEquals(
                "127.0.0.1 - eungjun [16/Oct/2013:18:31:31 +0900] \"GET /messages.js HTTP/1.1\" 200 - \"http://yobi:9000/\" \"Mozilla/5.0 (X11; Linux x86_64)\" 18ms",
                AccessLogFormat.format("127.0.0.1", "eungjun", Date(1381915891000), "GET", "/messages.js", "HTTP/1.1", 200, "http://yobi:9000/", "Mozilla/5.0 (X11; Linux x86_64)", 18))
            assertEquals(
                "10.0.0.5 - - [16/Oct/2013:18:31:31 +0900] \"GET /nope HTTP/1.1\" 404 - - \"\\\\uD55C\\\\uAE00\\\"UA\" -".replace("\\\\u", "\\u"),
                AccessLogFormat.format("10.0.0.5", "", Date(1381915891000), "GET", "/nope", "HTTP/1.1", 404, null, "한글\"UA", null).replace("\\uD55C\\uAE00", "\\uD55C\\uAE00"))
        } finally { TimeZone.setDefault(old) }
    }

    @Test
    fun `escapeJava는 lang3과 같다`() {
        assertEquals("a\\\"b\\\\c\\n\\t\\u0001\\uD55C/", AccessLogFormat.escapeJava("a\"b\\c\n\t\u0001한/"))
    }
}
