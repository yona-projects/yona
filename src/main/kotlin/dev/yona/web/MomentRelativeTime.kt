package dev.yona.web

import kotlin.math.abs
import kotlin.math.floor

/**
 * moment.js 2.4.0 의 `fromNow()` 상대 시각을 그대로 옮긴 것이다 (v1.6 `JodaDateUtil.momentFromNow`).
 * 지원 언어는 v1.6 메시지 번들과 같은 en, ko, ja, ru, uz 이며 그 밖의 언어는 moment 의 기본값인 en 으로 처리한다.
 */
object MomentRelativeTime {

    /** JS `Math.round`: 반올림하되 .5 는 양의 무한대 쪽(양수에서는 올림)이다. 입력은 항상 0 이상이다. */
    private fun round(x: Double): Long = floor(x + 0.5).toLong()

    /** 상대 시각 토큰. [count]는 `%d`에 들어가는 수이다. */
    private class Unit(val key: String, val count: Long)

    /** moment 2.4.0 `relativeTime` 의 임계값 사다리 (임계값은 반올림된 값 기준). */
    private fun classify(absMillis: Long): Unit {
        val seconds = round(absMillis / 1000.0)
        val minutes = round(seconds / 60.0)
        val hours = round(minutes / 60.0)
        val days = round(hours / 24.0)
        val years = round(days / 365.0)
        return when {
            seconds < 45 -> Unit("s", seconds)
            minutes == 1L -> Unit("m", 1)
            minutes < 45 -> Unit("mm", minutes)
            hours == 1L -> Unit("h", 1)
            hours < 22 -> Unit("hh", hours)
            days == 1L -> Unit("d", 1)
            days <= 25 -> Unit("dd", days)
            days <= 45 -> Unit("M", 1)
            days < 345 -> Unit("MM", round(days / 30.0))
            years == 1L -> Unit("y", 1)
            else -> Unit("yy", years)
        }
    }

    private class Lang(
        val future: String,
        val past: String,
        val table: Map<String, String>,
        val plural: Boolean = false,
    )

    private val EN = Lang("in %s", "%s ago", mapOf(
        "s" to "a few seconds", "m" to "a minute", "mm" to "%d minutes", "h" to "an hour", "hh" to "%d hours",
        "d" to "a day", "dd" to "%d days", "M" to "a month", "MM" to "%d months", "y" to "a year", "yy" to "%d years"))
    private val KO = Lang("%s 후", "%s 전", mapOf(
        "s" to "몇초", "m" to "일분", "mm" to "%d분", "h" to "한시간", "hh" to "%d시간",
        "d" to "하루", "dd" to "%d일", "M" to "한달", "MM" to "%d달", "y" to "일년", "yy" to "%d년"))
    private val JA = Lang("%s後", "%s前", mapOf(
        "s" to "数秒", "m" to "1分", "mm" to "%d分", "h" to "1時間", "hh" to "%d時間",
        "d" to "1日", "dd" to "%d日", "M" to "1ヶ月", "MM" to "%dヶ月", "y" to "1年", "yy" to "%d年"))
    private val UZ = Lang("Якин %s ичида", "Бир неча %s олдин", mapOf(
        "s" to "фурсат", "m" to "бир дакика", "mm" to "%d дакика", "h" to "бир соат", "hh" to "%d соат",
        "d" to "бир кун", "dd" to "%d кун", "M" to "бир ой", "MM" to "%d ой", "y" to "бир йил", "yy" to "%d йил"))
    private val RU = Lang("через %s", "%s назад", mapOf(
        "s" to "несколько секунд", "m" to "минуту", "h" to "час", "d" to "день", "M" to "месяц", "y" to "год"), plural = true)

    private val RU_FORMS = mapOf(
        "mm" to "минута_минуты_минут", "hh" to "час_часа_часов", "dd" to "день_дня_дней",
        "MM" to "месяц_месяца_месяцев", "yy" to "год_года_лет")

    private fun lang(language: String): Lang = when (language) {
        "ko" -> KO; "ja" -> JA; "ru" -> RU; "uz" -> UZ; else -> EN
    }

    /** ru 복수형 (moment 2.4.0 ru 의 `plural`). */
    private fun ruPlural(forms: String, n: Long): String {
        val f = forms.split("_")
        return if (n % 10 == 1L && n % 100 != 11L) f[0]
        else if (n % 10 in 2..4 && (n % 100 < 10 || n % 100 >= 20)) f[1] else f[2]
    }

    /**
     * `moment(epoch).lang(language).fromNow()`. [dateMillis]가 [nowMillis]와 같거나 과거이면 "전/ago", 미래이면 "후/in"이다.
     */
    fun fromNow(dateMillis: Long, nowMillis: Long, language: String): String {
        val diff = dateMillis - nowMillis
        val l = lang(language)
        val u = classify(abs(diff))
        val text = if (l.plural && u.key in RU_FORMS) "${u.count} ${ruPlural(RU_FORMS.getValue(u.key), u.count)}"
        else l.table.getValue(u.key).replace("%d", u.count.toString())
        return (if (diff > 0) l.future else l.past).replace("%s", text)
    }
}
