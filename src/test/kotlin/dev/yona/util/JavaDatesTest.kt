package dev.yona.util

import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `Date.toString()` 모양의 글자를 만드는 규칙을 검증한다. */
class JavaDatesTest {

    @Test
    fun `Timestamp도 Date와 같은 모양으로 바꾼다`() {
        val millis = 1_700_000_123_456L
        assertEquals(Date(millis).toString(), JavaDates.toString(Timestamp(millis)))
        assertEquals(Date(millis).toString(), JavaDates.toString(Date(millis)))
    }

    @Test
    fun `값이 없으면 null이다`() {
        assertNull(JavaDates.toString(null))
    }
}
