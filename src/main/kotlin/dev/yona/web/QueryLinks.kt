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
     * v1.6은 주소가 잘못되면(`%` 단독, `|` 등) 500이 되지만 v2는 오류 없이 쿼리를 그대로 두고 `pageNum` 쌍만 문자열로 걸러 낸다.
     */
    fun excelLink(requestUri: String, excelExt: String = "xls"): String = try {
        addQueryString(removeQueryString(requestUri, "pageNum"), "format=$excelExt")
    } catch (e: URISyntaxException) {
        fallback(requestUri, excelExt)
    } catch (e: IllegalArgumentException) {
        fallback(requestUri, excelExt)
    }

    private fun fallback(requestUri: String, excelExt: String): String {
        val q = requestUri.indexOf('?')
        val path = if (q < 0) requestUri else requestUri.substring(0, q)
        val parts = if (q < 0) emptyList() else requestUri.substring(q + 1).split("&")
            .filter { it.isNotEmpty() && it != "pageNum" && !it.startsWith("pageNum=") }
        return path + "?" + (parts + "format=$excelExt").joinToString("&")
    }
}
