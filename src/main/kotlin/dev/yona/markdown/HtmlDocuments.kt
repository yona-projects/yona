package dev.yona.markdown

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * HTML을 읽고 다시 쓰는 jsoup 문서를 만든다.
 *
 * v1.6은 jsoup 1.8.3을 기본 설정(들여쓰기 출력, `prettyPrint`) 그대로 쓰고 `doc.body().html()`이 블록 요소 앞뒤에 줄바꿈과 공백을 넣었다. 최신 jsoup은 들여쓰기 규칙이 달라
 * (`<br>` 앞뒤에도 줄바꿈을 넣고 인라인 요소 사이의 공백 글자를 지운다) 같은 설정으로는 화면이 달라질 수 있다. 그래서 **들여쓰기 출력을 끄고** 읽은 글자를 그대로 둔다.
 * 블록 요소 사이의 줄바꿈과 들여쓰기 공백만 v1.6의 저장 HTML과 다르고(화면에는 영향이 없다), 인라인 요소 사이의 공백과 `<pre>`의 글자는 그대로이다(A-336, 선생님 승인 2026-11-02).
 */
internal object HtmlDocuments {

    /** 문서 전체를 읽는다. 출력은 들여쓰기를 끈다. */
    fun parse(html: String): Document = Jsoup.parse(html).also { it.outputSettings().prettyPrint(false) }
}
