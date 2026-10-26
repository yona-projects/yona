package dev.yona.web

import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * 템플릿이 쓰는 표시 도우미이다. v1.6 `utils.TemplateHelper`(Scala)와 `JodaDateUtil.getDateString`의 일부이다.
 * 템플릿에서는 모델 이름 `helper`로 접근한다.
 *
 * 메시지 언어를 받지 않는 오버로드는 현재 요청의 언어를 쓴다.
 *
 * @param clock 현재 시각과 시간대. 테스트에서는 고정한 시계를 넣는다
 */
@Component
class TemplateHelper(private val messages: MessageSource, private val clock: Clock) {

    private val zone: ZoneId get() = clock.zone

    // --- 시간 ---

    /** 기본 형식 `yyyy-MM-dd h:mm:ss a`로 날짜를 문자열로 바꾼다. 날짜가 없으면 빈 문자열이다 (v1.6 `JodaDateUtil.getDateString`). */
    fun dateString(date: Date?): String = dateString(date, Locale.getDefault(Locale.Category.FORMAT))

    fun dateString(date: Date?, locale: Locale): String = format(date, "yyyy-MM-dd h:mm:ss a", locale)

    /** 지정한 형식으로 날짜를 문자열로 바꾼다. 날짜가 없으면 빈 문자열이다 (v1.6 `JodaDateUtil.getDateString(date, format)`). */
    @JvmOverloads
    fun dateString(date: Date?, pattern: String, locale: Locale = Locale.getDefault(Locale.Category.FORMAT)): String = format(date, pattern, locale)

    /**
     * 지나간 시간을 사람이 읽는 문구로 바꾼다 (v1.6 `agoString`). 하루 이상은 일, 한 시간 이상은 시간, 1분 이상은 분,
     * 1초 이상은 초 단위이고 그보다 짧으면 "방금"이다. 날짜가 없으면 빈 문자열이다.
     */
    fun agoString(date: Date?): String = agoString(date, LocaleContextHolder.getLocale())

    fun agoString(date: Date?, locale: Locale): String {
        if (date == null) return ""
        val ago = Duration.between(date.toInstant(), clock.instant())
        return when {
            ago.seconds >= 86_400 -> plural("common.time.day", ago.toDays(), locale)
            ago.seconds >= 3_600 -> plural("common.time.hour", ago.toHours(), locale)
            ago.seconds >= 60 -> plural("common.time.minute", ago.toMinutes(), locale)
            ago.seconds > 0 -> plural("common.time.second", ago.seconds, locale)
            else -> message("common.time.just", locale)
        }
    }

    /**
     * 8일이 지나지 않았으면 [agoString], 같은 해이면 `MM-dd`, 해가 다르면 `yyyy-MM-dd`로 보여 준다
     * (v1.6 `agoOrDateString`).
     */
    fun agoOrDateString(date: Date?): String = agoOrDateString(date, LocaleContextHolder.getLocale())

    fun agoOrDateString(date: Date?, locale: Locale): String {
        if (date == null) return ""
        val ago = Duration.between(date.toInstant(), clock.instant())
        return when {
            ago.toDays() < 8 -> agoString(date, locale)
            format(date, "yyyy", locale) == format(Date.from(clock.instant()), "yyyy", locale) -> format(date, "MM-dd", locale)
            else -> format(date, "yyyy-MM-dd", locale)
        }
    }

    // --- 메시지 ---

    /** 개수가 1이 아니면 키에 `s`를 붙여 복수형 메시지를 고른다 (v1.6 `plural`). 개수는 문자열 인자로 넘긴다. */
    fun plural(key: String, count: Number, locale: Locale = LocaleContextHolder.getLocale()): String {
        val pluralKey = if (count.toLong() != 1L) key + "s" else key
        return messages.getMessage(pluralKey, arrayOf(count.toString()), pluralKey, locale) ?: pluralKey
    }

    /**
     * 하루 안의 시각은 moment.js 2.4.0의 `fromNow`와 같은 상대 시간으로, 그보다 오래되었으면 `yyyy-MM-dd h:mm a`로 쓴다
     * (v1.6 `JodaDateUtil.socialDate`). 날짜가 없으면 빈 문자열이다.
     *
     * 하루의 기준은 Joda의 `DateTime.now().minusDays(1)`보다 엄격히 이전인지이다. 상대 시간은 [MomentRelativeTime]이 만들며 en, ko, ja, ru, uz의 문구와
     * 반올림 경계를 따른다. 언어는 요청 언어가 아니라 서버 JVM 기본 로캘의 언어이다(v1.6 `Locale.getDefault().getLanguage()`).
     */
    @JvmOverloads
    fun socialDate(date: Date?, locale: Locale = Locale.getDefault(Locale.Category.FORMAT)): String {
        if (date == null) return ""
        val now = clock.instant()
        val limit = now.atZone(clock.zone).minusDays(1).toInstant()
        if (date.toInstant().isBefore(limit)) return format(date, "yyyy-MM-dd h:mm a", locale)
        return MomentRelativeTime.fromNow(date.time, now.toEpochMilli(), locale.language)
    }

