package dev.yona.support

import org.jsoup.nodes.Element

/**
 * jsoup 1.8.3(v1.6이 쓰는 버전)에는 `selectFirst`가 없어서 테스트가 쓰는 같은 이름의 확장 함수이다.
 * 첫 번째로 일치하는 요소이며 없으면 `null`이다.
 */
fun Element.selectFirst(cssQuery: String): Element? = select(cssQuery).first()

/** jsoup 1.8.3에는 없는 `wholeText()`이다. 직접 가진 글 노드를 공백 그대로 이어 붙인다(`pre`, `textarea` 검증용). */
fun Element.wholeText(): String = textNodes().joinToString("") { it.wholeText }
