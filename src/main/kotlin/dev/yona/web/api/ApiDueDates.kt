package dev.yona.web.api

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.Date

/** 외부 REST가 마일스톤의 마감일(`due_on`)을 읽는 방법이다. 만들기와 고치기가 같은 규칙을 쓴다. */
object ApiDueDates {

    /**
     * `due_on`을 읽는다 (v1.6 `MilestoneApi`: Joda `new DateTime(text)`). 그날의 마지막 초로 맞추는 일은 `MilestoneService`가 한다.
     * `2030-01-02`(시스템 시간대의 자정), `2030-01-02T12:00:00`(시스템 시간대), `2030-01-02T12:00:00Z`, `2030-01-02T12:00:00+09:00`을 받는다.
     * 읽을 수 없으면 `null`이다. v1.6은 이 경우 `IllegalArgumentException`으로 `500`이 나므로 오류 없이 처리하는 쪽을 유지한다.
     */
    fun parse(text: String): Date? {
        val zone = ZoneId.systemDefault()
        return try {
            if (text.length <= 10) Date.from(LocalDate.parse(text).atStartOfDay(zone).toInstant())
            else try { Date.from(OffsetDateTime.parse(text).toInstant()) } catch (e: DateTimeParseException) { Date.from(LocalDateTime.parse(text).atZone(zone).toInstant()) }
        } catch (e: DateTimeParseException) { null }
    }
}
