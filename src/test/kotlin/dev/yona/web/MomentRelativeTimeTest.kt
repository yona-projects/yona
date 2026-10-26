package dev.yona.web

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * moment.js 2.4.0의 `fromNow` 상대 시각을 옮긴 [MomentRelativeTime]을 검증한다 (v1.6 `JodaDateUtil.momentFromNow`).
 *
 * 기대값 2,385줄(`moment/socialdate_golden.tsv`)은 v1.6이 쓰는 `moment-with-langs.min.js`(2.4.0)를 직접 실행해 얻었다. 5개 언어(en, ko, ja, ru, uz)의
 * 모든 전이점과 그 앞뒤 1ms를 담았다.
 */
class MomentRelativeTimeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `5개 언어의 경계값 2385개가 moment 2_4_0의 결과와 모두 같다`() {
        val lines = javaClass.getResourceAsStream("/moment/socialdate_golden.tsv")!!.bufferedReader().readLines().drop(1).filter { it.isNotBlank() }
        assertTrue(lines.size > 2000, "기대값 줄 수 ${lines.size}")
        val failures = lines.mapNotNull { line ->
            val (language, delta, expected) = line.split('\t')
            val actual = MomentRelativeTime.fromNow(now - delta.toLong(), now, language)
            if (actual == expected) null else "$language $delta 기대=$expected 실제=$actual"
        }
        assertTrue(failures.isEmpty(), "불일치 ${failures.size}건:\n" + failures.take(20).joinToString("\n"))
    }

    @Test
    fun `알 수 없는 언어는 영어이다`() {
        assertEquals("a minute ago", MomentRelativeTime.fromNow(now - 44_500, now, "xx"))
        assertEquals("a minute ago", MomentRelativeTime.fromNow(now - 44_500, now, "zh"))
    }

    @Test
    fun `같은 시각은 과거이고 미래는 후이다`() {
        assertEquals("몇초 전", MomentRelativeTime.fromNow(now, now, "ko"))
        assertEquals("일분 후", MomentRelativeTime.fromNow(now + 44_500, now, "ko"))
        assertEquals("in a minute", MomentRelativeTime.fromNow(now + 44_500, now, "en"))
    }
}
