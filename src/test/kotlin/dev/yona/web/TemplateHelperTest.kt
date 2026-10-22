package dev.yona.web

import org.junit.jupiter.api.Test
import org.springframework.context.support.StaticMessageSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.test.assertEquals

/**
 * 템플릿 도우미(`TemplateHelper`)의 시간 표시 규칙을 검증한다.
 *
 * 원본: `app/utils/TemplateHelper.scala:98-115`(`agoString`, `agoOrDateString`, `plural`),
 * `app/utils/JodaDateUtil.java`(`getDateString`).
 */
class TemplateHelperTest {

    private val now = Instant.parse("2026-10-22T09:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")
    private val clock = Clock.fixed(now, zone)

    private val messages = StaticMessageSource().apply {
        setUseCodeAsDefaultMessage(false)
        listOf(
            "common.time.day" to "{0} days ago", "common.time.days" to "{0} days ago",
            "common.time.hour" to "{0} hours ago", "common.time.hours" to "{0} hours ago",
            "common.time.minute" to "{0} minute ago", "common.time.minutes" to "{0} minutes ago",
            "common.time.second" to "{0} second ago", "common.time.seconds" to "{0} seconds ago",
            "common.time.just" to "Just now"
        ).forEach { (code, text) -> addMessage(code, Locale.ENGLISH, text) }
    }

    private val helper = TemplateHelper(messages, clock)

    private fun ago(seconds: Long) = Date.from(now.minusSeconds(seconds))

    @Test
    fun `1초 미만은 방금 전이다`() {
        assertEquals("Just now", helper.agoString(Date.from(now), Locale.ENGLISH))
    }

    @Test
    fun `초 단위는 단수와 복수를 가른다`() {
        assertEquals("1 second ago", helper.agoString(ago(1), Locale.ENGLISH))
        assertEquals("59 seconds ago", helper.agoString(ago(59), Locale.ENGLISH))
    }

    @Test
    fun `분 단위`() {
        assertEquals("1 minute ago", helper.agoString(ago(60), Locale.ENGLISH))
        assertEquals("2 minutes ago", helper.agoString(ago(150), Locale.ENGLISH))
    }

    @Test
    fun `시간 단위`() {
        assertEquals("1 hours ago", helper.agoString(ago(3600), Locale.ENGLISH))
        assertEquals("5 hours ago", helper.agoString(ago(5 * 3600), Locale.ENGLISH))
    }

    @Test
    fun `일 단위`() {
        assertEquals("1 days ago", helper.agoString(ago(86400), Locale.ENGLISH))
        assertEquals("7 days ago", helper.agoString(ago(7 * 86400 + 100), Locale.ENGLISH))
    }

    @Test
    fun `8일 미만이면 상대 시간을 쓴다`() {
        assertEquals("7 days ago", helper.agoOrDateString(ago(7 * 86400 + 100), Locale.ENGLISH))
    }

    @Test
    fun `8일이 지나면 같은 해에는 월-일을 쓴다`() {
        // 2026-10-22 18:00(서울) 기준 8일 전: 2026-10-14
        assertEquals("10-14", helper.agoOrDateString(ago(8 * 86400 + 100), Locale.ENGLISH))
    }

    @Test
    fun `해가 다르면 연-월-일을 쓴다`() {
        val lastYear = Date.from(Instant.parse("2025-03-05T00:00:00Z"))
        assertEquals("2025-03-05", helper.agoOrDateString(lastYear, Locale.ENGLISH))
    }

    @Test
    fun `날짜 문자열의 기본 형식은 연-월-일 시 분 초 오전오후이다`() {
        val formatted = helper.dateString(Date.from(Instant.parse("2026-10-22T09:05:07Z")), Locale.ENGLISH)
        assertEquals("2026-10-22 6:05:07 PM", formatted)
    }

    @Test
    fun `날짜가 없으면 빈 문자열이다`() {
        assertEquals("", helper.dateString(null, Locale.ENGLISH))
        assertEquals("", helper.agoString(null, Locale.ENGLISH))
    }

    @Test
    fun `개수가 1이 아니면 키에 s를 붙인다`() {
        assertEquals("1 minute ago", helper.plural("common.time.minute", 1, Locale.ENGLISH))
        assertEquals("2 minutes ago", helper.plural("common.time.minute", 2, Locale.ENGLISH))
        assertEquals("0 minutes ago", helper.plural("common.time.minute", 0, Locale.ENGLISH))
    }
}
