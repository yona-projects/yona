package dev.yona.web

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 엑셀 내려받기 링크를 검증한다 (v1.6 `HttpUtil.addQueryString(removeQueryString(uri, "pageNum"), "format=xls")`).
 *
 * 기대값 31줄(`moment/xls_cases_v16.tsv`)은 v1.6 `HttpUtil` 본문을 그대로 실행해 얻었다. `%2B`가 `+`로, `%26`이 `&`로 바뀌는 것, 값 없는 `pageNum`이
 * 빠지는 것, 빈 조각 `&&`가 남는 것이 v1.6의 동작이다. 마지막 세 줄(`%`, `pageNum=%`, `a|b`)은 v1.6이 `URISyntaxException`으로 500이 되는 입력이라
 * v2는 오류 없이 쿼리를 그대로 두고 `pageNum` 쌍만 거른다.
 */
class QueryLinksTest {

    @Test
    fun `v1_6이 낸 링크와 모두 같다`() {
        val rows = javaClass.getResourceAsStream("/moment/xls_cases_v16.tsv")!!.bufferedReader().readLines().drop(1).filter { it.isNotBlank() }.map { it.split('\t') }
        assertTrue(rows.size >= 30, "줄 수 ${rows.size}")
        val failures = rows.filter { !it[1].startsWith("EXCEPTION") }.mapNotNull { (uri, expected) ->
            val actual = QueryLinks.excelLink(uri)
            if (actual == expected) null else "$uri 기대=$expected 실제=$actual"
        }
        assertTrue(failures.isEmpty(), "불일치 ${failures.size}건:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `잘못된 주소는 오류 없이 pageNum만 거른다 (v1_6은 500)`() {
        assertEquals("/o/p/issues?filter=%&format=xls", QueryLinks.excelLink("/o/p/issues?filter=%"))
        assertEquals("/o/p/issues?format=xls", QueryLinks.excelLink("/o/p/issues?pageNum=%"))
        assertEquals("/o/p/issues?filter=a|b&format=xls", QueryLinks.excelLink("/o/p/issues?filter=a|b"))
    }
}
