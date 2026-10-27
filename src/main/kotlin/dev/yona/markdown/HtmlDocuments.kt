package dev.yona.markdown

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * HTML 조각을 읽고 다시 쓰는 jsoup 문서를 만든다.
 *
 * v1.6의 jsoup 1.8.3은 출력할 때 `<br>` 앞뒤에 줄바꿈을 넣지 않았다. 새 jsoup은 기본 들여쓰기 출력에서 `<br>`와 블록 요소 앞뒤에 줄바꿈과 공백을 넣어
 * 여러 줄 본문의 저장 HTML이 달라진다(`<p>a\n <br>\n b</p>`). 들여쓰기를 끄면 v1.6의 출력과 같다.
 */
internal object HtmlDocuments {

    /** 문서 전체를 읽는다. 출력은 들여쓰기 없이 원문 그대로 둔다. */
    fun parse(html: String): Document = Jsoup.parse(html).also { it.outputSettings().prettyPrint(false) }

    /** 조각을 읽는다. 출력은 들여쓰기 없이 원문 그대로 둔다. */
    fun parseBodyFragment(html: String): Document = Jsoup.parseBodyFragment(html).also { it.outputSettings().prettyPrint(false) }
}
