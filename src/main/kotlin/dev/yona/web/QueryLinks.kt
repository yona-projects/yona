package dev.yona.web

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * v1.6 `utils.HttpUtil.removeQueryString`, `addQueryString`을 그대로 옮긴 것이다. 엑셀 내려받기 링크
 * (`addQueryString(removeQueryString(요청 URI, "pageNum"), "format=xls")`)가 쓴다.
 *
 * `java.net.URI`의 `getQuery()`는 퍼센트 인코딩을 풀어 주고 다섯 인자 생성자는 `%`와 허용되지 않는 문자만 다시 인코딩한다.
 * 그래서 `%2B`는 `+`로, `%26`은 `&`로, `%ED%95%9C`는 `한`으로 바뀐 링크가 나온다. v1.6과 같은 문자열을 내려면 같은 API를 쓴다.
 */
object QueryLinks {

    /**
     * 질의 문자열에서 이름이 [name]인 첫 값을 찾는다. 폼 본문은 보지 않는다(v1.6 `request().getQueryString(...)`와 Play의 질의 바인더가 그렇다).
     * v1.6의 Netty 질의 해석처럼 `&`뿐 아니라 `;`도 구분자로 본다. 화면이 스크립트에 찍어 주는 구독 주소가 `&amp;`로 구분되어 있어(Twirl의 이스케이프)
     * 서버가 이를 받아들여야 같은 동작이 된다. 값이 없으면 `null`이다.
     */
    fun queryValue(request: jakarta.servlet.http.HttpServletRequest, name: String): String? {
        val query = request.queryString ?: return null
        for (pair in query.split('&', ';')) {
            val eq = pair.indexOf('=')
            val key = java.net.URLDecoder.decode(if (eq < 0) pair else pair.substring(0, eq), Charsets.UTF_8)
            if (key == name) return if (eq < 0) "" else java.net.URLDecoder.decode(pair.substring(eq + 1), Charsets.UTF_8)
        }
        return null
    }

    /**
     * 요청 주소(경로와 쿼리)이다. v1.6 `requestHeader.uri`는 `/go`처럼 안쪽으로 넘기기(forward) 전에 요청한 주소 그대로이므로,
     * 넘겨 받은 요청이면 처음 요청한 경로와 쿼리를 쓴다.
     */
    fun requestUri(request: jakarta.servlet.http.HttpServletRequest): String {
        val path = request.getAttribute(jakarta.servlet.RequestDispatcher.FORWARD_REQUEST_URI) as? String ?: request.requestURI
        val query = if (request.getAttribute(jakarta.servlet.RequestDispatcher.FORWARD_REQUEST_URI) != null)
            request.getAttribute(jakarta.servlet.RequestDispatcher.FORWARD_QUERY_STRING) as? String
        else request.queryString
        return path + (query?.let { "?$it" } ?: "")
    }

    /** [url]의 쿼리에 이미 인코딩된 `키=값` 쌍들을 덧붙인다. 쿼리가 없으면 새로 만든다. */
    fun addQueryString(url: String, vararg encodedPairs: String): String {
        val uri = URI(url)
        var query = uri.query ?: ""
        query += (if (query.isNotEmpty()) "&" else "") + encodedPairs.joinToString("&")
        return URI(uri.scheme, uri.authority, uri.path, query, uri.fragment).toString()
    }

    /** [url]의 쿼리에서 [keys]에 해당하는 쌍을 뺀다. 쿼리가 없으면 [url]을 그대로 돌려준다. 키 없는 쌍(`pageNum`)도 뺀다. */
    fun removeQueryString(url: String, vararg keys: String): String {
        val uri = URI(url)
        val query = uri.query ?: return url
        val keySet = keys.toSet()
        val kept = mutableListOf<String>()
        // Java String.split 의미: 끝의 빈 조각은 버린다.
        for (pairString in javaSplit(query, "&")) {
            val pair = javaSplit(pairString, "=")
            if (pair.isEmpty()) continue
            if (URLDecoder.decode(pair[0], Charsets.UTF_8) !in keySet) kept += pairString
        }
        return URI(uri.scheme, uri.authority, uri.path, kept.joinToString("&"), uri.fragment).toString()
    }

    /**
     * Java `String.split(String)`과 같다. 구분자가 없으면 원문 한 조각(빈 문자열이어도 `[""]`)이고, 있으면 끝의 빈 조각을 버린다
     * (그래서 `"="`는 빈 목록이 된다).
     */
    fun javaSplit(text: String, delimiter: String): List<String> = dev.yona.util.JavaStrings.split(text, delimiter)

    /**
     * 엑셀 링크이다. [requestUri]는 쿼리를 포함한 원래의 요청 URI(컨텍스트 경로 포함)이다.
     * 쿼리를 다시 조립하지 않고 원래 문자열 그대로 두고 `pageNum` 쌍(값이 없는 것 포함)과 빈 조각만 뺀 뒤 `format`을 덧붙인다.
     * v1.6은 `URI.getQuery()`로 퍼센트 인코딩을 풀어 다시 조립해 `%2B`가 `+`로, `%26`이 `&`로 바뀌어 검색 조건이 달라졌고 `&&`가 남았으며,
     * 잘못된 주소(`%` 단독, `|` 등)는 500이었다(R-94).
     */
    fun excelLink(requestUri: String, excelExt: String = "xls"): String {
        val q = requestUri.indexOf('?')
        val path = if (q < 0) requestUri else requestUri.substring(0, q)
        val parts = if (q < 0) emptyList() else requestUri.substring(q + 1).split("&")
            .filter { it.isNotEmpty() && it != "pageNum" && !it.startsWith("pageNum=") }
        return path + "?" + (parts + "format=$excelExt").joinToString("&")
    }
}
