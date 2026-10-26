package dev.yona.web.access

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.6 `utils.AccessLogger`가 한 줄로 만드는 접근 로그 형식이다 (Apache Combined Log Format 변형).
 *
 * `원격주소 - 사용자 [dd/MMM/yyyy:HH:mm:ss Z] "메서드 URI 프로토콜" 상태 - "Referer" "User-Agent" 소요시간`
 */
object AccessLogFormat {

    /** 값이 `null`이거나 비어 있으면 `-`이다 (v1.6 `orHyphen`). */
    fun orHyphen(value: String?): String = if (value.isNullOrEmpty()) "-" else value

    /** 값을 큰따옴표로 감싸고 [escapeJava]를 적용한다. `null`이면 `-`이다(빈 문자열은 `""`) (v1.6 `quotedOrHyphen`). */
    fun quotedOrHyphen(value: String?): String = if (value == null) "-" else "\"" + escapeJava(value) + "\""

    /**
     * commons-lang3 `StringEscapeUtils.escapeJava`와 같다. `"`, `\`, 제어 문자(`\b \n \t \f \r`)는 이스케이프 시퀀스로,
     * 32 미만이거나 127을 넘는 문자는 `\uXXXX`(대문자 16진수, 서로게이트 쌍은 두 개)로 바꾼다.
     */
    fun escapeJava(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (c in value) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\b' -> sb.append("\\b")
                c == '\n' -> sb.append("\\n")
                c == '\t' -> sb.append("\\t")
                c == '\u000C' -> sb.append("\\f")
                c == '\r' -> sb.append("\\r")
                c.code < 32 || c.code > 0x7f -> sb.append("\\u").append(String.format("%04X", c.code))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * @param elapsedMillis 요청을 받은 뒤 지난 시간. 알 수 없으면(오류 경로, 접근 거부 경로) `null`이고 `-`로 쓴다
     * @param now 기록 시각. 요청을 받은 시각이 아니라 기록하는 순간의 시각이다
     */
    fun format(
        remoteAddress: String?, username: String?, now: Date, method: String, uri: String, version: String,
        status: Int, referer: String?, userAgent: String?, elapsedMillis: Long?,
    ): String {
        val time = SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH).format(now)
        val elapsed = if (elapsedMillis != null) "${elapsedMillis}ms" else "-"
        return "$remoteAddress - ${orHyphen(username)} [$time] \"$method $uri $version\" $status - " +
            "${quotedOrHyphen(referer)} ${quotedOrHyphen(userAgent)} $elapsed"
    }
}