    // --- 크기 ---

    /**
     * 바이트 수를 읽기 쉽게 바꾼다 (v1.6 `humanize.Humanize.binaryPrefix`). 1024 미만은 `N bytes`, 그 이상은 소수점 한 자리의 `KB`, `MB`, `GB` 순이다.
     */
    fun binaryPrefix(size: Number): String {
        val value = size.toDouble()
        if (value < BINARY_BASE) return "${size.toLong()} bytes"
        val exponent = minOf((Math.log(value) / Math.log(BINARY_BASE)).toInt(), BINARY_UNITS.length)
        return String.format(Locale.ROOT, "%.1f %sB", value / Math.pow(BINARY_BASE, exponent.toDouble()), BINARY_UNITS[exponent - 1])
    }

    /**
     * 바이트 수를 읽기 쉽게 바꾼다 (v1.6 `FileUtil.humanReadableByteCount`). [si]이면 1000 단위(`kB`, `MB`), 아니면 1024 단위(`KiB`, `MiB`)이다.
     * 단위보다 작으면 `N B`이다.
     */
    fun humanReadableByteCount(bytes: Long, si: Boolean): String {
        val unit = if (si) 1000 else 1024
        if (bytes < unit) return "$bytes B"
        val exponent = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val prefix = (if (si) "kMGTPE" else "KMGTPE")[exponent - 1] + (if (si) "" else "i")
        return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(unit.toDouble(), exponent.toDouble()), prefix)
    }

    // --- 제목 ---

    /**
     * 제목 맨 앞의 대괄호 머리말(`[버그][긴급] 제목`)을 클릭할 수 있는 링크 HTML로 만든다 (v1.6 `showHeaderWordsInBracketsIfExist`).
     * 제목이 머리말뿐이면 빈 문자열이다. v1.6은 머리말을 이스케이프하지 않았지만 v2는 이스케이프한다([decisions.md] A-38).
     */
    fun headerWordsHtml(title: String?): String {
        val text = title.orEmpty()
        if (madeByHeaderWordsOnly(text)) return ""
        return headerWords(text).joinToString("") {
            "<a href='javascript:void(0)' class='title-prefix'>" + org.springframework.web.util.HtmlUtils.htmlEscape(it.trim()) + "</a>"
        }
    }

    /** 제목에서 앞의 대괄호 머리말을 뺀다. 제목이 머리말뿐이면 그대로이다 (v1.6 `removeHeaderWords`). */
    fun removeHeaderWords(title: String?): String {
        val text = title.orEmpty()
        return if (madeByHeaderWordsOnly(text)) text else text.replace(headerWords(text).joinToString(""), "")
    }

    /** 제목을 `=[`, `]` 뒤에서 잘라 `[`로 시작하고 `]`를 가진 조각만 고른다. */
    private fun headerWords(title: String): List<String> =
        dev.yona.domain.issue.TitleHeadWords.extract(title).filter { it.trim().indexOf("[") == 0 && it.contains("]") }

    private fun madeByHeaderWordsOnly(title: String): Boolean =
        title.trim().indexOf("]") + 1 == title.trim().length || title.replace(headerWords(title).joinToString(""), "").trim().isEmpty()

    /** [unit]이 [total]의 몇 퍼센트인지 정수로 돌려준다. [total]이 0이면 0이다 (v1.6 `getPercent`). */
    fun percent(unit: Number, total: Number): Int {
        val ratio = unit.toDouble() / total.toDouble() * 100
        return if (ratio.isNaN()) 0 else ratio.toInt()
    }

    private companion object {
        const val BINARY_BASE = 1024.0
        const val BINARY_UNITS = "KMGTPE"
    }

    private fun message(key: String, locale: Locale): String = messages.getMessage(key, null, key, locale) ?: key

    private fun format(date: Date?, pattern: String, locale: Locale): String {
        if (date == null) return ""
        return SimpleDateFormat(pattern, locale).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(date)
    }
}
