package dev.yona.markdown

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * HTML을 읽고 다시 쓰는 jsoup 문서를 만든다.
 *
 * v1.6은 jsoup 1.8.3(`org.jsoup:jsoup:1.8.3`)을 쓰고 `removeJavascriptInHref`, `checkReferrer`, `transformIssueLink`에서 `Jsoup.parse(source)`를
 * 기본 설정 그대로 쓴다. 기본 설정은 들여쓰기 출력(`prettyPrint`)이라 `doc.body().html()`이 블록 요소 앞뒤에 줄바꿈과 공백을 넣는다.
 * 같은 글자의 HTML을 저장하려고 같은 버전의 jsoup을 쓰고 설정도 바꾸지 않는다. 들여쓰기를 끄는 곳은 `AutoLinkRenderer`뿐이다(v1.6과 같다).
 */
internal object HtmlDocuments {

    /** 문서 전체를 읽는다. 출력 설정은 jsoup 기본값(들여쓰기 켬)이다. */
    fun parse(html: String): Document = Jsoup.parse(html)
}
