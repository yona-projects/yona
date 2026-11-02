package dev.yona.web.api

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Date

/**
 * 외부 REST가 기간 조건(`createdSince`, `since`, `until` 등, Magpie 연동 X-23)을 읽는 방법이다. 시간대가 없는 값은 UTC로 본다(서버의 시간대에 따라 결과가 달라지지 않게 한다).
 * `2026-02-01`은 UTC 0시이고, `2026-02-01T09:00:00`은 UTC, `2026-02-01T09:00:00Z`와 `2026-02-01T18:00:00+09:00`은 그대로 받는다. 읽을 수 없으면 `null`이다.
 */
object ApiInstants {

    fun parse(text: String?): Date? {
        val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            when {
                value.length <= 10 -> Date.from(LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant())
                value.endsWith("Z", ignoreCase = true) || value.contains(Regex("[+-]\\d{2}:?\\d{2}$")) -> Date.from(OffsetDateTime.parse(value).toInstant())
                else -> Date.from(LocalDateTime.parse(value).toInstant(ZoneOffset.UTC))
            }
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** 응답에 쓰는 표기이다(`2026-02-01T09:00:00Z`, 초 단위). */
    fun format(date: Date?): String? = date?.toInstant()?.let { Instant.ofEpochSecond(it.epochSecond).toString() }
}
