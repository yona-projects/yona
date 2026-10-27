package dev.yona.util

import java.util.Date

/**
 * `java.util.Date.toString()`과 같은 글자를 만드는 도우미이다.
 *
 * v1.6은 JSON이나 화면에 날짜를 `issue.createdDate.toString()`으로 찍는다(`Tue Oct 27 21:56:02 KST 2026`). Hibernate는 날짜 열을
 * `java.sql.Timestamp`로 돌려주고 그 `toString()`은 `2026-10-27 21:56:02.553` 모양이라 그대로 쓰면 글자가 달라진다.
 */
object JavaDates {

    /** [date]를 `Date.toString()` 모양의 글자로 바꾼다. 값이 없으면 `null`이다. */
    fun toString(date: Date?): String? = date?.let { Date(it.time).toString() }
}
