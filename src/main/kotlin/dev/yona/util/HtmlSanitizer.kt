package dev.yona.util

import org.owasp.html.Sanitizers

/** 사용자가 입력한 짧은 글의 HTML을 정화한다. v1.6 `utils.HtmlUtil.defaultSanitize`에 해당한다. */
object HtmlSanitizer {

    /** 굵게, 기울임 같은 서식 태그만 남기고 나머지(스크립트 등)는 제거한다. */
    fun defaultSanitize(source: String?): String = Sanitizers.FORMATTING.sanitize(source)
}
