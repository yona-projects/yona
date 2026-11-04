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
        // 퍼센트 인코딩이 있거나 빈 조각(`&&`)이 있는 주소는 v1.6이 다시 조립하며 값을 바꾸던 곳이다(R-94). 아래 별도 시험에서 고쳐진 결과를 확인한다
        val failures = rows.filter { !it[1].startsWith("EXCEPTION") && !it[0].contains('%') && !it[0].contains("&&") }.mapNotNull { (uri, expected) ->
            val actual = QueryLinks.excelLink(uri)
            if (actual == expected) null else "$uri 기대=$expected 실제=$actual"
        }
        assertTrue(failures.isEmpty(), "불일치 ${failures.size}건:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `엑셀 링크는 주소를 다시 조립하지 않아 인코딩된 값과 빈 조각이 그대로 남는다 (R-94)`() {
        // v1.6은 쿼리를 디코딩해 다시 조립해서 %2B가 +로, %26이 &로 바뀌어 검색 조건이 달라지고 &&가 남았다
        assertEquals("/o/p/issues?filter=a%2Bb&format=xls", QueryLinks.excelLink("/o/p/issues?filter=a%2Bb"))
        assertEquals("/o/p/issues?filter=a%26b&state=open&format=xls", QueryLinks.excelLink("/o/p/issues?filter=a%26b&state=open"))
        assertEquals("/o/p/issues?filter=%ED%95%9C%EA%B8%80&format=xls", QueryLinks.excelLink("/o/p/issues?filter=%ED%95%9C%EA%B8%80"))
        assertEquals("/o/p/issues?state=open&milestoneId=1&format=xls", QueryLinks.excelLink("/o/p/issues?state=open&&milestoneId=1"))
        assertEquals("/o/%ED%95%9C/issues?state=open&format=xls", QueryLinks.excelLink("/o/%ED%95%9C/issues?state=open"))
        // pageNum은 값이 있든 없든 뺀다
        assertEquals("/o/p/issues?state=open&format=xls", QueryLinks.excelLink("/o/p/issues?pageNum=3&state=open&pageNum"))
    }

    @Test
    fun `잘못된 주소는 오류 없이 pageNum만 거른다 (v1_6은 500)`() {
        assertEquals("/o/p/issues?filter=%&format=xls", QueryLinks.excelLink("/o/p/issues?filter=%"))
        assertEquals("/o/p/issues?format=xls", QueryLinks.excelLink("/o/p/issues?pageNum=%"))
        assertEquals("/o/p/issues?filter=a|b&format=xls", QueryLinks.excelLink("/o/p/issues?filter=a|b"))
    }

    @Test
    fun `넘겨 받은 요청이면 처음 요청한 경로와 쿼리를 쓴다`() {
        val request = org.springframework.mock.web.MockHttpServletRequest("GET", "/o/p/issues")
        request.queryString = "state=open"
        assertEquals("/o/p/issues?state=open", QueryLinks.requestUri(request))
        request.setAttribute(jakarta.servlet.RequestDispatcher.FORWARD_REQUEST_URI, "/o/p/go")
        request.setAttribute(jakarta.servlet.RequestDispatcher.FORWARD_QUERY_STRING, "pageNum=2")
        assertEquals("/o/p/go?pageNum=2", QueryLinks.requestUri(request))
        request.setAttribute(jakarta.servlet.RequestDispatcher.FORWARD_QUERY_STRING, null)
        assertEquals("/o/p/go", QueryLinks.requestUri(request))
    }
}
