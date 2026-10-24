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

    @Test
    fun `제목 앞의 대괄호 머리말을 링크로 만들고 본문에서는 뺀다`() {
        assertEquals(
            "<a href='javascript:void(0)' class='title-prefix'>[버그]</a><a href='javascript:void(0)' class='title-prefix'>[긴급]</a>",
            helper.headerWordsHtml("[버그][긴급] 로그인 오류")
        )
        assertEquals(" 로그인 오류", helper.removeHeaderWords("[버그][긴급] 로그인 오류"))
        assertEquals("", helper.headerWordsHtml("대괄호 없음"))
        assertEquals("대괄호 없음", helper.removeHeaderWords("대괄호 없음"))
    }

    @Test
    fun `제목이 머리말뿐이면 머리말을 링크로 만들지 않고 제목을 그대로 둔다`() {
        assertEquals("", helper.headerWordsHtml("[버그]"))
        assertEquals("[버그]", helper.removeHeaderWords("[버그]"))
    }

    @Test
    fun `머리말의 HTML은 이스케이프한다`() {
        assertEquals("<a href='javascript:void(0)' class='title-prefix'>[&lt;b&gt;]</a>", helper.headerWordsHtml("[<b>] 제목"))
    }

    @Test
    fun `비율은 정수로 내리고 전체가 0이면 0이다`() {
        assertEquals(33, helper.percent(1, 3))
        assertEquals(100, helper.percent(3, 3))
        assertEquals(0, helper.percent(0, 0))
    }

    @Test
    fun `지정한 형식으로 날짜를 문자열로 바꾼다`() {
        assertEquals("2026-10-22 6:00 PM", helper.dateString(Date.from(now), "yyyy-MM-dd h:mm a", Locale.ENGLISH))
        assertEquals("", helper.dateString(null, "yyyy-MM-dd h:mm a", Locale.ENGLISH))
    }

    @Test
    fun `바이트 수를 1000 단위로 읽기 쉽게 바꾼다`() {
        assertEquals("999 B", helper.humanReadableByteCount(999, true))
        assertEquals("1.5 kB", helper.humanReadableByteCount(1500, true))
        assertEquals("2.0 MB", helper.humanReadableByteCount(2_000_000, true))
    }

    @Test
    fun `바이트 수를 1024 단위로 읽기 쉽게 바꾼다`() {
        assertEquals("1023 B", helper.humanReadableByteCount(1023, false))
        assertEquals("1.5 KiB", helper.humanReadableByteCount(1536, false))
    }

    @Test
    fun `하루 안의 시각은 moment 방식의 영어 상대 시간이다`() {
        fun social(seconds: Long) = helper.socialDate(ago(seconds), Locale.ENGLISH)
        assertEquals("a few seconds ago", social(10))
        assertEquals("a minute ago", social(60))
        assertEquals("5 minutes ago", social(5 * 60))
        assertEquals("an hour ago", social(60 * 60))
        assertEquals("3 hours ago", social(3 * 3600))
        assertEquals("a day ago", social(23 * 3600))
    }

    @Test
    fun `하루 안의 시각은 한국어 상대 시간으로도 나온다`() {
        assertEquals("몇 초 전", helper.socialDate(ago(10), Locale.KOREAN))
        assertEquals("5분 전", helper.socialDate(ago(5 * 60), Locale.KOREAN))
        assertEquals("3시간 전", helper.socialDate(ago(3 * 3600), Locale.KOREAN))
    }

    @Test
    fun `하루보다 오래된 시각은 날짜와 시각이다`() {
        assertEquals("2026-10-20 6:00 PM", helper.socialDate(Date.from(now.minusSeconds(2 * 86_400)), Locale.ENGLISH))
        assertEquals("", helper.socialDate(null, Locale.ENGLISH))
    }
}
