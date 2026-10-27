package dev.yona.util

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 주소의 경로 조각을 인코딩하고 디코딩하는 도우미이다. v1.6 `utils.HttpUtil`의 `encodeUrlString`, `getEncodeEachPathName`,
 * `decodePathSegment`에 해당한다.
 */
object UrlPaths {

    /** 문자열 하나를 퍼센트 인코딩한다. 공백은 `+`가 아니라 `%20`이다 (v1.6 `HttpUtil.encodeUrlString`). */
    fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    /**
     * `/`로 나뉜 경로의 각 부분을 따로 인코딩한다. 비어 있으면 그대로이다 (v1.6 `HttpUtil.getEncodeEachPathName`).
     * v1.6은 Java `String.split("/")`를 쓰므로 끝의 빈 조각을 버린다. `a/b/`는 `a/b`, `/`는 빈 문자열이 된다.
     */
    fun encodeEach(path: String): String {
        if (path.isBlank()) return path
        if (!path.contains('/')) return encode(path)
        return path.split("/").dropLastWhile { it.isEmpty() }.joinToString("/") { encode(it) }
    }

    /**
     * 경로 조각을 한 번 더 디코딩한다. `+`는 글자 그대로 둔다(파일 이름에 `+`가 들어갈 수 있다). 주소의 경로 변수는 서버가 한 번 디코딩해서 받으므로,
     * 링크가 조각을 인코딩해 만들었다면 이 디코딩으로 원래 값이 된다 (v1.6 `HttpUtil.decodePathSegment`).
     */
    fun decodeSegment(value: String): String = URLDecoder.decode(value.replace("+", "*"), Charsets.UTF_8).replace("*", "+")

    /**
     * 주소의 경로 변수 자리(브랜치 이름 등)에 넣을 값이다. v1.6은 값을 `URLEncoder`로 인코딩한 뒤 라우트 역변환이 한 번 더 인코딩해 두 번
     * 인코딩된 주소가 나가고, 서버가 받아서 디코딩한 값을 `decodePathSegment`로 한 번 더 디코딩한다. 이렇게 하면 `/`가 든 이름(`feature/x`)도
     * 주소에 `%2F`가 직접 나타나지 않는다.
     */
    fun routeSegment(value: String): String = encode(URLEncoder.encode(value, Charsets.UTF_8))

    /**
     * 프로젝트 주소의 앞부분 `/소유자/이름`이다. v1.6 리버스 라우트(`routes.IssueApp.issue` 등)는 경로 변수를 퍼센트 인코딩하므로
     * 한글이나 공백이 든 이름은 인코딩해서 쓴다.
     */
    fun projectPath(owner: String?, name: String?): String = "/${encode(owner.orEmpty())}/${encode(name.orEmpty())}"

    /** [value]를 [decodeSegment]로 디코딩한다. 올바르지 않은 퍼센트 인코딩이면 값을 그대로 돌려준다. */
    fun decodeSegmentOrSelf(value: String): String = try { decodeSegment(value) } catch (e: IllegalArgumentException) { value }

    /** [base] 뒤에 [name]을 붙인다. [base]가 `/`로 끝나지 않으면 `/`를 끼우고 [name]의 각 부분은 인코딩한다 (v1.6 `getCorrectedPath`). */
    fun join(base: String, name: String): String =
        if (base.isNotEmpty() && base.endsWith("/")) base + encodeEach(name) else base + "/" + encodeEach(name)
}
