package dev.yona.markdown

import org.jsoup.Jsoup
import java.net.URI
import java.net.URISyntaxException

/**
 * 렌더링한 HTML의 링크를 안전하게 고친다. v1.6 `utils.Markdown`의 `removeJavascriptInHref`, `checkReferrer`에 해당한다.
 *
 * @property noReferrer 외부 링크에 `noreferrer`를 붙일지 여부 (`application.noreferrer`)
 * @property hostname 이 사이트의 호스트 이름. 이 이름으로 시작하는 호스트는 같은 사이트로 본다
 */
class LinkPostProcessor(private val noReferrer: Boolean, private val hostname: String) {

    /**
     * `javascript:`로 시작하는 `href`를 `#`으로 바꾼다. 문자와 숫자, 밑줄, 콜론이 아닌 문자를 모두 뗀 값으로 판단하므로
     * 줄바꿈이나 공백으로 숨긴 `java\nscript:`도 막는다.
     */
    fun removeJavascriptInHref(html: String): String {
        val body = Jsoup.parse(html).body()
        body.getElementsByAttribute("href").forEach { element ->
            val href = element.attr("href").replace(NON_WORD, "").lowercase()
            if (href.startsWith("javascript:")) element.attr("href", "#")
        }
        return body.html()
    }

    /** 설정이 켜져 있으면 다른 호스트로 가는 링크에 `noreferrer`를 붙인다. */
    fun checkReferrer(html: String): String {
        if (!noReferrer) return html
        val body = Jsoup.parse(html).body()
        body.getElementsByAttribute("href").forEach { element ->
            try {
                val host = URI(element.attr("href")).host
                if (host != null && !host.startsWith(hostname)) element.attr("rel", element.attr("rel") + " noreferrer")
            } catch (e: URISyntaxException) {
                // 잘못된 링크는 건너뛴다.
            }
        }
        return body.html()
    }

    private companion object {
        val NON_WORD = Regex("[^\\w:]")
    }
}
